/*
 * Copyright (c) 2020, 2022 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.codegen;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.codegen.CodeGenTest
 * </code>
 * </p>
 */
public class CodeGenTest extends TornadoTestBase {

    public static void cascadeKernel(IntArray grayIntegralImage, int imageWidth, int imageHeight, IntArray resultsXY) {
        for (@Parallel int y = 0; y < imageHeight; y++) {
            for (@Parallel int x = 0; x < imageWidth; x++) {
                int gradient = grayIntegralImage.get((y * imageWidth) + x);
            }
        }
    }

    public static void badCascadeKernel2() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; (stillLooksLikeAFace || (stage < 100)); stage++) {
                for (int t = 0; t < id; t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    public static void badCascadeKernel3() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; (stillLooksLikeAFace || (stage < 100)); stage++) {
                for (int t = 0; stillLooksLikeAFace && (t < id); t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    public static void badCascadeKernel4() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; stillLooksLikeAFace && (stage < id); stage++) {
                for (int t = 0; t < id; t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    /*
     * The following test is not intended to execute in parallel. This test shows
     * more complex control flow, in which there is an exit block followed by a
     * merge to represent the break in the first if-condition.
     *
     */
    private static void breakStatement(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            if (a.get(i) == 5) {
                break;
            }
            a.set(i, a.get(i) + 5);
        }
        a.set(0, 0);
    }

    public static void testLocalMemoryAllocation(KernelContext context, int localWorkGroupSize) {
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Allocate local memory
        float[] localArray = context.allocateFloatLocalArray(localWorkGroupSize);

        // Initialize local memory with threadId (for testing purposes)
        localArray[threadId] = threadId;

        // Synchronize threads
        context.localBarrier();

        // Simple operation to validate memory access
        if (threadId == 0) {
            float sum = 0.0f;
            for (int i = 0; i < blockDim; i++) {
                sum += localArray[i];
            }
        }

        // Synchronize again before exiting
        context.localBarrier();
    }

    public static void processHeadsFlashAttention(KernelContext context, FloatArray q, FloatArray key_cache, FloatArray value_cache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul,
            IntArray positionHolder, int layer, int contextLength) {

        // Thread and workgroup information
        int tid = context.localIdx;
        int gid = context.globalIdx; // gid is not actively used in the core logic here
        int h = context.groupIdx;  // Each workgroup processes one head
        int localSize = context.localGroupSizeX;

        // Early exit if this workgroup is beyond our head count
        // This relies on the kernel being launched with nHeads workgroups.
        if (h >= nHeads)
            return;

        int pos = positionHolder.get(0);
        int loff = layer * contextLength * kvDim;
        int kvHeadIdx = h / kvMul;
        int BLOCK_SIZE_C = 4;

        // Allocate shared memory for tiled computation
        float[] q_shared = context.allocateFloatLocalArray(headSize);
        float[] k_tile = context.allocateFloatLocalArray(BLOCK_SIZE_C * headSize);
        float[] v_tile = context.allocateFloatLocalArray(BLOCK_SIZE_C * headSize);
        float[] s_tile = context.allocateFloatLocalArray(BLOCK_SIZE_C);
        float[] shared_tile_max_holder = context.allocateFloatLocalArray(1); // FIX: For broadcasting tile max

        // Thread-local accumulators for online softmax
        float maxScore = Float.NEGATIVE_INFINITY;
        float sumExp = 0.0f;

        // Thread-local output accumulation
        float[] output = new float[headSize];
        for (int i = 0; i < headSize; i++) {
            output[i] = 0.0f;
        }

        // Load query vector into shared memory
        for (int i = tid; i < headSize; i += localSize) {
            q_shared[i] = q.get(h * headSize + i);
        }

        context.localBarrier();

        // Process sequence in tiles
        for (int tileC = 0; tileC <= pos; tileC += BLOCK_SIZE_C) {
            int tileEnd = Math.min(tileC + BLOCK_SIZE_C - 1, pos);

            // Load key and value vectors for this tile
            // Each thread loads a portion of the K and V vectors for the tile
            for (int tIdxInSeq = tileC + tid; tIdxInSeq <= tileEnd; tIdxInSeq += localSize) {
                int k_v_idx_in_tile = tIdxInSeq - tileC; // 0, 1, 2, or 3 for this tile
                int tileMemOffset = k_v_idx_in_tile * headSize;
                for (int d = 0; d < headSize; d++) {
                    int kvCacheAbsolutePos = tIdxInSeq;
                    int kvOffset = loff + kvCacheAbsolutePos * kvDim + kvHeadIdx * headSize + d;
                    k_tile[tileMemOffset + d] = key_cache.get(kvOffset);
                    v_tile[tileMemOffset + d] = value_cache.get(kvOffset);
                }
            }

            context.localBarrier();

            // Compute attention scores for this tile
            // Each thread computes one score for the tile
            for (int tIdxInSeq = tileC + tid; tIdxInSeq <= tileEnd; tIdxInSeq += localSize) {
                int score_idx_in_tile = tIdxInSeq - tileC; // 0, 1, 2, or 3 for this tile

                float score = 0.0f;
                for (int d = 0; d < headSize; d++) {
                    score += q_shared[d] * k_tile[score_idx_in_tile * headSize + d];
                }
                score /= TornadoMath.sqrt(headSize);
                s_tile[score_idx_in_tile] = score;
            }

            context.localBarrier();

            // Find max score in this tile (all threads compute it redundantly over the small s_tile)
            float tileLocalMax = Float.NEGATIVE_INFINITY;
            for (int i = 0; i <= tileEnd - tileC; i++) { // Iterate over valid scores in s_tile
                if (s_tile[i] > tileLocalMax) {
                    tileLocalMax = s_tile[i];
                }
            }

            // Broadcast max to all threads via shared memory
            if (tid == 0) {
                shared_tile_max_holder[0] = tileLocalMax; // FIX: Use dedicated holder
            }
            context.localBarrier();
            float currentTileMax = shared_tile_max_holder[0]; // FIX: Read from dedicated holder

            // Determine if we need to rescale previous results
            float newMax = Math.max(maxScore, currentTileMax);
            if (newMax != maxScore && maxScore != Float.NEGATIVE_INFINITY) {
                float scale = TornadoMath.exp(maxScore - newMax);
                sumExp *= scale;
                for (int d = 0; d < headSize; d++) {
                    output[d] *= scale;
                }
            }
            maxScore = newMax;

            // Process each key-value pair using original scores from s_tile
            // All threads iterate over all scores in the current tile
            for (int t_idx_in_s_tile = 0; t_idx_in_s_tile <= tileEnd - tileC; t_idx_in_s_tile++) {
                // s_tile[t_idx_in_s_tile] now correctly refers to the original score
                float expScore = TornadoMath.exp(s_tile[t_idx_in_s_tile] - maxScore);
                sumExp += expScore;

                for (int d = 0; d < headSize; d++) {
                    output[d] += expScore * v_tile[t_idx_in_s_tile * headSize + d];
                }
            }
            context.localBarrier(); // Ensure all threads finish with s_tile, k_tile, v_tile before next tile load
        }

        // Normalize and write final results
        float normFactor = (sumExp > 0.0f) ? (1.0f / sumExp) : 0.0f; // Avoid division by zero, return 0 if sumExp is 0
        for (int d = tid; d < headSize; d += localSize) {
            xb.set(h * headSize + d, output[d] * normFactor);
        }
    }

    @Test
    public void test01() throws TornadoExecutionPlanException {

        TaskGraph taskGraph = new TaskGraph("foo");

        int imageWidth = 512;
        int imageHeight = 512;
        IntArray grayIntegralImage = new IntArray(imageHeight * imageWidth);
        IntArray resultsXY = new IntArray(imageHeight * imageWidth);

        IntStream.range(0, imageHeight * imageHeight).forEach(x -> grayIntegralImage.set(x, x));

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, grayIntegralImage) //
                .task("bar", CodeGenTest::cascadeKernel, grayIntegralImage, imageWidth, imageHeight, resultsXY) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultsXY);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    private boolean isRunningOnCPU() {
        TornadoDevice device = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(0);
        return device.getDeviceType() == TornadoDeviceType.CPU;
    }

    @Test
    public void test02() throws TornadoExecutionPlanException {
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGenTest::badCascadeKernel2);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation();
        }
    }

    @Test
    @Ignore
    public void test03() throws TornadoExecutionPlanException {
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGenTest::badCascadeKernel3);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation();
        }
    }

    @Test
    public void test04() throws TornadoExecutionPlanException {
        assertNotBackendOptimization(TornadoVMBackendType.SPIRV);
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGenTest::badCascadeKernel4);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation();
        }
    }

    @Test
    public void test05() throws TornadoExecutionPlanException {
        final int size = 8192;
        IntArray a = new IntArray(size);
        a.init(10);
        a.set(12, 5);
        IntArray serial = new IntArray(size);
        serial.init(10);
        serial.set(12, 5);

        breakStatement(serial);

        TaskGraph taskGraph = new TaskGraph("break") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("task", CodeGenTest::breakStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(serial.get(i), a.get(i));
        }
    }

    @Test
    public void test06() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        int localWorkGroupSize = 256;

        TaskGraph taskGraph = new TaskGraph("localMemoryAllocation") //
                .task("task", CodeGenTest::testLocalMemoryAllocation, context, localWorkGroupSize);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    @Test
    public void testFlashAttention() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int dim = 2048;
        final int nHeads = 32;
        final int headSize = 64;
        final int numKVHeads = 8;
        final int kvMul = nHeads / numKVHeads; // Grouped Query Attention factor
        final int kvDim = headSize * numKVHeads;
        final int seqLen = 128;
        final int pos = 16;       // Current sequence position
        final int layer = 0;

        // Initialize input and output data
        FloatArray query = new FloatArray(dim);
        FloatArray keyCache = new FloatArray(seqLen * kvDim);
        FloatArray valueCache = new FloatArray(seqLen * kvDim);
        FloatArray output = new FloatArray(dim);
        FloatArray attentionWeights = new FloatArray(nHeads * (pos + 1));
        IntArray positionAndLayer = new IntArray(2); // Store position and layer indices

        // Initialize query vector with test values
        for (int i = 0; i < dim; i++) {
            query.set(i, 0.01f * i);
        }

        // Initialize key and value caches
        for (int i = 0; i < seqLen * kvDim; i++) {
            keyCache.set(i, 0.005f * i);
            valueCache.set(i, 0.005f * i);
        }

        // Clear output buffer
        for (int i = 0; i < dim; i++) {
            output.set(i, 0.0f);
        }

        // Set position and layer indices
        positionAndLayer.set(0, pos);
        positionAndLayer.set(1, layer);

        WorkerGrid parallelAttentionWorker = new WorkerGrid1D(nHeads);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", parallelAttentionWorker);
        parallelAttentionWorker.setGlobalWork(nHeads * 4, 1, 1);
        parallelAttentionWorker.setLocalWork(4, 1, 1);

        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, query, keyCache, valueCache, output, attentionWeights, positionAndLayer).task("t0",
                CodeGenTest::processHeadsFlashAttention, context, query, keyCache, valueCache, output, nHeads, headSize, kvDim, kvMul, positionAndLayer, 0, 512).transferToHost(
                        DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

    }

}
