/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import uk.ac.manchester.tornado.tc.frontend.SsaVariables.Local;
import uk.ac.manchester.tornado.tc.frontend.SsaVariables.StackSlot;
import uk.ac.manchester.tornado.tc.frontend.SsaVariables.VectorParam;
import uk.ac.manchester.tornado.tc.frontend.Bindings.SharedArray;
import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.Insn;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.TypeAnnotations;
import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.ApiTypes;
import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
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
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;

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
 * {@code E1006} read of an undefined local, {@code E1013} a printf or launch operand not written in the
 * call, {@code E1014} local memory passed to a launch; ErrorCodes lists them all.</p>
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

    private final Frontend.BytecodeSource source;
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

    // SSA construction (Braun et al.), over the bytecode CFG.
    private final SsaVariables ssa;
    private final Map<Block, Integer> entryDepth = new IdentityHashMap<>();
    private final Map<Block, List<Type>> entryStackTypes = new IdentityHashMap<>();

    private final Map<String, uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef> callees = new java.util.LinkedHashMap<>();
    private IrBuilder builder;
    private Deque<Value> stack;
    /**
     * Compile-time operands, as frontend-only intrinsics that the consuming call reads and that are
     * removed before the function leaves the frontend: a string constant (a Debug.printf format), the
     * method a DeviceKernel field names, and the Object[] of a varargs call with its element stores.
     */
    private static final String STRING = "tornado.string", DEVICE_KERNEL = "tornado.device_kernel", VARARGS = "tornado.varargs", VARARGS_SET = "tornado.varargs.set";
    /** Vector parameters of a kernel (hidden SSA variable → parameter): setters on them reach the host. */
    private final Map<VectorParam, BlockArg> vectorParams = new java.util.LinkedHashMap<>();
    /** Whether the method was compiled by kotlinc (see {@link Kotlin}). */
    private final boolean kotlin;
    /** Indices of Kotlin {@code parallelFor} loops, from {@code ParallelLoops.parallelIndex}. */
    private final Set<Value> kotlinParallelIndices = Collections.newSetFromMap(new IdentityHashMap<>());

    private Translator(Frontend.BytecodeSource source, ClassFile classFile, ClassFile.Method method, Function.Kind kind) {
        this(source, classFile, method, kind, null);
    }

    /** {@code name} overrides the function name (an inherited method is called by its subclass name). */
    private Translator(Frontend.BytecodeSource source, ClassFile classFile, ClassFile.Method method, Function.Kind kind, String name) {
        this.source = source;
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
        this.function = new Function(functionName, kind, kind == Function.Kind.KERNEL ? Bindings.type(returnDescriptor) : typeOf(returnDescriptor));
        this.kotlin = Kotlin.isKotlin(classFile);
        this.ssa = new SsaVariables(function, preds, (key, block) -> error("TC-E1006", "read of " + SsaVariables.describe(key) + " before it is assigned", insns.get(startIndex.getOrDefault(block, 0)).bci()));
    }

    static Function translate(Frontend.BytecodeSource source, ClassFile classFile, ClassFile.Method method) {
        return new Translator(source, classFile, method, Function.Kind.KERNEL).run();
    }

    /** The function and the static methods it calls (by TC function name), for transitive translation. */
    record Translated(Function function, Map<String, uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef> callees) {
    }

    static Translated translate(Frontend.BytecodeSource source, ClassFile classFile, ClassFile.Method method, Function.Kind kind) {
        var translator = new Translator(source, classFile, method, kind);
        return new Translated(translator.run(), translator.callees);
    }

    /** A device function translated under the name its callers use. */
    static Translated translateDevice(Frontend.BytecodeSource source, ClassFile classFile, ClassFile.Method method, String name) {
        var translator = new Translator(source, classFile, method, Function.Kind.DEVICE, name);
        return new Translated(translator.run(), translator.callees);
    }

    /**
     * The TC name of a Java method other than the kernel: the fully qualified owner, the name and the
     * parameter descriptor spelled as identifier characters ({@code pkg.Utils.max_II}), so methods of
     * same-named classes in different packages, and overloads, never share a name.
     */
    static String calleeName(String owner, String name, String descriptor) {
        var parameters = descriptor.substring(1, descriptor.indexOf(')')).replace('/', '_').replace(";", "").replace('[', 'A').replace('$', '_');
        return owner.replace('/', '.') + "." + name.replace('<', '_').replace('>', '_') + "_" + parameters;
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
            var self = entry.addArg(typeOf("L" + classFile.name() + ";"), "this");
            ssa.write(localKey(0, 'A'), entry, self);
            slot = 1;
        }
        builder = new IrBuilder(entry);
        for (var descriptor : params) {
            // Enum values are ordinals inside the kernel; a kernel's own parameters come from the host.
            var type = function.kind() == Function.Kind.KERNEL ? Bindings.type(descriptor) : typeOf(descriptor);
            var name = code.localAt(slot, 0).map(ClassFile.LocalVariable::name).orElse("arg" + slot);
            // A kernel's byte, boolean, char and short parameters arrive at their own width (the runtime sets
            // 1- and 2-byte kernel arguments, and OpenCL checks the size); the body sees Java's int.
            var narrow = function.kind() == Function.Kind.KERNEL ? narrowParameter(descriptor) : null;
            var arg = entry.addArg(narrow != null ? narrow : type, name);
            Value value = arg;
            if (narrow != null) {
                value = builder.convert(descriptor.equals("C") || descriptor.equals("Z") ? ConvertOp.ZEXT : ConvertOp.SEXT, arg, Type.I32);
                value.setDerivedName(name);
            }
            ssa.write(localKey(slot, descriptorKind(descriptor)), entry, value);
            if (type instanceof Type.VectorType) {
                // The object the caller passed: its value as setters change it. A kernel writes it back
                // at return; a device function may not change it (checked after translation).
                var key = new VectorParam(slot);
                ssa.write(key, entry, arg);
                vectorParams.put(key, arg);
            }
            slot += descriptor.equals("J") || descriptor.equals("D") ? 2 : 1;
        }
        ssa.seal(entry);
        var first = blockAt.get(0);
        builder.br(first, List.of());
        ssa.filled(entry);
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
        ssa.removeTrivialArguments();
        removeWriteOnlyObjects();
        removeCompileTimeOperands();
        checkVectorParamsUnchanged();
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

    /**
     * Whether instruction {@code index} is the {@code ifne} of an {@code assert}: javac tests the
     * class's synthetic {@code $assertionsDisabled} and jumps over the check when it is set. Assertions
     * are always disabled in kernels, so only the jump is kept and the check is never translated.
     */
    private boolean isAssertionGuard(int index) {
        if (index < 1 || insns.get(index).opcode() != IFNE || insns.get(index - 1).opcode() != GETSTATIC) {
            return false;
        }
        var ref = classFile.constants().member(insns.get(index - 1).index());
        return ref.name().equals("$assertionsDisabled") && ref.descriptor().equals("Z");
    }

    /**
     * {@code null} stored straight into a local declared as a TornadoVM vector ({@code Byte3 pixel =
     * null;} before a switch assigns it): vectors are values, so the local starts as a zero vector.
     * Java could only read it after an assignment (a read of null would throw).
     */
    private Optional<Value> nullVector(int index) {
        if (index + 2 >= insns.size()) {
            return Optional.empty();
        }
        var next = insns.get(index + 1);
        int slot = next.opcode() == ASTORE ? next.index() : next.opcode() >= ASTORE_0 && next.opcode() <= ASTORE_3 ? next.opcode() - ASTORE_0 : -1;
        if (slot < 0) {
            return Optional.empty();
        }
        var local = code.localAt(slot, insns.get(index + 2).bci());
        if (local.isEmpty() || !(Bindings.type(local.get().descriptor()) instanceof Type.VectorType(var element, var length))) {
            return Optional.empty();
        }
        var zero = element instanceof Type.FloatType ? builder.constFloat(element, 0) : builder.constInt(element, 0);
        return Optional.of(builder.intrinsic("tornado.vector.make", new Type.VectorType(element, length), java.util.Collections.nCopies(length, zero)));
    }

    /** The width a sub-int kernel parameter is passed at ({@code byte}, {@code boolean}: i8; {@code char}, {@code short}: i16), else null. */
    private static Type narrowParameter(String descriptor) {
        return switch (descriptor) {
            case "B", "Z" -> Type.I8;
            case "C", "S" -> Type.I16;
            default -> null;
        };
    }

    /** Bytecode successors of {@code block}, one entry per edge. */
    private List<Block> successorsOf(Block block) {
        var last = insns.get(lastIndex(block));
        if (isAssertionGuard(lastIndex(block))) {
            return List.of(blockAt.get(last.targets()[0]));
        }
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
        if (preds.get(block).stream().allMatch(ssa::isFilled)) {
            ssa.seal(block);
        }
        builder = new IrBuilder(block);
        stack = new ArrayDeque<>();
        int depth = entryDepth.getOrDefault(block, 0);
        var types = entryStackTypes.getOrDefault(block, List.of());
        for (int i = 0; i < depth; i++) {
            stack.addLast(ssa.read(new StackSlot(i), block, types.get(i), ""));
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
        ssa.filled(block);
        noteFilled(block);
    }

    /** Records the operand stack flowing into {@code successors} as SSA variables. */
    private void leaveTo(Block block, List<Block> successors) {
        int i = 0;
        var types = new ArrayList<Type>();
        for (var value : stack) {
            ssa.write(new StackSlot(i++), block, value);
            types.add(value.type());
        }
        for (var s : successors) {
            entryDepth.putIfAbsent(s, stack.size());
            entryStackTypes.putIfAbsent(s, types);
        }
    }

    private void noteFilled(Block block) {
        for (var s : block.successors()) {
            if (!ssa.isSealed(s) && preds.get(s).stream().allMatch(ssa::isFilled)) {
                ssa.seal(s);
            }
        }
    }

    // ---------------------------------------------------------------- SSA variables (Braun et al.)

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
        if (!kotlinParallelIndices.isEmpty()) {
            markKotlinParallelLoops();
        }
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
                if (!(ssa.keyOf(header.args().get(k)) instanceof Local(var slot, var kind))) {
                    continue;
                }
                if (parallel.stream().anyMatch(a -> a.slot() == slot && a.covers(bci))) {
                    header.addAttribute(Attribute.of("parallel", "iv", k));
                }
            }
        }
    }

    /** Kotlin only: tags the loop headers whose block argument is a {@code parallelFor} index. */
    private void markKotlinParallelLoops() {
        for (var header : blockAt.values()) {
            if (!function.blocks().contains(header)) {
                continue;
            }
            for (int k = 0; k < header.args().size(); k++) {
                if (kotlinParallelIndices.contains(header.args().get(k))) {
                    header.addAttribute(Attribute.of("parallel", "iv", k));
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
            case ACONST_NULL -> push(nullVector(i).orElseThrow(() -> error("TC-E1002", "null references are not supported in kernels", insn.bci())));
            case BIPUSH, SIPUSH -> push(b.constInt(Type.I32, insn.index()));
            case LDC, LDC_W, LDC2_W -> push(switch (classFile.constants().loadable(insn.index())) {
                case Integer v -> b.constInt(Type.I32, v);
                case Float v -> b.constFloat(Type.F32, v);
                case Long v -> b.constInt(Type.I64, v);
                case Double v -> b.constFloat(Type.F64, v);
                case String v -> b.intrinsic(STRING, Immediates.of("value", v), ApiTypes.STRING, List.of()); // a Debug.printf format
                default -> throw error("TC-E1002", "string constants are not supported in kernels", insn.bci());
            });
            case IINC -> {
                var key = localKey(insn.index(), 'I');
                var current = ssa.read(key, block, Type.I32, localName(insn.index(), insn.bci()));
                var sum = b.add(current, b.constInt(Type.I32, insn.value()));
                nameLocal(sum, insn.index(), insn.bci());
                ssa.write(key, block, sum);
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
            case IF_ACMPEQ, IF_ACMPNE -> {
                var rhs = pop();
                var lhs = pop();
                // Enum constants are ordinals: comparing them is comparing integers.
                if (!lhs.type().equals(Type.I32) || !rhs.type().equals(Type.I32)) {
                    throw error("TC-E1002", "reference comparisons are not supported in kernels", insn.bci());
                }
                conditionalBranch(block, b.icmp(op == IF_ACMPEQ ? IntPredicate.EQ : IntPredicate.NE, lhs, rhs), insn);
            }
            case IFNULL, IFNONNULL -> throw error("TC-E1002", "reference comparisons are not supported in kernels", insn.bci());
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
            case RETURN -> {
                vectorParams.forEach((key, param) -> {
                    var current = ssa.read(key, block, param.type(), param.name());
                    if (current != param) {
                        // Kernel: write the new value back. Device function: a marker checked once the
                        // trivial block arguments are gone (the value may still collapse to the parameter).
                        b.intrinsic(function.kind() == Function.Kind.KERNEL ? "tornado.vector.store_param" : PARAM_UNCHANGED, Type.VOID, List.of(param, current));
                    }
                });
                b.ret();
            }
            case GETFIELD, GETSTATIC -> {
                var ref = classFile.constants().member(insn.index());
                Value receiver = op == GETFIELD ? pop() : null;
                if (isWriteOnly(receiver)) {
                    throw error("TC-E1011", "allocating an object (" + receiver.type().text() + ") is not supported in kernels: only its constructor and setters may use it", insn.bci());
                }
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
                var enumInfo = source.enumInfo(ref.owner());
                if (op == GETSTATIC && enumInfo.isPresent() && enumInfo.get().constants().contains(ref.name())) {
                    push(b.constInt(Type.I32, enumInfo.get().constants().indexOf(ref.name())));
                    break;
                }
                if (op == GETFIELD && enumInfo.isPresent() && enumInfo.get().fields().containsKey(ref.name())) {
                    push(enumField(receiver, enumInfo.get().fields().get(ref.name()), Bindings.type(ref.descriptor())));
                    break;
                }
                if (op == GETSTATIC && ref.name().equals("$assertionsDisabled") && ref.descriptor().equals("Z")) {
                    push(b.constInt(Type.I32, 1)); // assertions are disabled in kernels (see isAssertionGuard)
                    break;
                }
                if (op == GETSTATIC && ref.descriptor().equals("L" + Bindings.DEVICE_KERNEL + ";")) {
                    var child = source.deviceKernel(ref.owner(), ref.name()).orElseThrow(() -> error("TC-E1013", "the DeviceKernel " + ref.owner().replace('/', '.') + "." + ref.name()
                            + " cannot be resolved: KernelContext.launch needs a static final field, e.g. static final DeviceKernel CHILD = DeviceKernel.of(MyKernels::child)", insn.bci()));
                    push(b.intrinsic(DEVICE_KERNEL, Immediates.of("owner", child.owner(), "name", child.name(), "descriptor", child.descriptor()), ApiTypes.DEVICE_KERNEL, List.of()));
                    break;
                }
                if (op == GETSTATIC && Bindings.field(ref.owner(), ref.name()).isEmpty()) {
                    var constant = source.staticFinal(ref.owner(), ref.name());
                    if (constant.isPresent()) {
                        push(switch (ref.descriptor().charAt(0)) {
                            case 'J' -> b.constInt(Type.I64, constant.get().longValue());
                            case 'F' -> b.constFloat(Type.F32, constant.get().floatValue());
                            case 'D' -> b.constFloat(Type.F64, constant.get().doubleValue());
                            default -> b.constInt(Type.I32, constant.get().intValue());
                        });
                        break;
                    }
                }
                var binding = Bindings.field(ref.owner(), ref.name()).orElseThrow(() -> error("TC-E1003", "field " + ref.owner().replace('/', '.') + "." + ref.name() + " is not supported in kernels yet", insn.bci()));
                if (binding instanceof Intrinsic(var name, var type, var immediates)) {
                    push(b.intrinsic(name, immediates, type, List.of()));
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
            case ANEWARRAY -> {
                // Only the Object[] of a varargs call (KernelContext.launch arguments) exists in a kernel.
                var length = pop();
                if (!classFile.constants().className(insn.index()).equals("java/lang/Object") || !(length instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var n))) {
                    throw error("TC-E1002", "arrays of references are not supported in kernels", insn.bci());
                }
                push(b.intrinsic(VARARGS, Immediates.of("length", n), ApiTypes.OBJECT_ARRAY, List.of()));
            }
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
                } else if (op == NEW && Bindings.collection(classFile.constants().className(insn.index())).isPresent()) {
                    // A vector collection made in the kernel: a private array, once its constructor runs.
                    push(new Inst(new Opcode.Intrinsic("tc.uninitialised"), Bindings.type("L" + classFile.constants().className(insn.index()) + ";")));
                } else if (op == NEW && classFile.constants().className(insn.index()).equals(Bindings.ATOMIC_INTEGER)) {
                    push(new Inst(new Opcode.Intrinsic("tc.uninitialised"), ApiTypes.ATOMIC_INTEGER));
                } else if (op == NEW && Bindings.type("L" + classFile.constants().className(insn.index()) + ";") instanceof Type.VectorType vec) {
                    push(new Inst(new Opcode.Intrinsic("tc.uninitialised"), vec)); // a vector value, made by its constructor
                } else if (op == NEW && classFile.constants().className(insn.index()).startsWith(WRITE_ONLY_PACKAGE)) {
                    // An API matrix made in the kernel: only its constructor and setters may use it
                    // (see writeOnly), so it is never observed and is dropped with them.
                    push(b.intrinsic(WRITE_ONLY, new Type.ObjectType(classFile.constants().className(insn.index())), List.of()));
                } else if (op == NEW) {
                    throw error("TC-E1011", "allocating an object (" + classFile.constants().className(insn.index()).replace('/', '.') + ") is not supported in kernels", insn.bci());
                } else if (op >= IRETURN && op <= DRETURN || op == ARETURN && !stack.isEmpty()) {
                    // A device method may return any value it has (arrays, objects, f16, vectors, enum
                    // ordinals): it is inlined. Kernels return void; the verifier checks the type.
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
        if (successors.size() == 1) {
            builder.br(successors.get(0), List.of()); // an assertion guard: assertions are disabled
            return;
        }
        builder.condBr(condition, successors.get(0), List.of(), successors.get(1), List.of());
    }

    private void load(Block block, int slot, int kindIndex, int bci) {
        char kind = "IJFDA".charAt(kindIndex);
        var name = localName(slot, bci);
        push(ssa.read(localKey(slot, kind), block, kind == 'A' ? ApiTypes.UNRESOLVED : primitive(kind), name));
    }

    /** Slots are keyed by the bytecode's kind, as the JVM types them, whatever value TC made (a boxed Integer is an i32). */
    private void store(Block block, int slot, char kind, int bci, int nextBci) {
        var value = pop();
        nameLocal(value, slot, nextBci);
        ssa.write(localKey(slot, kind), block, value);
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
            push(builder.intrinsic("tornado.vector.lane", Immediates.of("lane", lane), element, List.of(array)));
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
        if (ApiTypes.OBJECT_ARRAY.equals(array.type())) {
            var varargs = compileTime(array, VARARGS, "the arguments of a varargs call must be passed directly", bci);
            builder.intrinsic(VARARGS_SET, Immediates.of("index", constantLane(index, bci)), Type.VOID, List.of(varargs, value));
            return;
        }
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
        for (var key : vectorParams.keySet()) {
            if (ssa.read(key, current, old.type(), "") == old) {
                ssa.write(key, current, replacement);
            }
        }
        for (int slot = 0; slot < code.maxLocals(); slot++) {
            var key = localKey(slot, 'A');
            if (ssa.definedSomewhere(key) && ssa.read(key, current, old.type(), "") == old) {
                ssa.write(key, current, replacement);
            }
        }
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
        var lane = Constants.integer(throughTrivialMerges(index));
        if (lane.isPresent()) {
            return (int) lane.getAsLong();
        }
        throw error("TC-E1010", "vector lanes can only be accessed at a constant index", bci);
    }

    /**
     * {@code v}, looking through block arguments whose incoming values are all one value: operand-stack
     * slots carried across a branch such as {@code c ? x : y} (trivial arguments are only removed after
     * translation).
     */
    private Value throughTrivialMerges(Value v) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Value, Boolean>());
        while (v instanceof BlockArg arg && seen.add(arg)) {
            if (!ssa.isSealed(arg.block())) {
                return v; // a loop header still being built: its incoming values are not all known yet
            }
            Value same = null;
            for (var incoming : ssa.incomingValues(arg.block(), arg.block().args().indexOf(arg))) {
                if (incoming != arg && incoming != same) {
                    if (same != null) {
                        return v;
                    }
                    same = incoming;
                }
            }
            if (same == null) {
                return v;
            }
            v = same;
        }
        return v;
    }

    /**
     * The instruction defining compile-time operand {@code v}, a {@code intrinsic} reached directly or
     * through trivial merges; anything else is TC-E1003 with {@code what}.
     */
    private Inst compileTime(Value value, String intrinsic, String what, int bci) {
        var v = throughTrivialMerges(value);
        if (v instanceof Inst i && i.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(intrinsic)) {
            return i;
        }
        throw error("TC-E1013", what, bci);
    }

    /** The elements stored into a varargs array, by index; every element must be set. */
    private List<Value> varargValues(Inst array, String call, int bci) {
        var values = new Value[array.immediates().requireInt("length")];
        for (var block : function.blocks()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(VARARGS_SET) && inst.operand(0) == array) {
                    values[inst.immediates().requireInt("index")] = inst.operand(1);
                }
            }
        }
        for (var v : values) {
            if (v == null) {
                throw error("TC-E1013", "an argument of " + call + " is not set", bci);
            }
        }
        return List.of(values);
    }

    /**
     * Removes the compile-time operands once their calls have read them; one still used is a string,
     * DeviceKernel or Object[] that reached something other than Debug.printf or KernelContext.launch.
     */
    private void removeCompileTimeOperands() {
        var all = function.blocks().stream().flatMap(b -> b.insts().stream()).toList();
        for (var inst : all) {
            if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(VARARGS_SET)) {
                inst.erase();
            }
        }
        for (var inst : all) {
            if (inst.opcode() instanceof Opcode.Intrinsic(var name) && (name.equals(STRING) || name.equals(DEVICE_KERNEL) || name.equals(VARARGS)) && inst.block().isPresent()) {
                if (inst.hasUses()) {
                    throw new FrontendError(new Diagnostic("TC-E1013", "a " + inst.type().text().replace("obj<", "").replace(">", "")
                            + " can only be passed to Debug.printf or KernelContext.launch", "", inst.loc()));
                }
                inst.erase();
            }
        }
    }

    /**
     * {@code KernelContext.launch(child, [mode,] global..., local..., args...)}: a launch of the static
     * method the DeviceKernel names, as {@code tornado.device_launch<callee, mode>} with the
     * three global sizes, the three local sizes and the child's arguments (without its KernelContext).
     */
    private void deviceLaunch(uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef ref, List<Value> args, int bci) {
        var kernel = compileTime(args.get(1), DEVICE_KERNEL, "the DeviceKernel passed to KernelContext.launch must be read from a static final field", bci);
        var child = new Frontend.MethodRef(kernel.immediates().requireString("owner"), kernel.immediates().requireString("name"), kernel.immediates().requireString("descriptor"));
        int next = 2;
        var mode = "default";
        if (ref.descriptor().contains(Bindings.DEVICE_LAUNCH_MODE)) {
            if (!(args.get(next++) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var m))) {
                throw error("TC-E1013", "the DeviceLaunchMode of KernelContext.launch must be a constant", bci);
            }
            // By the constant's name, not its position: DEFAULT, TAIL, FIRE_AND_FORGET.
            var constants = source.enumInfo(Bindings.DEVICE_LAUNCH_MODE).orElseThrow(() -> error("TC-E1007", "DeviceLaunchMode is not on the class path of the task", bci)).constants();
            if (m < 0 || m >= constants.size()) {
                throw error("TC-E1013", "the DeviceLaunchMode of KernelContext.launch must be one of its constants", bci);
            }
            mode = constants.get((int) m).toLowerCase(java.util.Locale.ROOT);
        }
        var one = builder.constInt(Type.I32, 1);
        var operands = new ArrayList<Value>();
        if (ref.name().equals("launch3D")) {
            for (int k = 0; k < 6; k++) {
                operands.add(args.get(next++));
            }
        } else {
            operands.addAll(List.of(args.get(next), one, one, args.get(next + 1), one, one));
            next += 2;
        }
        var values = varargValues(compileTime(args.get(next), VARARGS, "the arguments of KernelContext.launch must be passed directly (as varargs)", bci), "KernelContext.launch", bci);
        for (var v : values) {
            if (v.type() instanceof Type.PointerType) {
                throw error("TC-E1014", "a local-memory array cannot be passed to KernelContext.launch: it belongs to the launching block", bci);
            }
            operands.add(v);
        }
        builder.intrinsic("tornado.device_launch", Immediates.of("mode", mode, "callee", child.owner().replace('/', '.') + "." + child.name(), "descriptor", child.descriptor()), Type.VOID, operands);
    }

    private static final String PARAM_UNCHANGED = "tc.vector_param_unchanged";

    /**
     * Java passes a vector object by reference: a helper's {@code v.setX(..)} changes the caller's
     * vector. TC's vectors are values, so a device function that changes a vector parameter is
     * refused (TC-E1012) rather than compiled with the change lost.
     */
    private void checkVectorParamsUnchanged() {
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (inst.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(PARAM_UNCHANGED)) {
                    if (inst.operand(1) != inst.operand(0)) {
                        throw error("TC-E1012", "this method changes its vector argument " + inst.operand(0).name()
                                + " (Java passes vectors by reference); return the changed vector instead", inst.loc().bci());
                    }
                    inst.erase();
                }
            }
        }
    }

    /** The descriptor's type, with enum classes as their ordinal ({@code i32}). */
    private Type typeOf(String descriptor) {
        if (descriptor.startsWith("L") && source.enumInfo(descriptor.substring(1, descriptor.length() - 1)).isPresent()) {
            return Type.I32;
        }
        return Bindings.type(descriptor);
    }

    /** A primitive field of an enum value (an ordinal): the constants' values, selected by ordinal. */
    private Value enumField(Value ordinal, List<Number> values, Type type) {
        Value result = constantOf(type, values.get(values.size() - 1));
        for (int k = values.size() - 2; k >= 0; k--) {
            if (values.get(k).equals(values.get(values.size() - 1)) && result instanceof Inst c && (c.opcode() instanceof Opcode.ConstInt || c.opcode() instanceof Opcode.ConstFloat)) {
                continue;
            }
            var is = builder.icmp(IntPredicate.EQ, ordinal, builder.constInt(Type.I32, k));
            result = builder.select(is, constantOf(type, values.get(k)), result);
        }
        return result;
    }

    private Value constantOf(Type type, Number v) {
        return type.isFloat() ? builder.constFloat(type, v.doubleValue()) : builder.constInt(type, v.longValue());
    }

    /**
     * Local arrays of value types are the only reference arrays kernels use: {@code HalfFloat[]}
     * holds f16 values, {@code Half2[]} half2 vectors.
     */
    private Optional<Type> halfElements(Value array) {
        if (array.type() instanceof Type.ArrayType(var element) && element.equals(Type.F16)) {
            return Optional.of(Type.F16); // a HalfFloat[] parameter of an inlined helper
        }
        return localValueArrayElement(array, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * Whether {@code v} is a local {@code HalfFloat[]}. A block argument (the array read after an
     * if or in a loop) is one when a definition reaching it on an incoming path is: block arguments
     * only collapse to their single incoming value once translation has finished.
     */
    private Optional<Type> localValueArrayElement(Value v, Set<Value> visited) {
        if (!visited.add(v)) {
            return Optional.empty();
        }
        if (v instanceof Inst inst) {
            return LocalArrays.element(inst).filter(e -> e.equals(Type.F16) || e instanceof Type.VectorType);
        }
        if (!(v instanceof BlockArg arg) || ssa.keyOf(arg) == null) {
            return Optional.empty();
        }
        var key = ssa.keyOf(arg);
        for (var pred : preds.getOrDefault(arg.block(), List.of())) {
            var reaching = ssa.reachingDefinition(key, pred, false, Collections.newSetFromMap(new IdentityHashMap<>()));
            var element = reaching == null ? Optional.<Type>empty() : localValueArrayElement(reaching, visited);
            if (element.isPresent()) {
                return element;
            }
        }
        return Optional.empty();
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

    private static final String WRITE_ONLY_PACKAGE = "uk/ac/manchester/tornado/api/types/matrix/";
    private static final String WRITE_ONLY = "tc.write_only_object";

    private static boolean isWriteOnly(Value value) {
        return value instanceof Inst i && i.opcode() instanceof Opcode.Intrinsic(var name) && name.equals(WRITE_ONLY);
    }

    /**
     * A matrix the kernel allocates and only passes to its constructor and to void methods taking
     * primitives (set, fill, clear) changes nothing anyone reads: the allocation and those calls are
     * dropped (dead-allocation elimination, as escape analysis would do). Any other use observes it.
     */
    private void removeWriteOnlyObjects() {
        boolean removed = false;
        for (var block : function.blocks()) {
            for (var object : List.copyOf(block.insts())) {
                if (!isWriteOnly(object)) {
                    continue;
                }
                for (var user : object.users()) {
                    boolean mutator = user.opcode() instanceof Opcode.Call(var callee) && user.type().equals(Type.VOID) && user.operand(0) == object
                            && user.operands().stream().skip(1).allMatch(v -> v != object && (v.type().isInteger() || v.type().isFloat()))
                            && java.util.regex.Pattern.matches(".*\\.(_init_|set\\w*|fill|clear)_[^.]*", callee);
                    if (!mutator) {
                        throw error("TC-E1011", "allocating an object (" + object.type().text() + ") is not supported in kernels: only its constructor and setters may use it", user.loc().bci());
                    }
                }
                for (var user : List.copyOf(object.users())) {
                    user.erase();
                }
                object.erase();
                removed = true;
            }
        }
        if (removed) {
            // The dropped calls' bodies are no longer needed by the inliner.
            var called = new java.util.HashSet<String>();
            for (var block : function.blocks()) {
                for (var inst : block.insts()) {
                    if (inst.opcode() instanceof Opcode.Call(var callee)) {
                        called.add(callee);
                    }
                }
            }
            callees.keySet().retainAll(called);
        }
    }

    /** Kotlin only: a call {@link Kotlin} lowers; its result, if any, is pushed. */
    private boolean kotlinCall(uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef ref, List<Value> args) {
        if (Kotlin.isNullCheck(ref)) {
            return true;
        }
        if (Kotlin.isParallelIndex(ref)) {
            kotlinParallelIndices.add(args.get(0));
            push(args.get(0));
            return true;
        }
        if (args.size() == 1 && Kotlin.isNumberUnbox(ref, args.get(0).type())) {
            push(args.get(0));
            return true;
        }
        return false;
    }

    private void invoke(Insn insn) {
        var ref = classFile.constants().member(insn.index());
        var binding = Bindings.method(ref.owner(), ref.name(), ref.descriptor());
        int argCount = parameterDescriptors(ref.descriptor()).size() + (insn.opcode() == INVOKESTATIC ? 0 : 1);
        var args = new ArrayList<Value>();
        for (int k = 0; k < argCount; k++) {
            args.add(0, pop());
        }
        var returnType = typeOf(ref.descriptor().substring(ref.descriptor().indexOf(')') + 1));
        if (kotlin && kotlinCall(ref, args)) {
            return;
        }
        var enumOwner = source.enumInfo(ref.owner());
        if (enumOwner.isPresent() && ref.name().equals("ordinal") && ref.descriptor().equals("()I")) {
            push(args.get(0));
            return;
        }
        var collection = Bindings.collection(ref.owner());
        if (ref.name().equals("<init>") && ref.descriptor().equals("(I)V") && collection.isPresent()) {
            var c = collection.get();
            var n = args.get(1);
            var width = builder.constInt(Type.I32, c.width());
            var storage = localArray(AddressSpace.PRIVATE, c.element(), builder.binary(BinaryOp.MUL, n, width));
            rebind(args.get(0), builder.intrinsic(LocalCollections.NAME, args.get(0).type(), List.of(storage, n, width)));
            return;
        }
        if (ref.owner().equals(Bindings.DEBUG) && ref.name().equals("printf")) {
            var what = "Debug.printf needs a constant format string and its arguments passed directly";
            var format = compileTime(args.get(0), STRING, what, insn.bci()).immediates().requireString("value");
            var values = varargValues(compileTime(args.get(1), VARARGS, what, insn.bci()), "Debug.printf", insn.bci());
            // The format is kept as the Java string: each dialect spells (escapes) it itself.
            builder.intrinsic("tornado.printf", Immediates.of("format", format), Type.VOID, values);
            return;
        }
        if (ref.owner().equals(Bindings.KERNEL_CONTEXT) && (ref.name().equals("launch") || ref.name().equals("launch3D"))) {
            deviceLaunch(ref, args, insn.bci());
            return;
        }
        if (binding.isEmpty() || binding.get() instanceof Bindings.InlineJava) {
            // Static calls, and instance calls on objects: resolved against the static type (the
            // runtime checks object arguments have exactly their declared class) and inlined.
            if (insn.opcode() == INVOKESTATIC || (!args.isEmpty() && args.get(0).type() instanceof Type.ObjectType o && !o.equals(ApiTypes.KERNEL_CONTEXT)) || enumOwner.isPresent()
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
            case Intrinsic(var name, var type, var immediates) -> {
                var operands = insn.opcode() == INVOKESTATIC ? args : args.subList(1, args.size());
                var result = builder.intrinsic(name, immediates, type, operands);
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
                rebind(args.get(0), builder.intrinsic("tornado.atomic_int.new", ApiTypes.ATOMIC_INTEGER, List.of(initial)));
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
                var element = ((Type.VectorType) vector.type()).element();
                var known = lane >= 0 ? java.util.OptionalLong.of(lane) : Constants.integer(throughTrivialMerges(args.get(1)));
                // A lane by a loop index (Float8.add's loop) stays an operand until unrolling makes it a
                // constant; legality refuses one that is still variable after optimisation (TC-E1010).
                var value = known.isPresent() ? builder.intrinsic("tornado.vector.lane", Immediates.of("lane", known.getAsLong()), element, List.of(vector))
                        : builder.intrinsic("tornado.vector.lane_at", element, List.of(vector, args.get(1)));
                push(element.equals(Type.I8) || element.equals(Type.I16) ? builder.convert(ConvertOp.SEXT, value, Type.I32) : value);
            }
            case Bindings.ReceiverIntrinsic(var name, var type) -> {
                var result = builder.intrinsic(name, type, args);
                if (result.producesValue()) {
                    push(result);
                }
            }
            case Bindings.VectorWhole(var op) -> {
                var vector = args.get(0);
                var vec = (Type.VectorType) vector.type();
                switch (op) {
                    case "set" -> rebind(vector, args.get(1));
                    case "fill" -> rebind(vector, builder.intrinsic("tornado.vector.make", vec, java.util.Collections.nCopies(vec.length(), narrow(args.get(1), vec.element()))));
                    default -> push(builder.constInt(Type.I32, vec.length()));
                }
            }
            case Bindings.VectorSlice(var start, var count) -> {
                var vector = args.get(0);
                var element = ((Type.VectorType) vector.type()).element();
                var lanes = new java.util.ArrayList<Value>();
                for (int l = start; l < start + count; l++) {
                    lanes.add(builder.intrinsic("tornado.vector.lane", Immediates.of("lane", l), element, List.of(vector)));
                }
                push(builder.intrinsic("tornado.vector.make", new Type.VectorType(element, count), lanes));
            }
            case Bindings.VectorSet(var lane) -> {
                var vector = args.get(0);
                var element = ((Type.VectorType) vector.type()).element();
                var known = lane >= 0 ? java.util.OptionalLong.of(lane) : Constants.integer(throughTrivialMerges(args.get(1)));
                var value = narrow(args.get(args.size() - 1), element);
                var updated = known.isPresent() ? builder.intrinsic("tornado.vector.insert", Immediates.of("lane", known.getAsLong()), vector.type(), List.of(vector, value))
                        : builder.intrinsic("tornado.vector.insert_at", vector.type(), List.of(vector, args.get(1), value));
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
        return builder.intrinsic(LocalArrays.PENDING, LocalArrays.pendingImmediates(space, element), new Type.PointerType(space), List.of(length));
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

    private static Local localKey(int slot, char kind) {
        return new Local(slot, kind);
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
        if (type instanceof Type.VectorType(var element, var length)) {
            return sizeOf(element) * length;
        }
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
