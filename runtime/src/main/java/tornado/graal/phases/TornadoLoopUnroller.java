/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.graal.phases;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.CountedLoopInfo;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.loop.phases.LoopTransformations;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.debug.Debug.INFO_LEVEL;
import static org.graalvm.compiler.loop.DefaultLoopPolicies.ExactFullUnrollMaxNodes;
import static org.graalvm.compiler.loop.DefaultLoopPolicies.FullUnrollMaxNodes;

public class TornadoLoopUnroller extends BasePhase<PhaseContext> {

    private final CanonicalizerPhase canonicalizer;

    public TornadoLoopUnroller(CanonicalizerPhase canonicalizer) {
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
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    public void execute(StructuredGraph graph, PhaseContext context) {
        run(graph, context);
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        if (graph.hasLoops()) {
            boolean peeled;
            do {
                peeled = false;
                final LoopsData dataCounted = new LoopsData(graph);
                dataCounted.detectedCountedLoops();
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (shouldFullUnroll(graph.getOptions(), loop)) {
                        Debug.log("FullUnroll %s", loop);
                        LoopTransformations.fullUnroll(loop, context, canonicalizer);
                        Debug.dump(INFO_LEVEL, graph, "After fullUnroll %s", loop);
                        peeled = true;
                        break;
                    }
                }
                dataCounted.deleteUnusedNodes();
            } while (peeled);
        }
    }
}
