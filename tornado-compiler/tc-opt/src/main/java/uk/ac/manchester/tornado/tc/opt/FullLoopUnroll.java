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
package uk.ac.manchester.tornado.tc.opt;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.IntPredicate;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;

/**
 * Full unrolling of small constant-trip loops (optimisation list item O9). A loop made of a header
 * and one body block, whose induction variable starts, steps and ends at constants, is replaced by
 * its iterations laid out in one straight-line block: indices become constants, so private arrays
 * indexed by them ({@code float[] r = new float[3]}) can live in registers. Tornado policy: only
 * loops without nested control flow, allocations or calls, at most {@link #MAX_TRIPS} iterations and
 * {@link #MAX_INSTS} instructions after unrolling. Parallel loops are never unrolled.
 */
public final class FullLoopUnroll implements Pass {

    static final int MAX_TRIPS = 8;
    static final int MAX_INSTS = 512;

    @Override
    public String name() {
        return "unroll";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (var loop : new LoopForest(function).loops()) {
                if (tryUnroll(function, loop)) {
                    changed = progress = true;
                    break; // the loop forest is stale now
                }
            }
        }
        return changed;
    }

    private static boolean tryUnroll(Function function, LoopForest.Loop loop) {
        var header = loop.header();
        if (loop.body().size() != 2 || header.hasAttribute("parallel")) {
            return false;
        }
        var body = loop.body().stream().filter(b -> b != header).findFirst().orElseThrow();
        var headerExit = header.terminator().orElse(null);
        var latch = body.terminator().orElse(null);
        if (headerExit == null || latch == null || !(headerExit.opcode() instanceof Opcode.CondBranch) || !(latch.opcode() instanceof Opcode.Branch)
                || latch.successors().get(0).target() != header) {
            return false;
        }
        int stay = headerExit.successors().get(0).target() == body ? 0 : headerExit.successors().get(1).target() == body ? 1 : -1;
        if (stay < 0 || headerExit.successors().get(1 - stay).target() == body) {
            return false;
        }
        // The single entry edge from outside the loop.
        Inst entryTerm = null;
        int entryIndex = -1;
        for (var b : function.blocks()) {
            if (loop.contains(b)) {
                continue;
            }
            var t = b.terminator().orElse(null);
            if (t == null) {
                continue;
            }
            for (int s = 0; s < t.successors().size(); s++) {
                if (t.successors().get(s).target() == header) {
                    if (entryTerm != null) {
                        return false;
                    }
                    entryTerm = t;
                    entryIndex = s;
                }
            }
        }
        if (entryTerm == null) {
            return false;
        }
        for (var b : List.of(header, body)) {
            for (var inst : b.insts()) {
                if (inst.opcode() instanceof Opcode.Alloca || inst.opcode() instanceof Opcode.Call) {
                    return false;
                }
            }
        }
        // Induction variable: i = c0; i < bound (or another signed test); i = i + step.
        if (!(headerExit.operand(0) instanceof Inst cmp) || !(cmp.opcode() instanceof Opcode.ICmp(var predicate)) || !(cmp.operand(0) instanceof BlockArg iv)
                || iv.block() != header) {
            return false;
        }
        int ivIndex = header.args().indexOf(iv);
        var init = constant(entryTerm.successors().get(entryIndex).args().get(ivIndex));
        var bound = constant(cmp.operand(1));
        var next = latch.successors().get(0).args().get(ivIndex);
        if (init == null || bound == null || !(next instanceof Inst add) || !(add.opcode() instanceof Opcode.Binary(var op)) || op != Opcode.BinaryOp.ADD || add.operand(0) != iv) {
            return false;
        }
        var step = constant(add.operand(1));
        if (step == null || step == 0 || !(predicate == IntPredicate.EQ || predicate == IntPredicate.NE || predicate == IntPredicate.SLT || predicate == IntPredicate.SLE
                || predicate == IntPredicate.SGT || predicate == IntPredicate.SGE)) {
            return false;
        }
        int trips = 0;
        for (long i = init; holds(predicate, i, bound) == (stay == 0); i += step) {
            if (++trips > MAX_TRIPS) {
                return false;
            }
        }
        int size = header.insts().size() + body.insts().size();
        if (size * (trips + 1) > MAX_INSTS) {
            return false;
        }

        // Lay the iterations out in one block.
        var unrolled = function.newBlock(header.label() + ".unrolled");
        Map<Value, Value> env = new IdentityHashMap<>();
        var initArgs = entryTerm.successors().get(entryIndex).args();
        for (int a = 0; a < header.args().size(); a++) {
            env.put(header.args().get(a), initArgs.get(a));
        }
        for (int t = 0; t < trips; t++) {
            cloneInto(header, unrolled, env);
            cloneInto(body, unrolled, env);
            var carried = latch.successors().get(0).args().stream().map(v -> env.getOrDefault(v, v)).toList();
            for (int a = 0; a < header.args().size(); a++) {
                env.put(header.args().get(a), carried.get(a));
            }
        }
        cloneInto(header, unrolled, env); // the final test, and header values used after the loop
        var exit = headerExit.successors().get(1 - stay);
        var exitArgs = exit.args().stream().map(v -> env.getOrDefault(v, v)).toList();
        var br = new Inst(new Opcode.Branch(), uk.ac.manchester.tornado.tc.ir.Type.VOID);
        br.setLoc(headerExit.loc());
        unrolled.append(br);
        br.addSuccessor(exit.target(), exitArgs);
        entryTerm.setSuccessorTarget(entryIndex, unrolled);
        entryTerm.setSuccessorArgs(entryIndex, List.of());

        // Values of the header used after the loop take their final values; the loop goes away.
        for (var arg : List.copyOf(header.args())) {
            arg.replaceAllUsesWith(env.get(arg));
        }
        for (var inst : List.copyOf(header.insts())) {
            if (inst.producesValue()) {
                inst.replaceAllUsesWith(env.get(inst));
            }
        }
        for (var b : List.of(header, body)) {
            for (var inst : List.copyOf(b.insts())) {
                inst.dropReferences();
            }
            function.removeBlock(b);
        }
        return true;
    }

    /** Copies the non-terminator instructions of {@code from} to the end of {@code to}, renaming through {@code env}. */
    private static void cloneInto(Block from, Block to, Map<Value, Value> env) {
        for (var inst : from.insts()) {
            if (inst.isTerminator()) {
                continue;
            }
            var copy = new Inst(inst.opcode(), inst.type());
            copy.setName(inst.name());
            copy.setLoc(inst.loc());
            inst.attributes().forEach(copy::addAttribute);
            to.append(copy);
            for (var operand : inst.operands()) {
                copy.addOperand(env.getOrDefault(operand, operand));
            }
            env.put(inst, copy);
        }
    }

    private static Long constant(Value v) {
        return v instanceof Inst i && i.opcode() instanceof Opcode.ConstInt(var c) ? c : null;
    }

    private static boolean holds(IntPredicate p, long a, long b) {
        return switch (p) {
            case EQ -> a == b;
            case NE -> a != b;
            case SLT -> a < b;
            case SLE -> a <= b;
            case SGT -> a > b;
            case SGE -> a >= b;
            default -> throw new IllegalStateException("unsigned predicates are not unrolled");
        };
    }
}
