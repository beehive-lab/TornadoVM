/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.graal.phases;

import static uk.ac.manchester.tornado.common.Tornado.debug;

import java.util.ArrayDeque;
import java.util.Queue;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.graal.nodes.StoreAtomicIndexedNode;

public class TornadoDataflowAnalysis extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        if (!context.hasMeta()) {
            return;
        }

        TaskMetaData meta = context.getMeta();
        Access[] accesses = meta.getArgumentsAccess();

        for (int i = 0; i < accesses.length; i++) {
            accesses[i] = Access.NONE;
            ParameterNode param = graph.getParameter(i);

            // Only interested in objects
            if (param != null && param.stamp() instanceof ObjectStamp) {
                accesses[i] = processUsages(param, context.getMetaAccess());
            }
            debug("access: parameter %d -> %s\n", i, accesses[i]);
        }
    }

    private static class MetaControlFlow {
        private boolean isWrittenTrueCondition;
        private boolean isWrittenFalseCondition;
        private IfNode fatherNodeStore;

        public MetaControlFlow(boolean isWrittenTrueCondition, boolean isWrittenFalseCondition, IfNode fatherNodeStore) {
            super();
            this.isWrittenTrueCondition = isWrittenTrueCondition;
            this.isWrittenFalseCondition = isWrittenFalseCondition;
            this.fatherNodeStore = fatherNodeStore;
        }

        public boolean isWrittenTrueCondition() {
            return isWrittenTrueCondition;
        }

        public boolean isWrittenFalseCondition() {
            return isWrittenFalseCondition;
        }

        public IfNode getFatherNodeStore() {
            return fatherNodeStore;
        }
    }

    private boolean checkIgnoreStride(ParallelRangeNode range) {
        ValueNode value = range.stride().value();
        if (value instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) value;
            Constant value2 = c.getValue();
            String v = value2.toValueString();
            int stride = Integer.parseInt(v);
            if (stride == 1) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean shouldIgnoreNode(IfNode ifNode, IfNode fatherNodeStore) {

        // Check first if the IF node controls stride, in which case we should
        // only ignore
        // if the stride is 1.
        boolean ignore = false;
        if (ifNode.condition() instanceof BinaryOpLogicNode) {
            BinaryOpLogicNode condition = (BinaryOpLogicNode) ifNode.condition();
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
            if (!fatherNodeStore.equals(ifNode)) {
                // We found different father IF node for each
                // branch.
                return true;
            }
        }
        return false;
    }

    /*
     * For a given node store in the IR, it checks whether the store is also
     * performed in another branch of the code. If it that the case, the
     * variable should be just WRITE, otherwise, it should be READ_WRITE.
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
            if (predecessor instanceof IfNode) {
                IfNode ifNode = (IfNode) predecessor;

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

    private Access processUsages(Node parameter, MetaAccessProvider metaAccess) {
        // NodeBitMap nodes = graph.createNodeBitMap();
        // nodes.clearAll();

        boolean isRead = false;
        boolean isWritten = false;

        Queue<Node> nf = new ArrayDeque<>();
        parameter.usages().forEach(nf::add);

        boolean isWrittenTrueCondition = false;
        boolean isWrittenFalseCondition = false;
        boolean isStored = false;
        IfNode fatherNodeStore = null;

        while (!nf.isEmpty()) {
            Node currentNode = nf.remove();
            if (currentNode instanceof LoadIndexedNode) {
                isRead = true;
                if (((ValueNode) currentNode).stamp().javaType(metaAccess).isArray()) {
                    nf.addAll(currentNode.usages().snapshot());
                }
            } else if (currentNode instanceof StoreIndexedNode || currentNode instanceof StoreAtomicIndexedNode) {
                MetaControlFlow meta = analyseControlFlowForWriting(currentNode, fatherNodeStore, isWrittenTrueCondition, isWrittenFalseCondition);
                fatherNodeStore = meta.getFatherNodeStore();
                isWrittenTrueCondition = meta.isWrittenTrueCondition();
                isWrittenFalseCondition = meta.isWrittenFalseCondition();
                isStored = true;
            } else if (currentNode instanceof LoadFieldNode) {
                LoadFieldNode loadField = (LoadFieldNode) currentNode;
                if (loadField.stamp() instanceof ObjectStamp) {
                    loadField.usages().forEach(nf::add);
                }
                isRead = true;
            } else if (currentNode instanceof StoreFieldNode) {
                MetaControlFlow meta = analyseControlFlowForWriting(currentNode, fatherNodeStore, isWrittenTrueCondition, isWrittenFalseCondition);
                fatherNodeStore = meta.getFatherNodeStore();
                isWrittenTrueCondition = meta.isWrittenTrueCondition();
                isWrittenFalseCondition = meta.isWrittenFalseCondition();
                isStored = true;
            } else if (currentNode instanceof PiNode) {
                currentNode.usages().forEach(nf::add);
            }
        }

        if (isStored) {
            isWritten = true;
        }
        if (isWrittenTrueCondition ^ isWrittenFalseCondition) {
            isRead = true;
        }

        Access result = Access.NONE;
        if (isRead && isWritten) {
            result = Access.READ_WRITE;
        } else if (isRead) {
            result = Access.READ;
        } else if (isWritten) {
            result = Access.WRITE;
        }

        return result;
    }
}
