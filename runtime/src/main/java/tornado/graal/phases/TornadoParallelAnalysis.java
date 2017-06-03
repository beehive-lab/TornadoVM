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

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeBitMap;
import com.oracle.graal.loop.InductionVariable;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.BasePhase;
import java.util.ArrayDeque;
import java.util.Queue;

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
            for (LoopEx loop : data.innerFirst()) {
                for (Node n : loop.getInductionVariables().keySet()) {
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
