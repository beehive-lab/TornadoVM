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
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Mirrors TestMatrixMultiplicationMMAInt8 for the FP8 (OCP E4M3/E5M2) m16n8k32
 * tensor-core path. The shared-memory tile layout is byte-identical to the int8
 * path - only the mma.sync element type differs - so the kernels differ from the
 * int8 ones only in the array types and the compute call.
 *
 * <p>CUDA backend only, compute capability 8.9+ (Ada/Hopper).</p>
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMAFP8
 * </code>
 * </p>
 */
public class TestMatrixMultiplicationMMAFP8 extends TornadoTestBase {

    static final int WMMA_M    = 16;
    static final int WMMA_N    = 16;   // covered by two m16n8k32 calls
    static final int WMMA_K    = 32;   // K=32 for FP8 MMA
    static final int WARP_SIZE = 32;

    // -----------------------------------------------------------------------
    // Kernels
    // -----------------------------------------------------------------------

    /**
     * FP8 GEMM: C[M x N] = A[M x K] * B[K x N]
     *   A : FP8Array (E4M3 bytes), row-major
     *   B : FP8Array (E4M3 bytes), row-major in global memory,
     *       transposed to col-major per 8-column panel in shared memory
     *   C : FloatArray, row-major, f32 accumulator
     *
     * One workgroup = one warp (32 threads) = one 16x16 output tile.
     *
     * Launch config:
     *   localSize  = 32
     *   globalSize = (M/16) * (N/16) * 32
     */
    public static void gemmMMAFP8E4M3(KernelContext ctx,
                                      FP8Array a, FP8Array b, FloatArray c,
                                      int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        // Byte-packed shared tiles, identical layout to the int8 m16n8k32 path:
        // A: 16x32 fp8 bytes = 128 ints; B: 8 cols x 32 k = 64 ints per panel.
        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {

            // Cooperative load A: pack 4 adjacent fp8 bytes into one int
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 4; idx += WARP_SIZE) {
                int elemBase = idx * 4;
                int r  = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int base = (tileRow + r) * dimK + kBase + kk;
                int packed = (a.get(base)     & 0xFF)
                        | ((a.get(base + 1) & 0xFF) << 8)
                        | ((a.get(base + 2) & 0xFF) << 16)
                        | ((a.get(base + 3) & 0xFF) << 24);
                aTile[r * (WMMA_K / 4) + kk / 4] = packed;
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int k_row  = idx / 4;        // 0..15
                int j_pair = idx % 4;        // 0..3
                int j_base = j_pair * 2;     // 0, 2, 4, 6
                int k_base = 2 * k_row;      // first of the pair of K-values

                // Left panel (cols tileCol..tileCol+7)
                int bL0 = b.get((kBase + k_base)     * dimN + tileCol + j_base)     & 0xFF;
                int bL1 = b.get((kBase + k_base + 1) * dimN + tileCol + j_base)     & 0xFF;
                int bL2 = b.get((kBase + k_base)     * dimN + tileCol + j_base + 1) & 0xFF;
                int bL3 = b.get((kBase + k_base + 1) * dimN + tileCol + j_base + 1) & 0xFF;
                bTile0[k_row * 4 + j_pair] = bL0 | (bL1 << 8) | (bL2 << 16) | (bL3 << 24);

                // Right panel (cols tileCol+8..tileCol+15)
                int bR0 = b.get((kBase + k_base)     * dimN + tileCol + 8 + j_base)     & 0xFF;
                int bR1 = b.get((kBase + k_base + 1) * dimN + tileCol + 8 + j_base)     & 0xFF;
                int bR2 = b.get((kBase + k_base)     * dimN + tileCol + 8 + j_base + 1) & 0xFF;
                int bR3 = b.get((kBase + k_base + 1) * dimN + tileCol + 8 + j_base + 1) & 0xFF;
                bTile1[k_row * 4 + j_pair] = bR0 | (bR1 << 8) | (bR2 << 16) | (bR3 << 24);
            }

            ctx.localBarrier();

            byte[] fragA = ctx.mmaLoadAFP8(aTile, WMMA_K);

            byte[] fragB0 = ctx.mmaLoadBFP8(bTile0, WMMA_K);
            fragC0 = ctx.mmaFP8E4M3(fragA, fragB0, fragC0, MMAShape.M16N8K32);

            byte[] fragB1 = ctx.mmaLoadBFP8(bTile1, WMMA_K);
            fragC1 = ctx.mmaFP8E4M3(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    /** Same kernel with the E5M2 interpretation of the bytes. */
    public static void gemmMMAFP8E5M2(KernelContext ctx,
                                      FP8Array a, FP8Array b, FloatArray c,
                                      int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {

            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 4; idx += WARP_SIZE) {
                int elemBase = idx * 4;
                int r  = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int base = (tileRow + r) * dimK + kBase + kk;
                int packed = (a.get(base)     & 0xFF)
                        | ((a.get(base + 1) & 0xFF) << 8)
                        | ((a.get(base + 2) & 0xFF) << 16)
                        | ((a.get(base + 3) & 0xFF) << 24);
                aTile[r * (WMMA_K / 4) + kk / 4] = packed;
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int k_row  = idx / 4;
                int j_pair = idx % 4;
                int j_base = j_pair * 2;
                int k_base = 2 * k_row;

                int bL0 = b.get((kBase + k_base)     * dimN + tileCol + j_base)     & 0xFF;
                int bL1 = b.get((kBase + k_base + 1) * dimN + tileCol + j_base)     & 0xFF;
                int bL2 = b.get((kBase + k_base)     * dimN + tileCol + j_base + 1) & 0xFF;
                int bL3 = b.get((kBase + k_base + 1) * dimN + tileCol + j_base + 1) & 0xFF;
                bTile0[k_row * 4 + j_pair] = bL0 | (bL1 << 8) | (bL2 << 16) | (bL3 << 24);

                int bR0 = b.get((kBase + k_base)     * dimN + tileCol + 8 + j_base)     & 0xFF;
                int bR1 = b.get((kBase + k_base + 1) * dimN + tileCol + 8 + j_base)     & 0xFF;
                int bR2 = b.get((kBase + k_base)     * dimN + tileCol + 8 + j_base + 1) & 0xFF;
                int bR3 = b.get((kBase + k_base + 1) * dimN + tileCol + 8 + j_base + 1) & 0xFF;
                bTile1[k_row * 4 + j_pair] = bR0 | (bR1 << 8) | (bR2 << 16) | (bR3 << 24);
            }

            ctx.localBarrier();

            byte[] fragA = ctx.mmaLoadAFP8(aTile, WMMA_K);

            byte[] fragB0 = ctx.mmaLoadBFP8(bTile0, WMMA_K);
            fragC0 = ctx.mmaFP8E5M2(fragA, fragB0, fragC0, MMAShape.M16N8K32);

            byte[] fragB1 = ctx.mmaLoadBFP8(bTile1, WMMA_K);
            fragC1 = ctx.mmaFP8E5M2(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // -----------------------------------------------------------------------
    // CPU references (decode through the same FP8 codecs the arrays use)
    // -----------------------------------------------------------------------

    private static void gemmReferenceE4M3(
            FP8Array a, FP8Array b, FloatArray ref,
            int dimM, int dimN, int dimK) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += FP8.e4m3ToFloat(a.get(i * dimK + k)) * FP8.e4m3ToFloat(b.get(k * dimN + j));
                }
                ref.set(i * dimN + j, sum);
            }
        }
    }

    private static void gemmReferenceE5M2(
            FP8Array a, FP8Array b, FloatArray ref,
            int dimM, int dimN, int dimK) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += FP8.e5m2ToFloat(a.get(i * dimK + k)) * FP8.e5m2ToFloat(b.get(k * dimN + j));
                }
                ref.set(i * dimN + j, sum);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /** Minimum case: M=16, N=16, K=32. One warp, one K iteration. */
    @Test
    public void testGemmFP8Minimal() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 32);
    }

    /** K=64: two K-iterations. */
    @Test
    public void testGemmFP8DeepK() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 64);
    }

    /** Four output tiles (2x2 grid of warps). */
    @Test
    public void testGemmFP8MultiTile() throws TornadoExecutionPlanException {
        runGemmTest(32, 32, 64);
    }

    /** Wide N: one row of tiles, four warp columns. */
    @Test
    public void testGemmFP8WideN() throws TornadoExecutionPlanException {
        runGemmTest(16, 64, 64);
    }

    /** Larger M: multiple seqLen rows. */
    @Test
    public void testGemmFP8PrefillSlice() throws TornadoExecutionPlanException {
        runGemmTest(64, 64, 64);
    }

    /** Identity test: A=identity, B=patterned -> C equals decoded B. Catches layout bugs. */
    @Test
    public void testGemmFP8Identity() throws TornadoExecutionPlanException {
        assumeFP8MMASupported();

        int M = 16, N = 16, K = 32;

        FP8Array a = new FP8Array(M * K);
        for (int i = 0; i < Math.min(M, K); i++) {
            a.setE4M3(i * K + i, 1.0f);
        }

        FP8Array b = new FP8Array(K * N);
        for (int i = 0; i < K * N; i++) {
            b.setE4M3(i, (i % 5) - 2.0f);  // small exact values: -2..2
        }

        FloatArray c   = new FloatArray(M * N);
        FloatArray ref = new FloatArray(M * N);
        gemmReferenceE4M3(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K, true);
    }

    /** All-halves test: every element should equal K * 0.25. */
    @Test
    public void testGemmFP8AllHalves() throws TornadoExecutionPlanException {
        assumeFP8MMASupported();

        int M = 16, N = 16, K = 32;

        FP8Array a = new FP8Array(M * K);
        FP8Array b = new FP8Array(K * N);
        for (int i = 0; i < M * K; i++) {
            a.setE4M3(i, 0.5f);
        }
        for (int i = 0; i < K * N; i++) {
            b.setE4M3(i, 0.5f);
        }

        FloatArray c   = new FloatArray(M * N);
        FloatArray ref = new FloatArray(M * N);
        gemmReferenceE4M3(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K, true);
    }

    /** E5M2 variant of the minimal case. */
    @Test
    public void testGemmFP8E5M2Minimal() throws TornadoExecutionPlanException {
        assumeFP8MMASupported();

        int M = 16, N = 16, K = 32;

        Random rng = new Random(42);
        FP8Array a = new FP8Array(M * K);
        FP8Array b = new FP8Array(K * N);
        for (int i = 0; i < M * K; i++) {
            a.setE5M2(i, quantizedValue(rng));
        }
        for (int i = 0; i < K * N; i++) {
            b.setE5M2(i, quantizedValue(rng));
        }

        FloatArray c   = new FloatArray(M * N);
        FloatArray ref = new FloatArray(M * N);
        gemmReferenceE5M2(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K, false);
    }

    // -----------------------------------------------------------------------
    // Runner
    // -----------------------------------------------------------------------

    private void assumeFP8MMASupported() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        assertNotBackend(TornadoVMBackendType.PTX);
    }

    private void runGemmTest(int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        assumeFP8MMASupported();

        Random rng = new Random(42);
        FP8Array a = new FP8Array(dimM * dimK);
        FP8Array b = new FP8Array(dimK * dimN);
        for (int i = 0; i < dimM * dimK; i++) {
            a.setE4M3(i, quantizedValue(rng));
        }
        for (int i = 0; i < dimK * dimN; i++) {
            b.setE4M3(i, quantizedValue(rng));
        }
        FloatArray c = new FloatArray(dimM * dimN);

        FloatArray ref = new FloatArray(dimM * dimN);
        gemmReferenceE4M3(a, b, ref, dimM, dimN, dimK);

        runGemmTestWithData(a, b, c, ref, dimM, dimN, dimK, true);
    }

    private void runGemmTestWithData(FP8Array a, FP8Array b, FloatArray c, FloatArray ref,
                                     int dimM, int dimN, int dimK, boolean e4m3)
            throws TornadoExecutionPlanException {

        int numWarps   = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        int localSize  = WARP_SIZE;

        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(localSize, 1, 1);

        GridScheduler gridScheduler = new GridScheduler("mma_fp8_test.gemm", workerGrid);

        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_fp8_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);
        if (e4m3) {
            tg = tg.task("gemm", TestMatrixMultiplicationMMAFP8::gemmMMAFP8E4M3, ctx, a, b, c, dimM, dimN, dimK);
        } else {
            tg = tg.task("gemm", TestMatrixMultiplicationMMAFP8::gemmMMAFP8E5M2, ctx, a, b, c, dimM, dimN, dimK);
        }
        tg = tg.transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(gridScheduler)
                    .execute();
        }

        // Products of the test values are exact in f32 and their sums stay well within
        // the exactly-representable range, so tensor-core and host accumulation agree
        // to a tight tolerance regardless of accumulation order.
        final float tol = 1e-3f;
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
                assertEquals(String.format("C[%d][%d]", i, j),
                        ref.get(idx), c.get(idx), tol);
            }
        }
    }

    /**
     * Values exactly representable in both FP8 formats (multiples of 0.25 in [-2, 2)),
     * so products are exact in f32 and accumulation order cannot change the sum.
     */
    private static float quantizedValue(Random rng) {
        return (rng.nextInt(16) - 8) * 0.25f;
    }
}
