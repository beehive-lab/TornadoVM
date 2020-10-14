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

import java.util.Iterator;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.IncAtomicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TornadoAtomicIntegerNode;

/**
 * This phase scans the IR and checks whether there is a
 * {@link TornadoAtomicIntegerNode} associated for each {@link IncAtomicNode}.
 * <p>
 * If it is not the case, this would mean that the atomic is passed as a
 * parameter to the lambda expression. Therefore, this phase introduces the
 * {@link TornadoAtomicIntegerNode} at the beginning of the Control-Flow-Graph.
 */
public class TornadoAtomicsParametersPhase extends Phase {

    private int numAtomicsBefore(NodeIterable<ParameterNode> parameterNodes, int index) {

        int i = 0;
        Iterator<ParameterNode> iterator = parameterNodes.iterator();
        int atomicsBefore = 0;
        while (i < index) {
            ParameterNode next = iterator.next();
            if (next.stamp(NodeView.DEFAULT).javaType(null).toClassName().equals("java.util.concurrent.atomic.AtomicInteger")) {
                atomicsBefore++;
            }
            i++;
        }
        return atomicsBefore;
    }

    @Override
    protected void run(StructuredGraph graph) {

        NodeIterable<IncAtomicNode> filter = graph.getNodes().filter(IncAtomicNode.class);
        if (!filter.isEmpty()) {
            NodeIterable<ParameterNode> parameterNodes = graph.getNodes(ParameterNode.TYPE);
            for (IncAtomicNode atomic : filter) {
                if (atomic.getAtomicNode() instanceof ParameterNode) {

                    ParameterNode atomicArgument = (ParameterNode) atomic.getAtomicNode();
                    int indexNode = atomicArgument.index();
                    int before = numAtomicsBefore(parameterNodes, indexNode);

                    TornadoAtomicIntegerNode newNode = new TornadoAtomicIntegerNode(OCLKind.INTEGER_ATOMIC_JAVA);
                    graph.addOrUnique(newNode);
                    newNode.assignIndexFromParameter(indexNode, before);

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
