/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

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
