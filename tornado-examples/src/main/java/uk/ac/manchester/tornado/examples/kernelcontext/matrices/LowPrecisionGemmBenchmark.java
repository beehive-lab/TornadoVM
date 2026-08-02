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
package uk.ac.manchester.tornado.examples.kernelcontext.matrices;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.BFloat16;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Low-precision GEMM benchmark: the same {@code C[M,N] = A[M,K] * B[K,N]} computed six ways, all
 * through {@link KernelContext}, timed back-to-back so the tensor-core and launch-overhead
 * effects are directly comparable.
 *
 * <ol>
 * <li><b>fp32 shared-memory tiled</b> -- classic tiled GEMM, no tensor cores. The baseline.</li>
 * <li><b>fp16 MMA</b> -- {@code mmaLoadA}/{@code mmaLoadB}/{@code mma} on {@code M16N8K16}
 * tensor-core fragments, fp32 accumulate.</li>
 * <li><b>bf16 MMA</b> -- identical fragment plumbing, {@code mmaBF16} reinterprets the same
 * register tuples as bf16. Wider exponent, fewer mantissa bits than fp16.</li>
 * <li><b>fp16 MMA + cp.async</b> -- {@code asyncCopyToLocal}/{@code asyncCopyCommit}/
 * {@code asyncCopyWaitGroup}: global-to-shared staging bypassing registers (Ampere
 * {@code cp.async}).</li>
 * <li><b>fp16 MMA + CUDA Graphs</b> -- same kernel, executed under {@code withCUDAGraph()} so the
 * launch sequence is captured once and replayed. Isolates per-launch CPU overhead from kernel
 * time; the win grows as kernels get shorter and more numerous.</li>
 * <li><b>int8 MMA</b> -- {@code mmaLoadAInt8}/{@code mmaLoadBInt8}/{@code mmaInt8} on
 * {@code M16N8K32}, s32 accumulate. Twice the K-depth per instruction of the fp16 shape, since
 * twice as many 8-bit operands fit the same fragment registers. Integer GEMM is exact, so its
 * reported error is a true correctness check, not a rounding measurement.</li>
 * <li><b>FP8 E4M3 MMA</b> -- {@code M16N8K32}. Requires compute capability &gt;= 8.9 (Ada/Hopper);
 * on older hardware TornadoVM raises {@code TornadoDeviceFP8NotSupported} and this stage reports
 * as skipped rather than failing.</li>
 * </ol>
 *
 * <p>
 * On multi-streaming: TornadoVM's CUDA backend already runs transfers and compute on separate,
 * NVTX-labelled CUDA streams (DEFAULT / H2D / COMPUTE / D2H, see {@code CUDACommandQueue}), but
 * that pipeline is managed internally -- there is no user-facing stream API on
 * {@code TornadoExecutionPlan} to schedule work onto explicit streams. {@code withConcurrentDevices()}
 * is about multi-DEVICE execution, not multi-stream within one GPU. The closest user-visible
 * control over that pipeline is the {@link DataTransferMode} on each argument, whose cost this
 * benchmark quantifies below.
 *
 * <p>
 * Every stage runs a warm-up execution first and reports STEADY-STATE time separately from the
 * first (JIT/NVRTC-compiling) run. Comparing first-run numbers is meaningless -- compilation
 * dominates by an order of magnitude and produces impressive-looking but entirely fictional
 * speedups.
 *
 * <h2>Reading the numbers: wall-clock understates the kernel speedup</h2>
 *
 * <p>
 * The times printed below are END-TO-END per execution, and each execution copies the 4 MB fp32
 * result back to the host ({@code transferToHost(EVERY_EXECUTION, ...)}). That transfer is the
 * same cost for every stage, so it compresses the ratios badly: the tensor-core stages look ~1.1x
 * to ~1.3x faster than the fp32 baseline, while the KERNELS themselves are 1.7x to 4.1x faster.
 * Always confirm a GEMM speedup claim against kernel-only timing.
 *
 * <p>
 * Measured on an RTX 3070 (sm_86, Ampere), CUDA 13.0, M=N=K=1024, mean of 20 steady-state
 * iterations, via {@code --enableProfiler console} ({@code TASK_KERNEL_TIME}):
 *
 * <pre>
 *   kernel                 device time    vs fp32
 *   gemmFp32Tiled            1334 us       1.00x
 *   gemmBf16MMA               771 us       1.73x
 *   gemmFp16MMA               757 us       1.76x
 *   gemmInt8MMA               446 us       2.99x
 *   gemmFp16MMACpAsync        327 us       4.09x
 * </pre>
 *
 * <p>
 * int8 lands between the 16-bit formats and the cp.async-pipelined fp16 kernel, which is the
 * expected shape: it halves the operand bytes AND doubles the K-depth per instruction
 * ({@code M16N8K32} vs {@code M16N8K16}), but still stages its tiles through the same
 * synchronous global-to-shared path that {@code cp.async} avoids.
 *
 * <p>
 * {@code nsys stats --report cuda_gpu_kern_sum} independently agrees on the three kernels it
 * records (1197 / 715 / 292 us). Note one profiler quirk worth knowing: in these runs nsys did
 * NOT record the {@code gemmBf16MMA} launches at all, even though the kernel demonstrably runs
 * (its cubin is emitted, its results carry bf16 rounding error, and TornadoVM's own profiler
 * times it). If a kernel goes missing from an nsys report, cross-check with
 * {@code --enableProfiler console} before concluding it never ran.
 *
 * <p>
 * On accuracy: {@code maxErr} is the largest deviation from an fp32 CPU reference. fp16 lands near
 * 0.006 and bf16 near 0.07 for these inputs -- roughly an order of magnitude worse, which is the
 * expected trade: bf16 keeps fp32's exponent range but has only 8 mantissa bits to fp16's 11.
 *
 * <p>
 * How to run:
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.matrices.LowPrecisionGemmBenchmark
 * </pre>
 *
 * <p>
 * Cross-check the wall-clock numbers against real GPU kernel time with Nsight Systems:
 * </p>
 *
 * <pre>
 * source setvars.sh
 * nsys profile --trace=cuda,nvtx -o lowprec-gemm \
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.matrices.LowPrecisionGemmBenchmark
 * nsys stats --report cuda_gpu_kern_sum lowprec-gemm.nsys-rep
 * </pre>
 *
 * <p>
 * Or for per-kernel timing straight from TornadoVM, without an nsys capture:
 * </p>
 *
 * <pre>
 * tornado --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.matrices.LowPrecisionGemmBenchmark
 * </pre>
 */
public class LowPrecisionGemmBenchmark {

    private static final int WMMA_M = 16;
    private static final int WMMA_N = 16;
    private static final int WMMA_K = 16;
    private static final int WMMA_K_INT8 = 32;
    private static final int WARP_SIZE = 32;

    private static final int M = 1024;
    private static final int N = 1024;
    private static final int K = 1024;

    private static final int FP32_TILE = 16;
    private static final int ITERATIONS = 20;

    // ------------------------------------------------------------------
    // 1. fp32 shared-memory tiled GEMM (baseline -- no tensor cores)
    // ------------------------------------------------------------------
    public static void gemmFp32Tiled(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int dimN, int dimK) {
        // Launched on a 2D worker grid: X spans N (columns), Y spans M (rows).
        int col = ctx.globalIdx;
        int row = ctx.globalIdy;
        int tx = ctx.localIdx;
        int ty = ctx.localIdy;

        float[] aTile = ctx.allocateFloatLocalArray(FP32_TILE * FP32_TILE);
        float[] bTile = ctx.allocateFloatLocalArray(FP32_TILE * FP32_TILE);

        float sum = 0.0f;
        for (int kBase = 0; kBase < dimK; kBase += FP32_TILE) {
            aTile[ty * FP32_TILE + tx] = a.get(row * dimK + kBase + tx);
            bTile[ty * FP32_TILE + tx] = b.get((kBase + ty) * dimN + col);
            ctx.localBarrier();

            for (int kk = 0; kk < FP32_TILE; kk++) {
                sum += aTile[ty * FP32_TILE + kk] * bTile[kk * FP32_TILE + tx];
            }
            ctx.localBarrier();
        }
        c.set(row * dimN + col, sum);
    }

    // ------------------------------------------------------------------
    // 2. fp16 MMA (tensor cores), fp32 accumulate
    // ------------------------------------------------------------------
    public static void gemmFp16MMA(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimN, int dimK) {
        int warpId = ctx.groupIdx;
        int lane = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow = (warpId / numTilesN) * WMMA_M;
        int tileCol = (warpId % numTilesN) * WMMA_N;

        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
                int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
                aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int jPair = idx % 4;
                int jBase = jPair * 2;

                int gL0 = (kBase + kRow) * dimN + tileCol + jBase;
                int loLeft = b.get(gL0).getHalfFloatValue() & 0xFFFF;
                int hiLeft = b.get(gL0 + 1).getHalfFloatValue() & 0xFFFF;
                bTile0[kRow * 4 + jPair] = loLeft | (hiLeft << 16);

                int gR0 = (kBase + kRow) * dimN + tileCol + 8 + jBase;
                int loRight = b.get(gR0).getHalfFloatValue() & 0xFFFF;
                int hiRight = b.get(gR0 + 1).getHalfFloatValue() & 0xFFFF;
                bTile1[kRow * 4 + jPair] = loRight | (hiRight << 16);
            }
            ctx.localBarrier();

            HalfFloat[] fragA = ctx.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);
            fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, WMMA_K);
            fragC1 = ctx.mma(fragA, fragB1, fragC1, MMAShape.M16N8K16);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // ------------------------------------------------------------------
    // 3. bf16 MMA -- same fragment plumbing, mmaBF16 reinterprets the bits
    // ------------------------------------------------------------------
    public static void gemmBf16MMA(KernelContext ctx, BFloat16Array a, BFloat16Array b, FloatArray c, int dimN, int dimK) {
        int warpId = ctx.groupIdx;
        int lane = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow = (warpId / numTilesN) * WMMA_M;
        int tileCol = (warpId % numTilesN) * WMMA_N;

        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase) & 0xFFFF;
                int hi = a.get(globalBase + 1) & 0xFFFF;
                aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int jPair = idx % 4;
                int jBase = jPair * 2;

                int gL0 = (kBase + kRow) * dimN + tileCol + jBase;
                int loLeft = b.get(gL0) & 0xFFFF;
                int hiLeft = b.get(gL0 + 1) & 0xFFFF;
                bTile0[kRow * 4 + jPair] = loLeft | (hiLeft << 16);

                int gR0 = (kBase + kRow) * dimN + tileCol + 8 + jBase;
                int loRight = b.get(gR0) & 0xFFFF;
                int hiRight = b.get(gR0 + 1) & 0xFFFF;
                bTile1[kRow * 4 + jPair] = loRight | (hiRight << 16);
            }
            ctx.localBarrier();

            HalfFloat[] fragA = ctx.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);
            fragC0 = ctx.mmaBF16(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, WMMA_K);
            fragC1 = ctx.mmaBF16(fragA, fragB1, fragC1, MMAShape.M16N8K16);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // ------------------------------------------------------------------
    // 4. fp16 MMA staged through cp.async instead of register round-trips
    // ------------------------------------------------------------------
    public static void gemmFp16MMACpAsync(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimN, int dimK) {
        int warpId = ctx.groupIdx;
        int lane = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow = (warpId / numTilesN) * WMMA_M;
        int tileCol = (warpId % numTilesN) * WMMA_N;

        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);
        int[] bTile1 = ctx.allocateIntLocalArray(WMMA_K * WMMA_N / 2);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                ctx.asyncCopyToLocal(aTile, r * (WMMA_K / 2) + kk / 2, a, globalBase);
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int jPair = idx % 4;
                int jBase = jPair * 2;
                int gLeft = (kBase + kRow) * dimN + tileCol + jBase;
                ctx.asyncCopyToLocal(bTile0, kRow * 4 + jPair, b, gLeft);
                int gRight = (kBase + kRow) * dimN + tileCol + 8 + jBase;
                ctx.asyncCopyToLocal(bTile1, kRow * 4 + jPair, b, gRight);
            }
            ctx.asyncCopyCommit();
            ctx.asyncCopyWaitGroup(0);
            ctx.localBarrier();

            HalfFloat[] fragA = ctx.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = ctx.mmaLoadB(bTile0, WMMA_K);
            fragC0 = ctx.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = ctx.mmaLoadB(bTile1, WMMA_K);
            fragC1 = ctx.mma(fragA, fragB1, fragC1, MMAShape.M16N8K16);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol, dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // ------------------------------------------------------------------
    // 6. int8 MMA -- s8 operands, s32 accumulate (M16N8K32)
    // ------------------------------------------------------------------
    public static void gemmInt8MMA(KernelContext ctx, Int8Array a, Int8Array b, IntArray c, int dimN, int dimK) {
        int warpId = ctx.groupIdx;
        int lane = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow = (warpId / numTilesN) * WMMA_M;
        int tileCol = (warpId % numTilesN) * WMMA_N;

        int[] aTile = ctx.allocateIntLocalArray(WMMA_M * WMMA_K_INT8 / 4);
        int[] bTile0 = ctx.allocateIntLocalArray(64);
        int[] bTile1 = ctx.allocateIntLocalArray(64);

        int[] fragC0 = ctx.mmaFragmentInt(0);
        int[] fragC1 = ctx.mmaFragmentInt(0);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K_INT8) {
            for (int idx = lane; idx < (WMMA_M * WMMA_K_INT8) / 4; idx += WARP_SIZE) {
                int elemBase = idx * 4;
                int r = elemBase / WMMA_K_INT8;
                int kk = elemBase % WMMA_K_INT8;
                int base = (tileRow + r) * dimK + kBase + kk;
                int packed = (a.get(base) & 0xFF) //
                        | ((a.get(base + 1) & 0xFF) << 8) //
                        | ((a.get(base + 2) & 0xFF) << 16) //
                        | ((a.get(base + 3) & 0xFF) << 24);
                aTile[r * (WMMA_K_INT8 / 4) + kk / 4] = packed;
            }

            // Each int packs 2 K-values x 2 J-values for the 8-bit fragment layout.
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int jPair = idx % 4;
                int jBase = jPair * 2;
                int kPair = 2 * kRow;

                int bL0 = b.get((kBase + kPair) * dimN + tileCol + jBase) & 0xFF;
                int bL1 = b.get((kBase + kPair + 1) * dimN + tileCol + jBase) & 0xFF;
                int bL2 = b.get((kBase + kPair) * dimN + tileCol + jBase + 1) & 0xFF;
                int bL3 = b.get((kBase + kPair + 1) * dimN + tileCol + jBase + 1) & 0xFF;
                bTile0[kRow * 4 + jPair] = bL0 | (bL1 << 8) | (bL2 << 16) | (bL3 << 24);

                int bR0 = b.get((kBase + kPair) * dimN + tileCol + 8 + jBase) & 0xFF;
                int bR1 = b.get((kBase + kPair + 1) * dimN + tileCol + 8 + jBase) & 0xFF;
                int bR2 = b.get((kBase + kPair) * dimN + tileCol + 8 + jBase + 1) & 0xFF;
                int bR3 = b.get((kBase + kPair + 1) * dimN + tileCol + 8 + jBase + 1) & 0xFF;
                bTile1[kRow * 4 + jPair] = bR0 | (bR1 << 8) | (bR2 << 16) | (bR3 << 24);
            }
            ctx.localBarrier();

            byte[] fragA = ctx.mmaLoadAInt8(aTile, WMMA_K_INT8);
            byte[] fragB0 = ctx.mmaLoadBInt8(bTile0, WMMA_K_INT8);
            fragC0 = ctx.mmaInt8(fragA, fragB0, fragC0, MMAShape.M16N8K32);
            byte[] fragB1 = ctx.mmaLoadBInt8(bTile1, WMMA_K_INT8);
            fragC1 = ctx.mmaInt8(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        ctx.mmaStoreInt(fragC0, c, tileRow, tileCol, dimN);
        ctx.mmaStoreInt(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    // ------------------------------------------------------------------
    // Host-side helpers
    // ------------------------------------------------------------------

    private static float[] randomMatrix(int elements, long seed) {
        Random rng = new Random(seed);
        float[] data = new float[elements];
        for (int i = 0; i < elements; i++) {
            data[i] = rng.nextFloat() * 2.0f - 1.0f;
        }
        return data;
    }

    /** Max |difference| against an fp32 CPU reference, sampled over the first few rows. */
    private static float maxError(FloatArray got, float[] refA, float[] refB, int sampleRows) {
        float worst = 0.0f;
        for (int i = 0; i < sampleRows; i++) {
            for (int j = 0; j < N; j += 97) { // stride to keep the check cheap
                float expected = 0.0f;
                for (int k = 0; k < K; k++) {
                    expected += refA[i * K + k] * refB[k * N + j];
                }
                worst = Math.max(worst, Math.abs(expected - got.get(i * N + j)));
            }
        }
        return worst;
    }

    /** Exact integer reference for the int8 stage -- any nonzero result is a genuine bug. */
    private static float maxErrorInt(IntArray got, byte[] refA, byte[] refB) {
        int worst = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < N; j += 97) {
                int expected = 0;
                for (int k = 0; k < K; k++) {
                    expected += refA[i * K + k] * refB[k * N + j];
                }
                worst = Math.max(worst, Math.abs(expected - got.get(i * N + j)));
            }
        }
        return worst;
    }

    private static double gflops(long nanos) {
        double seconds = nanos / 1e9;
        double ops = 2.0 * M * N * K; // one multiply + one add per element
        return (ops / seconds) / 1e9;
    }

    private static void report(String label, long steadyNs, long firstRunNs, float error) {
        System.out.printf("  %-26s  steady=%8.3f ms   %8.1f GFLOP/s   first-run=%8.1f ms   maxErr=%.4f%n", label, steadyNs / 1e6, gflops(steadyNs), firstRunNs / 1e6, error);
    }

    private static void reportSkipped(String label, String why) {
        System.out.printf("  %-26s  SKIPPED (%s)%n", label, why);
    }

    /** Runs the plan once (warm-up, pays JIT) then {@code ITERATIONS} more; returns {first, mean-steady}. */
    private static long[] timePlan(TornadoExecutionPlan plan, GridScheduler grid) throws TornadoExecutionPlanException {
        long start = System.nanoTime();
        plan.withGridScheduler(grid).execute();
        long firstRunNs = System.nanoTime() - start;

        long steadyStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            plan.withGridScheduler(grid).execute();
        }
        long steadyNs = (System.nanoTime() - steadyStart) / ITERATIONS;

        return new long[] { firstRunNs, steadyNs };
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        System.out.printf("Low-precision GEMM benchmark -- %dx%d x %dx%d, %d timed iterations per stage%n", M, K, K, N, ITERATIONS);
        System.out.println("Comparing steady-state times only; first-run includes JIT/NVRTC compilation.");
        System.out.println();

        float[] refA = randomMatrix(M * K, 11);
        float[] refB = randomMatrix(K * N, 23);

        KernelContext context = new KernelContext();
        int warpTiles = (M / WMMA_M) * (N / WMMA_N);
        int mmaGlobalSize = warpTiles * WARP_SIZE;

        // ---- 1. fp32 tiled baseline -------------------------------------------------
        FloatArray a32 = new FloatArray(M * K);
        FloatArray b32 = new FloatArray(K * N);
        FloatArray c32 = new FloatArray(M * N);
        for (int i = 0; i < M * K; i++) {
            a32.set(i, refA[i]);
        }
        for (int i = 0; i < K * N; i++) {
            b32.set(i, refB[i]);
        }

        WorkerGrid2D fp32Worker = new WorkerGrid2D(N, M);
        fp32Worker.setLocalWork(FP32_TILE, FP32_TILE, 1);
        GridScheduler fp32Grid = new GridScheduler("fp32.gemm", fp32Worker);
        TaskGraph fp32Graph = new TaskGraph("fp32") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a32, b32) //
                .task("gemm", LowPrecisionGemmBenchmark::gemmFp32Tiled, context, a32, b32, c32, N, K) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c32);

        System.out.println("Results:");
        long[] fp32Times;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(fp32Graph.snapshot())) {
            fp32Times = timePlan(plan, fp32Grid);
        }
        report("fp32 tiled (baseline)", fp32Times[1], fp32Times[0], maxError(c32, refA, refB, 2));

        // ---- 2. fp16 MMA ------------------------------------------------------------
        HalfFloatArray a16 = new HalfFloatArray(M * K);
        HalfFloatArray b16 = new HalfFloatArray(K * N);
        FloatArray c16 = new FloatArray(M * N);
        for (int i = 0; i < M * K; i++) {
            a16.set(i, new HalfFloat(refA[i]));
        }
        for (int i = 0; i < K * N; i++) {
            b16.set(i, new HalfFloat(refB[i]));
        }

        WorkerGrid1D mmaWorker = new WorkerGrid1D(mmaGlobalSize);
        mmaWorker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler fp16Grid = new GridScheduler("fp16.gemm", mmaWorker);
        TaskGraph fp16Graph = new TaskGraph("fp16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a16, b16) //
                .task("gemm", LowPrecisionGemmBenchmark::gemmFp16MMA, context, a16, b16, c16, N, K) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c16);

        long[] fp16Times;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(fp16Graph.snapshot())) {
            fp16Times = timePlan(plan, fp16Grid);
        }
        report("fp16 MMA (tensor core)", fp16Times[1], fp16Times[0], maxError(c16, refA, refB, 2));

        // ---- 3. bf16 MMA ------------------------------------------------------------
        BFloat16Array aBf = new BFloat16Array(M * K);
        BFloat16Array bBf = new BFloat16Array(K * N);
        FloatArray cBf = new FloatArray(M * N);
        for (int i = 0; i < M * K; i++) {
            aBf.set(i, BFloat16.bf16FromFloat(refA[i]));
        }
        for (int i = 0; i < K * N; i++) {
            bBf.set(i, BFloat16.bf16FromFloat(refB[i]));
        }

        WorkerGrid1D bf16Worker = new WorkerGrid1D(mmaGlobalSize);
        bf16Worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler bf16Grid = new GridScheduler("bf16.gemm", bf16Worker);
        TaskGraph bf16Graph = new TaskGraph("bf16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, aBf, bBf) //
                .task("gemm", LowPrecisionGemmBenchmark::gemmBf16MMA, context, aBf, bBf, cBf, N, K) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cBf);

        long[] bf16Times;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(bf16Graph.snapshot())) {
            bf16Times = timePlan(plan, bf16Grid);
        }
        report("bf16 MMA (tensor core)", bf16Times[1], bf16Times[0], maxError(cBf, refA, refB, 2));

        // ---- 4. fp16 MMA + cp.async -------------------------------------------------
        FloatArray cAsync = new FloatArray(M * N);
        WorkerGrid1D asyncWorker = new WorkerGrid1D(mmaGlobalSize);
        asyncWorker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler asyncGrid = new GridScheduler("cpasync.gemm", asyncWorker);
        TaskGraph asyncGraph = new TaskGraph("cpasync") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a16, b16) //
                .task("gemm", LowPrecisionGemmBenchmark::gemmFp16MMACpAsync, context, a16, b16, cAsync, N, K) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cAsync);

        long[] asyncTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(asyncGraph.snapshot())) {
            asyncTimes = timePlan(plan, asyncGrid);
        }
        report("fp16 MMA + cp.async", asyncTimes[1], asyncTimes[0], maxError(cAsync, refA, refB, 2));

        // ---- 5. fp16 MMA under a captured CUDA Graph --------------------------------
        FloatArray cGraph = new FloatArray(M * N);
        WorkerGrid1D graphWorker = new WorkerGrid1D(mmaGlobalSize);
        graphWorker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler cudaGraphGrid = new GridScheduler("cudagraph.gemm", graphWorker);
        TaskGraph cudaGraphTaskGraph = new TaskGraph("cudagraph") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a16, b16) //
                .task("gemm", LowPrecisionGemmBenchmark::gemmFp16MMA, context, a16, b16, cGraph, N, K) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cGraph);

        long[] graphTimes;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(cudaGraphTaskGraph.snapshot())) {
            plan.withCUDAGraph();
            graphTimes = timePlan(plan, cudaGraphGrid);
        }
        report("fp16 MMA + CUDA Graph", graphTimes[1], graphTimes[0], maxError(cGraph, refA, refB, 2));

        // ---- 6. int8 MMA (s8 in, s32 accumulate) ------------------------------------
        // Small integer operands so the s32 accumulator cannot overflow: worst case is
        // K * 7 * 7 = 1024 * 49, far inside int range. Integer GEMM is EXACT, so any nonzero
        // error here is a real bug, not a rounding artifact.
        Random intRng = new Random(37);
        byte[] refA8 = new byte[M * K];
        byte[] refB8 = new byte[K * N];
        Int8Array a8 = new Int8Array(M * K);
        Int8Array b8 = new Int8Array(K * N);
        IntArray c8 = new IntArray(M * N);
        for (int i = 0; i < M * K; i++) {
            refA8[i] = (byte) (intRng.nextInt(15) - 7);
            a8.set(i, refA8[i]);
        }
        for (int i = 0; i < K * N; i++) {
            refB8[i] = (byte) (intRng.nextInt(15) - 7);
            b8.set(i, refB8[i]);
        }

        WorkerGrid1D int8Worker = new WorkerGrid1D(mmaGlobalSize);
        int8Worker.setLocalWork(WARP_SIZE, 1, 1);
        GridScheduler int8Grid = new GridScheduler("int8.gemm", int8Worker);
        TaskGraph int8Graph = new TaskGraph("int8") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a8, b8) //
                .task("gemm", LowPrecisionGemmBenchmark::gemmInt8MMA, context, a8, b8, c8, N, K) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c8);

        long[] int8Times;
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(int8Graph.snapshot())) {
            int8Times = timePlan(plan, int8Grid);
        }
        report("int8 MMA (tensor core)", int8Times[1], int8Times[0], maxErrorInt(c8, refA8, refB8));

        // ---- 7. FP8 E4M3 MMA (needs compute capability >= 8.9) ----------------------
        // Two failure modes depending on the bailout flag -- see FP8GemmStage#run. With
        // -Dtornado.recover.bailout=False an exception propagates; with the default (what a
        // plain `tornado` run uses) it silently falls back to sequential host Java, which
        // FP8GemmStage detects and reports by returning false.
        try {
            if (!FP8GemmStage.run(context, refA, refB, M, N, K, ITERATIONS)) {
                reportSkipped("FP8 E4M3 MMA", "needs compute capability >= 8.9; bailed out to sequential");
            }
        } catch (Throwable t) {
            reportSkipped("FP8 E4M3 MMA", t.getClass().getSimpleName());
        }

        System.out.println();
        System.out.println("NOTE: these are END-TO-END times -- each execution copies the 4MB result back to");
        System.out.println("      the host, which is identical work for every stage and compresses the ratios.");
        System.out.println("      For kernel-only time, re-run with:  tornado --enableProfiler console ...");
        System.out.println("      (on an RTX 3070 the kernels are 1.76x fp16 / 1.73x bf16 / 2.99x int8 / 4.09x");
        System.out.println("       cp.async vs fp32 -- not the compressed numbers below)");
        System.out.println();
        System.out.println("Speedups vs fp32 tiled baseline (end-to-end, transfer-dominated):");
        System.out.printf("  fp16 MMA              %.2fx%n", (double) fp32Times[1] / fp16Times[1]);
        System.out.printf("  bf16 MMA              %.2fx%n", (double) fp32Times[1] / bf16Times[1]);
        System.out.printf("  fp16 MMA + cp.async   %.2fx%n", (double) fp32Times[1] / asyncTimes[1]);
        System.out.printf("  fp16 MMA + CUDA Graph %.2fx%n", (double) fp32Times[1] / graphTimes[1]);
        System.out.printf("  int8 MMA              %.2fx%n", (double) fp32Times[1] / int8Times[1]);
        System.out.println();
        System.out.printf("CUDA Graph vs plain fp16 MMA (launch-overhead effect): %.2fx%n", (double) fp16Times[1] / graphTimes[1]);
    }

}
