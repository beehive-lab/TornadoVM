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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.AddressSpace;
import uk.ac.manchester.tornado.tc.ir.Attribute;
import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Immediates;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Loc;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.BinaryOp;
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;
import uk.ac.manchester.tornado.tc.ir.analysis.CountedLoop;

/**
 * Reductions over a {@code @Parallel} loop ({@code @Reduce} arrays), lowered to the TornadoVM
 * runtime's reduction contract: the runtime replaces the {@code @Reduce} array by one with a slot
 * per work-group after a first slot holding the initial value, and appends a sequential task that
 * folds all slots.
 *
 * <p>Each thread accumulates its iterations in a register: {@code acc} starts at the operation's
 * neutral element and the loop runs {@code i = start + gid; i < bound; i += global size}, so any
 * launch size is correct and a thread with no iteration contributes the neutral element. On a GPU
 * the runtime launches the next power of two at or above the iteration count. After the loop every
 * thread reaches the work-group reduction (a shared-memory tree written in IR, for any work-group
 * size), and thread 0 writes the group's partial to {@code out[group + 1]}.</p>
 *
 * <p>Recognised: {@code out[0] = out[0] op v} for {@code +} and {@code *} (integer and float) and
 * {@code out[0] = max/min(out[0], v)}, including chains such as {@code (out[0] + a) + b}: the read of
 * {@code out[0]} is replaced by the accumulator, so each thread keeps Java's order of evaluation. The
 * store must run on every iteration of the one {@code @Parallel} loop, which counts by 1 from a
 * constant and carries no other values. Anything else is TC-E3204 (the kernel is refused).</p>
 */
public final class ReductionLowering {

    private ReductionLowering() {
    }

    /** {@code reduce.get(p)}: parameter {@code p} is a {@code @Reduce} array. */
    public static List<Diagnostic> run(Function function, List<Boolean> reduce) {
        return run(function, reduce, 1024);
    }

    /** {@code groupSlots}: the largest work-group, which sizes the shared scratch array of the tree. */
    public static List<Diagnostic> run(Function function, List<Boolean> reduce, int groupSlots) {
        var targets = new ArrayList<Value>();
        for (int p = 0; p < Math.min(reduce.size(), function.params().size()); p++) {
            if (reduce.get(p)) {
                targets.add(function.params().get(p));
            }
        }
        if (targets.isEmpty()) {
            return List.of();
        }
        boolean stores = function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof Opcode.ArrayStore && i.operand(0) instanceof Value v && targets.contains(v)
                && spine(i).isPresent());
        if (!stores) {
            // A store into a @Reduce array that reads it, but in no form this lowering knows, would race
            // between threads as an ordinary kernel: refused. Other stores make an ordinary kernel.
            boolean unrecognised = function.blocks().stream().flatMap(b -> b.insts().stream()).anyMatch(i -> i.opcode() instanceof Opcode.ArrayStore && targets.contains(i.operand(0))
                    && reads(i.operand(2), i.operand(0), i.operand(1), new java.util.HashSet<>()));
            return unrecognised ? refuse("reduction store not in the form out[0] = out[0] op v (op: + * min max, one operation along the chain)") : List.of();
        }
        var forest = Analyses.loops(function);
        var headers = function.blocks().stream().filter(b -> b.hasAttribute("parallel")).toList();
        if (headers.size() != 1) {
            return refuse("a reduction needs exactly one @Parallel loop (found " + headers.size() + ")");
        }
        var header = headers.get(0);
        var loop = forest.loopWithHeader(header).orElse(null);
        if (loop == null || header.args().size() != 1 || loop.latches().size() != 1) {
            return refuse("the @Parallel loop of a reduction must be a counted loop carrying only its index");
        }
        var iv = header.args().get(0);
        // The reduction store, in the loop body.
        Inst store = null;
        Spine spine = null;
        for (var block : loop.body()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.ArrayStore && targets.contains(inst.operand(0))) {
                    var match = spine(inst);
                    if (match.isEmpty() || store != null) {
                        return refuse("reduction store not in the form out[k] = out[k] op v (op: + * min max)");
                    }
                    // The group partials go to out[1..], the result to out[0]: only out[0] accumulates.
                    if (!Constants.isInteger(inst.operand(1), 0)) {
                        return refuse("a @Reduce loop must accumulate into element 0 of the reduction array (out[0] = out[0] op v)");
                    }
                    store = inst;
                    spine = match.get();
                }
            }
        }
        if (store == null) {
            return List.of(); // the array is only read here (a map using a reduced value): an ordinary kernel
        }
        // Every thread must reach the work-group reduction (it synchronises the group).
        var dominators = Analyses.dominators(function);
        if (!dominators.dominates(store.block().orElseThrow(), loop.latches().get(0))) {
            return refuse("the reduction store is conditional inside the loop");
        }
        var counted = CountedLoop.of(loop, 0).orElse(null);
        if (counted == null || counted.exit().isEmpty() || !(counted.backEdge().terminator().opcode() instanceof Opcode.Branch)) {
            return refuse("the reduction loop is not in counted form");
        }
        var upper = counted.exclusiveUpper();
        if (upper.isEmpty()) {
            return refuse("the reduction loop bound is not a compile-time constant");
        }
        var start = counted.constantInit();
        if (start.isEmpty() || !Constants.isInteger(counted.step(), 1)) {
            return refuse("the reduction loop must start at a constant and count by 1");
        }
        long last = upper.getAsLong();
        var op = spine.op();
        var loc = store.loc();
        var accType = store.operand(2).type();
        var target = store.operand(0);
        var exitTerm = counted.exit().get().branch();
        int stay = counted.exit().get().stay();
        var latch = counted.backEdge().terminator();
        int back = counted.backEdge().successor();
        var entryTerm = counted.entry().terminator();
        int entryIndex = counted.entry().successor();

        // The accumulator, carried by the loop from the neutral element.
        var acc = header.addArg(accType, "acc");
        var pre = IrBuilder.insertingBefore(entryTerm).at(loc);
        var gid = pre.intrinsic("tornado.thread_id.global", Immediates.of("dim", 0), Type.I32, List.of());
        var gsize = pre.intrinsic("tornado.global_size", Immediates.of("dim", 0), Type.I32, List.of());
        Value first = start.getAsLong() == 0 ? gid : pre.binary(BinaryOp.ADD, gid, pre.constInt(Type.I32, start.getAsLong()));
        entryTerm.setSuccessorArgs(entryIndex, List.of(first, neutral(pre, op, accType)));

        // The read of out[0] becomes the accumulator; the stored value is the next one.
        spine.load().replaceAllUsesWith(acc);
        spine.load().erase();
        var next = store.operand(2);
        store.erase();

        // Grid stride: i += global size (any launch size covers the iterations).
        var step = IrBuilder.insertingBefore(latch).at(loc).binary(BinaryOp.ADD, iv, gsize);
        latch.setSuccessorArgs(back, List.of(step, next));
        header.removeAttribute("parallel");

        // After the loop, which every thread leaves: the work-group reduction, then thread 0 writes the partial.
        var exit = exitTerm.successors().get(1 - stay);
        var exitTarget = exit.target();
        var exitArgs = List.copyOf(exit.args());
        var reduceBlock = function.newBlock(header.label() + ".reduce");
        exitTerm.setSuccessorTarget(1 - stay, reduceBlock);
        exitTerm.setSuccessorArgs(1 - stay, List.of());
        var done = treeReduce(function, reduceBlock, acc, op, groupSlots, loc);
        var at = new IrBuilder(done.block()).atEnd(done.block()).at(loc);
        var isFirst = at.icmp(Opcode.IntPredicate.EQ, done.thread(), at.constInt(Type.I32, 0));
        var write = function.newBlock(header.label() + ".partial");
        var join = function.newBlock(header.label() + ".reduced");
        at.condBr(isFirst, write, List.of(), join, List.of());
        var w = new IrBuilder(write).atEnd(write).at(loc);
        var slot = w.binary(BinaryOp.ADD, w.intrinsic("tornado.group_id", Immediates.of("dim", 0), Type.I32, List.of()), w.constInt(Type.I32, 1));
        w.arrayStore(target, slot, done.value());
        w.br(join, List.of());
        new IrBuilder(join).atEnd(join).at(loc).br(exitTarget, exitArgs);
        function.addAttribute(Attribute.of("domain", "dim", 0, "offset", start.getAsLong(), "step", 1, "upper", last));
        return List.of();
    }

    /** The operation's neutral element: 0, 1, or the extreme of the type for min and max. */
    private static Value neutral(IrBuilder b, String op, Type type) {
        boolean fp = type instanceof Type.FloatType;
        boolean wide = type.equals(Type.I64);
        return switch (op) {
            case "add" -> fp ? b.constFloat(type, 0.0) : b.constInt(type, 0);
            case "mul" -> fp ? b.constFloat(type, 1.0) : b.constInt(type, 1);
            case "min" -> fp ? b.constFloat(type, Double.POSITIVE_INFINITY) : b.constInt(type, wide ? Long.MAX_VALUE : Integer.MAX_VALUE);
            default -> fp ? b.constFloat(type, Double.NEGATIVE_INFINITY) : b.constInt(type, wide ? Long.MIN_VALUE : Integer.MIN_VALUE);
        };
    }

    /**
     * The reductions the {@code @Reduce} parameters perform ({@code add}, {@code mul}, {@code min},
     * {@code max}; one per parameter a reduction is found for) and the {@code @Parallel} loop's bound: the parameter
     * whose length it is (or -1) and a constant bound (or -1). What the runtime needs to rewrite a
     * task graph with reductions, read from the IR before any lowering.
     */
    public record Info(List<String> operations, int boundParameter, long boundConstant, long start) {

        public Info(List<String> operations, int boundParameter, long boundConstant) {
            this(operations, boundParameter, boundConstant, 0);
        }
    }

    public static Info info(Function function, List<Integer> reduceIndices) {
        var operations = new ArrayList<String>();
        for (int index : reduceIndices) {
            String op = null;
            if (index < function.params().size()) {
                var target = function.params().get(index);
                for (var block : function.blocks()) {
                    for (var inst : block.insts()) {
                        if (op == null && inst.opcode() instanceof Opcode.ArrayStore && inst.operand(0) == target) {
                            op = spine(inst).map(Spine::op).orElse(null);
                        }
                    }
                }
            }
            if (op != null) {
                operations.add(op); // as the runtime expects: one operation per reduction found
            }
        }
        int boundParameter = -1;
        long boundConstant = -1;
        for (var header : function.blocks()) {
            if (!header.hasAttribute("parallel") || header.terminator().isEmpty()) {
                continue;
            }
            var branch = header.terminator().get();
            if (branch.opcode() instanceof Opcode.CondBranch && branch.operand(0) instanceof Inst cmp && cmp.opcode() instanceof Opcode.ICmp) {
                for (var operand : cmp.operands()) {
                    if (operand instanceof Inst bound && bound.opcode() instanceof Opcode.ConstInt(var v)) {
                        boundConstant = v;
                    } else if (operand instanceof Inst bound && bound.opcode() instanceof Opcode.ArrayLength) {
                        boundParameter = function.params().indexOf(bound.operand(0));
                    }
                }
            }
            break;
        }
        // The loop start, for the runtime's launch size (bound - start iterations).
        long start = 0;
        var parallel = function.blocks().stream().filter(h -> h.hasAttribute("parallel")).findFirst();
        if (parallel.isPresent()) {
            var loop = Analyses.loops(function).loopWithHeader(parallel.get()).orElse(null);
            var counted = loop == null ? null : CountedLoop.of(loop, 0).orElse(null);
            if (counted != null && counted.constantInit().isPresent()) {
                start = counted.constantInit().getAsLong();
            }
        }
        return new Info(List.copyOf(operations), boundParameter, boundConstant, start);
    }

    /** Where a tree reduction ends: the block after it, the reduced value and the local thread index. */
    private record Reduced(Block block, Value value, Value thread) {
    }

    /**
     * A work-group tree reduction of each thread's {@code value}, appended to {@code from}: the values
     * go to a shared array; each pass folds the upper half of the {@code active} slots onto the lower
     * ({@code half = (active + 1) / 2}, threads below {@code active - half} combine), a barrier after
     * each pass, until one slot is left; every thread then reads slot 0. Any work-group size.
     */
    private static Reduced treeReduce(Function function, Block from, Value value, String op, int groupSlots, Loc loc) {
        var type = value.type();
        var entry = function.entry();
        var scratch = new IrBuilder(entry).before(entry.insts().get(0)).at(loc).alloca(AddressSpace.SHARED, type, groupSlots);
        scratch.setName("reduce_scratch");
        var b = new IrBuilder(from).atEnd(from).at(loc);
        var t = b.intrinsic("tornado.thread_id.local", Immediates.of("dim", 0), Type.I32, List.of());
        b.arrayStore(scratch, t, value);
        b.intrinsic("tornado.barrier.local", Type.VOID, List.of());
        var size = b.intrinsic("tornado.group_size.local", Immediates.of("dim", 0), Type.I32, List.of());
        var header = function.newBlock(from.label() + ".tree");
        var active = header.addArg(Type.I32, "active");
        b.br(header, List.of(size));

        var test = function.newBlock(from.label() + ".tree.test");
        var combine = function.newBlock(from.label() + ".tree.combine");
        var latch = function.newBlock(from.label() + ".tree.next");
        var done = function.newBlock(from.label() + ".tree.done");
        var h = new IrBuilder(header).atEnd(header).at(loc);
        h.condBr(h.icmp(Opcode.IntPredicate.SGT, active, h.constInt(Type.I32, 1)), test, List.of(), done, List.of());
        var c = new IrBuilder(test).atEnd(test).at(loc);
        var half = c.binary(BinaryOp.SHR, c.binary(BinaryOp.ADD, active, c.constInt(Type.I32, 1)), c.constInt(Type.I32, 1));
        half.setName("half");
        c.condBr(c.icmp(Opcode.IntPredicate.SLT, t, c.binary(BinaryOp.SUB, active, half)), combine, List.of(), latch, List.of());
        var m = new IrBuilder(combine).atEnd(combine).at(loc);
        var mine = m.arrayLoad(scratch, t, type);
        var other = m.arrayLoad(scratch, m.binary(BinaryOp.ADD, t, half), type);
        m.arrayStore(scratch, t, combine(m, op, mine, other));
        m.br(latch, List.of());
        var l = new IrBuilder(latch).atEnd(latch).at(loc);
        l.intrinsic("tornado.barrier.local", Type.VOID, List.of());
        l.br(header, List.of(half));
        var d = new IrBuilder(done).atEnd(done).at(loc);
        var result = d.arrayLoad(scratch, d.constInt(Type.I32, 0), type);
        result.setName("partial");
        return new Reduced(done, result, t);
    }

    /** One combining step with Java semantics (min/max through the math intrinsics: NaN and signed zeros). */
    private static Value combine(IrBuilder b, String op, Value x, Value y) {
        boolean fp = x.type() instanceof Type.FloatType;
        return switch (op) {
            case "add" -> b.binary(fp ? BinaryOp.FADD : BinaryOp.ADD, x, y);
            case "mul" -> b.binary(fp ? BinaryOp.FMUL : BinaryOp.MUL, x, y);
            default -> b.intrinsic("math." + op, x.type(), List.of(x, y));
        };
    }

    /** A reduction store: its operation and the read of {@code out[k]} at the start of the chain. */
    private record Spine(String op, Inst load) {
    }

    /**
     * {@code store out[k], v} where {@code v} is a chain of one operation ({@code +}, {@code *},
     * {@code min} or {@code max}) leading to one read of {@code out[k]}, whatever the other operands
     * ({@code (out[k] + a) + b}, {@code max(max(out[k], a), b)}, ...). Every link has a single use.
     */
    private static Optional<Spine> spine(Inst store) {
        var array = store.operand(0);
        var k = store.operand(1);
        String op = null;
        Value v = store.operand(2);
        while (true) {
            if (isRead(v, array, k)) {
                var load = (Inst) v;
                return op == null || load.users().size() != 1 ? Optional.empty() : Optional.of(new Spine(op, load));
            }
            if (!(v instanceof Inst link) || link.operandCount() != 2 || link.users().size() != 1) {
                return Optional.empty();
            }
            var linkOp = operation(link);
            if (linkOp == null || (op != null && !op.equals(linkOp))) {
                return Optional.empty();
            }
            op = linkOp;
            boolean left = reads(link.operand(0), array, k, new java.util.HashSet<>());
            boolean right = reads(link.operand(1), array, k, new java.util.HashSet<>());
            if (left == right) {
                return Optional.empty(); // out[k] on both sides, or on neither
            }
            v = left ? link.operand(0) : link.operand(1);
        }
    }

    private static String operation(Inst inst) {
        return switch (inst.opcode()) {
            case Opcode.Binary(var b) when b == BinaryOp.ADD || b == BinaryOp.FADD -> "add";
            case Opcode.Binary(var b) when b == BinaryOp.MUL || b == BinaryOp.FMUL -> "mul";
            case Opcode.Intrinsic(var name) when name.equals("math.max") -> "max";
            case Opcode.Intrinsic(var name) when name.equals("math.min") -> "min";
            default -> null;
        };
    }

    private static boolean isRead(Value v, Value array, Value k) {
        return v instanceof Inst load && load.opcode() instanceof Opcode.ArrayLoad && load.operand(0) == array && sameIndex(load.operand(1), k);
    }

    /** Whether {@code v} depends on a read of {@code out[k]} (through instructions of the same function). */
    private static boolean reads(Value v, Value array, Value k, java.util.Set<Value> seen) {
        if (isRead(v, array, k)) {
            return true;
        }
        if (!(v instanceof Inst inst) || !seen.add(v)) {
            return false;
        }
        for (var operand : inst.operands()) {
            if (reads(operand, array, k, seen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameIndex(Value a, Value b) {
        return a == b || a instanceof Inst x && b instanceof Inst y && x.opcode() instanceof Opcode.ConstInt(var u) && y.opcode() instanceof Opcode.ConstInt(var v) && u == v;
    }

    private static List<Diagnostic> refuse(String message) {
        return List.of(new Diagnostic("TC-E3204", message, "", Loc.UNKNOWN));
    }
}
