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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;

/**
 * Mirrors TestMatrixMultiplicationMMA for the int8 m16n8k32 path.
 * Swizzle and byte-offset variants are FP16-only in this PR and will be added when the int8 backend gains 3-arg load variants.
 *
 * <p>
 * How to run?
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMAInt8
 * </code>
 * </p>
 */
public class TestMatrixMultiplicationMMAInt8 extends TornadoTestBase {

    static final int WMMA_M    = 16;
    static final int WMMA_N    = 16;   // covered by two m16n8k32 calls
    static final int WMMA_K    = 32;   // K=32 for int8 MMA
    static final int WARP_SIZE = 32;
    /** N of a single m16n8k32 tile, used by the accumulator fragment tests. */
    static final int MMA_N     = 8;

    // -----------------------------------------------------------------------
    // Kernel
    // -----------------------------------------------------------------------

    /**
     * Int8 GEMM: C[M x N] = A[M x K] * B[K x N]
     *   A : IntArray, row-major (values in [-128, 127], stored as int32)
     *   B : IntArray, row-major in global memory,
     *       transposed to col-major per 8-column panel in shared memory
     *   C : IntArray, row-major, s32 accumulator
     *
     * One workgroup = one warp (32 threads) = one 16x16 output tile.
     * Each 16x16 tile requires two mma.sync calls (left + right 16x8 panels).
     *
     * Launch config:
     *   localSize  = 32
     *   globalSize = (M/16) * (N/16) * 32
     */
    public static void gemmMMAInt8(KernelContext ctx,
                                   ByteArray a, ByteArray b, IntArray c,
                                   int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        // Packed shared tiles: 2 bytes per b16 slot, stored as int pairs
        // A: 16×32 s8 → 16×16 b16 → 16 rows × 8 ints = 128
        // B: 8 cols × 32 k → 8 cols × 16 b16 → 8 rows × 8 ints = 64 per panel
        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);  // 32 * 8 / 4 = 64 ints
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * 8 / 4);

        int[] fragC0 = ctx.mmaFragmentInt(0);
        int[] fragC1 = ctx.mmaFragmentInt(0);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {

            // Cooperative load A: pack 2 adjacent bytes as b16 into one int pair
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
                int k_base = 2 * k_row;      // 0, 2, 4, ..., 30 — first of the pair of K-values

                // Left panel (cols tileCol..tileCol+7), columns j_base, j_base+1
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

            // Load A fragment (4 × b32, each packing 4 × s8)
            byte[] fragA = ctx.mmaLoadAInt8(aTile, WMMA_K);

            // Left panel
            byte[] fragB0 = ctx.mmaLoadBInt8(bTile0, WMMA_K);
            fragC0 = ctx.mmaInt8(fragA, fragB0, fragC0, MMAShape.M16N8K32);

            // Right panel
            byte[] fragB1 = ctx.mmaLoadBInt8(bTile1, WMMA_K);
            fragC1 = ctx.mmaInt8(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        // Store results
        ctx.mmaStoreInt(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStoreInt(fragC1, c, tileRow, tileCol + 8, dimN);

    }

    // -----------------------------------------------------------------------
    // CPU reference
    // -----------------------------------------------------------------------

    private static void gemmReferenceInt8(
            ByteArray a, ByteArray b, IntArray ref,
            int dimM, int dimN, int dimK) {
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int sum = 0;
                for (int k = 0; k < dimK; k++) {
                    sum += a.get(i * dimK + k) * b.get(k * dimN + j);
                }
                ref.set(i * dimN + j, sum);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Minimum case: M=16, N=16, K=32.
     * One warp, two mma.sync calls (left + right panel), one K iteration.
     */
    @Test
    public void testGemmInt8Minimal() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 32);
    }

    /**
     * K=64: two K-iterations.
     */
    @Test
    public void testGemmInt8DeepK() throws TornadoExecutionPlanException {
        runGemmTest(16, 16, 64);
    }

    /**
     * Four output tiles (2×2 grid of warps).
     */
    @Test
    public void testGemmInt8MultiTile() throws TornadoExecutionPlanException {
        runGemmTest(32, 32, 64);
    }

    /**
     * Wide N: one row of tiles, four warp columns.
     */
    @Test
    public void testGemmInt8WideN() throws TornadoExecutionPlanException {
        runGemmTest(16, 64, 64);
    }

    /**
     * Larger M: multiple seqLen rows.
     */
    @Test
    public void testGemmInt8PrefillSlice() throws TornadoExecutionPlanException {
        runGemmTest(64, 64, 64);
    }

    /**
     * Identity test: A=identity, B=sequential → C should equal B.
     * Catches layout/permutation bugs.
     */
    @Test
    public void testGemmInt8Identity() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        int M = 16, N = 16, K = 32;

        ByteArray a = new ByteArray(M * K);
        for (int i = 0; i < Math.min(M, K); i++)
            a.set(i * K + i, (byte) 1);  // identity (only first 16 rows have a 1)

        ByteArray b = new ByteArray(K * N);
        for (int i = 0; i < K * N; i++)
            b.set(i, (byte) ((i % 5) - 2));  // small values: -2, -1, 0, 1, 2

        IntArray c   = new IntArray(M * N);
        IntArray ref = new IntArray(M * N);
        gemmReferenceInt8(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K);
    }

    /**
     * All-ones test: every element should equal K.
     */
    @Test
    public void testGemmInt8AllOnes() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        int M = 16, N = 16, K = 32;

        ByteArray a = new ByteArray(M * K);
        ByteArray b = new ByteArray(K * N);
        for (int i = 0; i < M * K; i++) a.set(i, (byte) 1);
        for (int i = 0; i < K * N; i++) b.set(i, (byte) 1);

        IntArray c   = new IntArray(M * N);
        IntArray ref = new IntArray(M * N);
        gemmReferenceInt8(a, b, ref, M, N, K);

        runGemmTestWithData(a, b, c, ref, M, N, K);
    }

    // -----------------------------------------------------------------------
    // Runner
    // -----------------------------------------------------------------------

    private void runGemmTest(int dimM, int dimN, int dimK) throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        ByteArray a = randomInt8Array(dimM * dimK);
        ByteArray b = randomInt8Array(dimK * dimN);
        IntArray c = new IntArray(dimM * dimN);

        IntArray ref = new IntArray(dimM * dimN);
        gemmReferenceInt8(a, b, ref, dimM, dimN, dimK);

        runGemmTestWithData(a, b, c, ref, dimM, dimN, dimK);
    }

    private void runGemmTestWithData(ByteArray a, ByteArray b, IntArray c, IntArray ref,
                                     int dimM, int dimN, int dimK)
            throws TornadoExecutionPlanException {

        int numWarps   = (dimM / WMMA_M) * (dimN / WMMA_N);
        int globalSize = numWarps * WARP_SIZE;
        int localSize  = WARP_SIZE;

        WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
        workerGrid.setLocalWork(localSize, 1, 1);

        GridScheduler gridScheduler = new GridScheduler("mma_int8_test.gemm", workerGrid);

        KernelContext ctx = new KernelContext();

        TaskGraph tg = new TaskGraph("mma_int8_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("gemm", TestMatrixMultiplicationMMAInt8::gemmMMAInt8,
                        ctx, a, b, c, dimM, dimN, dimK)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withGridScheduler(gridScheduler)
                    .execute();
        }

        // Assert correctness — int8 MMA is exact (no floating-point rounding)
        for (int i = 0; i < dimM; i++) {
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
                assertEquals(String.format("C[%d][%d]", i, j),
                        ref.get(idx), c.get(idx));
            }
        }
    }

    @Test
    public void testGemmInt8DistinctKInB() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        int M = 16, N = 16, K = 32;

        // A all ones
        ByteArray a = new ByteArray(M * K);
        for (int idx = 0; idx < M * K; idx++) {
            a.set(idx, (byte) 1);
        }

        // B[k][j] = (k % 8) + 1  → values cycle 1..8, four times across K=32
        ByteArray b = new ByteArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, (byte) ((k % 8) + 1));
            }
        }

        IntArray c   = new IntArray(M * N);
        IntArray ref = new IntArray(M * N);
        gemmReferenceInt8(a, b, ref, M, N, K);

        // Expected: sum_k B[k][j] = 4 * (1+2+...+8) = 4 * 36 = 144 everywhere

        runGemmTestWithData(a, b, c, ref, M, N, K);
    }

    @Test
    public void testGemmInt8DistinctAB() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        int M = 16, N = 16, K = 32;

        // A[i][k] = (k % 8) + 1   (A is constant across rows, varies across k:
        //                          values cycle 1..8, four times across K=32)
        ByteArray a = new ByteArray(M * K);
        for (int i = 0; i < M; i++) {
            for (int k = 0; k < K; k++) {
                a.set(i * K + k, (byte) ((k % 8) + 1));
            }
        }

        // B[k][j] = (k % 8) + 1   (B is constant across columns, same K-pattern as A)
        ByteArray b = new ByteArray(K * N);
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < N; j++) {
                b.set(k * N + j, (byte) ((k % 8) + 1));
            }
        }

        IntArray c   = new IntArray(M * N);
        IntArray ref = new IntArray(M * N);
        gemmReferenceInt8(a, b, ref, M, N, K);

        // Expected: C[i][j] = sum_k (k%8 + 1)^2
        //                   = 4 * (1 + 4 + 9 + ... + 64) = 4 * 204 = 816 everywhere

        runGemmTestWithData(a, b, c, ref, M, N, K);
    }


    /**
     * Creates an IntArray with random values in [-3, 3].
     * Small range avoids int8 overflow in dot products while still
     * exercising sign handling.
     */
    private static ByteArray randomInt8Array(int size) {
        Random rng = new Random(42);  // fixed seed for reproducibility
        ByteArray arr = new ByteArray(size);
        for (int i = 0; i < size; i++) {
            arr.set(i, (byte) (rng.nextInt(7) - 3));  // -3 to 3
        }
        return arr;
    }

    // -----------------------------------------------------------------------
    // Reading the s32 accumulator fragment in registers
    // -----------------------------------------------------------------------

    /**
     * The int8 counterpart of the fp16 fragment reads in {@link TestMatrixMultiplicationMMA}:
     * each lane scales its four s32 accumulator elements and writes them out itself instead of
     * handing the fragment to {@code mmaStoreInt}. Output is in fragment order,
     * {@code out[lane * 4 + i]}, so the test pins the lane mapping as well as the values.
     */
    public static void scaleIntFragmentInRegisters(KernelContext ctx, ByteArray a, ByteArray b,
                                                   IntArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile = ctx.allocateIntLocalArray(WMMA_K * MMA_N / 4);

        loadInt8Tiles(ctx, a, b, aTile, bTile, lane);

        int[] acc = ctx.mmaFragmentInt(0);
        acc = ctx.mmaInt8(ctx.mmaLoadAInt8(aTile, WMMA_K), ctx.mmaLoadBInt8(bTile, WMMA_K), acc,
                MMAShape.M16N8K32);

        out.set(lane * 4, acc[0] * 2);
        out.set(lane * 4 + 1, acc[1] * 2);
        out.set(lane * 4 + 2, acc[2] * 2);
        out.set(lane * 4 + 3, acc[3] * 2);
    }

    /**
     * Indexing an A operand fragment. Its Java {@code byte[]} element type does not describe
     * the packed b32 lanes it holds, so the access is refused rather than compiled into
     * something that reads the wrong thing.
     */
    public static void readOperandFragment(KernelContext ctx, ByteArray a, ByteArray b,
                                           IntArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile = ctx.allocateIntLocalArray(WMMA_K * MMA_N / 4);

        loadInt8Tiles(ctx, a, b, aTile, bTile, lane);

        byte[] fragA = ctx.mmaLoadAInt8(aTile, WMMA_K);
        out.set(lane, fragA[0]);
    }

    /** Cooperative load of one 16x32 A tile and one 32x8 B panel, in the packed b32 layout. */
    private static void loadInt8Tiles(KernelContext ctx, ByteArray a, ByteArray b,
                                      int[] aTile, int[] bTile, int lane) {
        for (int idx = lane; idx < (WMMA_M * WMMA_K) / 4; idx += WARP_SIZE) {
            int elemBase = idx * 4;
            int r = elemBase / WMMA_K;
            int kk = elemBase % WMMA_K;
            int base = r * WMMA_K + kk;
            aTile[r * (WMMA_K / 4) + kk / 4] = (a.get(base) & 0xFF)
                    | ((a.get(base + 1) & 0xFF) << 8)
                    | ((a.get(base + 2) & 0xFF) << 16)
                    | ((a.get(base + 3) & 0xFF) << 24);
        }
        for (int idx = lane; idx < 64; idx += WARP_SIZE) {
            int k_row = idx / 4;
            int j_pair = idx % 4;
            int j_base = j_pair * 2;
            int k_base = 2 * k_row;
            int b0 = b.get(k_base * MMA_N + j_base) & 0xFF;
            int b1 = b.get((k_base + 1) * MMA_N + j_base) & 0xFF;
            int b2 = b.get(k_base * MMA_N + j_base + 1) & 0xFF;
            int b3 = b.get((k_base + 1) * MMA_N + j_base + 1) & 0xFF;
            bTile[k_row * 4 + j_pair] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
        ctx.localBarrier();
    }

    @Test
    public void testIntFragmentElementReads() throws TornadoExecutionPlanException {
        ByteArray a = randomInt8Array(WMMA_M * WMMA_K);
        ByteArray b = randomInt8Array(WMMA_K * MMA_N);
        IntArray out = runFragmentKernel(TestMatrixMultiplicationMMAInt8::scaleIntFragmentInRegisters, a, b);

        for (int lane = 0; lane < WARP_SIZE; lane++) {
            for (int i = 0; i < 4; i++) {
                int m = lane / 4 + 8 * (i / 2);
                int n = (lane % 4) * 2 + (i % 2);
                int expected = 0;
                for (int k = 0; k < WMMA_K; k++) {
                    expected += a.get(m * WMMA_K + k) * b.get(k * MMA_N + n);
                }
                assertEquals("lane " + lane + " element " + i, 2 * expected, out.get(lane * 4 + i));
            }
        }
    }

    /** Only the accumulator fragment is indexable; an A/B fragment is refused by name. */
    @Test(expected = TornadoBailoutRuntimeException.class)
    public void testOperandFragmentIndexIsRejected() throws TornadoExecutionPlanException {
        runFragmentKernel(TestMatrixMultiplicationMMAInt8::readOperandFragment,
                randomInt8Array(WMMA_M * WMMA_K), randomInt8Array(WMMA_K * MMA_N));
    }

    /** Runs {@code kernel} over a single warp on one 16x32 by 32x8 tile pair. */
    private IntArray runFragmentKernel(
            TornadoFunctions.Task4<KernelContext, ByteArray, ByteArray, IntArray> kernel,
            ByteArray a, ByteArray b) throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        IntArray out = new IntArray(WMMA_M * MMA_N);
        out.init(0);

        WorkerGrid1D workerGrid = new WorkerGrid1D(WARP_SIZE);
        workerGrid.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("mma_int8_frag_test.k", workerGrid);

        TaskGraph tg = new TaskGraph("mma_int8_frag_test")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, out)
                .task("k", kernel, new KernelContext(), a, b, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }
        return out;
    }

    /** Reads an s32 accumulator after a runtime-controlled MMA loop. */
    public static void readLoopCarriedIntFragment(KernelContext ctx, ByteArray a,
                                                  ByteArray b, IntArray iterations, IntArray out) {
        int lane = ctx.localIdx;
        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile = ctx.allocateIntLocalArray(WMMA_K * MMA_N / 4);
        loadInt8Tiles(ctx, a, b, aTile, bTile, lane);
        byte[] fragA = ctx.mmaLoadAInt8(aTile, WMMA_K);
        byte[] fragB = ctx.mmaLoadBInt8(bTile, WMMA_K);
        int[] acc = ctx.mmaFragmentInt(1);
        for (int k = 0; k < iterations.get(0); k++) {
            acc = ctx.mmaInt8(fragA, fragB, acc, MMAShape.M16N8K32);
        }
        out.set(lane * 4, acc[0] * 2);
        out.set(lane * 4 + 1, acc[1] * 2);
        out.set(lane * 4 + 2, acc[2] * 2);
        out.set(lane * 4 + 3, acc[3] * 2);
    }

    @Test
    public void testLoopCarriedIntFragmentElementReads() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        ByteArray a = randomInt8Array(WMMA_M * WMMA_K);
        ByteArray b = randomInt8Array(WMMA_K * MMA_N);
        IntArray iterations = new IntArray(1);
        IntArray out = new IntArray(WARP_SIZE * 4);
        WorkerGrid1D worker = new WorkerGrid1D(WARP_SIZE);
        worker.setLocalWork(WARP_SIZE, 1, 1);
        TaskGraph graph = new TaskGraph("mma_int_loop_read")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, iterations)
                .task("k", TestMatrixMultiplicationMMAInt8::readLoopCarriedIntFragment,
                        new KernelContext(), a, b, iterations, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("mma_int_loop_read.k", worker));
            for (int count : new int[] { 0, 1, 3 }) {
                iterations.set(0, count);
                plan.execute();
                for (int lane = 0; lane < WARP_SIZE; lane++) {
                    for (int i = 0; i < 4; i++) {
                        int m = lane / 4 + 8 * (i / 2);
                        int n = (lane % 4) * 2 + i % 2;
                        int dot = 0;
                        for (int k = 0; k < WMMA_K; k++) {
                            dot += a.get(m * WMMA_K + k) * b.get(k * MMA_N + n);
                        }
                        assertEquals("iterations " + count + " lane " + lane + " element " + i,
                                2 * (1 + count * dot), out.get(lane * 4 + i));
                    }
                }
            }
        }
    }
}
