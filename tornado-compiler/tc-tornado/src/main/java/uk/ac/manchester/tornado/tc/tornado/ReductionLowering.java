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
 * folds all slots. The kernel therefore runs one loop iteration per thread ({@code i = start + gid},
 * launch domain {@code [start, bound)}), reduces the iteration's value across the work-group (a
 * shared-memory tree written in IR: a shared array, barriers and a halving-stride loop, so every
 * dialect prints it the same way) and lets thread 0 write the partial to {@code out[group + 1]}.
 *
 * <p>Recognised: {@code out[k] = out[k] op v} for {@code +} and {@code *} (integer and float) and
 * {@code out[k] = max/min(out[k], v)}, in the body of the one {@code @Parallel} loop, which counts
 * by 1 and carries no other values. Anything else is TC-E3204 (the kernel goes to Graal).</p>
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
                && match(i).isPresent());
        if (!stores) {
            return List.of(); // no reduction into the @Reduce array: compiled as an ordinary kernel
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
        String op = null;
        Value value = null;
        for (var block : loop.body()) {
            for (var inst : block.insts()) {
                if (inst.opcode() instanceof Opcode.ArrayStore && targets.contains(inst.operand(0))) {
                    var match = match(inst);
                    if (match.isEmpty() || store != null) {
                        return refuse("reduction store not in the form out[k] = out[k] op v (op: + * min max)");
                    }
                    // The group partials go to out[1..], the result to out[0]: only out[0] accumulates.
                    if (!Constants.isInteger(inst.operand(1), 0)) {
                        return refuse("a @Reduce loop must accumulate into element 0 of the reduction array (out[0] = out[0] op v)");
                    }
                    store = inst;
                    op = match.get().op();
                    value = match.get().value();
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
        var exitTerm = counted.exit().get().branch();
        int stay = counted.exit().get().stay();
        var exit = exitTerm.successors().get(1 - stay);
        var latch = counted.backEdge().terminator();
        var entryTerm = counted.entry().terminator();
        int entryIndex = counted.entry().successor();

        // One iteration per thread: i = start + gid; no back edge.
        var b = IrBuilder.insertingBefore(header.insts().get(0));
        Value index = b.intrinsic("tornado.thread_id.global", Immediates.of("dim", 0), Type.I32, List.of());
        if (start.getAsLong() != 0) {
            index = b.binary(BinaryOp.ADD, index, b.constInt(Type.I32, start.getAsLong()));
        }
        index.setName(iv.name());
        iv.replaceAllUsesWith(index);
        header.removeArg(iv);
        entryTerm.setSuccessorArgs(entryIndex, List.of());
        var bodyTarget = exitTerm.successors().get(stay).target();
        var bodyArgs = exitTerm.successors().get(stay).args();
        var exitTarget = exit.target();
        var exitArgs = List.copyOf(exit.args());
        var exitLoc = exitTerm.loc();
        exitTerm.erase();
        new IrBuilder(header).at(exitLoc).br(bodyTarget, bodyArgs);
        latch.setSuccessorTarget(counted.backEdge().successor(), exitTarget);
        latch.setSuccessorArgs(counted.backEdge().successor(), exitArgs);
        header.removeAttribute("parallel");

        // The work-group reduces the iteration's value; thread 0 writes the group's slot.
        var block = store.block().orElseThrow();
        var insts = block.insts();
        var rest = new ArrayList<>(insts.subList(insts.indexOf(store) + 1, insts.size()));
        var join = function.newBlock(block.label() + ".reduced");
        for (var inst : rest) {
            inst.moveToEnd(join);
        }
        var target = store.operand(0);
        var loc = store.loc();
        store.erase();
        var done = treeReduce(function, block, value, op, groupSlots, loc);
        var at = new IrBuilder(done.block()).atEnd(done.block()).at(loc);
        var first = at.icmp(Opcode.IntPredicate.EQ, done.thread(), at.constInt(Type.I32, 0));
        var write = function.newBlock(block.label() + ".partial");
        var reduced = done.value();
        at.condBr(first, write, List.of(), join, List.of());
        var w = new IrBuilder(write).atEnd(write).at(store.loc());
        var slot = w.binary(BinaryOp.ADD, w.intrinsic("tornado.group_id", Immediates.of("dim", 0), Type.I32, List.of()), w.constInt(Type.I32, 1));
        w.arrayStore(target, slot, reduced);
        w.br(join, List.of());
        function.addAttribute(Attribute.of("domain", "dim", 0, "offset", start.getAsLong(), "step", 1, "upper", last));
        return List.of();
    }

    /**
     * The reductions the {@code @Reduce} parameters perform ({@code add}, {@code mul}, {@code min},
     * {@code max}; one per parameter a reduction is found for) and the {@code @Parallel} loop's bound: the parameter
     * whose length it is (or -1) and a constant bound (or -1). What the runtime needs to rewrite a
     * task graph with reductions, read from the IR before any lowering.
     */
    public record Info(List<String> operations, int boundParameter, long boundConstant) {
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
                            op = match(inst).map(Match::op).orElse(null);
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
        return new Info(List.copyOf(operations), boundParameter, boundConstant);
    }

    private record Match(String op, Value value) {
    }

    /** Where a tree reduction ends: the block after it, the reduced value and the local thread index. */
    private record Reduced(Block block, Value value, Value thread) {
    }

    /**
     * A work-group tree reduction of each thread's {@code value}, appended to {@code from}: the values
     * go to a shared array, then each pass halves the stride, threads below it combining their slot
     * with the one a stride away, a barrier after each pass; every thread then reads slot 0. The
     * work-group size is a power of two (the runtime launches reductions that way).
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
        var stride = header.addArg(Type.I32, "stride");
        b.br(header, List.of(b.binary(BinaryOp.SHR, size, b.constInt(Type.I32, 1))));

        var test = function.newBlock(from.label() + ".tree.test");
        var combine = function.newBlock(from.label() + ".tree.combine");
        var latch = function.newBlock(from.label() + ".tree.next");
        var done = function.newBlock(from.label() + ".tree.done");
        var h = new IrBuilder(header).atEnd(header).at(loc);
        h.condBr(h.icmp(Opcode.IntPredicate.SGT, stride, h.constInt(Type.I32, 0)), test, List.of(), done, List.of());
        var c = new IrBuilder(test).atEnd(test).at(loc);
        c.condBr(c.icmp(Opcode.IntPredicate.SLT, t, stride), combine, List.of(), latch, List.of());
        var m = new IrBuilder(combine).atEnd(combine).at(loc);
        var mine = m.arrayLoad(scratch, t, type);
        var other = m.arrayLoad(scratch, m.binary(BinaryOp.ADD, t, stride), type);
        m.arrayStore(scratch, t, combine(m, op, mine, other));
        m.br(latch, List.of());
        var l = new IrBuilder(latch).atEnd(latch).at(loc);
        l.intrinsic("tornado.barrier.local", Type.VOID, List.of());
        l.br(header, List.of(l.binary(BinaryOp.SHR, stride, l.constInt(Type.I32, 1))));
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

    /** {@code store out[k], op(load out[k], v)} (either operand order). */
    private static Optional<Match> match(Inst store) {
        var array = store.operand(0);
        var k = store.operand(1);
        if (!(store.operand(2) instanceof Inst combine)) {
            return Optional.empty();
        }
        String op = switch (combine.opcode()) {
            case Opcode.Binary(var b) when b == BinaryOp.ADD || b == BinaryOp.FADD -> "add";
            case Opcode.Binary(var b) when b == BinaryOp.MUL || b == BinaryOp.FMUL -> "mul";
            case Opcode.Intrinsic(var name) when name.equals("math.max") -> "max";
            case Opcode.Intrinsic(var name) when name.equals("math.min") -> "min";
            default -> null;
        };
        if (op == null || combine.operandCount() != 2) {
            return Optional.empty();
        }
        for (int side = 0; side < 2; side++) {
            if (combine.operand(side) instanceof Inst load && load.opcode() instanceof Opcode.ArrayLoad && load.operand(0) == array && sameIndex(load.operand(1), k)) {
                return Optional.of(new Match(op, combine.operand(1 - side)));
            }
        }
        return Optional.empty();
    }

    private static boolean sameIndex(Value a, Value b) {
        return a == b || a instanceof Inst x && b instanceof Inst y && x.opcode() instanceof Opcode.ConstInt(var u) && y.opcode() instanceof Opcode.ConstInt(var v) && u == v;
    }

    private static List<Diagnostic> refuse(String message) {
        return List.of(new Diagnostic("TC-E3204", message, "", Loc.UNKNOWN));
    }
}
