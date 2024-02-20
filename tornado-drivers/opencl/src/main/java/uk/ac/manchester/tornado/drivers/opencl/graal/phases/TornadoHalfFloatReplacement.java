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

import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ReadHalfFloatNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.WriteHalfFloatNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.runtime.graal.HalfFloatConstant;
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

        for (NewInstanceNode newInstanceNode : graph.getNodes().filter(NewInstanceNode.class)) {
            if (newInstanceNode.instanceClass().toString().contains("HalfFloat")) {
                if (newInstanceNode.successors().first() instanceof NewHalfFloatInstance) {
                    NewHalfFloatInstance newHalfFloatInstance = (NewHalfFloatInstance) newInstanceNode.successors().first();
                    ValueNode valueInput = newHalfFloatInstance.getValue();
                    if (valueInput instanceof ConstantNode) {
                        ConstantNode constantNode = (ConstantNode) valueInput;
                        String value = constantNode.getValue().toValueString();
                        Float valueFloat = Float.parseFloat(value);
                        short shortValue = valueFloat.shortValue();
                        Constant shortconst = new RawConstant(shortValue);
                        ConstantNode newCostantNode = new ConstantNode(shortconst, StampFactory.forKind(JavaKind.Short));
                        graph.addWithoutUnique(newCostantNode);
                        HalfFloatConstant halfFloatConstant = new HalfFloatConstant(newCostantNode);
                        graph.addWithoutUnique(halfFloatConstant);
                        newInstanceNode.replaceAtUsages(halfFloatConstant);
                        if (valueInput.usages().isEmpty()) {
                            valueInput.safeDelete();
                        }
                    } else {
                        newInstanceNode.replaceAtUsages(valueInput);
                    }
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

    private static void replaceAddHalfFloatNodes(StructuredGraph graph) {
        for (AddHalfFloatNode addHalfFloatNode : graph.getNodes().filter(AddHalfFloatNode.class)) {
            //addHalfFloatNode.getX().setStamp(StampFactory.forKind(JavaKind.Short));
            //addHalfFloatNode.getY().setStamp(StampFactory.forKind(JavaKind.Short));
            ValueNode addX, addY;
            if (addHalfFloatNode.getX() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) addHalfFloatNode.getX();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                addX = newld;
            } else {
                addX = addHalfFloatNode.getX();
            }

            if (addHalfFloatNode.getY() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) addHalfFloatNode.getY();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                addY = newld;
            } else {
                addY = addHalfFloatNode.getY();
            }

            AddNode addNode = new AddNode(addX, addY);
            graph.addWithoutUnique(addNode);
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
        }
    }

    private static void replaceSubHalfFloatNodes(StructuredGraph graph) {
        for (SubHalfFloatNode subHalfFloatNode : graph.getNodes().filter(SubHalfFloatNode.class)) {
            ValueNode subX, subY;
            if (subHalfFloatNode.getX() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) subHalfFloatNode.getX();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                subX = newld;
            } else {
                subX = subHalfFloatNode.getX();
            }

            if (subHalfFloatNode.getY() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) subHalfFloatNode.getY();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                subY = newld;
            } else {
                subY = subHalfFloatNode.getY();
            }

            SubNode subNode = new SubNode(subX, subY);
            graph.addWithoutUnique(subNode);
            subHalfFloatNode.replaceAtUsages(subNode);
            subHalfFloatNode.safeDelete();
        }
    }

    private static void replaceMultHalfFloatNodes(StructuredGraph graph) {
        for (MultHalfFloatNode multHalfFloatNode : graph.getNodes().filter(MultHalfFloatNode.class)) {
            ValueNode multX, multY;
            if (multHalfFloatNode.getX() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) multHalfFloatNode.getX();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                multX = newld;
            } else {
                multX = multHalfFloatNode.getX();
            }

            if (multHalfFloatNode.getY() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) multHalfFloatNode.getY();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                multY = newld;
            } else {
                multY = multHalfFloatNode.getY();
            }

            MulNode mulNode = new MulNode(multX, multY);
            graph.addWithoutUnique(mulNode);
            multHalfFloatNode.replaceAtUsages(mulNode);
            multHalfFloatNode.safeDelete();
        }
    }

    private static void replaceDivHalfFloatNodes(StructuredGraph graph) {
        for (DivHalfFloatNode divHalfFloatNode : graph.getNodes().filter(DivHalfFloatNode.class)) {
            ValueNode divX, divY;
            if (divHalfFloatNode.getX() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) divHalfFloatNode.getX();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                divX = newld;
            } else {
                divX = divHalfFloatNode.getX();
            }

            if (divHalfFloatNode.getY() instanceof VectorLoadElementNode) {
                VectorLoadElementNode ld = (VectorLoadElementNode) divHalfFloatNode.getY();
                VectorLoadElementNode newld = new VectorLoadElementNode(OCLKind.SHORT, ld.getVector(), ld.getLaneId());
                graph.addWithoutUnique(newld);
                divY = newld;
            } else {
                divY = divHalfFloatNode.getY();
            }

            FloatDivNode divNode = new FloatDivNode(divX, divY);
            graph.addWithoutUnique(divNode);
            divHalfFloatNode.replaceAtUsages(divNode);
            divHalfFloatNode.safeDelete();
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
