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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;

@Deprecated
public class TornadoParallelAnalysis extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (!context.hasMeta()) {
            return;
        }

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);
            data.detectedCountedLoops();

            int loopIndex = 0;
            final List<LoopEx> loops = data.outerFirst();
            Collections.reverse(loops);
            for (LoopEx loop : loops) {
                for (Node n : loop.getInductionVariables().getKeys()) {
                    NodeBitMap nodes = graph.createNodeBitMap();
                    nodes.clearAll();
                    InductionVariable iv = loop.getInductionVariables().get(n);
                    System.out.printf("iv: node=%s, iv=%s\n", n, iv);

                    Queue<Node> nf = new ArrayDeque<>();
//                    nodes.mark(n);
                    n.usages().forEach(nf::add);

                    while (!nf.isEmpty()) {
                        Node currentNode = nf.remove();
                        if (currentNode.hasUsages()) {
                            currentNode.usages().forEach(node -> {
                                if (!nodes.isMarked(node)) {
                                    nodes.mark(node);
                                    nf.add(node);
                                }
                            });
                        }

//                        if (currentNode instanceof AccessIndexedNode) {
                        System.out.printf("flood: %s\n", currentNode);
//                        }
                    }

                }

            }

        }

    }

}
