/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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
 * Authors: Michalis Papadimitriou
 *
 *
 * */

package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.loop.DefaultLoopPolicies.ExactFullUnrollMaxNodes;
import static org.graalvm.compiler.loop.DefaultLoopPolicies.FullUnrollMaxNodes;

import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.CountedLoopInfo;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.PragmaUnrollNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoPragmaUnroll extends BasePhase<TornadoHighTierContext> {

    private final CanonicalizerPhase canonicalizer;

    public TornadoPragmaUnroll(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public static boolean shouldFullUnroll(OptionValues options, LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount();
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
            if (loops - ifs != 0) {
                return true;
            }

            return true;
        } else {
            return true;
        }
    }

    public void execute(StructuredGraph graph, TornadoHighTierContext context) {
        run(graph, context);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (graph.hasLoops()) {
            boolean peeled;
            do {
                peeled = false;
                final LoopsData dataCounted = new LoopsData(graph);
                dataCounted.detectedCountedLoops();
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (shouldFullUnroll(graph.getOptions(), loop)) {
                        LoopBeginNode loopBegin = loop.loopBegin();

                        List<EndNode> snapshot = graph.getNodes().filter(EndNode.class).snapshot();

                        System.out.println(loopBegin.predecessor());
                        System.out.println("LOOP BEGIN PRE 0");

                        int idx = 0;
                        for (EndNode end : snapshot) {
                            System.out.println(end);
                            idx++;
                            if (idx == 2) {
                                PragmaUnrollNode unroll = graph.addOrUnique(new PragmaUnrollNode(2));
                                graph.addBeforeFixed(end, unroll);
                            }

                        }
                        peeled = false;
                        break;
                    }
                }
            } while (peeled);
        }

    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
