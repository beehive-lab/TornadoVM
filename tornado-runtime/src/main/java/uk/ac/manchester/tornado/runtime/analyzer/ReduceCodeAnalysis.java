/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
import java.util.List;
import java.util.Objects;

import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;

import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkFloatingPointIntrinsicsNode;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkIntIntrinsicNode;

/**
 * Code analysis class for reductions in TornadoVM.
 */
public class ReduceCodeAnalysis {

    private static boolean checkIfVarIsInLoop(StoreIndexedNode store) {
        Node node = store.predecessor();
        boolean hasPred = true;
        while (hasPred) {
            if (node instanceof LoopBeginNode) {
                return true;
            } else if (node instanceof StartNode) {
                hasPred = false;
            } else if (node instanceof MergeNode) {
                MergeNode merge = (MergeNode) node;
                EndNode endNode = merge.forwardEndAt(0);
                node = endNode.predecessor();
            } else {
                node = node.predecessor();
            }
        }
        return false;
    }

    public static List<REDUCE_OPERATION> getReduceOperation(List<ValueNode> reduceOperation) {
        // Match VALUE_NODE with OPERATION
        List<REDUCE_OPERATION> operations = new ArrayList<>();
        for (ValueNode operation : reduceOperation) {
            if (operation instanceof TornadoReduceAddNode) {
                operations.add(REDUCE_OPERATION.SUM);
            } else if (operation instanceof AddNode) {
                operations.add(REDUCE_OPERATION.SUM);
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
            } else if (operation instanceof BinaryNode && operation instanceof MarkFloatingPointIntrinsicsNode) {
                MarkFloatingPointIntrinsicsNode mark = (MarkFloatingPointIntrinsicsNode) operation;
                String op = mark.getOperation();
                if (op.equals("FMAX")) {
                    operations.add(REDUCE_OPERATION.MAX);
                } else if (op.equals("FMIN")) {
                    operations.add(REDUCE_OPERATION.MIN);
                } else {
                    throw new TornadoRuntimeException("[ERROR] Automatic reduce operation not supported yet: " + operation);
                }
            } else if (operation instanceof BinaryNode && operation instanceof MarkIntIntrinsicNode) {
                MarkIntIntrinsicNode mark = (MarkIntIntrinsicNode) operation;
                String op = mark.getOperation();
                if (op.equals("MAX")) {
                    operations.add(REDUCE_OPERATION.MAX);
                } else if (op.equals("MIN")) {
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

    private static boolean shouldSkip(int index, StructuredGraph graph) {
        return graph.method().isStatic() && index >= getNumberOfParameterNodes(graph);
    }

    public static List<REDUCE_OPERATION> getReduceOperation(StructuredGraph graph, List<Integer> reduceIndices) {
        List<ValueNode> reduceOperation = new ArrayList<>();
        for (Integer paramIndex : reduceIndices) {

            if (!graph.method().isStatic()) {
                paramIndex++;
            }

            if (shouldSkip(paramIndex, graph)) {
                continue;
            }

            ParameterNode parameterNode = graph.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();
            // Get Input-Range for the reduction loop
            for (Node node : usages) {
                if (node instanceof StoreIndexedNode) {
                    StoreIndexedNode store = (StoreIndexedNode) node;
                    if (!checkIfVarIsInLoop(store)) {
                        continue;
                    }
                    if (store.value() instanceof BinaryNode) {
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
        return getReduceOperation(reduceOperation);
    }

    public static List<REDUCE_OPERATION> getReduceOperatorFromSketch(Graph graph, List<Integer> reduceIndices) {
        List<ValueNode> reduceOperation = new ArrayList<>();
        final StructuredGraph sg = (StructuredGraph) graph.copy(TornadoCoreRuntime.getDebugContext());

        for (Integer paramIndex : reduceIndices) {

            if (!sg.method().isStatic()) {
                paramIndex++;
            }

            ParameterNode parameterNode = sg.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();
            // Get Input-Range for the reduction loop
            for (Node node : usages) {
                if (node instanceof StoreAtomicIndexedNode) {
                    StoreAtomicIndexedNode store = (StoreAtomicIndexedNode) node;

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

        return getReduceOperation(reduceOperation);
    }

    private static ArrayLengthNode inspectArrayLengthNode(Node aux) {
        ArrayLengthNode arrayLengthNode = null;
        aux = aux.successors().first();
        if (aux instanceof IfNode) {
            IfNode ifNode = (IfNode) aux;
            LogicNode condition = ifNode.condition();
            if (condition instanceof IntegerLessThanNode) {
                IntegerLessThanNode iln = (IntegerLessThanNode) condition;
                if (iln.getX() instanceof ArrayLengthNode) {
                    arrayLengthNode = (ArrayLengthNode) iln.getX();
                } else if (iln.getY() instanceof ArrayLengthNode) {
                    arrayLengthNode = (ArrayLengthNode) iln.getY();
                }
            }
        }
        return arrayLengthNode;
    }

    private static ValueNode inspectConstantNode(Node aux) {
        ConstantNode constantNode = null;
        aux = aux.successors().first();
        if (aux instanceof IfNode) {
            IfNode ifNode = (IfNode) aux;
            LogicNode condition = ifNode.condition();
            if (condition instanceof IntegerLessThanNode) {
                IntegerLessThanNode iln = (IntegerLessThanNode) condition;
                if (iln.getX() instanceof ConstantNode) {
                    constantNode = (ConstantNode) iln.getX();
                } else if (iln.getY() instanceof ConstantNode) {
                    constantNode = (ConstantNode) iln.getY();
                }
            }
        }
        return constantNode;
    }

    private static int getNumberOfParameterNodes(StructuredGraph graph) {
        return graph.getNodes().filter(ParameterNode.class).count();
    }

    /**
     * A method can apply multiple reduction variables. We return a list of all its
     * loop bounds.
     *
     * @param graph
     *            Graal-IR graph to be analyzed
     * @param reduceIndexes
     *            List of reduce indexes within the method parameter list
     * @return ArrayList<ValueNode>
     */
    private static ArrayList<ValueNode> findLoopUpperBoundNode(StructuredGraph graph, ArrayList<Integer> reduceIndexes) {
        ArrayList<ValueNode> loopBound = new ArrayList<>();
        for (Integer paramIndex : reduceIndexes) {

            if (!graph.method().isStatic()) {
                paramIndex++;
            }

            if (shouldSkip(paramIndex, graph)) {
                continue;
            }

            ParameterNode parameterNode = graph.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();

            // Get Input-Range for the reduction loop
            for (Node node : usages) {
                if (node instanceof StoreIndexedNode) {
                    Node aux = node;
                    LoopBeginNode loopBegin = null;
                    ValueNode loopBoundNode = null;

                    while (!(aux instanceof LoopBeginNode)) {
                        // Move reference to predecessor (bottom-up traversal)
                        if (aux instanceof MergeNode) {
                            MergeNode mergeNode = (MergeNode) aux;
                            aux = mergeNode.forwardEndAt(0);
                        } else {
                            aux = aux.predecessor();
                        }

                        if (aux instanceof StartNode) {
                            // We reach the beginning of the graph
                            break;
                        } else if (aux instanceof LoopBeginNode) {
                            loopBegin = (LoopBeginNode) aux;
                        } else if (aux instanceof ArrayLengthNode) {
                            loopBoundNode = (ArrayLengthNode) aux;
                        }
                    }

                    if (loopBoundNode == null) {
                        // XXX: Patch to support PE when using ArrayLength at the beginning of the
                        // method.
                        // TODO: Find a better way to PE loop bounds
                        loopBoundNode = inspectArrayLengthNode(aux);
                    }

                    // If the loopBoundNode is still null, we look for ConstantNode as a loop bound
                    // instead of ArrayLength
                    if (loopBoundNode == null) {
                        loopBoundNode = inspectConstantNode(aux);
                    }

                    if (loopBegin != null) {
                        if (loopBoundNode instanceof ArrayLengthNode) {
                            loopBound.add(((ArrayLengthNode) Objects.requireNonNull(loopBoundNode)).array());
                        } else {
                            loopBound.add(Objects.requireNonNull(loopBoundNode));
                        }
                    }
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
    public static MetaReduceCodeAnalysis analysisTaskGraph(List<TaskPackage> taskPackages) {
        int taskIndex = 0;
        int inputSize = 0;

        HashMap<Integer, MetaReduceTasks> tableMetaDataReduce = new HashMap<>();

        for (TaskPackage taskMetadata : taskPackages) {

            Object taskCode = taskMetadata.getTaskParameters()[0];
            StructuredGraph graph = CodeAnalysis.buildHighLevelGraalGraph(taskCode);

            assert graph != null;
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
                    int position = !graph.method().isStatic() ? i + 1 : i;
                    if (valueNode.equals(graph.getParameter(position))) {
                        Object object = taskPackages.get(taskIndex).getTaskParameters()[i + 1];
                        inputSize = Array.getLength(object);
                    } else if (valueNode instanceof ConstantNode) {
                        ConstantNode constant = (ConstantNode) valueNode;
                        inputSize = Integer.parseInt(constant.getValue().toValueString());
                    }
                }
            }

            MetaReduceTasks reduceTasks = new MetaReduceTasks(taskIndex, graph, reduceIndices, inputSize);
            tableMetaDataReduce.put(taskIndex, reduceTasks);
            taskIndex++;
        }

        return (tableMetaDataReduce.isEmpty() ? null : new MetaReduceCodeAnalysis(tableMetaDataReduce));
    }

    /**
     * It performs a loop-range substitution for the lower part of the reduction.
     *
     * @param graph
     *            Input Graal {@link StructuredGraph}
     * @param lowValue
     *            Low value to include in the compile-graph
     */
    public static void performLoopBoundNodeSubstitution(StructuredGraph graph, long lowValue) {
        for (Node n : graph.getNodes()) {
            if (n instanceof LoopBeginNode) {
                LoopBeginNode beginNode = (LoopBeginNode) n;
                FixedNode node = beginNode.next();
                while (!(node instanceof IfNode)) {
                    node = (FixedNode) node.successors().first();
                }

                IfNode ifNode = (IfNode) node;
                LogicNode condition = ifNode.condition();
                if (condition instanceof IntegerLessThanNode) {
                    IntegerLessThanNode integer = (IntegerLessThanNode) condition;
                    ValueNode x = integer.getX();
                    final ConstantNode low = graph.addOrUnique(ConstantNode.forLong(lowValue));
                    if (x instanceof PhiNode) {
                        // Node substitution
                        PhiNode phi = (PhiNode) x;
                        if (phi.valueAt(0) instanceof ConstantNode) {
                            phi.setValueAt(0, low);
                        }
                    }
                }
            }
        }
    }

    public enum REDUCE_OPERATION { //
        SUM, //
        MUL, //
        MIN, //
        MAX //
    }
}
