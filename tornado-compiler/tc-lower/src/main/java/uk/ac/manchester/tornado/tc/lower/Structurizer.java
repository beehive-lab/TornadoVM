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
package uk.ac.manchester.tornado.tc.lower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Successor;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;

/**
 * Translates TC-SSA into TC-S: out-of-SSA translation plus control-flow structuring.
 *
 * <p><b>Structuring</b> follows the dominator-tree method of N. Ramsey, "Beyond Relooper: Recursive
 * Translation of Unstructured Control Flow to Structured Control Flow" (ICFP 2022). A block is
 * emitted inside the code of its immediate dominator. A child with two or more forward in-edges (a
 * merge node) is emitted after a {@link S.Labelled} block, and edges to it become breaks out of that
 * block; edges back to a loop header become continues. TC refinement: the children of a loop header
 * that lie outside its natural loop (loop exits) are placed after the {@link S.Loop}, so leaving a
 * loop is a plain break. Irreducible control flow is rejected with {@code TC-E3001}.</p>
 *
 * <p><b>Out of SSA</b>: block arguments become variables, assigned on each incoming edge as a
 * parallel copy (sequenced through temporaries when the copies interfere). Constants, allocation
 * addresses and pure single-use values in the same block are folded into expression trees; every
 * other value gets a variable assigned at its definition.</p>
 */
public final class Structurizer {

    /** Thrown when the control flow cannot be structured. */
    public static final class StructuringException extends RuntimeException {
        private final String code;

        StructuringException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Function function;
    private final DominatorTree dominators;
    private final IrPrinter names = new IrPrinter(false);
    private final Map<Block, Integer> rpo = new IdentityHashMap<>();
    private final Map<Block, Integer> forwardInEdges = new IdentityHashMap<>();
    private final Map<Block, Set<Block>> loops = new IdentityHashMap<>();
    private final Map<Value, S.Var> vars = new IdentityHashMap<>();
    /** Names given so far; target words are the emitter's concern (codegen Naming), not this pass's. */
    private final Set<String> usedNames = new HashSet<>();

    private final List<S.Var> locals = new ArrayList<>();
    private final Map<Value, BlockArg> coalesced = new IdentityHashMap<>();
    private final List<S.Alloca> allocas = new ArrayList<>();
    private final Map<Inst, S.Alloca> allocaByInst = new IdentityHashMap<>();
    private int temporaries;

    private Structurizer(Function function) {
        this.function = function;
        this.dominators = Analyses.dominators(function);
        names.printFunction(function);
    }

    public static S.Kernel structure(Function function) {
        return new Structurizer(function).run();
    }

    private S.Kernel run() {
        var order = dominators.reversePostorder();
        for (int i = 0; i < order.size(); i++) {
            rpo.put(order.get(i), i);
        }
        analyseEdges(order);
        var params = new ArrayList<S.Var>();
        for (var param : function.params()) {
            params.add(variable(param));
        }
        for (var block : order) {
            for (var inst : block.insts()) {
                coalesceTarget(inst).ifPresent(arg -> coalesced.put(inst, arg));
            }
        }
        for (var block : order) {
            for (var arg : block.args()) {
                if (block != function.entry() && !locals.contains(variable(arg))) {
                    locals.add(variable(arg));
                }
            }
            for (var inst : block.insts()) {
                if (inst.producesValue() && !isInline(inst) && coalesced.containsKey(inst)) {
                    continue; // shares the variable of the block argument it feeds
                }
                if (inst.opcode() instanceof Opcode.Alloca) {
                    var alloca = new S.Alloca(inst, unique(cName(names.name(inst))));
                    allocas.add(alloca);
                    allocaByInst.put(inst, alloca);
                } else if (inst.producesValue() && !isInline(inst)) {
                    locals.add(variable(inst));
                }
            }
        }
        var body = doTree(function.entry());
        var simplified = Simplifier.simplify(body);
        var elementTypes = new IdentityHashMap<S.Var, Type>();
        vars.forEach((value, var) -> elementTypeOf(value).ifPresent(type -> elementTypes.put(var, type)));
        return new S.Kernel(function, params, locals, allocas, simplified, elementTypes);
    }

    // ---------------------------------------------------------------- CFG facts

    private void analyseEdges(List<Block> order) {
        for (var block : order) {
            forwardInEdges.put(block, 0);
        }
        for (var block : order) {
            for (var target : block.successors()) {
                if (dominators.dominates(target, block)) {
                    loops.computeIfAbsent(target, _k -> Collections.newSetFromMap(new IdentityHashMap<>()));
                } else if (rpo.get(target) <= rpo.get(block)) {
                    throw new StructuringException("TC-E3001", "irreducible control flow in @" + function.name() + ": edge ^" + names.blockName(block) + " -> ^"
                            + names.blockName(target) + " re-enters a loop without passing its header");
                } else {
                    forwardInEdges.merge(target, 1, Integer::sum);
                }
            }
        }
        var preds = function.predecessors();
        for (var header : loops.keySet()) {
            var body = loops.get(header);
            body.add(header);
            var work = new ArrayDeque<Block>();
            for (var pred : preds.get(header)) {
                if (dominators.dominates(header, pred) && body.add(pred)) {
                    work.push(pred);
                }
            }
            while (!work.isEmpty()) {
                for (var pred : preds.get(work.pop())) {
                    if (dominators.isReachable(pred) && body.add(pred)) {
                        work.push(pred);
                    }
                }
            }
        }
    }

    private boolean isLoopHeader(Block block) {
        return loops.containsKey(block);
    }

    private boolean inLoop(Block header, Block block) {
        return loops.get(header).contains(block);
    }

    /** A block emitted after a labelled block (its edges become breaks) rather than inline. */
    private boolean isPlaced(Block block) {
        var parent = dominators.immediateDominator(block);
        if (parent.isEmpty()) {
            return false;
        }
        return forwardInEdges.get(block) >= 2 || (isLoopHeader(parent.get()) && !inLoop(parent.get(), block));
    }

    // ---------------------------------------------------------------- structuring

    private List<S.Stmt> doTree(Block block) {
        var placed = new ArrayList<Block>();
        for (var child : dominators.children(block)) {
            if (isPlaced(child)) {
                placed.add(child);
            }
        }
        placed.sort(Comparator.comparing((Block b) -> rpo.get(b)).reversed());
        if (isLoopHeader(block)) {
            var inside = placed.stream().filter(c -> inLoop(block, c)).toList();
            var outside = placed.stream().filter(c -> !inLoop(block, c)).toList();
            List<S.Stmt> loop = List.of(new S.Loop(label(block), nodeWithin(block, inside)));
            return wrap(outside, loop);
        }
        return nodeWithin(block, placed);
    }

    /** {@code following} sorted by descending reverse-postorder number. */
    private List<S.Stmt> nodeWithin(Block block, List<Block> following) {
        if (following.isEmpty()) {
            var code = new ArrayList<S.Stmt>(blockCode(block));
            code.addAll(terminator(block));
            return code;
        }
        var first = following.get(0);
        var result = new ArrayList<S.Stmt>();
        result.add(new S.Labelled(label(first), nodeWithin(block, following.subList(1, following.size()))));
        result.addAll(doTree(first));
        return result;
    }

    private List<S.Stmt> wrap(List<Block> following, List<S.Stmt> inner) {
        if (following.isEmpty()) {
            return inner;
        }
        var first = following.get(0);
        var result = new ArrayList<S.Stmt>();
        result.add(new S.Labelled(label(first), wrap(following.subList(1, following.size()), inner)));
        result.addAll(doTree(first));
        return result;
    }

    private List<S.Stmt> terminator(Block block) {
        var term = block.terminator().orElseThrow(() -> new StructuringException("TC-E3002", "block ^" + names.blockName(block) + " has no terminator"));
        return switch (term.opcode()) {
            case Opcode.Branch _b -> edge(block, term.successors().get(0));
            case Opcode.CondBranch _c -> List.of(new S.If(expr(term.operand(0)), edge(block, term.successors().get(0)), edge(block, term.successors().get(1))));
            case Opcode.Switch(var keys) -> {
                var cases = new ArrayList<S.Case>();
                for (int i = 0; i < keys.size(); i++) {
                    cases.add(new S.Case(keys.get(i), edge(block, term.successors().get(i))));
                }
                yield List.of(new S.Switch(expr(term.operand(0)), cases, edge(block, term.successors().get(keys.size()))));
            }
            case Opcode.Return _r -> List.of(new S.Return(term.operandCount() == 0 ? Optional.empty() : Optional.of(expr(term.operand(0)))));
            case Opcode.Unreachable _u -> List.of(new S.Unreachable());
            default -> throw new StructuringException("TC-E3002", "unexpected terminator " + term.opcode().mnemonic());
        };
    }

    /** Edge copies for the target's arguments, then the jump itself. */
    private List<S.Stmt> edge(Block from, Successor successor) {
        var target = successor.target();
        var code = new ArrayList<S.Stmt>(parallelCopy(target.args(), successor.args()));
        if (dominators.dominates(target, from)) {
            code.add(new S.Continue(label(target)));
        } else if (isPlaced(target)) {
            code.add(new S.Break(label(target)));
        } else {
            code.addAll(doTree(target));
        }
        return code;
    }

    /**
     * Sequences the copies of one edge so that no copy overwrites a variable another pending copy
     * still reads; a cycle (for example a swap) is broken by saving one variable in a temporary.
     */
    private List<S.Stmt> parallelCopy(List<BlockArg> targets, List<Value> sources) {
        record Copy(S.Var target, S.Expr source) {
        }
        var pending = new ArrayList<Copy>();
        for (int i = 0; i < targets.size(); i++) {
            var target = variable(targets.get(i));
            var source = expr(sources.get(i));
            if (!(source instanceof S.VarRef(var v) && v == target)) {
                pending.add(new Copy(target, source));
            }
        }
        var code = new ArrayList<S.Stmt>();
        while (!pending.isEmpty()) {
            Copy ready = null;
            for (var candidate : pending) {
                var readByOthers = pending.stream().anyMatch(other -> other != candidate && reads(other.source, candidate.target));
                if (!readByOthers) {
                    ready = candidate;
                    break;
                }
            }
            if (ready != null) {
                code.add(new S.Assign(ready.target, ready.source, Loc.UNKNOWN));
                pending.remove(ready);
                continue;
            }
            // Every pending target is still read elsewhere: save one in a temporary.
            var saved = pending.get(0).target;
            var temp = new S.Var(unique("tmp" + temporaries++), saved.type());
            locals.add(temp);
            code.add(new S.Assign(temp, new S.VarRef(saved), Loc.UNKNOWN));
            for (int i = 0; i < pending.size(); i++) {
                var copy = pending.get(i);
                pending.set(i, new Copy(copy.target, substitute(copy.source, saved, temp)));
            }
        }
        return code;
    }

    private static boolean reads(S.Expr expr, S.Var var) {
        var found = new HashSet<S.Var>();
        collectVars(expr, found);
        return found.contains(var);
    }

    private static S.Expr substitute(S.Expr expr, S.Var from, S.Var to) {
        return switch (expr) {
            case S.VarRef(var v) -> v == from ? new S.VarRef(to) : expr;
            case S.Op(var inst, var operands) -> new S.Op(inst, operands.stream().map(o -> substitute(o, from, to)).toList());
            case S.Const _c -> expr;
            case S.AllocaRef _a -> expr;
            case S.ElementAddress(var address, var base, var index, var element) ->
                new S.ElementAddress((S.Op) substitute(address, from, to), substitute(base, from, to), substitute(index, from, to), element);
        };
    }

    private static void collectVars(S.Expr expr, Set<S.Var> into) {
        switch (expr) {
            case S.VarRef(var v) -> into.add(v);
            case S.Op(var op, var operands) -> operands.forEach(o -> collectVars(o, into));
            case S.Const _c -> {
            }
            case S.AllocaRef _a -> {
            }
            case S.ElementAddress e -> collectVars(e.address(), into);
        }
    }

    // ---------------------------------------------------------------- straight-line code

    private List<S.Stmt> blockCode(Block block) {
        var code = new ArrayList<S.Stmt>();
        for (var inst : block.insts()) {
            if (inst.isTerminator() || isInline(inst) || inst.opcode() instanceof Opcode.Alloca) {
                continue;
            }
            var op = new S.Op(inst, inst.operands().stream().map(this::expr).toList());
            if (inst.producesValue()) {
                code.add(new S.Assign(variable(inst), op, inst.loc()));
            } else {
                code.add(new S.Effect(op, inst.loc()));
            }
        }
        return code;
    }

    /** Values printed in place instead of through a variable. */
    private boolean isInline(Inst inst) {
        var opcode = inst.opcode();
        if (inst.type() instanceof Type.TileType || inst.type() instanceof Type.ObjectType) {
            // Tiles and views are named values (their C++ types are long): one statement each, as on Graal.
            return false;
        }
        if (opcode instanceof Opcode.ConstInt || opcode instanceof Opcode.ConstFloat || opcode instanceof Opcode.Alloca) {
            return true;
        }
        if (inst.type().equals(Type.I1) && inst.users().size() == 1 && inst.users().get(0).isTerminator()
                && inst.users().get(0).block().equals(inst.block()) && inst.users().get(0).operand(0) == inst) {
            // A condition consumed only by its block's branch prints inside the `if`/`while`.
            return true;
        }
        if (opcode.isPure() && inst.type().equals(Type.I64) && feedsOnlyAddresses(inst, 0)) {
            // Widened byte offsets are ABI plumbing, not program values: print them inside the access.
            return true;
        }
        if (hasJavaName(inst) && !(opcode instanceof Opcode.PointerOffset)) {
            // A value the programmer named stays a variable, so the C reads like the Java.
            return false;
        }
        if (opcode instanceof Opcode.PointerOffset) {
            // Element addresses print as `a_data[i]`; the data pointer itself (base + constant) is one variable.
            return !isDataPointer(inst);
        }
        if (opcode instanceof Opcode.Load && inst.users().size() == 1) {
            return loadCanMove(inst);
        }
        if (opcode.isPure() && feedsOnlyAddresses(inst, 0)) {
            // Byte offsets print inside the access (`a_data[i]`), even when GVN shares them.
            return true;
        }
        if (!opcode.isPure() || inst.users().size() != 1) {
            return false;
        }
        var user = inst.users().get(0);
        // C spells these float comparisons with each operand twice: fold only variables into them.
        if (user.opcode() instanceof Opcode.FCmp(var p) && (p == Opcode.FloatPredicate.ONE || p == Opcode.FloatPredicate.UEQ || p == Opcode.FloatPredicate.ORD || p == Opcode.FloatPredicate.UNO)) {
            return false;
        }
        return user.block().isPresent() && user.block().equals(inst.block());
    }

    /** Named from the class file's local variables (not a number, not a name TC derived). */
    private static boolean hasJavaName(Inst inst) {
        var name = inst.name();
        return !name.isEmpty() && !name.chars().allMatch(Character::isDigit) && !name.endsWith("_data") && !name.endsWith("_len");
    }

    /** Integer arithmetic whose only purpose is to form byte offsets of element addresses. */
    private static boolean feedsOnlyAddresses(Inst inst, int depth) {
        if (depth > 8 || !inst.hasUses() || !inst.type().isInteger()) {
            return false;
        }
        for (var user : inst.users()) {
            boolean ok = switch (user.opcode()) {
                case Opcode.PointerOffset p -> user.operand(1) == inst && user.operand(0) != inst;
                case Opcode.Binary b -> user.operand(1) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt && feedsOnlyAddresses(user, depth + 1);
                case Opcode.Convert c -> feedsOnlyAddresses(user, depth + 1);
                default -> false;
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** A pointer to the first element of an array: a parameter or allocation plus a constant offset. */
    static boolean isDataPointer(Inst inst) {
        // `array + header` of a global array. A constant offset into a local array (`r + 4` after
        // unrolling) is an element address instead: it prints as `r[1]`, which keeps the array
        // promotable to registers by nvcc (a byte-pointer cast of its address would not).
        return inst.opcode() instanceof Opcode.PointerOffset && !(inst.operand(0) instanceof Inst base && (base.opcode() instanceof Opcode.PointerOffset || base.opcode() instanceof Opcode.Alloca))
                && inst.operand(1) instanceof Inst offset && offset.opcode() instanceof Opcode.ConstInt;
    }

    /**
     * A single-use load may be evaluated where its value is finally used if no instruction with a side
     * effect (store, barrier, call, atomic) lies between them in the block.
     */
    private boolean loadCanMove(Inst load) {
        var block = load.block().orElse(null);
        Inst point = evaluationPoint(load.users().get(0), 0);
        if (block == null || point == null || !point.block().equals(load.block())) {
            return false;
        }
        var insts = block.insts();
        for (int k = insts.indexOf(load) + 1; k < insts.indexOf(point); k++) {
            var between = insts.get(k);
            if (!between.opcode().isPure() && !(between.opcode() instanceof Opcode.Load)) {
                return false;
            }
        }
        return true;
    }

    /** Where an inlined value is actually evaluated: the first user up the chain that is not inlined. */
    private Inst evaluationPoint(Inst user, int depth) {
        if (depth > 64 || user.isTerminator() || !user.producesValue() || !user.opcode().isPure() || user.users().size() != 1 || !user.block().equals(user.users().get(0).block())) {
            return user;
        }
        return evaluationPoint(user.users().get(0), depth + 1);
    }

    /** The element type of a data pointer whose every use is an element access of one type and stride. */
    private Optional<Type> elementTypeOf(Value value) {
        if (!(value instanceof Inst data) || !isDataPointer(data)) {
            return Optional.empty();
        }
        Type element = null;
        for (var user : data.users()) {
            if (!(user.opcode() instanceof Opcode.PointerOffset) || user.operand(0) != data || stride(user.operand(1)) <= 0) {
                return Optional.empty();
            }
            for (var access : user.users()) {
                var type = switch (access.opcode()) {
                    case Opcode.Load l -> access.type();
                    case Opcode.Store s -> access.operand(0) == user ? access.operand(1).type() : null;
                    default -> null;
                };
                if (type == null || (element != null && !element.equals(type)) || sizeOf(type) != stride(user.operand(1))) {
                    return Optional.empty();
                }
                element = type;
            }
        }
        return Optional.ofNullable(element);
    }

    /** {@code S} when {@code offset} is {@code sext(i) * S}, {@code i * S} or {@code sext(i)} (S = 1); else -1. */
    static long stride(Value offset) {
        if (offset instanceof Inst mul && mul.opcode() instanceof Opcode.Binary(var op) && op == Opcode.BinaryOp.MUL && mul.operand(1) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var s)) {
            return s;
        }
        if (offset instanceof Inst sext && sext.opcode() instanceof Opcode.Convert(var op) && op == Opcode.ConvertOp.SEXT) {
            return 1;
        }
        return -1;
    }

    static int sizeOf(Type type) {
        return switch (type.text()) {
            case "i8", "i1" -> 1;
            case "i16", "f16", "bf16" -> 2;
            case "i64", "f64" -> 8;
            default -> 4;
        };
    }

    private S.Expr expr(Value value) {
        if (value instanceof Inst inst) {
            if (inst.opcode() instanceof Opcode.ConstInt || inst.opcode() instanceof Opcode.ConstFloat) {
                return new S.Const(inst);
            }
            if (inst.opcode() instanceof Opcode.Alloca) {
                return new S.AllocaRef(allocaByInst.get(inst));
            }
            if (isInline(inst)) {
                var op = new S.Op(inst, inst.operands().stream().map(this::expr).toList());
                return inst.opcode() instanceof Opcode.PointerOffset ? elementAddress(inst, op) : op;
            }
        }
        return new S.VarRef(variable(value));
    }

    /**
     * {@code base + offset} as element {@code i} of {@code base}, when {@code base} is a typed data
     * pointer or a local array of T and the offset is {@code sext(i) * sizeof(T)}, {@code i *
     * sizeof(T)}, {@code sext(i)} for bytes, or a constant multiple of the size; else the plain address.
     */
    private S.Expr elementAddress(Inst address, S.Op op) {
        var base = address.operand(0);
        var element = base instanceof Inst b && b.opcode() instanceof Opcode.Alloca(var space, var e, var count) ? Optional.of(e) : elementTypeOf(base);
        if (element.isEmpty() || !(op.operands().get(0) instanceof S.VarRef || op.operands().get(0) instanceof S.AllocaRef)) {
            return op;
        }
        var index = elementIndex(address.operand(1), sizeOf(element.get()));
        return index.<S.Expr> map(i -> new S.ElementAddress(op, op.operands().get(0), i, element.get())).orElse(op);
    }

    /** The element index a byte offset spells for elements of {@code size} bytes (the offset's arithmetic printed inline). */
    private Optional<S.Expr> elementIndex(Value offset, int size) {
        if (!(offset instanceof Inst o)) {
            return Optional.empty();
        }
        if (o.opcode() instanceof Opcode.ConstInt(var bytes)) {
            // A constant byte offset (an unrolled index): `r + 8` with 4-byte elements is r[2].
            return bytes % size == 0 ? Optional.of(new S.Const(new Inst(new Opcode.ConstInt(bytes / size), Type.I32))) : Optional.empty();
        }
        if (!isInline(o)) {
            return Optional.empty();
        }
        if (o.opcode() instanceof Opcode.Binary(var op) && op == Opcode.BinaryOp.MUL && o.operand(1) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt(var s) && s == size) {
            var i = o.operand(0);
            return Optional.of(i instanceof Inst x && isInline(x) && x.opcode() instanceof Opcode.Convert(var conv) && conv == Opcode.ConvertOp.SEXT ? expr(x.operand(0)) : expr(i));
        }
        if (size == 1 && o.opcode() instanceof Opcode.Convert(var conv) && conv == Opcode.ConvertOp.SEXT) {
            return Optional.of(expr(o.operand(0)));
        }
        return Optional.empty();
    }

    private S.Var variable(Value value) {
        var target = coalesced.get(value);
        if (target != null) {
            return variable(target);
        }
        return vars.computeIfAbsent(value, v -> new S.Var(unique(cName(suggestedName(v))), v.type()));
    }

    /**
     * Copy coalescing: a value whose only use is to be passed to block argument {@code A} on the edge
     * that ends its block is written straight into A's variable, so {@code s2 = s1 / 2; s1 = s2;}
     * becomes {@code s = s / 2;}. Only when nothing in the block can read A after the value is
     * computed (conservatively: no later reader of A, and no folded reader of A at all).
     */
    private Optional<BlockArg> coalesceTarget(Inst inst) {
        if (!inst.producesValue() || isInline(inst) || inst.users().size() != 1) {
            return Optional.empty();
        }
        var user = inst.users().get(0);
        if (!user.isTerminator() || !user.block().equals(inst.block()) || user.operands().contains(inst)) {
            return Optional.empty();
        }
        // Only on an unconditional branch: the variable is written before the terminator, so on a
        // conditional one the other successor (or an expression inlined there) would read the new value
        // where it expects the old one (design review CG-3).
        if (!(user.opcode() instanceof Opcode.Branch)) {
            return Optional.empty();
        }
        BlockArg target = null;
        for (var s : user.successors()) {
            for (int i = 0; i < s.args().size(); i++) {
                if (s.args().get(i) == inst) {
                    if (target != null) {
                        return Optional.empty();
                    }
                    target = s.target().args().get(i);
                }
            }
        }
        if (target == null || target.type() != inst.type() && !target.type().equals(inst.type())) {
            return Optional.empty();
        }
        var arg = target;
        // Between the definition and the end of the block, nothing may read A's old value: not a later
        // instruction, not an expression folded into one, and not another argument of the edge.
        var block = inst.block().orElseThrow();
        var insts = block.insts();
        for (int k = insts.indexOf(inst) + 1; k < insts.size(); k++) {
            var later = insts.get(k);
            if (!isInline(later) && later.operands().stream().anyMatch(v -> readsThrough(v, arg))) {
                return Optional.empty();
            }
        }
        for (var s : user.successors()) {
            for (var passed : s.args()) {
                if (passed != inst && readsThrough(passed, target)) {
                    return Optional.empty();
                }
            }
        }
        // Two values of one block written into the same variable would clobber each other.
        for (var other : coalesced.entrySet()) {
            if (other.getValue() == target && other.getKey() instanceof Inst i && i.block().equals(inst.block())) {
                return Optional.empty();
            }
        }
        return Optional.of(target);
    }

    /** Whether evaluating {@code value} where it is used reads {@code target}, directly or through folded expressions. */
    private boolean readsThrough(Value value, BlockArg target) {
        if (value == target) {
            return true;
        }
        return value instanceof Inst i && isInline(i) && i.operands().stream().anyMatch(v -> readsThrough(v, target));
    }

    /** The Java name when known; otherwise a name derived from what defines the value. */
    private String suggestedName(Value value) {
        var printed = names.name(value);
        var hint = value.name();
        if (!hint.isEmpty() && !hint.chars().allMatch(Character::isDigit)) {
            return printed;
        }
        if (value instanceof Inst inst) {
            switch (inst.opcode()) {
                case Opcode.Intrinsic(var name) -> {
                    // Role names: gid_x, lid_x, group_x, gsize_x, lsize_x (the dimension is an immediate).
                    var dim = inst.immediates().integer("dim").map(d -> String.valueOf("xyz".charAt(d.intValue()))).orElse("");
                    var role = switch (name) {
                        case "tornado.thread_id.global" -> "gid_";
                        case "tornado.thread_id.local" -> "lid_";
                        case "tornado.group_id" -> "group_";
                        case "tornado.global_size" -> "gsize_";
                        case "tornado.group_size.local" -> "lsize_";
                        default -> null;
                    };
                    if (role != null && !dim.isEmpty()) {
                        return role + dim;
                    }
                    if (name.startsWith("math.")) {
                        return name.substring("math.".length());
                    }
                }
                case Opcode.Binary(var op) when op == Opcode.BinaryOp.MUL && inst.operand(1) instanceof Inst c && c.opcode() instanceof Opcode.ConstInt
                        && !inst.operand(0).name().isEmpty() && !inst.operand(0).name().chars().allMatch(Character::isDigit) -> {
                    return cName(names.name(inst.operand(0))) + "_off"; // row offset of a flattened index, e.g. i * 1024
                }
                case Opcode.Load l -> {
                    var base = inst.operand(0);
                    while (base instanceof Inst b && b.opcode() instanceof Opcode.PointerOffset) {
                        if (!b.name().isEmpty() && !b.name().chars().allMatch(Character::isDigit)) {
                            return b.name().replaceAll("_data$", "") + "_val";
                        }
                        base = b.operand(0);
                    }
                    if (!base.name().isEmpty()) {
                        return base.name() + "_val";
                    }
                }
                default -> {
                }
            }
        }
        return printed;
    }

    private String label(Block block) {
        return "L_" + cName(names.blockName(block));
    }

    private String unique(String base) {
        var candidate = base;
        for (int k = 1; !usedNames.add(candidate); k++) {
            candidate = base + "_" + k;
        }
        return candidate;
    }

    /** A C identifier for a TC name: dots and dollars become underscores, leading digits get a prefix. */
    static String cName(String name) {
        // SSA versions of one Java local (`s`, `s.1`, `s.2`) share the Java name; unique() resolves clashes.
        var c = name.replaceAll("\\.\\d+$", "").replace('.', '_').replace('$', '_');
        return c.isEmpty() || Character.isDigit(c.charAt(0)) ? "v" + c : c;
    }
}
