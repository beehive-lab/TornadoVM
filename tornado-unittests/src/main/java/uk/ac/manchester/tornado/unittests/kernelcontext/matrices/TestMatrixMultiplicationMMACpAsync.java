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

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
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
 * The MMA GEMM of {@link TestMatrixMultiplicationMMA} with the synchronous cooperative
 * tile loads replaced by cp.async global-to-shared copies
 * ({@code KernelContext#asyncCopyToLocal} + commit/wait). Shapes and data patterns
 * mirror the synchronous test so the two paths are directly comparable.
 *
 * <p>CUDA backend only (cp.async is emitted as inline PTX by the CUDA code
 * generator), compute capability 8.0+.</p>
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMACpAsync
 * </code>
 * </p>
 */
public class TestMatrixMultiplicationMMACpAsync extends TornadoTestBase {

    static final int WMMA_M    = 16;
    static final int WMMA_N    = 16;
    static final int WMMA_K    = 16;
    static final int WARP_SIZE = 32;

    /** N of a single m16n8k16 tile, used by the shared fp16 tile tests below. */
    static final int MMA_N  = 8;
    static final float A_VALUE = 0.25f;
    static final float SCALE   = 0.5f;
    static final float BIAS    = 1.0f;

    /**
     * Same GEMM as TestMatrixMultiplicationMMA#gemmMMA, but every packed b32 tile slot
     * is filled by a cp.async copy of the two adjacent fp16 source elements instead of
     * a synchronous load-pack-store, with one commit/wait pair guarding the barrier.
     */
    public static void gemmMMACpAsync(KernelContext ctx,
                                      HalfFloatArray a, HalfFloatArray b, FloatArray c,
                                      int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {

            // Async cooperative load A: each b32 slot is 2 adjacent f16 elements.
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r  = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                ctx.asyncCopyToLocal(aTile, r * (WMMA_K / 2) + kk / 2, a, globalBase);
            }

            // Async cooperative load B: adjacent column pairs per panel.
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int k_row  = idx / 4;
                int j_pair = idx % 4;
                int j_base = j_pair * 2;

                int gLeft = (kBase + k_row) * dimN + tileCol + j_base;
                ctx.asyncCopyToLocal(bTile0, k_row * 4 + j_pair, b, gLeft);

                int gRight = (kBase + k_row) * dimN + tileCol + 8 + j_base;
                ctx.asyncCopyToLocal(bTile1, k_row * 4 + j_pair, b, gRight);
            }

            ctx.asyncCopyCommit();
            ctx.asyncCopyWaitGroup(0);
            ctx.localBarrier();

            HalfFloat[] fragA  = ctx.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);
            fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, WMMA_K);
            fragC1 = ctx.mma(fragA, fragB1, fragC1, MMAShape.M16N8K16);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // -----------------------------------------------------------------------
    // CPU reference
    // -----------------------------------------------------------------------

    private static void gemmReference(
            HalfFloatArray a, HalfFloatArray b, FloatArray ref,
            int dimM, int dimN, int dimK) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += a.get(i * dimK + k).getFloat32()
                            * b.get(k * dimN + j).getFloat32();
                }
                ref.set(i * dimN + j, sum);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Tests (same shape set as the synchronous MMA test)
    // -----------------------------------------------------------------------

    @Test
    public void testGemmCpAsyncMinimal() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 16);
    }

    @Test
    public void testGemmCpAsyncDeepK() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 64);
    }

    @Test
    public void testGemmCpAsyncMultiTile() throws TornadoExecutionPlanException {
        runGemmTest(32, 32, 64);
    }

    @Test
    public void testGemmCpAsyncWideN() throws TornadoExecutionPlanException {
        runGemmTest(16, 64, 64);
    }

    @Test
    public void testGemmCpAsyncPrefillSlice() throws TornadoExecutionPlanException {
        runGemmTest(64, 64, 64);
    }

    /** Identity B: catches any tile-slot misplacement introduced by the async copies. */
    @Test
    public void testGemmCpAsyncIdentityB() throws TornadoExecutionPlanException {
        assumeCpAsyncSupported();

        int M = 16, N = 16, K = 16;

        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int i = 0; i < M; i++) {
            for (int k = 0; k < K; k++) {
                a.set(i * K + k, new HalfFloat((float) (i * 16 + k)));
            }
        }

        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, new HalfFloat(k == j ? 1.0f : 0.0f));
            }
        }

        FloatArray c = new FloatArray(M * N);
        FloatArray ref = new FloatArray(M * N);
        gemmReference(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K);
    }

    // -----------------------------------------------------------------------
    // Runner
    // -----------------------------------------------------------------------

    private void assumeCpAsyncSupported() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    private void runGemmTest(int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        assumeCpAsyncSupported();

        HalfFloatArray a = randomFP16(dimM * dimK);
        HalfFloatArray b = randomFP16(dimK * dimN);
        FloatArray c = new FloatArray(dimM * dimN);

        FloatArray ref = new FloatArray(dimM * dimN);
        gemmReference(a, b, ref, dimM, dimN, dimK);

        runGemmTestWithData(a, b, c, ref, dimM, dimN, dimK);
    }

    private void runGemmTestWithData(HalfFloatArray a, HalfFloatArray b, FloatArray c, FloatArray ref,
                                     int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        int numWarps   = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        int localSize  = WARP_SIZE;

        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(localSize, 1, 1);

        GridScheduler gridScheduler = new GridScheduler("mma_cpasync_test.gemm", workerGrid);

        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_cpasync_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMACpAsync::gemmMMACpAsync,
                        ctx, a, b, c, dimM, dimN, dimK)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(gridScheduler)
                    .execute();
        }

        float tol = 0.01f;
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
                assertEquals(String.format("C[%d][%d]", i, j),
                        ref.get(idx), c.get(idx), tol);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shared fp16 tile: cuda_fp16.h include selection
    // -----------------------------------------------------------------------

    /**
     * A shared fp16 tile filled from a run-time value and consumed only through
     * ldmatrix/mma, so the kernel never materialises a {@code __half} register and its
     * only fp16 constructs are the {@code __shared__} tile declaration and the
     * {@code ((__half *) tile)[...]} swizzled store. The CUDA backend has to recognise
     * those and emit {@code #include <cuda_fp16.h>}; when the include scan only looked
     * for the {@code __half} / {@code half2} / {@code 2half} spellings this kernel
     * reached NVRTC without the header and failed to compile with
     * {@code identifier "half" is undefined}.
     *
     * <p>The stored value must be computed at run time: a compile-time constant is
     * folded into {@code __float2half(...)}, which the scan already matched and which
     * therefore hides the defect.
     */
    public static void sharedHalfTileNoFp16Spelling(KernelContext ctx, HalfFloatArray a,
                                                    FloatArray scale, FloatArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        HalfFloat[] bTile = ctx.allocateHalfFloatLocalArray(MMA_N * WMMA_K);

        // A is filled by cp.async, so no fp16 element is ever read into a register.
        for (int slot = 0; slot < 4; slot++) {
            int i = lane + slot * WARP_SIZE;
            int row = i >>> 3;
            int kk = (i & 7) << 1;
            ctx.asyncCopyToLocal(aTile, i, a, row * WMMA_K + kk);
        }

        for (int t = 0; t < 4; t++) {
            int row = (lane & 3) * 4 + t;
            int col = lane >> 2;
            ctx.mmaStoreBSwizzled(bTile, row, col, MMA_N, new HalfFloat(scale.get(0) + t), 0);
        }

        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        float[] acc = ctx.mmaFragment(0.0f);
        acc = ctx.mma(ctx.mmaLoadA(aTile, WMMA_K, 0), ctx.mmaLoadBSwizzled(bTile, WMMA_K, 0), acc,
                MMAShape.M16N8K16);
        ctx.mmaStore(acc, out, 0, 0, MMA_N);
    }

    /**
     * {@link #sharedHalfTileNoFp16Spelling} plus one half-to-float conversion, which
     * emits {@code __half2float} and so trips the include scan on its own. This is the
     * path that already worked; it is kept next to the probe so a future change to the
     * scan cannot fix one case by breaking the other.
     */
    public static void sharedHalfTileWithFp16Spelling(KernelContext ctx, HalfFloatArray a,
                                                      FloatArray scale, HalfFloatArray bias,
                                                      FloatArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        HalfFloat[] bTile = ctx.allocateHalfFloatLocalArray(MMA_N * WMMA_K);

        for (int slot = 0; slot < 4; slot++) {
            int i = lane + slot * WARP_SIZE;
            int row = i >>> 3;
            int kk = (i & 7) << 1;
            ctx.asyncCopyToLocal(aTile, i, a, row * WMMA_K + kk);
        }

        for (int t = 0; t < 4; t++) {
            int row = (lane & 3) * 4 + t;
            int col = lane >> 2;
            ctx.mmaStoreBSwizzled(bTile, row, col, MMA_N, new HalfFloat(scale.get(0) + t), 0);
        }

        ctx.asyncCopyCommit();
        ctx.asyncCopyWaitGroup(0);
        ctx.localBarrier();

        float[] acc = ctx.mmaFragment(0.0f);
        acc = ctx.mma(ctx.mmaLoadA(aTile, WMMA_K, 0), ctx.mmaLoadBSwizzled(bTile, WMMA_K, 0), acc,
                MMAShape.M16N8K16);
        ctx.mmaStore(acc, out, 0, 0, MMA_N);
        if (lane == 0) {
            out.set(0, out.get(0) + bias.get(0).getFloat32());
        }
    }

    @Test
    public void testSharedHalfTileWithoutFp16Spelling() throws TornadoExecutionPlanException {
        FloatArray out = runSharedHalfTile(false);
        for (int i = 0; i < WMMA_M * MMA_N; i++) {
            assertEquals("out[" + i + "]", sharedHalfTileReference(), out.get(i), 0.01f);
        }
    }

    @Test
    public void testSharedHalfTileWithFp16Spelling() throws TornadoExecutionPlanException {
        FloatArray out = runSharedHalfTile(true);
        assertEquals("out[0]", sharedHalfTileReference() + BIAS, out.get(0), 0.01f);
        for (int i = 1; i < WMMA_M * MMA_N; i++) {
            assertEquals("out[" + i + "]", sharedHalfTileReference(), out.get(i), 0.01f);
        }
    }

    /**
     * Host oracle for both kernels: A is {@value #A_VALUE} everywhere and the B tile
     * holds {@code SCALE + (k % 4)} in every column, so every output element is the
     * same dot product over K.
     */
    private static float sharedHalfTileReference() {
        float sum = 0.0f;
        for (int k = 0; k < WMMA_K; k++) {
            sum += A_VALUE * (SCALE + (k % 4));
        }
        return sum;
    }

    private FloatArray runSharedHalfTile(boolean withFp16Spelling) throws TornadoExecutionPlanException {
        assumeCpAsyncSupported();

        HalfFloatArray a = new HalfFloatArray(WMMA_M * WMMA_K);
        for (int i = 0; i < WMMA_M * WMMA_K; i++) {
            a.set(i, new HalfFloat(A_VALUE));
        }
        HalfFloatArray bias = new HalfFloatArray(1);
        bias.set(0, new HalfFloat(BIAS));
        FloatArray scale = new FloatArray(1);
        scale.set(0, SCALE);
        FloatArray out = new FloatArray(WMMA_M * MMA_N);
        out.init(0.0f);

        WorkerGrid1D workerGrid = new WorkerGrid1D(WARP_SIZE);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("half_tile_test.mma", workerGrid);

        KernelContext ctx = new KernelContext();
        TaskGraph tg = new TaskGraph("half_tile_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, bias, scale, out);
        if (withFp16Spelling) {
            tg.task("mma", TestMatrixMultiplicationMMACpAsync::sharedHalfTileWithFp16Spelling,
                    ctx, a, scale, bias, out);
        } else {
            tg.task("mma", TestMatrixMultiplicationMMACpAsync::sharedHalfTileNoFp16Spelling,
                    ctx, a, scale, out);
        }
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }
        return out;
    }

    private static HalfFloatArray randomFP16(int size) {
        HalfFloatArray arr = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            arr.set(i, new HalfFloat((float) (Math.random() * 2.0 - 1.0)));
        }
        return arr;
    }
}
