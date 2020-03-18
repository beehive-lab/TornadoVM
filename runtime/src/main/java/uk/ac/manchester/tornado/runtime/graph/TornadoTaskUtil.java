/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.util.List;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.BasicInductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;

import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;

/**
 * @author James Clarkson
 */
class TornadoTaskUtil {

    private static LoopEx findParallelLoop(StructuredGraph graph) {
        final LoopsData data = new LoopsData(graph);
        data.detectedCountedLoops();

        final List<LoopEx> loops = data.outerFirst();

        List<ParallelRangeNode> parRanges = graph.getNodes().filter(ParallelRangeNode.class).snapshot();
        for (LoopEx loop : loops) {
            for (ParallelRangeNode parRange : parRanges) {
                for (Node n : parRange.offset().usages()) {
                    if (loop.getInductionVariables().containsKey(n)) {
                        BasicInductionVariable iv = (BasicInductionVariable) loop.getInductionVariables().get(n);
                        System.out.printf("[%d] parallel loop: %s -> init=%s, cond=%s, stride=%s, op=%s\n", parRange.index(), loop.loopBegin(), parRange.offset().value(), parRange.value(),
                                parRange.stride(), iv.getOp());
                        return loop;
                    }
                }
            }
        }
        return null;
    }

    public static StructuredGraph merge(CompilableTask t1, CompilableTask t2, StructuredGraph g1, StructuredGraph g2, int[] merges) {
        LoopEx l1 = findParallelLoop(g1);
        LoopEx l2 = findParallelLoop(g2);
        return null;
    }
}
