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
import org.graalvm.compiler.phases.tiers.PhaseContext;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.PragmaUnrollNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoPragmaUnroll extends BasePhase<TornadoHighTierContext> {

    private final CanonicalizerPhase canonicalizer;

    public TornadoPragmaUnroll(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public static boolean shouldFullUnroll(OptionValues options, LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {

            System.out.println("Try to unroll 3" + " \n");
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

            System.out.println("Loops: ---> " + loops + "  ifs: ---> " + ifs + "\n");
            if (loops - ifs != 0) {
                // return false;
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

    public static void fullUnroll(LoopEx loop, PhaseContext context, CanonicalizerPhase canonicalizer) {
        LoopBeginNode loopBegin = loop.loopBegin();
        StructuredGraph graph = loopBegin.graph();
        int initialNodeCount = graph.getNodeCount();

        try {
            // FixedNode node =
            Node node = loopBegin.predecessor();
            PragmaUnrollNode unroll = graph.addOrUnique(new PragmaUnrollNode(2));
            graph.add(unroll);

            // node.isAlive();
            System.out.println("Loop begin node predessor: " + node);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return;

        // do {
        // if (loopBegin.isDeleted()) {
        // return;
        // }
        //
        // Graph.Mark mark = graph.getMark();
        // peel(loop);
        // canonicalizer.applyIncremental(graph, context, mark);
        // loop.invalidateFragments();
        // } while (graph.getNodeCount() <= initialNodeCount + (Integer)
        // GraalOptions.MaximumDesiredSize.getValue(graph.getOptions()) * 2);
        //
        // throw new RetryableBailoutException("FullUnroll : Graph seems to grow out of
        // proportion");:w
        /// continue;
    }

    public static void peel(LoopEx loop) {
        loop.inside().duplicate().insertBefore(loop);
        loop.loopBegin().setLoopFrequency(Math.max(0.0D, loop.loopBegin().loopFrequency() - 1.0D));
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

                        System.out.println(loopBegin);

                        List<EndNode> snapshot = graph.getNodes().filter(EndNode.class).snapshot();

                        System.out.println(loopBegin.predecessor());
                        System.out.println("LOOP BEGIN PRE");

                        int idx = 0;
                        for (EndNode end : snapshot) {
                            System.out.println(end);
                            idx++;
                            if (idx == 2) {
                                PragmaUnrollNode unroll = graph.addOrUnique(new PragmaUnrollNode(2));
                                // graph.addAfterFixed(unroll, end);
                                graph.addBeforeFixed(end, unroll);
                                // loopBegin.replaceAtPredecessor(unroll);

                                System.out.println("End node successors ---> " + end.successors());
                                System.out.println("End node predecessors ---> " + end.predecessor());
                                System.out.println("End node isAlive ---> " + end.isAlive());

                                // loopBegin.replaceAtPredecessor(unroll);
                                // loopBegin.predecessor().replaceAtPredecessor();
                                System.out.println("Loop begin predecessor --->" + loopBegin.predecessor());
                            }
                            System.out.println("End inputs: - -- ->" + end.inputPositions());

                        }

                        // unroll.setNext(loopBegin);

                        // graph.getNodes().filter(EndNode.class).forEach(Endnode end)--->;
                        // tmp.add(graph.getNodes().filter(EndNode.class);

                        // EndNode endToTrack = graph.getNodes().filter(EndNode.class)

                        System.out.println(loopBegin.predecessor());
                        // loopBegin.predecessor().replaceAtPredecessor(unroll);
                        // graph.addBeforeFixed(unroll, loopBegin);

                        // Debug.dump(INFO_LEVEL, graph, "After fullUnroll %s", loop);
                        peeled = false;
                        break;
                    }
                }
            } while (peeled);
        }

    }

    // public void replaceAtPredecessor(Node other) {
    // Node.checkReplaceWith(other);
    // if (this.predecessor != null) {
    // if (!this.predecessor.getNodeClass().replaceFirstSuccessor(this.predecessor,
    // this, other)) {
    // this.fail("not found in successors, predecessor: %s", this.predecessor);
    // }
    //
    // this.predecessor.updatePredecessor(this, other);
    // }
    //
    // }
    @Override
    public boolean checkContract() {
        return false;
    }
}
