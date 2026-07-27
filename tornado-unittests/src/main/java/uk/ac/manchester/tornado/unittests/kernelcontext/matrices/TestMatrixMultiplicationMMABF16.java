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
import static org.junit.Assert.assertTrue;

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
import uk.ac.manchester.tornado.api.types.BFloat16;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Mirrors TestMatrixMultiplicationMMA for the bfloat16 m16n8k16 tensor-core path.
 * BF16 shares fp16's tile shape and fragment layout, so the kernel differs from the
 * fp16 one only in the storage type (raw bf16 bits in a {@link ShortArray}) and the
 * compute call ({@code mmaBF16}).
 *
 * <p>CUDA backend only, compute capability 8.0+.</p>
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMABF16
 * </code>
 * </p>
 */
public class TestMatrixMultiplicationMMABF16 extends TornadoTestBase {

    static final int WMMA_M    = 16;
    static final int WMMA_N    = 16;   // covered by two m16n8k16 calls
    static final int WMMA_K    = 16;
    static final int WARP_SIZE = 32;

    // -----------------------------------------------------------------------
    // Kernel
    // -----------------------------------------------------------------------

    /**
     * BF16 GEMM: C[M x N] = A[M x K] * B[K x N]
     *   A : ShortArray of raw bf16 bits, row-major
     *   B : ShortArray of raw bf16 bits, row-major in global memory,
     *       transposed to col-major per 8-column panel in shared memory
     *   C : FloatArray, row-major, f32 accumulator
     *
     * Identical structure to TestMatrixMultiplicationMMA#gemmMMA: the b16 packing,
     * ldmatrix loads and store are bit-type-agnostic; only mmaBF16 interprets the
     * bits as bfloat16.
     */
    public static void gemmMMABF16(KernelContext ctx,
                                   ShortArray a, ShortArray b, FloatArray c,
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

            // Cooperative load A: pack 2 adjacent bf16 bit patterns into one int
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r  = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase) & 0xFFFF;
                int hi = a.get(globalBase + 1) & 0xFFFF;
                aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int k_row  = idx / 4;
                int j_pair = idx % 4;
                int j_base = j_pair * 2;

                int gL0 = (kBase + k_row) * dimN + tileCol + j_base;
                int lo_left = b.get(gL0) & 0xFFFF;
                int hi_left = b.get(gL0 + 1) & 0xFFFF;
                bTile0[k_row * 4 + j_pair] = lo_left | (hi_left << 16);

                int gR0 = (kBase + k_row) * dimN + tileCol + 8 + j_base;
                int lo_right = b.get(gR0) & 0xFFFF;
                int hi_right = b.get(gR0 + 1) & 0xFFFF;
                bTile1[k_row * 4 + j_pair] = lo_right | (hi_right << 16);
            }
            ctx.localBarrier();

            HalfFloat[] fragA  = ctx.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);
            fragC0 = ctx.mmaBF16(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, WMMA_K);
            fragC1 = ctx.mmaBF16(fragA, fragB1, fragC1, MMAShape.M16N8K16);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // -----------------------------------------------------------------------
    // CPU reference
    // -----------------------------------------------------------------------

    private static void gemmReference(
            ShortArray a, ShortArray b, FloatArray ref,
            int dimM, int dimN, int dimK) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                float sum = 0.0f;
                for (int k = 0; k < dimK; k++) {
                    sum += BFloat16.bf16ToFloat(a.get(i * dimK + k))
                            * BFloat16.bf16ToFloat(b.get(k * dimN + j));
                }
                ref.set(i * dimN + j, sum);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Tests (same shape set as the fp16 MMA test)
    // -----------------------------------------------------------------------

    @Test
    public void testGemmBF16Minimal() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 16);
    }

    @Test
    public void testGemmBF16DeepK() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 64);
    }

    @Test
    public void testGemmBF16MultiTile() throws TornadoExecutionPlanException {
        runGemmTest(32, 32, 64);
    }

    @Test
    public void testGemmBF16WideN() throws TornadoExecutionPlanException {
        runGemmTest(16, 64, 64);
    }

    @Test
    public void testGemmBF16PrefillSlice() throws TornadoExecutionPlanException {
        runGemmTest(64, 64, 64);
    }

    /** Identity B: catches layout/permutation bugs independent of rounding. */
    @Test
    public void testGemmBF16IdentityB() throws TornadoExecutionPlanException {
        assumeBF16MMASupported();

        int M = 16, N = 16, K = 16;

        ShortArray a = new ShortArray(M * K);
        for (int i = 0; i < M; i++) {
            for (int k = 0; k < K; k++) {
                a.set(i * K + k, BFloat16.bf16FromFloat((float) (i * 16 + k)));
            }
        }

        ShortArray b = new ShortArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, BFloat16.bf16FromFloat(k == j ? 1.0f : 0.0f));
            }
        }

        FloatArray c = new FloatArray(M * N);
        FloatArray ref = new FloatArray(M * N);
        gemmReference(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K);
    }

    /**
     * Numerical-delta characterization: the same fp32 inputs quantized to bf16 and fp16,
     * run through the respective MMA paths, both compared against the exact fp32 GEMM.
     * BF16 keeps 8 mantissa bits (vs fp16's 11), so its error should be roughly an order
     * of magnitude larger but still bounded; the observed maxima are printed so the
     * numbers are visible in the test log.
     */
    @Test
    public void testBF16VersusFP16Delta() throws TornadoExecutionPlanException {
        assumeBF16MMASupported();

        int M = 64, N = 64, K = 64;
        Random rng = new Random(42);

        float[] aF = new float[M * K];
        float[] bF = new float[K * N];
        for (int i = 0; i < aF.length; i++) {
            aF[i] = rng.nextFloat() * 2.0f - 1.0f;
        }
        for (int i = 0; i < bF.length; i++) {
            bF[i] = rng.nextFloat() * 2.0f - 1.0f;
        }

        // Quantize the same data to both formats.
        ShortArray aBf = new ShortArray(M * K);
        ShortArray bBf = new ShortArray(K * N);
        HalfFloatArray aHf = new HalfFloatArray(M * K);
        HalfFloatArray bHf = new HalfFloatArray(K * N);
        for (int i = 0; i < aF.length; i++) {
            aBf.set(i, BFloat16.bf16FromFloat(aF[i]));
            aHf.set(i, new HalfFloat(aF[i]));
        }
        for (int i = 0; i < bF.length; i++) {
            bBf.set(i, BFloat16.bf16FromFloat(bF[i]));
            bHf.set(i, new HalfFloat(bF[i]));
        }

        // Exact fp32 reference on the unquantized data.
        float[] refF32 = new float[M * N];
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < K; k++) {
                    sum += aF[i * K + k] * bF[k * N + j];
                }
                refF32[i * N + j] = sum;
            }
        }

        // BF16 MMA on device.
        FloatArray cBf = new FloatArray(M * N);
        runGemmOnDevice(aBf, bBf, cBf, M, N, K);

        // FP16 MMA on device (same kernel structure from the fp16 test class).
        FloatArray cHf = new FloatArray(M * N);
        runFp16GemmOnDevice(aHf, bHf, cHf, M, N, K);

        float maxAbsBf = 0.0f, maxAbsHf = 0.0f;
        for (int i = 0; i < M * N; i++) {
            maxAbsBf = Math.max(maxAbsBf, Math.abs(cBf.get(i) - refF32[i]));
            maxAbsHf = Math.max(maxAbsHf, Math.abs(cHf.get(i) - refF32[i]));
        }
        System.out.printf("BF16 vs FP16 MMA delta (M=N=K=64, inputs U[-1,1]): max |bf16 - f32| = %.5f, max |fp16 - f32| = %.5f, ratio = %.2fx%n",
                maxAbsBf, maxAbsHf, maxAbsBf / Math.max(maxAbsHf, 1e-9f));

        // BF16 quantization error per element is <= 2^-8 relative; a K=64 dot product of
        // U[-1,1] values stays well under this bound in practice. FP16's must be smaller.
        assertTrue("bf16 error unexpectedly large: " + maxAbsBf, maxAbsBf < 0.25f);
        assertTrue("fp16 error should not exceed bf16 error: bf16=" + maxAbsBf + " fp16=" + maxAbsHf,
                maxAbsHf <= maxAbsBf);
    }

    // -----------------------------------------------------------------------
    // Runners
    // -----------------------------------------------------------------------

    private void assumeBF16MMASupported() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        assertNotBackend(TornadoVMBackendType.PTX);
    }

    private void runGemmTest(int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        assumeBF16MMASupported();

        Random rng = new Random(42);
        ShortArray a = new ShortArray(dimM * dimK);
        ShortArray b = new ShortArray(dimK * dimN);
        for (int i = 0; i < dimM * dimK; i++) {
            a.set(i, BFloat16.bf16FromFloat(rng.nextFloat() * 2.0f - 1.0f));
        }
        for (int i = 0; i < dimK * dimN; i++) {
            b.set(i, BFloat16.bf16FromFloat(rng.nextFloat() * 2.0f - 1.0f));
        }
        FloatArray c = new FloatArray(dimM * dimN);

        FloatArray ref = new FloatArray(dimM * dimN);
        gemmReference(a, b, ref, dimM, dimN, dimK);

        runGemmTestWithData(a, b, c, ref, dimM, dimN, dimK);
    }

    private void runGemmTestWithData(ShortArray a, ShortArray b, FloatArray c, FloatArray ref,
                                     int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        runGemmOnDevice(a, b, c, dimM, dimN, dimK);

        // Both sides multiply-accumulate the exact same bf16 values in f32, so the only
        // difference is accumulation order - tight tolerance.
        float tol = 0.01f;
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
                assertEquals(String.format("C[%d][%d]", i, j),
                        ref.get(idx), c.get(idx), tol);
            }
        }
    }

    private void runGemmOnDevice(ShortArray a, ShortArray b, FloatArray c,
                                 int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        int numWarps   = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;

        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_bf16_test.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_bf16_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMABF16::gemmMMABF16,
                        ctx, a, b, c, dimM, dimN, dimK)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(gridScheduler)
                    .execute();
        }
    }

    private void runFp16GemmOnDevice(HalfFloatArray a, HalfFloatArray b, FloatArray c,
                                     int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        int numWarps   = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;

        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_bf16_ref.gemm", workerGrid);
        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_bf16_ref")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMA::gemmMMA,
                        ctx, a, b, c, dimM, dimN, dimK)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(gridScheduler)
                    .execute();
        }
    }
}
