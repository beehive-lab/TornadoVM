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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.analysis.Constants;

/**
 * Control-flow simplification (optimisation list item O4): conditional branches on constants
 * become branches, a block with a single predecessor that branches only to it is merged into that
 * predecessor, and unreachable blocks are removed.
 */
public final class CfgSimplify implements Pass {

    @Override
    public String name() {
        return "cfg-simplify";
    }

    @Override
    public boolean run(Function function) {
        boolean changed = false;
        boolean progress = true;
        while (progress) {
            progress = foldConstantBranches(function) | mergeStraightLines(function) | removeUnreachable(function);
            changed |= progress;
        }
        return changed;
    }

    private static boolean foldConstantBranches(Function function) {
        boolean changed = false;
        for (var block : function.blocks()) {
            var term = block.terminator().orElse(null);
            if (term == null || !(term.opcode() instanceof Opcode.CondBranch)) {
                continue;
            }
            var c = Constants.number(term.operand(0));
            if (c.isEmpty()) {
                continue;
            }
            var taken = term.successors().get(c.get().longValue() != 0 ? 0 : 1);
            var target = taken.target();
            var args = List.copyOf(taken.args());
            term.erase();
            new IrBuilder(block).at(term.loc()).br(target, args);
            changed = true;
        }
        return changed;
    }

    /** Merges B into P when P ends with {@code br B(args)} and P is B's only predecessor. */
    private static boolean mergeStraightLines(Function function) {
        boolean changed = false;
        var preds = function.predecessors();
        for (var p : List.copyOf(function.blocks())) {
            if (!function.blocks().contains(p)) {
                continue;
            }
            var term = p.terminator().orElse(null);
            if (term == null || !(term.opcode() instanceof Opcode.Branch)) {
                continue;
            }
            var b = term.successors().get(0).target();
            if (b == p || b == function.entry() || preds.get(b).size() != 1 || countEdges(function, b) != 1) {
                continue;
            }
            var args = List.copyOf(term.successors().get(0).args());
            term.erase();
            for (int i = 0; i < b.args().size(); i++) {
                b.args().get(i).replaceAllUsesWith(args.get(i));
            }
            for (var inst : List.copyOf(b.insts())) {
                inst.moveToEnd(p);
            }
            for (var arg : List.copyOf(b.args())) {
                b.removeArg(arg);
            }
            // b's block attributes are not carried over: they describe b (a loop header's
            // arguments, say), and a block with a single predecessor heads no loop.
            function.removeBlock(b);
            preds = function.predecessors();
            changed = true;
        }
        return changed;
    }

    private static int countEdges(Function function, Block target) {
        int n = 0;
        for (var block : function.blocks()) {
            for (var s : block.successors()) {
                if (s == target) {
                    n++;
                }
            }
        }
        return n;
    }

    static boolean removeUnreachable(Function function) {
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
        var dead = new ArrayList<Block>();
        for (var block : function.blocks()) {
            if (!reachable.contains(block)) {
                dead.add(block);
            }
        }
        for (var block : dead) {
            for (var inst : block.insts()) {
                inst.dropReferences();
            }
        }
        for (var block : dead) {
            for (var inst : List.copyOf(block.insts())) {
                for (var user : List.copyOf(inst.users())) {
                    user.dropReferences();
                }
            }
            function.removeBlock(block);
        }
        return !dead.isEmpty();
    }
}
