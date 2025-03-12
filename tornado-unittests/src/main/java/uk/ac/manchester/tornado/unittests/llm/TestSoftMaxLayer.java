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
    // Sequential implementation of softmax for correctness checking
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

    // Step 1: Find maximum value with reduction pattern
    public static void findMax(KernelContext context, FloatArray input, FloatArray maxResult) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;

        float[] localMax = context.allocateFloatLocalArray(256);

        // Initialize with this thread's value or negative infinity if out of bounds
        if (globalIdx < input.getSize()) {
            localMax[localIdx] = input.get(globalIdx);
        } else {
            localMax[localIdx] = Float.NEGATIVE_INFINITY;
        }

        // Parallel reduction to find max
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localMax[localIdx] = Math.max(localMax[localIdx], localMax[localIdx + stride]);
            }
        }

        // Write result for this work group
        if (localIdx == 0) {
            maxResult.set(groupID, localMax[0]);
        }
    }

    // Step 2: Find global maximum from partial results
    public static void finalizeMax(KernelContext context, FloatArray partialMax, int numGroups) {
        int globalIdx = context.globalIdx;

        if (globalIdx == 0) {
            float max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < numGroups; i++) {
                max = Math.max(max, partialMax.get(i));
            }
            // Store the global max in the first element
            partialMax.set(0, max);
        }
    }

    // Step 3: Compute exp(x - max) and partial sums
    public static void computeExpAndPartialSums(KernelContext context, FloatArray input, FloatArray expOutput, FloatArray partialSums, FloatArray maxValue) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;

        float[] localSums = context.allocateFloatLocalArray(256);
        float max = maxValue.get(0);

        // Compute exp(x - max) and prepare for sum reduction
        float expValue = 0.0f;
        if (globalIdx < input.getSize()) {
            expValue = (float) Math.exp(input.get(globalIdx) - max);
            expOutput.set(globalIdx, expValue);
        }

        localSums[localIdx] = expValue;

        // Sum reduction
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localSums[localIdx] += localSums[localIdx + stride];
            }
        }

        // Write partial sum for this work group
        if (localIdx == 0) {
            partialSums.set(groupID, localSums[0]);
        }
    }

    // Step 4: Calculate final sum
    public static void calculateTotalSum(KernelContext context, FloatArray partialSums, int numGroups) {
        int globalIdx = context.globalIdx;

        if (globalIdx == 0) {
            float sum = 0.0f;
            for (int i = 0; i < numGroups; i++) {
                sum += partialSums.get(i);
            }
            // Store the total sum in the first element
            partialSums.set(0, sum);
        }
    }

    // Step 5: Normalize
    public static void normal(KernelContext context, FloatArray expOutput, FloatArray result, FloatArray totalSum) {
        int globalIdx = context.globalIdx;
        float sum = totalSum.get(0);

        if (globalIdx < expOutput.getSize()) {
            result.set(globalIdx, expOutput.get(globalIdx) / sum);
        }
    }

    @Test
    public void testSoftmax() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        final int numGroups = size / localSize + (size % localSize == 0 ? 0 : 1);

        // Input and output arrays
        FloatArray input = new FloatArray(size);
        FloatArray expValues = new FloatArray(size);
        FloatArray output = new FloatArray(size);
        FloatArray expectedOutput;

        // Arrays for reduction operations
        FloatArray maxValues = new FloatArray(numGroups);
        FloatArray sumValues = new FloatArray(numGroups);

        // Initialize input with some values
        for (int i = 0; i < size; i++) {
            input.set(i, (float) (Math.random() * 10 - 5)); // Random values between -5 and 5
        }

        // Compute expected output using sequential implementation
        expectedOutput = softmaxSequential(input, size);

        // Set up worker grid and scheduler
        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        WorkerGrid singleThreadWorker = new WorkerGrid1D(1);

        GridScheduler gridScheduler = new GridScheduler("s0.findMax", worker);
        gridScheduler.setWorkerGrid("s0.finalizeMax", singleThreadWorker);
        gridScheduler.setWorkerGrid("s0.expAndSum", worker);
        gridScheduler.setWorkerGrid("s0.calculateSum", singleThreadWorker);
        gridScheduler.setWorkerGrid("s0.normalize", worker);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Create task graph
        TaskGraph taskGraph = new TaskGraph("s0")
        //@formatter:off
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, maxValues, sumValues)
                .task("findMax", TestSoftMaxLayer::findMax, context, input, maxValues)
                .task("finalizeMax", TestSoftMaxLayer::finalizeMax, context, maxValues, numGroups)
                .task("expAndSum", TestSoftMaxLayer::computeExpAndPartialSums, context, input, expValues, sumValues, maxValues)
                .task("calculateSum", TestSoftMaxLayer::calculateTotalSum, context, sumValues, numGroups)
                .task("normalize", TestSoftMaxLayer::normal, context, expValues, output, sumValues)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        //@formatter:on

        // Execute the task graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Validate results
        for (int i = 0; i < size; i++) {
            float expected = expectedOutput.get(i);
            float actual = output.get(i);
            assertEquals("Mismatch at index " + i, expected, actual, 1e-5f);
        }
    }

}
