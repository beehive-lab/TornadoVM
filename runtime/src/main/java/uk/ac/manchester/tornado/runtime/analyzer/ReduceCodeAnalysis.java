/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.analyzer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;

import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * Code analysis class for Tornado
 *
 */
public class ReduceCodeAnalysis {

    // @formatter:off
    public enum REDUCE_OPERATION {
        ADD, 
        MUL, 
        MIN, 
        MAX
    }
    // @formatter:on

    public static ArrayList<REDUCE_OPERATION> getReduceOperation(StructuredGraph graph, ArrayList<Integer> reduceIndices) {
        ArrayList<ValueNode> reduceOperation = new ArrayList<>();
        for (Integer paramIndex : reduceIndices) {

            ParameterNode parameterNode = graph.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();
            // Get Input-Range for the reduction loop
            for (Node node : usages) {
                if (node instanceof StoreIndexedNode) {
                    StoreIndexedNode store = (StoreIndexedNode) node;
                    if (store.value() instanceof BinaryNode || store.value() instanceof BinaryArithmeticNode) {
                        ValueNode value = store.value();
                        reduceOperation.add(value);
                    } else if (store.value() instanceof InvokeNode) {
                        InvokeNode invoke = (InvokeNode) store.value();
                        if (invoke.callTarget().targetName().startsWith("Math")) {
                            reduceOperation.add(invoke);
                        }
                    }
                }
            }
        }

        // Match VALUE_NODE with OPERATION
        ArrayList<REDUCE_OPERATION> operations = new ArrayList<>();
        for (ValueNode operation : reduceOperation) {
            if (operation instanceof AddNode) {
                operations.add(REDUCE_OPERATION.ADD);
            } else if (operation instanceof MulNode) {
                operations.add(REDUCE_OPERATION.MUL);
            } else if (operation instanceof InvokeNode) {
                InvokeNode invoke = (InvokeNode) operation;
                if (invoke.callTarget().targetName().equals("Math.max")) {
                    operations.add(REDUCE_OPERATION.MAX);
                } else if (invoke.callTarget().targetName().equals("Math.min")) {
                    operations.add(REDUCE_OPERATION.MIN);
                } else {
                    throw new TornadoRuntimeException("[ERROR] Automatic reduce operation not supported yet: " + operation);
                }
            } else {
                throw new TornadoRuntimeException("[ERROR] Automatic reduce operation not supported yet: " + operation);
            }
        }
        return operations;
    }

    /**
     * A method can apply multiple reduction variables. We return a list of all
     * its loop bounds.
     * 
     * @param graph
     *            Graal-IR graph to analyze
     * @param reduceIndexes
     *            list of reduce indexes within the method parameter list
     * @return ArrayList<ValueNode>
     */
    public static ArrayList<ValueNode> findLoopUpperBoundNode(StructuredGraph graph, ArrayList<Integer> reduceIndexes) {
        ArrayList<ValueNode> loopBound = new ArrayList<>();
        for (Integer paramIndex : reduceIndexes) {
            ParameterNode parameterNode = graph.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();

            // Get Input-Range for the reduction loop
            for (Node node : usages)
                if (node instanceof StoreIndexedNode) {
                    Node aux = node;
                    LoopBeginNode loopBegin = null;
                    ArrayLengthNode arrayLength = null;

                    while (!(aux instanceof LoopBeginNode)) {
                        aux = aux.predecessor();
                        if (aux instanceof StartNode) {
                            break;
                        } else if (aux instanceof LoopBeginNode) {
                            loopBegin = (LoopBeginNode) aux;
                        } else if (aux instanceof ArrayLengthNode) {
                            arrayLength = (ArrayLengthNode) aux;
                        }
                    }

                    if (loopBegin != null) {
                        loopBound.add(Objects.requireNonNull(arrayLength).array());
                    }
                }
        }
        return loopBound;
    }

    /**
     * It obtains a list of reduce parameters for each task.
     * 
     * @return {@link MetaReduceTasks}
     */
    public static MetaReduceCodeAnalysis analysisTaskSchedule(ArrayList<TaskPackage> taskPackages) {
        int taskIndex = 0;
        int inputSize = 0;

        HashMap<Integer, MetaReduceTasks> tableMetaReduce = new HashMap<>();

        for (TaskPackage tpackage : taskPackages) {

            Object taskCode = tpackage.getTaskParameters()[0];
            StructuredGraph graph = CodeAnalysis.buildHighLevelGraalGraph(taskCode);

            Annotation[][] annotations = graph.method().getParameterAnnotations();
            ArrayList<Integer> reduceIndices = new ArrayList<>();
            for (int paramIndex = 0; paramIndex < annotations.length; paramIndex++) {
                for (Annotation annotation : annotations[paramIndex]) {
                    if (annotation instanceof Reduce) {
                        reduceIndices.add(paramIndex);
                    }
                }
            }

            if (reduceIndices.isEmpty()) {
                taskIndex++;
                continue;
            }

            // Perform PE to obtain the value of the upper-bound loop
            ArrayList<ValueNode> loopBound = findLoopUpperBoundNode(graph, reduceIndices);
            for (int i = 0; i < graph.method().getParameters().length; i++) {
                for (ValueNode valueNode : loopBound) {
                    if (valueNode.equals(graph.getParameter(i))) {
                        Object object = taskPackages.get(taskIndex).getTaskParameters()[i + 1];
                        inputSize = Array.getLength(object);
                    }
                }
            }

            if (!reduceIndices.isEmpty()) {
                MetaReduceTasks reduceTasks = new MetaReduceTasks(taskIndex, graph, reduceIndices, inputSize);
                tableMetaReduce.put(taskIndex, reduceTasks);
            }
            taskIndex++;
        }

        return (tableMetaReduce.isEmpty() ? null : new MetaReduceCodeAnalysis(tableMetaReduce));
    }
}
