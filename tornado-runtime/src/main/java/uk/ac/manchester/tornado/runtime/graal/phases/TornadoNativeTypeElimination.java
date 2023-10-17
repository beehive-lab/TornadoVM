/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.type.annotations.PanamaElementSize;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;

public class TornadoNativeTypeElimination extends BasePhase<TornadoSketchTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private int getElementKindSize(LoadFieldNode baseIndexNode) {
        int kindElement = 0;
        Annotation[] declaredAnnotations = baseIndexNode.field().getDeclaringClass().getDeclaredAnnotations();
        if (declaredAnnotations.length == 0) {
            throw new TornadoRuntimeException("Annotation is missing");
        } else {
            for (Annotation annotation : declaredAnnotations) {
                if (annotation instanceof PanamaElementSize panamaElementSize) {
                    kindElement = panamaElementSize.size();
                }
            }
        }
        return kindElement;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        for (LoadFieldNode loadFieldSegment : graph.getNodes().filter(LoadFieldNode.class)) {
            if (loadFieldSegment.toString().contains("segment")) {

                // Remove the loadField#baseIndex and replace it with a Constant value
                if (loadFieldSegment.successors().first()instanceof LoadFieldNode baseIndexNode) {
                    int elementKindSize = getElementKindSize(baseIndexNode);
                    int panamaObjectHeaderSize = (int) TornadoOptions.PANAMA_OBJECT_HEADER_SIZE;
                    int baseIndexPosition = panamaObjectHeaderSize / elementKindSize;
                    ConstantNode constantNode = graph.addOrUnique(ConstantNode.forInt(baseIndexPosition));
                    baseIndexNode.replaceAtUsages(constantNode);
                    deleteFixed(baseIndexNode);
                }

                // Remove FixedGuard nodes with its PI Node
                if (loadFieldSegment.successors().filter(FixedGuardNode.class).isNotEmpty()) {
                    FixedGuardNode fixedGuardNode = loadFieldSegment.successors().filter(FixedGuardNode.class).first();
                    removeFixedGuardNodes(fixedGuardNode, loadFieldSegment);
                }

                for (Node in : loadFieldSegment.inputs()) {
                    if (in instanceof PiNode) {
                        for (Node us : loadFieldSegment.usages()) {
                            if (us instanceof OffsetAddressNode) { // USAGE IS PI
                                us.replaceFirstInput(loadFieldSegment, in);
                            }
                        }
                        break;
                    }
                }
                deleteFixed(loadFieldSegment);
            }
        }
    }

    public static void removeFixedGuardNodes(FixedGuardNode fixedGuardNode, LoadFieldNode loadFieldSegment) {
        ArrayList<Node> nodesToBeRemoved = new ArrayList<>();
        for (Node node : fixedGuardNode.inputs()) {
            if (node instanceof InstanceOfNode) {
                nodesToBeRemoved.add(node);
            }
        }

        // identify the usages that need to be removed
        for (Node node : fixedGuardNode.usages()) {
            if (node instanceof PiNode pi && (pi.usages().filter(OffsetAddressNode.class).isNotEmpty())) {
                OffsetAddressNode off = pi.usages().filter(OffsetAddressNode.class).first();
                // if this address node is used by a javaread/javawrite node
                if (off.usages().filter(JavaReadNode.class).isNotEmpty() //
                        || off.usages().filter(JavaWriteNode.class).isNotEmpty() //
                        || off.usages().filter(WriteAtomicNode.class).isNotEmpty()) {
                    off.replaceFirstInput(pi, loadFieldSegment);
                    nodesToBeRemoved.add(pi);
                }
            }
        }
        nodesToBeRemoved.forEach(Node::safeDelete);
        deleteFixed(fixedGuardNode);
    }

    public static void deleteFixed(Node node) {
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
