/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.util.EconomicMap;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;

import static tornado.common.Tornado.debug;
import static tornado.common.Tornado.info;

public class TornadoAutoParalleliser extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        if (!context.hasMeta() || !context.meta.enableAutoParallelisation() || graph.getNodes().filter(ParallelRangeNode.class).isNotEmpty()) {
            info("auto parallelisation disabled");
            return;
        }

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);
            data.detectedCountedLoops();

            final List<LoopEx> loops = data.outerFirst();
            // Collections.reverse(loops);

            // is single loop nest?
            for (int i = loops.size() - 1; i > 1; i--) {
                if (loops.get(i).parent() != loops.get(i - 1)) {
                    info("method %s does not have a single loop-nest", graph.method().getName());
                    return;
                }
            }

            int parallelDepth = 0;
            for (int i = 0; i < loops.size() && parallelDepth < 3; i++) {
                final LoopEx current = loops.get(0);
                final LoopBeginNode loopBegin = current.loopBegin();
                final EconomicMap<Node, InductionVariable> ivMap = current.getInductionVariables();

                info("%s loop info:\n", loopBegin);
                final Set<Node> phis = new HashSet<>();
                for (PhiNode phi : loopBegin.phis()) {
                    info("\tphi: %s\n", phi);
                    phis.add(phi);
                }

                List<InductionVariable> ivs = new ArrayList<>();
                for (Node n : ivMap.getKeys()) {
                    info("\tiv: node=%s iv=%s\n", n, ivMap.get(n));
                    phis.remove(n);
                    ivs.add(ivMap.get(n));
                }

                if (!phis.isEmpty()) {
                    info("unable to parallelise because of loop-dependencies:\n");
                    for (Node n : phis) {
                        PhiNode phi = (PhiNode) n;
                        StringBuilder sb = new StringBuilder();
                        for (ValueNode value : phi.values()) {
                            if (value.getNodeSourcePosition() != null) {
                                sb.append(value.getNodeSourcePosition().toString());
                            }
                            sb.append("\n");
                        }
                        info("\tnode %s updated:\n", n);
                        info(sb.toString().trim());
                    }
                    return;
                }

                if (ivs.size() > 1) {
                    debug("Too many ivs");
                    return;
                }

                final InductionVariable iv = ivs.get(0);
                ValueNode maxIterations = null;
                List<IntegerLessThanNode> conditions = iv.valueNode().usages()
                        .filter(IntegerLessThanNode.class).snapshot();
                if (conditions.size() == 1) {
                    final IntegerLessThanNode lessThan = conditions.get(0);
                    maxIterations = lessThan.getY();
                } else {
                    debug("Unable to parallelise: multiple uses of iv");
                    continue;
                }

                if (iv.isConstantInit() && iv.isConstantStride()) {

                    final ConstantNode newInit = graph.addWithoutUnique(ConstantNode
                            .forInt((int) iv.constantInit()));
                    final ConstantNode newStride = graph.addWithoutUnique(ConstantNode
                            .forInt((int) iv.constantStride()));

                    final ParallelOffsetNode offset = graph
                            .addWithoutUnique(new ParallelOffsetNode(parallelDepth, newInit));

                    final ParallelStrideNode stride = graph
                            .addWithoutUnique(new ParallelStrideNode(parallelDepth, newStride));

                    final ParallelRangeNode range = graph
                            .addWithoutUnique(new ParallelRangeNode(parallelDepth, maxIterations,
                                    offset, stride));

                    final ValuePhiNode phi = (ValuePhiNode) iv.valueNode();
                    final ValueNode oldStride = phi.singleBackValueOrThis(); // was singleBackValue()

                    //System.out.printf("oldStride: %s\n",oldStride.toString());
                    if (oldStride.usages().count() > 1) {
                        final ValueNode duplicateStride = (ValueNode) oldStride.copyWithInputs(true);

                        oldStride.replaceAtMatchingUsages(duplicateStride, usage -> !usage.equals(phi));

                        //duplicateStride.removeUsage(phi);
                        //oldStride.removeUsage(node)
                    }

                    iv.initNode().replaceAtMatchingUsages(offset, node -> node.equals(phi));
                    iv.strideNode().replaceAtMatchingUsages(stride,
                            node -> node.equals(oldStride));

                    // only replace this node in the loop condition
                    maxIterations.replaceAtMatchingUsages(range, node -> node.equals(conditions.get(0)));

                } else {
                    debug("Unable to parallelise: non-constant stride or offset");
                    continue;
                }
                parallelDepth++;
            }

            info("automatically parallelised %s (%dD kernel)\n", graph.method().getName(), parallelDepth);
        }

    }

}
