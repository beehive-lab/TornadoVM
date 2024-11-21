/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import jdk.vm.ci.meta.JavaConstant;
]import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import java.util.Optional;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatDivNode;


public class InfinityReplacementPhase  extends Phase {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        graph.getNodes().filter(ConstantNode.class).forEach(constantNode -> {


            String resultValue = constantNode.getValue().toValueString(); // Get the result of the division
            if (resultValue.equals("Infinity") || resultValue.equals("-Infinity")) {

                if (resultValue.equals("Infinity")) {
                    ConstantNode constantOne = ConstantNode.forConstant(JavaConstant.forFloat(1.0f), null, graph);
                    ConstantNode constantZero = ConstantNode.forConstant(JavaConstant.forFloat(0.0f), null, graph);

                    // Step 2: Create the division node (1.0f / 0.0f)
                    FloatDivNode divisionNode = new FloatDivNode(constantOne, constantZero);

                    // Add the division node to the graph
                    graph.addOrUnique(divisionNode);

                    constantNode.replaceAtUsages(divisionNode);
                    if (constantNode.hasNoUsages()) {
                        constantNode.safeDelete();
                    }

                } else if (resultValue.equals("-Infinity")) {
                    ConstantNode constantMinusOne = ConstantNode.forConstant(JavaConstant.forFloat(-1.0f), null, graph);
                    ConstantNode constantZero = ConstantNode.forConstant(JavaConstant.forFloat(0.0f), null, graph);

                    // Step 2: Create the division node (1.0f / 0.0f)
                    FloatDivNode divisionNode = new FloatDivNode(constantMinusOne, constantZero);

                    // Add the division node to the graph
                    graph.addOrUnique(divisionNode);

                    constantNode.replaceAtUsages(divisionNode);
                    if (constantNode.hasNoUsages()) {
                        constantNode.safeDelete();
                    }
                }
            }
        });
    }
}
