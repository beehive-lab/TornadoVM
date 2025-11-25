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
 * Fused pipeline tests with full numerical verification.
 * Tests progressive combinations of kernels matching the LlamaFP16FFNLayers pipeline.
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V org.beehive.gpullama3.tornadovm.kernels.TestTransformerKernelsFused
 * </code>
 */
public class TestTransformerKernelsFused extends TornadoTestBase {

    // Model configuration
    private static final int DIM = 256;
    private static final int KV_DIM = 64;
    private static final int HIDDEN_DIM = 512;
    private static final int N_HEADS = 4;
    private static final int HEAD_SIZE = DIM / N_HEADS;
    private static final int KV_MUL = N_HEADS / (KV_DIM / HEAD_SIZE);
    private static final int CONTEXT_LENGTH = 128;
    private static final float RMS_NORM_EPS = 1e-5f;
    private static final int LOCAL_SIZE = 64;
    private static final int LOCAL_SIZE_RMS = 256;

    // Tolerances
    private static final float EPSILON_FP32 = 1e-4f;
    private static final float EPSILON_FP16 = 0.05f;
    private static final float EPSILON_ACCUMULATED = 0.15f;  // For multi-stage pipelines

    private Random random;
    private KernelContext context;

    // State arrays
    private FloatArray x, xb, xb2, q, k, v;
    private FloatArray keyCache, valueCache, att, hb;
    private FloatArray temp, tempFFN;
    private IntArray positionHolder;

    // Weights
    private FloatArray rmsAttWeight, rmsFfnWeight;
    private HalfFloatArray wq, wk, wv, wo, w1, w2, w3;

    @Before
    public void setUp() {
        random = new Random(42);
        context = new KernelContext();
        initializeArrays();
        initializeWeights();
    }

    private void initializeArrays() {
        x = new FloatArray(DIM);
        xb = new FloatArray(DIM);
        xb2 = new FloatArray(DIM);
        q = new FloatArray(DIM);
        k = new FloatArray(KV_DIM);
        v = new FloatArray(KV_DIM);
        keyCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        valueCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        att = new FloatArray(N_HEADS * CONTEXT_LENGTH);
        hb = new FloatArray(HIDDEN_DIM);

        int numGroups = (DIM + LOCAL_SIZE_RMS - 1) / LOCAL_SIZE_RMS;
        temp = new FloatArray(numGroups + 1);
        tempFFN = new FloatArray(numGroups + 1);
        positionHolder = new IntArray(1);

        fillRandom(x, -1.0f, 1.0f);
        temp.init(0.0f);
        tempFFN.init(0.0f);
        positionHolder.set(0, 5);
    }

    private void initializeWeights() {
        rmsAttWeight = new FloatArray(DIM);
        rmsFfnWeight = new FloatArray(DIM);
        fillRandom(rmsAttWeight, 0.8f, 1.2f);
        fillRandom(rmsFfnWeight, 0.8f, 1.2f);

        wq = createRandomHalfFloatArray(DIM * DIM, -0.1f, 0.1f);
        wk = createRandomHalfFloatArray(KV_DIM * DIM, -0.1f, 0.1f);
        wv = createRandomHalfFloatArray(KV_DIM * DIM, -0.1f, 0.1f);
        wo = createRandomHalfFloatArray(DIM * DIM, -0.1f, 0.1f);
        w1 = createRandomHalfFloatArray(HIDDEN_DIM * DIM, -0.1f, 0.1f);
        w2 = createRandomHalfFloatArray(DIM * HIDDEN_DIM, -0.1f, 0.1f);
        w3 = createRandomHalfFloatArray(HIDDEN_DIM * DIM, -0.1f, 0.1f);
    }

    // ==================== Sequential Reference Implementations ====================

    private float computeRmsNormFactorSequential(FloatArray x, float eps) {
        float ss = 0.0f;
        for (int i = 0; i < x.getSize(); i++) {
            ss += x.get(i) * x.get(i);
        }
        ss /= x.getSize();
        ss += eps;
        return 1.0f / (float) Math.sqrt(ss);
    }

    private void applyRmsNormSequential(FloatArray output, FloatArray x, FloatArray weights, float normFactor) {
        for (int i = 0; i < x.getSize(); i++) {
            output.set(i, weights.get(i) * (normFactor * x.get(i)));
        }
    }

    private void matrixVectorSequentialFP16(FloatArray output, HalfFloatArray weights, FloatArray x, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) {
                sum += weights.get(i * cols + j).getFloat32() * x.get(j);
            }
            output.set(i, sum);
        }
    }

    private void matrixVectorWithResidualSequential(FloatArray output, HalfFloatArray weights, FloatArray x, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) {
                sum += weights.get(i * cols + j).getFloat32() * x.get(j);
            }
            output.set(i, output.get(i) + sum);
        }
    }

    private void ropeRotationSequential(FloatArray sq, FloatArray sk, int pos, int kvDim, int headSize) {
        int numPairs = sq.getSize() / 2;
        for (int i = 0; i < numPairs; i++) {
            int idx = i * 2;
            int headDim = idx % headSize;
            float freq = 1.0f / (float) Math.pow(50000.0f, headDim / (float) headSize);
            float val = pos * freq;
            float fcr = (float) Math.cos(val);
            float fci = (float) Math.sin(val);

            float v0q = sq.get(idx);
            float v1q = sq.get(idx + 1);
            sq.set(idx, v0q * fcr - v1q * fci);
            sq.set(idx + 1, v0q * fci + v1q * fcr);

            if (idx < kvDim && idx + 1 < sk.getSize()) {
                float v0k = sk.get(idx);
                float v1k = sk.get(idx + 1);
                sk.set(idx, v0k * fcr - v1k * fci);
                sk.set(idx + 1, v0k * fci + v1k * fcr);
            }
        }
    }

    private void copyToCacheSequential(FloatArray keyCache, FloatArray key, FloatArray valueCache, FloatArray value, int position, int kvDim, int layer, int contextLength) {
        int loff = layer * contextLength * kvDim;
        int destOffset = loff + position * kvDim;
        for (int i = 0; i < key.getSize(); i++) {
            keyCache.set(destOffset + i, key.get(i));
            valueCache.set(destOffset + i, value.get(i));
        }
    }

    private void processHeadSequential(FloatArray q, FloatArray keyCache, FloatArray valueCache, FloatArray xb, int h, int headSize, int kvDim, int kvMul, int loff, int pos) {
        int kvHeadIdx = h / kvMul;

        float[] attScores = new float[pos + 1];
        for (int t = 0; t <= pos; t++) {
            int keyOffset = loff + t * kvDim + kvHeadIdx * headSize;
            float score = 0.0f;
            for (int i = 0; i < headSize; i++) {
                score += q.get(h * headSize + i) * keyCache.get(keyOffset + i);
            }
            attScores[t] = score / (float) Math.sqrt(headSize);
        }

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

        for (int i = 0; i < headSize; i++) {
            float weightedSum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                int valueOffset = loff + t * kvDim + kvHeadIdx * headSize;
                weightedSum += attScores[t] * valueCache.get(valueOffset + i);
            }
            xb.set(h * headSize + i, weightedSum);
        }
    }

    private void processHeadsSequential(FloatArray q, FloatArray keyCache, FloatArray valueCache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul, int pos, int layer,
            int contextLength) {
        int loff = layer * contextLength * kvDim;
        for (int h = 0; h < nHeads; h++) {
            processHeadSequential(q, keyCache, valueCache, xb, h, headSize, kvDim, kvMul, loff, pos);
        }
    }

    private float siluSequential(float x) {
        return x * (1.0f / (1.0f + (float) Math.exp(-x)));
    }

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

    private GridScheduler createScheduler() {
        WorkerGrid rmsWorker = new WorkerGrid1D(DIM);
        rmsWorker.setLocalWork(LOCAL_SIZE_RMS, 1, 1);

        WorkerGrid qMatmulWorker = new WorkerGrid1D(DIM * LOCAL_SIZE);
        qMatmulWorker.setLocalWork(LOCAL_SIZE, 1, 1);

        WorkerGrid kvMatmulWorker = new WorkerGrid1D(KV_DIM * LOCAL_SIZE);
        kvMatmulWorker.setLocalWork(LOCAL_SIZE, 1, 1);

        WorkerGrid ropeWorker = new WorkerGrid1D(DIM / 2);
        ropeWorker.setLocalWork(Math.min(128, DIM / 2), 1, 1);

        WorkerGrid ffnWorker = new WorkerGrid1D(HIDDEN_DIM * LOCAL_SIZE);
        ffnWorker.setLocalWork(LOCAL_SIZE, 1, 1);

        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid("s0.rmsReduce", rmsWorker);
        scheduler.addWorkerGrid("s0.rmsApply", rmsWorker);
        scheduler.addWorkerGrid("s0.qmatmul", qMatmulWorker);
        scheduler.addWorkerGrid("s0.kmatmul", kvMatmulWorker);
        scheduler.addWorkerGrid("s0.vmatmul", kvMatmulWorker);
        scheduler.addWorkerGrid("s0.rope", ropeWorker);
        scheduler.addWorkerGrid("s0.outputProj", qMatmulWorker);
        scheduler.addWorkerGrid("s0.rmsReduceFFN", rmsWorker);
        scheduler.addWorkerGrid("s0.rmsApplyFFN", rmsWorker);
        scheduler.addWorkerGrid("s0.fusedFFN", ffnWorker);
        scheduler.addWorkerGrid("s0.ffnProj", qMatmulWorker);

        return scheduler;
    }

    // ==================== Stage 1: RMS Normalization ====================

    @Test
    public void testFusedStage1_RMSNorm() throws TornadoExecutionPlanException {
        // Sequential reference
        FloatArray expectedXb = new FloatArray(DIM);
        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        applyRmsNormSequential(expectedXb, x, rmsAttWeight, normFactor);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, temp)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb, temp);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertEquals("RMS norm factor", normFactor, temp.get(0), 0.01f);
        assertArrayEquals("Stage 1: RMS Norm output", expectedXb, xb, EPSILON_FP32);
    }

    // ==================== Stage 2: RMS Norm + Q Projection ====================

    @Test
    public void testFusedStage2_RMSNorm_QMatmul() throws TornadoExecutionPlanException {
        // Sequential reference
        FloatArray expectedXb = new FloatArray(DIM);
        FloatArray expectedQ = new FloatArray(DIM);

        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        applyRmsNormSequential(expectedXb, x, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(expectedQ, wq, expectedXb, DIM, DIM);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, temp, wq)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, q);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("Stage 2: Q projection output", expectedQ, q, EPSILON_FP16);
    }

    // ==================== Stage 3: RMS Norm + QKV Projections ====================

    @Test
    public void testFusedStage3_RMSNorm_QKVMatmul() throws TornadoExecutionPlanException {
        // Sequential reference
        FloatArray expectedXb = new FloatArray(DIM);
        FloatArray expectedQ = new FloatArray(DIM);
        FloatArray expectedK = new FloatArray(KV_DIM);
        FloatArray expectedV = new FloatArray(KV_DIM);

        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        applyRmsNormSequential(expectedXb, x, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(expectedQ, wq, expectedXb, DIM, DIM);
        matrixVectorSequentialFP16(expectedK, wk, expectedXb, KV_DIM, DIM);
        matrixVectorSequentialFP16(expectedV, wv, expectedXb, KV_DIM, DIM);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, temp, wq, wk, wv)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, q, k, v);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("Stage 3: Q output", expectedQ, q, EPSILON_FP16);
        assertArrayEquals("Stage 3: K output", expectedK, k, EPSILON_FP16);
        assertArrayEquals("Stage 3: V output", expectedV, v, EPSILON_FP16);
    }

    // ==================== Stage 4: QKV + RoPE ====================

    @Test
    public void testFusedStage4_QKV_RoPE() throws TornadoExecutionPlanException {
        int position = positionHolder.get(0);

        // Sequential reference
        FloatArray expectedXb = new FloatArray(DIM);
        FloatArray expectedQ = new FloatArray(DIM);
        FloatArray expectedK = new FloatArray(KV_DIM);

        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        applyRmsNormSequential(expectedXb, x, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(expectedQ, wq, expectedXb, DIM, DIM);
        matrixVectorSequentialFP16(expectedK, wk, expectedXb, KV_DIM, DIM);
        ropeRotationSequential(expectedQ, expectedK, position, KV_DIM, HEAD_SIZE);

        GridScheduler scheduler = createScheduler();
        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, temp, wq, wk, wv, positionHolder)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .task("rope", GPULlama3Kernels::ropeRotation, context, positionHolder, q, k, KV_DIM, HEAD_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, q, k);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("Stage 4: Q after RoPE", expectedQ, q, EPSILON_FP16);
        assertArrayEquals("Stage 4: K after RoPE", expectedK, k, EPSILON_FP16);
    }

    // ==================== Stage 5: QKV + RoPE + Cache Update ====================

    @Test
    public void testFusedStage5_QKV_RoPE_Cache() throws TornadoExecutionPlanException {
        final int layer = 0;
        int position = positionHolder.get(0);

        // Sequential reference
        FloatArray expectedXb = new FloatArray(DIM);
        FloatArray expectedQ = new FloatArray(DIM);
        FloatArray expectedK = new FloatArray(KV_DIM);
        FloatArray expectedV = new FloatArray(KV_DIM);
        FloatArray expectedKeyCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray expectedValueCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);

        float normFactor = computeRmsNormFactorSequential(x, RMS_NORM_EPS);
        applyRmsNormSequential(expectedXb, x, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(expectedQ, wq, expectedXb, DIM, DIM);
        matrixVectorSequentialFP16(expectedK, wk, expectedXb, KV_DIM, DIM);
        matrixVectorSequentialFP16(expectedV, wv, expectedXb, KV_DIM, DIM);
        ropeRotationSequential(expectedQ, expectedK, position, KV_DIM, HEAD_SIZE);
        copyToCacheSequential(expectedKeyCache, expectedK, expectedValueCache, expectedV, position, KV_DIM, layer, CONTEXT_LENGTH);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, temp, wq, wk, wv, positionHolder, keyCache, valueCache)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .task("rope", GPULlama3Kernels::ropeRotation, context, positionHolder, q, k, KV_DIM, HEAD_SIZE)
                .task("copyToCache", GPULlama3Kernels::copyToCache, keyCache, k, valueCache, v, positionHolder, KV_DIM, layer, CONTEXT_LENGTH)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, keyCache, valueCache);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify cache at the specific position
        int destOffset = position * KV_DIM;
        for (int i = 0; i < KV_DIM; i++) {
            assertEquals("Stage 5: Key cache at " + i, expectedKeyCache.get(destOffset + i), keyCache.get(destOffset + i), EPSILON_FP16);
            assertEquals("Stage 5: Value cache at " + i, expectedValueCache.get(destOffset + i), valueCache.get(destOffset + i), EPSILON_FP16);
        }
    }

    // ==================== Stage 6: Full Attention Block ====================

    @Test
    public void testFusedStage6_FullAttentionBlock() throws TornadoExecutionPlanException {
        final int layer = 0;
        int position = positionHolder.get(0);

        // Sequential reference
        FloatArray seqXb = new FloatArray(DIM);
        FloatArray seqQ = new FloatArray(DIM);
        FloatArray seqK = new FloatArray(KV_DIM);
        FloatArray seqV = new FloatArray(KV_DIM);
        FloatArray seqKeyCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqValueCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqAttOut = new FloatArray(DIM);
        FloatArray expectedX = copyArray(x);

        float normFactor = computeRmsNormFactorSequential(expectedX, RMS_NORM_EPS);
        applyRmsNormSequential(seqXb, expectedX, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(seqQ, wq, seqXb, DIM, DIM);
        matrixVectorSequentialFP16(seqK, wk, seqXb, KV_DIM, DIM);
        matrixVectorSequentialFP16(seqV, wv, seqXb, KV_DIM, DIM);
        ropeRotationSequential(seqQ, seqK, position, KV_DIM, HEAD_SIZE);
        copyToCacheSequential(seqKeyCache, seqK, seqValueCache, seqV, position, KV_DIM, layer, CONTEXT_LENGTH);
        processHeadsSequential(seqQ, seqKeyCache, seqValueCache, seqAttOut, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, position, layer, CONTEXT_LENGTH);
        // Copy seqAttOut to seqXb for output projection
        for (int i = 0; i < DIM; i++)
            seqXb.set(i, seqAttOut.get(i));
        matrixVectorWithResidualSequential(expectedX, wo, seqXb, DIM, DIM);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, temp, wq, wk, wv, wo, positionHolder, keyCache, valueCache, att)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .task("rope", GPULlama3Kernels::ropeRotation, context, positionHolder, q, k, KV_DIM, HEAD_SIZE)
                .task("copyToCache", GPULlama3Kernels::copyToCache, keyCache, k, valueCache, v, positionHolder, KV_DIM, layer, CONTEXT_LENGTH)
                .task("attention", GPULlama3Kernels::processHeadsParallel, q, keyCache, valueCache, xb, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, CONTEXT_LENGTH, positionHolder, att, layer, CONTEXT_LENGTH)
                .task("outputProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, xb, x, wo, DIM, DIM, LOCAL_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("Stage 6: Full attention block output", expectedX, x, EPSILON_ACCUMULATED);
    }

    // ==================== Stage 7: Attention + FFN RMS Norm ====================

    @Test
    public void testFusedStage7_AttentionBlock_FFNRMSNorm() throws TornadoExecutionPlanException {
        final int layer = 0;
        int position = positionHolder.get(0);

        // Sequential reference (build on Stage 6)
        FloatArray seqXb = new FloatArray(DIM);
        FloatArray seqQ = new FloatArray(DIM);
        FloatArray seqK = new FloatArray(KV_DIM);
        FloatArray seqV = new FloatArray(KV_DIM);
        FloatArray seqKeyCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqValueCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqAttOut = new FloatArray(DIM);
        FloatArray seqX = copyArray(x);
        FloatArray expectedXb = new FloatArray(DIM);

        // Attention block
        float normFactor = computeRmsNormFactorSequential(seqX, RMS_NORM_EPS);
        applyRmsNormSequential(seqXb, seqX, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(seqQ, wq, seqXb, DIM, DIM);
        matrixVectorSequentialFP16(seqK, wk, seqXb, KV_DIM, DIM);
        matrixVectorSequentialFP16(seqV, wv, seqXb, KV_DIM, DIM);
        ropeRotationSequential(seqQ, seqK, position, KV_DIM, HEAD_SIZE);
        copyToCacheSequential(seqKeyCache, seqK, seqValueCache, seqV, position, KV_DIM, layer, CONTEXT_LENGTH);
        processHeadsSequential(seqQ, seqKeyCache, seqValueCache, seqAttOut, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, position, layer, CONTEXT_LENGTH);
        for (int i = 0; i < DIM; i++)
            seqXb.set(i, seqAttOut.get(i));
        matrixVectorWithResidualSequential(seqX, wo, seqXb, DIM, DIM);

        // FFN RMS norm
        float ffnNormFactor = computeRmsNormFactorSequential(seqX, RMS_NORM_EPS);
        applyRmsNormSequential(expectedXb, seqX, rmsFfnWeight, ffnNormFactor);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, rmsFfnWeight, temp, tempFFN, wq, wk, wv, wo, positionHolder, keyCache, valueCache, att)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .task("rope", GPULlama3Kernels::ropeRotation, context, positionHolder, q, k, KV_DIM, HEAD_SIZE)
                .task("copyToCache", GPULlama3Kernels::copyToCache, keyCache, k, valueCache, v, positionHolder, KV_DIM, layer, CONTEXT_LENGTH)
                .task("attention", GPULlama3Kernels::processHeadsParallel, q, keyCache, valueCache, xb, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, CONTEXT_LENGTH, positionHolder, att, layer, CONTEXT_LENGTH)
                .task("outputProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, xb, x, wo, DIM, DIM, LOCAL_SIZE)
                .task("rmsReduceFFN", GPULlama3Kernels::reductionOneBlockWithLayer, context, tempFFN, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApplyFFN", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsFfnWeight, tempFFN)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("Stage 7: FFN input (after RMS norm)", expectedXb, xb, EPSILON_ACCUMULATED);
    }

    // ==================== Stage 8: Complete Transformer Layer ====================

    @Test
    public void testFusedStage8_CompleteTransformerLayer() throws TornadoExecutionPlanException {
        final int layer = 0;
        int position = positionHolder.get(0);

        // Full sequential reference
        FloatArray seqXb = new FloatArray(DIM);
        FloatArray seqQ = new FloatArray(DIM);
        FloatArray seqK = new FloatArray(KV_DIM);
        FloatArray seqV = new FloatArray(KV_DIM);
        FloatArray seqKeyCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqValueCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqAttOut = new FloatArray(DIM);
        FloatArray seqHb = new FloatArray(HIDDEN_DIM);
        FloatArray expectedX = copyArray(x);

        // Attention block
        float normFactor = computeRmsNormFactorSequential(expectedX, RMS_NORM_EPS);
        applyRmsNormSequential(seqXb, expectedX, rmsAttWeight, normFactor);
        matrixVectorSequentialFP16(seqQ, wq, seqXb, DIM, DIM);
        matrixVectorSequentialFP16(seqK, wk, seqXb, KV_DIM, DIM);
        matrixVectorSequentialFP16(seqV, wv, seqXb, KV_DIM, DIM);
        ropeRotationSequential(seqQ, seqK, position, KV_DIM, HEAD_SIZE);
        copyToCacheSequential(seqKeyCache, seqK, seqValueCache, seqV, position, KV_DIM, layer, CONTEXT_LENGTH);
        processHeadsSequential(seqQ, seqKeyCache, seqValueCache, seqAttOut, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, position, layer, CONTEXT_LENGTH);
        for (int i = 0; i < DIM; i++)
            seqXb.set(i, seqAttOut.get(i));
        matrixVectorWithResidualSequential(expectedX, wo, seqXb, DIM, DIM);

        // FFN block
        float ffnNormFactor = computeRmsNormFactorSequential(expectedX, RMS_NORM_EPS);
        applyRmsNormSequential(seqXb, expectedX, rmsFfnWeight, ffnNormFactor);
        fusedFFNSequential(seqHb, seqXb, w1, w3, DIM, HIDDEN_DIM);
        matrixVectorWithResidualSequential(expectedX, w2, seqHb, DIM, HIDDEN_DIM);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, rmsFfnWeight, temp, tempFFN, wq, wk, wv, wo, w1, w2, w3, positionHolder, keyCache, valueCache, att)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .task("rope", GPULlama3Kernels::ropeRotation, context, positionHolder, q, k, KV_DIM, HEAD_SIZE)
                .task("copyToCache", GPULlama3Kernels::copyToCache, keyCache, k, valueCache, v, positionHolder, KV_DIM, layer, CONTEXT_LENGTH)
                .task("attention", GPULlama3Kernels::processHeadsParallel, q, keyCache, valueCache, xb, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, CONTEXT_LENGTH, positionHolder, att, layer, CONTEXT_LENGTH)
                .task("outputProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, xb, x, wo, DIM, DIM, LOCAL_SIZE)
                .task("rmsReduceFFN", GPULlama3Kernels::reductionOneBlockWithLayer, context, tempFFN, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApplyFFN", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsFfnWeight, tempFFN)
                .task("fusedFFN", GPULlama3Kernels::fusedFeedForwardWithSiLUAndGLUActivation, context, xb, hb, w1, w3, DIM, HIDDEN_DIM, LOCAL_SIZE)
                .task("ffnProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, hb, x, w2, HIDDEN_DIM, DIM, LOCAL_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("Stage 8: Complete transformer layer output", expectedX, x, EPSILON_ACCUMULATED);

    }

    // ==================== Multi-Iteration Test ====================

    @Test
    public void testFusedMultipleIterations() throws TornadoExecutionPlanException {
        final int layer = 0;
        final int numIterations = 3;

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsAttWeight, rmsFfnWeight, temp, tempFFN, wq, wk, wv, wo, w1, w2, w3, positionHolder, keyCache, valueCache, att)
                .task("rmsReduce", GPULlama3Kernels::reductionOneBlockWithLayer, context, temp, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApply", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsAttWeight, temp)
                .task("qmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, q, wq, DIM, DIM, LOCAL_SIZE)
                .task("kmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, k, wk, DIM, KV_DIM, LOCAL_SIZE)
                .task("vmatmul", GPULlama3Kernels::matrixVectorGeneric, context, xb, v, wv, DIM, KV_DIM, LOCAL_SIZE)
                .task("rope", GPULlama3Kernels::ropeRotation, context, positionHolder, q, k, KV_DIM, HEAD_SIZE)
                .task("copyToCache", GPULlama3Kernels::copyToCache, keyCache, k, valueCache, v, positionHolder, KV_DIM, layer, CONTEXT_LENGTH)
                .task("attention", GPULlama3Kernels::processHeadsParallel, q, keyCache, valueCache, xb, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, CONTEXT_LENGTH, positionHolder, att, layer, CONTEXT_LENGTH)
                .task("outputProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, xb, x, wo, DIM, DIM, LOCAL_SIZE)
                .task("rmsReduceFFN", GPULlama3Kernels::reductionOneBlockWithLayer, context, tempFFN, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApplyFFN", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsFfnWeight, tempFFN)
                .task("fusedFFN", GPULlama3Kernels::fusedFeedForwardWithSiLUAndGLUActivation, context, xb, hb, w1, w3, DIM, HIDDEN_DIM, LOCAL_SIZE)
                .task("ffnProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, hb, x, w2, HIDDEN_DIM, DIM, LOCAL_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Track sequential state
        FloatArray seqX = copyArray(x);
        FloatArray seqXb = new FloatArray(DIM);
        FloatArray seqQ = new FloatArray(DIM);
        FloatArray seqK = new FloatArray(KV_DIM);
        FloatArray seqV = new FloatArray(KV_DIM);
        FloatArray seqKeyCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqValueCache = new FloatArray(CONTEXT_LENGTH * KV_DIM);
        FloatArray seqAttOut = new FloatArray(DIM);
        FloatArray seqHb = new FloatArray(HIDDEN_DIM);

        for (int iter = 0; iter < numIterations; iter++) {
            int position = iter;
            positionHolder.set(0, position);
            temp.init(0.0f);
            tempFFN.init(0.0f);

            // Sequential computation for this iteration
            float normFactor = computeRmsNormFactorSequential(seqX, RMS_NORM_EPS);
            applyRmsNormSequential(seqXb, seqX, rmsAttWeight, normFactor);
            matrixVectorSequentialFP16(seqQ, wq, seqXb, DIM, DIM);
            matrixVectorSequentialFP16(seqK, wk, seqXb, KV_DIM, DIM);
            matrixVectorSequentialFP16(seqV, wv, seqXb, KV_DIM, DIM);
            ropeRotationSequential(seqQ, seqK, position, KV_DIM, HEAD_SIZE);
            copyToCacheSequential(seqKeyCache, seqK, seqValueCache, seqV, position, KV_DIM, layer, CONTEXT_LENGTH);
            processHeadsSequential(seqQ, seqKeyCache, seqValueCache, seqAttOut, N_HEADS, HEAD_SIZE, KV_DIM, KV_MUL, position, layer, CONTEXT_LENGTH);
            for (int i = 0; i < DIM; i++)
                seqXb.set(i, seqAttOut.get(i));
            matrixVectorWithResidualSequential(seqX, wo, seqXb, DIM, DIM);
            float ffnNormFactor = computeRmsNormFactorSequential(seqX, RMS_NORM_EPS);
            applyRmsNormSequential(seqXb, seqX, rmsFfnWeight, ffnNormFactor);
            fusedFFNSequential(seqHb, seqXb, w1, w3, DIM, HIDDEN_DIM);
            matrixVectorWithResidualSequential(seqX, w2, seqHb, DIM, HIDDEN_DIM);

            // TornadoVM execution
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.withGridScheduler(scheduler).execute();
            }

            // Verify
            assertArrayEquals("Iteration " + iter + ": output mismatch", seqX, x, EPSILON_ACCUMULATED);

            System.out.printf("Iteration %d: x[0] expected=%.6f, actual=%.6f%n", iter, seqX.get(0), x.get(0));
        }
    }

    // ==================== FFN Block Only Test ====================

    @Test
    public void testFusedFFNBlockOnly() throws TornadoExecutionPlanException {
        // Sequential reference
        FloatArray seqXb = new FloatArray(DIM);
        FloatArray seqHb = new FloatArray(HIDDEN_DIM);
        FloatArray expectedX = copyArray(x);

        float ffnNormFactor = computeRmsNormFactorSequential(expectedX, RMS_NORM_EPS);
        applyRmsNormSequential(seqXb, expectedX, rmsFfnWeight, ffnNormFactor);
        fusedFFNSequential(seqHb, seqXb, w1, w3, DIM, HIDDEN_DIM);
        matrixVectorWithResidualSequential(expectedX, w2, seqHb, DIM, HIDDEN_DIM);

        GridScheduler scheduler = createScheduler();

        // @formatter:off
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsFfnWeight, tempFFN, w1, w2, w3)
                .task("rmsReduceFFN", GPULlama3Kernels::reductionOneBlockWithLayer, context, tempFFN, x, DIM, RMS_NORM_EPS, LOCAL_SIZE_RMS)
                .task("rmsApplyFFN", GPULlama3Kernels::reductionOneBlock2WithLayer, context, xb, x, rmsFfnWeight, tempFFN)
                .task("fusedFFN", GPULlama3Kernels::fusedFeedForwardWithSiLUAndGLUActivation, context, xb, hb, w1, w3, DIM, HIDDEN_DIM, LOCAL_SIZE)
                .task("ffnProj", GPULlama3Kernels::matrixVectorGenericWithResidual, context, hb, x, w2, HIDDEN_DIM, DIM, LOCAL_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x, hb);
        // @formatter:on

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        assertArrayEquals("FFN block: hidden state", seqHb, hb, EPSILON_ACCUMULATED);
        assertArrayEquals("FFN block: output", expectedX, x, EPSILON_ACCUMULATED);
    }
}