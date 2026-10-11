/*
 * Copyright (c) 2026 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.kernelcontext.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests the m16n8k16 fragment loads straight from global memory,
 * {@link KernelContext#mmaLoadA(HalfFloatArray, int, int, int)} and
 * {@link KernelContext#mmaLoadB(HalfFloatArray, int, int, int)}, against a CPU GEMM over the
 * same FP16-rounded inputs.
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMMAGlobalFragmentLoad
 * </code>
 * </p>
 */
public class TestMMAGlobalFragmentLoad extends TornadoTestBase {

    private static final int TILE = 16;
    private static final int WARP_SIZE = 32;

    /**
     * C = A * B with row-major A (M x K), B (K x N) and C (M x N). Each warp computes one 16x16
     * tile of C from fragments it loads itself: no shared memory and no barrier, so the warps of a
     * work-group are independent and the local size can be any multiple of 32.
     */
    public static void gemmGlobal(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimM, int dimN, int dimK) {
        int warpId = ctx.globalIdx / WARP_SIZE;
        int tilesN = dimN / TILE;
        int tileRow = (warpId / tilesN) * TILE;
        int tileCol = (warpId % tilesN) * TILE;
        float[] accLeft = ctx.mmaFragment(0.0f);
        float[] accRight = ctx.mmaFragment(0.0f);
        for (int k = 0; k < dimK; k += TILE) {
            HalfFloat[] fragA = ctx.mmaLoadA(a, tileRow, k, dimK);
            HalfFloat[] fragBLeft = ctx.mmaLoadB(b, k, tileCol, dimN);
            HalfFloat[] fragBRight = ctx.mmaLoadB(b, k, tileCol + 8, dimN);
            accLeft = ctx.mma(fragA, fragBLeft, accLeft, MMAShape.M16N8K16);
            accRight = ctx.mma(fragA, fragBRight, accRight, MMAShape.M16N8K16);
        }
        ctx.mmaStore(accLeft, c, tileRow, tileCol, dimN);
        ctx.mmaStore(accRight, c, tileRow, tileCol + 8, dimN);
    }

    /**
     * Global A fragment combined with a B fragment loaded through shared memory, to check that both
     * paths agree on the fragment layout. Single 16x8 output tile, K = 16.
     */
    public static void gemmMixed(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimN) {
        int lane = ctx.localIdx;
        int[] bTile = ctx.allocateIntLocalArray(64);
        for (int idx = lane; idx < 64; idx += WARP_SIZE) {
            int kRow = idx / 4;
            int g = kRow * dimN + (idx % 4) * 2;
            bTile[idx] = (b.get(g).getHalfFloatValue() & 0xFFFF) | ((b.get(g + 1).getHalfFloatValue() & 0xFFFF) << 16);
        }
        ctx.localBarrier();
        float[] acc = ctx.mmaFragment(0.0f);
        HalfFloat[] fragA = ctx.mmaLoadA(a, 0, 0, TILE);
        HalfFloat[] fragB = ctx.mmaLoadB(bTile, TILE);
        acc = ctx.mma(fragA, fragB, acc, MMAShape.M16N8K16);
        ctx.mmaStore(acc, c, 0, 0, dimN);
    }

    private static HalfFloatArray randomFP16(int size, long seed) {
        Random random = new Random(seed);
        HalfFloatArray array = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(random.nextFloat() * 2.0f - 1.0f));
        }
        return array;
    }

    private static void gemmReference(HalfFloatArray a, HalfFloatArray b, float[] ref, int dimM, int dimN, int dimK, int ldb) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += a.get(i * dimK + k).getFloat32() * b.get(k * ldb + j).getFloat32();
                }
                ref[i * dimN + j] = sum;
            }
        }
    }

    private static void check(float[] ref, FloatArray c, int dimM, int dimN, int ldc) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                assertEquals(String.format("C[%d][%d]", i, j), ref[i * dimN + j], c.get(i * ldc + j), 1e-2f);
            }
        }
    }

    private void runGemm(int dimM, int dimN, int dimK, int localSize) throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        HalfFloatArray a = randomFP16(dimM * dimK, 7);
        HalfFloatArray b = randomFP16(dimK * dimN, 11);
        FloatArray c = new FloatArray(dimM * dimN);
        float[] ref = new float[dimM * dimN];
        gemmReference(a, b, ref, dimM, dimN, dimK, dimN);

        WorkerGrid1D worker = new WorkerGrid1D((dimM / TILE) * (dimN / TILE) * WARP_SIZE);
        worker.setLocalWork(localSize, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        KernelContext ctx = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMMAGlobalFragmentLoad::gemmGlobal, ctx, a, b, c, dimM, dimN, dimK) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(scheduler).execute();
        }
        check(ref, c, dimM, dimN, dimN);
    }

    @Test
    public void testSingleTile() throws TornadoExecutionPlanException {
        runGemm(16, 16, 16, WARP_SIZE);
    }

    @Test
    public void testDeepK() throws TornadoExecutionPlanException {
        runGemm(16, 16, 128, WARP_SIZE);
    }

    @Test
    public void testRectangular() throws TornadoExecutionPlanException {
        runGemm(48, 32, 80, WARP_SIZE);
    }

    @Test
    public void testMultiTile() throws TornadoExecutionPlanException {
        runGemm(128, 128, 128, WARP_SIZE);
    }

    /**
     * Four independent warps per work-group: the loads need no barrier, so a larger work-group
     * computes the same result.
     */
    @Test
    public void testFourWarpsPerGroup() throws TornadoExecutionPlanException {
        runGemm(128, 128, 64, 4 * WARP_SIZE);
    }

    @Test
    public void testMixedGlobalAndSharedFragments() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        final int dimN = 8;
        HalfFloatArray a = randomFP16(TILE * TILE, 3);
        HalfFloatArray b = randomFP16(TILE * dimN, 5);
        FloatArray c = new FloatArray(TILE * dimN);
        float[] ref = new float[TILE * dimN];
        gemmReference(a, b, ref, TILE, dimN, TILE, dimN);

        WorkerGrid1D worker = new WorkerGrid1D(WARP_SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        KernelContext ctx = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMMAGlobalFragmentLoad::gemmMixed, ctx, a, b, c, dimN) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(scheduler).execute();
        }
        check(ref, c, TILE, dimN, dimN);
    }
}
