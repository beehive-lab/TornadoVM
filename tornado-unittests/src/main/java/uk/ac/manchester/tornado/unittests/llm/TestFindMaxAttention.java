/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.llm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run the tests?
 * </p>
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.llm.TestFindMaxAttention
 * </code>
 */
public class TestFindMaxAttention extends TornadoTestBase {

    /**
     * Sequential implementation for finding max attention scores used for reference and validation.
     *
     * @param pos
     *     The current position in the sequence (inclusive upper bound)
     * @param seqLen
     *     The sequence length
     * @param numHeads
     *     The number of attention heads
     * @param attScores
     *     The attention scores array
     * @return
     *     A new FloatArray containing the maximum values for each head
     */
    public static FloatArray findMaxAttentionScoresSequential(int pos, int seqLen, int numHeads, FloatArray attScores) {
        FloatArray maxValues = new FloatArray(numHeads);

        // For each head, find the maximum score
        for (int h = 0; h < numHeads; h++) {
            float maxVal = Float.NEGATIVE_INFINITY;
            int attOffset = h * seqLen;

            // Find max in the range [0, pos]
            for (int t = 0; t <= pos; t++) {
                maxVal = Math.max(maxVal, attScores.get(attOffset + t));
            }

            maxValues.set(h, maxVal);
        }

        return maxValues;
    }

    /**
     * Parallel implementation for finding maximum attention scores.
     * Each head is processed by a separate work group, with threads collaborating
     * through parallel reduction to find the maximum value.
     *
     * @param context
     *     The kernel execution context
     * @param pos
     *     The current position in the sequence (inclusive upper bound)
     * @param seqLen
     *     The sequence length
     * @param attScores
     *     The attention scores array
     * @param maxValues
     *     The output array to store maximum values for each head
     */
    public static void findMaxAttentionScores(KernelContext context, int pos, int seqLen, FloatArray attScores, FloatArray maxValues, int localWorkgroupSize) {
        int globalId = context.globalIdx;  // Global thread ID
        int localId = context.localIdx;    // Thread ID within work group
        int workGroupSize = context.localGroupSizeX;  // Work group size
        int numWorkGroups = context.localGroupSizeX;  // Number of work groups

        // Calculate which head this thread is working on
        int h = globalId / workGroupSize;

        // Check if this thread should process a head (don't exceed numHeads)
        if (h < maxValues.getSize()) {
            float[] maxReduction = context.allocateFloatLocalArray(localWorkgroupSize);

            // Attention scores offset for this head
            int attOffset = h * seqLen;

            // Find the maximum value for this thread's assigned elements
            float maxVal = Float.NEGATIVE_INFINITY;

            // Each thread processes a stride of elements
            for (int t = localId; t <= pos; t += workGroupSize) {
                maxVal = Math.max(maxVal, attScores.get(attOffset + t));
            }

            // Store in local memory for reduction
            maxReduction[localId] = maxVal;

            // Parallel reduction to find global maximum
            for (int stride = workGroupSize / 2; stride > 0; stride /= 2) {
                context.localBarrier();
                if (localId < stride) {
                    maxReduction[localId] = Math.max(maxReduction[localId], maxReduction[localId + stride]);
                }
            }

            // Only the first thread in each work group writes the result
            if (localId == 0) {
                maxValues.set(h, maxReduction[0]);
            }
        }
    }

    /**
     * Test the parallel implementation against the sequential implementation.
     *
     * @throws TornadoExecutionPlanException
     *     If there's an error in the Tornado execution plan
     */
    @Test
    public void testFindMaxAttentionScores() throws TornadoExecutionPlanException {
        // Define the problem configuration
        final int numHeads = 16;        // Number of attention heads
        final int seqLen = 2048;        // Sequence length
        final int pos = 1024;           // Current position in sequence
        final int threadsPerHead = 256; // Set to a power of 2 (common for GPUs)

        // Create input and output arrays
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray maxValues = new FloatArray(numHeads);
        FloatArray expectedMaxValues;

        // Initialize attention scores with random values between -5 and 5
        for (int i = 0; i < attScores.getSize(); i++) {
            attScores.set(i, (float) (Math.random() * 10 - 5));
        }

        // Compute expected max values using sequential implementation
        expectedMaxValues = findMaxAttentionScoresSequential(pos, seqLen, numHeads, attScores);

        // Set up worker grid for parallel execution
        // IMPORTANT: Make sure global work size is >= local work size
        int localSize = 256; // This is your "threadsPerHead" or work group size
        int globalSize = numHeads * localSize; // This ensures global size is a multiple of local size

        WorkerGrid worker = new WorkerGrid2D(globalSize, 1);
        worker.setLocalWork(localSize, 1, 1);
        worker.setGlobalWork(globalSize, 1, 1);

        // Create grid scheduler
        GridScheduler gridScheduler = new GridScheduler("s0.findMaxAttentionScores", worker);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Create task graph
        TaskGraph taskGraph = new TaskGraph("s0")
        //@formatter:off
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores)
                .task("findMaxAttentionScores", TestFindMaxAttention::findMaxAttentionScores,
                        context, pos, seqLen, attScores, maxValues, localSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, maxValues);
        //@formatter:on

        // Execute the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Validate results
        for (int h = 0; h < numHeads; h++) {
            float expected = expectedMaxValues.get(h);
            float actual = maxValues.get(h);
            assertEquals("Mismatch at head " + h, expected, actual, 1e-5f);
        }

        System.out.println("All results match! Test passed.");
    }
}
