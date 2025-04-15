/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import uk.ac.manchester.tornado.api.WorkerGrid1D;
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
 * tornado-test -V uk.ac.manchester.tornado.unittests.llm.TestSoftMaxLayer
 * </code>
 */
public class TestSoftMaxLayer extends TornadoTestBase {

    /**
     * Sequential implementation of the softmax function used for reference and validation.
     * The implementation follows the standard approach of:
     * 1. Finding the maximum value for numerical stability
     * 2. Computing exp(x_i - max) for each element
     * 3. Computing the sum of all the exponentials
     * 4. Normalizing each value by dividing by the sum
     *
     * @param x
     *     The input array of values
     * @param size
     *     The size of the input array
     * @return A new FloatArray containing the softmax probabilities
     */
    public static FloatArray softmaxSequential(FloatArray x, int size) {
        FloatArray out = new FloatArray(size);

        // Find max value for numerical stability
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) {
            if (x.get(i) > maxVal) {
                maxVal = x.get(i);
            }
        }

        // Compute exp(x_i - max) and sum
        float sum = 0.0f;
        for (int i = 0; i < size; i++) {
            float val = (float) Math.exp(x.get(i) - maxVal);
            out.set(i, val);
            sum += val;
        }

        // Normalize
        for (int i = 0; i < size; i++) {
            out.set(i, out.get(i) / sum);
        }

        return out;
    }

    /**
     * First kernel in the softmax pipeline: find the maximum value in each work group.
     * Uses a parallel reduction pattern to efficiently find local maximum values.
     *
     * @param context
     *     The kernel execution context
     * @param input
     *     The input array containing the values
     * @param maxResult
     *     The output array to store the maximum values for each work group
     */
    public static void findMax(KernelContext context, FloatArray input, FloatArray maxResult, int localWorkgroupSize) {
        int globalIdx = context.globalIdx;       // Global thread ID
        int localIdx = context.localIdx;         // Local thread ID within the work group
        int localGroupSize = context.localGroupSizeX;  // Size of the work group
        int groupID = context.groupIdx;          // Work group ID

        // Allocate local memory for the reduction operation
        float[] localMax = context.allocateFloatLocalArray(localWorkgroupSize);

        // Initialize local memory with this thread's value or negative infinity if out of bounds
        if (globalIdx < input.getSize()) {
            localMax[localIdx] = input.get(globalIdx);
        } else {
            localMax[localIdx] = Float.NEGATIVE_INFINITY;
        }

        // Parallel reduction to find max - each iteration reduces the problem size by half
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();  // Ensure all threads have written to local memory
            if (localIdx < stride) {
                localMax[localIdx] = Math.max(localMax[localIdx], localMax[localIdx + stride]);
            }
        }

        // Only the first thread in each work group writes the result
        if (localIdx == 0) {
            maxResult.set(groupID, localMax[0]);
        }
    }

    /**
     * Second kernel in the softmax pipeline: find the global maximum from partial results.
     * This runs on a single thread to combine the results from all work groups.
     *
     * @param context
     *     The kernel execution context
     * @param partialMax
     *     The array containing the maximum values from each work group
     * @param numGroups
     *     The number of work groups (size of partialMax)
     */
    public static void finalizeMax(KernelContext context, FloatArray partialMax, int numGroups) {
        int globalIdx = context.globalIdx;

        // Only the first thread performs this operation
        if (globalIdx == 0) {
            float max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < numGroups; i++) {
                max = Math.max(max, partialMax.get(i));
            }
            // Store the global max in the first element for other kernels to use
            partialMax.set(0, max);
        }
    }

    /**
     * Third kernel in the softmax pipeline: compute exponentials and partial sums.
     * For each element:
     * 1. Computes exp(x_i - max) to avoid numerical overflow
     * 2. Stores the result in expOutput
     * 3. Performs a parallel reduction to compute partial sums for each work group
     *
     * @param context
     *     The kernel execution context
     * @param input
     *     The input array of values
     * @param expOutput
     *     The output array to store the exponential values
     * @param partialSums
     *     The array to store partial sums for each work group
     * @param maxValue
     *     The array containing the global maximum value in its first element
     */
    public static void computeExpAndPartialSums(KernelContext context, FloatArray input, FloatArray expOutput, FloatArray partialSums, FloatArray maxValue, int localWorkGroupSize) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;

        // Allocate local memory for sum reduction
        float[] localSums = context.allocateFloatLocalArray(localWorkGroupSize);

        // Get the global maximum value (computed in the previous kernel)
        float max = maxValue.get(0);

        // Compute exp(x - max) for this thread's element
        float expValue = 0.0f;
        if (globalIdx < input.getSize()) {
            expValue = (float) Math.exp(input.get(globalIdx) - max);
            expOutput.set(globalIdx, expValue);
        }

        // Store the exponential value in local memory for reduction
        localSums[localIdx] = expValue;

        // Parallel reduction to compute sum - each iteration reduces the problem size by half
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();  // Ensure all threads have written to local memory
            if (localIdx < stride) {
                localSums[localIdx] += localSums[localIdx + stride];
            }
        }

        // Only the first thread in each work group writes the partial sum
        if (localIdx == 0) {
            partialSums.set(groupID, localSums[0]);
        }
    }

    /**
     * Fourth kernel in the softmax pipeline: calculate the total sum.
     * This runs on a single thread to combine the partial sums from all work groups.
     *
     * @param context
     *     The kernel execution context
     * @param partialSums
     *     The array containing the partial sums from each work group
     * @param numGroups
     *     The number of work groups (size of partialSums)
     */
    public static void calculateTotalSum(KernelContext context, FloatArray partialSums, int numGroups) {
        int globalIdx = context.globalIdx;

        // Only the first thread performs this operation
        if (globalIdx == 0) {
            float sum = 0.0f;
            for (int i = 0; i < numGroups; i++) {
                sum += partialSums.get(i);
            }
            // Store the total sum in the first element for the normalize kernel to use
            partialSums.set(0, sum);
        }
    }

    /**
     * Final kernel in the softmax pipeline: normalize all values.
     * Each thread divides its assigned element by the total sum to produce
     * the final softmax probabilities.
     *
     * @param context
     *     The kernel execution context
     * @param expOutput
     *     The array containing exponential values
     * @param result
     *     The output array to store the final softmax probabilities
     * @param totalSum
     *     The array containing the total sum in its first element
     */
    public static void normal(KernelContext context, FloatArray expOutput, FloatArray result, FloatArray totalSum) {
        int globalIdx = context.globalIdx;

        // Get the total sum (computed in the previous kernel)
        float sum = totalSum.get(0);

        // Normalize this thread's element
        if (globalIdx < expOutput.getSize()) {
            result.set(globalIdx, expOutput.get(globalIdx) / sum);
        }
    }

    /**
     * Test the parallel softmax implementation against the sequential implementation.
     * This test:
     * 1. Creates input data with random values
     * 2. Computes the expected output using the sequential implementation
     * 3. Sets up and executes the parallel softmax pipeline
     * 4. Validates that the parallel implementation produces the same results
     *
     * @throws TornadoExecutionPlanException
     *     If there's an error in the Tornado execution plan
     */
    @Test
    public void testSoftmax() throws TornadoExecutionPlanException {
        // Define the problem size and work group configuration
        final int size = 1024;                   // Total number of elements
        final int localSize = 256;               // Work group size
        final int numGroups = size / localSize + (size % localSize == 0 ? 0 : 1);  // Number of work groups

        // Input and output arrays
        FloatArray input = new FloatArray(size);         // Input values
        FloatArray expValues = new FloatArray(size);     // Intermediate exponential values
        FloatArray output = new FloatArray(size);        // Output softmax probabilities
        FloatArray expectedOutput;                       // Expected output from sequential implementation

        // Arrays for reduction operations
        FloatArray maxValues = new FloatArray(numGroups);  // Stores max values for each work group
        FloatArray sumValues = new FloatArray(numGroups);  // Stores partial sums for each work group

        // Initialize input with random values between -5 and 5
        for (int i = 0; i < size; i++) {
            input.set(i, (float) (Math.random() * 10 - 5));
        }

        // Compute expected output using sequential implementation
        expectedOutput = softmaxSequential(input, size);

        // Set up worker grid for parallel kernels
        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        // Set up worker grid for single-threaded kernels
        WorkerGrid singleThreadWorker = new WorkerGrid1D(1);

        // Configure the grid scheduler to assign appropriate worker grids to each task
        GridScheduler gridScheduler = new GridScheduler("s0.findMax", worker);
        gridScheduler.addWorkerGrid("s0.finalizeMax", singleThreadWorker);
        gridScheduler.addWorkerGrid("s0.expAndSum", worker);
        gridScheduler.addWorkerGrid("s0.calculateSum", singleThreadWorker);
        gridScheduler.addWorkerGrid("s0.normalize", worker);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Create task graph - defining the pipeline of operations
        TaskGraph taskGraph = new TaskGraph("s0")
        //@formatter:off
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, maxValues, sumValues)
                // Step 1: Find maximum values in each work group
                .task("findMax", TestSoftMaxLayer::findMax, context, input, maxValues, localSize)
                // Step 2: Find global maximum
                .task("finalizeMax", TestSoftMaxLayer::finalizeMax, context, maxValues, numGroups)
                // Step 3: Compute exponentials and partial sums
                .task("expAndSum", TestSoftMaxLayer::computeExpAndPartialSums, context, input, expValues, sumValues, maxValues, localSize)
                // Step 4: Calculate total sum
                .task("calculateSum", TestSoftMaxLayer::calculateTotalSum, context, sumValues, numGroups)
                // Step 5: Normalize to get final softmax probabilities
                .task("normalize", TestSoftMaxLayer::normal, context, expValues, output, sumValues)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        //@formatter:on

        // Execute the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Validate results - compare parallel output with sequential output
        for (int i = 0; i < size; i++) {
            float expected = expectedOutput.get(i);
            float actual = output.get(i);
            assertEquals("Mismatch at index " + i, expected, actual, 1e-5f);  // Allow small floating-point differences
        }
    }
}
