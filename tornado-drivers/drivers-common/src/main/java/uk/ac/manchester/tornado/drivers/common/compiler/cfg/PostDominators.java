/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.drivers.common.compiler.cfg;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import tornado.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Immediate post-dominators of the blocks of a control-flow graph, for the code generators that
 * rebuild structured control flow ({@code if}/{@code else}, loops) from it.
 *
 * <p>
 * Graal's {@code ControlFlowGraph.computePostdominators()} leaves the post-dominator unset for many
 * blocks, including the heads of the {@code if}s a short-circuit condition ({@code A || B},
 * {@code A && B}) is lowered to. Guessing the join instead (for example, the nearest merge the head
 * dominates) can pick a <em>partial</em> merge that only some paths reach, and the code generated
 * then lets one branch fall through into the other. This computes the relation exactly, over the
 * whole graph, with a virtual exit that every returning block leads to.
 * </p>
 */
public final class PostDominators {

    private final HIRBlock[] blocks;
    private final Map<HIRBlock, Integer> index = new HashMap<>();
    /** {@code postDominators[i]}: the blocks that post-dominate block {@code i}, itself included. */
    private final BitSet[] postDominators;
    /** Blocks from which some exit (a block with no successors) is reachable. */
    private final BitSet reachesExit = new BitSet();

    public PostDominators(HIRBlock[] blocks) {
        this.blocks = blocks;
        int n = blocks.length;
        for (int i = 0; i < n; i++) {
            index.put(blocks[i], i);
        }

        // Which blocks can reach an exit: walk predecessors back from every exit.
        ArrayDeque<Integer> work = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (blocks[i].getSuccessorCount() == 0) {
                reachesExit.set(i);
                work.add(i);
            }
        }
        while (!work.isEmpty()) {
            HIRBlock block = blocks[work.poll()];
            for (int p = 0; p < block.getPredecessorCount(); p++) {
                Integer predecessor = index.get(block.getPredecessorAt(p));
                if (predecessor != null && !reachesExit.get(predecessor)) {
                    reachesExit.set(predecessor);
                    work.add(predecessor);
                }
            }
        }

        // Iterative data flow on the reverse graph: pdom(b) = {b} + intersection of pdom(s) over
        // the successors s of b that reach an exit. Exits start as {b}, everything else as "all".
        BitSet all = new BitSet(n);
        all.set(0, n);
        postDominators = new BitSet[n];
        for (int i = 0; i < n; i++) {
            if (blocks[i].getSuccessorCount() == 0) {
                postDominators[i] = new BitSet(n);
                postDominators[i].set(i);
            } else {
                postDominators[i] = (BitSet) all.clone();
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            // Blocks come in reverse post-order, so visiting them backwards converges quickly.
            for (int i = n - 1; i >= 0; i--) {
                HIRBlock block = blocks[i];
                if (block.getSuccessorCount() == 0 || !reachesExit.get(i)) {
                    continue;
                }
                BitSet next = null;
                for (int s = 0; s < block.getSuccessorCount(); s++) {
                    Integer successor = index.get(block.getSuccessorAt(s));
                    if (successor == null || !reachesExit.get(successor)) {
                        continue;
                    }
                    if (next == null) {
                        next = (BitSet) postDominators[successor].clone();
                    } else {
                        next.and(postDominators[successor]);
                    }
                }
                if (next == null) {
                    continue;
                }
                next.set(i);
                if (!next.equals(postDominators[i])) {
                    postDominators[i] = next;
                    changed = true;
                }
            }
        }
    }

    /** Whether an exit is reachable from {@code block}, so its post-dominators are known. */
    public boolean reachesExit(HIRBlock block) {
        Integer i = index.get(block);
        return i != null && reachesExit.get(i);
    }

    /**
     * The block every path from {@code block} to an exit goes through first, or {@code null} when
     * there is none: the paths leave through different exits (for example, both branches return)
     * or no exit is reachable.
     */
    public HIRBlock immediatePostDominator(HIRBlock block) {
        Integer i = index.get(block);
        if (i == null || !reachesExit.get(i)) {
            return null;
        }
        BitSet strict = (BitSet) postDominators[i].clone();
        strict.clear(i);
        int size = strict.cardinality();
        // The immediate post-dominator is the strict post-dominator that all the others
        // post-dominate: the one whose own set is exactly the rest.
        for (int d = strict.nextSetBit(0); d >= 0; d = strict.nextSetBit(d + 1)) {
            if (postDominators[d].cardinality() == size) {
                return blocks[d];
            }
        }
        return null;
    }
}
