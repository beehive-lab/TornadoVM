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
import uk.ac.manchester.tornado.tc.ir.Type;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;

/**
 * Reductions over a {@code @Parallel} loop ({@code @Reduce} arrays), lowered to the TornadoVM
 * runtime's reduction contract: the runtime replaces the {@code @Reduce} array by one with a slot
 * per work-group after a first slot holding the initial value, and appends a sequential task that
 * folds all slots. The kernel therefore runs one loop iteration per thread ({@code i = start + gid},
 * launch domain {@code [start, bound)}), reduces the iteration's value across the work-group (a
 * shared-memory tree, intrinsic {@code tornado.reduce.<op>}) and lets thread 0 write the partial to
 * {@code out[group + 1]}.
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
        var forest = new LoopForest(function);
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
                    if (!(inst.operand(1) instanceof Inst k && k.opcode() instanceof Opcode.ConstInt(var slot) && slot == 0)) {
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
        var dominators = new uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree(function);
        if (!dominators.dominates(store.block().orElseThrow(), loop.latches().get(0))) {
            return refuse("the reduction store is conditional inside the loop");
        }
        var exitTerm = header.terminator().orElseThrow();
        if (!(exitTerm.opcode() instanceof Opcode.CondBranch) || !(exitTerm.operand(0) instanceof Inst cmp) || !(cmp.opcode() instanceof Opcode.ICmp(var p)) || cmp.operand(0) != iv
                || !(cmp.operand(1) instanceof Inst bound && bound.opcode() instanceof Opcode.ConstInt(var upper))) {
            return refuse("the reduction loop bound is not a compile-time constant");
        }
        int stay = loop.contains(exitTerm.successors().get(0).target()) ? 0 : 1;
        var exit = exitTerm.successors().get(1 - stay);
        long last = switch (p) {
            case SGE, SLT -> upper;
            case SGT, SLE -> upper + 1;
            default -> Long.MIN_VALUE;
        };
        var latch = loop.latches().get(0).terminator().orElseThrow();
        var entry = function.predecessors().get(header).stream().filter(b -> !loop.contains(b)).map(b -> b.terminator().orElseThrow()).toList();
        if (last == Long.MIN_VALUE || entry.size() != 1 || !(latch.opcode() instanceof Opcode.Branch)) {
            return refuse("the reduction loop is not in counted form");
        }
        var entryTerm = entry.get(0);
        int entryIndex = -1;
        for (int s = 0; s < entryTerm.successors().size(); s++) {
            if (entryTerm.successors().get(s).target() == header) {
                entryIndex = s;
            }
        }
        var next = latch.successors().get(0).args().get(0);
        if (!(entryTerm.successors().get(entryIndex).args().get(0) instanceof Inst init && init.opcode() instanceof Opcode.ConstInt(var start))
                || !(next instanceof Inst add && add.opcode() instanceof Opcode.Binary(var addOp) && addOp == BinaryOp.ADD && add.operand(0) == iv
                        && add.operand(1) instanceof Inst one && one.opcode() instanceof Opcode.ConstInt(var step) && step == 1)) {
            return refuse("the reduction loop must start at a constant and count by 1");
        }

        // One iteration per thread: i = start + gid; no back edge.
        var b = new IrBuilder(header).before(header.insts().get(0)).at(header.insts().get(0).loc());
        Value index = b.intrinsic("tornado.thread_id.global.x", Type.I32, List.of());
        if (start != 0) {
            index = b.binary(BinaryOp.ADD, index, b.constInt(Type.I32, start));
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
        latch.setSuccessorTarget(0, exitTarget);
        latch.setSuccessorArgs(0, exitArgs);
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
        var at = new IrBuilder(block).atEnd(block).at(store.loc());
        var reduced = at.intrinsic("tornado.reduce." + op, value.type(), List.of(value));
        reduced.setName("partial");
        var tid = at.intrinsic("tornado.thread_id.local.x", Type.I32, List.of());
        var first = at.icmp(Opcode.IntPredicate.EQ, tid, at.constInt(Type.I32, 0));
        var write = function.newBlock(block.label() + ".partial");
        store.erase();
        at.condBr(first, write, List.of(), join, List.of());
        var w = new IrBuilder(write).atEnd(write).at(store.loc());
        var slot = w.binary(BinaryOp.ADD, w.intrinsic("tornado.group_id.x", Type.I32, List.of()), w.constInt(Type.I32, 1));
        w.arrayStore(target, slot, reduced);
        w.br(join, List.of());
        function.addAttribute(new Attribute("domain", "0," + start + ",1," + last));
        return List.of();
    }

    private record Match(String op, Value value) {
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
