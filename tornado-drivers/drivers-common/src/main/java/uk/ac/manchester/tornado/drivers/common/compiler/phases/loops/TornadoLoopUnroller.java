/*
 * Copyright (c) 2020, 2024 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020 APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.common.compiler.phases.loops;

import static jdk.graal.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static jdk.graal.compiler.debug.DebugContext.INFO_LEVEL;
import static jdk.graal.compiler.nodes.loop.DefaultLoopPolicies.Options.ExactFullUnrollMaxNodes;
import static jdk.graal.compiler.nodes.loop.DefaultLoopPolicies.Options.FullUnrollMaxNodes;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;

public class TornadoLoopUnroller extends BasePhase<CoreProviders> {

    private final CanonicalizerPhase canonicalizer;

    public TornadoLoopUnroller(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    private static boolean shouldFullUnroll(OptionValues options, LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount().asLong();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? ExactFullUnrollMaxNodes.getValue(options) : FullUnrollMaxNodes.getValue(options);
        maxNodes = Math.min(maxNodes, MaximumDesiredSize.getValue(options) - loop.loopBegin().graph().getNodeCount());
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        if (size * maxTrips <= maxNodes) {
            // check whether we're allowed to unroll this loop
            int loops = 0;
            int ifs = 0;
            for (Node node : loop.inside().nodes()) {
                if (node instanceof ControlFlowAnchorNode) {
                    return false;
                } else if (node instanceof LoopBeginNode) {
                    loops++;
                } else if (node instanceof IfNode) {
                    ifs++;
                }
            }

            return (loops - ifs == 0);
        } else {
            return false;
        }
    }

    public void execute(StructuredGraph graph, CoreProviders providers) {
        run(graph, providers);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        if (graph.hasLoops()) {
            boolean peeled;
            do {
                peeled = false;
                final LoopsData dataCounted = new TornadoLoopsData(graph);
                dataCounted.detectCountedLoops();
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (shouldFullUnroll(graph.getOptions(), loop)) {
                        getDebugContext().log("FullUnroll %s", loop);
                        LoopTransformations.fullUnroll(loop, providers, canonicalizer);
                        getDebugContext().dump(INFO_LEVEL, graph, "After fullUnroll %s", loop);
                        peeled = true;
                        break;
                    }
                }
                dataCounted.deleteUnusedNodes();
            } while (peeled);
        }
    }
}
