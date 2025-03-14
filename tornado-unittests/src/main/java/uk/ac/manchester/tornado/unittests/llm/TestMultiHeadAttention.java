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

import java.util.Random;

import org.junit.Ignore;
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
 * tornado-test -V uk.ac.manchester.tornado.unittests.llm.TestMultiHeadAttention
 * </code>
 */
public class TestMultiHeadAttention extends TornadoTestBase {

    /**
     * Calculate attention scores between query and key vectors
     */
    public static void calculateAttentionScores(KernelContext context, int pos, int seqLen, FloatArray query, FloatArray keyCache, FloatArray attScores, int kvDim, int kvMul, int headSize, int loff) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Get the query vector offset for this head
        int queryOffset = h * headSize;

        // Attention scores offset for this head
        int attOffset = h * seqLen;

        // Iterate over all timesteps, including the current one
        for (int t = threadId; t <= pos; t += blockDim) {
            // Get the key vector for this head and at this timestep
            int keyOffset = loff + t * kvDim + (h / kvMul) * headSize;

            // Calculate the attention score as the dot product of query and key
            float score = 0.0f;
            for (int i = 0; i < headSize; i++) {
                score += query.get(queryOffset + i) * keyCache.get(keyOffset + i);
            }

            // Scale by sqrt(head_size)
            score /= Math.sqrt(headSize);

            // Save the score to the attention buffer
            attScores.set(attOffset + t, score);
        }
    }

    /**
     * Find maximum attention score for numerical stability in softmax
     */
    public static void findMaxAttentionScores(KernelContext context, int pos, int seqLen, FloatArray attScores, FloatArray maxValues, int workGroupSize) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Attention scores offset for this head
        int attOffset = h * seqLen;

        // Find the maximum value for numerical stability
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int t = threadId; t <= pos; t += blockDim) {
            maxVal = Math.max(maxVal, attScores.get(attOffset + t));
        }

        // Parallel reduction to find global maximum
        float[] maxReduction = context.allocateFloatLocalArray(workGroupSize); //TODO: ISSUES
        maxReduction[threadId] = maxVal;

        for (int stride = blockDim / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (threadId < stride) {
                maxReduction[threadId] = Math.max(maxReduction[threadId], maxReduction[threadId + stride]);
            }
        }

        // Thread 0 in each work group writes the max value
        if (threadId == 0) {
            maxValues.set(h, maxReduction[0]);
        }
    }

    public static void calculateExpAndSum(KernelContext context, int pos, int seqLen, FloatArray attScores, FloatArray maxValues, FloatArray expValues, FloatArray sumValues, int localWorkGroupSize) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Get max value for this head
        float maxVal = maxValues.get(h);

        // Attention scores and exp values offset for this head
        int attOffset = h * seqLen;
        int expOffset = h * seqLen;

        // Compute exp(score - max) and thread-local sum
        float expSum = 0.0f;
        for (int t = threadId; t <= pos; t += blockDim) {
            float score = attScores.get(attOffset + t);
            float expValue = (float) Math.exp(score - maxVal);
            expValues.set(expOffset + t, expValue);
            expSum += expValue;
        }

        // Ensure all exp values are computed before summing
        context.localBarrier();

        // Parallel reduction to get the total sum
        float[] sumReduction = context.allocateFloatLocalArray(localWorkGroupSize);
        sumReduction[threadId] = expSum;

        for (int stride = blockDim / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (threadId < stride) {
                sumReduction[threadId] += sumReduction[threadId + stride];
            }
        }

        // Thread 0 in each work group writes the sum
        if (threadId == 0) {
            sumValues.set(h, sumReduction[0]);
        }

        // Ensure sum value is written before proceeding
        context.localBarrier();
    }

    /**
     * Normalize exponential values to get softmax probabilities
     */
    public static void normalizeSoftmax(KernelContext context, int pos, int seqLen, FloatArray expValues, FloatArray sumValues, FloatArray attScores) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Get sum value for this head
        float sum = sumValues.get(h);

        // Exp values and attention scores offset for this head
        int expOffset = h * seqLen;
        int attOffset = h * seqLen;

        // Normalize values and write back to attention scores
        for (int t = threadId; t <= pos; t += blockDim) {
            float normalizedValue = expValues.get(expOffset + t) / sum;
            attScores.set(attOffset + t, normalizedValue);
        }
    }

    /**
     * Compute weighted sum of values based on attention weights
     */
    //    public static void computeWeightedSum(KernelContext context, int pos, int seqLen, FloatArray attScores, FloatArray valueCache, FloatArray output, int kvDim, int kvMul, int headSize, int loff) {
    //        int h = context.groupIdx;         // Head index
    //        int threadId = context.localIdx;  // Thread ID within work group
    //        int blockDim = context.localGroupSizeX;  // Work group size
    //
    //        // Attention scores offset for this head
    //        int attOffset = h * seqLen;
    //
    //        // Output offset for this head
    //        int outputOffset = h * headSize;
    //
    //        // Calculate weighted sum for each head dimension
    //        for (int i = threadId; i < headSize; i += blockDim) {
    //            float val = 0.0f;
    //            for (int t = 0; t <= pos; t++) {
    //                // Get the value vector for this head and timestep
    //                int valueOffset = loff + t * kvDim + (h / kvMul) * headSize;
    //
    //                // Get the attention weight for this timestep
    //                float a = attScores.get(attOffset + t);
    //
    //                val += a * valueCache.get(valueOffset + i);
    //            }
    //            output.set(outputOffset + i, val);
    //        }
    //    }

    public static void computeWeightedSum(KernelContext context, int pos, int seqLen, FloatArray attScores, FloatArray valueCache, FloatArray output, int kvDim, int kvMul, int headSize, int loff) {
        int h = context.groupIdx;         // Head index
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Attention scores offset for this head
        int attOffset = h * seqLen;

        // Output offset for this head
        int outputOffset = h * headSize;

        // Calculate weighted sum for each head dimension
        for (int i = threadId; i < headSize; i += blockDim) {
            float val = 0.0f;
            for (int t = 0; t <= pos; t++) {
                // Get the value vector for this head and timestep
                int valueOffset = loff + t * kvDim + (h / kvMul) * headSize;

                // Get the attention weight for this timestep
                float a = attScores.get(attOffset + t);

                val += a * valueCache.get(valueOffset + i);
            }
            output.set(outputOffset + i, val);
        }

        // Make sure all threads finish writing their outputs
        context.localBarrier();
    }

    /**
     * Also implement a reference sequential implementation for verification
     */
    public static void multiHeadAttentionSequential(int pos, int seqLen, float[] query, float[] keyCache, float[] valueCache, float[] output, int numHeads, int kvDim, int kvMul, int headSize,
            int loff) {

        // For each head
        for (int h = 0; h < numHeads; h++) {
            float[] attScores = new float[seqLen];

            // Calculate attention scores
            for (int t = 0; t <= pos; t++) {
                float score = 0.0f;
                for (int i = 0; i < headSize; i++) {
                    score += query[h * headSize + i] * keyCache[loff + t * kvDim + (h / kvMul) * headSize + i];
                }
                score /= Math.sqrt(headSize);
                attScores[t] = score;
            }

            // Apply softmax
            float maxVal = Float.NEGATIVE_INFINITY;
            for (int t = 0; t <= pos; t++) {
                maxVal = Math.max(maxVal, attScores[t]);
            }

            float sum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                attScores[t] = (float) Math.exp(attScores[t] - maxVal);
                sum += attScores[t];
            }

            for (int t = 0; t <= pos; t++) {
                attScores[t] /= sum;
            }

            // Weighted sum of values
            for (int i = 0; i < headSize; i++) {
                float val = 0.0f;
                for (int t = 0; t <= pos; t++) {
                    val += attScores[t] * valueCache[loff + t * kvDim + (h / kvMul) * headSize + i];
                }
                output[h * headSize + i] = val;
            }
        }
    }

    // Simple implementation of query-key dot product for a single head
    private static void singleHeadAttentionScores(float[] query, float[] key, float[] scores, int headSize) {
        for (int i = 0; i < scores.length; i++) {
            float score = 0.0f;
            for (int j = 0; j < headSize; j++) {
                score += query[j] * key[i * headSize + j];
            }
            scores[i] = score / (float) Math.sqrt(headSize);
        }
    }

    /**
     * Test for attention scores calculation for a single head.
     * This isolates the first step of the multi-head attention mechanism.
     */
    @Test
    public void testAttentionScoresCalculation() throws TornadoExecutionPlanException {
        final int headSize = 64;
        final int seqLen = 32;
        final int pos = 31; // Processing the entire sequence
        final int threadsPerHead = 32;
        final int kvDim = headSize;
        final int kvMul = 1;
        final int loff = 0;

        // Create data for a single head
        FloatArray query = new FloatArray(headSize);
        FloatArray keyCache = new FloatArray(seqLen * kvDim);
        FloatArray attScores = new FloatArray(seqLen);

        // Initialize with a fixed seed for reproducibility
        Random random = new Random(42);
        for (int i = 0; i < query.getSize(); i++) {
            query.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < keyCache.getSize(); i++) {
            keyCache.set(i, random.nextFloat() * 2 - 1);
        }

        // Sequential reference implementation for a single head
        float[] expectedScores = new float[seqLen];
        singleHeadAttentionScores(query.toHeapArray(), keyCache.toHeapArray(), expectedScores, headSize);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up a single-head test
        WorkerGrid worker = new WorkerGrid1D(threadsPerHead);
        worker.setGlobalWork(threadsPerHead, 1, 1);
        worker.setLocalWork(threadsPerHead, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.calculateSingleHeadScores", worker);

        // Define a kernel for single-head attention score calculation
        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, query, keyCache).task("calculateSingleHeadScores",
                TestMultiHeadAttention::calculateAttentionScores, context, pos, seqLen, query, keyCache, attScores, kvDim, kvMul, headSize, loff).transferToHost(DataTransferMode.EVERY_EXECUTION,
                        attScores);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Verify results
        float[] actualScores = attScores.toHeapArray();
        for (int i = 0; i <= pos; i++) {
            assertEquals("Mismatch at position " + i, expectedScores[i], actualScores[i], 1e-5f);
        }
    }

    /**
     * Test for finding the maximum attention score.
     * This is the second step in the multi-head attention mechanism.
     */
    @Test
    public void testFindMaxAttentionScores() throws TornadoExecutionPlanException {
        final int numHeads = 1;
        final int seqLen = 32;
        final int pos = 31;
        final int localSize = 32;

        // Create data for a single head
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray maxValues = new FloatArray(numHeads);

        // Initialize attention scores with known pattern
        Random random = new Random(42);
        float expectedMax = Float.NEGATIVE_INFINITY;
        for (int i = 0; i <= pos; i++) {
            float value = random.nextFloat() * 10 - 5; // Values between -5 and 5
            attScores.set(i, value);
            expectedMax = Math.max(expectedMax, value);
        }

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up a single-head test
        WorkerGrid worker = new WorkerGrid1D(localSize);
        worker.setGlobalWork(localSize, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s1.findMax", worker);

        // Define task for finding max values
        TaskGraph taskGraph = new TaskGraph("s1").transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores).task("findMax", TestMultiHeadAttention::findMaxAttentionScores, context, pos, seqLen,
                attScores, maxValues, localSize).transferToHost(DataTransferMode.EVERY_EXECUTION, maxValues);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Verify results
        assertEquals("Max value mismatch", expectedMax, maxValues.get(0), 1e-5f);
    }

    /**
     * Test for computing the exponentials and their sum.
     * This is the third step in the multi-head attention mechanism.
     */
    @Test
    public void testCalculateExpAndSum() throws TornadoExecutionPlanException {
        final int numHeads = 1;
        final int seqLen = 32;
        final int pos = 31;
        final int localSize = 32;

        // Create data for a single head
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray maxValues = new FloatArray(numHeads);
        FloatArray expValues = new FloatArray(numHeads * seqLen);
        FloatArray sumValues = new FloatArray(numHeads);

        // Initialize attention scores and max value
        Random random = new Random(42);
        float maxVal = -5.0f; // Fixed max value for predictability
        maxValues.set(0, maxVal);

        // Calculate expected results sequentially
        float expectedSum = 0.0f;
        float[] expectedExpValues = new float[seqLen];

        for (int i = 0; i <= pos; i++) {
            float score = random.nextFloat() * 10 - 5; // Values between -5 and 5
            attScores.set(i, score);

            float expValue = (float) Math.exp(score - maxVal);
            expectedExpValues[i] = expValue;
            expectedSum += expValue;
        }

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up single-head test
        WorkerGrid worker = new WorkerGrid1D(localSize);
        worker.setGlobalWork(localSize, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s2.calculateExp", worker);

        // Define task for calculating exp and sum
        TaskGraph taskGraph = new TaskGraph("s2").transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, maxValues).task("calculateExp", TestMultiHeadAttention::calculateExpAndSum, context,
                pos, seqLen, attScores, maxValues, expValues, sumValues, localSize).transferToHost(DataTransferMode.EVERY_EXECUTION, expValues, sumValues);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Verify results
        assertEquals("Sum value mismatch", expectedSum, sumValues.get(0), expectedSum * 1e-4f);
        for (int i = 0; i <= pos; i++) {
            assertEquals("Exp value mismatch at position " + i, expectedExpValues[i], expValues.get(i), expectedExpValues[i] * 1e-4f);
        }
    }

    /**
     * Test for normalizing the exponential values to get softmax probabilities.
     * This is the fourth step in the multi-head attention mechanism.
     */
    @Test
    public void testNormalizeSoftmax() throws TornadoExecutionPlanException {
        final int numHeads = 1;
        final int seqLen = 32;
        final int pos = 31;
        final int localSize = 32;

        // Create data for a single head
        FloatArray expValues = new FloatArray(numHeads * seqLen);
        FloatArray sumValues = new FloatArray(numHeads);
        FloatArray attScores = new FloatArray(numHeads * seqLen);

        // Initialize with known values
        Random random = new Random(42);
        float sum = 0.0f;
        float[] expectedSoftmax = new float[seqLen];

        for (int i = 0; i <= pos; i++) {
            float expValue = random.nextFloat() * 5; // Non-negative values
            expValues.set(i, expValue);
            sum += expValue;
        }
        sumValues.set(0, sum);

        // Calculate expected softmax values
        for (int i = 0; i <= pos; i++) {
            expectedSoftmax[i] = expValues.get(i) / sum;
        }

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up single-head test
        WorkerGrid worker = new WorkerGrid1D(localSize);
        worker.setGlobalWork(localSize, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s3.normalize", worker);

        // Define task for normalizing softmax
        TaskGraph taskGraph = new TaskGraph("s3").transferToDevice(DataTransferMode.FIRST_EXECUTION, expValues, sumValues).task("normalize", TestMultiHeadAttention::normalizeSoftmax, context, pos,
                seqLen, expValues, sumValues, attScores).transferToHost(DataTransferMode.EVERY_EXECUTION, attScores);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Verify results
        for (int i = 0; i <= pos; i++) {
            assertEquals("Softmax probability mismatch at position " + i, expectedSoftmax[i], attScores.get(i), 1e-5f);
        }
    }

    /**
     * Test for computing weighted sum of values based on attention weights.
     * This is the final step in the multi-head attention mechanism.
     */
    @Test
    public void testComputeWeightedSum() throws TornadoExecutionPlanException {
        final int numHeads = 1;
        final int seqLen = 32;
        final int pos = 31;
        final int headSize = 64;
        final int localSize = 64; // One thread per dimension in head
        final int kvDim = headSize;
        final int kvMul = 1;
        final int loff = 0;

        // Create data
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray valueCache = new FloatArray(seqLen * kvDim);
        FloatArray output = new FloatArray(numHeads * headSize);
        FloatArray expectedOutput = new FloatArray(numHeads * headSize);

        // Initialize with known values
        Random random = new Random(42);

        // Initialize attention scores as normalized probabilities
        float sum = 0.0f;
        for (int i = 0; i <= pos; i++) {
            float value = random.nextFloat();
            sum += value;
            attScores.set(i, value);
        }
        for (int i = 0; i <= pos; i++) {
            attScores.set(i, attScores.get(i) / sum); // Normalize to probabilities
        }

        // Initialize value cache
        for (int i = 0; i < valueCache.getSize(); i++) {
            valueCache.set(i, random.nextFloat() * 2 - 1); // Values between -1 and 1
        }

        // Calculate expected weighted sum sequentially
        for (int i = 0; i < headSize; i++) {
            float weightedSum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                int valueOffset = loff + t * kvDim + (0 / kvMul) * headSize; // For head 0
                weightedSum += attScores.get(t) * valueCache.get(valueOffset + i);
            }
            expectedOutput.set(i, weightedSum);
        }

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up test
        WorkerGrid worker = new WorkerGrid1D(localSize);
        worker.setGlobalWork(localSize, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s4.weightedSum", worker);

        // Define task for computing weighted sum
        TaskGraph taskGraph = new TaskGraph("s4").transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, valueCache).task("weightedSum", TestMultiHeadAttention::computeWeightedSum, context,
                pos, seqLen, attScores, valueCache, output, kvDim, kvMul, headSize, loff).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Verify results
        for (int i = 0; i < headSize; i++) {
            assertEquals("Weighted sum mismatch at dimension " + i, expectedOutput.get(i), output.get(i), Math.abs(expectedOutput.get(i) * 1e-4));
        }
    }

    /**
     * Test the full pipeline with a single head.
     * This combines all steps but with simplified single-head processing.
     */
    @Test
    public void testSingleHeadFullPipeline() throws TornadoExecutionPlanException {
        final int headSize = 64;
        final int seqLen = 32;
        final int pos = 31;
        final int localSize = 64; // Match max dimensions needed

        // Create single-head data
        FloatArray query = new FloatArray(headSize);
        FloatArray keyCache = new FloatArray(seqLen * headSize);
        FloatArray valueCache = new FloatArray(seqLen * headSize);
        FloatArray attScores = new FloatArray(seqLen);
        FloatArray maxValues = new FloatArray(1);
        FloatArray expValues = new FloatArray(seqLen);
        FloatArray sumValues = new FloatArray(1);
        FloatArray output = new FloatArray(headSize);

        // Initialize with random data
        Random random = new Random(42);
        for (int i = 0; i < query.getSize(); i++) {
            query.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < keyCache.getSize(); i++) {
            keyCache.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < valueCache.getSize(); i++) {
            valueCache.set(i, random.nextFloat() * 2 - 1);
        }

        // Calculate expected results sequentially
        FloatArray sequentialOutput = new FloatArray(headSize);

        // 1. Calculate attention scores
        float[] scores = new float[seqLen];
        for (int t = 0; t <= pos; t++) {
            float score = 0.0f;
            for (int i = 0; i < headSize; i++) {
                score += query.get(i) * keyCache.get(t * headSize + i);
            }
            scores[t] = score / (float) Math.sqrt(headSize);
        }

        // 2. Find max for numerical stability
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int t = 0; t <= pos; t++) {
            maxVal = Math.max(maxVal, scores[t]);
        }

        // 3. Compute exp and sum
        float sum = 0.0f;
        float[] expScores = new float[seqLen];
        for (int t = 0; t <= pos; t++) {
            expScores[t] = (float) Math.exp(scores[t] - maxVal);
            sum += expScores[t];
        }

        // 4. Normalize with softmax
        float[] softmaxScores = new float[seqLen];
        for (int t = 0; t <= pos; t++) {
            softmaxScores[t] = expScores[t] / sum;
        }

        // 5. Compute weighted sum
        for (int i = 0; i < headSize; i++) {
            float weightedSum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                weightedSum += softmaxScores[t] * valueCache.get(t * headSize + i);
            }
            sequentialOutput.set(i, weightedSum);
        }

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up task graphs for pipeline
        // Task Graph 1: Calculate attention scores
        TaskGraph tg1 = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, query, keyCache).task("calculateScores", TestMultiHeadAttention::calculateAttentionScores, context, pos,
                seqLen, query, keyCache, attScores, headSize, 1, headSize, 0).persistOnDevice(attScores);

        // Task Graph 2: Find maximum values
        TaskGraph tg2 = new TaskGraph("s1").consumeFromDevice(tg1.getTaskGraphName(), attScores).task("findMax", TestMultiHeadAttention::findMaxAttentionScores, context, pos, seqLen, attScores,
                maxValues, localSize).persistOnDevice(attScores, maxValues);

        // Task Graph 3: Calculate exponentials and sum
        TaskGraph tg3 = new TaskGraph("s2").consumeFromDevice(tg2.getTaskGraphName(), attScores, maxValues).task("calculateExp", TestMultiHeadAttention::calculateExpAndSum, context, pos, seqLen,
                attScores, maxValues, expValues, sumValues, localSize).persistOnDevice(attScores, expValues, sumValues);

        // Task Graph 4: Normalize to get softmax probabilities
        TaskGraph tg4 = new TaskGraph("s3").consumeFromDevice(tg3.getTaskGraphName(), expValues, sumValues).task("normalize", TestMultiHeadAttention::normalizeSoftmax, context, pos, seqLen, expValues,
                sumValues, attScores).persistOnDevice(attScores);

        // Task Graph 5: Calculate weighted sum
        TaskGraph tg5 = new TaskGraph("s4").transferToDevice(DataTransferMode.FIRST_EXECUTION, valueCache).consumeFromDevice(tg4.getTaskGraphName(), attScores).task("weightedSum",
                TestMultiHeadAttention::computeWeightedSum, context, pos, seqLen, attScores, valueCache, output, headSize, 1, headSize, 0).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        // Set up grid schedulers
        WorkerGrid worker1 = new WorkerGrid1D(localSize);
        worker1.setGlobalWork(localSize, 1, 1);
        worker1.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler1 = new GridScheduler("s0.calculateScores", worker1);

        WorkerGrid worker2 = new WorkerGrid1D(localSize);
        worker2.setGlobalWork(localSize, 1, 1);
        worker2.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler2 = new GridScheduler("s1.findMax", worker2);

        WorkerGrid worker3 = new WorkerGrid1D(localSize);
        worker3.setGlobalWork(localSize, 1, 1);
        worker3.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler3 = new GridScheduler("s2.calculateExp", worker3);

        WorkerGrid worker4 = new WorkerGrid1D(localSize);
        worker4.setGlobalWork(localSize, 1, 1);
        worker4.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler4 = new GridScheduler("s3.normalize", worker4);

        WorkerGrid worker5 = new WorkerGrid1D(localSize);
        worker5.setGlobalWork(localSize, 1, 1);
        worker5.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler5 = new GridScheduler("s4.weightedSum", worker5);

        // Create immutable task graphs
        ImmutableTaskGraph itg1 = tg1.snapshot();
        ImmutableTaskGraph itg2 = tg2.snapshot();
        ImmutableTaskGraph itg3 = tg3.snapshot();
        ImmutableTaskGraph itg4 = tg4.snapshot();
        ImmutableTaskGraph itg5 = tg5.snapshot();

        // Execute the pipeline
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg1, itg2, itg3, itg4, itg5)) {
            executionPlan.withGraph(0).withGridScheduler(gridScheduler1).execute();
            executionPlan.withGraph(1).withGridScheduler(gridScheduler2).execute();
            executionPlan.withGraph(2).withGridScheduler(gridScheduler3).execute();
            executionPlan.withGraph(3).withGridScheduler(gridScheduler4).execute();
            executionPlan.withGraph(4).withGridScheduler(gridScheduler5).execute();
        }

        // Verify results
        for (int i = 0; i < headSize; i++) {
            assertEquals("Output mismatch at dimension " + i, sequentialOutput.get(i), output.get(i), Math.abs(sequentialOutput.get(i) * 1e-4));
        }
    }

    /**
     * Test to debug the full multi-head attention implementation.
     * This uses multiple heads and checks intermediate outputs at each step.
     */
    @Ignore
    public void testMultiHeadAttentionDebug() throws TornadoExecutionPlanException {
        // Configuration parameters
        final int numHeads = 4;  // Reduced number of heads for debugging
        final int seqLen = 16;   // Smaller sequence length for easier debugging
        final int pos = 15;      // Process entire sequence
        final int threadsPerHead = 16; // Set to a power of 2
        final int headSize = 32; // Smaller head size for debugging
        final int kvDim = headSize * numHeads;
        final int kvMul = 1;
        final int loff = 0;
        final int localSize = 16;  // Threads per work group

        // Allocate data arrays
        FloatArray query = new FloatArray(numHeads * headSize);
        FloatArray keyCache = new FloatArray(seqLen * kvDim);
        FloatArray valueCache = new FloatArray(seqLen * kvDim);
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray maxValues = new FloatArray(numHeads);
        FloatArray expValues = new FloatArray(numHeads * seqLen);
        FloatArray sumValues = new FloatArray(numHeads);
        FloatArray output = new FloatArray(numHeads * headSize);

        // Initialize with random but deterministic data
        Random random = new Random(42);
        for (int i = 0; i < query.getSize(); i++) {
            query.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < keyCache.getSize(); i++) {
            keyCache.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < valueCache.getSize(); i++) {
            valueCache.set(i, random.nextFloat() * 2 - 1);
        }

        // Sequential reference implementation
        FloatArray sequentialOutput = new FloatArray(numHeads * headSize);
        TestMultiHeadAttention.multiHeadAttentionSequential(pos, seqLen, query.toHeapArray(), keyCache.toHeapArray(), valueCache.toHeapArray(), sequentialOutput.toHeapArray(), numHeads, kvDim, kvMul,
                headSize, loff);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up task graphs for debugging
        int globalSize = numHeads * localSize; // Ensure global size is multiple of local size

        // Task Graph 1: Calculate attention scores
        TaskGraph tg1 = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, query, keyCache).task("calculateScores", TestMultiHeadAttention::calculateAttentionScores, context, pos,
                seqLen, query, keyCache, attScores, kvDim, kvMul, headSize, loff).transferToHost(DataTransferMode.EVERY_EXECUTION, attScores);

        WorkerGrid worker1 = new WorkerGrid1D(globalSize);
        worker1.setGlobalWork(globalSize, 1, 1);
        worker1.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler1 = new GridScheduler("s0.calculateScores", worker1);

        ImmutableTaskGraph itg1 = tg1.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg1)) {
            executionPlan.withGridScheduler(gridScheduler1).execute();
        }

        // Compare intermediate attention score results
        float[][] refAttScores = new float[numHeads][seqLen];
        for (int h = 0; h < numHeads; h++) {
            for (int t = 0; t <= pos; t++) {
                float score = 0.0f;
                for (int i = 0; i < headSize; i++) {
                    score += query.get(h * headSize + i) * keyCache.get(loff + t * kvDim + (h / kvMul) * headSize + i);
                }
                score /= Math.sqrt(headSize);
                refAttScores[h][t] = score;
            }
        }

        // Verify attention scores
        for (int h = 0; h < numHeads; h++) {
            for (int t = 0; t <= pos; t++) {
                float expected = refAttScores[h][t];
                float actual = attScores.get(h * seqLen + t);
                assertEquals("Attention score mismatch at head " + h + ", position " + t, expected, actual, Math.abs(expected * 1e-5f));
            }
        }

        // Task Graph 2: Find maximum values
        TaskGraph tg2 = new TaskGraph("s1").transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores).task("findMax", TestMultiHeadAttention::findMaxAttentionScores, context, pos, seqLen,
                attScores, maxValues, localSize).transferToHost(DataTransferMode.EVERY_EXECUTION, maxValues);

        WorkerGrid worker2 = new WorkerGrid1D(globalSize);
        worker2.setGlobalWork(globalSize, 1, 1);
        worker2.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler2 = new GridScheduler("s1.findMax", worker2);

        ImmutableTaskGraph itg2 = tg2.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg2)) {
            executionPlan.withGridScheduler(gridScheduler2).execute();
        }

        // Calculate reference max values
        float[] refMaxValues = new float[numHeads];
        for (int h = 0; h < numHeads; h++) {
            refMaxValues[h] = Float.NEGATIVE_INFINITY;
            for (int t = 0; t <= pos; t++) {
                refMaxValues[h] = Math.max(refMaxValues[h], refAttScores[h][t]);
            }
        }

        // Verify max values
        for (int h = 0; h < numHeads; h++) {
            assertEquals("Max value mismatch for head " + h, refMaxValues[h], maxValues.get(h), Math.abs(refMaxValues[h] * 1e-5f));
        }

        // Task Graph 3: Calculate exponentials and sum
        TaskGraph tg3 = new TaskGraph("s2").transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, maxValues).task("calculateExp", TestMultiHeadAttention::calculateExpAndSum, context, pos,
                seqLen, attScores, maxValues, expValues, sumValues, localSize).transferToHost(DataTransferMode.EVERY_EXECUTION, expValues, sumValues);

        WorkerGrid worker3 = new WorkerGrid1D(globalSize);
        worker3.setGlobalWork(globalSize, 1, 1);
        worker3.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler3 = new GridScheduler("s2.calculateExp", worker3);

        ImmutableTaskGraph itg3 = tg3.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg3)) {
            executionPlan.withGridScheduler(gridScheduler3).execute();
        }

        // Calculate reference exp values and sums
        float[][] refExpValues = new float[numHeads][seqLen];
        float[] refSumValues = new float[numHeads];

        for (int h = 0; h < numHeads; h++) {
            refSumValues[h] = 0.0f;
            for (int t = 0; t <= pos; t++) {
                refExpValues[h][t] = (float) Math.exp(refAttScores[h][t] - refMaxValues[h]);
                refSumValues[h] += refExpValues[h][t];
            }
        }

        // Verify exp values and sums
        for (int h = 0; h < numHeads; h++) {
            assertEquals("Sum value mismatch for head " + h, refSumValues[h], sumValues.get(h), Math.abs(refSumValues[h] * 1e-4f));

            for (int t = 0; t <= pos; t++) {
                assertEquals("Exp value mismatch at head " + h + ", position " + t, refExpValues[h][t], expValues.get(h * seqLen + t), Math.abs(refExpValues[h][t] * 1e-4f));
            }
        }

        // Task Graph 4: Normalize softmax
        TaskGraph tg4 = new TaskGraph("s3").transferToDevice(DataTransferMode.FIRST_EXECUTION, expValues, sumValues).task("normalize", TestMultiHeadAttention::normalizeSoftmax, context, pos, seqLen,
                expValues, sumValues, attScores).transferToHost(DataTransferMode.EVERY_EXECUTION, attScores);

        WorkerGrid worker4 = new WorkerGrid1D(globalSize);
        worker4.setGlobalWork(globalSize, 1, 1);
        worker4.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler4 = new GridScheduler("s3.normalize", worker4);

        ImmutableTaskGraph itg4 = tg4.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg4)) {
            executionPlan.withGridScheduler(gridScheduler4).execute();
        }

        // Calculate reference softmax values
        float[][] refSoftmaxValues = new float[numHeads][seqLen];
        for (int h = 0; h < numHeads; h++) {
            for (int t = 0; t <= pos; t++) {
                refSoftmaxValues[h][t] = refExpValues[h][t] / refSumValues[h];
            }
        }

        // Verify softmax values
        for (int h = 0; h < numHeads; h++) {
            for (int t = 0; t <= pos; t++) {
                assertEquals("Softmax value mismatch at head " + h + ", position " + t, refSoftmaxValues[h][t], attScores.get(h * seqLen + t), 1e-5f);
            }
        }

        // Task Graph 5: Calculate weighted sum
        TaskGraph tg5 = new TaskGraph("s4").transferToDevice(DataTransferMode.FIRST_EXECUTION, attScores, valueCache).task("weightedSum", TestMultiHeadAttention::computeWeightedSum, context, pos,
                seqLen, attScores, valueCache, output, kvDim, kvMul, headSize, loff).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        WorkerGrid worker5 = new WorkerGrid1D(globalSize);
        worker5.setGlobalWork(globalSize, 1, 1);
        worker5.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler5 = new GridScheduler("s4.weightedSum", worker5);

        ImmutableTaskGraph itg5 = tg5.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg5)) {
            executionPlan.withGridScheduler(gridScheduler5).execute();
        }

        // Verify final output
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < headSize; i++) {
                assertEquals("Output value mismatch at head " + h + ", dimension " + i, sequentialOutput.get(h * headSize + i), output.get(h * headSize + i), Math.abs(sequentialOutput.get(
                        h * headSize + i) * 1e-4f));
            }
        }
    }

    @Test
    public void testMultiHeadAttentionFixed() throws TornadoExecutionPlanException {
        // Configuration parameters
        final int numHeads = 4;
        final int seqLen = 16;
        final int pos = 15;
        final int headSize = 32;
        final int localSize = 16;
        final int kvDim = headSize * numHeads;
        final int kvMul = 1;
        final int loff = 0;

        // Allocate data arrays
        FloatArray query = new FloatArray(numHeads * headSize);
        FloatArray keyCache = new FloatArray(seqLen * kvDim);
        FloatArray valueCache = new FloatArray(seqLen * kvDim);
        FloatArray attScores = new FloatArray(numHeads * seqLen);
        FloatArray maxValues = new FloatArray(numHeads);
        FloatArray expValues = new FloatArray(numHeads * seqLen);
        FloatArray sumValues = new FloatArray(numHeads);
        FloatArray output = new FloatArray(numHeads * headSize);

        // Initialize with random but deterministic data
        Random random = new Random(42);
        for (int i = 0; i < query.getSize(); i++) {
            query.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < keyCache.getSize(); i++) {
            keyCache.set(i, random.nextFloat() * 2 - 1);
        }
        for (int i = 0; i < valueCache.getSize(); i++) {
            valueCache.set(i, random.nextFloat() * 2 - 1);
        }

        // Make a copy of the input data for the sequential reference
        float[] queryCopy = query.toHeapArray();
        float[] keyCacheCopy = keyCache.toHeapArray();
        float[] valueCacheCopy = valueCache.toHeapArray();

        // Sequential reference implementation
        float[] sequentialOutputArray = new float[numHeads * headSize];
        multiHeadAttentionSequential(pos, seqLen, queryCopy, keyCacheCopy, valueCacheCopy,
                sequentialOutputArray, numHeads, kvDim, kvMul, headSize, loff);

        // Convert to FloatArray for easier comparison
        FloatArray sequentialOutput = FloatArray.fromArray(sequentialOutputArray);

        System.out.println("Executing integrated pipeline test...");

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up task graphs for the pipeline test
        int globalSize = numHeads * localSize;

        // =====================================================================
        // IMPORTANT: We must use PERSIST_ON_DEVICE to maintain data on the GPU
        // =====================================================================

        // Task Graph 1: Calculate attention scores
        TaskGraph tg1 = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, query, keyCache)
                .task("calculateScores", TestMultiHeadAttention::calculateAttentionScores,
                        context, pos, seqLen, query, keyCache, attScores, kvDim, kvMul, headSize, loff)
                .persistOnDevice(attScores);

        // Task Graph 2: Find maximum values
        TaskGraph tg2 = new TaskGraph("s1")
                .consumeFromDevice(tg1.getTaskGraphName(), attScores)
                .task("findMax", TestMultiHeadAttention::findMaxAttentionScores,
                        context, pos, seqLen, attScores, maxValues, localSize)
                .persistOnDevice(attScores, maxValues);

        // Task Graph 3: Calculate exponentials and sum
        TaskGraph tg3 = new TaskGraph("s2")
                .consumeFromDevice(tg2.getTaskGraphName(), attScores, maxValues)
                .task("calculateExp", TestMultiHeadAttention::calculateExpAndSum,
                        context, pos, seqLen, attScores, maxValues, expValues, sumValues, localSize)
                .persistOnDevice(attScores, expValues, sumValues);

        // Task Graph 4: Normalize softmax
        TaskGraph tg4 = new TaskGraph("s3")
                .consumeFromDevice(tg3.getTaskGraphName(), expValues, sumValues)
                .task("normalize", TestMultiHeadAttention::normalizeSoftmax,
                        context, pos, seqLen, expValues, sumValues, attScores)
                .persistOnDevice(attScores);

        // Task Graph 5: Calculate weighted sum
        TaskGraph tg5 = new TaskGraph("s4")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, valueCache)
                .consumeFromDevice(tg4.getTaskGraphName(), attScores)
                .task("weightedSum", TestMultiHeadAttention::computeWeightedSum,
                        context, pos, seqLen, attScores, valueCache, output, kvDim, kvMul, headSize, loff)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        // Set up worker grids and grid schedulers
        WorkerGrid worker1 = new WorkerGrid1D(globalSize);
        worker1.setGlobalWork(globalSize, 1, 1);
        worker1.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler1 = new GridScheduler("s0.calculateScores", worker1);

        WorkerGrid worker2 = new WorkerGrid1D(globalSize);
        worker2.setGlobalWork(globalSize, 1, 1);
        worker2.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler2 = new GridScheduler("s1.findMax", worker2);

        WorkerGrid worker3 = new WorkerGrid1D(globalSize);
        worker3.setGlobalWork(globalSize, 1, 1);
        worker3.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler3 = new GridScheduler("s2.calculateExp", worker3);

        WorkerGrid worker4 = new WorkerGrid1D(globalSize);
        worker4.setGlobalWork(globalSize, 1, 1);
        worker4.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler4 = new GridScheduler("s3.normalize", worker4);

        WorkerGrid worker5 = new WorkerGrid1D(globalSize);
        worker5.setGlobalWork(globalSize, 1, 1);
        worker5.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler5 = new GridScheduler("s4.weightedSum", worker5);

        // Create immutable task graphs
        ImmutableTaskGraph itg1 = tg1.snapshot();
        ImmutableTaskGraph itg2 = tg2.snapshot();
        ImmutableTaskGraph itg3 = tg3.snapshot();
        ImmutableTaskGraph itg4 = tg4.snapshot();
        ImmutableTaskGraph itg5 = tg5.snapshot();

        // Execute the pipeline
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg1, itg2, itg3, itg4, itg5)) {
            executionPlan.withGraph(0).withGridScheduler(gridScheduler1).execute();
            executionPlan.withGraph(1).withGridScheduler(gridScheduler2).execute();
            executionPlan.withGraph(2).withGridScheduler(gridScheduler3).execute();
            executionPlan.withGraph(3).withGridScheduler(gridScheduler4).execute();
            executionPlan.withGraph(4).withGridScheduler(gridScheduler5).execute();
        }

        // Print comparison of results for head 0
        System.out.println("Comparison of results for head 0:");
        for (int i = 0; i < Math.min(headSize, 5); i++) {
            System.out.printf("Dimension %d: Sequential=%.6f, Parallel=%.6f, Diff=%.9f%n",
                    i, sequentialOutput.get(i), output.get(i),
                    Math.abs(sequentialOutput.get(i) - output.get(i)));
        }

        // Verify results for all heads
        boolean passed = true;
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < headSize; i++) {
                int idx = h * headSize + i;
                float expected = sequentialOutput.get(idx);
                float actual = output.get(idx);

                // Use relative tolerance for large values, absolute tolerance for small values
                float tolerance = Math.max(1e-5f, Math.abs(expected) * 1e-4f);

                if (Math.abs(expected - actual) > tolerance) {
                    System.out.printf("MISMATCH at head %d, dim %d: Expected %.9f, Got %.9f, Diff %.9f%n",
                            h, i, expected, actual, Math.abs(expected - actual));
                    passed = false;
                }
            }
        }

        if (passed) {
            System.out.println("All values match within tolerance!");
        } else {
            System.out.println("Some values don't match within tolerance!");
        }

        // Final formal assertions
        for (int h = 0; h < numHeads; h++) {
            for (int i = 0; i < headSize; i++) {
                int idx = h * headSize + i;
                assertEquals(String.format("Output mismatch at head %d, dimension %d", h, i),
                        sequentialOutput.get(idx), output.get(idx),
                        Math.max(1e-5f, Math.abs(sequentialOutput.get(idx) * 1e-4f)));
            }
        }
    }
}