/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.util.ArrayList;
import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;

public class TornadoHalfFloatFixedGuardElimination extends BasePhase<TornadoSketchTierContext> {

    private static void deleteFixed(Node node) {
        if (!node.isDeleted()) {
            Node predecessor = node.predecessor();
            Node successor = node.successors().first();

            node.replaceFirstSuccessor(successor, null);
            node.replaceAtPredecessor(successor);
            predecessor.replaceFirstSuccessor(node, successor);

            for (Node us : node.usages()) {
                node.removeUsage(us);
            }
            node.clearInputs();
            node.safeDelete();
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        ArrayList<ValueNode> nodesToBeDeleted = new ArrayList<ValueNode>();
        for (HalfFloatPlaceholder placeholderNode : graph.getNodes().filter(HalfFloatPlaceholder.class)) {
            if (placeholderNode.getInput() instanceof PiNode placeholderInput) {
                ValueNode halfFloatValue = placeholderInput.object();
                FixedGuardNode placeholderGuard = (FixedGuardNode) placeholderInput.getGuard();
                if (placeholderGuard.inputs().filter(IsNullNode.class).isNotEmpty()) {
                    IsNullNode isNullNode = placeholderGuard.inputs().filter(IsNullNode.class).first();
                    nodesToBeDeleted.add(isNullNode);
                }
                deleteFixed(placeholderGuard);
                placeholderNode.setInput(halfFloatValue);
                nodesToBeDeleted.add(placeholderInput);
            }
        }

        for (ValueNode node : nodesToBeDeleted) {
            node.safeDelete();
        }
    }
}
