/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeWorkList;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;
import java.util.*;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.nodes.vector.*;
import tornado.graal.phases.TornadoHighTierContext;

public class TornadoVectorResolver extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        /*
         * For some reason we generate a new vector value node when a parameter
         * is used... ... GVN may not be working in this case ... ... for now we
         * manually resolve the vector loads on parameters
         */
//		final VectorValueNode[] parameters = new VectorValueNode[graph.getNodes().filter(ParameterNode.class).count()];
//		graph.getNodes().filter(VectorLoadElementProxyNode.class).forEach(load -> {
//			if(load.getOrigin() instanceof ParameterNode){
//				final ParameterNode param = (ParameterNode) load.getOrigin();
//				if(parameters[param.index()] == null){
//					parameters[param.index()] = graph.addOrUnique(new VectorValueNode(load.getVectorKind(),load.getOrigin()));
//				}
//				load.setOrigin(parameters[param.index()]);
//				resolveProxyLoad(load);
//			}
//		});
        final NodeWorkList workList = graph.createNodeWorkList();
        workList.addAll(graph.getNodes().filter(VectorValueNode.class));

        workList.forEach(node -> process(graph, (VectorValueNode) node));

        graph.getNodes().filter(VectorValueNode.class).forEach(vector -> {
            if (vector.getOrigin() instanceof NewVectorNode) {
                vector.clearOrigin();
            }
        });

        final List<NewVectorNode> newVectors = graph.getNodes().filter(NewVectorNode.class).snapshot();
        for (final NewVectorNode newVector : newVectors) {
            GraphUtil.removeFixedWithUnusedInputs(newVector);
        }

        graph.getNodes().filter(VectorLoadElementProxyNode.class).forEach(this::resolveProxyLoad);

    }

    private void process(StructuredGraph graph, VectorValueNode vector) {

        final List<Node> usages = vector.usages().snapshot();
//		System.out.println("==============================");
//		System.out.printf("resolver: vector=%s, usages=",vector);
//		printNodes(usages);

        //final int vectorLength = vector.getVectorKind().getVectorLength();
        final int elementLoads = vector.usages().filter(VectorLoadElementProxyNode.class).count();
        final int elementStores = vector.usages().filter(VectorStoreElementProxyNode.class).count();

        if (elementLoads == 0 && elementStores == 0) {
            return;
        }

        //System.out.printf("resolver: vector=%s, length=%d, loads=%d, stores=%d\n", vector,
        //		vectorLength, elementLoads, elementStores);
        //System.out.printf("resolver: trying to resolve vector=%s, length=%d, loads=%d, stores=%d\n", vector,
        //		vectorLength, elementLoads, elementStores);
        // printFrontier(vector);
        final ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, true, true);
        cfg.computePostdominators();

        final Block originBlock = cfg.blockFor(vector.getOrigin());
        //System.out.printf("resolver: vector=%s, originBlock=%s\n",vector,originBlock);

        final Set<Block> defBlocks = findDefs(cfg, vector, usages);
        final Set<Block> useBlocks = findUses(cfg, usages);

        final Map<Block, ValueNode> blockExitValues = new HashMap<>();

        //System.out.printf("resovler: vector=%s, defs=", vector);
        //printBlocks(defBlocks);
        //System.out.printf("resovler: vector=%s, uses=", vector);
        //printBlocks(useBlocks);
        /*
         * populate blockExitValues with newly calculated values
         */
        for (final Block defBlock : defBlocks) {
            final ValueNode exitValue = renameVariables(cfg, defBlock, usages, vector, vector);
            blockExitValues.put(defBlock, exitValue);
            //System.out.printf("resolver: def renaming: block=%s, incoming=%s, exit=%s\n",defBlock, vector, exitValue);
        }

        for (final Block useBlock : useBlocks) {
            //System.out.printf("resolver: processing use block %s\n",useBlock);
            if (useBlock.getBeginNode() instanceof AbstractMergeNode) {
                final AbstractMergeNode mergeNode = (AbstractMergeNode) useBlock.getBeginNode();
                final ValueNode[] values = new ValueNode[mergeNode.phiPredecessorCount()];

                for (int i = 0; i < mergeNode.phiPredecessorCount(); i++) {
                    final AbstractEndNode end = mergeNode.phiPredecessorAt(i);

                    //System.out.printf("resolver: i=%s phi pred=%s, forward end=%s, cfgPred=%s\n",i,end,mergeNode.forwardEndAt(i),preds.get(i));
                    final Block endBlock = cfg.blockFor(end);

                    //System.out.printf("resolver: checking block %s, end=%s...\n",endBlock,end);
                    final Block defBlock = getDefinitionBlockAtExit(originBlock, endBlock, defBlocks);
                    values[i] = blockExitValues.get(defBlock);

                    //System.out.printf("resolver: path (%d) value is %s\n",i,values[i]);
                }

                final ValuePhiNode phi = graph.addOrUnique(new ValuePhiNode(vector
                        .stamp(), mergeNode, values));
                //System.out.printf("resolver: renaming variables... phi=%s\n",phi.toString());
                Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "before rename variables...");
                renameVariables(cfg, useBlock, usages, vector, phi);
            } else if (useBlock != originBlock) {

                final Block defBlock = getDefinitionBlockAtExit(originBlock, useBlock, defBlocks);
                final ValueNode incomingValue = blockExitValues.get(defBlock);
                //System.out.printf("resolver: block=%s, renaming %s with %s\n",useBlock,vector,incomingValue);
                renameVariables(cfg, useBlock, usages, vector, incomingValue);

            } else {
                //System.out.printf("resolver: block=%s, skipping origin block\n",originBlock);
            }
        }

        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "After renaming vector=%s", vector);
    }

    private ValueNode renameVariables(ControlFlowGraph cfg, Block block, List<Node> usages, VectorValueNode toReplace,
            ValueNode incomingValue) {

        if (block == null) {
            return toReplace;
        }

//		System.out.printf("resolver: renaming variables block=%s, toReplace=%s, incoming=%s\n",block,toReplace,incomingValue);
        /*
         * create an ordered list of vector operations... these may be
         * overlapping/interleaved
         */
        final List<Node> operations = new ArrayList<Node>();
        for (FixedNode node : block.getNodes()) {
            if (usages.contains(node)) {
                operations.add(node);
            } else if (node instanceof ValueAnchorNode && usages.contains(((ValueAnchorNode) node).getAnchoredNode())) {
                operations.add(node);
            }
        }

        usages.removeAll(operations);

        //System.out.printf("resolver: rename ops=");
        //printNodes(operations);

        /*
         * re-order vector operations
         */
        final int length = toReplace.getOCLKind().getVectorLength();
        final BitSet dirty = new BitSet(length);
        final Node[] stores = new Node[length];
        final Deque<Node> ordered = new ArrayDeque<Node>();
        while (!operations.isEmpty()) {
            for (int i = 0; i < operations.size(); i++) {
                final Node node = operations.get(i);
                //System.out.printf("resolver: examining node=%s\n", node);
                if (node instanceof VectorStoreElementProxyNode) {
                    final int lane = ((VectorStoreElementProxyNode) node).getLane();
                    //System.out.printf("resolver: found store node=%s on lane=%d\n", node, lane);
                    if (!dirty.get(lane)) {
                        //System.out
                        //		.printf("resolver: moving store node=%s on lane=%d\n", node, lane);
                        dirty.set(lane);
                        stores[lane] = node;
                        /*
                         * case where all lanes of a vector are set - flush the
                         * load
                         */
                        //System.out.printf("resolver: dirty lanes=%d\n", dirty.cardinality());
                        if (dirty.cardinality() == length) {
                            //System.out.printf("resolver: flushing %d stores\n", dirty.cardinality());
                            for (final Node store : stores) {
                                if (store != null) {
                                    ordered.add(store);
                                }
                            }
                            dirty.clear();
                            break;
                        }
                    }
                } else if (node instanceof VectorLoadElementProxyNode) {
                    final int lane = ((VectorLoadElementProxyNode) node).getLane();
                    //System.out.printf("resolver: found load node=%s on lane=%d\n", node, lane);
                    /*
                     * we can re-order loads as are there are no outstanding
                     * stores on this lane
                     */
                    if (!dirty.get(lane)) {
                        //System.out.printf("resolver: moving load node=%s on lane=%d\n", node, lane);
                        ordered.add(node);
                    }
                } else //System.out.printf("resolver: found vector use node=%s\n", node);
                /*
                 * ensure all stores have been committed before outputting this
                 * op
                 */ if (dirty.isEmpty()) {
                        //System.out.printf("resolver: moving vector use node=%s\n", node);
                        ordered.add(node);
                    }
            }

            /*
             * if we have a partial store then we flush it
             */
            if (!dirty.isEmpty()) {
                //System.out.printf("resolver: flushing %d stores\n", dirty.cardinality());
                for (final Node store : stores) {
                    if (store != null) {
                        ordered.add(store);
                    }
                }
            }

            /*
             * reset for next iterations
             */
            operations.removeAll(ordered);
            dirty.clear();

            for (int i = 0; i < stores.length; i++) {
                stores[i] = null;
            }

            //System.out.printf("resolver: operations left=");
            //printNodes(operations);
        }

        //System.out.printf("resolver: ordered ops=");
        //printNodes(ordered);
        //System.out.printf("incoming value=%s\n", incomingValue);
        ValueNode current = incomingValue;
        while (!ordered.isEmpty()) {
            Node node = ordered.peek();
            if (node instanceof VectorLoadElementProxyNode) {
                if (current instanceof VectorValueNode) {
                    processLoadElement(ordered, toReplace,
                            (VectorValueNode) current, length);
                } else if (current instanceof ValuePhiNode) {
                    final ValuePhiNode phi = (ValuePhiNode) current;
                    final ValueNode value = phi.singleValue();
                    if (!value.equals(PhiNode.MULTIPLE_VALUES) && value instanceof VectorValueNode) {
                        processLoadElement(ordered, toReplace,
                                (VectorValueNode) value, length);
                    }
                } else {
                    TornadoInternalError.shouldNotReachHere("current: " + current.toString());
                }
            } else if (node instanceof VectorStoreElementProxyNode) {
                current = processStoreElement(
                        ordered, toReplace, length);
            } else {
                processUse(ordered.pop(),
                        toReplace, current);
            }
        }

        //System.out.printf("resolver: end renaming\n");
        return current;
    }

    private void processUse(Node use, VectorValueNode toReplace,
            ValueNode replacement) {

        // this may situation should not occur... maybe remove?
        if (use instanceof VectorLoadNode) {
            return;
        }

        if (use instanceof ValueAnchorNode) {
            final ValueAnchorNode anchor = (ValueAnchorNode) use;
            anchor.getAnchoredNode().replaceFirstInput(toReplace, replacement);
            anchor.removeAnchoredNode();
            GraphUtil.unlinkFixedNode(anchor);
        } else {
            use.replaceFirstInput(toReplace, replacement);
        }

    }

    private VectorValueNode processStoreElement(Deque<Node> ordered, VectorValueNode toReplace,
            int length) {
        final VectorValueNode newVector = toReplace.duplicate();
        //System.out.printf("resolver: replacing %s with new vector=%s\n",toReplace, newVector);
        BitSet lanes = new BitSet(length);
        for (int i = 0; i < length; i++) {
            if (ordered.peek() instanceof VectorStoreElementProxyNode) {
                final VectorStoreElementProxyNode storeProxy = (VectorStoreElementProxyNode) ordered
                        .peek();
                //System.out.printf("resolver: next store is %s\n",storeProxy);
                int lane = storeProxy.getLane();
                //System.out.printf("resolver: processing store=%s, lane=%s\n",storeProxy,lane);
                if (!lanes.get(lane)) {
                    ordered.pop();
                    //System.out.printf("resolver: replacing inputs on store=%s\n",storeProxy);
                    storeProxy.replaceFirstInput(toReplace, newVector);
                    resolveProxyStore(newVector, storeProxy);
                } else {
                    break;
                }
            }
        }
        return newVector;
    }

    private void processLoadElement(Deque<Node> ordered, VectorValueNode toReplace,
            VectorValueNode replacement, int length) {
        while (ordered.peek() instanceof VectorLoadElementProxyNode) {
            final VectorLoadElementProxyNode loadProxy = (VectorLoadElementProxyNode) ordered.pop();
            //System.out.printf("resolver: processing load %s\n",loadProxy);
            loadProxy.replaceFirstInput(toReplace, replacement);
            resolveProxyLoad(loadProxy);

        }
    }

    private Block getDefinitionBlockAtExit(Block entry, Block exit, Set<Block> defBlocks) {
        Block defBlock = null;

        final Set<Block> visited = new HashSet<Block>();
        final Deque<Block> workList = new ArrayDeque<Block>();
        workList.push(exit);
        //visited.add(entry);

        //System.out.printf("resolver: defines on path: entry=%s, exit=%s, def blocks=",entry,exit);
        //printBlocks(defBlocks);
        //System.out.println();
        while (!workList.isEmpty()) {
            final Block current = workList.pop();
            visited.add(current);

            //System.out.printf("resolver: at %s...\n",current);
            if (defBlocks.contains(current)) {
                defBlock = current;
                //System.out.printf("resolver: def found in block %s\n",current);
                break;
            }

            for (Block pred : current.getPredecessors()) {
                //System.out.printf("resolver: checking pred %s of %s\n",pred, current);
                if (!visited.contains(pred)) {
                    //System.out.printf("resolver: %s added to work list\n",pred);
                    workList.push(pred);
                }
            }
        }

        return defBlock;
    }

    private Set<Block> findUses(ControlFlowGraph cfg, List<Node> usages) {
        final Set<Block> uses = new HashSet<Block>();
        for (Node node : usages) {
            final Block useBlock = cfg.blockFor(node);
            if (useBlock != null) {
                uses.add(useBlock);
            }
            //else
            //	System.out.printf("resolver: use=%s, occurs in null block\n",node);
        }
        return uses;
    }

    private Set<Block> findDefs(ControlFlowGraph cfg, VectorValueNode vector, List<Node> usages) {
        final Set<Block> defs = new HashSet<Block>();
        defs.add(cfg.blockFor(vector.getOrigin()));
        for (Node node : usages) {
            if (node instanceof VectorStoreElementProxyNode) {
                defs.add(cfg.blockFor(node));
            }
        }
        return defs;
    }

    @SuppressWarnings({"unused", "deprecation"})
    private static void printNodes(Collection<? extends Node> nodes) {
        System.out.printf("{ ");
        for (Node n : nodes) {
            System.out.printf("%d ", n.getId());
        }
        System.out.println("}");
    }

    @SuppressWarnings("unused")
    private static void printBlocks(Collection<Block> blocks) {
        System.out.printf("{ ");
        for (Block b : blocks) {
            System.out.printf("%s ", b);
        }
        System.out.println("}");
    }

    private void resolveProxyLoad(VectorLoadElementProxyNode proxyLoad) {
        final StructuredGraph graph = proxyLoad.graph();
        final VectorLoadElementNode load = proxyLoad.tryResolve();
        if (load != null) {
            graph.replaceFixedWithFloating(proxyLoad, graph.addOrUnique(load));
        }

    }

    private void resolveProxyStore(VectorValueNode vector, VectorStoreElementProxyNode proxyStore) {
        proxyStore.tryResolve();
        proxyStore.clearInputs();
        GraphUtil.removeFixedWithUnusedInputs(proxyStore);
        vector.removeUsage(proxyStore);
    }

}
