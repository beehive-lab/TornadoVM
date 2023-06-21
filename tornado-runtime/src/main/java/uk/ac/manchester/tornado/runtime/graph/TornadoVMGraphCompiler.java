/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.common.BatchConfiguration;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;

import java.nio.BufferOverflowException;
import java.util.Arrays;
import java.util.BitSet;

public class TornadoVMGraphCompiler {
    /**
     * Generate TornadoVM byte-code from a Tornado Task Graph.
     *
     * @param graph
     *         TornadoVM execution Graph.
     * @param context
     *         TornadoVM execution context.
     * @param batchSize
     *         Batch size
     * @return {@link TornadoVMBytecodeBuilder[]}
     */
    public static TornadoVMBytecodeBuilder[] compile(TornadoGraph graph, TornadoExecutionContext context, long batchSize) {
        if (isSingleContextCompilation(context, batchSize)) {
            return compileSingleContextTornadoGraphToTornadoBytecodes(graph, context, batchSize);
        } else {
            return compileMultiContextTornadoGraphToTornadoBytecodes(graph, context);
        }
    }

    private static boolean isSingleContextCompilation(TornadoExecutionContext context, long batchSize) {
        boolean isSingleDeviceExecution = context.getValidContextSize() == 1;
        boolean isBatchEnabled = batchSize != -1;

        if (isBatchEnabled && !isSingleDeviceExecution) {
            throw new TornadoRuntimeException("[UNSUPPORTED] Batches can only be enabled for single device execution");
        }

        return isSingleDeviceExecution;
    }

    private static TornadoVMBytecodeBuilder[] compileSingleContextTornadoGraphToTornadoBytecodes(TornadoGraph graph, TornadoExecutionContext context, long batchSize) {
        TornadoVMBytecodeBuilder[] bytecodes = new TornadoVMBytecodeBuilder[1];
        final boolean isSingleContext = true;
        bytecodes[0] = new TornadoVMBytecodeBuilder();

        Tornado.debug("Single context/interpreter compilation");
        final BitSet asyncNodes = graph.filter((AbstractNode n) -> n instanceof ContextOpNode);

        final IntermediateTornadoGraph intermediateTornadoGraph = new IntermediateTornadoGraph(asyncNodes, graph);
        intermediateTornadoGraph.traverseIntermediateGraph();

        // Generate Context + BEGIN bytecode
        bytecodes[0].begin(1, intermediateTornadoGraph.getTasks().cardinality(), intermediateTornadoGraph.getNumberOfDependencies() + 1);

        BatchConfiguration sizeBatch = null;
        if (batchSize != -1) {
            sizeBatch = sizeBatch.computeChunkSizes(context, batchSize);
        }

        if (batchSize != -1) {
            // compute in batches
            long offset = 0;
            long nthreads = batchSize / sizeBatch.getNumBytesType();
            for (int i = 0; i < sizeBatch.getTotalChunks(); i++) {
                offset = (batchSize * i);
                scheduleAndEmitTornadoVMBytecodes(bytecodes[0], graph, intermediateTornadoGraph.getNodeIds(), intermediateTornadoGraph.getDependencies(), offset, batchSize, nthreads, 1,
                        isSingleContext);
            }
            // Last chunk
            if (sizeBatch.getRemainingChunkSize() != 0) {
                offset += (batchSize);
                nthreads = sizeBatch.getRemainingChunkSize() / sizeBatch.getNumBytesType();
                long realBatchSize = sizeBatch.getTotalChunks() == 0 ? 0 : sizeBatch.getRemainingChunkSize();
                long realOffsetSize = sizeBatch.getTotalChunks() == 0 ? 0 : offset;
                scheduleAndEmitTornadoVMBytecodes(bytecodes[0], graph, intermediateTornadoGraph.getNodeIds(), intermediateTornadoGraph.getDependencies(), realOffsetSize, realBatchSize, nthreads, 1,
                        isSingleContext);
            }

        } else {
            // Generate bytecodes with no batches
            scheduleAndEmitTornadoVMBytecodes(bytecodes[0], graph, intermediateTornadoGraph.getNodeIds(), intermediateTornadoGraph.getDependencies(), 1, isSingleContext);
        }

        // Last operation -> perform synchronisation
        if (TornadoOptions.ENABLE_STREAM_OUT_BLOCKING) {
            synchronizeOperationLastByteCode(bytecodes[0], intermediateTornadoGraph.getNumberOfDependencies());
        } else {
            bytecodes[0].barrier(intermediateTornadoGraph.getNumberOfDependencies());
        }
        // Generate END bytecode
        bytecodes[0].end();

        if (context.meta().shouldDumpSchedule()) {
            intermediateTornadoGraph.printDependencyMatrix();
        }

        return bytecodes;
    }

    private static TornadoVMBytecodeBuilder[] compileMultiContextTornadoGraphToTornadoBytecodes(TornadoGraph graph, TornadoExecutionContext context) {
        TornadoVMBytecodeBuilder[] bytecodes = new TornadoVMBytecodeBuilder[context.getValidContextSize()];

        Arrays.fill(bytecodes, new TornadoVMBytecodeBuilder());

        final BitSet asyncNodes = graph.filter((AbstractNode n) -> n instanceof ContextOpNode);

        final IntermediateTornadoGraph intermediateTornadoGraph = new IntermediateTornadoGraph(asyncNodes, graph);

        intermediateTornadoGraph.traverseIntermediateGraph();

        for (int i = 0; i < bytecodes.length; i++) {

            TornadoVMBytecodeBuilder bytecodeForTasks = new TornadoVMBytecodeBuilder();
            // Generate Context + BEGIN bytecode
            bytecodeForTasks.begin(1, 1, intermediateTornadoGraph.getNumberOfDependencies() + 1);

            // Generate bytecodes with no batches
            scheduleAndEmitTornadoVMBytecodes(bytecodeForTasks, graph, intermediateTornadoGraph.getNodeIds(), intermediateTornadoGraph.getDependencies(), i, context.getDevices().size() == 1);

            // Last operation -> perform synchronisation
            if (TornadoOptions.ENABLE_STREAM_OUT_BLOCKING) {
                synchronizeOperationLastByteCode(bytecodeForTasks, intermediateTornadoGraph.getNumberOfDependencies());
            } else {
                bytecodeForTasks.barrier(intermediateTornadoGraph.getNumberOfDependencies());
            }

            // Generate END bytecode
            bytecodeForTasks.end();

            bytecodes[i] = bytecodeForTasks;
        }

        if (context.meta().shouldDumpSchedule()) {
            intermediateTornadoGraph.printDependencyMatrix();
        }

        return bytecodes;
    }

    private static void synchronizeOperationLastByteCode(TornadoVMBytecodeBuilder result, int numDepLists) {
        final byte[] code = result.getCode();
        final int codeSize = result.getCodeSize();
        if (code[codeSize - 13] == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()) {
            code[codeSize - 13] = TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value();
        } else if (code[codeSize - 29] == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()) {
            code[codeSize - 29] = TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value();
        } else {
            result.barrier(numDepLists);
        }
    }

    private static void scheduleAndEmitTornadoVMBytecodes(TornadoVMBytecodeBuilder result, TornadoGraph graph, int[] nodeIds, BitSet[] deps, int id, boolean singleContext) {
        scheduleAndEmitTornadoVMBytecodes(result, graph, nodeIds, deps, 0, 0, 0, id, singleContext);
    }

    private static void scheduleAndEmitTornadoVMBytecodes(TornadoVMBytecodeBuilder bytecodes, TornadoGraph graph, int[] nodeIds, BitSet[] deps, long offset, long bufferBatchSize, long nThreads,
            int id, boolean singleContext) {
        final BitSet scheduled = new BitSet(deps.length);
        scheduled.clear();
        final BitSet nodes = new BitSet(graph.getValid().length());
        final int[] depLists = new int[deps.length];
        Arrays.fill(depLists, -1);
        int index = 0;
        for (int i = 0; i < deps.length; i++) {
            if (!deps[i].isEmpty()) {
                final AbstractNode current = graph.getNode(nodeIds[i]);
                if (current instanceof DependentReadNode) {
                    continue;
                }
                depLists[i] = index;
                index++;
            }
        }

        while (scheduled.cardinality() < deps.length) {
            for (int i = 0; i < deps.length; i++) {
                if (!scheduled.get(i)) {
                    final BitSet outstandingDeps = new BitSet(nodes.length());
                    outstandingDeps.or(deps[i]);
                    outstandingDeps.andNot(nodes);

                    if (outstandingDeps.isEmpty()) {
                        final ContextOpNode asyncNode = (ContextOpNode) graph.getNode(nodeIds[i]);

                        if (asyncNode.getContext().getDeviceIndex() == id || singleContext) {
                            try {
                                bytecodes.emitAsyncNode(asyncNode, asyncNode.getContext().getDeviceIndex(), (deps[i].isEmpty()) ? -1 : depLists[i], offset, bufferBatchSize, nThreads);
                            } catch (BufferOverflowException e) {
                                throw new TornadoRuntimeException(
                                        "[ERROR] Buffer Overflow exception. Use -Dtornado.tvm.maxbytecodesize=<value> with value > " + TornadoVMBytecodeBuilder.MAX_TORNADO_VM_BYTECODE_SIZE + " to increase the buffer code size");
                            }
                        }

                        for (int j = 0; j < deps.length; j++) {
                            if (j == i) {
                                continue;
                            }
                            if (deps[j].get(nodeIds[i]) && depLists[j] != -1) {
                                bytecodes.emitAddDependency(depLists[j]);
                            }
                        }
                        scheduled.set(i);
                        nodes.set(nodeIds[i]);
                    }
                }
            }
        }
    }

    /**
     * Represents an intermediate graph used during the traversal of a TornadoGraph. This class provides methods to traverse the intermediate graph and retrieve information about the graph's
     * structure.
     */
    private static class IntermediateTornadoGraph {
        private final TornadoGraph graph;
        private final BitSet asyncNodes;
        private static BitSet[] dependencies = new BitSet[0];
        private static BitSet tasks = null;
        private static int[] nodeIds = new int[0];
        private int index;
        private int numberOfDependencies;

        /**
         * Constructs an IntermediateTornadoGraph with the specified asyncNodes BitSet.
         *
         * @param asyncNodes
         *         The BitSet representing the asynchronous nodes in the graph.
         */
        public IntermediateTornadoGraph(BitSet asyncNodes, TornadoGraph graph) {
            this.graph = graph;
            this.asyncNodes = asyncNodes;
            this.dependencies = new BitSet[asyncNodes.cardinality()];
            this.tasks = new BitSet(asyncNodes.cardinality());
            this.nodeIds = new int[asyncNodes.cardinality()];
            this.index = 0;
            this.numberOfDependencies = 0;
        }

        public BitSet[] getDependencies() {
            return dependencies;
        }

        public BitSet getTasks() {
            return tasks;
        }

        public int[] getNodeIds() {
            return nodeIds;
        }

        public int getNumberOfDependencies() {
            return numberOfDependencies;
        }

        private void traverseIntermediateGraph() {
            for (int i = asyncNodes.nextSetBit(0); i != -1 && i < asyncNodes.length(); i = asyncNodes.nextSetBit(i + 1)) {
                dependencies[index] = calculateDependencies(graph, i);
                nodeIds[index] = i;
                if (graph.getNode(i) instanceof TaskNode) {
                    tasks.set(index);
                }
                if (!dependencies[index].isEmpty()) {
                    numberOfDependencies++;
                }
                index++;
            }
        }

        private static BitSet calculateDependencies(TornadoGraph graph, int i) {
            final BitSet dependencies = new BitSet(graph.getValid().length());
            final AbstractNode node = graph.getNode(i);
            for (AbstractNode input : node.getInputs()) {
                if (input instanceof ContextOpNode) {
                    if (input instanceof DependentReadNode) {
                        dependencies.set(((DependentReadNode) input).getDependent().getId());
                    } else {
                        dependencies.set(input.getId());
                    }
                }
            }
            return dependencies;
        }

        public void printDependencyMatrix() {
            StringBuffer output = new StringBuffer();
            output.append("TornadoGraph dependency matrix...\n");

            int maxNodeId = Arrays.stream(nodeIds).max().getAsInt();
            int maxLabelLength = Integer.toString(maxNodeId).length();

            output.append("+" + "-".repeat(maxLabelLength + 2) + "+");
            output.append("-".repeat(15) + "+");
            output.append("\n");

            for (int i = 0; i < nodeIds.length; i++) {
                final int nodeId = nodeIds[i];
                final String label = String.format("%" + maxLabelLength + "d", nodeId);
                final String type = (tasks.get(i)) ? "task" : "data";

                output.append("| ").append(String.format("%-" + maxLabelLength + "s", label));
                output.append(" [").append(type).append("]| ").append(toString(dependencies[i])).append("\n");

                output.append("|").append("-".repeat(maxLabelLength + 2)).append("+");
                output.append("-".repeat(15)).append("+");
                output.append("\n");
            }

            System.out.println(output.toString());
        }

        private static String toString(BitSet set) {
            if (set.isEmpty()) {
                return "<none>";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = set.nextSetBit(0); i != -1 && i < set.length(); i = set.nextSetBit(i + 1)) {
                sb.append("" + i + " ");
            }
            return sb.toString();
        }

    }
}
