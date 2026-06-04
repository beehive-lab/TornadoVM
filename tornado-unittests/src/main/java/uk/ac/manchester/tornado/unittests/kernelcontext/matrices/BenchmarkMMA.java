package uk.ac.manchester.tornado.unittests.kernelcontext.matrices;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.HalfFloat;

import org.junit.Test;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.util.Arrays;
import java.util.Random;

public class BenchmarkMMA {

    // -----------------------------------------------------------------------
    // Tuning constants
    // -----------------------------------------------------------------------
    static final int WARMUP    = 10;
    static final int MEASURED  = 50;
    static final int WARP_SIZE = 32;

    // f16 MMA constants
    static final int WMMA_M_F16 = 16;
    static final int WMMA_N_F16 = 16;
    static final int WMMA_K_F16 = 16;

    // int8 MMA constants
    static final int WMMA_M_I8 = 16;
    static final int WMMA_N_I8 = 16;
    static final int WMMA_K_I8 = 32;

    // Baseline tiled matmul
    static final int TILE_SIZE = 16;

    // =====================================================================
    //  KERNELS
    // =====================================================================

    // -----------------------------------------------------------------------
    // 1. f16 MMA Kernel (tensor cores, mma.sync.m16n8k16)
    // ----------------------------------------------------------------------

    static final int WMMA_M  = 16;
    static final int WMMA_N  = 16;  // covered by two m16n8k16 calls
    static final int WMMA_K  = 16;

    public static void gemmMMAf16(KernelContext ctx,
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

            // Cooperative load B: col-major, pack 2 adjacent k-values per int
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

    // -----------------------------------------------------------------------
    // 2. int8 MMA Kernel (tensor cores, mma.sync.m16n8k32)
    // -----------------------------------------------------------------------
    public static void gemmMMAint8(KernelContext ctx,
                                   ByteArray a, ByteArray b, IntArray c,
                                   int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N_I8;
        int tileRow   = (warpId / numTilesN) * WMMA_M_I8;
        int tileCol   = (warpId % numTilesN) * WMMA_N_I8;

        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M_I8 * WMMA_K_I8 / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(8 * WMMA_K_I8 / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(8 * WMMA_K_I8 / 2);

        int[] fragC0 = ctx.mmaFragmentInt(0);
        int[] fragC1 = ctx.mmaFragmentInt(0);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K_I8) {

            // Cooperative load A
            for (int idx = lane; idx < (WMMA_M_I8 * WMMA_K_I8) / 4; idx += WARP_SIZE) {
                int elemBase = idx * 4;
                int r  = elemBase / WMMA_K_I8;
                int kk = elemBase % WMMA_K_I8;
                int base = (tileRow + r) * dimK + kBase + kk;
                int packed = (a.get(base)     & 0xFF)
                        | ((a.get(base + 1) & 0xFF) << 8)
                        | ((a.get(base + 2) & 0xFF) << 16)
                        | ((a.get(base + 3) & 0xFF) << 24);
                aTile[r * (WMMA_K_I8 / 4) + kk / 4] = packed;
            }

            // Cooperative load B - side-by-side row-major (this code is fine as-is)
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int r = idx / 8;
                int n = idx % 8;
                int klo, jbase;
                if (n < 4) {
                    klo   = 2 * r;
                    jbase = 2 * n;
                } else {
                    klo   = 2 * r + 16;
                    jbase = 2 * (n - 4);
                }

                int bL0 = b.get((kBase + klo)     * dimN + tileCol + jbase)     & 0xFF;
                int bL1 = b.get((kBase + klo + 1) * dimN + tileCol + jbase)     & 0xFF;
                int bL2 = b.get((kBase + klo)     * dimN + tileCol + jbase + 1) & 0xFF;
                int bL3 = b.get((kBase + klo + 1) * dimN + tileCol + jbase + 1) & 0xFF;
                bTile0[r * 8 + n] = bL0 | (bL1 << 8) | (bL2 << 16) | (bL3 << 24);

                int bR0 = b.get((kBase + klo)     * dimN + tileCol + 8 + jbase)     & 0xFF;
                int bR1 = b.get((kBase + klo + 1) * dimN + tileCol + 8 + jbase)     & 0xFF;
                int bR2 = b.get((kBase + klo)     * dimN + tileCol + 8 + jbase + 1) & 0xFF;
                int bR3 = b.get((kBase + klo + 1) * dimN + tileCol + 8 + jbase + 1) & 0xFF;
                bTile1[r * 8 + n] = bR0 | (bR1 << 8) | (bR2 << 16) | (bR3 << 24);
            }

            ctx.localBarrier();

            byte[] fragA = ctx.mmaLoadAInt8(aTile, WMMA_K_I8);
            byte[] fragB0 = ctx.mmaLoadBInt8(bTile0, WMMA_K_I8);
            fragC0 = ctx.mmaInt8(fragA, fragB0, fragC0, MMAShape.M16N8K32);
            byte[] fragB1 = ctx.mmaLoadBInt8(bTile1, WMMA_K_I8);
            fragC1 = ctx.mmaInt8(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        ctx.mmaStoreInt(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStoreInt(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // -----------------------------------------------------------------------
    // 3. Baseline Kernel (tiled matmul, no tensor cores, f32 throughout)
    // -----------------------------------------------------------------------
    public static void gemmBaseline(KernelContext ctx,
                                    HalfFloatArray a, HalfFloatArray b, FloatArray c,
                                    int dimM, int dimN, int dimK) {

        int row = ctx.globalIdx;
        int col = ctx.globalIdy;

        float[] aTile = ctx.allocateFloatLocalArray(TILE_SIZE * TILE_SIZE);
        float[] bTile = ctx.allocateFloatLocalArray(TILE_SIZE * TILE_SIZE);

        int localRow = ctx.localIdx;
        int localCol = ctx.localIdy;

        float sum = 0.0f;
        for (int t = 0; t < dimK; t += TILE_SIZE) {
            if (row < dimM && (t + localCol) < dimK) {
                aTile[localRow * TILE_SIZE + localCol] = a.get(row * dimK + t + localCol).getFloat32();
            } else {
                aTile[localRow * TILE_SIZE + localCol] = 0.0f;
            }
            if ((t + localRow) < dimK && col < dimN) {
                bTile[localRow * TILE_SIZE + localCol] = b.get((t + localRow) * dimN + col).getFloat32();
            } else {
                bTile[localRow * TILE_SIZE + localCol] = 0.0f;
            }
            ctx.localBarrier();

            for (int k = 0; k < TILE_SIZE; k++) {
                sum += aTile[localRow * TILE_SIZE + k] * bTile[k * TILE_SIZE + localCol];
            }
            ctx.localBarrier();
        }

        if (row < dimM && col < dimN) {
            c.set(row * dimN + col, sum);
        }
    }

    // =====================================================================
    //  CPU REFERENCES
    // =====================================================================

    private static void gemmReferenceF16(HalfFloatArray a, HalfFloatArray b, FloatArray ref,
                                         int M, int N, int K) {
        for (int i = 0; i < M; i++)
            for (int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < K; k++)
                    sum += a.get(i * K + k).getFloat32() * b.get(k * N + j).getFloat32();
                ref.set(i * N + j, sum);
            }
    }

    private static void gemmReferenceInt8(ByteArray a, ByteArray b, IntArray ref,
                                          int M, int N, int K) {
        for (int i = 0; i < M; i++)
            for (int j = 0; j < N; j++) {
                int sum = 0;
                for (int k = 0; k < K; k++)
                    sum += a.get(i * K + k) * b.get(k * N + j);
                ref.set(i * N + j, sum);
            }
    }

    // =====================================================================
    //  BENCHMARK HELPERS
    // =====================================================================

    private static double[] runTimed(TornadoExecutionPlan plan, int warmup, int measured)
            throws TornadoExecutionPlanException {
        for (int i = 0; i < warmup; i++) plan.execute();

        double[] times = new double[measured];
        for (int i = 0; i < measured; i++) {
            long start = System.nanoTime();
            plan.execute();
            long end = System.nanoTime();
            times[i] = (end - start) / 1e9;
        }
        Arrays.sort(times);
        return times;
    }

    private static void printResults(String label, double[] times, double gflops) {
        double min    = times[0];
        double median = times[times.length / 2];
        double mean   = Arrays.stream(times).average().orElse(0);

        System.out.printf("  %s:%n", label);
        System.out.printf("    min    = %.4f ms  →  %7.2f GFLOP/s%n", min * 1e3, gflops / min);
        System.out.printf("    median = %.4f ms  →  %7.2f GFLOP/s%n", median * 1e3, gflops / median);
        System.out.printf("    mean   = %.4f ms  →  %7.2f GFLOP/s%n", mean * 1e3, gflops / mean);
    }

    // =====================================================================
    //  BENCHMARK RUNNER
    // =====================================================================

    private void benchmarkSize(int M, int N, int K) throws TornadoExecutionPlanException {
        System.out.printf("%n====== GEMM %d x %d x %d ======%n", M, N, K);

        Random rng = new Random(42);
        double gflops = 2.0 * M * N * K / 1e9;

        // --- Shared f16 data ---
        HalfFloatArray aF16 = new HalfFloatArray(M * K);
        HalfFloatArray bF16 = new HalfFloatArray(K * N);
        for (int i = 0; i < M * K; i++)
            aF16.set(i, new HalfFloat((float) (rng.nextDouble() * 2.0 - 1.0)));
        for (int i = 0; i < K * N; i++)
            bF16.set(i, new HalfFloat((float) (rng.nextDouble() * 2.0 - 1.0)));

        // --- 1. f16 MMA benchmark ---
        {
            FloatArray c = new FloatArray(M * N);
            int numWarps   = (M / WMMA_M_F16) * (N / WMMA_N_F16);
            int globalSize = numWarps * WARP_SIZE;

            WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
            workerGrid.setLocalWork(WARP_SIZE, 1, 1);
            GridScheduler grid = new GridScheduler("bench_mma_f16.gemm", workerGrid);
            KernelContext ctx = new KernelContext();

            TaskGraph tg = new TaskGraph("bench_mma_f16")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, aF16, bF16)
                    .task("gemm", BenchmarkMMA::gemmMMAf16, ctx, aF16, bF16, c, M, N, K)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            ImmutableTaskGraph itg = tg.snapshot();
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
                plan.withGridScheduler(grid);
                double[] times = runTimed(plan, WARMUP, MEASURED);
                printResults("f16 MMA (tensor core)", times, gflops);

                FloatArray ref = new FloatArray(M * N);
                gemmReferenceF16(aF16, bF16, ref, M, N, K);
                float err = Math.abs(ref.get(0) - c.get(0));
                System.out.printf("    correctness: ref[0]=%.4f  gpu[0]=%.4f  err=%.4f%n",
                        ref.get(0), c.get(0), err);
            }
        }

        // --- 2. int8 MMA benchmark (only if K is a multiple of 32) ---
        if (K % WMMA_K_I8 == 0) {
            ByteArray aI8 = new ByteArray(M * K);
            ByteArray bI8 = new ByteArray(K * N);
            for (int i = 0; i < M * K; i++)
                aI8.set(i, (byte) (rng.nextInt(7) - 3));   // [-3, 3]
            for (int i = 0; i < K * N; i++)
                bI8.set(i, (byte) (rng.nextInt(7) - 3));

            IntArray c = new IntArray(M * N);
            int numWarps   = (M / WMMA_M_I8) * (N / WMMA_N_I8);
            int globalSize = numWarps * WARP_SIZE;

            WorkerGrid1D workerGrid = new WorkerGrid1D(globalSize);
            workerGrid.setLocalWork(WARP_SIZE, 1, 1);
            GridScheduler grid = new GridScheduler("bench_mma_i8.gemm", workerGrid);
            KernelContext ctx = new KernelContext();

            TaskGraph tg = new TaskGraph("bench_mma_i8")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, aI8, bI8)
                    .task("gemm", BenchmarkMMA::gemmMMAint8, ctx, aI8, bI8, c, M, N, K)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            ImmutableTaskGraph itg = tg.snapshot();
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
                plan.withGridScheduler(grid);
                double[] times = runTimed(plan, WARMUP, MEASURED);
                printResults("int8 MMA (tensor core)", times, gflops);

                IntArray ref = new IntArray(M * N);
                gemmReferenceInt8(aI8, bI8, ref, M, N, K);
                int err = Math.abs(ref.get(0) - c.get(0));
                System.out.printf("    correctness: ref[0]=%d  gpu[0]=%d  err=%d%n",
                        ref.get(0), c.get(0), err);
            }
        } else {
            System.out.printf("  int8 MMA: SKIPPED (K=%d not a multiple of %d)%n", K, WMMA_K_I8);
        }

        // --- 3. Baseline benchmark (2D grid, tiled, no tensor cores) ---
        {
            FloatArray c = new FloatArray(M * N);

            WorkerGrid workerGrid = new WorkerGrid2D(M, N);
            workerGrid.setLocalWork(TILE_SIZE, TILE_SIZE, 1);
            GridScheduler grid = new GridScheduler("bench_base.gemm", workerGrid);
            KernelContext ctx = new KernelContext();

            TaskGraph tg = new TaskGraph("bench_base")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, aF16, bF16)
                    .task("gemm", BenchmarkMMA::gemmBaseline, ctx, aF16, bF16, c, M, N, K)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            ImmutableTaskGraph itg = tg.snapshot();
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
                plan.withGridScheduler(grid);
                double[] times = runTimed(plan, WARMUP, MEASURED);
                printResults("Baseline (tiled, no TC)", times, gflops);
            }
        }
    }

    // =====================================================================
    //  TEST ENTRY POINTS
    // =====================================================================

    /** Small square — sanity check, launch-overhead dominated. */
    @Test
    public void bench_128x128x128() throws TornadoExecutionPlanException {
        benchmarkSize(128, 128, 128);
    }

    /** Medium square — starts showing compute throughput. */
    @Test
    public void bench_256x256x256() throws TornadoExecutionPlanException {
        benchmarkSize(256, 256, 256);
    }

    /** 512×512 — good balance of tiles and compute. */
    @Test
    public void bench_512x512x512() throws TornadoExecutionPlanException {
        benchmarkSize(512, 512, 512);
    }

    /** 1024×1024 — large enough to saturate tensor cores. */
    @Test
    public void bench_1024x1024x1024() throws TornadoExecutionPlanException {
        benchmarkSize(1024, 1024, 1024);
    }

    /** Attention QKV projection: seqLen=128 × headDim=128 × 3·headDim=384. */
    @Test
    public void bench_attn_qkv_128x384x128() throws TornadoExecutionPlanException {
        benchmarkSize(128, 384, 128);
    }

    /** FFN single projection: 128×4096×4096. */
    @Test
    public void bench_ffn_slice_128x4096x4096() throws TornadoExecutionPlanException {
        benchmarkSize(128, 4096, 4096);
    }

    /** Prefill attention scores: seqLen×seqLen×headDim = 256×256×128. */
    @Test
    public void bench_attn_scores_256x256x128() throws TornadoExecutionPlanException {
        benchmarkSize(256, 256, 128);
    }

    /** 2048×2048 — stress test for large matrix sizes. */
    @Test
    public void bench_2048x2048x2048() throws TornadoExecutionPlanException {
        benchmarkSize(2048, 2048, 2048);
    }

    /** Q8_0 quantization shape: seqLen=256 × K=4096 × hiddenDim=4096. */
    @Test
    public void bench_q8_ffn_256x4096x4096() throws TornadoExecutionPlanException {
        benchmarkSize(256, 4096, 4096);
    }
}
