/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import java.util.Optional;

import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.phases.Phase;

import jdk.vm.ci.meta.JavaConstant;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.PartialUnrollNode;

/**
 * Compilation phase that inserts a {@link PartialUnrollNode} right after the {@link LoopBeginNode}
 * only for the loops in which unrolling can be applied. TornadoVM does not unroll parallel loops,
 * It only inserts a partial unroll for sequential loops within the kernel.
 *
 * <p>Note that both "Unroll" and "PartialUnroll" control loop for SPIR-V are considered requests to the
 * underlying compiler. Thus, these instructions do not force loop unroll.
 * </p>
 */
public class PartialLoopUnrollPhase extends Phase {

    final int LOOP_UNROLL_FACTOR = 32;

    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private int getLoopIncrement(ValueNode valueNode) {
        if (valueNode instanceof PhiNode phiNode) {
            ValueNode incrementOperation = phiNode.values().get(1);
            if (incrementOperation instanceof BinaryNode binaryNode) {
                if (binaryNode.getX() instanceof ConstantNode constantNode) {
                    return constantNode.asJavaConstant().asInt();
                } else if (binaryNode.getY() instanceof ConstantNode constantNode) {
                    return constantNode.asJavaConstant().asInt();
                }
            }
        }
        return 1;
    }

    private int getLoopBound(ValueNode valueNode) {
        if (valueNode instanceof ConstantNode constantNode) {
            JavaConstant javaConstant = constantNode.asJavaConstant();
            return javaConstant.asInt();
        }
        return 0;
    }

    @Override
    protected void run(StructuredGraph graph) {

        graph.getNodes().filter(LoopBeginNode.class).forEach(loopBeginNode -> {
            boolean candidateForUnroll = true;
            NodeIterable<ValuePhiNode> valuePhiNodes = loopBeginNode.valuePhis();
            // obtain the loop bound
            int loopBound = 0;

            // set the default increment
            int increments = 1;

            // We get the graal unroll factor to detect if graal also will inline the loop
            int graalUnrollFactor = loopBeginNode.getUnrollFactor();

            // Obtain loop bound and increments
            if (loopBeginNode.next() instanceof IfNode ifNode) {
                if (ifNode.condition() instanceof IntegerLessThanNode lessThanNode) {
                    loopBound = getLoopBound(lessThanNode.getY());
                    increments = getLoopIncrement(lessThanNode.getX());
                }
            }

            // Detect loop is parallel or sequential
            for (ValuePhiNode phiNode : valuePhiNodes) {
                NodeInputList<ValueNode> values = phiNode.values();
                for (ValueNode value : values) {
                    if (value instanceof GlobalThreadIdNode) {
                        // We detected a parallel loop, thus, we avoid the insertion
                        // of the partial loop unroll.
                        candidateForUnroll = false;
                        break;
                    }
                }
                if (!candidateForUnroll) {
                    break;
                }
            }

            if (graalUnrollFactor == loopBound) {
                // loop was fully unrolled by graalJTT
                return;
            }

            loopBound /= graalUnrollFactor;
            loopBound /= increments;

            if (candidateForUnroll && loopBound != 0) {
                // Insert unroll node
                // TODO: Pass the unroll factor from the API level

                // Depending on LoopBound, check if we can apply unroll
                if ((loopBound > LOOP_UNROLL_FACTOR) && (loopBound % LOOP_UNROLL_FACTOR == 0)) {
                    PartialUnrollNode partialUnrollNode = graph.addOrUnique(new PartialUnrollNode(LOOP_UNROLL_FACTOR));
                    FixedNode successorLoop = loopBeginNode.next();
                    loopBeginNode.setNext(partialUnrollNode);
                    partialUnrollNode.setNext(successorLoop);
                }
            }
        });
    }
}
