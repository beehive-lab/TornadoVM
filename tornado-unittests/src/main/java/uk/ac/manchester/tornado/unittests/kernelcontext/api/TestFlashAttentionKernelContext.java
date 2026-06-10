/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

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
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Standalone reproducer for the GPULlama3 Flash-Attention kernel that fails to compile under the TornadoVM OpenCL backend.
 *
 * <p>
 * The kernel uses one work-group per attention head ({@code groupIdx == head}) and {@code localSize} cooperative threads per
 * head ({@code localIdx == tid}). The output of the GPU kernel is validated against a sequential softmax-attention reference.
 * </p>
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestFlashAttentionKernelContext
 * </code>
 */
public class TestFlashAttentionKernelContext extends TornadoTestBase {

    public static void processHeadsFlashAttentionOptV2(KernelContext context, FloatArray q, FloatArray key_cache, FloatArray value_cache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul, IntArray positionHolder, int layer, int contextLength) {

        final int MAX_HEAD_SIZE = 128;
        final int MAX_LOCAL_SIZE = 128;
        final int MAX_BLOCK_SIZE_C = 32;
        final int MAX_TILE_ELEMENTS = MAX_BLOCK_SIZE_C * MAX_HEAD_SIZE;

        int tid = context.localIdx;
        int h = context.groupIdx;
        int localSize = context.localGroupSizeX;

        if (h >= nHeads) {
            return;
        }

        int pos = positionHolder.get(0);
        int loff = layer * contextLength * kvDim;
        int kvHeadIdx = h / kvMul;
        int BLOCK_SIZE_C = 32;

        float[] q_shared = context.allocateFloatLocalArray(MAX_HEAD_SIZE);
        float[] k_tile = context.allocateFloatLocalArray(MAX_TILE_ELEMENTS);
        float[] v_tile = context.allocateFloatLocalArray(MAX_TILE_ELEMENTS);

        float[] s_tile = context.allocateFloatLocalArray(BLOCK_SIZE_C);
        float[] exp_tile = context.allocateFloatLocalArray(BLOCK_SIZE_C);
        float[] reduction_shared = context.allocateFloatLocalArray(MAX_LOCAL_SIZE);
        float[] state_shared = context.allocateFloatLocalArray(4);

        // === Dimension partitioning: each thread handles subset of output dims ===
        int dimsPerThread = (headSize + localSize - 1) / localSize;
        int myStartDim = tid * dimsPerThread;
        int myEndDim = Math.min(myStartDim + dimsPerThread, headSize);
        int myDimCount = myEndDim - myStartDim;

        // FIX from previous iteration: ensuring output array is statically sized
        final int MAX_OUTPUT_DIMS = MAX_HEAD_SIZE / 8; // e.g., 32 if MAX_HEAD_SIZE=256
        float[] output = new float[MAX_OUTPUT_DIMS];

        // Initialize thread-local output
        for (int i = 0; i < myDimCount; i++) {
            output[i] = 0.0f;
        }

        // Initialize shared state
        if (tid == 0) {
            state_shared[0] = Float.NEGATIVE_INFINITY;
            state_shared[1] = 0.0f;
        }

        // Load query into shared memory (cooperative)
        // NOTE: Loop bound must still use headSize to read correct data volume
        for (int i = tid; i < headSize; i += localSize) {
            q_shared[i] = q.get(h * headSize + i);
        }
        context.localBarrier();

        // Process sequence in tiles
        for (int tileC = 0; tileC <= pos; tileC += BLOCK_SIZE_C) {
            int tileEnd = Math.min(tileC + BLOCK_SIZE_C - 1, pos);
            int tileLen = tileEnd - tileC + 1;

            // === Cooperative K/V tile loading ===
            int totalElements = tileLen * headSize;
            int elementsPerThread = (totalElements + localSize - 1) / localSize;
            int startElem = tid * elementsPerThread;
            int endElem = Math.min(startElem + elementsPerThread, totalElements);

            for (int globalElemIdx = startElem; globalElemIdx < endElem; globalElemIdx++) {
                int seqIdx = globalElemIdx / headSize;
                int dimIdx = globalElemIdx % headSize;
                int kvOffset = loff + (tileC + seqIdx) * kvDim + kvHeadIdx * headSize + dimIdx;
                int tileMemOffset = seqIdx * headSize + dimIdx;

                // Check bounds just to be safe, though kvDim/headSize should ensure this is valid.
                if (tileMemOffset < MAX_TILE_ELEMENTS) {
                    k_tile[tileMemOffset] = key_cache.get(kvOffset);
                    v_tile[tileMemOffset] = value_cache.get(kvOffset);
                }
            }
            context.localBarrier();

            // === Compute attention scores (cooperative) ===
            for (int t = tid; t < tileLen; t += localSize) {
                float score = 0.0f;
                for (int d = 0; d < headSize; d++) {
                    score += q_shared[d] * k_tile[t * headSize + d];
                }
                s_tile[t] = score / TornadoMath.sqrt(headSize);
            }
            context.localBarrier();

            // ... (Parallel reduction for tileMax - uses reduction_shared, which is now fixed)
            float threadMax = Float.NEGATIVE_INFINITY;
            for (int t = tid; t < tileLen; t += localSize) {
                if (s_tile[t] > threadMax) {
                    threadMax = s_tile[t];
                }
            }
            reduction_shared[tid] = threadMax;
            context.localBarrier();

            for (int stride = localSize / 2; stride > 0; stride /= 2) {
                if (tid < stride) {
                    reduction_shared[tid] = Math.max(reduction_shared[tid], reduction_shared[tid + stride]);
                }
                context.localBarrier();
            }
            float tileMax = reduction_shared[0];

            // === Update running max and rescale if needed ===
            float prevMax = state_shared[0];
            float newMax = Math.max(prevMax, tileMax);
            float scale = 1.0f;

            if (newMax != prevMax && prevMax != Float.NEGATIVE_INFINITY) {
                scale = TornadoMath.exp(prevMax - newMax);
                for (int i = 0; i < myDimCount; i++) {
                    output[i] *= scale;
                }
            }

            // === Compute exp(score - max) and tile sum (cooperative) ===
            for (int t = tid; t < tileLen; t += localSize) {
                exp_tile[t] = TornadoMath.exp(s_tile[t] - newMax);
            }
            context.localBarrier();

            // Parallel reduction for tile sum
            // ... (Uses reduction_shared, which is now fixed)
            float threadSum = 0.0f;
            for (int t = tid; t < tileLen; t += localSize) {
                threadSum += exp_tile[t];
            }
            reduction_shared[tid] = threadSum;
            context.localBarrier();

            for (int stride = localSize / 2; stride > 0; stride /= 2) {
                if (tid < stride) {
                    reduction_shared[tid] += reduction_shared[tid + stride];
                }
                context.localBarrier();
            }
            float tileSum = reduction_shared[0];

            // Update shared state (thread 0)
            if (tid == 0) {
                state_shared[0] = newMax;
                state_shared[1] = state_shared[1] * scale + tileSum;
            }
            context.localBarrier();

            // === Accumulate output (each thread handles its dimensions) ===
            for (int t = 0; t < tileLen; t++) {
                float expScore = exp_tile[t];
                for (int i = 0; i < myDimCount; i++) {
                    int d = myStartDim + i;
                    output[i] += expScore * v_tile[t * headSize + d];
                }
            }
            context.localBarrier();
        }

        // === Final normalization and write ===
        float sumExp = state_shared[1];
        float normFactor = (sumExp > 0.0f) ? (1.0f / sumExp) : 0.0f;

        int baseOffset = h * headSize + myStartDim;
        for (int i = 0; i < myDimCount; i++) {
            xb.set(baseOffset + i, output[i] * normFactor);
        }
    }

    /**
     * Sequential softmax-attention reference. The numerically-stable streaming Flash-Attention kernel above should produce the
     * same result, head-by-head, as this textbook implementation.
     */
    private static void flashAttentionSequential(FloatArray q, FloatArray key_cache, FloatArray value_cache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul, int pos, int layer,
            int contextLength) {
        int loff = layer * contextLength * kvDim;
        for (int h = 0; h < nHeads; h++) {
            int kvHeadIdx = h / kvMul;

            // scores
            float[] scores = new float[pos + 1];
            float max = Float.NEGATIVE_INFINITY;
            for (int t = 0; t <= pos; t++) {
                float score = 0.0f;
                for (int d = 0; d < headSize; d++) {
                    int kvOffset = loff + t * kvDim + kvHeadIdx * headSize + d;
                    score += q.get(h * headSize + d) * key_cache.get(kvOffset);
                }
                score /= (float) Math.sqrt(headSize);
                scores[t] = score;
                if (score > max) {
                    max = score;
                }
            }

            // softmax
            float sum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                scores[t] = (float) Math.exp(scores[t] - max);
                sum += scores[t];
            }
            float norm = (sum > 0.0f) ? (1.0f / sum) : 0.0f;

            // weighted sum of values
            for (int d = 0; d < headSize; d++) {
                float acc = 0.0f;
                for (int t = 0; t <= pos; t++) {
                    int kvOffset = loff + t * kvDim + kvHeadIdx * headSize + d;
                    acc += scores[t] * value_cache.get(kvOffset);
                }
                xb.set(h * headSize + d, acc * norm);
            }
        }
    }

    @Test
    public void testFlashAttention() throws TornadoExecutionPlanException {
        final int nHeads = 4;
        final int headSize = 64;        // <= MAX_HEAD_SIZE (256)
        final int kvMul = 1;            // no grouped-query attention => one KV head per query head
        final int kvDim = nHeads * headSize / kvMul; // 256
        final int layer = 0;
        final int contextLength = 128;
        final int pos = 40;             // current sequence position (spans 2 tiles of 32)
        final int localSize = 32;       // threads per head (work-group size)

        // --- Allocate and seed input/output data ---
        FloatArray q = new FloatArray(nHeads * headSize);
        FloatArray keyCache = new FloatArray(contextLength * kvDim);
        FloatArray valueCache = new FloatArray(contextLength * kvDim);
        FloatArray xb = new FloatArray(nHeads * headSize);
        IntArray positionHolder = new IntArray(1);
        positionHolder.set(0, pos);

        // Deterministic pseudo-random-ish seeding so the result is reproducible.
        for (int i = 0; i < q.getSize(); i++) {
            q.set(i, (float) Math.sin(i * 0.01f) * 0.5f);
        }
        for (int i = 0; i < keyCache.getSize(); i++) {
            keyCache.set(i, (float) Math.cos(i * 0.001f) * 0.5f);
            valueCache.set(i, (float) Math.sin(i * 0.002f) * 0.5f);
        }
        xb.init(0.0f);

        // --- Compute the sequential reference ---
        FloatArray xbReference = new FloatArray(nHeads * headSize);
        xbReference.init(0.0f);
        flashAttentionSequential(q, keyCache, valueCache, xbReference, nHeads, headSize, kvDim, kvMul, pos, layer, contextLength);

        // --- Configure the grid: one work-group per head, localSize threads per group ---
        WorkerGrid worker = new WorkerGrid1D(nHeads * localSize);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, keyCache, valueCache, positionHolder) //
                .task("t0", TestFlashAttentionKernelContext::processHeadsFlashAttentionOptV2, context, q, keyCache, valueCache, xb, nHeads, headSize, kvDim, kvMul, positionHolder, layer,
                        contextLength) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, xb);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // --- Validate against the sequential reference ---
        for (int i = 0; i < xb.getSize(); i++) {
            assertEquals(xbReference.get(i), xb.get(i), 1e-3f);
        }
    }
}
