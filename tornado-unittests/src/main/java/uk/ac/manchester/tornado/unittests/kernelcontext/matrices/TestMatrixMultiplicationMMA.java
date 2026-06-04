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
        int[] bTile0 = ctx.allocateIntLocalArray(8 * WMMA_K / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(8 * WMMA_K / 2);

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

//            // Cooperative load B: col-major, pack 2 adjacent k-values per int
//            for (int idx = lane; idx < (8 * WMMA_K) / 2; idx += WARP_SIZE) {
//                int elemBase = idx * 2;
//                int col = elemBase / WMMA_K;
//                int kk  = elemBase % WMMA_K;
//                int g0 = (kBase + kk) * dimN + tileCol + col;
//                int g1 = (kBase + kk + 1) * dimN + tileCol + col;
//                int lo0 = b.get(g0).getHalfFloatValue() & 0xFFFF;
//                int hi0 = b.get(g1).getHalfFloatValue() & 0xFFFF;
//                bTile0[col * (WMMA_K / 2) + kk / 2] = lo0 | (hi0 << 16);
//
//                int g2 = (kBase + kk) * dimN + tileCol + 8 + col;
//                int g3 = (kBase + kk + 1) * dimN + tileCol + 8 + col;
//                int lo1 = b.get(g2).getHalfFloatValue() & 0xFFFF;
//                int hi1 = b.get(g3).getHalfFloatValue() & 0xFFFF;
//                bTile1[col * (WMMA_K / 2) + kk / 2] = lo1 | (hi1 << 16);
//            }

//            // bTile0 holds 16 k-rows × 8 cols of b16. Row stride = 4 ints (16 bytes).
//// Lane idx ranges over (16 * 8) / 2 = 64 packed pairs.
//            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
//                int elemBase = idx * 2;
//                int kk = elemBase / 8;           // 0..15
//                int j  = elemBase % 8;           // 0, 2, 4, 6 within row
//                int g0 = (kBase + kk) * dimN + tileCol + j;
//                int g1 = (kBase + kk) * dimN + tileCol + j + 1;
//                int lo = b.get(g0).getHalfFloatValue() & 0xFFFF;
//                int hi = b.get(g1).getHalfFloatValue() & 0xFFFF;
//                bTile0[kk * 4 + j / 2] = lo | (hi << 16);
//            }

//            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
//                int elemBase = idx * 2;
//                int kk = elemBase / 8;           // 0..15
//                int j  = elemBase % 8;           // 0, 2, 4, 6 within row
//
//                // Left panel: cols tileCol + j, tileCol + j + 1
//                int g0_left = (kBase + kk) * dimN + tileCol + j;
//                int g1_left = (kBase + kk) * dimN + tileCol + j + 1;
//                int lo_left = b.get(g0_left).getHalfFloatValue() & 0xFFFF;
//                int hi_left = b.get(g1_left).getHalfFloatValue() & 0xFFFF;
//                bTile0[kk * 4 + j / 2] = lo_left | (hi_left << 16);
//
//                // Right panel: cols tileCol + 8 + j, tileCol + 8 + j + 1
//                int g0_right = (kBase + kk) * dimN + tileCol + 8 + j;
//                int g1_right = (kBase + kk) * dimN + tileCol + 8 + j + 1;
//                int lo_right = b.get(g0_right).getHalfFloatValue() & 0xFFFF;
//                int hi_right = b.get(g1_right).getHalfFloatValue() & 0xFFFF;
//                bTile1[kk * 4 + j / 2] = lo_right | (hi_right << 16);
//            }
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r = elemBase / 16;       // 0..7
                int slot = elemBase % 16;    // 0, 2, 4, ..., 14

                int k, j;
                if (slot < 8) {
                    k = r;
                    j = slot;
                } else {
                    k = r + 8;
                    j = slot - 8;
                }

                // Left panel: B cols tileCol + j .. tileCol + j + 1
                int gL0 = (kBase + k) * dimN + tileCol + j;
                int gL1 = (kBase + k) * dimN + tileCol + j + 1;
                int loL = b.get(gL0).getHalfFloatValue() & 0xFFFF;
                int hiL = b.get(gL1).getHalfFloatValue() & 0xFFFF;
                bTile0[r * 8 + slot / 2] = loL | (hiL << 16);

                // Right panel: B cols tileCol + 8 + j .. tileCol + 8 + j + 1
                int gR0 = (kBase + k) * dimN + tileCol + 8 + j;
                int gR1 = (kBase + k) * dimN + tileCol + 8 + j + 1;
                int loR = b.get(gR0).getHalfFloatValue() & 0xFFFF;
                int hiR = b.get(gR1).getHalfFloatValue() & 0xFFFF;
                bTile1[r * 8 + slot / 2] = loR | (hiR << 16);
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
//    public static void gemmMMA(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimM, int dimN, int dimK) {
//
//        int warpId = ctx.groupIdx;
//        int lane   = ctx.localIdx;
//
//        int numTilesN = dimN / WMMA_N;
//        int tileRow   = (warpId / numTilesN) * WMMA_M;
//        int tileCol   = (warpId % numTilesN) * WMMA_N;
//
//        float[] aTile  = ctx.allocateFloatLocalArray(WMMA_M * WMMA_K);
//        float[] bTile0 = ctx.allocateFloatLocalArray(8 * WMMA_K);
//        float[] bTile1 = ctx.allocateFloatLocalArray(8 * WMMA_K);
//
//        float[] fragC0 = ctx.mmaFragment(0.0f);
//        float[] fragC1 = ctx.mmaFragment(0.0f);
//
//        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {
//
//            for (int idx = lane; idx < WMMA_M * WMMA_K; idx += WARP_SIZE) {
//                int r  = idx / WMMA_K;
//                int kk = idx % WMMA_K;
//                aTile[r * WMMA_K + kk] = a.get((tileRow + r) * dimK + kBase + kk).getFloat32();
//            }
//
//            for (int idx = lane; idx < 8 * WMMA_K; idx += WARP_SIZE) {
//                int col = idx / WMMA_K;
//                int kk  = idx % WMMA_K;
//                bTile0[col * WMMA_K + kk] = b.get((kBase + kk) * dimN + tileCol     + col).getFloat32();
//                bTile1[col * WMMA_K + kk] = b.get((kBase + kk) * dimN + tileCol + 8 + col).getFloat32();
//            }
//
//            ctx.localBarrier();
//
//            HalfFloat[] fragA = ctx.mmaLoadA(aTile, WMMA_K);
//
//            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);
//            fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
//
//            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, WMMA_K);
//            fragC1 = ctx.mma(fragA, fragB1, fragC1, MMAShape.M16N8K16);
//
//            ctx.localBarrier();
//        }
//
//        ctx.mmaStore(fragC0, c, tileRow, tileCol,     dimN);
//        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
//    }

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

        System.out.println("=== Distinct-K test: every C[i][j] should be 136 ===");
        for (int i = 0; i < M; i++) {
            StringBuilder line = new StringBuilder(String.format("row[%2d]: ", i));
            for (int j = 0; j < N; j++) {
                line.append(String.format("%6.0f", c.get(i * N + j)));
            }
            System.out.println(line);
        }
    }

    @Test
    public void testGemmKMappingProbe() throws TornadoExecutionPlanException {
        int M = 16, N = 16, K = 16;

        // A[i][k] = 2^k   (k = 0..15, so A[i][k] ∈ {1, 2, 4, 8, ..., 32768})
        // But 2^15 = 32768 won't fit in fp16 (max ~65504), and 2^14 = 16384 is fine.
        // Use 2^k for k in 0..10 to stay safe.
        // Better: A[i][k] = k itself, B[k][j] = 16^(some_distinguishable_index)
        // But 16^16 overflows everything. Let me use smaller.

        // Instead: A[i][k] = (k+1)  (1..16, no row dependence)
        //          B[k][j] = (k+1)*100 + j  (k+1 in high digits, j in low)
        // Each product = (k+1) * ((k+1)*100 + j) = 100*(k+1)² + (k+1)*j
        // Sum over k: 100 * sum_k (k+1)² + j * sum_k (k+1)
        //           = 100 * 1496 + j * 136
        //           = 149600 + 136j
        // This overflows fp16 (max 65504). Not usable.

        // Smaller version: A[i][k] = k+1, B[k][j] = (k+1)*10
        // Product = 10*(k+1)²
        // Sum = 10*1496 = 14960. Just inside fp32 (output is FloatArray).
        // But fp16 inputs cap at 65504, so b[k][j] = (k+1)*10 ranges to 160, fine.
        // Expected: 14960 everywhere.

        HalfFloatArray a = new HalfFloatArray(M * K);
        for (int i = 0; i < M; i++)
            for (int k = 0; k < K; k++)
                a.set(i * K + k, new HalfFloat((float)(k + 1)));

        HalfFloatArray b = new HalfFloatArray(K * N);
        for (int k = 0; k < K; k++)
            for (int j = 0; j < N; j++)
                b.set(k * N + j, new HalfFloat((float)((k + 1) * 10)));

        FloatArray c = new FloatArray(M * N);

        // Expected: 14960 everywhere

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

        System.out.println("=== K-mapping probe: every cell should be 14960 ===");
        System.out.println("Row 0:");
        StringBuilder line = new StringBuilder();
        for (int j = 0; j < N; j++) {
            line.append(String.format("%8.0f", c.get(j)));
        }
        System.out.println(line);
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

        System.out.println("=== Distinct-K-in-B test: every C[i][j] should be 136 ===");
        for (int i = 0; i < M; i++) {
            StringBuilder line = new StringBuilder(String.format("row[%2d]: ", i));
            for (int j = 0; j < N; j++) {
                line.append(String.format("%6.0f", c.get(i * N + j)));
            }
            System.out.println(line);
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

        // Print the full 16×16 result so we can read the permutation off
        System.out.println("=== Identity-B test: C should equal A (A[i][j] = i*16 + j) ===");
        for (int i = 0; i < M; i++) {
            StringBuilder line = new StringBuilder(String.format("row[%2d]: ", i));
            for (int j = 0; j < N; j++) {
                line.append(String.format("%6.0f", c.get(i * N + j)));
            }
            System.out.println(line);
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

        System.out.println("=== Distinct-AB test: every C[i][j] should be 1496 ===");
        for (int i = 0; i < M; i++) {
            StringBuilder line = new StringBuilder(String.format("row[%2d]: ", i));
            for (int j = 0; j < N; j++) {
                line.append(String.format("%6.0f", c.get(i * N + j)));
            }
            System.out.println(line);
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

        System.out.println("=== B layout probe: each cell should be 1200 + 16*j ===");
        System.out.println("Expected row: 1200, 1216, 1232, ..., 1440");
        StringBuilder line = new StringBuilder("Row 0: ");
        for (int j = 0; j < N; j++) {
            line.append(String.format("%6.0f", c.get(j)));
        }
        System.out.println(line);
    }

    private static HalfFloatArray randomFP16(int size) {
        HalfFloatArray arr = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
           // arr.set(i, new HalfFloat(1.0f));
           arr.set(i, new HalfFloat((float)(Math.random() * 2.0 - 1.0)));
        }
        return arr;
    }

}
