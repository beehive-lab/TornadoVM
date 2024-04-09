/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import java.util.ArrayList;
import java.util.Optional;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.RawConstant;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.FloatDivNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.internal.annotations.HalfType;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ReadHalfFloatNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.WriteHalfFloatNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorAddHalfNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorMultHalfNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorSubHalfNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.AddHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.DivHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;
import uk.ac.manchester.tornado.runtime.graal.nodes.MultHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewHalfFloatInstance;
import uk.ac.manchester.tornado.runtime.graal.nodes.SubHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.VectorHalfRead;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoHalfFloatReplacement extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        for (ValueAnchorNode valueAnchorNode : graph.getNodes().filter(ValueAnchorNode.class)) {
            ArrayList<PiNode> deletePi = new ArrayList<PiNode>();
            for (Node valueAnchorNodeUsage : valueAnchorNode.usages()) {
                if (valueAnchorNodeUsage instanceof PiNode) {
                    PiNode piNode = (PiNode) valueAnchorNodeUsage;
                    piNode.replaceAtUsages(piNode.object());
                    deletePi.add(piNode);
                }
            }
            for (PiNode p : deletePi) {
                p.safeDelete();
            }
            deleteFixed(valueAnchorNode);
        }

        // replace reads with halfFloat reads
        for (JavaReadNode javaRead : graph.getNodes().filter(JavaReadNode.class)) {
            if (javaRead.successors().first() instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) javaRead.successors().first();
                if (newInstanceNode.instanceClass().getAnnotation(HalfType.class) != null) {
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

        for (NewInstanceNode newInstanceNode : graph.getNodes().filter(NewInstanceNode.class)) {
            if (newInstanceNode.instanceClass().getAnnotation(HalfType.class) != null) {
                if (newInstanceNode.successors().first() instanceof NewHalfFloatInstance) {
                    NewHalfFloatInstance newHalfFloatInstance = (NewHalfFloatInstance) newInstanceNode.successors().first();
                    ValueNode valueInput = newHalfFloatInstance.getValue();
                    newInstanceNode.replaceAtUsages(valueInput);
                    deleteFixed(newInstanceNode);
                    deleteFixed(newHalfFloatInstance);
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

        // replace the half float operator nodes with the corresponding regular operators
        replaceAddHalfFloatNodes(graph);
        replaceSubHalfFloatNodes(graph);
        replaceMultHalfFloatNodes(graph);
        replaceDivHalfFloatNodes(graph);

        // add after the loadindexedvector nodes the marker node to fix the offset of its read

        for (LoadIndexedVectorNode loadIndexedVectorNode : graph.getNodes().filter(LoadIndexedVectorNode.class)) {
            if (loadIndexedVectorNode.getOCLKind().isHalf()) {
                VectorHalfRead vectorHalfRead;
                if (loadIndexedVectorNode.index() instanceof ConstantNode) {
                    ConstantNode offset = (ConstantNode) loadIndexedVectorNode.index();
                    int offsetValue = Integer.valueOf(offset.getValue().toValueString());
                    vectorHalfRead = graph.addWithoutUnique(new VectorHalfRead(offsetValue));
                } else {
                    vectorHalfRead = graph.addWithoutUnique(new VectorHalfRead());
                }
                graph.addAfterFixed(loadIndexedVectorNode, vectorHalfRead);
            }
        }

        for (VectorValueNode vectorValueNode : graph.getNodes().filter(VectorValueNode.class)) {
            if (vectorValueNode.getOCLKind().isHalf()) {
                for (Node vectorElement : vectorValueNode.inputs()) {
                    if (vectorElement instanceof VectorLoadElementNode) {
                        VectorLoadElementNode vectorLoad = (VectorLoadElementNode) vectorElement;
                        VectorLoadElementNode vectorLoadShort = new VectorLoadElementNode(OCLKind.HALF, vectorLoad.getVector(), vectorLoad.getLaneId());
                        graph.addWithoutUnique(vectorLoadShort);
                        vectorLoad.replaceAtUsages(vectorLoadShort);
                        vectorLoad.safeDelete();
                    } else if (vectorElement instanceof ConstantNode constantNode && constantNode.getValue().toValueString().contains("null")) {
                        Constant zeroValue = new RawConstant(0);
                        ConstantNode zero = new ConstantNode(zeroValue, StampFactory.forKind(JavaKind.Short));
                        graph.addWithoutUnique(zero);
                        constantNode.replaceAtUsages(zero);
                        constantNode.safeDelete();
                    }
                }
            }
        }

    }

    private static ValueNode replaceAdd(AddHalfFloatNode addHalfFloatNode, StructuredGraph graph) {
        ValueNode addNode;
        ValueNode addX = getHalfOperand(addHalfFloatNode.getX(), graph);
        ValueNode addY = getHalfOperand(addHalfFloatNode.getY(), graph);
        if (addX instanceof VectorLoadElementNode || addY instanceof VectorLoadElementNode) {
            addNode = new VectorAddHalfNode(addX, addY);
            graph.addWithoutUnique(addNode);
        } else {
            addNode = new AddNode(addX, addY);
            graph.addWithoutUnique(addNode);
        }

        if (addHalfFloatNode.usages().filter(PiNode.class).isNotEmpty()) {
            PiNode piNode = addHalfFloatNode.usages().filter(PiNode.class).first();
            if (piNode.inputs().filter(ValueAnchorNode.class).isNotEmpty()) {
                ValueAnchorNode anchorNode = piNode.inputs().filter(ValueAnchorNode.class).first();
                deleteFixed(anchorNode);
                piNode.replaceAtUsages(addNode);
                piNode.safeDelete();
            } else {
                piNode.replaceAtUsages(addNode);
                piNode.safeDelete();
            }
        } else {
            addHalfFloatNode.replaceAtUsages(addNode);
        }
        addHalfFloatNode.safeDelete();
        return addNode;
    }

    private static void replaceAddHalfFloatNodes(StructuredGraph graph) {
        for (AddHalfFloatNode addHalfFloatNode : graph.getNodes().filter(AddHalfFloatNode.class)) {
            replaceAdd(addHalfFloatNode, graph);
        }
    }

    public static ValueNode replaceSub(SubHalfFloatNode subHalfFloatNode, StructuredGraph graph) {
        ValueNode subNode;
        ValueNode subX = getHalfOperand(subHalfFloatNode.getX(), graph);
        ValueNode subY = getHalfOperand(subHalfFloatNode.getY(), graph);

        if (subX instanceof VectorLoadElementNode || subY instanceof VectorLoadElementNode) {
            subNode = new VectorSubHalfNode(subX, subY);
            graph.addWithoutUnique(subNode);
        } else {
            subNode = new SubNode(subX, subY);
            graph.addWithoutUnique(subNode);
        }

        subHalfFloatNode.replaceAtUsages(subNode);
        subHalfFloatNode.safeDelete();
        return subNode;
    }

    private static void replaceSubHalfFloatNodes(StructuredGraph graph) {
        for (SubHalfFloatNode subHalfFloatNode : graph.getNodes().filter(SubHalfFloatNode.class)) {
            replaceSub(subHalfFloatNode, graph);
        }
    }

    private static ValueNode replaceMult(MultHalfFloatNode multHalfFloatNode, StructuredGraph graph) {
        ValueNode multNode;
        ValueNode multX = getHalfOperand(multHalfFloatNode.getX(), graph);
        ValueNode multY = getHalfOperand(multHalfFloatNode.getY(), graph);

        if (multX instanceof VectorLoadElementNode || multY instanceof VectorLoadElementNode) {
            multNode = new VectorMultHalfNode(multX, multY);
            graph.addWithoutUnique(multNode);
        } else {
            multNode = new MulNode(multX, multY);
            graph.addWithoutUnique(multNode);
        }

        multHalfFloatNode.replaceAtUsages(multNode);
        multHalfFloatNode.safeDelete();
        return multNode;
    }

    private static void replaceMultHalfFloatNodes(StructuredGraph graph) {
        for (MultHalfFloatNode multHalfFloatNode : graph.getNodes().filter(MultHalfFloatNode.class)) {
            replaceMult(multHalfFloatNode, graph);
        }
    }

    private static ValueNode replaceDiv(DivHalfFloatNode divHalfFloatNode, StructuredGraph graph) {
        ValueNode divX = getHalfOperand(divHalfFloatNode.getX(), graph);
        ValueNode divY = getHalfOperand(divHalfFloatNode.getY(), graph);

        FloatDivNode divNode = new FloatDivNode(divX, divY);
        graph.addWithoutUnique(divNode);

        divHalfFloatNode.replaceAtUsages(divNode);
        divHalfFloatNode.safeDelete();
        return divNode;
    }

    private static void replaceDivHalfFloatNodes(StructuredGraph graph) {
        for (DivHalfFloatNode divHalfFloatNode : graph.getNodes().filter(DivHalfFloatNode.class)) {
            replaceDiv(divHalfFloatNode, graph);
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

    private static ValueNode getHalfOperand(ValueNode operand, StructuredGraph graph) {
        ValueNode halfOperand;
        if (operand instanceof VectorLoadElementNode) {
            VectorLoadElementNode loadElementNodeX = (VectorLoadElementNode) operand;
            halfOperand = new VectorLoadElementNode(OCLKind.HALF, loadElementNodeX.getVector(), loadElementNodeX.getLaneId());
            graph.addWithoutUnique(halfOperand);
        } else if (operand instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) operand;
            halfOperand = new ConstantNode(c.getValue(), StampFactory.forKind(JavaKind.Short));
            graph.addWithoutUnique(halfOperand);
        } else if (operand instanceof MultHalfFloatNode) { // if operand is another half operator, replace this first to avoid issues with the Stamp
            halfOperand = replaceMult((MultHalfFloatNode) operand, graph);
        } else if (operand instanceof AddHalfFloatNode) {
            halfOperand = replaceAdd((AddHalfFloatNode) operand, graph);
        } else if (operand instanceof SubHalfFloatNode) {
            halfOperand = replaceSub((SubHalfFloatNode) operand, graph);
        } else if (operand instanceof DivHalfFloatNode) {
            halfOperand = replaceDiv((DivHalfFloatNode) operand, graph);
        } else {
            halfOperand = operand;
        }
        return halfOperand;
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
