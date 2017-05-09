/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.graal.phases;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.loop.CountedLoopInfo;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.loop.phases.LoopTransformations;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.debug.ControlFlowAnchorNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.tiers.PhaseContext;

import static com.oracle.graal.compiler.common.GraalOptions.MaximumDesiredSize;
import static com.oracle.graal.debug.Debug.INFO_LOG_LEVEL;
import static com.oracle.graal.loop.DefaultLoopPolicies.ExactFullUnrollMaxNodes;
import static com.oracle.graal.loop.DefaultLoopPolicies.FullUnrollMaxNodes;

public class TornadoLoopUnroller extends BasePhase<PhaseContext> {

    private final CanonicalizerPhase canonicalizer;

    public TornadoLoopUnroller(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public static boolean shouldFullUnroll(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? ExactFullUnrollMaxNodes.getValue() : FullUnrollMaxNodes.getValue();
        maxNodes = Math.min(maxNodes, MaximumDesiredSize.getValue() - loop.loopBegin().graph().getNodeCount());
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
                    if (shouldFullUnroll(loop)) {
                        Debug.log("FullUnroll %s", loop);
                        LoopTransformations.fullUnroll(loop, context, canonicalizer);
                        Debug.dump(INFO_LOG_LEVEL, graph, "After fullUnroll %s", loop);
                        peeled = true;
                        break;
                    }
                }
                dataCounted.deleteUnusedNodes();
            } while (peeled);
        }
    }
}
