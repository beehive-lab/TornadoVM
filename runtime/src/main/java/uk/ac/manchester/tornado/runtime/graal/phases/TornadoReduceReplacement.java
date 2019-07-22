/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.lang.annotation.Annotation;
import java.util.Iterator;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.OCLReduceSubNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;

public class TornadoReduceReplacement extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        findParametersWithReduceAnnotations(graph, context);

        // TODO: Pending, if it is local variable
    }

    /**
     * It checks if there is a reduction in the IR. For now it checks simple
     * reductions. It assumes that the value to store is a binary arithmetic and
     * then load index. As soon as we discover more cases, new nodes should be
     * inspected here.
     * 
     * Cover all the cases here as soon as we discover more reductions
     * use-cases.
     * 
     * @param arrayToStore
     * @param indexToStore
     * @param currentNode
     * @return boolean
     */
    public boolean recursiveCheck(ValueNode arrayToStore, ValueNode indexToStore, ValueNode currentNode) {
        boolean isReduction = false;
        if (currentNode instanceof BinaryNode) {
            BinaryNode value = (BinaryNode) currentNode;
            ValueNode x = value.getX();
            isReduction = recursiveCheck(arrayToStore, indexToStore, x);
            if (isReduction == false) {
                ValueNode y = value.getY();
                return recursiveCheck(arrayToStore, indexToStore, y);
            }
        } else if (currentNode instanceof LoadIndexedNode) {
            LoadIndexedNode loadNode = (LoadIndexedNode) currentNode;
            if (loadNode.array() == arrayToStore && loadNode.index() == indexToStore) {
                isReduction = true;
            }
        }
        return isReduction;
    }

    private boolean checkIfReduction(StoreIndexedNode store) {
        ValueNode arrayToStore = store.array();
        ValueNode indexToStore = store.index();
        ValueNode valueToStore = store.value();
        return recursiveCheck(arrayToStore, indexToStore, valueToStore);
    }

    private static class ReductionNodes {
        private ValueNode value;
        private ValueNode accumulator;
        private ValueNode inputArray;
        private ValueNode startNode;

        public ReductionNodes(ValueNode value, ValueNode accumulator, ValueNode inputArray, ValueNode startNode) {
            super();
            this.value = value;
            this.accumulator = accumulator;
            this.inputArray = inputArray;
            this.startNode = startNode;
        }
    }

    private ValueNode obtainInputArray(ValueNode currentNode, ValueNode outputArray, ValueNode indexToStore) {
        ValueNode array = null;
        if (currentNode instanceof StoreIndexedNode) {
            StoreIndexedNode store = (StoreIndexedNode) currentNode;
            return obtainInputArray(store.value(), store.array(), store.index());
        } else if (currentNode instanceof BinaryArithmeticNode) {
            BinaryArithmeticNode<?> value = (BinaryArithmeticNode<?>) currentNode;
            array = obtainInputArray(value.getX(), outputArray, indexToStore);
            if (array == null) {
                array = obtainInputArray(value.getY(), outputArray, indexToStore);
            }
        } else if (currentNode instanceof BinaryNode) {
            if (currentNode.getClass().getName().endsWith("OCLFPBinaryIntrinsicNode")) {
                array = obtainInputArray(((BinaryNode) currentNode).getX(), outputArray, indexToStore);
                if (array == null) {
                    array = obtainInputArray(((BinaryNode) currentNode).getY(), outputArray, indexToStore);
                }
            } else if (currentNode.getClass().getName().endsWith("OCLIntBinaryIntrinsicNode")) {
                array = obtainInputArray(((BinaryNode) currentNode).getX(), outputArray, indexToStore);
                if (array == null) {
                    array = obtainInputArray(((BinaryNode) currentNode).getY(), outputArray, indexToStore);
                }
            }
        } else if (currentNode instanceof LoadIndexedNode) {
            LoadIndexedNode loadNode = (LoadIndexedNode) currentNode;
            if (loadNode.array() != outputArray) {
                array = loadNode.array();
            }
        }
        return array;
    }

    private ReductionNodes createReductionNode(StructuredGraph graph, StoreIndexedNode store, ValueNode inputArray, ValueNode startNode) {
        ValueNode value = null;
        ValueNode accumulator = null;

        ValueNode storeValue = store.value();

        if (storeValue instanceof AddNode) {
            AddNode addNode = (AddNode) store.value();
            final OCLReduceAddNode atomicAdd = graph.addOrUnique(new OCLReduceAddNode(addNode.getX(), addNode.getY()));
            accumulator = addNode.getX();
            value = atomicAdd;
            addNode.safeDelete();
        } else if (storeValue instanceof MulNode) {
            MulNode mulNode = (MulNode) store.value();
            final OCLReduceMulNode atomicMultiplication = graph.addOrUnique(new OCLReduceMulNode(mulNode.getX(), mulNode.getY()));
            accumulator = mulNode.getX();
            value = atomicMultiplication;
            mulNode.safeDelete();
        } else if (storeValue instanceof SubNode) {
            SubNode subNode = (SubNode) store.value();
            final OCLReduceSubNode atomicSub = graph.addOrUnique(new OCLReduceSubNode(subNode.getX(), subNode.getY()));
            accumulator = subNode.getX();
            value = atomicSub;
            subNode.safeDelete();
        } else if (storeValue instanceof BinaryNode) {

            // We need the name because it is loaded from inner core
            // (tornado-driver).
            if (storeValue.getClass().getName().endsWith("OCLFPBinaryIntrinsicNode")) {
                accumulator = ((BinaryNode) storeValue).getX();
            } else if (storeValue.getClass().getName().endsWith("OCLIntBinaryIntrinsicNode")) {
                accumulator = ((BinaryNode) storeValue).getX();
            } else {
                // For any other binary node
                // if it is a builtin, we apply the general case
                accumulator = storeValue;
            }
            value = storeValue;
        } else {
            throw new RuntimeException("\n\n[NODE REDUCTION NOT SUPPORTED] Node : " + store.value() + " not suported yet.");
        }

        return new ReductionNodes(value, accumulator, inputArray, startNode);
    }

    /**
     * Final Node Replacement
     * 
     */
    private void performNodeReplacement(StructuredGraph graph, StoreIndexedNode store, Node pred, ReductionNodes reductionNode) {

        ValueNode value = reductionNode.value;
        ValueNode accumulator = reductionNode.accumulator;
        ValueNode inputArray = reductionNode.inputArray;
        ValueNode startNode = reductionNode.startNode;
        final StoreAtomicIndexedNode atomicStore = graph.addOrUnique(new StoreAtomicIndexedNode(store.array(), store.index(), store.elementKind(), value, accumulator, inputArray, startNode));

        ValueNode arithmeticNode = null;
        if (value instanceof OCLReduceAddNode) {
            OCLReduceAddNode reduce = (OCLReduceAddNode) value;
            if (reduce.getX() instanceof BinaryArithmeticNode) {
                arithmeticNode = reduce.getX();
            } else if (reduce.getY() instanceof BinaryArithmeticNode) {
                arithmeticNode = reduce.getY();
            }
        }
        atomicStore.setOptionalOperation(arithmeticNode);

        atomicStore.setNext(store.next());
        pred.replaceFirstSuccessor(store, atomicStore);
        store.replaceAndDelete(atomicStore);

    }

    private void processReduceAnnotation(StructuredGraph graph, int index) {
        final ParameterNode reduceParameter = graph.getParameter(index);

        NodeIterable<Node> usages = reduceParameter.usages();
        Iterator<Node> iterator = usages.iterator();

        while (iterator.hasNext()) {

            Node node = iterator.next();

            if (node instanceof StoreIndexedNode) {
                StoreIndexedNode store = (StoreIndexedNode) node;

                boolean isReductionValue = checkIfReduction(store);
                if (!isReductionValue) {
                    continue;
                }

                ValueNode inputArray = obtainInputArray(store.value(), store.array(), store.index());
                ValueNode startNode = obtainStartNode(store);

                ReductionNodes reductionNode = createReductionNode(graph, store, inputArray, startNode);
                Node pred = node.predecessor();
                performNodeReplacement(graph, store, pred, reductionNode);

            } else if (node instanceof StoreFieldNode) {
                throw new RuntimeException("\n\n[NOT SUPPORTED] Node StoreFieldNode is not suported yet.");
            }
        }
    }

    private ValueNode obtainStartNode(StoreIndexedNode store) {
        boolean startFound = false;
        ValueNode startNode = null;
        IfNode ifNode = null;
        Node n = store.predecessor();
        while (!startFound) {
            if (n instanceof IfNode) {
                ifNode = (IfNode) n;
                while (!(n.predecessor() instanceof LoopBeginNode)) {
                    n = n.predecessor();
                    if (n instanceof StartNode) {
                        // node not found
                        return null;
                    }
                }
                // in this point n = loopBeginNode
                CompareNode condition = (CompareNode) ifNode.condition();
                if (condition.getX() instanceof PhiNode) {
                    PhiNode phi = (PhiNode) condition.getX();
                    startNode = phi.valueAt(0);
                    break;
                }
            }
            n = n.predecessor();
        }
        return startNode;
    }

    private int getNumberOfParameterNodes(StructuredGraph graph) {
        int numParameters = 0;
        for (Node n : graph.getNodes()) {
            if (n instanceof ParameterNode) {
                numParameters++;
            }
        }
        return numParameters;
    }

    private void findParametersWithReduceAnnotations(StructuredGraph graph, TornadoSketchTierContext context) {
        final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();
        for (int index = 0; index < parameterAnnotations.length; index++) {
            for (Annotation annotation : parameterAnnotations[index]) {
                if (annotation instanceof Reduce) {
                    // If the number of arguments do not match, then we
                    // increase the index to obtain the correct one when
                    // indexing from getParameters. This is an issue when having
                    // inheritance with interfaces from Apache Flink. See issue
                    // #185 on Github
                    if (!graph.method().isStatic() || getNumberOfParameterNodes(graph) > parameterAnnotations.length) {
                        index++;
                    }

                    processReduceAnnotation(graph, index);
                }
            }
        }
    }
}
