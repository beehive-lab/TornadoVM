/*
 * Copyright (c) 2020, 2024 APT Group, Department of Computer Science,
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

import java.lang.annotation.Annotation;
import java.util.Optional;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.BasePhase;

import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNodeExtension;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceSubNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNodeExtension;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkFloatingPointIntrinsicsNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntIntrinsicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntrinsicsNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoReduceReplacement extends BasePhase<TornadoSketchTierContext> {
    private static ValueNode getArithmeticNode(ValueNode value, ValueNode accumulator) {
        ValueNode arithmeticNode = null;
        if (value instanceof TornadoReduceAddNode reduce) {
            if (reduce.getX() instanceof BinaryArithmeticNode) {
                arithmeticNode = reduce.getX();
            } else if (reduce.getY() instanceof BinaryArithmeticNode) {
                arithmeticNode = reduce.getY();
            } else if (reduce.getX() instanceof MarkFloatingPointIntrinsicsNode) {
                arithmeticNode = reduce.getX();
            } else if (reduce.getY() instanceof MarkFloatingPointIntrinsicsNode) {
                arithmeticNode = reduce.getY();
            }
        }

        if (arithmeticNode == null && accumulator instanceof BinaryNode) {
            arithmeticNode = accumulator;
        }
        return arithmeticNode;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        findParametersWithReduceAnnotations(graph);
        // TODO: Pending, if it is local variable
    }

    /**
     * It checks if there is a reduction in the IR. For now it checks simple reductions. It assumes that the value to store is a binary arithmetic and then load index. As soon as we discover more
     * cases, new nodes should be inspected here.
     *
     * <p>
     * Cover all the cases here as soon as we discover more reductions use-cases.
     * </p>
     *
     * @param arrayToStore
     *     Array to store
     * @param indexToStore
     *     Index used in the store array
     * @param currentNode
     *     Current node to be inspected
     * @return boolean
     */
    private boolean recursiveCheck(ValueNode arrayToStore, ValueNode indexToStore, ValueNode currentNode) {
        boolean isReduction = false;
        if (currentNode instanceof BinaryNode value) {
            ValueNode x = value.getX();
            isReduction = recursiveCheck(arrayToStore, indexToStore, x);
            if (!isReduction) {
                ValueNode y = value.getY();
                return recursiveCheck(arrayToStore, indexToStore, y);
            }
        } else if (currentNode instanceof LoadIndexedNode loadNode) {
            if (loadNode.array() == arrayToStore && loadNode.index() == indexToStore) {
                isReduction = true;
            }
        } else if (currentNode instanceof JavaReadNode readNode) {
            if (readNode.getAddress() instanceof OffsetAddressNode readAddress) {
                if (indexToStore instanceof OffsetAddressNode writeAddress) {
                    isReduction = writeAddress.valueEquals(readAddress);
                }
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

    private boolean checkIfVarIsInLoop(Node store) {
        Node node = store.predecessor();
        boolean hasPred = true;
        while (hasPred) {
            if (node instanceof LoopBeginNode) {
                return true;
            } else if (node instanceof StartNode) {
                hasPred = false;
            } else if (node instanceof MergeNode merge) {
                EndNode endNode = merge.forwardEndAt(0);
                node = endNode.predecessor();
            } else {
                node = node.predecessor();
            }
        }
        return false;
    }

    private ValueNode obtainInputArray(ValueNode currentNode, ValueNode outputArray) {
        ValueNode array = null;
        if (currentNode instanceof StoreIndexedNode store) {
            return obtainInputArray(store.value(), store.array());
        } else if (currentNode instanceof BinaryArithmeticNode<?> value) {
            array = obtainInputArray(value.getX(), outputArray);
            if (array == null) {
                array = obtainInputArray(value.getY(), outputArray);
            }
        } else if (currentNode instanceof BinaryNode) {
            if (currentNode instanceof MarkIntrinsicsNode) {
                array = obtainInputArray(((BinaryNode) currentNode).getX(), outputArray);
                if (array == null) {
                    array = obtainInputArray(((BinaryNode) currentNode).getY(), outputArray);
                }
            }
        } else if (currentNode instanceof LoadIndexedNode loadNode) {
            if (loadNode.array() != outputArray) {
                array = loadNode.array();
            }
        } else if (currentNode instanceof JavaReadNode) {
            return obtainInputArray(((JavaReadNode) currentNode).getAddress(), outputArray);
        } else if (currentNode instanceof OffsetAddressNode) {
            return obtainInputArray(((OffsetAddressNode) currentNode).getBase(), outputArray);
        } else if (currentNode instanceof LoadFieldNode loadFieldNode) {
            if (loadFieldNode.getValue() instanceof PiNode piNode) {
                ParameterNode parameterNode = piNode.inputs().filter(ParameterNode.class).first();
                if (parameterNode != outputArray) {
                    array = parameterNode;
                }
            }
        } else if (currentNode instanceof PiNode) {
            if (((PiNode) currentNode).object() instanceof ParameterNode) {
                ParameterNode parameterNode = (ParameterNode) (((PiNode) currentNode)).object();
                if (parameterNode != outputArray) {
                    array = parameterNode;
                }
            }
        }
        return array;
    }

    private ReductionMetadataNode createReductionNode(StructuredGraph graph, Node store, ValueNode inputArray, ValueNode startNode) throws RuntimeException {
        ValueNode value;
        ValueNode accumulator;
        ValueNode storeValue = null;

        if (store instanceof StoreIndexedNode) {
            storeValue = ((StoreIndexedNode) store).value();
        } else if (store instanceof JavaWriteNode) {
            storeValue = ((JavaWriteNode) store).value();
        }
        if (storeValue == null) {
            throw new TornadoRuntimeException("\n\n[NODE REDUCTION NOT SUPPORTED] Node : " + store + " not supported yet.");
        }

        if (storeValue instanceof AddNode addNode) {
            final TornadoReduceAddNode atomicAdd = graph.addOrUnique(new TornadoReduceAddNode(addNode.getX(), addNode.getY()));
            accumulator = addNode.getY();
            value = atomicAdd;
            addNode.safeDelete();
        } else if (storeValue instanceof MulNode mulNode) {
            final TornadoReduceMulNode atomicMultiplication = graph.addOrUnique(new TornadoReduceMulNode(mulNode.getX(), mulNode.getY()));
            accumulator = mulNode.getX();
            value = atomicMultiplication;
            mulNode.safeDelete();
        } else if (storeValue instanceof SubNode subNode) {
            final TornadoReduceSubNode atomicSub = graph.addOrUnique(new TornadoReduceSubNode(subNode.getX(), subNode.getY()));
            accumulator = subNode.getX();
            value = atomicSub;
            subNode.safeDelete();
        } else if (storeValue instanceof BinaryNode) {

            // We need to compare with the name because it is loaded from inner core
            // (tornado-driver).
            if (storeValue instanceof MarkFloatingPointIntrinsicsNode || storeValue instanceof MarkIntIntrinsicNode) {
                accumulator = ((BinaryNode) storeValue).getY();
                // TODO: Control getX case
            } else {
                // For any other binary node
                // if it is a builtin, we apply the general case
                accumulator = storeValue;
            }
            value = storeValue;
        } else {
            throw new TornadoRuntimeException("\n\n[NODE REDUCTION NOT SUPPORTED] Node : " + storeValue + " not supported yet.");
        }

        return new ReductionMetadataNode(value, accumulator, inputArray, startNode);
    }

    /**
     * Final Node Replacement.
     */
    private void performNodeReplacement(StructuredGraph graph, FixedWithNextNode node, Node predecessor, ReductionMetadataNode reductionNode, ValueNode outArray) {

        ValueNode value = reductionNode.value;
        ValueNode accumulator = reductionNode.accumulator;
        ValueNode inputArray = reductionNode.inputArray;
        ValueNode startNode = reductionNode.startNode;
        FixedWithNextNode storeNode = null;
        if (node instanceof StoreIndexedNode store) {
            StoreAtomicIndexedNodeExtension storeAtomicIndexedNodeExtension = new StoreAtomicIndexedNodeExtension(startNode);
            graph.addOrUnique(storeAtomicIndexedNodeExtension);
            storeNode = graph.addOrUnique(new StoreAtomicIndexedNode(store.array(), store.index(), store.elementKind(), //
                    store.getBoundsCheck(), value, accumulator, inputArray, storeAtomicIndexedNodeExtension));
        } else if (node instanceof JavaWriteNode javaWriteNode) {
            WriteAtomicNodeExtension writeAtomicNodeExtension = new WriteAtomicNodeExtension(startNode);
            graph.addOrUnique(writeAtomicNodeExtension);
            storeNode = graph.addOrUnique(new WriteAtomicNode(javaWriteNode.getWriteKind(), javaWriteNode.getAddress(), value, accumulator, inputArray, outArray, writeAtomicNodeExtension));
        }

        ValueNode arithmeticNode = getArithmeticNode(value, accumulator);

        if (storeNode instanceof StoreAtomicIndexedNode storeAtomicIndexedNode) {
            storeAtomicIndexedNode.setOptionalOperation(arithmeticNode);
        } else if (storeNode instanceof WriteAtomicNode writeAtomicNode) {
            writeAtomicNode.setOptionalOperation(arithmeticNode);
        }

        FixedNode next = node.next();
        predecessor.replaceFirstSuccessor(node, storeNode);
        node.replaceAndDelete(storeNode);
        storeNode.setNext(next);
    }

    private boolean shouldSkip(int index, StructuredGraph graph) {
        return graph.method().isStatic() && index >= getNumberOfParameterNodes(graph);
    }

    private void processReduceAnnotation(StructuredGraph graph, int index) {

        if (shouldSkip(index, graph)) {
            return;
        }

        final ParameterNode reduceParameter = graph.getParameter(index);
        assert (reduceParameter != null);
        NodeIterable<Node> usages = reduceParameter.usages();

        for (Node node : usages) {
            if (node instanceof StoreIndexedNode store) {

                boolean isReductionValue = checkIfReduction(store);
                if (!isReductionValue) {
                    continue;
                }

                boolean isInALoop = checkIfVarIsInLoop(store);
                if (!isInALoop) {
                    continue;
                }

                insertReduceStoreNode(graph, store, store.value(), store.array());
            } else if (node instanceof StoreFieldNode) {
                throw new TornadoRuntimeException("\n[NOT SUPPORTED] Node StoreFieldNode is not supported yet.");
            } else if (node instanceof PiNode) {
                // for memory segments
                for (OffsetAddressNode offsetAddressNode : node.usages().filter(OffsetAddressNode.class)) {
                    if (offsetAddressNode.usages().filter(JavaWriteNode.class).isNotEmpty()) {
                        JavaWriteNode javaWriteNode = offsetAddressNode.usages().filter(JavaWriteNode.class).first();
                        // follow these steps but adapted to javaWriteNode
                        ParameterNode parameterNode = node.inputs().filter(ParameterNode.class).first();
                        boolean isReductionValue = recursiveCheck(parameterNode, offsetAddressNode, javaWriteNode.value());
                        if (!isReductionValue) {
                            continue;
                        }

                        boolean isInALoop = checkIfVarIsInLoop(javaWriteNode);
                        if (!isInALoop) {
                            continue;
                        }

                        insertReduceStoreNode(graph, javaWriteNode, javaWriteNode.value(), parameterNode);
                    }
                }

            }
        }
    }

    private void insertReduceStoreNode(StructuredGraph graph, FixedWithNextNode storeNode, ValueNode storeNodeValue, ValueNode array) {
        ValueNode inputArray = obtainInputArray(storeNodeValue, array);
        ValueNode startNode = obtainStartLoopNode(storeNode);
        ReductionMetadataNode reductionNode = createReductionNode(graph, storeNode, inputArray, startNode);
        Node predecessor = storeNode.predecessor();
        performNodeReplacement(graph, storeNode, predecessor, reductionNode, array);
    }

    private ValueNode obtainStartLoopNode(Node store) {
        boolean startFound = false;
        ValueNode startNode = null;
        IfNode ifNode;
        Node node = store.predecessor();
        while (!startFound) {
            if (node instanceof IfNode) {
                ifNode = (IfNode) node;
                while (!(node.predecessor() instanceof LoopBeginNode)) {
                    node = node.predecessor();
                    if (node instanceof StartNode) {
                        // node not found
                        return null;
                    }
                }
                // in this point node = loopBeginNode
                CompareNode condition = (CompareNode) ifNode.condition();
                if (condition.getX() instanceof PhiNode phi) {
                    startNode = phi.valueAt(0);
                    break;
                }
            }

            if (node instanceof MergeNode merge) {
                // When having a merge node, we follow the path any merges. We
                // choose the first
                // one, but any path will be joined.
                EndNode endNode = merge.forwardEndAt(0);
                node = endNode.predecessor();
            } else if (node instanceof LoopBeginNode loopBeginNode) {
                // It could happen that the start index is controlled by a
                // PhiNode instead of an
                // if-condition. In this case, we get the PhiNode
                NodeIterable<Node> usages = loopBeginNode.usages();
                for (Node u : usages) {
                    if (u instanceof PhiNode phiNode) {
                        startNode = phiNode.valueAt(0);
                        startFound = true;
                    }
                }
            } else {
                node = node.predecessor();
            }
        }
        return startNode;
    }

    private int getNumberOfParameterNodes(StructuredGraph graph) {
        return graph.getNodes().filter(ParameterNode.class).count();
    }

    private void findParametersWithReduceAnnotations(StructuredGraph graph) {
        final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();
        for (int index = 0; index < parameterAnnotations.length; index++) {
            for (Annotation annotation : parameterAnnotations[index]) {
                if (annotation instanceof Reduce) {
                    // If the number of arguments does not match, then we increase the index to
                    // obtain the correct one when indexing from getParameters. This is an issue
                    // when having inheritance with interfaces from Apache Flink. See issue
                    // #185 on Github
                    if (!graph.method().isStatic() || getNumberOfParameterNodes(graph) > parameterAnnotations.length) {
                        index++;
                    }
                    processReduceAnnotation(graph, index);
                }
            }
        }
    }

    private record ReductionMetadataNode(ValueNode value, ValueNode accumulator, ValueNode inputArray, ValueNode startNode) {
    }
}
