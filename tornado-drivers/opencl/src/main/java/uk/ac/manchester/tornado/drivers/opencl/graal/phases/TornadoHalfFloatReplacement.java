/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.AddHalfFloatNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ReadHalfFloatNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.WriteHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewHalfFloatInstance;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoHalfFloatReplacement extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        // replace reads with halfFloat reads
        for (JavaReadNode javaRead : graph.getNodes().filter(JavaReadNode.class)) {
            if (javaRead.successors().first() instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) javaRead.successors().first();
                if (newInstanceNode.instanceClass().toString().contains("HalfFloat")) {
                    if (newInstanceNode.successors().first() instanceof NewHalfFloatInstance) {
                        NewHalfFloatInstance newHalfFloatInstance = (NewHalfFloatInstance) newInstanceNode.successors().first();
                        deleteFixed(newHalfFloatInstance);
                    }
                    AddressNode readingAddress = javaRead.getAddress();
                    ReadHalfFloatNode readHalfFloatNode = new ReadHalfFloatNode(readingAddress);
                    graph.addWithoutUnique(readHalfFloatNode);
                    replaceFixed(javaRead, readHalfFloatNode);
                    newInstanceNode.replaceAtUsages(readHalfFloatNode);
                    deleteFixed(newInstanceNode);
                }
            }
        }

        // replace writes with halfFloat writes
        for (JavaWriteNode javaWrite : graph.getNodes().filter(JavaWriteNode.class)) {
            if (isWriteHalfFloat(javaWrite)) {
                // This casting is safe to do as it is already checked by the isWriteHalfFloat function
                HalfFloatPlaceholder placeholder = (HalfFloatPlaceholder) javaWrite.value();
                ValueNode writingValue;
                if (javaWrite.predecessor() instanceof NewHalfFloatInstance) {
                    // if a new HalfFloat instance is written
                    NewHalfFloatInstance newHalfFloatInstance = (NewHalfFloatInstance) javaWrite.predecessor();
                    writingValue = newHalfFloatInstance.getValue();
                    if (newHalfFloatInstance.predecessor() instanceof NewInstanceNode) {
                        NewInstanceNode newInstanceNode = (NewInstanceNode) newHalfFloatInstance.predecessor();
                        if (newInstanceNode.instanceClass().toString().contains("HalfFloat")) {
                            deleteFixed(newInstanceNode);
                            deleteFixed(newHalfFloatInstance);
                        }
                    }
                } else {
                    // if the result of an operation or a stored value is written
                    writingValue = placeholder.getInput();
                }
                placeholder.replaceAtUsages(writingValue);
                placeholder.safeDelete();
                AddressNode writingAddress = javaWrite.getAddress();
                WriteHalfFloatNode writeHalfFloatNode = new WriteHalfFloatNode(writingAddress, writingValue);
                graph.addWithoutUnique(writeHalfFloatNode);
                replaceFixed(javaWrite, writeHalfFloatNode);
                deleteFixed(javaWrite);
            }
        }

        for (AddHalfFloatNode addHalfFloatNode : graph.getNodes().filter(AddHalfFloatNode.class)) {
            AddNode addNode = new AddNode(addHalfFloatNode.getX(), addHalfFloatNode.getY());
            graph.addWithoutUnique(addNode);
            addHalfFloatNode.replaceAtUsages(addNode);
            addHalfFloatNode.safeDelete();
        }

    }

    private static boolean isWriteHalfFloat(JavaWriteNode javaWrite) {
        if (javaWrite.value() instanceof HalfFloatPlaceholder) {
            return true;
        }
        return false;
    }

    private static void replaceFixed(Node n, Node other) {
        Node pred = n.predecessor();
        Node suc = n.successors().first();

        n.replaceFirstSuccessor(suc, null);
        n.replaceAtPredecessor(other);
        pred.replaceFirstSuccessor(n, other);
        other.replaceFirstSuccessor(null, suc);

        for (Node us : n.usages()) {
            n.removeUsage(us);
        }
        n.clearInputs();
        n.safeDelete();

    }

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

}
