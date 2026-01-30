/*
 * Copyright (c) 2020, 2024 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal.phases.sketcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelOffsetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelStrideNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoAutoParalleliser extends BasePhase<TornadoSketchTierContext> {

    private TornadoLogger logger = new TornadoLogger(this.getClass());

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        if (!TornadoOptions.AUTO_PARALLELISATION || graph.getNodes().filter(ParallelRangeNode.class).isNotEmpty()) {
            logger.info("auto parallelisation disabled");
            return;
        }
        autoParallelise(graph);
    }

    private void autoParallelise(StructuredGraph graph) {
        if (graph.hasLoops()) {
            final LoopsData data = new TornadoLoopsData(graph);
            data.detectCountedLoops();

            final List<LoopEx> loops = data.outerFirst();

            // is single loop nest?
            for (int i = loops.size() - 1; i > 1; i--) {
                if (loops.get(i).parent() != loops.get(i - 1)) {
                    logger.info("method %s does not have a single loop-nest", graph.method().getName());
                    return;
                }
            }

            int parallelDepth = 0;
            for (int i = 0; i < loops.size() && parallelDepth < 3; i++) {
                final LoopEx current = loops.get(0);
                final LoopBeginNode loopBegin = current.loopBegin();
                final EconomicMap<Node, InductionVariable> ivMap = current.getInductionVariables();

                logger.info("%s loop info:\n", loopBegin);
                final Set<Node> phis = new HashSet<>();
                for (PhiNode phi : loopBegin.phis()) {
                    logger.info("\tphi: %s\n", phi);
                    phis.add(phi);
                }

                List<InductionVariable> ivs = new ArrayList<>();
                for (Node n : ivMap.getKeys()) {
                    logger.info("\tiv: node=%s iv=%s\n", n, ivMap.get(n));
                    phis.remove(n);
                    ivs.add(ivMap.get(n));
                }

                if (!phis.isEmpty()) {
                    logger.info("unable to parallelise because of loop-dependencies:\n");
                    for (Node n : phis) {
                        PhiNode phi = (PhiNode) n;
                        StringBuilder sb = new StringBuilder();
                        for (ValueNode value : phi.values()) {
                            if (value.getNodeSourcePosition() != null) {
                                sb.append(value.getNodeSourcePosition().toString());
                            }
                            sb.append("\n");
                        }
                        logger.info("\tnode %s updated:\n", n);
                        logger.info(sb.toString().trim());
                    }
                    throw new TornadoBailoutRuntimeException("unable to parallelise because of loop-dependencies.");
                }

                if (ivs.size() > 1) {
                    logger.debug("Too many ivs");
                    return;
                }

                final InductionVariable iv = ivs.getFirst();
                ValueNode maxIterations;

                List<IntegerLessThanNode> conditions = iv.valueNode().usages().filter(IntegerLessThanNode.class).snapshot();
                final IntegerLessThanNode lessThan = conditions.getFirst();
                maxIterations = lessThan.getY();

                parallelizationReplacement(graph, iv, parallelDepth, maxIterations, conditions);

                parallelDepth++;
            }
            logger.info("automatically parallelised %s (%dD kernel)\n", graph.method().getName(), parallelDepth);
        }
    }

    private void parallelizationReplacement(StructuredGraph graph, InductionVariable iv, int parallelDepth, ValueNode maxIterations, List<IntegerLessThanNode> conditions)
            throws TornadoCompilationException {
        if (iv.isConstantInit() && iv.isConstantStride()) {
            final ConstantNode newInit = graph.addWithoutUnique(ConstantNode.forInt((int) iv.constantInit()));

            final ConstantNode newStride = graph.addWithoutUnique(ConstantNode.forInt((int) iv.constantStride()));

            final ParallelOffsetNode offset = graph.addWithoutUnique(new ParallelOffsetNode(parallelDepth, newInit));

            final ParallelStrideNode stride = graph.addWithoutUnique(new ParallelStrideNode(parallelDepth, newStride));

            final ParallelRangeNode range = graph.addWithoutUnique(new ParallelRangeNode(parallelDepth, maxIterations, offset, stride));

            final ValuePhiNode phi = (ValuePhiNode) iv.valueNode();

            final ValueNode oldStride = phi.singleBackValueOrThis(); // was singleBackValue()

            if (oldStride.usages().count() > 1) {
                final ValueNode duplicateStride = (ValueNode) oldStride.copyWithInputs(true);
                oldStride.replaceAtMatchingUsages(duplicateStride, usage -> !usage.equals(phi));
            }

            iv.initNode().replaceAtMatchingUsages(offset, node -> node.equals(phi));
            iv.strideNode().replaceAtMatchingUsages(stride, node -> node.equals(oldStride));

            // only replace this node in the loop condition
            maxIterations.replaceAtMatchingUsages(range, node -> node.equals(conditions.get(0)));

        } else {
            throw new TornadoBailoutRuntimeException("Failed to parallelize because of non-constant loop strides. \nSequential code will run on the device.");
        }
    }
}
