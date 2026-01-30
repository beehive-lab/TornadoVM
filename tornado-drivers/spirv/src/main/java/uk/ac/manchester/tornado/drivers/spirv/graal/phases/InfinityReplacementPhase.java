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
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import jdk.vm.ci.meta.JavaConstant;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.Phase;

import java.util.Optional;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;

/**
 * The {@link InfinityReplacementPhase} class is responsible for identifying and replacing instances
 * of positive and negative infinity constants in the graph with division operations.
 * <p>
 * Specifically, this phase looks for constant nodes whose values are "Infinity" or "-Infinity" and replaces
 * them with a division node that represents the result of dividing either 1.0f or -1.0f by 0.0f,
 * depending on whether the original value was "Infinity" or "-Infinity".
 */
public class InfinityReplacementPhase extends Phase {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    /**
     * Runs the transformation on the provided graph by searching for constant nodes
     * with values of "Infinity" or "-Infinity" and replacing them with division nodes
     * representing {@code 1.0f / 0.0f} or {@code -1.0f / 0.0f}, respectively.
     *
     * @param graph the {@link StructuredGraph} to process
     */
    @Override
    protected void run(StructuredGraph graph) {
        graph.getNodes().filter(ConstantNode.class).forEach(constantNode -> {
            String resultValue = constantNode.getValue().toValueString();
            if (resultValue.equals("Infinity") || resultValue.equals("-Infinity")) {
                float constantValue = resultValue.equals("Infinity") ? 1.0f : -1.0f;
                replaceWithDivisionNode(constantValue, graph, constantNode);
            }
        });
    }

    /**
     * Replaces the given constant node with a division node that divides a constant value by zero.
     * This division represents either {@code 1.0f / 0.0f} or {@code -1.0f / 0.0f} depending on the
     * {@code constantValue} passed in.
     *
     * @param constantValue the constant value (either 1.0f or -1.0f) to use in the division
     * @param graph the {@link StructuredGraph} that contains the nodes
     * @param constantNode the original constant node to be replaced
     */
    private void replaceWithDivisionNode(float constantValue, StructuredGraph graph, ConstantNode constantNode) {
        ConstantNode constant = ConstantNode.forConstant(JavaConstant.forFloat(constantValue), null, graph);
        ConstantNode constantZero = ConstantNode.forConstant(JavaConstant.forFloat(0.0f), null, graph);

        // Create the division node (constantValue / 0.0f)
        FloatDivNode divisionNode = new FloatDivNode(constant, constantZero);

        // Add the division node to the graph
        graph.addOrUnique(divisionNode);

        constantNode.replaceAtUsages(divisionNode);
        if (constantNode.hasNoUsages()) {
            constantNode.safeDelete();
        }
    }

}
