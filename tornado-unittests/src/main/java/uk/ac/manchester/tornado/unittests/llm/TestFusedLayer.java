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
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test for the fused layer with RMSNorm and RoPE rotation in LLM.
 */
public class TestFusedLayer extends TornadoTestBase {
    private static final boolean DEBUG = true;

    /**
     * Compute sum of squares for RMSNorm
     */
    public static void reduceSquareSums(KernelContext context, FloatArray x, FloatArray reduce, int localSize) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;

        if (globalIdx >= x.getSize())
            return;

        // Allocate local memory for reduction
        float[] localA = context.allocateFloatLocalArray(localSize);
        // Compute square of value
        localA[localIdx] = x.get(globalIdx) * x.get(globalIdx);

        // Parallel reduction in local memory
        for (int stride = localGroupSize / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }

        // Write result to output array
        if (localIdx == 0) {
            reduce.set(groupID, localA[0]);
        }
    }

    /**
     * Compute final sum and scaling factor for RMSNorm
     */
    public static void finalSum(KernelContext context, FloatArray reduce, int size, float eps) {
        int globalIdx = context.globalIdx;

        // Only first thread computes the final sum
        if (globalIdx == 0) {
            float sum = 0.0f;
            for (int i = 0; i < reduce.getSize(); i++) {
                sum += reduce.get(i);
            }

            // Compute normalization scaling factor
            float mean = sum / (float) size;
            float scalingFactor = 1.0f / TornadoMath.sqrt(mean + eps);
            reduce.set(0, scalingFactor);
        }
    }

    /**
     * Apply normalization and scaling
     */
    public static void normalizeAndScale(KernelContext context, FloatArray output, FloatArray input, FloatArray weights, FloatArray scalingFactorBuffer, int size) {
        int idx = context.globalIdx;

        if (idx < size) {
            float scalingFactor = scalingFactorBuffer.get(0);
            float scaledValue = weights.get(idx) * (scalingFactor * input.get(idx));
            output.set(idx, scaledValue);
        }
    }

    /**
     * Matrix-vector multiplication for general use
     */
    public static void matrixVectorSimple(KernelContext context, FloatArray x, FloatArray output, FloatArray weights, int n, int d) {
        int idx = context.globalIdx;

        if (idx < output.getSize()) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += weights.get(idx * n + j) * x.get(j);
            }
            output.set(idx, sum);
        }
    }

    /**
     * Matrix-vector multiplication specialized for K
     */
    public static void matrixVectorK(KernelContext context, FloatArray x, FloatArray output, FloatArray weights, int dim) {
        int i = context.globalIdx;

        if (i < output.getSize()) {
            float sum = 0.0f;
            for (int j = 0; j < dim; j++) {
                float weight = weights.get(i * dim + j);
                float input = x.get(j);
                sum += weight * input;
            }
            output.set(i, sum);
        }
    }

    /**
     * Matrix-vector multiplication specialized for V
     */
    public static void matrixVectorV(KernelContext context, FloatArray x, FloatArray output, FloatArray weights, int dim) {
        int i = context.globalIdx;

        if (i < output.getSize()) {
            float sum = 0.0f;
            for (int j = 0; j < dim; j++) {
                float weight = weights.get(i * dim + j);
                float input = x.get(j);
                sum += weight * input;
            }
            output.set(i, sum);
        }
    }

    /**
     * RoPE (Rotary Position Embeddings) rotation
     */
    public static void ropeRotation(KernelContext context, int pos, FloatArray sq, FloatArray sk, int kv_dim, int head_size) {
        int i = context.globalIdx * 2;

        // Ensure we're within bounds and handle the even indices properly
        if (i < sq.getSize() && i % 2 == 0) {
            int head_dim = i % head_size;
            float freq = 1.0f / TornadoMath.pow(10000.0f, head_dim / (float) head_size);
            float val = pos * freq;
            float fcr = TornadoMath.cos(val);
            float fci = TornadoMath.sin(val);

            int rotn = i < kv_dim ? 2 : 1; // how many vectors? 2 = q & k, 1 = q only

            // Rotate query vector
            float v0q = sq.get(i);
            float v1q = sq.get(i + 1);
            sq.set(i, v0q * fcr - v1q * fci);
            sq.set(i + 1, v0q * fci + v1q * fcr);

            // Rotate key vector if needed
            if (rotn > 1 && i < sk.getSize()) {
                float v0k = sk.get(i);
                float v1k = sk.get(i + 1);
                sk.set(i, v0k * fcr - v1k * fci);
                sk.set(i + 1, v0k * fci + v1k * fcr);
            }
        }
    }

    /**
     * Sequential implementation of RMSNorm
     */
    public static void rmsNormSequential(FloatArray input, FloatArray weights, FloatArray output, float eps) {
        // Calculate sum of squares
        float sumSquares = 0.0f;
        for (int i = 0; i < input.getSize(); i++) {
            sumSquares += input.get(i) * input.get(i);
        }

        // Calculate scaling factor
        float mean = sumSquares / input.getSize();
        float scale = 1.0f / (float) Math.sqrt(mean + eps);

        // Apply normalization and scaling
        for (int i = 0; i < input.getSize(); i++) {
            output.set(i, weights.get(i) * (scale * input.get(i)));
        }
    }

    /**
     * Sequential RoPE rotation implementation
     */
    public static void ropeRotationSequential(int pos, FloatArray q, FloatArray k, int dim, int kvDim, int headSize) {
        // RoPE relative positional encoding: complex-valued rotate q and k in each head
        for (int i = 0; i < dim; i += 2) {
            int head_dim = i % headSize;
            // Calculate frequency components
            float freq = 1.0f / (float) Math.pow(10000.0f, head_dim / (float) headSize);
            float val = pos * freq;
            float fcr = (float) Math.cos(val);
            float fci = (float) Math.sin(val);

            int rotn = i < kvDim ? 2 : 1; // how many vectors? 2 = q & k, 1 = q only

            // Rotate query vector
            float v0q = q.get(i);
            float v1q = q.get(i + 1);
            q.set(i, v0q * fcr - v1q * fci);
            q.set(i + 1, v0q * fci + v1q * fcr);

            // Rotate key vector if needed
            if (rotn > 1) {
                float v0k = k.get(i);
                float v1k = k.get(i + 1);
                k.set(i, v0k * fcr - v1k * fci);
                k.set(i + 1, v0k * fci + v1k * fcr);
            }
        }
    }

    /**
     * Initialize model weights and inputs with deterministic random values
     */
    private void initializeWeights(Random random, FloatArray xFloat, FloatArray rmsWeights, FloatArray wq, FloatArray wk, FloatArray wv, int dim, int kvDim) {
        // Initialize input and RMS weights
        for (int i = 0; i < dim; i++) {
            xFloat.set(i, random.nextFloat() * 2 - 1);
            rmsWeights.set(i, random.nextFloat() + 0.5f);
        }

        // Initialize Query weights
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                wq.set(i * dim + j, (random.nextFloat() * 0.2f - 0.1f) / (float) Math.sqrt(dim));
            }
        }

        // Initialize Key and Value weights with separate loops for consistency
        for (int i = 0; i < kvDim; i++) {
            for (int j = 0; j < dim; j++) {
                wk.set(i * dim + j, (random.nextFloat() * 0.2f - 0.1f) / (float) Math.sqrt(dim));
                wv.set(i * dim + j, (random.nextFloat() * 0.2f - 0.1f) / (float) Math.sqrt(dim));
            }
        }
    }

    /**
     * Run sequential reference implementation
     */
    private void runSequentialImplementation(FloatArray xFloatSeq, FloatArray rmsWeights, FloatArray xbSeq, FloatArray qSeq, FloatArray kSeq, FloatArray vSeq, FloatArray wq, FloatArray wk,
            FloatArray wv, int dim, int kvDim, int headSize, int position, float rmsNormEps) {
        System.out.println("Running sequential reference implementation...");

        // 1. RMSNorm
        rmsNormSequential(xFloatSeq, rmsWeights, xbSeq, rmsNormEps);

        // 2. Matrix multiplications for Q, K, V projections
        for (int i = 0; i < dim; i++) {
            float sumQ = 0.0f;
            for (int j = 0; j < dim; j++) {
                sumQ += wq.get(i * dim + j) * xbSeq.get(j);
            }
            qSeq.set(i, sumQ);
        }

        for (int i = 0; i < kvDim; i++) {
            float sumK = 0.0f;
            float sumV = 0.0f;
            for (int j = 0; j < dim; j++) {
                sumK += wk.get(i * dim + j) * xbSeq.get(j);
                sumV += wv.get(i * dim + j) * xbSeq.get(j);
            }
            kSeq.set(i, sumK);
            vSeq.set(i, sumV);
        }

        // 3. RoPE rotation
        ropeRotationSequential(position, qSeq, kSeq, dim, kvDim, headSize);
    }

    /**
     * Verify results between sequential and parallel implementations
     */
    private void verifyResults(FloatArray qSeq, FloatArray kSeq, FloatArray vSeq, FloatArray q, FloatArray k, FloatArray v, int dim, int kvDim) {
        if (DEBUG) {
            System.out.println("\nComparing query vectors:");
            boolean qMatch = true;
            for (int i = 0; i < dim; i++) {
                float expected = qSeq.get(i);
                float actual = q.get(i);
                float relError = Math.abs(expected - actual) / (Math.abs(expected) + 1e-6f);

                if (i < 10) { // Print first few elements
                    System.out.printf("Q[%2d]: Expected=%.7f, Actual=%.7f, RelError=%.7f%n", i, expected, actual, relError);
                }

                if (relError > 0.01f) {
                    qMatch = false;
                }
            }

            System.out.println("\nComparing key vectors:");
            boolean kMatch = true;
            for (int i = 0; i < kvDim; i++) {
                float expected = kSeq.get(i);
                float actual = k.get(i);
                float relError = Math.abs(expected - actual) / (Math.abs(expected) + 1e-6f);

                if (i < 10) { // Print first few elements
                    System.out.printf("K[%2d]: Expected=%.7f, Actual=%.7f, RelError=%.7f%n", i, expected, actual, relError);
                }

                if (relError > 0.01f) {
                    kMatch = false;
                }
            }

            System.out.println("\nComparing value vectors:");
            boolean vMatch = true;
            for (int i = 0; i < kvDim; i++) {
                float expected = vSeq.get(i);
                float actual = v.get(i);
                float relError = Math.abs(expected - actual) / (Math.abs(expected) + 1e-6f);

                if (i < 10) { // Print first few elements
                    System.out.printf("V[%2d]: Expected=%.7f, Actual=%.7f, RelError=%.7f%n", i, expected, actual, relError);
                }

                if (relError > 0.01f) {
                    vMatch = false;
                }
            }

            if (qMatch && kMatch && vMatch) {
                System.out.println("\nAll values match within tolerance!");
            } else {
                System.out.println("\nSome values don't match within tolerance:");
                System.out.println("- Query vectors match: " + qMatch);
                System.out.println("- Key vectors match: " + kMatch);
                System.out.println("- Value vectors match: " + vMatch);
            }
        }

        // JUnit assertions
        for (int i = 0; i < dim; i++) {
            assertEquals("Query vector mismatch at index " + i, qSeq.get(i), q.get(i), Math.abs(qSeq.get(i) * 0.01f) + 1e-5f);
        }

        for (int i = 0; i < kvDim; i++) {
            assertEquals("Key vector mismatch at index " + i, kSeq.get(i), k.get(i), Math.abs(kSeq.get(i) * 0.01f) + 1e-5f);
            assertEquals("Value vector mismatch at index " + i, vSeq.get(i), v.get(i), Math.abs(vSeq.get(i) * 0.01f) + 1e-5f);
        }
    }

    @Test
    public void testFusedLayerWithRoPE() throws TornadoExecutionPlanException {
        // Configuration parameters
        final int dim = 64;           // Model dimension
        final int headSize = 16;      // Size of each attention head
        final int numHeads = 4;       // Number of attention heads
        final int numKVHeads = 2;     // Number of key-value heads (for multi-query attention)
        final int kvDim = (dim * numKVHeads) / numHeads; // Key-value dimension
        final int position = 10;      // Current position in sequence
        final float rmsNormEps = 1e-5f; // RMSNorm epsilon
        final int localSize = 16;     // Worker threads per workgroup

        System.out.println("Setting up test with dim=" + dim + ", headSize=" + headSize + ", numHeads=" + numHeads + ", numKVHeads=" + numKVHeads);

        // Create arrays
        FloatArray xFloat = new FloatArray(dim);      // Input state
        FloatArray xb = new FloatArray(dim);          // Normalized state
        FloatArray q = new FloatArray(dim);           // Query vectors
        FloatArray k = new FloatArray(kvDim);         // Key vectors
        FloatArray v = new FloatArray(kvDim);         // Value vectors
        FloatArray att = new FloatArray(kvDim);       // Attention scores
        FloatArray keyCache = new FloatArray(10 * kvDim); // Key cache (10 positions)
        FloatArray valueCache = new FloatArray(10 * kvDim); // Value cache (10 positions)
        ByteArray wclsBytes = new ByteArray(100);     // Placeholder for classifier weights
        FloatArray rmsWeights = new FloatArray(dim);  // RMSNorm weights

        // Weight matrices
        FloatArray wq = new FloatArray(dim * dim);    // Query projection
        FloatArray wk = new FloatArray(kvDim * dim);  // Key projection
        FloatArray wv = new FloatArray(kvDim * dim);  // Value projection

        // RMSNorm reduction buffer
        FloatArray reduce = new FloatArray(dim / localSize);

        // Initialize with random data
        Random random = new Random(42);
        initializeWeights(random, xFloat, rmsWeights, wq, wk, wv, dim, kvDim);

        // Create copies for reference implementation
        FloatArray xFloatSeq = new FloatArray(dim);
        FloatArray xbSeq = new FloatArray(dim);
        FloatArray qSeq = new FloatArray(dim);
        FloatArray kSeq = new FloatArray(kvDim);
        FloatArray vSeq = new FloatArray(kvDim);

        // Copy values for sequential processing
        for (int i = 0; i < dim; i++) {
            xFloatSeq.set(i, xFloat.get(i));
        }

        // Run sequential reference implementation
        runSequentialImplementation(xFloatSeq, rmsWeights, xbSeq, qSeq, kSeq, vSeq, wq, wk, wv, dim, kvDim, headSize, position, rmsNormEps);

        // Create kernel context
        KernelContext context = new KernelContext();

        // Set up task graph for the fused layer
        System.out.println("Setting up fused layer task graph...");

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("fused")
            // Transfer all input arrays to device
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, xFloat, xb, q, k, v, att, keyCache, valueCache, wclsBytes, rmsWeights, wq, wk, wv, reduce)
            // RMSNorm sequence
            .task("reduce", TestFusedLayer::reduceSquareSums, context, xFloat, reduce, localSize)
            .task("sum", TestFusedLayer::finalSum, context, reduce, dim, rmsNormEps)
            .task("ns", TestFusedLayer::normalizeAndScale, context, xb, xFloat, rmsWeights, reduce, dim)

            // Matrix multiplications for projections
            .task("matmul1", TestFusedLayer::matrixVectorSimple, context, xb, q, wq, dim, dim)
            .task("matmul2", TestFusedLayer::matrixVectorSimple, context, xb, k, wk, dim, dim)
            .task("matmul3", TestFusedLayer::matrixVectorSimple, context, xb, v, wv, dim, dim)

            // RoPE rotation
            .task("rope", TestFusedLayer::ropeRotation, context, position, q, k, kvDim, headSize)

            // Transfer results back to host
            .transferToHost(DataTransferMode.EVERY_EXECUTION, q, k, v, xFloat);

        // @formatter:on

        // Create worker grids
        WorkerGrid dimWorker = new WorkerGrid1D(dim);
        dimWorker.setGlobalWork(dim, 1, 1);
        dimWorker.setLocalWork(localSize, 1, 1);

        WorkerGrid singleWorker = new WorkerGrid1D(1);
        singleWorker.setGlobalWork(1, 1, 1);
        singleWorker.setLocalWork(1, 1, 1);

        // Configure grid scheduler
        GridScheduler gridScheduler = new GridScheduler();

        // Set up worker grids for each task
        gridScheduler.setWorkerGrid("fused.reduce", dimWorker);
        gridScheduler.setWorkerGrid("fused.sum", singleWorker);
        gridScheduler.setWorkerGrid("fused.ns", dimWorker);

        // Set up projection workers with correct dimensions
        gridScheduler.setWorkerGrid("fused.matmul1", dimWorker);    // For Query (dim)

        // Use specific worker grids for K and V that match their actual sizes
        WorkerGrid kvDimWorker = new WorkerGrid1D(kvDim);
        kvDimWorker.setGlobalWork(kvDim, 1, 1);
        kvDimWorker.setLocalWork(Math.min(kvDim, localSize), 1, 1);
        gridScheduler.setWorkerGrid("fused.matmul2", kvDimWorker);  // For Key (kvDim)
        gridScheduler.setWorkerGrid("fused.matmul3", kvDimWorker);  // For Value (kvDim)

        // Set up RoPE worker with the right dimension
        WorkerGrid ropeWorker = new WorkerGrid1D(dim / 2);
        ropeWorker.setGlobalWork(dim / 2, 1, 1);
        ropeWorker.setLocalWork(Math.min(dim / 2, localSize), 1, 1);
        gridScheduler.setWorkerGrid("fused.rope", ropeWorker);

        // Create execution plan
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Execute the fused layer
        System.out.println("Executing fused layer task graph...");
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
            System.out.println("Execution completed successfully");
        } catch (Exception e) {
            System.out.println("Exception during execution: " + e.getMessage());
            e.printStackTrace();
        }

        // Verify results
        verifyResults(qSeq, kSeq, vSeq, q, k, v, dim, kvDim);
    }
}