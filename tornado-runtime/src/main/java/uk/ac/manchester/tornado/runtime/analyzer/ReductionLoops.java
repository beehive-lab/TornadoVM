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
package uk.ac.manchester.tornado.runtime.analyzer;

import tornado.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import tornado.graal.compiler.core.common.cfg.Loop;
import tornado.graal.compiler.nodes.FixedNode;
import tornado.graal.compiler.nodes.LoopBeginNode;
import tornado.graal.compiler.nodes.LoopEndNode;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.cfg.ControlFlowGraph;
import tornado.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Structural checks shared by the reduction analysis (which decides whether a reduction is launched
 * on a padded, power-of-two grid) and the compiler phase that makes such a launch correct, so that
 * the two always agree.
 */
public final class ReductionLoops {

    private ReductionLoops() {
    }

    /**
     * The loop in which {@code store} runs on every iteration: the only loop containing it (loop
     * depth 1), with {@code store} dominating every back edge, so no branch or {@code continue}
     * skips it. Returns null when there is no such loop.
     */
    public static LoopBeginNode loopRunningOnEveryIteration(StructuredGraph graph, FixedNode store) {
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        HIRBlock block = cfg.blockFor(store);
        if (block == null || block.getLoopDepth() != 1) {
            return null;
        }
        Loop<HIRBlock> loop = block.getLoop();
        if (!(loop.getHeader().getBeginNode() instanceof LoopBeginNode loopBegin)) {
            return null;
        }
        for (LoopEndNode end : loopBegin.loopEnds()) {
            if (!AbstractControlFlowGraph.dominates(block, cfg.blockFor(end))) {
                return null;
            }
        }
        return loopBegin;
    }
}
