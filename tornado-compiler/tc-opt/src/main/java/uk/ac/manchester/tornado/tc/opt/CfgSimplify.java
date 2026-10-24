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
 * Control-flow simplification: conditional branches on constants
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
        // Edges into each block. A merge moves B's terminator, with its edges, to P and removes the
        // only edge into B, so the counts of the other blocks stay valid.
        var edges = new IdentityHashMap<Block, Integer>();
        for (var block : function.blocks()) {
            for (var s : block.successors()) {
                edges.merge(s, 1, Integer::sum);
            }
        }
        var merged = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        for (var p : List.copyOf(function.blocks())) {
            if (merged.contains(p)) {
                continue;
            }
            // P takes in a whole chain of such blocks.
            while (true) {
                var term = p.terminator().orElse(null);
                if (term == null || !(term.opcode() instanceof Opcode.Branch)) {
                    break;
                }
                var b = term.successors().get(0).target();
                if (b == p || b == function.entry() || edges.getOrDefault(b, 0) != 1) {
                    break;
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
                merged.add(b);
                changed = true;
            }
        }
        return changed;
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
