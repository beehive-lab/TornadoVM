/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopFragmentInside;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

import uk.ac.manchester.tornado.runtime.common.Tornado;

public class TornadoPartialLoopUnroll extends BasePhase<MidTierContext> {
    private static final int LOOP_UNROLL_FACTOR = 32; // TODO: Measure perf benefits

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {

        int initialNodeCount = graph.getNodeCount(); // Add a check for powers of two
        for (int i = 0; i < Tornado.UNROLL_FACTOR - 1; i++) {
            if (graph.getNodeCount() < initialNodeCount + GraalOptions.MaximumDesiredSize.getValue(graph.getOptions()) * 2) {
                partialUnroll(graph, context);
            }
        }

    }

    private static void partialUnroll(StructuredGraph graph, MidTierContext context) {
        final LoopsData dataCounted = new LoopsData(graph);

        LoopPolicies loopPolicies = createLoopPolicies();
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

        canonicalizer.apply(graph, context);
        dataCounted.detectedCountedLoops();
        for (LoopEx loop : dataCounted.countedLoops()) {
            loop.loopBegin().setUnrollFactor(4);
            LoopFragmentInside newSegment = loop.inside().duplicate();
            newSegment.insertWithinAfter(loop, null);
            System.out.println("FREQ : " + loop.loopBegin().loopFrequency());
        }
        new DeadCodeEliminationPhase().apply(graph);
    }

    public static LoopPolicies createLoopPolicies() {
        return new DefaultLoopPolicies();
    }

}
