/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import static org.graalvm.compiler.graph.Graph.NodeEvent.NODE_ADDED;
import static org.graalvm.compiler.graph.Graph.NodeEvent.ZERO_USAGES;
import static org.graalvm.word.LocationIdentity.any;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.cfg.HIRLoop;
import org.graalvm.compiler.nodes.memory.FloatableAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryAnchorNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MemoryMap;
import org.graalvm.compiler.nodes.memory.MemoryMapNode;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.word.LocationIdentity;

import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVBarrierNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.VectorLoadElementNode;

/**
 * This phase modifies the functionality of the originally FloatingRead Phase
 * from Graal. The original phase reschedules and replaces Read-Nodes with
 * {@link FloatingReadNode} nodes in order to move loads closer to the actual
 * usage. An extra check is performed through shouldBeFloatingRead to check the
 * node origin of the access to prevent read associated with private memory to
 * be replaced by floating reads and schedule outside the scope of the parallel
 * loop.
 *
 * {@link org.graalvm.compiler.phases.common.FloatingReadPhase}
 */
public class TornadoFloatingReadReplacement extends Phase {
    private boolean createFloatingReads;
    private boolean createMemoryMapNodes;

    public static class MemoryMapImpl implements MemoryMap {

        private final EconomicMap<LocationIdentity, MemoryKill> lastMemorySnapshot;

        public MemoryMapImpl(TornadoFloatingReadReplacement.MemoryMapImpl memoryMap) {
            lastMemorySnapshot = EconomicMap.create(Equivalence.DEFAULT, memoryMap.lastMemorySnapshot);
        }

        public MemoryMapImpl(StartNode start) {
            this();
            lastMemorySnapshot.put(any(), start);
        }

        public MemoryMapImpl() {
            lastMemorySnapshot = EconomicMap.create(Equivalence.DEFAULT);
        }

        @Override
        public MemoryKill getLastLocationAccess(LocationIdentity locationIdentity) {
            MemoryKill lastLocationAccess;
            if (locationIdentity.isImmutable()) {
                return null;
            } else {
                lastLocationAccess = lastMemorySnapshot.get(locationIdentity);
                if (lastLocationAccess == null) {
                    lastLocationAccess = lastMemorySnapshot.get(any());
                    assert lastLocationAccess != null;
                }
                return lastLocationAccess;
            }
        }

        @Override
        public Iterable<LocationIdentity> getLocations() {
            return lastMemorySnapshot.getKeys();
        }

        public EconomicMap<LocationIdentity, MemoryKill> getMap() {
            return lastMemorySnapshot;
        }
    }

    public TornadoFloatingReadReplacement() {
        this(true, false);
    }

    /**
     * @param createFloatingReads
     *            specifies whether {@link FloatableAccessNode}s like
     *            {@link ReadNode} should be converted into floating nodes (e.g.,
     *            {@link FloatingReadNode}s) where possible
     * @param createMemoryMapNodes
     *            a {@link MemoryMapNode} will be created for each return if this is
     *            true
     */
    public TornadoFloatingReadReplacement(boolean createFloatingReads, boolean createMemoryMapNodes) {
        this.createFloatingReads = createFloatingReads;
        this.createMemoryMapNodes = createMemoryMapNodes;
    }

    @Override
    public float codeSizeIncrease() {
        return 1.50f;
    }

    /**
     * Removes nodes from a given set that (transitively) have a usage outside the
     * set.
     */
    private static EconomicSet<Node> removeExternallyUsedNodes(EconomicSet<Node> set) {
        boolean change;
        do {
            change = false;
            for (Iterator<Node> iter = set.iterator(); iter.hasNext();) {
                Node node = iter.next();
                for (Node usage : node.usages()) {
                    if (!set.contains(usage)) {
                        change = true;
                        iter.remove();
                        break;
                    }
                }
            }
        } while (change);
        return set;
    }

    protected void processNode(FixedNode node, EconomicSet<LocationIdentity> currentState) {
        if (node instanceof SingleMemoryKill) {
            processIdentity(currentState, ((SingleMemoryKill) node).getKilledLocationIdentity());
        } else if (node instanceof MultiMemoryKill) {
            for (LocationIdentity identity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                processIdentity(currentState, identity);
            }
        }
    }

    private static void processIdentity(EconomicSet<LocationIdentity> currentState, LocationIdentity identity) {
        if (identity.isMutable()) {
            currentState.add(identity);
        }
    }

    protected void processBlock(Block b, EconomicSet<LocationIdentity> currentState) {
        for (FixedNode n : b.getNodes()) {
            processNode(n, currentState);
        }
    }

    private EconomicSet<LocationIdentity> processLoop(HIRLoop loop, EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops) {
        LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
        EconomicSet<LocationIdentity> result = modifiedInLoops.get(loopBegin);
        if (result != null) {
            return result;
        }

        result = EconomicSet.create(Equivalence.DEFAULT);
        for (Loop<Block> inner : loop.getChildren()) {
            result.addAll(processLoop((HIRLoop) inner, modifiedInLoops));
        }

        for (Block b : loop.getBlocks()) {
            if (b.getLoop() == loop) {
                processBlock(b, result);
            }
        }

        modifiedInLoops.put(loopBegin, result);
        return result;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops = null;
        if (graph.hasLoops()) {
            modifiedInLoops = EconomicMap.create(Equivalence.IDENTITY);
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);
            for (Loop<?> l : cfg.getLoops()) {
                HIRLoop loop = (HIRLoop) l;
                processLoop(loop, modifiedInLoops);
            }
        }

        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener(EnumSet.of(NODE_ADDED, ZERO_USAGES));
        try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
            ReentrantNodeIterator.apply(new TornadoFloatingReadReplacement.FloatingReadClosure(modifiedInLoops, createFloatingReads, createMemoryMapNodes), graph.start(),
                    new TornadoFloatingReadReplacement.MemoryMapImpl(graph.start()));
        }

        for (Node n : removeExternallyUsedNodes(listener.getNodes())) {
            if (n.isAlive() && n instanceof FloatingNode) {
                n.replaceAtUsages(null);
                GraphUtil.killWithUnusedFloatingInputs(n);
            }
        }
        if (createFloatingReads) {
            graph.setAfterStage(StructuredGraph.StageFlag.FLOATING_READS);
        }
    }

    public static TornadoFloatingReadReplacement.MemoryMapImpl mergeMemoryMaps(AbstractMergeNode merge, List<? extends MemoryMap> states) {
        TornadoFloatingReadReplacement.MemoryMapImpl newState = new TornadoFloatingReadReplacement.MemoryMapImpl();

        EconomicSet<LocationIdentity> keys = EconomicSet.create(Equivalence.DEFAULT);
        for (MemoryMap other : states) {
            keys.addAll(other.getLocations());
        }
        assert checkNoImmutableLocations(keys);

        for (LocationIdentity key : keys) {
            int mergedStatesCount = 0;
            boolean isPhi = false;
            MemoryKill merged = null;
            for (MemoryMap state : states) {
                MemoryKill last = state.getLastLocationAccess(key);
                if (isPhi) {
                    // Fortify: Suppress Null Deference false positive (`isPhi == true` implies
                    // `merged != null`)
                    ((MemoryPhiNode) merged).addInput(ValueNodeUtil.asNode(last));
                } else {
                    if (merged == last) {
                        // nothing to do
                    } else if (merged == null) {
                        merged = last;
                    } else {
                        MemoryPhiNode phi = merge.graph().addWithoutUnique(new MemoryPhiNode(merge, key));
                        for (int j = 0; j < mergedStatesCount; j++) {
                            phi.addInput(ValueNodeUtil.asNode(merged));
                        }
                        phi.addInput(ValueNodeUtil.asNode(last));
                        merged = phi;
                        isPhi = true;
                    }
                }
                mergedStatesCount++;
            }
            newState.getMap().put(key, merged);
        }
        return newState;

    }

    private static boolean checkNoImmutableLocations(EconomicSet<LocationIdentity> keys) {
        keys.forEach(t -> {
            assert t.isMutable();
        });
        return true;
    }

    public static class FloatingReadClosure extends ReentrantNodeIterator.NodeIteratorClosure<TornadoFloatingReadReplacement.MemoryMapImpl> {

        private final EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops;
        private boolean createFloatingReads;
        private boolean createMemoryMapNodes;

        public FloatingReadClosure(EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops, boolean createFloatingReads, boolean createMemoryMapNodes) {
            this.modifiedInLoops = modifiedInLoops;
            this.createFloatingReads = createFloatingReads;
            this.createMemoryMapNodes = createMemoryMapNodes;
        }

        @Override
        protected TornadoFloatingReadReplacement.MemoryMapImpl processNode(FixedNode node, TornadoFloatingReadReplacement.MemoryMapImpl state) {

            if (node instanceof LoopExitNode) {
                final LoopExitNode loopExitNode = (LoopExitNode) node;
                final EconomicSet<LocationIdentity> modifiedInLoop = modifiedInLoops.get(loopExitNode.loopBegin());
                final boolean anyModified = modifiedInLoop.contains(LocationIdentity.any());
                state.getMap().replaceAll(
                        (locationIdentity, memoryNode) -> (anyModified || modifiedInLoop.contains(locationIdentity)) ? ProxyNode.forMemory(memoryNode, loopExitNode, locationIdentity) : memoryNode);
            }

            if (node instanceof MemoryAnchorNode) {
                processAnchor((MemoryAnchorNode) node, state);
                return state;
            }

            if (node instanceof MemoryAccess) {
                processAccess((MemoryAccess) node, state);
            }

            if (createFloatingReads && node instanceof FloatableAccessNode) {
                processFloatable((FloatableAccessNode) node, state);
            }
            if (node instanceof SingleMemoryKill) {
                processCheckpoint((SingleMemoryKill) node, state);
            } else if (node instanceof MultiMemoryKill) {
                processCheckpoint((MultiMemoryKill) node, state);
            }

            if (createMemoryMapNodes && node instanceof ReturnNode) {
                ((ReturnNode) node).setMemoryMap(node.graph().unique(new MemoryMapNode(state.getMap())));
            }
            return state;
        }

        /**
         * Improve the memory graph by re-wiring all usages of a
         * {@link MemoryAnchorNode} to the real last access location.
         */
        private static void processAnchor(MemoryAnchorNode anchor, TornadoFloatingReadReplacement.MemoryMapImpl state) {
            for (Node node : anchor.usages().snapshot()) {
                if (node instanceof MemoryAccess) {
                    MemoryAccess access = (MemoryAccess) node;
                    if (access.getLastLocationAccess() == anchor) {
                        MemoryKill lastLocationAccess = state.getLastLocationAccess(access.getLocationIdentity());
                        assert lastLocationAccess != null;
                        access.setLastLocationAccess(lastLocationAccess);
                    }
                }
            }

            if (anchor.hasNoUsages()) {
                anchor.graph().removeFixed(anchor);
            }
        }

        private static void processAccess(MemoryAccess access, TornadoFloatingReadReplacement.MemoryMapImpl state) {
            LocationIdentity locationIdentity = access.getLocationIdentity();
            if (!locationIdentity.equals(LocationIdentity.any())) {
                MemoryKill lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                access.setLastLocationAccess(lastLocationAccess);
            }
        }

        private static void processCheckpoint(SingleMemoryKill checkpoint, TornadoFloatingReadReplacement.MemoryMapImpl state) {
            processIdentity(checkpoint.getKilledLocationIdentity(), checkpoint, state);
        }

        private static void processCheckpoint(MultiMemoryKill checkpoint, TornadoFloatingReadReplacement.MemoryMapImpl state) {
            for (LocationIdentity identity : checkpoint.getKilledLocationIdentities()) {
                processIdentity(identity, checkpoint, state);
            }
        }

        private static void processIdentity(LocationIdentity identity, MemoryKill checkpoint, TornadoFloatingReadReplacement.MemoryMapImpl state) {
            if (identity.isAny()) {
                state.getMap().clear();
            }
            if (identity.isMutable()) {
                state.getMap().put(identity, checkpoint);
            }
        }

        /**
         * @param accessNode
         *            is a {@link FixedNode} that will be replaced by a
         *            {@link FloatingNode}. This method checks if the node that is going
         *            to be replaced has an {@link SPIRVBarrierNode} as next.
         */
        private static boolean isNextNodeSPIRVBarrierNode(FloatableAccessNode accessNode) {
            return (accessNode.next() instanceof SPIRVBarrierNode);
        }

        /**
         * @param nextNode
         *            is a {@link FixedNode} that will be replaced by a
         *            {@link FloatingNode}. This method removes the redundant
         *            {@link SPIRVBarrierNode}.
         */
        private static void replaceRedundantNextSPIRVBarrierNode(Node nextNode) {
            nextNode.replaceAtUsages(nextNode.successors().first());
            Node predecessor = nextNode.predecessor();
            predecessor.replaceFirstSuccessor(nextNode, nextNode.successors().first());
        }

        @SuppressWarnings("try")
        private static void processFloatable(FloatableAccessNode accessNode, TornadoFloatingReadReplacement.MemoryMapImpl state) {
            StructuredGraph graph = accessNode.graph();
            LocationIdentity locationIdentity = accessNode.getLocationIdentity();
            if (accessNode.canFloat() && shouldBeFloatingRead(accessNode)) {
                assert accessNode.getNullCheck() == false;
                MemoryKill lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                try (DebugCloseable position = accessNode.withNodeSourcePosition()) {
                    FloatingAccessNode floatingNode = accessNode.asFloatingNode();
                    assert floatingNode.getLastLocationAccess() == lastLocationAccess;
                    if (isNextNodeSPIRVBarrierNode(accessNode)) {
                        replaceRedundantNextSPIRVBarrierNode(accessNode.next());
                    }
                    graph.replaceFixedWithFloating(accessNode, floatingNode);
                }
            }
        }

        private static boolean shouldBeFloatingRead(FloatableAccessNode accessNode) {
            boolean shouldReadFloat = true;
            boolean isVectorLoad = accessNode.usages().filter(VectorLoadElementNode.class).isNotEmpty();
            boolean hasPrivateArrays = accessNode.graph().getNodes().filter(FixedArrayNode.class).isNotEmpty();

            for (Node node : accessNode.inputs().snapshot()) {
                if (node instanceof OffsetAddressNode) {
                    if (node.inputs().filter(FixedArrayNode.class).isNotEmpty() || hasPrivateArrays && !isVectorLoad) {
                        shouldReadFloat = false;
                    }
                }
            }
            return shouldReadFloat;
        }

        @Override
        protected TornadoFloatingReadReplacement.MemoryMapImpl merge(AbstractMergeNode merge, List<TornadoFloatingReadReplacement.MemoryMapImpl> states) {
            return mergeMemoryMaps(merge, states);
        }

        @Override
        protected TornadoFloatingReadReplacement.MemoryMapImpl afterSplit(AbstractBeginNode node, TornadoFloatingReadReplacement.MemoryMapImpl oldState) {
            TornadoFloatingReadReplacement.MemoryMapImpl result = new TornadoFloatingReadReplacement.MemoryMapImpl(oldState);
            if (node.predecessor() instanceof WithExceptionNode && node.predecessor() instanceof MemoryKill) {
                /*
                 * This WithExceptionNode cannot be the lastLocationAccess for a
                 * FloatingReadNode. Since it is both a memory kill and a control flow split,
                 * the scheduler cannot schedule anything immediately after the kill. It can
                 * only schedule in the normal or exceptional successor - and we have to tell
                 * the scheduler here which side it needs to choose by putting in the location
                 * identity on both successors.
                 */
                LocationIdentity killedLocationIdentity = node.predecessor() instanceof SingleMemoryKill ? ((SingleMemoryKill) node.predecessor()).getKilledLocationIdentity() : LocationIdentity.any();
                result.getMap().put(killedLocationIdentity, (MemoryKill) node);
            }
            return result;
        }

        @Override
        protected EconomicMap<LoopExitNode, TornadoFloatingReadReplacement.MemoryMapImpl> processLoop(LoopBeginNode loop, TornadoFloatingReadReplacement.MemoryMapImpl initialState) {
            EconomicSet<LocationIdentity> modifiedLocations = modifiedInLoops.get(loop);
            EconomicMap<LocationIdentity, MemoryPhiNode> phis = EconomicMap.create(Equivalence.DEFAULT);
            if (modifiedLocations.contains(LocationIdentity.any())) {
                // create phis for all locations if ANY is modified in the loop
                modifiedLocations = EconomicSet.create(Equivalence.DEFAULT, modifiedLocations);
                modifiedLocations.addAll(initialState.getMap().getKeys());
            }

            for (LocationIdentity location : modifiedLocations) {
                createMemoryPhi(loop, initialState, phis, location);
            }
            initialState.getMap().putAll(phis);

            ReentrantNodeIterator.LoopInfo<TornadoFloatingReadReplacement.MemoryMapImpl> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);

            UnmodifiableMapCursor<LoopEndNode, TornadoFloatingReadReplacement.MemoryMapImpl> endStateCursor = loopInfo.endStates.getEntries();
            while (endStateCursor.advance()) {
                int endIndex = loop.phiPredecessorIndex(endStateCursor.getKey());
                UnmodifiableMapCursor<LocationIdentity, MemoryPhiNode> phiCursor = phis.getEntries();
                while (phiCursor.advance()) {
                    LocationIdentity key = phiCursor.getKey();
                    PhiNode phi = phiCursor.getValue();
                    phi.initializeValueAt(endIndex, ValueNodeUtil.asNode(endStateCursor.getValue().getLastLocationAccess(key)));
                }
            }
            return loopInfo.exitStates;
        }

        private static void createMemoryPhi(LoopBeginNode loop, TornadoFloatingReadReplacement.MemoryMapImpl initialState, EconomicMap<LocationIdentity, MemoryPhiNode> phis,
                LocationIdentity location) {
            MemoryPhiNode phi = loop.graph().addWithoutUnique(new MemoryPhiNode(loop, location));
            phi.addInput(ValueNodeUtil.asNode(initialState.getLastLocationAccess(location)));
            phis.put(location, phi);
        }
    }
}