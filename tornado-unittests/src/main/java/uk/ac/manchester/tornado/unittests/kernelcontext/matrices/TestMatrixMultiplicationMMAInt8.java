/*
 * Unit tests for int8 Tensor Core MMA via KernelContext.
 *
 * Tests mma.sync.aligned.m16n8k32.row.col.s32.s8.s8.s32 on the PTX backend.
 *
 * Each 16×16 output tile requires two mma.sync calls (left + right 16×8 panels),
 * same as the f16 path but with K=32 per MMA instruction.
 *
 * Usage:
 *   tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMAInt8
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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;

public class TestMatrixMultiplicationMMAInt8 extends TornadoTestBase {

    static final int WMMA_M    = 16;
    static final int WMMA_N    = 16;   // covered by two m16n8k32 calls
    static final int WMMA_K    = 32;   // K=32 for int8 MMA
    static final int WARP_SIZE = 32;

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

        // Print first 4 rows for diagnostic
        System.out.println("=== First 4 rows: expected | actual ===");
        for (int i = 0; i < Math.min(4, dimM); i++) {
            StringBuilder exp = new StringBuilder("ref[" + i + "]: ");
            StringBuilder act = new StringBuilder("act[" + i + "]: ");
            for (int j = 0; j < dimN; j++) {
                int idx = i * dimN + j;
                exp.append(String.format("%7d", ref.get(idx)));
                act.append(String.format("%7d", c.get(idx)));
            }
            System.out.println(exp);
            System.out.println(act);
            System.out.println();
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
}
