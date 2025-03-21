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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Parallel implementation of the FFN layer using KernelContext and worker grids.
 */
public class TestParallelFFNLayer extends TornadoTestBase {

    private static final boolean DEBUG = true;

    /**
     * Matrix-vector multiplication using KernelContext
     */
    public static void matrixVectorMultiply(KernelContext context, FloatArray x, FloatArray output, FloatArray weights, int n, int d) {
        int idx = context.globalIdx;

        if (idx < d) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                if (j < x.getSize() && (idx * n + j) < weights.getSize()) {
                    sum += weights.get(idx * n + j) * x.get(j);
                }
            }

            output.set(idx, sum);
        }
    }

    /**
     * In-place addition using KernelContext
     */
    public static void addInPlace(KernelContext context, FloatArray input, FloatArray output) {
        int idx = context.globalIdx;

        if (idx < Math.min(input.getSize(), output.getSize())) {
            output.set(idx, output.get(idx) + input.get(idx));
        }
    }

    /**
     * SiLU activation function using KernelContext
     */
    public static void siluActivation(KernelContext context, FloatArray input) {
        int idx = context.globalIdx;

        if (idx < input.getSize()) {
            float value = input.get(idx);
            float result = value / (1.0f + TornadoMath.exp(-value));
            input.set(idx, result);
        }
    }

    /**
     * Element-wise multiplication using KernelContext
     */
    public static void elementMultiply(KernelContext context, FloatArray input, FloatArray output) {
        int idx = context.globalIdx;

        if (idx < Math.min(input.getSize(), output.getSize())) {
            output.set(idx, output.get(idx) * input.get(idx));
        }
    }

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
    public static void normalizeAndScale2(KernelContext context, FloatArray output, FloatArray input, FloatArray weights, FloatArray scalingFactorBuffer, int size) {
        int idx = context.globalIdx;

        if (idx < size) {
            float scalingFactor = scalingFactorBuffer.get(0);
            float scaledValue = weights.get(idx) * (scalingFactor * input.get(idx));
            output.set(idx, scaledValue);
        }
    }

    /**
     * Sequential reference implementation of FFN layer
     */
    public static void ffnLayerSequential(FloatArray x, FloatArray wo, FloatArray w1, FloatArray w2, FloatArray w3, FloatArray rmsWeight, float rmsEps, int dim, int hiddenDim) {
        // Create temporary buffers
        FloatArray temp = new FloatArray(dim);
        FloatArray hidden = new FloatArray(hiddenDim);
        FloatArray hidden2 = new FloatArray(hiddenDim);

        // 1. Apply projection from attention output
        for (int i = 0; i < dim; i++) {
            float sum = 0.0f;
            for (int j = 0; j < dim; j++) {
                sum += wo.get(i * dim + j) * x.get(j);
            }
            temp.set(i, sum);
        }

        // 2. Residual connection
        for (int i = 0; i < dim; i++) {
            x.set(i, x.get(i) + temp.get(i));
        }

        // 3. Apply RMSNorm
        float sumSquares = 0.0f;
        for (int i = 0; i < dim; i++) {
            sumSquares += x.get(i) * x.get(i);
        }
        float mean = sumSquares / dim;
        float scalingFactor = 1.0f / (float) Math.sqrt(mean + rmsEps);

        for (int i = 0; i < dim; i++) {
            temp.set(i, rmsWeight.get(i) * (scalingFactor * x.get(i)));
        }

        // 4. Apply first projections (w1 and w3)
        for (int i = 0; i < hiddenDim; i++) {
            float sum1 = 0.0f;
            for (int j = 0; j < dim; j++) {
                sum1 += w1.get(i * dim + j) * temp.get(j);
            }
            hidden.set(i, sum1);

            float sum3 = 0.0f;
            for (int j = 0; j < dim; j++) {
                sum3 += w3.get(i * dim + j) * temp.get(j);
            }
            hidden2.set(i, sum3);
        }

        // 5. Apply SwiGLU activation
        for (int i = 0; i < hiddenDim; i++) {
            float val = hidden.get(i);
            float sigmoid = 1.0f / (1.0f + (float) Math.exp(-val));
            hidden.set(i, val * sigmoid);
        }

        // 6. Element-wise multiply
        for (int i = 0; i < hiddenDim; i++) {
            hidden.set(i, hidden.get(i) * hidden2.get(i));
        }

        // 7. Final projection
        for (int i = 0; i < dim; i++) {
            float sum = 0.0f;
            for (int j = 0; j < hiddenDim; j++) {
                sum += w2.get(i * hiddenDim + j) * hidden.get(j);
            }
            temp.set(i, sum);
        }

        // 8. Final residual
        for (int i = 0; i < dim; i++) {
            x.set(i, x.get(i) + temp.get(i));
        }
    }

    private static GridScheduler getGridScheduler(int dim, int localSize, int hiddenDim) {
        WorkerGrid dimWorker = new WorkerGrid1D(dim);
        dimWorker.setGlobalWork(dim, 1, 1);
        dimWorker.setLocalWork(localSize, 1, 1);

        WorkerGrid hiddenWorker = new WorkerGrid1D(hiddenDim);
        hiddenWorker.setGlobalWork(hiddenDim, 1, 1);
        hiddenWorker.setLocalWork(localSize, 1, 1);

        WorkerGrid singleWorker = new WorkerGrid1D(1);
        singleWorker.setGlobalWork(1, 1, 1);
        singleWorker.setLocalWork(1, 1, 1);

        // Configure grid scheduler with worker grids for each task
        GridScheduler gridScheduler = new GridScheduler();

        // Set up model dimension workers
        gridScheduler.setWorkerGrid("ffn-layer.matmul1", dimWorker);
        gridScheduler.setWorkerGrid("ffn-layer.residual1", dimWorker);
        gridScheduler.setWorkerGrid("ffn-layer.reduce", dimWorker);
        gridScheduler.setWorkerGrid("ffn-layer.ns", dimWorker);
        gridScheduler.setWorkerGrid("ffn-layer.residual2", dimWorker);

        // Set up hidden dimension workers
        gridScheduler.setWorkerGrid("ffn-layer.projection1", hiddenWorker);
        gridScheduler.setWorkerGrid("ffn-layer.projection3", hiddenWorker);
        gridScheduler.setWorkerGrid("ffn-layer.silu", hiddenWorker);
        gridScheduler.setWorkerGrid("ffn-layer.multiply", hiddenWorker);
        gridScheduler.setWorkerGrid("ffn-layer.projection2", dimWorker);

        // Set up single thread worker for final sum
        gridScheduler.setWorkerGrid("ffn-layer.sum", singleWorker);
        return gridScheduler;
    }

    @Test
    public void testParallelFFNLayer() throws TornadoExecutionPlanException {
        // Test with small dimensions
        final int dim = 16;
        final int hiddenDim = 32;
        final float rmsNormEps = 1e-5f;
        final int localSize = 8;

        // Input/output array
        FloatArray x = new FloatArray(dim);

        // Weights
        FloatArray wo = new FloatArray(dim * dim);
        FloatArray w1 = new FloatArray(hiddenDim * dim);
        FloatArray w2 = new FloatArray(dim * hiddenDim);
        FloatArray w3 = new FloatArray(hiddenDim * dim);
        FloatArray rmsWeight = new FloatArray(dim);

        // Intermediate buffers
        FloatArray temp = new FloatArray(dim);
        FloatArray hidden = new FloatArray(hiddenDim);
        FloatArray hidden2 = new FloatArray(hiddenDim);

        // RMSNorm reduction buffer
        int reduceSize = (dim + localSize - 1) / localSize; // ceiling division
        FloatArray reduce = new FloatArray(reduceSize);

        // Initialize with random data
        Random random = new Random(42);
        for (int i = 0; i < dim; i++) {
            x.set(i, random.nextFloat() * 2 - 1);
            temp.set(i, 0.0f);
            rmsWeight.set(i, random.nextFloat() + 0.5f);

            for (int j = 0; j < dim; j++) {
                wo.set(i * dim + j, (random.nextFloat() * 0.4f - 0.2f) / (float) Math.sqrt(dim));
            }

            for (int j = 0; j < hiddenDim; j++) {
                w2.set(i * hiddenDim + j, (random.nextFloat() * 0.4f - 0.2f) / (float) Math.sqrt(hiddenDim));
            }
        }

        for (int i = 0; i < hiddenDim; i++) {
            hidden.set(i, 0.0f);
            hidden2.set(i, 0.0f);

            for (int j = 0; j < dim; j++) {
                w1.set(i * dim + j, (random.nextFloat() * 0.4f - 0.2f) / (float) Math.sqrt(dim));
                w3.set(i * dim + j, (random.nextFloat() * 0.4f - 0.2f) / (float) Math.sqrt(dim));
            }
        }

        // Copy for sequential reference
        FloatArray xSeq = new FloatArray(dim);
        for (int i = 0; i < dim; i++) {
            xSeq.set(i, x.get(i));
        }

        // Run sequential reference implementation
        ffnLayerSequential(xSeq, wo, w1, w2, w3, rmsWeight, rmsNormEps, dim, hiddenDim);

        // Create kernel context
        KernelContext context = new KernelContext();

        // @formatter:off
        // Create unified task graph with proper parallel implementation
        TaskGraph taskGraph = new TaskGraph("ffn-layer")
            // Transfer all input arrays to device
            .transferToDevice(DataTransferMode.FIRST_EXECUTION, x, temp, hidden, hidden2, wo, w1, w2, w3, rmsWeight, reduce)

            // Step 1: Matrix multiplication with attention output and residual
            .task("matmul1", TestParallelFFNLayer::matrixVectorMultiply, context, x, temp, wo, dim, dim)
            .task("residual1", TestParallelFFNLayer::addInPlace, context, temp, x)

            // Step 2: RMSNorm sequence
            .task("reduce", TestParallelFFNLayer::reduceSquareSums, context, x, reduce, localSize)
            .task("sum", TestParallelFFNLayer::finalSum, context, reduce, dim, rmsNormEps)
            .task("ns", TestParallelFFNLayer::normalizeAndScale2, context, temp, x, rmsWeight, reduce, dim)

            // Step 3: Parallel projections with W1 and W3
            .task("projection1", TestParallelFFNLayer::matrixVectorMultiply, context, temp, hidden, w1, dim, hiddenDim)
            .task("projection3", TestParallelFFNLayer::matrixVectorMultiply, context, temp, hidden2, w3, dim, hiddenDim)

            // Step 4: SiLU activation and element-wise multiplication
            .task("silu", TestParallelFFNLayer::siluActivation, context, hidden)
            .task("multiply", TestParallelFFNLayer::elementMultiply, context, hidden2, hidden)

            // Step 5: Final projection and residual
            .task("projection2", TestParallelFFNLayer::matrixVectorMultiply, context, hidden, temp, w2, hiddenDim, dim)
            .task("residual2", TestParallelFFNLayer::addInPlace, context, temp, x)

            // Transfer result back to host
            .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
        // @formatter:on

        // Create worker grids for different task dimensions
        GridScheduler gridScheduler = getGridScheduler(dim, localSize, hiddenDim);

        // Create execution plan
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Execute the task graph
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Verify results
        if (DEBUG) {
            System.out.println("\nComparing results:");
            boolean allMatch = true;
            for (int i = 0; i < dim; i++) {
                float expected = xSeq.get(i);
                float actual = x.get(i);
                float relError = Math.abs(expected - actual) / (Math.abs(expected) + 1e-6f);

                System.out.printf("Element %2d: Expected=%.7f, Actual=%.7f, RelError=%.7f%n", i, expected, actual, relError);

                if (relError > 0.01f) {
                    allMatch = false;
                }
            }

            if (allMatch) {
                System.out.println("All values match within tolerance!");
            } else {
                System.out.println("Some values don't match within tolerance.");
            }
        }

        // JUnit assertions
        for (int i = 0; i < dim; i++) {
            assertEquals("Output mismatch at index " + i, xSeq.get(i), x.get(i), Math.abs(xSeq.get(i) * 0.01f) + 1e-5f);
        }
    }
}
