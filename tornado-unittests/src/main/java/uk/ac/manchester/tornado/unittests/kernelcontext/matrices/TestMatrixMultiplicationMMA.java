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

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;

public class TestMatrixMultiplicationMMA extends TornadoTestBase {
    static final int WMMA_M  = 16;
    static final int WMMA_N  = 16;  // covered by two m16n8k16 calls
    static final int WMMA_K  = 16;
    static final int WARP_SIZE = 32;

    // -----------------------------------------------------------------------
    // Kernel
    // -----------------------------------------------------------------------

    /**
     * GEMM: C[M x N] = A[M x K] * B[K x N]
     *   A : HalfFloatArray, row-major
     *   B : HalfFloatArray, row-major in global memory,
     *       transposed to col-major per 8-column panel in shared memory
     *   C : FloatArray, row-major, f32 accumulator
     *
     * One workgroup = one warp (32 threads) = one 16x16 output tile.
     * Each 16x16 tile requires two mma.sync calls (left + right 16x8 panels).
     *
     * Launch config:
     *   localSize  = 32
     *   globalSize = (M/16) * (N/16) * 32
     */
    public static void gemmMMA(KernelContext ctx,
                                  HalfFloatArray a, HalfFloatArray b, FloatArray c,
                                  int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        // Packed shared tiles: 2 f16 per int = b16 pairs
        // A: 16 rows × 8 packed ints = 128 (was 256 floats)
        // B: 8 cols × 8 packed ints = 64 per panel (was 128 floats)
        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {

            // Cooperative load A: pack 2 adjacent f16 as b16 pair into one int
            // Each thread handles 2 f16 elements per iteration
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r  = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
                int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
                aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int k_row  = idx / 4;        // 0..15
                int j_pair = idx % 4;        // 0..3 (which pair of j: 0,1 / 2,3 / 4,5 / 6,7)
                int j_base = j_pair * 2;

                int gL0 = (kBase + k_row) * dimN + tileCol + j_base;
                int gL1 = (kBase + k_row) * dimN + tileCol + j_base + 1;
                int lo_left = b.get(gL0).getHalfFloatValue() & 0xFFFF;
                int hi_left = b.get(gL1).getHalfFloatValue() & 0xFFFF;
                bTile0[k_row * 4 + j_pair] = lo_left | (hi_left << 16);

                int gR0 = (kBase + k_row) * dimN + tileCol + 8 + j_base;
                int gR1 = (kBase + k_row) * dimN + tileCol + 8 + j_base + 1;
                int lo_right = b.get(gR0).getHalfFloatValue() & 0xFFFF;
                int hi_right = b.get(gR1).getHalfFloatValue() & 0xFFFF;
                bTile1[k_row * 4 + j_pair] = lo_right | (hi_right << 16);
            }
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


    public static void gemmMMASwizzled(KernelContext ctx,
                                       HalfFloatArray a, HalfFloatArray b, FloatArray c,
                                       int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        // A: same int-packed layout as gemmMMA (unchanged for this test)
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);

        // B: native fp16, swizzled. Two panels, each 8 rows x 16 cols of fp16 = 128 elements.
        HalfFloat[] bTile0 = ctx.allocateHalfFloatLocalArray(8 * 16);
        HalfFloat[] bTile1 = ctx.allocateHalfFloatLocalArray(8 * 16);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {

            // Cooperative load A (unchanged)
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r  = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
                int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
                aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
            }

            for (int idx = lane; idx < 128; idx += WARP_SIZE) {
                int k_row = idx / 8;     // 0..15
                int j     = idx % 8;     // 0..7

                HalfFloat valL = b.get((kBase + k_row) * dimN + tileCol + j);
                HalfFloat valR = b.get((kBase + k_row) * dimN + tileCol + 8 + j);

                ctx.swizzleStoreFp16Stride32(bTile0, k_row, j, 8, valL);
                ctx.swizzleStoreFp16Stride32(bTile1, k_row, j, 8, valR);
            }

            ctx.localBarrier();

            HalfFloat[] fragA  = ctx.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = ctx.mmaLoadBSwizzled(bTile0, WMMA_K);
            fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadBSwizzled(bTile1, WMMA_K);
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
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Minimum case: M=16, N=16, K=16.
     * One warp, two mma.sync calls (left + right panel), one K iteration.
     */
    @Test
    public void testGemmMinimal() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 16);
    }

    /**
     * K=64: four K-iterations → four mma.sync pairs per warp.
     * Representative of a transformer inner loop chunk.
     */
    @Test
    public void testGemmDeepK() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 64);
    }

    /**
     * Four output tiles (2×2 grid of warps).
     * Exercises tile coordinate arithmetic: tileRow/tileCol computation.
     */
    @Test
    public void testGemmMultiTile() throws TornadoExecutionPlanException {
        runGemmTest(32, 32, 64);
    }

    /**
     * Wide N: one row of tiles, four warp columns.
     * Representative of a single prefill token row against a 64-wide
     * output projection slice.
     */
    @Test
    public void testGemmWideN() throws TornadoExecutionPlanException {
        runGemmTest(16, 64, 64);
    }

    /**
     * Larger M: multiple seqLen rows, all four projection columns.
     * Closest to a real prefill workload slice.
     */
    @Test
    public void testGemmPrefillSlice() throws TornadoExecutionPlanException {
        runGemmTest(64, 64, 64);
    }

    // -----------------------------------------------------------------------
    // Runner
    // -----------------------------------------------------------------------
    private void runGemmTest(int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        HalfFloatArray a = randomFP16(dimK * dimN); //allOnesFP16(dimM * dimK);
        HalfFloatArray b = randomFP16(dimK * dimN);
        FloatArray c     = new FloatArray(dimM * dimN);

        FloatArray ref = new FloatArray(dimM * dimN);
        gemmReference(a, b, ref, dimM, dimN, dimK);

        int numWarps   = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        int localSize  = WARP_SIZE;                  // one warp per workgroup

        // WorkerGrid1D takes global size only; local size set separately
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(localSize, 1, 1);

        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);

        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA,
                        ctx, a, b, c, dimM, dimN, dimK)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(gridScheduler)
                    .execute();
        }

        float tol = 0.01f;//1e-2f * dimK;
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
               // System.out.println("C[" + i + "][" + j + "] = (expected): " + ref.get(idx) + " - (actual): " + c.get(idx));
                assertEquals(String.format("C[%d][%d]", i, j),
                        ref.get(idx), c.get(idx), tol);
            }
        }
    }

    @Test
    public void testGemmDistinctK() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A[i][k] = k + 1   (i.e., row is irrelevant, A is the same in every row;
        //                    column k has value k+1, so values 1..16)
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int i = 0; i < M; i++) {
            for (int k = 0; k < K; k++) {
                a.set(i * K + k, new HalfFloat((float)(k + 1)));
            }
        }

        // B[k][j] = 1.0 everywhere
        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int idx = 0; idx < K * N; idx++) {
            b.set(idx, new HalfFloat(1.0f));
        }

        FloatArray c = new FloatArray(M * N);

        // Expected: C[i][j] = sum_k A[i][k] * B[k][j] = sum_k (k+1) * 1 = 1+2+...+16 = 136
        // for EVERY i, j.

        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float expected = 136.0f;
        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    @Test
    public void testGemmKMappingProbe() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int i = 0; i < M; i++)
            for (int k = 0; k < K; k++)
                a.set(i * K + k, new HalfFloat((float)(k + 1)));

        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++)
            for (int j = 0; j < N; j++)
                b.set(k * N + j, new HalfFloat((float)((k + 1) * 10)));

        FloatArray c = new FloatArray(M * N);


        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float expected = 14960.0f;
        final float tol = 0.1f;  // larger absolute tolerance for larger values
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    @Test
    public void testGemmDistinctKInB() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A all ones
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int idx = 0; idx < M * K; idx++) {
            a.set(idx, new HalfFloat(1.0f));
        }

        // B[k][j] = k + 1 (every column has values 1..16 going down)
        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, new HalfFloat((float)(k + 1)));
            }
        }

        FloatArray c = new FloatArray(M * N);

        // Expected: C[i][j] = sum_k 1 * (k+1) = 136 everywhere

        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float expected = 136.0f;
        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    @Test
    public void testGemmIdentityB() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A[i][k] = i * 16 + k  → each element is its flat row-major index, 0..255
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int i = 0; i < M; i++) {
            for (int k = 0; k < K; k++) {
                a.set(i * K + k, new HalfFloat((float)(i * 16 + k)));
            }
        }

        // B = identity matrix
        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, new HalfFloat(k == j ? 1.0f : 0.0f));
            }
        }

        FloatArray c = new FloatArray(M * N);

        // C should equal A: C[i][j] = sum_k A[i][k] * B[k][j] = A[i][j]
        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float expected = (float)(i * 16 + j);
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    @Test
    public void testGemmDistinctAB() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A[i][k] = k + 1   (same as distinct-K test: A is constant across rows,
        //                    varies across k from 1..16)
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int i = 0; i < M; i++) {
            for (int k = 0; k < K; k++) {
                a.set(i * K + k, new HalfFloat((float)(k + 1)));
            }
        }

        // B[k][j] = k + 1   (B is constant across columns, varies across k from 1..16)
        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, new HalfFloat((float)(k + 1)));
            }
        }

        FloatArray c = new FloatArray(M * N);

        // Expected: C[i][j] = sum_k (k+1)(k+1) = 1+4+9+16+...+256 = 1496 everywhere

        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float expected = 1496.0f;
        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    @Test
    public void testGemmBLayoutProbe() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A = all ones (so the kernel just sums B[k][j] over k)
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int idx = 0; idx < M * K; idx++) a.set(idx, new HalfFloat(1.0f));

        // B[k][j] = k * 10 + j
        // Each k is in the tens digit, each j is in the units digit.
        // sum_k B[k][j] = 10*(0+1+...+15) + 16*j = 1200 + 16*j
        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++)
            for (int j = 0; j < N; j++)
                b.set(k * N + j, new HalfFloat((float)(k * 10 + j)));

        FloatArray c = new FloatArray(M * N);

        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        // Assertion: C[i][j] = sum_{k=0..15} (k*10 + j) = 10*120 + 16*j = 1200 + 16*j.
        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float expected = 1200.0f + 16.0f * j;
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    @Test
    public void testGemmSwizzledIdentityB() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A = identity
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < K; j++) {
                a.set(k * K + j, new HalfFloat(k == j ? 1.0f : 0.0f));
            }
        }

        // B[i][j] = i*16 + j (recognizable row-major index pattern)
        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
               // b.set(k * N + j, new HalfFloat((float)(k * 16 + j)));
                b.set(k * N + j, new HalfFloat((float)(100 * k + j)));
            }
        }

        FloatArray c = new FloatArray(M * N);

        int numWarps = (M / WMMA_M) * (N / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_swizzled_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_swizzled_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMASwizzled, ctx, a, b, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        // Verify
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                int idx = i * N + j;
                float expected = (float)(100 * i + j);
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(idx), 0.01f);
            }
        }
    }

    public static void swizzleRoundTripKernel(KernelContext ctx,
                                              HalfFloatArray in,
                                              FloatArray out) {
        int lane = ctx.localIdx;

        HalfFloat[] tile = ctx.allocateHalfFloatLocalArray(8 * 16);

        // Each lane writes 4 elements
        for (int idx = lane; idx < 128; idx += WARP_SIZE) {
            int r    = idx / 16;
            int slot = idx % 16;
            ctx.swizzleStoreFp16Stride32(tile, r, slot, 16, in.get(idx));
        }

        ctx.localBarrier();

        // Each lane reads back its 4 elements via swizzleLoad
        for (int idx = lane; idx < 128; idx += WARP_SIZE) {
            int r    = idx / 16;
            int slot = idx % 16;
            HalfFloat read = ctx.swizzleLoadFp16Stride32(tile, r, slot, 16);
            out.set(idx, read.getFloat32());
        }
    }

    @Test
    public void testSwizzleRoundTrip() throws TornadoExecutionPlanException {
        // Pre-populate on the host with 128 fp16 values
        HalfFloatArray in = new HalfFloatArray(128);
        for (int idx = 0; idx < 128; idx++) {
            in.set(idx, new HalfFloat((float) idx));
        }

        FloatArray out = new FloatArray(128);

        WorkerGrid1D workerGrid = new WorkerGrid1D(WARP_SIZE);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("swz_rt.kernel", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("swz_rt")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, out)
                .task("kernel", TestMatrixMultiplicationMMA::swizzleRoundTripKernel, ctx, in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        for (int idx = 0; idx < 128; idx++) {
            float expected = (float) idx;
            float actual = out.get(idx);
            assertEquals(String.format("idx=%d", idx),
                    expected, actual, 0.01f);
        }
    }

    public static void mmaIndexedProbeKernel(KernelContext ctx,
                                             HalfFloatArray a,
                                             HalfFloatArray indices,
                                             FloatArray c,
                                             int dimM, int dimN, int dimK) {
        int lane = ctx.localIdx;
        int tileRow = 0, tileCol = 0;  // single warp, single tile

        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        HalfFloat[] bTile0 = ctx.allocateHalfFloatLocalArray(16 * 8);  // 128 fp16, canonical layout

        float[] fragC0 = ctx.mmaFragment(0.0f);

        // Cooperative load A: identity (same as in gemmMMASwizzled)
        for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
            int elemBase = idx * 2;
            int r  = elemBase / WMMA_K;
            int kk = elemBase % WMMA_K;
            int globalBase = (tileRow + r) * dimK + kk;
            int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
            int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
            aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
        }

        for (int idx = lane; idx < 128; idx += WARP_SIZE) {
            int k_row = idx / 8;     // 0..15
            int j     = idx % 8;     // 0..7
            ctx.swizzleStoreFp16Stride32(bTile0, k_row, j, 8, indices.get(idx));
        }

        ctx.localBarrier();

        HalfFloat[] fragA = ctx.mmaLoadA(aTile, WMMA_K);
        HalfFloat[] fragB0 = ctx.mmaLoadBSwizzled(bTile0, WMMA_K);
        fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);

        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
    }

    @Test
    public void testMmaIndexedProbe() throws TornadoExecutionPlanException {
        int M = 16, N = 8, K = 16;  // single mma call

        // A = identity
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < K; j++) {
                a.set(k * K + j, new HalfFloat(k == j ? 1.0f : 0.0f));
            }
        }

        // indices: idx -> idx (0..127)
        HalfFloatArray indices = new HalfFloatArray(128);
        for (int i = 0; i < 128; i++) {
            indices.set(i, new HalfFloat((float) i));
        }

        FloatArray c = new FloatArray(M * N);

        WorkerGrid1D workerGrid = new WorkerGrid1D(WARP_SIZE);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_indexed.k", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_indexed")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, indices)
                .task("k", TestMatrixMultiplicationMMA::mmaIndexedProbeKernel, ctx, a, indices, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float expected = (float) (8 * i + j);
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    public static void mmaIndexedProbeNonSwizzledKernel(KernelContext ctx,
                                                        HalfFloatArray a, HalfFloatArray indices, FloatArray c, int dimM, int dimN, int dimK) {
        int lane = ctx.localIdx;
        int tileRow = 0, tileCol = 0;

        // Same A allocation as the swizzled probe
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);

        // Int-packed B tile: 16 rows × 8 cols of fp16, packed 2 fp16 per int.
        // 16 * 8 / 2 = 64 ints total.
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * 8 / 2);

        float[] fragC0 = ctx.mmaFragment(0.0f);

        // Cooperative load A: identity (same as in swizzled probe)
        for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
            int elemBase = idx * 2;
            int r  = elemBase / WMMA_K;
            int kk = elemBase % WMMA_K;
            int globalBase = (tileRow + r) * dimK + kk;
            int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
            int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
            aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
        }

        // Cooperative load B: int-packed canonical layout.
        // Logical position (k_row, j) of bTile0 should hold value (k_row * 8 + j),
        // matching the swizzled version's encoding exactly.
        // Each int packs 2 adjacent j values: (k_row, 2*j_pair) and (k_row, 2*j_pair+1).
        for (int idx = lane; idx < 64; idx += WARP_SIZE) {
            int k_row  = idx / 4;        // 0..15
            int j_pair = idx % 4;        // 0..3
            int j_base = j_pair * 2;     // 0, 2, 4, 6

            // Read the pre-encoded values from indices[]:
            //   indices[k_row * 8 + j_base]     = encoded(k_row, j_base)
            //   indices[k_row * 8 + j_base + 1] = encoded(k_row, j_base + 1)
            int lo = indices.get(k_row * 8 + j_base).getHalfFloatValue() & 0xFFFF;
            int hi = indices.get(k_row * 8 + j_base + 1).getHalfFloatValue() & 0xFFFF;
            bTile0[k_row * 4 + j_pair] = lo | (hi << 16);
        }

        ctx.localBarrier();

        HalfFloat[] fragA  = ctx.mmaLoadA(aTile, WMMA_K);
        HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);     // <-- non-swizzled
        fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);

        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
    }

    @Test
    public void testMmaIndexedProbeNonSwizzled() throws TornadoExecutionPlanException {
        int M = 16, N = 8, K = 16;

        // A = identity (16×16)
        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < K; j++) {
                a.set(k * K + j, new HalfFloat(k == j ? 1.0f : 0.0f));
            }
        }

        // indices[i] = i for i in 0..127
        // So value at logical (k_row, j) = k_row * 8 + j = the same idx encoding.
        HalfFloatArray indices = new HalfFloatArray(128);
        for (int i = 0; i < 128; i++) {
            indices.set(i, new HalfFloat((float) i));
        }

        FloatArray c = new FloatArray(M * N);

        WorkerGrid1D workerGrid = new WorkerGrid1D(WARP_SIZE);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_indexed_ns.k", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_indexed_ns")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, indices)
                .task("k", TestMatrixMultiplicationMMA::mmaIndexedProbeNonSwizzledKernel,
                        ctx, a, indices, c, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        final float tol = 0.01f;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float expected = (float) (8 * i + j);
                assertEquals(String.format("C[%d][%d]", i, j),
                        expected, c.get(i * N + j), tol);
            }
        }
    }

    public static void swizzleStorageProbeKernel(KernelContext ctx,
                                                 HalfFloatArray indices,
                                                 HalfFloatArray dumpedTile,
                                                 int unused) {
        int lane = ctx.localIdx;

        // 256-byte fp16 tile = 128 fp16 elements
        HalfFloat[] bTile0 = ctx.allocateHalfFloatLocalArray(128);

        // Step 1: Cooperative store, value = idx (a known sequence) at logical (k_row, j).
        // For idx in 0..127, k_row = idx/8 ∈ 0..15, j = idx%8 ∈ 0..7.
        // swizzleStore puts indices.get(idx) at logical position (k_row, j) with stride=8.
        for (int idx = lane; idx < 128; idx += WARP_SIZE) {
            int k_row = idx / 8;
            int j     = idx % 8;
            ctx.swizzleStoreFp16Stride32(bTile0, k_row, j, 8, indices.get(idx));
        }

        ctx.localBarrier();

        // Step 2: Lane 0 dumps the raw fp16 array contents to global memory.
        // dumpedTile.set(i, bTile0[i]) for i in 0..127.
        // This tells us what's at each fp16 element of bTile0.
        if (lane == 0) {
            for (int i = 0; i < 128; i++) {
                dumpedTile.set(i, bTile0[i]);
            }
        }
    }

    @Test
    public void testSwizzleStorageProbe() throws TornadoExecutionPlanException {
        // indices[i] = i for i in 0..127, stored as fp16.
        HalfFloatArray indices = new HalfFloatArray(128);
        for (int i = 0; i < 128; i++) {
            indices.set(i, new HalfFloat((float) i));
        }

        HalfFloatArray dumpedTile = new HalfFloatArray(128);
        final float SENTINEL = -1.0f;
        for (int i = 0; i < 128; i++) {
            dumpedTile.set(i, new HalfFloat(SENTINEL));
        }

        WorkerGrid1D workerGrid = new WorkerGrid1D(WARP_SIZE);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("swz_probe.k", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("swz_probe")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, indices, dumpedTile)
                .task("k", TestMatrixMultiplicationMMA::swizzleStorageProbeKernel,
                        ctx, indices, dumpedTile, 0)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dumpedTile);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        // Assertion: derive the expected element-index per input idx from the swizzle formula.
        //   byteOff = 2 * idx
        //   swzByte = byteOff XOR (((byteOff >> 7) & 7) << 4)
        //   element = swzByte / 2
        // Equivalently:
        //   idx in 0..63  -> element = idx        (lower half, no swizzle)
        //   idx in 64..127 -> element = idx XOR 8 (upper half, pair-swap by bit 3)
        //
        // After the fix (shr.b32 swzByte, swzByte, 1 in the PTX), the value lands at the
        // intended element index. Before the fix, swzByte was used as the bracket index
        // directly (treated by PTX as an fp16 element index), so values were placed at
        // byte 4*idx (= element 2*idx) and the upper half went out of bounds.
        final float tol = 0.01f;

        // Build the expected layout
        float[] expected = new float[128];
        java.util.Arrays.fill(expected, SENTINEL);
        for (int idx = 0; idx < 128; idx++) {
            int byteOff = 2 * idx;
            int swzByte = byteOff ^ (((byteOff >> 7) & 7) << 4);
            int element = swzByte / 2;
            expected[element] = (float) idx;
        }

        // Sanity check: every element should have been written (no sentinels left).
        for (int i = 0; i < 128; i++) {
            if (expected[i] == SENTINEL) {
                throw new AssertionError(
                        "Test setup bug: expected layout has a sentinel at element " + i
                                + " (the swizzle formula didn't cover every element).");
            }
        }

        // Verify each element of the dumped tile matches the expected layout.
        for (int i = 0; i < 128; i++) {
            assertEquals(String.format("bTile0[%d]", i),
                    expected[i], dumpedTile.get(i).getFloat32(), tol);
        }
    }

    private static HalfFloatArray randomFP16(int size) {
        HalfFloatArray arr = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
           arr.set(i, new HalfFloat((float)(Math.random() * 2.0 - 1.0)));
        }
        return arr;
    }

}
