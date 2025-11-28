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
package uk.ac.manchester.tornado.unittests.gpullama;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Unit tests for individual transformer kernels with full numerical verification.
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V org.beehive.gpullama3.tornadovm.kernels.TestTransformerKernelsUnit
 * </code>
 */
public class TestTransformerKernelsUnit extends TornadoTestBase {

    private static final float EPSILON_FP32 = 1e-4f;
    private static final float EPSILON_FP16 = 0.05f;  // FP16 has lower precision
    private static final float EPSILON_ACCUMULATED = 0.1f;  // For operations with accumulated error
    private static final float RMS_NORM_EPS = 1e-5f;
    private static final int LOCAL_SIZE = 64;
    private static final int LOCAL_SIZE_RMS = 256;

    private Random random;

    @Before
    public void setUp() {
        random = new Random(42);
    }

    // ==================== Sequential Reference Implementations ====================

    /**
     * Sequential RMS normalization - Phase 1: compute normalization factor
     */
    private float computeRmsNormFactorSequential(FloatArray x, float eps) {
        float ss = 0.0f;
        for (int i = 0; i < x.getSize(); i++) {
            ss += x.get(i) * x.get(i);
        }
        ss /= x.getSize();
        ss += eps;
        return 1.0f / (float) Math.sqrt(ss);
    }

    /**
     * Sequential RMS normalization - Phase 2: apply normalization
     */
    private void applyRmsNormSequential(FloatArray output, FloatArray x, FloatArray weights, float normFactor) {
        for (int i = 0; i < x.getSize(); i++) {
            output.set(i, weights.get(i) * (normFactor * x.get(i)));
        }
    }

    /**
     * Sequential matrix-vector multiplication (FP32 weights)
     */
    private void matrixVectorSequentialFP32(FloatArray output, FloatArray weights, FloatArray x, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) {
                sum += weights.get(i * cols + j) * x.get(j);
            }
            output.set(i, sum);
        }
    }

    /**
     * Sequential matrix-vector multiplication (FP16 weights)
     */
    private void matrixVectorSequentialFP16(FloatArray output, HalfFloatArray weights, FloatArray x, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) {
                sum += weights.get(i * cols + j).getFloat32() * x.get(j);
            }
            output.set(i, sum);
        }
    }

    /**
     * Sequential matrix-vector with residual addition (FP16 weights)
     */
    private void matrixVectorWithResidualSequential(FloatArray output, HalfFloatArray weights, FloatArray x, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) {
                sum += weights.get(i * cols + j).getFloat32() * x.get(j);
            }
            output.set(i, output.get(i) + sum);
        }
    }

    /**
     * Sequential RoPE rotation
     */
    private void ropeRotationSequential(FloatArray sq, FloatArray sk, int pos, int kvDim, int headSize) {
        int numPairs = sq.getSize() / 2;
        for (int i = 0; i < numPairs; i++) {
            int idx = i * 2;
            int headDim = idx % headSize;
            float freq = 1.0f / (float) Math.pow(50000.0f, headDim / (float) headSize);
            float val = pos * freq;
            float fcr = (float) Math.cos(val);
            float fci = (float) Math.sin(val);

            // Rotate query
            float v0q = sq.get(idx);
            float v1q = sq.get(idx + 1);
            sq.set(idx, v0q * fcr - v1q * fci);
            sq.set(idx + 1, v0q * fci + v1q * fcr);

            // Rotate key if within kvDim
            if (idx < kvDim && idx + 1 < sk.getSize()) {
                float v0k = sk.get(idx);
                float v1k = sk.get(idx + 1);
                sk.set(idx, v0k * fcr - v1k * fci);
                sk.set(idx + 1, v0k * fci + v1k * fcr);
            }
        }
    }

    /**
     * Sequential SiLU activation
     */
    private float siluSequential(float x) {
        return x * (1.0f / (1.0f + (float) Math.exp(-x)));
    }

    /**
     * Sequential fused FFN with SiLU and GLU
     */
    private void fusedFFNSequential(FloatArray output, FloatArray x, HalfFloatArray w1, HalfFloatArray w3, int inputDim, int hiddenDim) {
        for (int i = 0; i < hiddenDim; i++) {
            float sum1 = 0.0f;
            float sum3 = 0.0f;
            for (int j = 0; j < inputDim; j++) {
                sum1 += w1.get(i * inputDim + j).getFloat32() * x.get(j);
                sum3 += w3.get(i * inputDim + j).getFloat32() * x.get(j);
            }
            float silu = siluSequential(sum1);
            output.set(i, silu * sum3);
        }
    }

    /**
     * Sequential copy to KV cache
     */
    private void copyToCacheSequential(FloatArray keyCache, FloatArray key, FloatArray valueCache, FloatArray value, int position, int kvDim, int layer, int contextLength) {
        int loff = layer * contextLength * kvDim;
        int destOffset = loff + position * kvDim;
        for (int i = 0; i < key.getSize(); i++) {
            keyCache.set(destOffset + i, key.get(i));
            valueCache.set(destOffset + i, value.get(i));
        }
    }

    /**
     * Sequential element-wise addition
     */
    private void addInPlaceSequential(FloatArray a, FloatArray b) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + b.get(i));
        }
    }

    /**
     * Sequential split gate/up with SiLU
     */
    private void splitGateUpAndSiLUSequential(FloatArray hb, FloatArray hbG, FloatArray hbU, int hiddenDim) {
        for (int i = 0; i < hiddenDim; i++) {
            float gateVal = hb.get(i);
            float upVal = hb.get(hiddenDim + i);
            float siluGate = siluSequential(gateVal);
            hbG.set(i, siluGate);
            hbU.set(i, siluGate * upVal);
        }
    }

    /**
     * Sequential attention for a single head
     */
    private void processHeadSequential(FloatArray q, FloatArray keyCache, FloatArray valueCache, FloatArray xb, int h, int headSize, int kvDim, int kvMul, int loff, int pos) {
        int kvHeadIdx = h / kvMul;

        // Compute attention scores
        float[] attScores = new float[pos + 1];
        for (int t = 0; t <= pos; t++) {
            int keyOffset = loff + t * kvDim + kvHeadIdx * headSize;
            float score = 0.0f;
            for (int i = 0; i < headSize; i++) {
                score += q.get(h * headSize + i) * keyCache.get(keyOffset + i);
            }
            attScores[t] = score / (float) Math.sqrt(headSize);
        }

        // Softmax
        float maxScore = attScores[0];
        for (int t = 1; t <= pos; t++) {
            if (attScores[t] > maxScore)
                maxScore = attScores[t];
        }

        float sumExp = 0.0f;
        for (int t = 0; t <= pos; t++) {
            attScores[t] = (float) Math.exp(attScores[t] - maxScore);
            sumExp += attScores[t];
        }

        for (int t = 0; t <= pos; t++) {
            attScores[t] /= sumExp;
        }

        // Weighted sum of values
        for (int i = 0; i < headSize; i++) {
            float weightedSum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                int valueOffset = loff + t * kvDim + kvHeadIdx * headSize;
                weightedSum += attScores[t] * valueCache.get(valueOffset + i);
            }
            xb.set(h * headSize + i, weightedSum);
        }
    }

    /**
     * Sequential multi-head attention
     */
    private void processHeadsSequential(FloatArray q, FloatArray keyCache, FloatArray valueCache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul, int pos, int layer,
            int contextLength) {
        int loff = layer * contextLength * kvDim;
        for (int h = 0; h < nHeads; h++) {
            processHeadSequential(q, keyCache, valueCache, xb, h, headSize, kvDim, kvMul, loff, pos);
        }
    }

    // ==================== Helper Methods ====================

    private void fillRandom(FloatArray array, float min, float max) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + random.nextFloat() * range);
        }
    }

    private FloatArray copyArray(FloatArray src) {
        FloatArray dst = new FloatArray(src.getSize());
        for (int i = 0; i < src.getSize(); i++) {
            dst.set(i, src.get(i));
        }
        return dst;
    }

    private HalfFloatArray createRandomHalfFloatArray(int size, float min, float max) {
        HalfFloatArray array = new HalfFloatArray(size);
        float range = max - min;
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(min + random.nextFloat() * range));
        }
        return array;
    }

    private void assertArrayEquals(String message, FloatArray expected, FloatArray actual, float tolerance) {
        assertEquals(message + " - size mismatch", expected.getSize(), actual.getSize());
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(message + " at index " + i, expected.get(i), actual.get(i), tolerance);
        }
    }

    // ==================== Unit Tests ====================

    @Test
    public void testReductionOneBlockWithLayer() throws TornadoExecutionPlanException {
        final int dim = 512;
        final int localSize = 256;
        final int numGroups = (dim + localSize - 1) / localSize;

        FloatArray x = new FloatArray(dim);
        FloatArray output = new FloatArray(numGroups + 1);
        fillRandom(x, -1.0f, 1.0f);

        // Sequential reference
        float expectedNormFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);

        WorkerGrid worker = new WorkerGrid1D(dim);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, output)
                .task("t0", GPULlama3Kernels::reductionOneBlockWithLayer, context, output, x, dim, RMS_NORM_EPS, localSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertEquals("RMS norm factor", expectedNormFactor, output.get(0), 0.01f);
    }

    @Test
    public void testReductionOneBlock2WithLayer() throws TornadoExecutionPlanException {
        final int dim = 512;
        final int localSize = 256;
        final int numGroups = (dim + localSize - 1) / localSize;

        FloatArray x = new FloatArray(dim);
        FloatArray xb = new FloatArray(dim);
        FloatArray weights = new FloatArray(dim);
        FloatArray temp = new FloatArray(numGroups + 1);
        FloatArray expectedOutput = new FloatArray(dim);

        fillRandom(x, -1.0f, 1.0f);
        fillRandom(weights, 0.5f, 1.5f);

        // Sequential reference
        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        temp.set(0, normFactor);
        applyRmsNormSequential(expectedOutput, x, weights, normFactor);

        WorkerGrid worker = new WorkerGrid1D(dim);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights, temp)
                .task("t0", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, weights, temp)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("RMS norm application", expectedOutput, xb, EPSILON_FP32);
    }

    @Test
    public void testFullRMSNormalization() throws TornadoExecutionPlanException {
        final int dim = 512;
        final int localSize = 256;
        final int numGroups = (dim + localSize - 1) / localSize;

        FloatArray x = new FloatArray(dim);
        FloatArray xb = new FloatArray(dim);
        FloatArray weights = new FloatArray(dim);
        FloatArray temp = new FloatArray(numGroups + 1);
        FloatArray expectedOutput = new FloatArray(dim);

        fillRandom(x, -1.0f, 1.0f);
        fillRandom(weights, 0.5f, 1.5f);
        temp.init(0.0f);

        // Sequential reference: full RMS norm
        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        applyRmsNormSequential(expectedOutput, x, weights, normFactor);

        WorkerGrid worker = new WorkerGrid1D(dim);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s0.reduce", worker);
        gridScheduler.addWorkerGrid("s0.apply", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights, temp)
                .task("reduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, dim, RMS_NORM_EPS, localSize)
                .task("apply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, weights, temp)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb, temp);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertEquals("RMS norm factor", normFactor, temp.get(0), 0.01f);
        assertArrayEquals("Full RMS normalization", expectedOutput, xb, EPSILON_FP32);
    }

    @Test
    public void testMatrixVectorGenericFP32() throws TornadoExecutionPlanException {
        final int inputDim = 256;
        final int outputDim = 128;
        final int localSize = 64;

        FloatArray x = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(outputDim * inputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray expectedOutput = new FloatArray(outputDim);

        fillRandom(x, -1.0f, 1.0f);
        fillRandom(weights, -0.1f, 0.1f);

        // Sequential reference
        matrixVectorSequentialFP32(expectedOutput, weights, x, outputDim, inputDim);

        WorkerGrid worker = new WorkerGrid1D(outputDim * localSize);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights)
                .task("t0", GPULlama3Kernels::matrixVectorGeneric, context, x, output, weights, inputDim, outputDim, localSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("MatVec FP32", expectedOutput, output, EPSILON_FP32);
    }

    @Test
    public void testMatrixVectorGenericFP16() throws TornadoExecutionPlanException {
        final int inputDim = 256;
        final int outputDim = 128;
        final int localSize = 64;

        FloatArray x = new FloatArray(inputDim);
        HalfFloatArray weights = createRandomHalfFloatArray(outputDim * inputDim, -0.1f, 0.1f);
        FloatArray output = new FloatArray(outputDim);
        FloatArray expectedOutput = new FloatArray(outputDim);

        fillRandom(x, -1.0f, 1.0f);

        // Sequential reference
        matrixVectorSequentialFP16(expectedOutput, weights, x, outputDim, inputDim);

        WorkerGrid worker = new WorkerGrid1D(outputDim * localSize);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights)
                .task("t0", GPULlama3Kernels::matrixVectorGeneric, context, x, output, weights, inputDim, outputDim, localSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("MatVec FP16", expectedOutput, output, EPSILON_FP16);
    }

    @Test
    public void testMatrixVectorGenericWithResidual() throws TornadoExecutionPlanException {
        final int inputDim = 256;
        final int outputDim = 128;
        final int localSize = 64;

        FloatArray x = new FloatArray(inputDim);
        FloatArray residual = new FloatArray(outputDim);
        FloatArray expectedResidual = new FloatArray(outputDim);
        HalfFloatArray weights = createRandomHalfFloatArray(outputDim * inputDim, -0.1f, 0.1f);

        fillRandom(x, -1.0f, 1.0f);
        fillRandom(residual, -0.5f, 0.5f);

        // Copy residual for sequential computation
        for (int i = 0; i < outputDim; i++) {
            expectedResidual.set(i, residual.get(i));
        }
        matrixVectorWithResidualSequential(expectedResidual, weights, x, outputDim, inputDim);

        WorkerGrid worker = new WorkerGrid1D(outputDim * localSize);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, residual, weights)
                .task("t0", GPULlama3Kernels::matrixVectorGenericWithResidual, context, x, residual, weights, inputDim, outputDim, localSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, residual);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("MatVec with residual", expectedResidual, residual, EPSILON_FP16);
    }

    @Test
    public void testRopeRotation() throws TornadoExecutionPlanException {
        final int dim = 128;
        final int kvDim = 64;
        final int headSize = 32;
        final int position = 5;

        FloatArray sq = new FloatArray(dim);
        FloatArray sk = new FloatArray(kvDim);
        FloatArray expectedSq = new FloatArray(dim);
        FloatArray expectedSk = new FloatArray(kvDim);
        IntArray positionHolder = new IntArray(1);

        fillRandom(sq, -1.0f, 1.0f);
        fillRandom(sk, -1.0f, 1.0f);
        positionHolder.set(0, position);

        // Copy for sequential computation
        for (int i = 0; i < dim; i++)
            expectedSq.set(i, sq.get(i));
        for (int i = 0; i < kvDim; i++)
            expectedSk.set(i, sk.get(i));
        ropeRotationSequential(expectedSq, expectedSk, position, kvDim, headSize);

        WorkerGrid worker = new WorkerGrid1D(dim / 2);
        worker.setLocalWork(Math.min(128, dim / 2), 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, positionHolder, sq, sk)
                .task("t0", GPULlama3Kernels::ropeRotation, context, positionHolder, sq, sk, kvDim, headSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sq, sk);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("RoPE query rotation", expectedSq, sq, EPSILON_FP32);
        assertArrayEquals("RoPE key rotation", expectedSk, sk, EPSILON_FP32);
    }

    @Test
    public void testRopeRotationMultiplePositions() throws TornadoExecutionPlanException {
        final int dim = 128;
        final int kvDim = 64;
        final int headSize = 32;

        for (int position : new int[] { 0, 1, 10, 50, 100 }) {
            FloatArray sq = new FloatArray(dim);
            FloatArray sk = new FloatArray(kvDim);
            FloatArray expectedSq = new FloatArray(dim);
            FloatArray expectedSk = new FloatArray(kvDim);
            IntArray positionHolder = new IntArray(1);

            fillRandom(sq, -1.0f, 1.0f);
            fillRandom(sk, -1.0f, 1.0f);
            positionHolder.set(0, position);

            for (int i = 0; i < dim; i++)
                expectedSq.set(i, sq.get(i));
            for (int i = 0; i < kvDim; i++)
                expectedSk.set(i, sk.get(i));
            ropeRotationSequential(expectedSq, expectedSk, position, kvDim, headSize);

            WorkerGrid worker = new WorkerGrid1D(dim / 2);
            worker.setLocalWork(Math.min(128, dim / 2), 1, 1);
            GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
            KernelContext context = new KernelContext();

            // @formatter:off
            TaskGraph taskGraph = new TaskGraph("s0")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, positionHolder, sq, sk)
                    .task("t0", GPULlama3Kernels::ropeRotation, context, positionHolder, sq, sk, kvDim, headSize)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, sq, sk);
            // @formatter:on

            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.withGridScheduler(gridScheduler).execute();
            }

            assertArrayEquals("RoPE Q at position " + position, expectedSq, sq, EPSILON_FP32);
            assertArrayEquals("RoPE K at position " + position, expectedSk, sk, EPSILON_FP32);
        }
    }

    @Test
    public void testCopyToCache() throws TornadoExecutionPlanException {
        final int kvDim = 64;
        final int contextLength = 128;
        final int numLayers = 4;
        final int layer = 2;
        final int position = 10;

        FloatArray key = new FloatArray(kvDim);
        FloatArray value = new FloatArray(kvDim);
        FloatArray keyCache = new FloatArray(numLayers * contextLength * kvDim);
        FloatArray valueCache = new FloatArray(numLayers * contextLength * kvDim);
        FloatArray expectedKeyCache = new FloatArray(numLayers * contextLength * kvDim);
        FloatArray expectedValueCache = new FloatArray(numLayers * contextLength * kvDim);
        IntArray positionHolder = new IntArray(1);

        fillRandom(key, -1.0f, 1.0f);
        fillRandom(value, -1.0f, 1.0f);
        positionHolder.set(0, position);

        // Sequential reference
        copyToCacheSequential(expectedKeyCache, key, expectedValueCache, value, position, kvDim, layer, contextLength);

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, key, value, keyCache, valueCache, positionHolder)
                .task("t0", GPULlama3Kernels::copyToCache, keyCache, key, valueCache, value, positionHolder, kvDim, layer, contextLength)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, keyCache, valueCache);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        // Verify the entire cache matches (including zeros)
        int loff = layer * contextLength * kvDim;
        int destOffset = loff + position * kvDim;
        for (int i = 0; i < kvDim; i++) {
            assertEquals("Key cache at " + i, key.get(i), keyCache.get(destOffset + i), EPSILON_FP32);
            assertEquals("Value cache at " + i, value.get(i), valueCache.get(destOffset + i), EPSILON_FP32);
        }
    }

    @Test
    public void testFusedFeedForwardWithSiLUAndGLUActivation() throws TornadoExecutionPlanException {
        final int inputDim = 128;
        final int hiddenDim = 64;
        final int localSize = 32;

        FloatArray x = new FloatArray(inputDim);
        HalfFloatArray w1 = createRandomHalfFloatArray(hiddenDim * inputDim, -0.1f, 0.1f);
        HalfFloatArray w3 = createRandomHalfFloatArray(hiddenDim * inputDim, -0.1f, 0.1f);
        FloatArray output = new FloatArray(hiddenDim);
        FloatArray expectedOutput = new FloatArray(hiddenDim);

        fillRandom(x, -1.0f, 1.0f);

        // Sequential reference
        fusedFFNSequential(expectedOutput, x, w1, w3, inputDim, hiddenDim);

        WorkerGrid worker = new WorkerGrid1D(hiddenDim * localSize);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w1, w3)
                .task("t0", GPULlama3Kernels::fusedFeedForwardWithSiLUAndGLUActivation, context, x, output, w1, w3, inputDim, hiddenDim, localSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("Fused FFN", expectedOutput, output, EPSILON_ACCUMULATED);
    }

    @Test
    public void testAddInPlace() throws TornadoExecutionPlanException {
        final int size = 512;

        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray expectedA = new FloatArray(size);

        fillRandom(a, -1.0f, 1.0f);
        fillRandom(b, -1.0f, 1.0f);

        // Copy for sequential
        for (int i = 0; i < size; i++) {
            expectedA.set(i, a.get(i));
        }
        addInPlaceSequential(expectedA, b);

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("t0", GPULlama3Kernels::addInPlace, a, b, size)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertArrayEquals("Add in place", expectedA, a, EPSILON_FP32);
    }

    @Test
    public void testSplitQKV() throws TornadoExecutionPlanException {
        final int dimQ = 256;
        final int dimKV = 64;
        final int totalSize = dimQ + 2 * dimKV;

        FloatArray qkv = new FloatArray(totalSize);
        FloatArray q = new FloatArray(dimQ);
        FloatArray k = new FloatArray(dimKV);
        FloatArray v = new FloatArray(dimKV);

        fillRandom(qkv, -1.0f, 1.0f);

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, qkv)
                .task("t0", GPULlama3Kernels::splitQKV, qkv, q, k, v, dimQ, dimKV)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, q, k, v);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        // Verify exact split
        for (int i = 0; i < dimQ; i++) {
            assertEquals("Q[" + i + "]", qkv.get(i), q.get(i), EPSILON_FP32);
        }
        for (int i = 0; i < dimKV; i++) {
            assertEquals("K[" + i + "]", qkv.get(dimQ + i), k.get(i), EPSILON_FP32);
            assertEquals("V[" + i + "]", qkv.get(dimQ + dimKV + i), v.get(i), EPSILON_FP32);
        }
    }

    @Test
    public void testSplitGateUpAndSiLU() throws TornadoExecutionPlanException {
        final int hiddenDim = 256;

        FloatArray hb = new FloatArray(hiddenDim * 2);
        FloatArray hbG = new FloatArray(hiddenDim);
        FloatArray hbU = new FloatArray(hiddenDim);
        FloatArray expectedHbG = new FloatArray(hiddenDim);
        FloatArray expectedHbU = new FloatArray(hiddenDim);

        fillRandom(hb, -2.0f, 2.0f);

        // Sequential reference
        splitGateUpAndSiLUSequential(hb, expectedHbG, expectedHbU, hiddenDim);

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, hb)
                .task("t0", GPULlama3Kernels::splitGateUpAndSiLU, hb, hbG, hbU, hiddenDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hbG, hbU);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertArrayEquals("Gate with SiLU", expectedHbG, hbG, EPSILON_FP32);
        assertArrayEquals("Up * Gate", expectedHbU, hbU, EPSILON_FP32);
    }

    @Test
    public void testProcessHeadsParallel() throws TornadoExecutionPlanException {
        final int nHeads = 4;
        final int headSize = 32;
        final int kvDim = nHeads * headSize;
        final int dim = nHeads * headSize;
        final int contextLength = 64;
        final int position = 3;
        final int layer = 0;
        final int kvMul = 1;

        FloatArray q = new FloatArray(dim);
        FloatArray keyCache = new FloatArray(contextLength * kvDim);
        FloatArray valueCache = new FloatArray(contextLength * kvDim);
        FloatArray xb = new FloatArray(dim);
        FloatArray expectedXb = new FloatArray(dim);
        FloatArray att = new FloatArray(nHeads * contextLength);
        IntArray positionHolder = new IntArray(1);

        fillRandom(q, -1.0f, 1.0f);
        fillRandom(keyCache, -1.0f, 1.0f);
        fillRandom(valueCache, -1.0f, 1.0f);
        positionHolder.set(0, position);

        // Sequential reference
        processHeadsSequential(q, keyCache, valueCache, expectedXb, nHeads, headSize, kvDim, kvMul, position, layer, contextLength);

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, keyCache, valueCache, positionHolder, att)
                .task("t0", GPULlama3Kernels::processHeadsParallel, q, keyCache, valueCache, xb, nHeads, headSize, kvDim, kvMul, contextLength, positionHolder, att, layer, contextLength)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertArrayEquals("Parallel attention", expectedXb, xb, EPSILON_FP32);
    }

    @Test
    public void testProcessHeadsFlashAttention() throws TornadoExecutionPlanException {
        final int nHeads = 4;
        final int headSize = 32;
        final int kvDim = nHeads * headSize;
        final int dim = nHeads * headSize;
        final int contextLength = 64;
        final int position = 3;
        final int layer = 0;
        final int kvMul = 1;

        FloatArray q = new FloatArray(dim);
        FloatArray keyCache = new FloatArray(contextLength * kvDim);
        FloatArray valueCache = new FloatArray(contextLength * kvDim);
        FloatArray xb = new FloatArray(dim);
        FloatArray expectedXb = new FloatArray(dim);
        IntArray positionHolder = new IntArray(1);

        fillRandom(q, -1.0f, 1.0f);
        fillRandom(keyCache, -1.0f, 1.0f);
        fillRandom(valueCache, -1.0f, 1.0f);
        positionHolder.set(0, position);

        // Sequential reference (same as processHeadsParallel)
        processHeadsSequential(q, keyCache, valueCache, expectedXb, nHeads, headSize, kvDim, kvMul, position, layer, contextLength);

        WorkerGrid worker = new WorkerGrid1D(nHeads * headSize);
        worker.setLocalWork(headSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, keyCache, valueCache, positionHolder)
                .task("t0", GPULlama3Kernels::processHeadsFlashAttention, context, q, keyCache, valueCache, xb, nHeads, headSize, kvDim, kvMul, positionHolder, layer, contextLength)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        assertArrayEquals("Flash attention", expectedXb, xb, EPSILON_FP32);
    }

    @Test
    public void testAttentionConsistency() throws TornadoExecutionPlanException {
        // Test that both attention implementations produce the same result
        final int nHeads = 4;
        final int headSize = 32;
        final int kvDim = nHeads * headSize;
        final int dim = nHeads * headSize;
        final int contextLength = 64;
        final int position = 5;
        final int layer = 0;
        final int kvMul = 1;

        FloatArray q = new FloatArray(dim);
        FloatArray keyCache = new FloatArray(contextLength * kvDim);
        FloatArray valueCache = new FloatArray(contextLength * kvDim);
        FloatArray xbParallel = new FloatArray(dim);
        FloatArray xbFlash = new FloatArray(dim);
        FloatArray att = new FloatArray(nHeads * contextLength);
        IntArray positionHolder = new IntArray(1);

        fillRandom(q, -1.0f, 1.0f);
        fillRandom(keyCache, -1.0f, 1.0f);
        fillRandom(valueCache, -1.0f, 1.0f);
        positionHolder.set(0, position);

        // Test parallel attention
        // @formatter:off
        TaskGraph taskGraphParallel = new TaskGraph("s_parallel")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, keyCache, valueCache, positionHolder, att)
                .task("t0", GPULlama3Kernels::processHeadsParallel, q, keyCache, valueCache, xbParallel, nHeads, headSize, kvDim, kvMul, contextLength, positionHolder, att, layer, contextLength)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xbParallel);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraphParallel = taskGraphParallel.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraphParallel)) {
            executionPlan.execute();
        }

        // Test flash attention
        WorkerGrid worker = new WorkerGrid1D(nHeads * headSize);
        worker.setLocalWork(headSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s_flash.t0", worker);
        KernelContext context = new KernelContext();

        // @formatter:off
        TaskGraph taskGraphFlash = new TaskGraph("s_flash")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, keyCache, valueCache, positionHolder)
                .task("t0", GPULlama3Kernels::processHeadsFlashAttention, context, q, keyCache, valueCache, xbFlash, nHeads, headSize, kvDim, kvMul, positionHolder, layer, contextLength)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xbFlash);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraphFlash = taskGraphFlash.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraphFlash)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Both implementations should match
        assertArrayEquals("Parallel vs Flash attention consistency", xbParallel, xbFlash, EPSILON_FP32);
    }
}