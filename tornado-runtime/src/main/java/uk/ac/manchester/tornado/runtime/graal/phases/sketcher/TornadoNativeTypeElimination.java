/*
 * Copyright (c) 2023, 2024 APT Group, Department of Computer Science,
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.PointerEqualsNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelOffsetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoNativeTypeElimination extends BasePhase<TornadoSketchTierContext> {

    private static void removeFixedGuardNodes(FixedGuardNode fixedGuardNode, LoadFieldNode loadFieldSegment) {
        ArrayList<Node> nodesToBeRemoved = new ArrayList<>();
        nodesToBeRemoved.add(fixedGuardNode);
        for (Node node : fixedGuardNode.inputs()) {
            if (node instanceof InstanceOfNode || node instanceof IsNullNode) {
                nodesToBeRemoved.add(node);
            }
        }

        // identify the usages that need to be removed
        for (Node node : fixedGuardNode.usages()) {
            if (node instanceof PiNode piNode && (piNode.usages().filter(OffsetAddressNode.class).isNotEmpty())) {
                for (OffsetAddressNode offsetAddressNode : piNode.usages().filter(OffsetAddressNode.class).snapshot()) {
                    if (piHasParameterInput(piNode)) {
                        return;
                    }
                    if (piNodeUsageReplacement(offsetAddressNode, piNode, loadFieldSegment)) {
                        nodesToBeRemoved.add(piNode);
                    }
                }
            } else if (node instanceof PiNode piNode && (piNode.usages().filter(LoadHubNode.class).isNotEmpty())) {
                //NOTE: This is a special case where Graal includes additional FixedGuardNodes during sketching
                // It was encountered in the TestMatrixTypes unittest
                nodesToBeRemoved.add(piNode);
                LoadHubNode loadHubNode = piNode.usages().filter(LoadHubNode.class).first();
                nodesToBeRemoved.add(loadHubNode);
                for (Node pointerEqualsNode : loadHubNode.usages().filter(PointerEqualsNode.class)) {
                    nodesToBeRemoved.add(pointerEqualsNode);
                    // the constant node associated with the PointerEquals node is not necessary and should also be removed
                    nodesToBeRemoved.add(pointerEqualsNode.inputs().filter(ConstantNode.class).first());
                    if (pointerEqualsNode.usages().filter(FixedGuardNode.class).isNotEmpty()) {
                        FixedGuardNode typeCheckingFixed = pointerEqualsNode.usages().filter(FixedGuardNode.class).first();
                        nodesToBeRemoved.add(typeCheckingFixed);
                        for (PiNode piNodeOfTypeCheckingFixed : typeCheckingFixed.usages().filter(PiNode.class)) {
                            for (OffsetAddressNode offsetAddressNode : piNodeOfTypeCheckingFixed.usages().filter(OffsetAddressNode.class).snapshot()) {
                                if (piHasParameterInput(piNodeOfTypeCheckingFixed)) {
                                    return;
                                }
                                if (piNodeUsageReplacement(offsetAddressNode, piNodeOfTypeCheckingFixed, loadFieldSegment)) {
                                    nodesToBeRemoved.add(piNodeOfTypeCheckingFixed);
                                }
                            }
                        }
                    }
                }
            }
        }
        deleteFixedGuardAndInputNodes(nodesToBeRemoved);
    }

    private static boolean piHasParameterInput(PiNode piNode) {
        return piNode.inputs().filter(ParameterNode.class).isNotEmpty();
    }

    private static boolean piNodeUsageReplacement(OffsetAddressNode off, PiNode piNode, LoadFieldNode loadFieldSegment) {
        if (off.usages().filter(JavaReadNode.class).isNotEmpty() || off.usages().filter(JavaWriteNode.class).isNotEmpty() || off.usages().filter(WriteAtomicNode.class).isNotEmpty()) {
            if (piNode.inputs().filter(LoadFieldNode.class).isNotEmpty()) {
                LoadFieldNode ldf = piNode.inputs().filter(LoadFieldNode.class).first();
                off.replaceFirstInput(piNode, ldf);
            } else {
                off.replaceFirstInput(piNode, loadFieldSegment);
            }
            return true;
        }
        return false;
    }

    private static void deleteFixedGuardAndInputNodes(ArrayList<Node> nodesToBeRemoved) {
        for (Node n : nodesToBeRemoved) {
            if (n != null && !n.isDeleted()) {
                if (n instanceof FixedGuardNode) {
                    deleteFixed(n);
                } else {
                    n.safeDelete();
                }
            }
        }
    }

    private static void deleteFixed(Node node) {
        if (!node.isDeleted()) {
            Node predecessor = node.predecessor();
            Node successor = node.successors().first();

            node.replaceFirstSuccessor(successor, null);
            node.replaceAtPredecessor(successor);
            predecessor.replaceFirstSuccessor(node, successor);

            for (Node us : node.usages().snapshot()) {
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

    private int getElementKindSize(LoadFieldNode baseIndexNode) {
        int kindElement = 0;
        Annotation[] declaredAnnotations = baseIndexNode.field().getDeclaringClass().getDeclaredAnnotations();
        if (declaredAnnotations.length == 0) {
            throw new TornadoRuntimeException("Annotation is missing");
        } else {
            for (Annotation annotation : declaredAnnotations) {
                if (annotation instanceof SegmentElementSize panamaElementSize) {
                    kindElement = panamaElementSize.size();
                }
            }
        }
        return kindElement;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        for (LoadFieldNode loadFieldSegment : graph.getNodes().filter(LoadFieldNode.class).snapshot()) {
            if (loadFieldSegment.toString().contains("segment")) {

                // Remove the loadField#baseIndex and replace it with a Constant value
                if (loadFieldSegment.successors().first() instanceof LoadFieldNode baseIndexNode) {
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

                // CRITICAL FIX: Only replace OffsetAddressNode usages with the segment's object
                // This preserves other field accesses for TaskSpecialisation to evaluate
                for (OffsetAddressNode offsetAddressNode : loadFieldSegment.usages().filter(OffsetAddressNode.class).snapshot()) {
                    offsetAddressNode.replaceFirstInput(loadFieldSegment, loadFieldSegment.object());
                }

                // CRITICAL: Do NOT delete LoadFieldNode if it's used by ParallelOffsetNode
                // ParallelOffsetNode needs the segment load for parallel loop analysis
                boolean hasParallelOffsetUsage = loadFieldSegment.usages().filter(ParallelOffsetNode.class).isNotEmpty();

                if (!hasParallelOffsetUsage) {
                    if (loadFieldSegment.predecessor() instanceof FixedGuardNode fixedGuardNode) {
                        deleteFixed(loadFieldSegment);
                        if (fixedGuardNode.predecessor() instanceof LoadFieldNode ldf) {
                            removeFixedGuardNodes(fixedGuardNode, ldf);
                        }
                    } else {
                        deleteFixed(loadFieldSegment);
                    }
                }
            }
        }
    }
}
