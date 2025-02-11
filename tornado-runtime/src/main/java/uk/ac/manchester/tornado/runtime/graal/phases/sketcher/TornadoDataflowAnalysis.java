/*
 * Copyright (c) 2020, 2023, 2024 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelStrideNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkVectorStore;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoDataflowAnalysis extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        Access[] accesses = context.getAccesses();
        TornadoLogger logger = new TornadoLogger(this.getClass());

        for (int i = 0; i < accesses.length; i++) {
            accesses[i] = Access.NONE;
            ParameterNode param = graph.getParameter(i);

            // Only interested in objects
            if (param != null && param.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                accesses[i] = processUsages(param, context.getMetaAccess());
            }
            logger.debug("access: parameter %d -> %s\n", i, accesses[i]);
        }
        logger.debug("[Compiler Pass] TornadoVM DataFlow Analysis finished");
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private boolean checkIgnoreStride(ParallelRangeNode range) {
        ValueNode value = range.stride().value();
        if (value instanceof ConstantNode c) {
            Constant value2 = c.getValue();
            String v = value2.toValueString();
            int stride = Integer.parseInt(v);
            return (stride == 1);
        }
        return false;
    }

    private boolean shouldIgnoreNode(IfNode ifNode, IfNode fatherNodeStore) {
        // Check first if the IF node controls stride, in which case we should
        // only ignore if the stride is 1.
        boolean ignore = false;
        if (ifNode.condition() instanceof BinaryOpLogicNode condition) {
            if (condition.getX() instanceof ParallelRangeNode) {
                ignore = checkIgnoreStride((ParallelRangeNode) condition.getX());
            } else if (condition.getY() instanceof ParallelRangeNode) {
                ignore = checkIgnoreStride((ParallelRangeNode) condition.getY());
            }
        }

        if (ignore) {
            return true;
        }

        // Check if the IF node found is different from the one previously
        // recorded.
        if (fatherNodeStore != null) {
            // We found different father IF node for each
            // branch.
            return (!fatherNodeStore.equals(ifNode));
        }
        return false;
    }

    /*
     * For a given node store in the IR, it checks whether the store is also
     * performed in another branch of the code. If it that the case, the variable
     * should be just WRITE, otherwise, it should be READ_WRITE.
     */
    private MetaControlFlow analyseControlFlowForWriting(final Node currentNode, IfNode fatherNodeStore, final boolean isWrittenTrueCondition, final boolean isWrittenFalseCondition) {
        boolean trueCondition = isWrittenTrueCondition;
        boolean falseCondition = isWrittenFalseCondition;
        boolean exit = false;
        Node predecessor = currentNode;
        Node next = predecessor;
        while (!exit && !(predecessor instanceof StartNode)) {
            predecessor = predecessor.predecessor();
            if (predecessor == null) {
                break;
            }
            if (predecessor instanceof IfNode ifNode) {

                if (shouldIgnoreNode(ifNode, fatherNodeStore)) {
                    continue;
                }

                if (ifNode.trueSuccessor() == next) {
                    trueCondition = true;
                } else if (ifNode.falseSuccessor() == next) {
                    falseCondition = true;
                }
                fatherNodeStore = ifNode;
                exit = true;
            }
            next = predecessor;
        }
        return new MetaControlFlow(trueCondition, falseCondition, fatherNodeStore);
    }

    private boolean isNodeFromKnownObject(Node currentNode) {
        // Comparison based on names due to circular dependencies
        return currentNode.getClass().getName().equals("uk.ac.manchester.tornado.drivers.opencl.graal.nodes.IncAtomicNode") || currentNode.getClass().getName().equals(
                "uk.ac.manchester.tornado.drivers.opencl.graal.nodes.DecAtomicNode");
    }

    private Access processUsages(Node parameter, MetaAccessProvider metaAccess) {

        boolean isRead = false;
        boolean isWritten = false;
        boolean isReadField = false;
        boolean isWrittenField = false;

        Queue<Node> nodesToProcess = new ArrayDeque<>();
        parameter.usages().forEach(nodesToProcess::add);

        boolean isWrittenTrueCondition = false;
        boolean isWrittenFalseCondition = false;
        IfNode fatherNodeStore = null;

        boolean isWritingAllPositions = false;

        while (!nodesToProcess.isEmpty()) {
            Node currentNode = nodesToProcess.remove();
            if (currentNode instanceof LoadIndexedNode) {
                isRead = true;
                if (((ValueNode) currentNode).stamp(NodeView.DEFAULT).javaType(metaAccess).isArray()) {
                    nodesToProcess.addAll(currentNode.usages().snapshot());
                }
            } else if (currentNode instanceof StoreIndexedNode || currentNode instanceof StoreAtomicIndexedNode || currentNode instanceof WriteNode || currentNode instanceof JavaWriteNode) {
                MetaControlFlow meta = analyseControlFlowForWriting(currentNode, fatherNodeStore, isWrittenTrueCondition, isWrittenFalseCondition);
                fatherNodeStore = meta.fatherNodeStore();
                isWrittenTrueCondition = meta.isWrittenTrueCondition();
                isWrittenFalseCondition = meta.isWrittenFalseCondition();
                isWritten = true;
                if (currentNode instanceof StoreIndexedNode) {
                    isWritingAllPositions = analyseWritingPositions((StoreIndexedNode) currentNode);
                }
            } else if (currentNode instanceof ReadNode || currentNode instanceof JavaReadNode) {
                ValueNode readNode = (ValueNode) currentNode;
                if (readNode.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                    readNode.usages().forEach(nodesToProcess::add);
                }
                isRead = true;
            } else if (currentNode instanceof LoadFieldNode loadField) {
                if (isTornadoNativeArray(loadField)) {
                    continue;
                }
                if (loadField.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                    loadField.usages().forEach(nodesToProcess::add);
                }
                isReadField = true;
            } else if (currentNode instanceof StoreFieldNode) {
                MetaControlFlow meta = analyseControlFlowForWriting(currentNode, fatherNodeStore, isWrittenTrueCondition, isWrittenFalseCondition);
                fatherNodeStore = meta.fatherNodeStore();
                isWrittenTrueCondition = meta.isWrittenTrueCondition();
                isWrittenFalseCondition = meta.isWrittenFalseCondition();
                isWrittenField = true;
                isReadField = true;
            } else if (currentNode instanceof MarkVectorStore) {
                isWritten = true;
            } else if (isNodeFromKnownObject(currentNode)) {
                // All known objects are passed by reference -> R/W (e.g., Atomics)
                isRead = true;
                isWritten = true;
            } else if (currentNode instanceof PiNode || currentNode instanceof AddressNode || currentNode instanceof OffsetAddressNode) {
                currentNode.usages().forEach(nodesToProcess::add);
            }
        }

        if ((isWrittenTrueCondition ^ isWrittenFalseCondition) && !isWritingAllPositions) {
            isRead = true;
        }

        Access result = Access.NONE;
        if (isRead && isWritten) {
            result = Access.READ_WRITE;
        } else if (isRead) {
            result = Access.READ_ONLY;
        } else if (isWritten) {
            result = Access.WRITE_ONLY;
        }

        if (isReadField && isWrittenField) {
            result = Access.asArray()[result.position | Access.READ_WRITE.position];
        } else if (isReadField) {
            result = Access.asArray()[result.position | Access.READ_ONLY.position];
        } else if (isWrittenField) {
            result = Access.asArray()[result.position | Access.WRITE_ONLY.position];
        }

        return result;
    }

    private boolean isTornadoNativeArray(LoadFieldNode loadFieldNode) {
        String field = loadFieldNode.field().toString();

        return field.contains("uk.ac.manchester.tornado.api.types.arrays.IntArray") || field.contains("uk.ac.manchester.tornado.api.types.arrays.DoubleArray") || field.contains(
                "uk.ac.manchester.tornado.api.types.arrays.FloatArray") || field.contains("uk.ac.manchester.tornado.api.types.arrays.LongArray") || field.contains(
                        "uk.ac.manchester.tornado.api.types.arrays.CharArray") || field.contains("uk.ac.manchester.tornado.api.types.arrays.ShortArray") || field.contains(
                                "uk.ac.manchester.tornado.api.types.arrays.ByteArray");
    }

    /**
     * Quick check to obtain if the store index of the store operation covers all
     * iterations or a subset. This check is useful to determine READ-ONLY or
     * WRITE-ONLY Accesses over an array.
     *
     * @param storeIndexedNode
     *     store indexed (array) value
     * @return It returns true if the store covers all iterations.
     */
    private boolean analyseWritingPositions(StoreIndexedNode storeIndexedNode) {

        // Part I
        // Check first that we are not in an if-condition. This will mean that the
        // write-node is only for some iterations of the loop. Thus, even though the
        // induction variable is incremented by 1, the write is only affected by some of
        // the iterations of the loop.
        Node pre = storeIndexedNode.predecessor();
        while ((pre != null) && !(pre instanceof IfNode)) {
            pre = pre.predecessor();
        }

        if (pre != null) {
            if (pre.predecessor() instanceof BeginNode) {
                return false;
            }
        }

        // Part II
        // If the check was not false, then we can continue with the analysis
        ArrayDeque<Node> nodesToProcess = new ArrayDeque<>();
        HashSet<Node> visited = new HashSet<>();
        nodesToProcess.add(storeIndexedNode.index());
        while (!nodesToProcess.isEmpty()) {
            Node node = nodesToProcess.remove();
            visited.add(node);
            switch (node) {
                case ParallelStrideNode parallelStrideNode -> {
                    Node valueNode = parallelStrideNode.value();
                    if (valueNode instanceof ConstantNode constantNode) {
                        ConstantNode constantNode1 = ConstantNode.forInt(1);
                        return constantNode.getValue().equals(constantNode1.getValue());
                    }
                }
                case BinaryArithmeticNode binaryArithmeticNode -> {
                    Node a = binaryArithmeticNode.getX();
                    Node b = binaryArithmeticNode.getY();
                    if (!visited.contains(a)) {
                        nodesToProcess.add(a);
                    }
                    if (!visited.contains(b)) {
                        nodesToProcess.add(b);
                    }
                }
                case PhiNode phiNode -> {
                    for (ValueNode valuePhiNode : phiNode.values()) {
                        if (!visited.contains(valuePhiNode)) {
                            nodesToProcess.add(valuePhiNode);
                        }
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private record MetaControlFlow(boolean isWrittenTrueCondition, boolean isWrittenFalseCondition,
                                   IfNode fatherNodeStore) {
    }

}
