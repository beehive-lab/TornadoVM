/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNodeExtension;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceSubNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNodeExtension;

import java.lang.annotation.Annotation;
import java.util.Optional;

public class TornadoReduceReplacement extends BasePhase<TornadoSketchTierContext> {
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
     *         Array to store
     * @param indexToStore
     *         Index used in the store array
     * @param currentNode
     *         Current node to be inspected
     * @return boolean
     */
    private boolean recursiveCheck(ValueNode arrayToStore, ValueNode indexToStore, ValueNode currentNode) {
        boolean isReduction = false;
        if (currentNode instanceof BinaryNode) {
            BinaryNode value = (BinaryNode) currentNode;
            ValueNode x = value.getX();
            isReduction = recursiveCheck(arrayToStore, indexToStore, x);
            if (!isReduction) {
                ValueNode y = value.getY();
                return recursiveCheck(arrayToStore, indexToStore, y);
            }
        } else if (currentNode instanceof LoadIndexedNode) {
            LoadIndexedNode loadNode = (LoadIndexedNode) currentNode;
            if (loadNode.array() == arrayToStore && loadNode.index() == indexToStore) {
                isReduction = true;
            }
        } else if (currentNode instanceof JavaReadNode) {
            JavaReadNode readNode = (JavaReadNode) currentNode;
            //((OffsetAddressNode)readNode.getAddress()).getOffset();
            if (readNode.getAddress() instanceof OffsetAddressNode) {
                OffsetAddressNode readAddress = (OffsetAddressNode) readNode.getAddress();
                if (indexToStore instanceof OffsetAddressNode) {
                    OffsetAddressNode writeAddress = (OffsetAddressNode) indexToStore;
                    isReduction = writeAddress.valueEquals(readAddress);
                }
            }

            // isReduction = ((OffsetAddressNode) indexToStore).getOffset().valueEquals(((OffsetAddressNode)readNode.getAddress()).getOffset());
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

    private ValueNode obtainInputArray(ValueNode currentNode, ValueNode outputArray) {
        ValueNode array = null;
        if (currentNode instanceof StoreIndexedNode) {
            StoreIndexedNode store = (StoreIndexedNode) currentNode;
            return obtainInputArray(store.value(), store.array());
        } else if (currentNode instanceof BinaryArithmeticNode) {
            BinaryArithmeticNode<?> value = (BinaryArithmeticNode<?>) currentNode;
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
        } else if (currentNode instanceof LoadIndexedNode) {
            LoadIndexedNode loadNode = (LoadIndexedNode) currentNode;
            if (loadNode.array() != outputArray) {
                array = loadNode.array();
            }
        } else if (currentNode instanceof JavaReadNode) {
            return obtainInputArray(((JavaReadNode) currentNode).getAddress(), outputArray);
        } else if (currentNode instanceof OffsetAddressNode) {
            return obtainInputArray(((OffsetAddressNode) currentNode).getBase(), outputArray);
        } else if (currentNode instanceof LoadFieldNode) {
            LoadFieldNode ldf = (LoadFieldNode) currentNode;
            if (ldf.getValue() instanceof PiNode) {
                PiNode pi = (PiNode) ldf.getValue();
                ParameterNode par = pi.inputs().filter(ParameterNode.class).first();
                if (par != outputArray) {
                    array = par;
                }
            }
        } else if (currentNode instanceof PiNode) {
            if (currentNode.inputs().filter(LoadFieldNode.class).isNotEmpty()) {
                LoadFieldNode ldf = currentNode.inputs().filter(LoadFieldNode.class).first();
                return obtainInputArray(ldf, outputArray);
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

        if (storeValue instanceof AddNode) {
            AddNode addNode = (AddNode) storeValue;
            final TornadoReduceAddNode atomicAdd = graph.addOrUnique(new TornadoReduceAddNode(addNode.getX(), addNode.getY()));
            accumulator = addNode.getY();
            value = atomicAdd;
            addNode.safeDelete();
        } else if (storeValue instanceof MulNode) {
            MulNode mulNode = (MulNode) storeValue;
            final TornadoReduceMulNode atomicMultiplication = graph.addOrUnique(new TornadoReduceMulNode(mulNode.getX(), mulNode.getY()));
            accumulator = mulNode.getX();
            value = atomicMultiplication;
            mulNode.safeDelete();
        } else if (storeValue instanceof SubNode) {
            SubNode subNode = (SubNode) storeValue;
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
        FixedWithNextNode st = null;
        if (node instanceof StoreIndexedNode) {
            StoreAtomicIndexedNodeExtension storeAtomicIndexedNodeExtension = new StoreAtomicIndexedNodeExtension(startNode);
            graph.addOrUnique(storeAtomicIndexedNodeExtension);
            StoreIndexedNode store = (StoreIndexedNode) node;
            st = graph.addOrUnique(new StoreAtomicIndexedNode(store.array(), store.index(), store.elementKind(), //
                    store.getBoundsCheck(), value, accumulator, inputArray, storeAtomicIndexedNodeExtension));
        } else if (node instanceof JavaWriteNode) {
            WriteAtomicNodeExtension writeAtomicNodeExtension = new WriteAtomicNodeExtension(startNode);
            graph.addOrUnique(writeAtomicNodeExtension);
            JavaWriteNode jwrite = (JavaWriteNode) node;
            st = graph.addOrUnique(new WriteAtomicNode(jwrite.getWriteKind(), jwrite.getAddress(), value, accumulator, inputArray, outArray, writeAtomicNodeExtension));
        }

        ValueNode arithmeticNode = null;
        if (value instanceof TornadoReduceAddNode) {
            TornadoReduceAddNode reduce = (TornadoReduceAddNode) value;
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

        if (st instanceof StoreAtomicIndexedNode) {
            ((StoreAtomicIndexedNode) st).setOptionalOperation(arithmeticNode);
        } else if (st instanceof WriteAtomicNode) {
            ((WriteAtomicNode) st).setOptionalOperation(arithmeticNode);
        }

        FixedNode next = node.next();

        predecessor.replaceFirstSuccessor(node, st);
        node.replaceAndDelete(st);
        st.setNext(next);
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
            if (node instanceof StoreIndexedNode) {
                StoreIndexedNode store = (StoreIndexedNode) node;

                boolean isReductionValue = checkIfReduction(store);
                if (!isReductionValue) {
                    continue;
                }

                boolean isInALoop = checkIfVarIsInLoop(store);
                if (!isInALoop) {
                    continue;
                }

                ValueNode inputArray = obtainInputArray(store.value(), store.array());
                ValueNode startNode = obtainStartLoopNode(store);

                ReductionMetadataNode reductionNode = createReductionNode(graph, store, inputArray, startNode);
                Node predecessor = node.predecessor();
                performNodeReplacement(graph, store, predecessor, reductionNode, store.array());

            } else if (node instanceof StoreFieldNode) {
                throw new TornadoRuntimeException("\n[NOT SUPPORTED] Node StoreFieldNode is not supported yet.");
            } else if (node instanceof PiNode) {
                // for memory segments
                for (Node piUsage : node.usages()) {
                    if (piUsage instanceof LoadFieldNode) {
                        LoadFieldNode lf = (LoadFieldNode) piUsage;
                        if (lf.uncheckedStamp().toString().contains("jdk.internal.foreign.NativeMemorySegmentImpl")) {
                            // We have the loading of the segment field
                            if (lf.usages().filter(PiNode.class).isNotEmpty()) {
                                PiNode pi = lf.usages().filter(PiNode.class).first();
                                if (pi.usages().filter(OffsetAddressNode.class).isNotEmpty()) {
                                    OffsetAddressNode offset = pi.usages().filter(OffsetAddressNode.class).first();
                                    if (offset.usages().filter(JavaWriteNode.class).isNotEmpty()) {
                                        JavaWriteNode jwrite = offset.usages().filter(JavaWriteNode.class).first();
                                        // follow these steps but adapted to jwrite
                                        ParameterNode par = node.inputs().filter(ParameterNode.class).first();
                                        boolean isReductionValue = recursiveCheck(par, offset, jwrite.value());
                                        if (!isReductionValue) {
                                            continue;
                                        }

                                        boolean isInALoop = checkIfVarIsInLoop(jwrite);
                                        if (!isInALoop) {
                                            continue;
                                        }

                                        ValueNode inputArray = obtainInputArray(jwrite.value(), par);
                                        ValueNode startNode = obtainStartLoopNode(jwrite);
                                        //
                                        ReductionMetadataNode reductionNode = createReductionNode(graph, jwrite, inputArray, startNode);
                                        Node predecessor = jwrite.predecessor();
                                        performNodeReplacement(graph, jwrite, predecessor, reductionNode, par);
                                    }
                                }
                            }
                        }

                    }
                }

            }
        }
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
                if (condition.getX() instanceof PhiNode) {
                    PhiNode phi = (PhiNode) condition.getX();
                    startNode = phi.valueAt(0);
                    break;
                }
            }

            if (node instanceof MergeNode) {
                // When having a merge node, we follow the path any merges. We
                // choose the first
                // one, but any path will be joined.
                MergeNode merge = (MergeNode) node;
                EndNode endNode = merge.forwardEndAt(0);
                node = endNode.predecessor();
            } else if (node instanceof LoopBeginNode) {
                // It could happen that the start index is controlled by a
                // PhiNode instead of an
                // if-condition. In this case, we get the PhiNode
                LoopBeginNode loopBeginNode = (LoopBeginNode) node;
                NodeIterable<Node> usages = loopBeginNode.usages();
                for (Node u : usages) {
                    if (u instanceof PhiNode) {
                        PhiNode phiNode = (PhiNode) u;
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

    private static class ReductionMetadataNode {
        private final ValueNode value;
        private final ValueNode accumulator;
        private final ValueNode inputArray;
        private final ValueNode startNode;

        ReductionMetadataNode(ValueNode value, ValueNode accumulator, ValueNode inputArray, ValueNode startNode) {
            super();
            this.value = value;
            this.accumulator = accumulator;
            this.inputArray = inputArray;
            this.startNode = startNode;
        }
    }
}
