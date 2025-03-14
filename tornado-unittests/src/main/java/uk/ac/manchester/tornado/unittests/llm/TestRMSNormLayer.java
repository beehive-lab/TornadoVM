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

import java.util.stream.IntStream;

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
 * Test to validate the functionality of benchmark compute layers or components of a Large Language Model (LLM) transformer,
 * focusing on operations like RMS Normalization (RNSNorm) and Matrix-Vector Multiplication (MatMul).
 *
 * The test verifies that the fused kernel approach, which combines RMSNorm and MatMul in a single execution flow,
 * produces correct results by comparing the output of a parallel (fused) execution with a sequential (reference) implementation.
 *
 * Specifically, the test checks:
 * 1. **RMS Normalization (RNSNorm)**:
 * - RMSNorm is computed by normalizing input values using the inverse square root of the sum of squares, scaled by a weight factor.
 * - The test compares the result of this normalization from the parallel execution against a sequential computation.
 *
 * 2. **Matrix-Vector Multiplication (MatMul)**:
 * - The test also ensures the matrix-vector multiplication operation (MatMul) works correctly when combined with the RMSNorm step, ensuring that the fusion of these operations does not compromise
 * their correctness.
 *
 * The execution is tested in two forms:
 * - **Sequential Execution**: Using traditional CPU-based loops for reference results.
 * - **Fused Parallel Execution**: Leveraging TornadoVM's parallel computing capabilities to execute the same operations using fused kernels.
 *
 * For both the RMSNorm and MatMul operations, the test compares the result from the parallel execution with the sequential reference to ensure that the fused parallel approach produces identical
 * outputs, within an acceptable error tolerance.
 *
 * <p>
 * How to run the tests?
 * </p>
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.llm.TestRMSNormLayer
 * </code>
 */

public class TestRMSNormLayer extends TornadoTestBase {

    public static FloatArray matmul(FloatArray weights, FloatArray x, FloatArray out, int dim0, int dim1) {
        IntStream.range(0, dim0).forEach(i -> {
            float result = 0f;
            int thisOffset = i * dim1;
            for (int j = 0; j < dim1; j++) {
                result += weights.get(thisOffset + j) * x.get(j);
            }
            out.set(i, result);
        });
        return out;
    }

    public static void matrixVector(KernelContext context, FloatArray a, FloatArray b, FloatArray c, int dim0, int dim1) {
        int idx = context.globalIdx;
        float sum = 0.0f;
        for (int k = 0; k < dim0; k++) {
            sum += a.get((idx * dim1) + k) * b.get(k);
        }
        c.set(idx, sum);
    }

    public static void reduceSquareSums(KernelContext context, FloatArray a, FloatArray reduce) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        float[] localA = context.allocateFloatLocalArray(256);
        localA[localIdx] = a.get(globalIdx) * a.get(globalIdx);
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            reduce.set(groupID, localA[0]);
        }
    }

    private static void finalSum(KernelContext context, FloatArray reduce, int size, float eps) {
        int globalIdx = context.globalIdx;

        float sum = 0.0f;
        if (globalIdx == 0) {
            for (int i = 0; i < size; i++) {
                sum += reduce.get(i);
            }
        }

        float ss = sum / (float) size;
        ss += eps;
        ss = 1.0f / TornadoMath.sqrt(ss);
        reduce.set(0, ss);
    }

    public static void normalizeAndScale(KernelContext context, FloatArray out, FloatArray input, FloatArray weight, FloatArray scalingFactorBuffer, int size, float eps) {

        int globalIdx = context.globalIdx;

        if (globalIdx < size) {
            float scaledValue = weight.get(globalIdx) * (scalingFactorBuffer.get(0) * input.get(globalIdx));
            out.set(globalIdx, scaledValue);
        }
    }

    public FloatArray rmsnorm(FloatArray x, FloatArray weight, int size, float rmsNormEps) {
        FloatArray out = new FloatArray(size);
        // Step 1: Calculate sum of squares
        float ss = 0.0f;
        for (int i = 0; i < size; i++) {
            ss += x.get(i) * x.get(i);  // Sum of squares
        }

        ss /= size;  // Normalize by the size
        ss += rmsNormEps; // Add epsilon
        ss = (float) (1.0 / Math.sqrt(ss)); // Inverse square root

        // Step 2: Normalize and scale
        for (int i = 0; i < size; i++) {
            float normalizedValue = ss * x.get(i);
            out.set(i, weight.get(i) * normalizedValue);
        }
        return out;
    }

    @Test
    public void testRNSNorm() throws TornadoExecutionPlanException {
        final int size = 2048;
        final int localSize = 256;
        float eps = 1e-5f;

        FloatArray x = new FloatArray(size);
        FloatArray rnsOutput = new FloatArray(size);
        FloatArray weight = new FloatArray(size);
        FloatArray sequentialOutput;

        FloatArray reduce = new FloatArray(size / localSize);

        for (int i = 0; i < size; i++) {
            x.set(i, (float) (i + 1));
            weight.set(i, 0.5f);
        }

        sequentialOutput = rmsnorm(x, weight, size, eps);

        WorkerGrid worker = new WorkerGrid1D(size);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        gridScheduler.setWorkerGrid("s0.t1", new WorkerGrid1D(1));
        gridScheduler.setWorkerGrid("s0.t2", worker);

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, size, eps, x, reduce) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localSize, weight) //
                .task("t0", TestRMSNormLayer::reduceSquareSums, context, x, reduce) //
                .task("t1", TestRMSNormLayer::finalSum, context, reduce, size, eps) //
                .task("t2", TestRMSNormLayer::normalizeAndScale, context, rnsOutput, x, weight, reduce, size, eps) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, rnsOutput); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < rnsOutput.getSize(); i++) {
            float expected = sequentialOutput.get(i);  // Expected value from the sequential output
            float actual = rnsOutput.get(i);               // Actual value from the RNS output

            // Perform the comparison with assertion
            assertEquals("Mismatch at index " + i, expected, actual, 1e-6f); // Allow some tolerance
        }

    }

    @Test
    public void testRNSNormFusedWithMatMul() throws TornadoExecutionPlanException {
        final int size = 2048;
        final int localSize = 256;
        float eps = 1e-5f;

        FloatArray x = new FloatArray(size);
        FloatArray state = new FloatArray(size);
        FloatArray rnsOutput = new FloatArray(size);
        FloatArray sequentialOutput;

        FloatArray weights = new FloatArray(size * size);
        FloatArray outputLogits = new FloatArray(size);
        FloatArray outputSeqLogits = new FloatArray(size);

        FloatArray reduce = new FloatArray(size / localSize);

        outputLogits.init(0f);
        outputSeqLogits.init(0f);

        for (int i = 0; i < size; i++) {
            x.set(i, (float) (i + 1));
            state.set(i, 0.5f + i);
        }

        for (int i = 0; i < size * size; i++) {
            weights.set(i, 0.8f + i);
        }

        sequentialOutput = rmsnorm(x, weights, size, eps);
        outputSeqLogits = matmul(weights, sequentialOutput, outputSeqLogits, size, size);

        KernelContext context = new KernelContext();
        TaskGraph taskGraph = new TaskGraph("fused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, size, eps, x, reduce, weights) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, localSize, state) //
                .task("reduce", TestRMSNormLayer::reduceSquareSums, context, x, reduce) //
                .task("sum", TestRMSNormLayer::finalSum, context, reduce, size, eps) //
                .task("ns", TestRMSNormLayer::normalizeAndScale, context, rnsOutput, x, state, reduce, size, eps) //
                .task("mv", TestRMSNormLayer::matrixVector, context, weights, rnsOutput, outputLogits, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputLogits); //

        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("fused.reduce", worker);
        gridScheduler.setWorkerGrid("fused.sum", new WorkerGrid1D(1));
        gridScheduler.setWorkerGrid("fused.ns", worker);
        gridScheduler.setWorkerGrid("fused.mv", worker);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < outputSeqLogits.getSize(); i++) {
            float expected = outputSeqLogits.get(i);           // Expected value from the sequential output
            float actual = outputLogits.get(i);               // Actual value from the RNS output
        }

    }

}
