/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import java.util.Optional;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.Phase;

import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.IncAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.NodeAtomic;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TornadoAtomicIntegerNode;

/**
 * This phase scans the IR and checks whether there is a
 * {@link TornadoAtomicIntegerNode} associated for each {@link IncAtomicNode}.
 *
 * <p>
 * If it is not the case, this would mean that the atomic is passed as a
 * parameter to the lambda expression. Therefore, this phase introduces the
 * {@link TornadoAtomicIntegerNode} at the beginning of the Control-Flow-Graph.
 * </p>
 */
public class TornadoAtomicsParametersPhase extends Phase {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        NodeIterable<NodeAtomic> filter = graph.getNodes().filter(NodeAtomic.class);

        if (!filter.isEmpty()) {
            for (NodeAtomic atomic : filter) {
                if (atomic.getAtomicNode() instanceof ParameterNode) {

                    ParameterNode atomicArgument = (ParameterNode) atomic.getAtomicNode();
                    int indexNode = atomicArgument.index();

                    TornadoAtomicIntegerNode newNode = new TornadoAtomicIntegerNode(OCLKind.INTEGER_ATOMIC_JAVA);
                    graph.addOrUnique(newNode);
                    newNode.assignIndexFromParameter(indexNode);

                    final ConstantNode index = graph.addOrUnique(ConstantNode.forInt(0));
                    newNode.setInitialValue(index);

                    // Add the new control flow node
                    StartNode startNode = graph.start();
                    FixedNode first = (FixedNode) startNode.successors().first();
                    startNode.setNext(newNode);
                    newNode.setNext(first);

                    // Replace usages for this new node
                    ParameterNode parameter = (ParameterNode) atomic.getAtomicNode();
                    newNode.replaceAtMatchingUsages(atomic, node -> !node.equals(atomic));
                    parameter.replaceAtMatchingUsages(newNode, node -> node.equals(atomic));

                    assert graph.verify();
                }
            }
        }

    }

}
