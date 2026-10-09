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
package uk.ac.manchester.tornado.tc.ir.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.Block;
import uk.ac.manchester.tornado.tc.ir.Function;

/**
 * Dominator tree of a function's control-flow graph, computed with the iterative algorithm of
 * Cooper, Harvey and Kennedy ("A Simple, Fast Dominance Algorithm", 2001) over reverse postorder.
 * Blocks unreachable from the entry have no dominator and are reported by {@link #isReachable}.
 */
public final class DominatorTree {

    private final List<Block> reversePostorder;
    private final Map<Block, Integer> order = new IdentityHashMap<>();
    private final Map<Block, Block> idom = new IdentityHashMap<>();
    private final Map<Block, List<Block>> children = new IdentityHashMap<>();

    public DominatorTree(Function function) {
        this.reversePostorder = computeReversePostorder(function);
        for (int i = 0; i < reversePostorder.size(); i++) {
            order.put(reversePostorder.get(i), i);
        }
        compute(function);
        for (var block : reversePostorder) {
            children.put(block, new ArrayList<>());
        }
        for (var block : reversePostorder) {
            var parent = idom.get(block);
            if (parent != block) {
                children.get(parent).add(block);
            }
        }
    }

    /** Reachable blocks in reverse postorder; the entry block comes first. */
    public List<Block> reversePostorder() {
        return Collections.unmodifiableList(reversePostorder);
    }

    public boolean isReachable(Block block) {
        return order.containsKey(block);
    }

    /** Immediate dominator; empty for the entry block and for unreachable blocks. */
    public Optional<Block> immediateDominator(Block block) {
        var parent = idom.get(block);
        return parent == null || parent == block ? Optional.empty() : Optional.of(parent);
    }

    public List<Block> children(Block block) {
        return Collections.unmodifiableList(children.getOrDefault(block, List.of()));
    }

    /** True when every path from the entry to {@code b} passes through {@code a} (reflexive). */
    public boolean dominates(Block a, Block b) {
        if (!isReachable(a) || !isReachable(b)) {
            return false;
        }
        var current = b;
        while (true) {
            if (current == a) {
                return true;
            }
            var parent = idom.get(current);
            if (parent == current) {
                return false;
            }
            current = parent;
        }
    }

    private void compute(Function function) {
        var preds = function.predecessors();
        var entry = function.entry();
        idom.put(entry, entry);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var block : reversePostorder) {
                if (block == entry) {
                    continue;
                }
                Block newIdom = null;
                for (var pred : preds.get(block)) {
                    if (!idom.containsKey(pred)) {
                        continue;
                    }
                    newIdom = newIdom == null ? pred : intersect(pred, newIdom);
                }
                if (newIdom != null && idom.get(block) != newIdom) {
                    idom.put(block, newIdom);
                    changed = true;
                }
            }
        }
    }

    private Block intersect(Block a, Block b) {
        var finger1 = a;
        var finger2 = b;
        while (finger1 != finger2) {
            while (order.get(finger1) > order.get(finger2)) {
                finger1 = idom.get(finger1);
            }
            while (order.get(finger2) > order.get(finger1)) {
                finger2 = idom.get(finger2);
            }
        }
        return finger1;
    }

    private static List<Block> computeReversePostorder(Function function) {
        var postorder = new ArrayList<Block>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        record Frame(Block block, int nextSuccessor) {
        }
        var stack = new ArrayDeque<Frame>();
        stack.push(new Frame(function.entry(), 0));
        visited.add(function.entry());
        while (!stack.isEmpty()) {
            var frame = stack.pop();
            var successors = frame.block().successors();
            if (frame.nextSuccessor() < successors.size()) {
                stack.push(new Frame(frame.block(), frame.nextSuccessor() + 1));
                var successor = successors.get(frame.nextSuccessor());
                if (visited.add(successor)) {
                    stack.push(new Frame(successor, 0));
                }
            } else {
                postorder.add(frame.block());
            }
        }
        Collections.reverse(postorder);
        return postorder;
    }
}
