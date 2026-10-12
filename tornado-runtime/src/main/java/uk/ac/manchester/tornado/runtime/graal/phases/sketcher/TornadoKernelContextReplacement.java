/*
 * Copyright (c) 2021, 2024 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.graal.phases.sketcher;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.util.ArrayList;
import java.util.Optional;

import tornado.graal.compiler.debug.DebugContext;
import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.FixedNode;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.PiNode;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.calc.IsNullNode;
import tornado.graal.compiler.nodes.extended.GuardingNode;
import tornado.graal.compiler.nodes.extended.UnboxNode;
import tornado.graal.compiler.nodes.java.LoadFieldNode;
import tornado.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.graal.nodes.GetGroupIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.GlobalGroupSizeFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.LocalGroupSizeFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ThreadIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ThreadLocalIdFixedWithNextNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

/**
 * The {@link TornadoKernelContextReplacement} phase is performed during
 * {@link uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier}.
 * The objective is to replace all the FieldNodes of the {@link KernelContext}
 * fields with FixedNodes that can be lowered to TornadoVM nodes for OpenCL
 * and CUDA code emission.
 */
public class TornadoKernelContextReplacement extends BasePhase<TornadoSketchTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private void replaceKernelContextNode(StructuredGraph graph, ArrayList<Node> nodesToBeRemoved, LoadFieldNode oldNode, FixedWithNextNode newNode, boolean kotlin) {
        if (kotlin) {
            replaceKotlinKernelContextNode(graph, nodesToBeRemoved, oldNode, newNode);
            return;
        }
        for (Node n : oldNode.successors()) {
            for (Node input : n.inputs()) { // This should be NullNode
                input.safeDelete();
            }
            for (Node usage : n.usages()) { // This should be PiNode
                usage.safeDelete();
            }

            Node fixedWithNextNode = n.successors().first();
            fixedWithNextNode.replaceAtPredecessor(null);
            oldNode.replaceFirstSuccessor(n, fixedWithNextNode);

            n.safeDelete();
        }

        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After-FIXED REMOVED");

        Node unboxNode = oldNode.next();

        if (unboxNode instanceof UnboxNode) {

            unboxNode.replaceAtUsages(oldNode);

            Node fixedWithNextNode = unboxNode.successors().first();
            fixedWithNextNode.replaceAtPredecessor(null);
            oldNode.replaceFirstSuccessor(unboxNode, fixedWithNextNode);

            unboxNode.safeDelete();
        }

        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After-UNBOXING");

        graph.addWithoutUnique(newNode);

        oldNode.replaceAtUsages(newNode);

        oldNode.replaceAtUsages(newNode);
        oldNode.replaceAtPredecessor(newNode);

        Node fixedWithNextNode = oldNode.successors().first();
        fixedWithNextNode.replaceAtPredecessor(null);
        newNode.replaceFirstSuccessor(null, fixedWithNextNode);

        nodesToBeRemoved.add(oldNode);
    }

    /**
     * Kotlin variant of {@link #replaceKernelContextNode}, used for methods compiled by kotlinc only.
     *
     * <p>
     * The thread-index fields of {@link KernelContext} are boxed {@link Integer}s. javac unboxes the field once (
     * {@code int i = context.globalIdx}), which gives the single null-check / unbox pair that
     * {@link #replaceKernelContextNode} expects right after the field load. kotlinc may keep the value boxed in a local
     * and unbox it at every use, so the load can have any number of null checks ({@code FixedGuard(IsNull)} plus
     * {@link PiNode}) and {@link UnboxNode}s, anywhere in the method. All of them are removed, and the load is replaced
     * by the thread-index node, which already produces the unboxed value.
     * </p>
     */
    private void replaceKotlinKernelContextNode(StructuredGraph graph, ArrayList<Node> nodesToBeRemoved, LoadFieldNode oldNode, FixedWithNextNode newNode) {
        // 1. Null checks of the boxed field: FixedGuard(IsNull(load)) and the PiNodes anchored on it.
        for (Node usage : oldNode.usages().snapshot()) {
            if (usage instanceof IsNullNode isNull) {
                for (Node conditionUsage : isNull.usages().snapshot()) {
                    if (conditionUsage instanceof GuardingNode && conditionUsage instanceof FixedWithNextNode guard) {
                        for (Node guardUsage : guard.usages().snapshot()) {
                            if (guardUsage instanceof PiNode pi) {
                                pi.replaceAtUsages(oldNode);
                                pi.safeDelete();
                            }
                        }
                        if (guard.hasNoUsages()) {
                            graph.removeFixed(guard);
                        }
                    }
                }
                if (isNull.hasNoUsages()) {
                    isNull.safeDelete();
                }
            }
        }

        // 2. Every unbox of the field now reads the load directly: use the load in its place.
        for (Node usage : oldNode.usages().snapshot()) {
            if (usage instanceof UnboxNode unbox) {
                unbox.replaceAtUsages(oldNode);
                graph.removeFixed(unbox);
            }
        }

        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After-KOTLIN-UNBOXING");

        // 3. Replace the load by the thread-index node, as for Java.
        graph.addWithoutUnique(newNode);
        oldNode.replaceAtUsages(newNode);
        FixedNode next = oldNode.next();
        oldNode.setNext(null);
        oldNode.replaceAtPredecessor(newNode);
        newNode.setNext(next);

        nodesToBeRemoved.add(oldNode);
    }

    private void introduceKernelContext(StructuredGraph graph, boolean kotlin) {
        ArrayList<Node> nodesToBeRemoved = new ArrayList<>();
        graph.getNodes().filter(LoadFieldNode.class).forEach((node) -> {
            if (node != null) {
                String field = node.field().format("%H.%n");
                if (field.contains("KernelContext.globalId")) {
                    ThreadIdFixedWithNextNode threadIdNode;
                    if (field.contains("globalIdx")) {
                        threadIdNode = new ThreadIdFixedWithNextNode(node.getValue(), 0);
                    } else if (field.contains("globalIdy")) {
                        threadIdNode = new ThreadIdFixedWithNextNode(node.getValue(), 1);
                    } else if (field.contains("globalIdz")) {
                        threadIdNode = new ThreadIdFixedWithNextNode(node.getValue(), 2);
                    } else {
                        throw new TornadoRuntimeException("Unrecognized dimension");
                    }

                    replaceKernelContextNode(graph, nodesToBeRemoved, node, threadIdNode, kotlin);
                } else if (field.contains("KernelContext.localId")) {
                    ThreadLocalIdFixedWithNextNode threadLocalIdNode;
                    if (field.contains("localIdx")) {
                        threadLocalIdNode = new ThreadLocalIdFixedWithNextNode(node.getValue(), 0);
                    } else if (field.contains("localIdy")) {
                        threadLocalIdNode = new ThreadLocalIdFixedWithNextNode(node.getValue(), 1);
                    } else if (field.contains("localIdz")) {
                        threadLocalIdNode = new ThreadLocalIdFixedWithNextNode(node.getValue(), 2);
                    } else {
                        throw new TornadoRuntimeException("Unrecognized dimension");
                    }

                    replaceKernelContextNode(graph, nodesToBeRemoved, node, threadLocalIdNode, kotlin);
                } else if (field.contains("KernelContext.groupId")) {
                    GetGroupIdFixedWithNextNode groupIdNode;
                    if (field.contains("groupIdx")) {
                        groupIdNode = new GetGroupIdFixedWithNextNode(node.getValue(), 0);
                    } else if (field.contains("groupIdy")) {
                        groupIdNode = new GetGroupIdFixedWithNextNode(node.getValue(), 1);
                    } else if (field.contains("groupIdz")) {
                        groupIdNode = new GetGroupIdFixedWithNextNode(node.getValue(), 2);
                    } else {
                        throw new TornadoRuntimeException("Unrecognized dimension");
                    }

                    replaceKernelContextNode(graph, nodesToBeRemoved, node, groupIdNode, kotlin);
                } else if (field.contains("KernelContext.globalGroupSize")) {
                    GlobalGroupSizeFixedWithNextNode globalGroupSizeNode;
                    if (field.contains("globalGroupSizeX")) {
                        globalGroupSizeNode = new GlobalGroupSizeFixedWithNextNode(node.getValue(), 0);
                    } else if (field.contains("globalGroupSizeY")) {
                        globalGroupSizeNode = new GlobalGroupSizeFixedWithNextNode(node.getValue(), 1);
                    } else if (field.contains("globalGroupSizeZ")) {
                        globalGroupSizeNode = new GlobalGroupSizeFixedWithNextNode(node.getValue(), 2);
                    } else {
                        throw new TornadoRuntimeException("Unrecognized dimension");
                    }

                    replaceKernelContextNode(graph, nodesToBeRemoved, node, globalGroupSizeNode, kotlin);
                } else if (field.contains("KernelContext.localGroupSize")) {
                    LocalGroupSizeFixedWithNextNode localGroupSizeNode;
                    if (field.contains("localGroupSizeX")) {
                        localGroupSizeNode = new LocalGroupSizeFixedWithNextNode(node.getValue(), 0);
                    } else if (field.contains("localGroupSizeY")) {
                        localGroupSizeNode = new LocalGroupSizeFixedWithNextNode(node.getValue(), 1);
                    } else if (field.contains("localGroupSizeZ")) {
                        localGroupSizeNode = new LocalGroupSizeFixedWithNextNode(node.getValue(), 2);
                    } else {
                        throw new TornadoRuntimeException("Unrecognized dimension");
                    }

                    replaceKernelContextNode(graph, nodesToBeRemoved, node, localGroupSizeNode, kotlin);
                }
            }
        });

        nodesToBeRemoved.forEach(node -> {
            node.clearSuccessors();
            node.clearInputs();
            node.safeDelete();
        });

    }

    public void execute(StructuredGraph graph, TornadoSketchTierContext context) {
        run(graph, context);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        introduceKernelContext(graph, context.isKotlin());
    }
}
