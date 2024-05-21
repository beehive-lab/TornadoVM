/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.nio.BufferOverflowException;
import java.util.Arrays;
import java.util.BitSet;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.common.BatchConfiguration;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;

public class TornadoVMGraphCompiler {
    /**
     * It generates the TornadoVM byte-codes from a Tornado Task Graph.
     *
     * @param graph
     *     TornadoVM execution Graph.
     * @param executionContext
     *     TornadoVM execution executionContext.
     * @return {@link TornadoVMBytecodeBuilder[]}
     */
    public static TornadoVMBytecodeResult[] compile(TornadoGraph graph, TornadoExecutionContext executionContext) {
        return compileTornadoGraphToTornadoBytecodes(graph, executionContext);
    }

    private static TornadoVMBytecodeResult[] compileTornadoGraphToTornadoBytecodes(TornadoGraph graph, TornadoExecutionContext executionContext) {
        final boolean isSingleContextCompilation = shouldGenerateSingleBytecode(executionContext);

        final int numContexts = isSingleContextCompilation ? 1 : executionContext.getValidContextSize();

        final BitSet asyncNodes = graph.filter(ContextOpNode.class::isInstance);

        final IntermediateTornadoGraph intermediateTornadoGraph = new IntermediateTornadoGraph(asyncNodes, graph);

        TornadoVMBytecodeResult[] tornadoVMBytecodeResults = new TornadoVMBytecodeResult[numContexts];

        intermediateTornadoGraph.analyzeDependencies();

        new TornadoLogger().debug("Compiling bytecodes...");

        for (int i = 0; i < tornadoVMBytecodeResults.length; i++) {

            TornadoVMBytecodeBuilder tornadoVMBytecodeBuilder = new TornadoVMBytecodeBuilder(isSingleContextCompilation);

            // Generate Context + BEGIN bytecode
            tornadoVMBytecodeBuilder.begin(1, 1, intermediateTornadoGraph.getNumberOfDependencies() + 1);

            // Generate bytecodes with no batches
            if (executionContext.getBatchSize() == TornadoExecutionContext.INIT_VALUE) {
                scheduleAndEmitTornadoVMBytecodes(tornadoVMBytecodeBuilder, graph, intermediateTornadoGraph, 0, 0, 0, i, executionContext);
            } else {
                // Generate bytecodes for batch processing.
                // It splits the iteration space and the input arrays into batches
                scheduleBatchDependentBytecodes(executionContext, tornadoVMBytecodeBuilder, graph, intermediateTornadoGraph);
            }

            // Last operation -> perform synchronisation
            if (TornadoOptions.ENABLE_STREAM_OUT_BLOCKING) {
                synchronizeOperationLastByteCode(tornadoVMBytecodeBuilder, intermediateTornadoGraph.getNumberOfDependencies());
            } else {
                tornadoVMBytecodeBuilder.barrier(intermediateTornadoGraph.getNumberOfDependencies());
            }

            // Generate END bytecode
            tornadoVMBytecodeBuilder.end();

            tornadoVMBytecodeResults[i] = new TornadoVMBytecodeResult(tornadoVMBytecodeBuilder.getCode(), tornadoVMBytecodeBuilder.getCodeSize());

        }

        if (executionContext.meta().shouldDumpTaskGraph()) {
            intermediateTornadoGraph.printDependencyMatrix();
        }

        return tornadoVMBytecodeResults;
    }

    private static boolean shouldGenerateSingleBytecode(TornadoExecutionContext executionContext) {
        boolean isSingleDeviceExecution = executionContext.getValidContextSize() == 1;
        boolean isBatchEnabled = executionContext.getBatchSize() != -1;

        if (isBatchEnabled && !isSingleDeviceExecution) {
            throw new TornadoRuntimeException("[UNSUPPORTED] Batches can only be enabled for single device execution");
        }

        return isSingleDeviceExecution;
    }

    private static void scheduleBatchDependentBytecodes(TornadoExecutionContext executionContext, TornadoVMBytecodeBuilder tornadoVMBytecodeBuilder, TornadoGraph graph,
            IntermediateTornadoGraph intermediateTornadoGraph) {
        final long batchSize = executionContext.getBatchSize();

        BatchConfiguration batchConfiguration = BatchConfiguration.computeChunkSizes(executionContext, batchSize);

        long offset = 0;
        long numberOfThreads = batchSize / batchConfiguration.getNumBytesType();
        for (int i = 0; i < batchConfiguration.getTotalChunks(); i++) {
            offset = (batchSize * i);
            scheduleAndEmitTornadoVMBytecodes(tornadoVMBytecodeBuilder, graph, intermediateTornadoGraph, offset, batchSize, numberOfThreads, 1, executionContext);
        }
        // Last chunk
        if (batchConfiguration.getRemainingChunkSize() != 0) {
            offset += (batchSize);
            numberOfThreads = batchConfiguration.getRemainingChunkSize() / batchConfiguration.getNumBytesType();
            long realBatchSize = batchConfiguration.getTotalChunks() == 0 ? 0 : batchConfiguration.getRemainingChunkSize();
            long realOffsetSize = batchConfiguration.getTotalChunks() == 0 ? 0 : offset;
            scheduleAndEmitTornadoVMBytecodes(tornadoVMBytecodeBuilder, graph, intermediateTornadoGraph, realOffsetSize, realBatchSize, numberOfThreads, 1, executionContext);
        }
    }

    private static void synchronizeOperationLastByteCode(TornadoVMBytecodeBuilder result, int numDepLists) {
        final byte[] code = result.getCode();
        int position = result.getLastCopyOutPosition();
        if (code[position] == TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS.value()) {
            code[position] = TornadoVMBytecodes.TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING.value();
        } else {
            result.barrier(numDepLists);
        }
    }

    private static void scheduleAndEmitTornadoVMBytecodes(TornadoVMBytecodeBuilder tornadoVMBytecodeBuilder, TornadoGraph graph, IntermediateTornadoGraph intermediateTornadoGraph, long offset,
            long bufferBatchSize, long nThreads, int id, TornadoExecutionContext executionContext) {
        final int[] nodeIds = intermediateTornadoGraph.getNodeIds();
        final BitSet[] dependencies = intermediateTornadoGraph.getDependencies();

        final BitSet scheduled = new BitSet(dependencies.length);
        scheduled.clear();
        final BitSet nodes = new BitSet(graph.getValid().length());
        final int[] depLists = new int[dependencies.length];
        Arrays.fill(depLists, -1);
        int index = 0;
        for (int i = 0; i < dependencies.length; i++) {
            if (!dependencies[i].isEmpty()) {
                final AbstractNode current = graph.getNode(nodeIds[i]);
                if (current instanceof DependentReadNode) {
                    continue;
                }
                depLists[i] = index;
                index++;
            }
        }

        while (scheduled.cardinality() < dependencies.length) {
            for (int i = 0; i < dependencies.length; i++) {
                if (!scheduled.get(i)) {
                    final BitSet outstandingDeps = new BitSet(nodes.length());
                    outstandingDeps.or(dependencies[i]);
                    outstandingDeps.andNot(nodes);

                    if (outstandingDeps.isEmpty()) {
                        final ContextOpNode asyncNode = (ContextOpNode) graph.getNode(nodeIds[i]);

                        if (shouldEmitAsyncNodeForTheCurrentContext(id, asyncNode, tornadoVMBytecodeBuilder.isSingleContext(), executionContext)) {
                            try {
                                tornadoVMBytecodeBuilder.emitAsyncNode(asyncNode, (dependencies[i].isEmpty()) ? -1 : depLists[i], offset, bufferBatchSize, nThreads);
                            } catch (BufferOverflowException e) {
                                throw new TornadoRuntimeException(
                                        STR."[ERROR] Buffer Overflow exception. Use -Dtornado.tvm.maxbytecodesize=<value> with value > \{TornadoVMBytecodeBuilder.MAX_TORNADO_VM_BYTECODE_SIZE} to increase the buffer code size");
                            }
                        }

                        for (int j = 0; j < dependencies.length; j++) {
                            if (j == i) {
                                continue;
                            }
                            if (dependencies[j].get(nodeIds[i]) && depLists[j] != -1) {
                                tornadoVMBytecodeBuilder.emitAddDependency(depLists[j]);
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
     * It determines whether an asynchronous node should be emitted for the current
     * context based on the provided parameters.
     *
     * @param id
     *     The index of the device in the list of devices from the execution
     *     context.
     * @param asyncNode
     *     The asynchronous node being considered for emission.
     * @param singleContext
     *     A flag indicating if the operation is being executed in a single
     *     context. If true, the method will always return true. If false, it
     *     will check the context's device at the given index.
     * @param executionContext
     *     The {@link TornadoExecutionContext} containing the list of
     *     devices.
     * @return True if the asynchronous node should be emitted for the current
     *     context, otherwise false.
     */
    private static boolean shouldEmitAsyncNodeForTheCurrentContext(int id, ContextOpNode asyncNode, boolean singleContext, TornadoExecutionContext executionContext) throws IndexOutOfBoundsException {
        return singleContext || (id >= 0 && id < executionContext.getDevices().size() && asyncNode.getContext().getDevice() == executionContext.getDevices().get(id));
    }

}
