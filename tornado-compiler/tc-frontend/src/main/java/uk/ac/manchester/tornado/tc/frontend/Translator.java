/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.frontend;

import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import uk.ac.manchester.tornado.tc.frontend.Bindings.ArrayGet;
import uk.ac.manchester.tornado.tc.frontend.Bindings.ArraySet;
import uk.ac.manchester.tornado.tc.frontend.Bindings.ArraySize;
import uk.ac.manchester.tornado.tc.frontend.Bindings.Identity;
import uk.ac.manchester.tornado.tc.frontend.Bindings.Intrinsic;
import uk.ac.manchester.tornado.tc.frontend.Bindings.SharedArray;
import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.Insn;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.TypeAnnotations;
import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Opcode.FloatPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Opcode.UnaryOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;

/**
 * Translates one Java method into pre-ABI TC-SSA.
 *
 * <p>The bytecode is split into basic blocks and walked in reverse postorder while an abstract operand
 * stack is simulated. Local variables (and operand-stack slots that are live across a block boundary,
 * as in {@code c ? a : b}) are SSA variables built with the on-the-fly algorithm of Braun, Buchwald,
 * Hack, Leißa, Mallon and Zwinkau, "Simple and Efficient Construction of Static Single Assignment
 * Form" (CC 2013): a block is sealed once all its predecessors have been filled; block arguments play
 * the role of phi functions; trivial block arguments are removed afterwards.</p>
 *
 * <p>Error codes: {@code TC-E1001} exception handlers, {@code E1002} unsupported bytecode,
 * {@code E1003} unsupported call or field, {@code E1004} non-constant local-array size,
 * {@code E1005} unsupported type, {@code E1006} read of an undefined local.</p>
 */
final class Translator {

    /** Stops translation with a diagnostic. */
    static final class FrontendError extends RuntimeException {
        final transient Diagnostic diagnostic;

        FrontendError(Diagnostic diagnostic) {
            super(diagnostic.code() + ": " + diagnostic.message(), null, false, false);
            this.diagnostic = diagnostic;
        }
    }

    private static final String PARALLEL = "Luk/ac/manchester/tornado/api/annotations/Parallel;";

    private final ClassFile classFile;
    private final ClassFile.Method method;
    private final ClassFile.Code code;
    private final String sourceFile;
    private final List<Insn> insns;
    private final Map<Integer, Integer> indexOfBci = new HashMap<>();
    private final Function function;

    // Bytecode CFG.
    private final TreeMap<Integer, Block> blockAt = new TreeMap<>();
    private final Map<Block, Integer> startIndex = new IdentityHashMap<>();
    private final Map<Block, List<Block>> preds = new IdentityHashMap<>();

    // SSA construction state (Braun et al.).
    private final Map<Block, Map<String, Value>> currentDef = new IdentityHashMap<>();
    private final Map<Block, LinkedHashMap<String, BlockArg>> incomplete = new IdentityHashMap<>();
    private final Set<Block> sealed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Block> filled = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Block, Integer> entryDepth = new IdentityHashMap<>();
    private final Map<Block, List<Type>> entryStackTypes = new IdentityHashMap<>();

    private final Map<BlockArg, String> argumentKeys = new IdentityHashMap<>();
    private final Map<String, uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef> callees = new java.util.LinkedHashMap<>();
    private IrBuilder builder;
    private Deque<Value> stack;

    private Translator(ClassFile classFile, ClassFile.Method method, Function.Kind kind) {
        this(classFile, method, kind, null);
    }

    /** {@code name} overrides the function name (an inherited method is called by its subclass name). */
    private Translator(ClassFile classFile, ClassFile.Method method, Function.Kind kind, String name) {
        this.classFile = classFile;
        this.method = method;
        this.code = method.code().orElseThrow(() -> new IllegalArgumentException(method.name() + " has no code"));
        var simpleClass = classFile.name().substring(classFile.name().lastIndexOf('/') + 1);
        this.sourceFile = (simpleClass.contains("$") ? simpleClass.substring(0, simpleClass.indexOf('$')) : simpleClass) + ".java";
        this.insns = Bytecode.decode(code.bytes());
        for (int i = 0; i < insns.size(); i++) {
            indexOfBci.put(insns.get(i).bci(), i);
        }
        var returnDescriptor = method.descriptor().substring(method.descriptor().indexOf(')') + 1);
        var functionName = name != null ? name : kind == Function.Kind.KERNEL ? simpleClass + "." + method.name() : calleeName(classFile.name(), method.name(), method.descriptor());
        this.function = new Function(functionName, kind, Bindings.type(returnDescriptor));
    }

    static Function translate(ClassFile classFile, ClassFile.Method method) {
        return new Translator(classFile, method, Function.Kind.KERNEL).run();
    }

    /** The function and the static methods it calls (by TC function name), for transitive translation. */
    record Translated(Function function, Map<String, uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef> callees) {
    }

    static Translated translate(ClassFile classFile, ClassFile.Method method, Function.Kind kind) {
        var translator = new Translator(classFile, method, kind);
        return new Translated(translator.run(), translator.callees);
    }

    /** A device function translated under the name its callers use. */
    static Translated translateDevice(ClassFile classFile, ClassFile.Method method, String name) {
        var translator = new Translator(classFile, method, Function.Kind.DEVICE, name);
        return new Translated(translator.run(), translator.callees);
    }

    /** The TC name of a Java static method: {@code Owner.name}, plus a descriptor hash for overloads. */
    static String calleeName(String owner, String name, String descriptor) {
        return owner.substring(owner.lastIndexOf('/') + 1).replace('$', '_') + "." + name + "_" + Integer.toHexString(descriptor.hashCode());
    }

    private Function run() {
        if (!code.handlers().isEmpty()) {
            throw error("TC-E1001", "exception handlers (try/catch/finally) are not supported in kernels", 0);
        }
        var entry = function.newBlock("entry");
        buildBlocks(entry);
        // Parameters: slot numbers follow the descriptor; long and double take two slots. An instance
        // method (a device function reached by devirtualised call, or an instance-method task, whose
        // receiver the runtime passes as the first argument) takes `this` first, in slot 0.
        var params = parameterDescriptors(method.descriptor());
        int slot = 0;
        if (!method.isStatic()) {
            var self = entry.addArg(Bindings.type("L" + classFile.name() + ";"), "this");
            writeVariable(localKey(0, 'A'), entry, self);
            slot = 1;
        }
        for (var descriptor : params) {
            var type = Bindings.type(descriptor);
            var name = code.localAt(slot, 0).map(ClassFile.LocalVariable::name).orElse("arg" + slot);
            var arg = entry.addArg(type, name);
            writeVariable(localKey(slot, descriptorKind(descriptor)), entry, arg);
            slot += descriptor.equals("J") || descriptor.equals("D") ? 2 : 1;
        }
        sealed.add(entry);
        builder = new IrBuilder(entry);
        var first = blockAt.get(0);
        builder.br(first, List.of());
        filled.add(entry);
        noteFilled(entry);

        var order = reversePostorder(first);
        var reachable = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        reachable.addAll(order);
        reachable.add(entry);
        for (var block : order) {
            preds.get(block).removeIf(p -> !reachable.contains(p));
        }
        for (var block : order) {
            translateBlock(block);
        }
        removeUnreachable();
        removeTrivialArguments();
        var problems = LocalArrays.resolveLengths(function);
        if (!problems.isEmpty()) {
            throw new FrontendError(problems.get(0));
        }
        markParallelLoops();
        return function;
    }

    // ---------------------------------------------------------------- CFG

    private void buildBlocks(Block entry) {
        var leaders = new TreeSet<Integer>();
        leaders.add(0);
        for (int i = 0; i < insns.size(); i++) {
            var insn = insns.get(i);
            for (var target : insn.targets()) {
                leaders.add(target);
            }
            if (Bytecode.endsBlock(insn.opcode()) && i + 1 < insns.size()) {
                leaders.add(insns.get(i + 1).bci());
            }
        }
        for (var bci : leaders) {
            var block = function.newBlock("bci" + bci);
            blockAt.put(bci, block);
            startIndex.put(block, indexOfBci.get(bci));
            preds.put(block, new ArrayList<>());
        }
        preds.get(blockAt.get(0)).add(entry);
        for (var block : blockAt.values()) {
            for (var successor : successorsOf(block)) {
                preds.get(successor).add(block);
            }
        }
    }

    /** Bytecode successors of {@code block}, one entry per edge. */
    private List<Block> successorsOf(Block block) {
        var last = insns.get(lastIndex(block));
        var successors = new ArrayList<Block>();
        for (var target : last.targets()) {
            successors.add(blockAt.get(target));
        }
        int op = last.opcode();
        if (!(op == GOTO || op == GOTO_W || op == TABLESWITCH || op == LOOKUPSWITCH || Bytecode.isReturn(op) || op == ATHROW)) {
            var next = blockAt.higherEntry(insns.get(startIndex.get(block)).bci());
            if (next != null) {
                successors.add(next.getValue()); // a conditional branch lists [taken, fall-through]
            }
        }
        return successors;
    }

    private int lastIndex(Block block) {
        var start = insns.get(startIndex.get(block)).bci();
        var next = blockAt.higherKey(start);
        return (next == null ? insns.size() : indexOfBci.get(next)) - 1;
    }

    private List<Block> reversePostorder(Block first) {
        var order = new ArrayList<Block>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        record Frame(Block block, List<Block> successors, int next) {
        }
        var stackOfFrames = new ArrayDeque<Frame>();
        visited.add(first);
        stackOfFrames.push(new Frame(first, successorsOf(first), 0));
        while (!stackOfFrames.isEmpty()) {
            var frame = stackOfFrames.pop();
            if (frame.next() < frame.successors().size()) {
                stackOfFrames.push(new Frame(frame.block(), frame.successors(), frame.next() + 1));
                var s = frame.successors().get(frame.next());
                if (visited.add(s)) {
                    stackOfFrames.push(new Frame(s, successorsOf(s), 0));
                }
            } else {
                order.add(frame.block());
            }
        }
        Collections.reverse(order);
        return order;
    }

    // ---------------------------------------------------------------- blocks

    private void translateBlock(Block block) {
        if (preds.get(block).stream().allMatch(filled::contains)) {
            seal(block);
        }
        builder = new IrBuilder(block);
        stack = new ArrayDeque<>();
        int depth = entryDepth.getOrDefault(block, 0);
        var types = entryStackTypes.getOrDefault(block, List.of());
        for (int i = 0; i < depth; i++) {
            stack.addLast(readVariable("S" + i, block, types.get(i), ""));
        }
        int last = lastIndex(block);
        for (int i = startIndex.get(block); i <= last; i++) {
            var insn = insns.get(i);
            builder.at(new Loc(sourceFile, code.lineAt(insn.bci()), insn.bci()));
            i = translate(block, insn, i);
        }
        if (block.terminator().isEmpty()) {
            // Fall-through into the next block.
            var next = blockAt.higherEntry(insns.get(startIndex.get(block)).bci()).getValue();
            leaveTo(block, List.of(next));
            builder.br(next, List.of());
        }
        filled.add(block);
        noteFilled(block);
    }

    /** Records the operand stack flowing into {@code successors} as SSA variables. */
    private void leaveTo(Block block, List<Block> successors) {
        int i = 0;
        var types = new ArrayList<Type>();
        for (var value : stack) {
            writeVariable("S" + i++, block, value);
            types.add(value.type());
        }
        for (var s : successors) {
            entryDepth.putIfAbsent(s, stack.size());
            entryStackTypes.putIfAbsent(s, types);
        }
    }

    private void noteFilled(Block block) {
        for (var s : block.successors()) {
            if (!sealed.contains(s) && preds.get(s).stream().allMatch(filled::contains)) {
                seal(s);
            }
        }
    }

    // ---------------------------------------------------------------- SSA variables (Braun et al.)

    private void writeVariable(String key, Block block, Value value) {
        currentDef.computeIfAbsent(block, _k -> new HashMap<>()).put(key, value);
    }

    private Value readVariable(String key, Block block, Type type, String name) {
        var defs = currentDef.get(block);
        if (defs != null && defs.containsKey(key)) {
            return defs.get(key);
        }
        Value value;
        var blockPreds = preds.getOrDefault(block, List.of());
        if (!sealed.contains(block)) {
            var known = type instanceof Type.ObjectType o && o.className().equals("?") ? probeType(key, block, Collections.newSetFromMap(new IdentityHashMap<>())) : null;
            var arg = block.addArg(known != null ? known : type, name);
            argumentKeys.put(arg, key);
            incomplete.computeIfAbsent(block, _k -> new LinkedHashMap<>()).put(key, arg);
            value = arg;
        } else if (blockPreds.size() == 1) {
            value = readVariable(key, blockPreds.get(0), type, name);
        } else if (blockPreds.isEmpty()) {
            throw error("TC-E1006", "read of local " + key + " before it is assigned", insns.get(startIndex.getOrDefault(block, 0)).bci());
        } else {
            var arg = block.addArg(type, name);
            argumentKeys.put(arg, key);
            writeVariable(key, block, arg);
            addArgumentOperands(key, block, arg);
            value = arg;
        }
        writeVariable(key, block, value);
        return value;
    }

    /**
     * The type of {@code key} as already defined on a path into {@code block}, without creating block
     * arguments: a reference read in a loop header that is not sealed yet must know whether it is an
     * array, a local array (pointer) or an object before its first use is translated.
     */
    private Type probeType(String key, Block block, Set<Block> visited) {
        if (!visited.add(block)) {
            return null;
        }
        var defs = currentDef.get(block);
        if (defs != null && defs.containsKey(key)) {
            return defs.get(key).type();
        }
        for (var pred : preds.getOrDefault(block, List.of())) {
            if (filled.contains(pred)) {
                var t = probeType(key, pred, visited);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Passes the value of {@code key} on every incoming edge of {@code block} to {@code arg}. */
    private void addArgumentOperands(String key, Block block, BlockArg arg) {
        for (var pred : distinctPreds(block)) {
            var value = readVariable(key, pred, arg.type(), arg.name());
            if (arg.type() instanceof Type.ObjectType o && o.className().equals("?")) {
                arg.setType(value.type());
            }
            var term = pred.terminator().orElseThrow();
            for (int i = 0; i < term.successors().size(); i++) {
                if (term.successors().get(i).target() == block) {
                    var args = new ArrayList<>(term.successors().get(i).args());
                    args.add(value);
                    term.setSuccessorArgs(i, args);
                }
            }
        }
    }

    private void seal(Block block) {
        if (!sealed.add(block)) {
            return;
        }
        var pending = incomplete.remove(block);
        if (pending != null) {
            pending.forEach((key, arg) -> addArgumentOperands(key, block, arg));
        }
    }

    private List<Block> distinctPreds(Block block) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        var result = new ArrayList<Block>();
        for (var p : preds.get(block)) {
            if (seen.add(p)) {
                result.add(p);
            }
        }
        return result;
    }

    /** Removes block arguments whose incoming values are all the same value (or the argument itself). */
    private void removeTrivialArguments() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : function.blocks()) {
                if (block == function.entry()) {
                    continue;
                }
                for (int index = block.args().size() - 1; index >= 0; index--) {
                    var arg = block.args().get(index);
                    Value same = null;
                    boolean trivial = true;
                    for (var incoming : incomingValues(block, index)) {
                        if (incoming == arg || incoming == same) {
                            continue;
                        }
                        if (same != null) {
                            trivial = false;
                            break;
                        }
                        same = incoming;
                    }
                    if (!trivial || same == null) {
                        continue;
                    }
                    arg.replaceAllUsesWith(same);
                    removeIncoming(block, index);
                    block.removeArg(arg);
                    changed = true;
                }
            }
        }
    }

    private List<Value> incomingValues(Block block, int index) {
        var values = new ArrayList<Value>();
        for (var pred : function.blocks()) {
            pred.terminator().ifPresent(term -> {
                for (var s : term.successors()) {
                    if (s.target() == block) {
                        values.add(s.args().get(index));
                    }
                }
            });
        }
        return values;
    }

    private void removeIncoming(Block block, int index) {
        for (var pred : function.blocks()) {
            pred.terminator().ifPresent(term -> {
                for (int i = 0; i < term.successors().size(); i++) {
                    if (term.successors().get(i).target() == block) {
                        var args = new ArrayList<>(term.successors().get(i).args());
                        args.remove(index);
                        term.setSuccessorArgs(i, args);
                    }
                }
            });
        }
    }

    private void removeUnreachable() {
        var reachable = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        var work = new ArrayDeque<Block>();
        work.push(function.entry());
        reachable.add(function.entry());
        while (!work.isEmpty()) {
            for (var s : work.pop().successors()) {
                if (reachable.add(s)) {
                    work.push(s);
                }
            }
        }
        for (var block : List.copyOf(function.blocks())) {
            if (!reachable.contains(block)) {
                for (var inst : List.copyOf(block.insts())) {
                    inst.dropReferences();
                }
                function.removeBlock(block);
            }
        }
    }

    /**
     * Tags loop headers whose block argument carries a local annotated {@code @Parallel}:
     * {@code parallel(iv=k)} names the argument; {@code tc-tornado} lowers the loop.
     */
    private void markParallelLoops() {
        var parallel = TypeAnnotations.locals(code.typeAnnotations(), classFile.constants()).stream().filter(a -> a.descriptor().equals(PARALLEL)).toList();
        if (parallel.isEmpty()) {
            return;
        }
        for (var entry : blockAt.entrySet()) {
            var header = entry.getValue();
            if (!function.blocks().contains(header)) {
                continue;
            }
            int bci = entry.getKey();
            for (int k = 0; k < header.args().size(); k++) {
                var key = argumentKeys.get(header.args().get(k));
                if (key == null || !key.startsWith("L")) {
                    continue;
                }
                int slot = Integer.parseInt(key.substring(1, key.length() - 1));
                if (parallel.stream().anyMatch(a -> a.slot() == slot && a.covers(bci))) {
                    header.addAttribute(new Attribute("parallel", "iv=" + k));
                }
            }
        }
    }

    // ---------------------------------------------------------------- instructions

    /** Translates {@code insn} (index {@code i}); returns the index of the last instruction consumed. */
    private int translate(Block block, Insn insn, int i) {
        int op = insn.opcode();
        var b = builder;
        switch (op) {
            case NOP -> {
            }
            case ACONST_NULL -> throw error("TC-E1002", "null references are not supported in kernels", insn.bci());
            case BIPUSH, SIPUSH -> push(b.constInt(Type.I32, insn.index()));
            case LDC, LDC_W, LDC2_W -> push(switch (classFile.constants().loadable(insn.index())) {
                case Integer v -> b.constInt(Type.I32, v);
                case Float v -> b.constFloat(Type.F32, v);
                case Long v -> b.constInt(Type.I64, v);
                case Double v -> b.constFloat(Type.F64, v);
                default -> throw error("TC-E1002", "string constants are not supported in kernels", insn.bci());
            });
            case IINC -> {
                var key = localKey(insn.index(), 'I');
                var current = readVariable(key, block, Type.I32, localName(insn.index(), insn.bci()));
                var sum = b.add(current, b.constInt(Type.I32, insn.value()));
                nameLocal(sum, insn.index(), insn.bci());
                writeVariable(key, block, sum);
            }
            case POP -> stack.removeLast();
            case POP2 -> {
                var top = stack.removeLast();
                if (category(top) == 1) {
                    stack.removeLast();
                }
            }
            case DUP -> push(stack.peekLast());
            case DUP_X1 -> {
                var v1 = pop();
                var v2 = pop();
                push(v1);
                push(v2);
                push(v1);
            }
            case DUP2 -> {
                var v1 = pop();
                if (category(v1) == 2) {
                    push(v1);
                    push(v1);
                } else {
                    var v2 = pop();
                    push(v2);
                    push(v1);
                    push(v2);
                    push(v1);
                }
            }
            case SWAP -> {
                var v1 = pop();
                var v2 = pop();
                push(v1);
                push(v2);
            }
            case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> {
                if (i + 1 <= lastIndex(block) && insns.get(i + 1).opcode() >= IFEQ && insns.get(i + 1).opcode() <= IFLE) {
                    var branch = insns.get(i + 1);
                    var rhs = pop();
                    var lhs = pop();
                    var condition = op == LCMP ? b.icmp(intPredicate(branch.opcode() - IFEQ), lhs, rhs) : b.fcmp(floatPredicate(branch.opcode() - IFEQ, op == FCMPG || op == DCMPG), lhs, rhs);
                    conditionalBranch(block, condition, branch);
                    return i + 1;
                }
                var rhs = pop();
                var lhs = pop();
                push(threeWayCompare(op, lhs, rhs));
            }
            case IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL -> throw error("TC-E1002", "reference comparisons are not supported in kernels", insn.bci());
            case GOTO, GOTO_W -> {
                var target = blockAt.get(insn.targets()[0]);
                leaveTo(block, List.of(target));
                b.br(target, List.of());
            }
            case TABLESWITCH, LOOKUPSWITCH -> {
                var selector = pop();
                var keys = new ArrayList<Long>();
                var cases = new ArrayList<Block>();
                for (int k = 0; k < insn.keys().length; k++) {
                    keys.add((long) insn.keys()[k]);
                    cases.add(blockAt.get(insn.targets()[k]));
                }
                var deflt = blockAt.get(insn.targets()[insn.keys().length]);
                var all = new ArrayList<>(cases);
                all.add(deflt);
                leaveTo(block, all);
                b.switchOn(selector, keys, cases, deflt);
            }
            case RETURN -> b.ret();
            case GETFIELD, GETSTATIC -> {
                var ref = classFile.constants().member(insn.index());
                Value receiver = op == GETFIELD ? pop() : null;
                if (receiver != null && receiver.type() instanceof Type.ObjectType && Bindings.field(ref.owner(), ref.name()).isEmpty()) {
                    // A field of an object argument: read from its device copy (laid out like the JVM object).
                    var memory = Bindings.elementType(ref.descriptor());
                    var loaded = b.fieldLoad(receiver, ref.name(), memory);
                    push(switch (ref.descriptor().charAt(0)) {
                        case 'C', 'Z' -> b.convert(ConvertOp.ZEXT, loaded, Type.I32);
                        case 'B', 'S' -> b.convert(ConvertOp.SEXT, loaded, Type.I32);
                        default -> loaded;
                    });
                    break;
                }
                var binding = Bindings.field(ref.owner(), ref.name()).orElseThrow(() -> error("TC-E1003", "field " + ref.owner().replace('/', '.') + "." + ref.name() + " is not supported in kernels yet", insn.bci()));
                if (binding instanceof Intrinsic(var name, var type)) {
                    push(b.intrinsic(name, type, List.of()));
                } else if (binding instanceof Bindings.ConstantField(var value)) {
                    push(b.constInt(Type.I32, value));
                }
            }
            case PUTFIELD -> {
                var ref = classFile.constants().member(insn.index());
                var value = pop();
                var receiver = pop();
                if (!(receiver.type() instanceof Type.ObjectType)) {
                    throw error("TC-E1003", "field writes are only supported on object arguments", insn.bci());
                }
                // Stored at its memory width (sub-int fields narrow, like array stores).
                var memory = Bindings.elementType(ref.descriptor());
                var stored = memory.equals(value.type()) ? value : b.convert(ConvertOp.TRUNC, value, memory);
                b.fieldStore(receiver, ref.name(), stored);
            }
            case PUTSTATIC -> throw error("TC-E1003", "static field writes are not supported in kernels", insn.bci());
            case INVOKEVIRTUAL, INVOKESTATIC, INVOKEINTERFACE, INVOKESPECIAL -> invoke(insn);
            case NEWARRAY -> {
                var length = pop();
                var element = switch (insn.value()) {
                    case 4, 8 -> Type.I8;
                    case 5, 9 -> Type.I16;
                    case 6 -> Type.F32;
                    case 7 -> Type.F64;
                    case 10 -> Type.I32;
                    default -> Type.I64;
                };
                push(localArray(AddressSpace.PRIVATE, element, length));
            }
            case ARRAYLENGTH -> {
                var array = pop();
                if (array instanceof Inst inst && inst.opcode() instanceof Opcode.Alloca(var space, var element, var count)) {
                    push(b.constInt(Type.I32, count));
                } else if (array instanceof Inst inst && LocalArrays.isPending(inst)) {
                    push(inst.operand(0));
                } else if (array.type() instanceof Type.PointerType) {
                    // Possibly a block argument SSA construction has not removed yet: resolved after it.
                    push(b.intrinsic(LocalArrays.LENGTH, Type.I32, List.of(array)));
                } else {
                    push(b.arrayLength(array));
                }
            }
            case CHECKCAST -> {
            }
            default -> {
                if (op >= ICONST_M1 && op <= ICONST_5) {
                    push(b.constInt(Type.I32, op - ICONST_M1 - 1));
                } else if (op == LCONST_0 || op == LCONST_1) {
                    push(b.constInt(Type.I64, op - LCONST_0));
                } else if (op >= FCONST_0 && op <= FCONST_2) {
                    push(b.constFloat(Type.F32, op - FCONST_0));
                } else if (op == DCONST_0 || op == DCONST_1) {
                    push(b.constFloat(Type.F64, op - DCONST_0));
                } else if (op >= ILOAD && op <= ALOAD) {
                    load(block, insn.index(), op - ILOAD, insn.bci());
                } else if (op >= ILOAD_0 && op <= ALOAD_3) {
                    load(block, (op - ILOAD_0) % 4, (op - ILOAD_0) / 4, insn.bci());
                } else if (op >= ISTORE && op <= ASTORE) {
                    store(block, insn.index(), "IJFDA".charAt(op - ISTORE), insn.bci(), insns.get(i + 1 < insns.size() ? i + 1 : i).bci());
                } else if (op >= ISTORE_0 && op <= ASTORE_3) {
                    store(block, (op - ISTORE_0) % 4, "IJFDA".charAt((op - ISTORE_0) / 4), insn.bci(), insns.get(i + 1 < insns.size() ? i + 1 : i).bci());
                } else if (op >= IALOAD && op <= SALOAD) {
                    arrayLoad(op - IALOAD, insn.bci());
                } else if (op >= IASTORE && op <= SASTORE) {
                    arrayStore(op - IASTORE, insn.bci());
                } else if (op >= IADD && op <= DREM) {
                    arithmetic(op);
                } else if (op >= INEG && op <= DNEG) {
                    var v = pop();
                    push(b.unary(v.type().isFloat() ? UnaryOp.FNEG : UnaryOp.NEG, v));
                } else if (op >= ISHL && op <= LXOR) {
                    bitwise(op);
                } else if (op >= IFEQ && op <= IFLE) {
                    var v = pop();
                    conditionalBranch(block, b.icmp(intPredicate(op - IFEQ), v, b.constInt(Type.I32, 0)), insn);
                } else if (op >= IF_ICMPEQ && op <= IF_ICMPLE) {
                    var rhs = pop();
                    var lhs = pop();
                    conditionalBranch(block, b.icmp(intPredicate(op - IF_ICMPEQ), lhs, rhs), insn);
                } else if (op >= I2L && op <= I2S) {
                    convert(op, insn.bci());
                } else if (op == NEW && classFile.constants().className(insn.index()).equals(Bindings.HALF_FLOAT)) {
                    // Placeholder until the constructor call gives the value (see Bindings.ValueInit).
                    push(new Inst(new Opcode.Intrinsic("tc.uninitialised"), Type.F16));
                } else if (op == NEW && classFile.constants().className(insn.index()).equals(Bindings.ATOMIC_INTEGER)) {
                    push(new Inst(new Opcode.Intrinsic("tc.uninitialised"), new Type.ObjectType("AtomicInteger")));
                } else if (op == NEW && Bindings.type("L" + classFile.constants().className(insn.index()) + ";") instanceof Type.VectorType vec) {
                    push(new Inst(new Opcode.Intrinsic("tc.uninitialised"), vec)); // a vector value, made by its constructor
                } else if (op == NEW) {
                    throw error("TC-E1011", "allocating an object (" + classFile.constants().className(insn.index()).replace('/', '.') + ") is not supported in kernels", insn.bci());
                } else if (op >= IRETURN && op <= DRETURN || op == ARETURN && stack.peek() != null
                        && (stack.peek().type().equals(Type.F16) || stack.peek().type() instanceof Type.VectorType)) {
                    b.ret(pop());
                } else {
                    throw error("TC-E1002", "bytecode '" + Bytecode.name(op) + "' is not supported in kernels", insn.bci());
                }
            }
        }
        return i;
    }

    private void conditionalBranch(Block block, Value condition, Insn insn) {
        var successors = successorsOf(block);
        leaveTo(block, successors);
        builder.condBr(condition, successors.get(0), List.of(), successors.get(1), List.of());
    }

    private void load(Block block, int slot, int kindIndex, int bci) {
        char kind = "IJFDA".charAt(kindIndex);
        var name = localName(slot, bci);
        push(readVariable(localKey(slot, kind), block, kind == 'A' ? new Type.ObjectType("?") : primitive(kind), name));
    }

    /** Slots are keyed by the bytecode's kind, as the JVM types them, whatever value TC made (a boxed Integer is an i32). */
    private void store(Block block, int slot, char kind, int bci, int nextBci) {
        var value = pop();
        nameLocal(value, slot, nextBci);
        writeVariable(localKey(slot, kind), block, value);
    }

    private void nameLocal(Value value, int slot, int bci) {
        if (value.name().isEmpty()) {
            code.localAt(slot, bci).ifPresent(v -> value.setName(v.name()));
        }
    }

    private String localName(int slot, int bci) {
        return code.localAt(slot, bci).map(ClassFile.LocalVariable::name).orElse("");
    }

    private void arrayLoad(int kind, int bci) {
        var index = pop();
        var array = pop();
        if (array.type() instanceof Type.VectorType(var element, var length)) {
            // A tensor-core fragment: lanes live in registers, so only a constant index can be read.
            // Only accumulators (C/D) have a per-lane element layout; A/B operand fragments are packed registers.
            if (array instanceof Inst producer && producer.opcode() instanceof Opcode.Intrinsic(var name) && (name.contains(".load_a") || name.contains(".load_b"))) {
                throw error("TC-E1010", "elements of an MMA operand fragment (A or B) cannot be read; only accumulator fragments can", bci);
            }
            if (!(index instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var lane)) || lane < 0 || lane >= length) {
                throw error("TC-E1010", "fragment elements can only be read at a constant index within the fragment", bci);
            }
            push(builder.intrinsic("tornado.vector.lane." + lane, element, List.of(array)));
            return;
        }
        // kind: 0 i, 1 l, 2 f, 3 d, 4 a, 5 b(byte/boolean), 6 c, 7 s
        var element = switch (kind) {
            case 0 -> Type.I32;
            case 1 -> Type.I64;
            case 2 -> Type.F32;
            case 3 -> Type.F64;
            case 5 -> Type.I8;
            case 6, 7 -> Type.I16;
            default -> halfElements(array).orElseThrow(() -> error("TC-E1002", "arrays of references are not supported in kernels", bci));
        };
        Value loaded = array.type() instanceof Type.PointerType ? pointerLoad(array, index, element) : builder.arrayLoad(array, index);
        push(switch (kind) {
            case 5, 7 -> builder.convert(ConvertOp.SEXT, loaded, Type.I32);
            case 6 -> builder.convert(ConvertOp.ZEXT, loaded, Type.I32);
            default -> loaded;
        });
    }

    private void arrayStore(int kind, int bci) {
        var value = pop();
        var index = pop();
        var array = pop();
        var element = switch (kind) {
            case 5 -> Type.I8;
            case 6, 7 -> Type.I16;
            case 4 -> halfElements(array).orElseThrow(() -> error("TC-E1002", "arrays of references are not supported in kernels", bci));
            default -> value.type();
        };
        var stored = element.equals(value.type()) ? value : builder.convert(ConvertOp.TRUNC, value, element);
        if (array.type() instanceof Type.PointerType) {
            builder.store(elementPointer(array, index, element), stored);
        } else {
            builder.arrayStore(array, index, stored);
        }
    }

    /**
     * Value semantics for vector objects: after {@code v.setX(s)} (or a constructor), every local
     * slot and stack entry that held the old value holds the new one.
     */
    private void rebind(Value old, Value replacement) {
        var rebuilt = new ArrayDeque<Value>();
        for (var v : stack) {
            rebuilt.addLast(v == old ? replacement : v);
        }
        stack = rebuilt;
        if (old instanceof Inst i && i.opcode() instanceof Opcode.Intrinsic(var name) && name.equals("tc.uninitialised")) {
            return; // a fresh object is only on the stack
        }
        var current = builder.block();
        for (int slot = 0; slot < code.maxLocals(); slot++) {
            var key = localKey(slot, 'A');
            if (definedSomewhere(key) && readVariable(key, current, old.type(), "") == old) {
                writeVariable(key, current, replacement);
            }
        }
    }

    private boolean definedSomewhere(String key) {
        for (var defs : currentDef.values()) {
            if (defs.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private Value narrow(Value v, Type element) {
        if (v.type().equals(element)) {
            return v;
        }
        return element.equals(Type.I8) || element.equals(Type.I16) ? builder.convert(ConvertOp.TRUNC, v, element) : v;
    }

    private Value zero(Type element) {
        return element.isFloat() ? builder.constFloat(element, 0.0) : builder.constInt(element, 0);
    }

    private int constantLane(Value index, int bci) {
        if (index instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var l)) {
            return (int) l;
        }
        throw error("TC-E1010", "vector lanes can only be accessed at a constant index", bci);
    }

    /** A local {@code HalfFloat[]} holds f16 values (HalfFloat is a value type): the only reference array kernels use. */
    private static Optional<Type> halfElements(Value array) {
        if (array.type() instanceof Type.ArrayType(var element) && element.equals(Type.F16)) {
            return Optional.of(Type.F16); // a HalfFloat[] parameter of an inlined helper
        }
        return array instanceof Inst inst && LocalArrays.element(inst).filter(Type.F16::equals).isPresent() ? Optional.of(Type.F16) : Optional.empty();
    }

    /** Local (shared or private) arrays are pointers without a header: element i is at i * size. */
    private Value pointerLoad(Value pointer, Value index, Type element) {
        return builder.load(elementPointer(pointer, index, element), element);
    }

    private Value elementPointer(Value pointer, Value index, Type element) {
        var wide = builder.convert(ConvertOp.SEXT, index, Type.I64);
        var offset = builder.binary(BinaryOp.MUL, wide, builder.constInt(Type.I64, sizeOf(element)));
        return builder.pointerOffset(pointer, offset);
    }

    private void arithmetic(int op) {
        var rhs = pop();
        var lhs = pop();
        int base = (op - IADD) / 4; // add, sub, mul, div, rem
        boolean isFloat = lhs.type().isFloat();
        var binary = switch (base) {
            case 0 -> isFloat ? BinaryOp.FADD : BinaryOp.ADD;
            case 1 -> isFloat ? BinaryOp.FSUB : BinaryOp.SUB;
            case 2 -> isFloat ? BinaryOp.FMUL : BinaryOp.MUL;
            case 3 -> isFloat ? BinaryOp.FDIV : BinaryOp.DIV;
            default -> isFloat ? BinaryOp.FREM : BinaryOp.REM;
        };
        push(builder.binary(binary, lhs, rhs));
    }

    private void bitwise(int op) {
        var rhs = pop();
        var lhs = pop();
        var binary = switch ((op - ISHL) / 2) {
            case 0 -> BinaryOp.SHL;
            case 1 -> BinaryOp.SHR;
            case 2 -> BinaryOp.USHR;
            case 3 -> BinaryOp.AND;
            case 4 -> BinaryOp.OR;
            default -> BinaryOp.XOR;
        };
        if ((binary == BinaryOp.SHL || binary == BinaryOp.SHR || binary == BinaryOp.USHR) && !rhs.type().equals(lhs.type())) {
            // Java long shifts take an int count.
            rhs = builder.convert(ConvertOp.SEXT, rhs, lhs.type());
        }
        push(builder.binary(binary, lhs, rhs));
    }

    private void convert(int op, int bci) {
        var v = pop();
        var b = builder;
        push(switch (Bytecode.name(op)) {
            case "i2l" -> b.convert(ConvertOp.SEXT, v, Type.I64);
            case "i2f" -> b.convert(ConvertOp.I2F, v, Type.F32);
            case "i2d", "l2d" -> b.convert(ConvertOp.I2F, v, Type.F64);
            case "l2i" -> b.convert(ConvertOp.TRUNC, v, Type.I32);
            case "l2f" -> b.convert(ConvertOp.I2F, v, Type.F32);
            case "f2i", "d2i" -> b.convert(ConvertOp.F2I_SAT, v, Type.I32);
            case "f2l", "d2l" -> b.convert(ConvertOp.F2I_SAT, v, Type.I64);
            case "f2d" -> b.convert(ConvertOp.FEXT, v, Type.F64);
            case "d2f" -> b.convert(ConvertOp.FTRUNC, v, Type.F32);
            case "i2b" -> b.convert(ConvertOp.SEXT, b.convert(ConvertOp.TRUNC, v, Type.I8), Type.I32);
            case "i2c" -> b.convert(ConvertOp.ZEXT, b.convert(ConvertOp.TRUNC, v, Type.I16), Type.I32);
            case "i2s" -> b.convert(ConvertOp.SEXT, b.convert(ConvertOp.TRUNC, v, Type.I16), Type.I32);
            default -> throw error("TC-E1002", "bytecode '" + Bytecode.name(op) + "' is not supported in kernels", bci);
        });
    }

    /** {@code lcmp}/{@code fcmp*}/{@code dcmp*} whose result is used as an int: -1, 0 or 1. */
    private Value threeWayCompare(int op, Value lhs, Value rhs) {
        var b = builder;
        boolean isFloat = op != LCMP;
        var lt = isFloat ? b.fcmp(op == FCMPG || op == DCMPG ? FloatPredicate.OLT : FloatPredicate.ULT, lhs, rhs) : b.icmp(IntPredicate.SLT, lhs, rhs);
        var eq = isFloat ? b.fcmp(FloatPredicate.OEQ, lhs, rhs) : b.icmp(IntPredicate.EQ, lhs, rhs);
        var minusOne = b.constInt(Type.I32, -1);
        var zero = b.constInt(Type.I32, 0);
        var one = b.constInt(Type.I32, 1);
        return b.select(lt, minusOne, b.select(eq, zero, one));
    }

    private void invoke(Insn insn) {
        var ref = classFile.constants().member(insn.index());
        var binding = Bindings.method(ref.owner(), ref.name(), ref.descriptor());
        int argCount = parameterDescriptors(ref.descriptor()).size() + (insn.opcode() == INVOKESTATIC ? 0 : 1);
        var args = new ArrayList<Value>();
        for (int k = 0; k < argCount; k++) {
            args.add(0, pop());
        }
        var returnType = Bindings.type(ref.descriptor().substring(ref.descriptor().indexOf(')') + 1));
        if (binding.isEmpty() || binding.get() instanceof Bindings.InlineJava) {
            // Static calls, and instance calls on objects: resolved against the static type (the
            // runtime checks object arguments have exactly their declared class) and inlined.
            if (insn.opcode() == INVOKESTATIC || (!args.isEmpty() && args.get(0).type() instanceof Type.ObjectType o && !o.className().equals("KernelContext"))
                    || binding.filter(Bindings.InlineJava.class::isInstance).isPresent()) {
                var callee = calleeName(ref.owner(), ref.name(), ref.descriptor());
                callees.put(callee, ref);
                var call = builder.call(callee, returnType, args);
                if (call.producesValue()) {
                    push(call);
                }
                return;
            }
            throw error("TC-E1003", "call to " + ref.owner().replace('/', '.') + "." + ref.name() + ref.descriptor() + " is not supported in kernels yet", insn.bci());
        }
        switch (binding.get()) {
            case Intrinsic(var name, var type) -> {
                var operands = insn.opcode() == INVOKESTATIC ? args : args.subList(1, args.size());
                var result = builder.intrinsic(name, type, operands);
                if (result.producesValue()) {
                    push(result);
                }
            }
            case ArrayGet g -> {
                // Sub-int elements widen to int on the JVM operand stack (char unsigned, others signed).
                var loaded = builder.arrayLoad(args.get(0), args.get(1));
                var element = loaded.type();
                push(element.equals(Type.I8) || element.equals(Type.I16) ? builder.convert(ref.descriptor().endsWith(")C") ? ConvertOp.ZEXT : ConvertOp.SEXT, loaded, Type.I32) : loaded);
            }
            case ArraySet s -> {
                var element = args.get(0).type() instanceof Type.ArrayType(var e) ? e : args.get(2).type();
                var value = args.get(2);
                builder.arrayStore(args.get(0), args.get(1), element.equals(value.type()) ? value : builder.convert(ConvertOp.TRUNC, value, element));
            }
            case ArraySize(var type) -> push(builder.arrayLength(args.get(0), type));
            case Bindings.Convert(var op, var result) -> push(builder.convert(op, args.get(args.size() - 1), result));
            case Bindings.ValueInit(var from, var op, var result) -> {
                // args.get(0) is the placeholder pushed by `new`: every copy of it (from `dup`) becomes the value.
                var arg = args.get(1);
                var narrowed = arg.type().equals(from) ? arg : builder.convert(ConvertOp.TRUNC, arg, from);
                var value = builder.convert(op, narrowed, result);
                var placeholder = args.get(0);
                var rebuilt = new ArrayDeque<Value>();
                for (var v : stack) {
                    rebuilt.addLast(v == placeholder ? value : v);
                }
                stack = rebuilt;
            }
            case Bindings.InlineJava j -> throw error("TC-E9002", "inline-Java binding reached the intrinsic switch", insn.bci());
            case Bindings.ConstantField c -> throw error("TC-E9002", "a constant field bound as a method", insn.bci());
            case Bindings.Atomic(var name, var value, var returnsPrevious) -> {
                // args: context, array, index, values... A local array is a pointer: pass the element's
                // address; a global array stays (array, index) until ABI lowering gives its address.
                var array = args.get(1);
                var operands = new ArrayList<Value>();
                if (array.type() instanceof Type.PointerType) {
                    operands.add(elementPointer(array, args.get(2), value));
                } else {
                    operands.add(array);
                    operands.add(args.get(2));
                }
                operands.addAll(args.subList(3, args.size()));
                var result = builder.intrinsic("tornado.atomic." + name, returnsPrevious ? value : Type.VOID, operands);
                if (returnsPrevious) {
                    push(result);
                }
            }
            case Bindings.ByteView(var type, var store) -> {
                if (store) {
                    builder.arrayStore(args.get(0), args.get(1), args.get(2));
                } else {
                    push(builder.arrayLoad(args.get(0), args.get(1), type));
                }
            }
            case Bindings.AtomicNew a -> {
                var initial = args.size() > 1 ? args.get(1) : builder.constInt(Type.I32, 0);
                rebind(args.get(0), builder.intrinsic("tornado.atomic_int.new", new Type.ObjectType("AtomicInteger"), List.of(initial)));
            }
            case Bindings.AtomicOp(var name, var delta) -> {
                var operands = new ArrayList<Value>(List.of(args.get(0)));
                operands.add(args.size() > 1 ? args.get(1) : builder.constInt(Type.I32, delta));
                var result = builder.intrinsic("tornado.atomic_int." + name, name.equals("set") ? Type.VOID : Type.I32, operands);
                if (result.producesValue()) {
                    push(result);
                }
            }
            case Bindings.VectorInit(var vec) -> {
                var element = vec.element();
                var lanes = new ArrayList<Value>();
                for (int l = 0; l < vec.length(); l++) {
                    lanes.add(args.size() > 1 ? narrow(args.get(1 + l), element) : zero(element));
                }
                var value = builder.intrinsic("tornado.vector.make", vec, lanes);
                rebind(args.get(0), value);
            }
            case Bindings.VectorGet(var lane) -> {
                var vector = args.get(0);
                int l = lane >= 0 ? lane : constantLane(args.get(1), insn.bci());
                var element = ((Type.VectorType) vector.type()).element();
                var value = builder.intrinsic("tornado.vector.lane." + l, element, List.of(vector));
                push(element.equals(Type.I8) || element.equals(Type.I16) ? builder.convert(ConvertOp.SEXT, value, Type.I32) : value);
            }
            case Bindings.VectorSet(var lane) -> {
                var vector = args.get(0);
                int l = lane >= 0 ? lane : constantLane(args.get(1), insn.bci());
                var element = ((Type.VectorType) vector.type()).element();
                var updated = builder.intrinsic("tornado.vector.insert." + l, vector.type(), List.of(vector, narrow(args.get(args.size() - 1), element)));
                rebind(vector, updated);
            }
            case Bindings.HalfBits h -> push(builder.convert(ConvertOp.SEXT, builder.convert(ConvertOp.BITCAST, args.get(0), Type.I16), Type.I32));
            case Bindings.HalfArith(var op) -> {
                var a = builder.convert(ConvertOp.FEXT, args.get(0), Type.F32);
                var c = builder.convert(ConvertOp.FEXT, args.get(1), Type.F32);
                push(builder.convert(ConvertOp.FTRUNC, builder.binary(op, a, c), Type.F16));
            }
            case SharedArray(var element) -> push(localArray(AddressSpace.SHARED, element, args.get(1)));
            case Identity i -> push(args.get(0));
        }
    }

    /**
     * A local or shared array. A constant length gives the allocation now; any other length (a scalar
     * argument, say) gives a pending allocation that value specialisation may make constant
     * ({@link LocalArrays} materialises it, or reports TC-E1004).
     */
    private Value localArray(AddressSpace space, Type element, Value length) {
        if (length instanceof Inst inst && inst.opcode() instanceof Opcode.ConstInt(var v) && v > 0) {
            return builder.alloca(space, element, (int) v);
        }
        return builder.intrinsic(LocalArrays.pendingName(space, element), new Type.PointerType(space), List.of(length));
    }

    // ---------------------------------------------------------------- helpers

    private void push(Value v) {
        stack.addLast(v);
    }

    private Value pop() {
        return stack.removeLast();
    }

    private static int category(Value v) {
        return v.type().equals(Type.I64) || v.type().equals(Type.F64) ? 2 : 1;
    }

    private static String localKey(int slot, char kind) {
        return "L" + slot + kind;
    }

    private static char descriptorKind(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'L', '[' -> 'A';
            case 'J' -> 'J';
            case 'F' -> 'F';
            case 'D' -> 'D';
            default -> 'I';
        };
    }

    private static Type primitive(char kind) {
        return switch (kind) {
            case 'I' -> Type.I32;
            case 'J' -> Type.I64;
            case 'F' -> Type.F32;
            default -> Type.F64;
        };
    }

    private static IntPredicate intPredicate(int condition) {
        return switch (condition) {
            case 0 -> IntPredicate.EQ;
            case 1 -> IntPredicate.NE;
            case 2 -> IntPredicate.SLT;
            case 3 -> IntPredicate.SGE;
            case 4 -> IntPredicate.SGT;
            default -> IntPredicate.SLE;
        };
    }

    /**
     * The predicate of {@code xcmpl/xcmpg} followed by {@code if<cond>}: {@code cmpl} yields -1 for
     * NaN, {@code cmpg} yields 1, which decides whether the fused comparison is ordered or unordered.
     */
    private static FloatPredicate floatPredicate(int condition, boolean nanIsGreater) {
        return switch (condition) {
            case 0 -> FloatPredicate.OEQ;
            case 1 -> FloatPredicate.UNE;
            case 2 -> nanIsGreater ? FloatPredicate.OLT : FloatPredicate.ULT;
            case 3 -> nanIsGreater ? FloatPredicate.UGE : FloatPredicate.OGE;
            case 4 -> nanIsGreater ? FloatPredicate.UGT : FloatPredicate.OGT;
            default -> nanIsGreater ? FloatPredicate.OLE : FloatPredicate.ULE;
        };
    }

    private static int sizeOf(Type type) {
        return switch (type.text()) {
            case "i8" -> 1;
            case "i16", "f16", "bf16" -> 2;
            case "i64", "f64" -> 8;
            default -> 4;
        };
    }

    static List<String> parameterDescriptors(String descriptor) {
        var result = new ArrayList<String>();
        int i = 1;
        while (descriptor.charAt(i) != ')') {
            int start = i;
            while (descriptor.charAt(i) == '[') {
                i++;
            }
            if (descriptor.charAt(i) == 'L') {
                i = descriptor.indexOf(';', i);
            }
            i++;
            result.add(descriptor.substring(start, i));
        }
        return result;
    }

    private FrontendError error(String code, String message, int bci) {
        return new FrontendError(new Diagnostic(code, message, method.name() + method.descriptor() + " at bci " + bci, new Loc(sourceFile, this.code.lineAt(bci), bci)));
    }
}
