/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import java.util.ArrayList;
import java.util.LongSummaryStatistics;
import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Benchmarks four GEMM variants:
 *   - tiled fp16 baseline (no MMA)
 *   - MMA with B in [K, N] row-major (standard layout)
 *   - MMA with B in [N, K] row-major (matches GPULlama3 weight layout)
 *   - MMA + swizzled shared memory, B in [K, N]
 *
 * Usage:
 *   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplicationMMA
 *      <no args>         square 2048^3 (default)
 *      <size>            square M=N=K=size
 *      <M> <N> <K>       rectangular shape (e.g. 128 8192 2048 for Llama-3.2-1B FFN gate-up)
 */
public class MatrixMultiplicationMMA {

    private static final int WARM_UP_ITERATIONS = 20;
    private static final int BENCHMARK_ITERATIONS = 30;
    private static final float REL_TOLERANCE = 2e-2f;
    private static final Random RNG = new Random(42);

    private static final int TS = 16;
    private static final int WARP_SIZE = 32;

    private static final int BM = 128, BN = 128, BK = 16;
    private static final int WARPS_M = 4, WARPS_N = 2;
    private static final int WARPS_PER_BLOCK = WARPS_M * WARPS_N;
    private static final int THREADS_PER_BLOCK = WARPS_PER_BLOCK * WARP_SIZE;
    private static final int WM = BM / WARPS_M;
    private static final int WN = BN / WARPS_N;
    private static final int B_SUBTILE_BYTES = 256;

    // KERNEL 1: baseline (B is [K, N] row-major)
    public static void gemmBaselineFp16(KernelContext ctx,
                                        HalfFloatArray A, HalfFloatArray B, FloatArray C,
                                        int M, int N, int K) {
        int row = ctx.localIdx;
        int col = ctx.localIdy;
        int globalRow = TS * ctx.groupIdx + row;
        int globalCol = TS * ctx.groupIdy + col;
        HalfFloat[] aSub = ctx.allocateHalfFloatLocalArray(TS * TS);
        HalfFloat[] bSub = ctx.allocateHalfFloatLocalArray(TS * TS);
        float sum = 0.0f;
        int numTiles = K / TS;
        for (int t = 0; t < numTiles; t++) {
            int tiledRow = TS * t + row;
            int tiledCol = TS * t + col;
            aSub[col * TS + row] = A.get(globalRow * K + tiledCol);
            bSub[col * TS + row] = B.get(tiledRow * N + globalCol);
            ctx.localBarrier();
            for (int k = 0; k < TS; k++) {
                sum += aSub[k * TS + row].getFloat32() * bSub[col * TS + k].getFloat32();
            }
            ctx.localBarrier();
        }
        C.set(globalRow * N + globalCol, sum);
    }

    // KERNEL 2: multi-warp MMA non-swizzled (B is [K, N] row-major)
    public static void gemmMMA(KernelContext ctx,
                               HalfFloatArray A, HalfFloatArray B, FloatArray C,
                               int M, int N, int K) {
        int tid = ctx.localIdx;
        int warpId = tid / WARP_SIZE;
        int warpM = warpId / WARPS_N;
        int warpN = warpId % WARPS_N;
        int blockRow = BM * ctx.groupIdx;
        int blockCol = BN * ctx.groupIdy;

        int[] aTile = ctx.allocateIntLocalArray(BM * BK / 2);
        int[] bTile = ctx.allocateIntLocalArray(BK * BN / 2);

        float[] c00 = ctx.mmaFragment(0.0f); float[] c01 = ctx.mmaFragment(0.0f);
        float[] c02 = ctx.mmaFragment(0.0f); float[] c03 = ctx.mmaFragment(0.0f);
        float[] c04 = ctx.mmaFragment(0.0f); float[] c05 = ctx.mmaFragment(0.0f);
        float[] c06 = ctx.mmaFragment(0.0f); float[] c07 = ctx.mmaFragment(0.0f);
        float[] c10 = ctx.mmaFragment(0.0f); float[] c11 = ctx.mmaFragment(0.0f);
        float[] c12 = ctx.mmaFragment(0.0f); float[] c13 = ctx.mmaFragment(0.0f);
        float[] c14 = ctx.mmaFragment(0.0f); float[] c15 = ctx.mmaFragment(0.0f);
        float[] c16 = ctx.mmaFragment(0.0f); float[] c17 = ctx.mmaFragment(0.0f);

        int numKSteps = K / BK;
        for (int kStep = 0; kStep < numKSteps; kStep++) {
            int kBase = kStep * BK;
            for (int idx = tid; idx < BM * BK / 2; idx += THREADS_PER_BLOCK) {
                int m_row = idx / (BK / 2);
                int k_pair = idx % (BK / 2);
                int k_base = k_pair * 2;
                int gA = (blockRow + m_row) * K + (kBase + k_base);
                int lo = A.get(gA).getHalfFloatValue() & 0xFFFF;
                int hi = A.get(gA + 1).getHalfFloatValue() & 0xFFFF;
                aTile[m_row * (BK / 2) + k_pair] = lo | (hi << 16);
            }
            for (int idx = tid; idx < BK * BN / 2; idx += THREADS_PER_BLOCK) {
                int subTileId = idx / 64;
                int intInSub = idx % 64;
                int k_row = intInSub / 4;
                int j_pair = intInSub % 4;
                int j_base = j_pair * 2;
                int col_in_block = subTileId * 8 + j_base;
                int gB = (kBase + k_row) * N + (blockCol + col_in_block);
                int lo = B.get(gB).getHalfFloatValue() & 0xFFFF;
                int hi = B.get(gB + 1).getHalfFloatValue() & 0xFFFF;
                bTile[idx] = lo | (hi << 16);
            }
            ctx.localBarrier();

            int aOff0 = warpM * 1024;
            int aOff1 = warpM * 1024 + 512;
            HalfFloat[] a0 = ctx.mmaLoadA(aTile, BK, aOff0);
            HalfFloat[] a1 = ctx.mmaLoadA(aTile, BK, aOff1);
            int bBase = warpN * 8;
            HalfFloat[] b0 = ctx.mmaLoadB(bTile, BK, (bBase + 0) * B_SUBTILE_BYTES);
            HalfFloat[] b1 = ctx.mmaLoadB(bTile, BK, (bBase + 1) * B_SUBTILE_BYTES);
            HalfFloat[] b2 = ctx.mmaLoadB(bTile, BK, (bBase + 2) * B_SUBTILE_BYTES);
            HalfFloat[] b3 = ctx.mmaLoadB(bTile, BK, (bBase + 3) * B_SUBTILE_BYTES);
            HalfFloat[] b4 = ctx.mmaLoadB(bTile, BK, (bBase + 4) * B_SUBTILE_BYTES);
            HalfFloat[] b5 = ctx.mmaLoadB(bTile, BK, (bBase + 5) * B_SUBTILE_BYTES);
            HalfFloat[] b6 = ctx.mmaLoadB(bTile, BK, (bBase + 6) * B_SUBTILE_BYTES);
            HalfFloat[] b7 = ctx.mmaLoadB(bTile, BK, (bBase + 7) * B_SUBTILE_BYTES);

            c00 = ctx.mma(a0, b0, c00, MMAShape.M16N8K16);
            c01 = ctx.mma(a0, b1, c01, MMAShape.M16N8K16);
            c02 = ctx.mma(a0, b2, c02, MMAShape.M16N8K16);
            c03 = ctx.mma(a0, b3, c03, MMAShape.M16N8K16);
            c04 = ctx.mma(a0, b4, c04, MMAShape.M16N8K16);
            c05 = ctx.mma(a0, b5, c05, MMAShape.M16N8K16);
            c06 = ctx.mma(a0, b6, c06, MMAShape.M16N8K16);
            c07 = ctx.mma(a0, b7, c07, MMAShape.M16N8K16);
            c10 = ctx.mma(a1, b0, c10, MMAShape.M16N8K16);
            c11 = ctx.mma(a1, b1, c11, MMAShape.M16N8K16);
            c12 = ctx.mma(a1, b2, c12, MMAShape.M16N8K16);
            c13 = ctx.mma(a1, b3, c13, MMAShape.M16N8K16);
            c14 = ctx.mma(a1, b4, c14, MMAShape.M16N8K16);
            c15 = ctx.mma(a1, b5, c15, MMAShape.M16N8K16);
            c16 = ctx.mma(a1, b6, c16, MMAShape.M16N8K16);
            c17 = ctx.mma(a1, b7, c17, MMAShape.M16N8K16);
            ctx.localBarrier();
        }

        int rBase = blockRow + warpM * WM;
        int cBase = blockCol + warpN * WN;
        ctx.mmaStore(c00, C, rBase + 0,  cBase + 0,  N);
        ctx.mmaStore(c01, C, rBase + 0,  cBase + 8,  N);
        ctx.mmaStore(c02, C, rBase + 0,  cBase + 16, N);
        ctx.mmaStore(c03, C, rBase + 0,  cBase + 24, N);
        ctx.mmaStore(c04, C, rBase + 0,  cBase + 32, N);
        ctx.mmaStore(c05, C, rBase + 0,  cBase + 40, N);
        ctx.mmaStore(c06, C, rBase + 0,  cBase + 48, N);
        ctx.mmaStore(c07, C, rBase + 0,  cBase + 56, N);
        ctx.mmaStore(c10, C, rBase + 16, cBase + 0,  N);
        ctx.mmaStore(c11, C, rBase + 16, cBase + 8,  N);
        ctx.mmaStore(c12, C, rBase + 16, cBase + 16, N);
        ctx.mmaStore(c13, C, rBase + 16, cBase + 24, N);
        ctx.mmaStore(c14, C, rBase + 16, cBase + 32, N);
        ctx.mmaStore(c15, C, rBase + 16, cBase + 40, N);
        ctx.mmaStore(c16, C, rBase + 16, cBase + 48, N);
        ctx.mmaStore(c17, C, rBase + 16, cBase + 56, N);
    }

    // KERNEL 2a: multi-warp MMA non-swizzled with cp.async tile loads (B is [K, N] row-major).
    // Identical to gemmMMA except that every packed b32 tile slot is filled by an
    // asynchronous cp.async copy (global -> shared, no register round-trip), closed by
    // one commit/wait pair before the barrier. CUDA backend, sm_80+.
    public static void gemmMMACpAsync(KernelContext ctx,
                                      HalfFloatArray A, HalfFloatArray B, FloatArray C,
                                      int M, int N, int K) {
        int tid = ctx.localIdx;
        int warpId = tid / WARP_SIZE;
        int warpM = warpId / WARPS_N;
        int warpN = warpId % WARPS_N;
        int blockRow = BM * ctx.groupIdx;
        int blockCol = BN * ctx.groupIdy;

        int[] aTile = ctx.allocateIntLocalArray(BM * BK / 2);
        int[] bTile = ctx.allocateIntLocalArray(BK * BN / 2);

        float[] c00 = ctx.mmaFragment(0.0f); float[] c01 = ctx.mmaFragment(0.0f);
        float[] c02 = ctx.mmaFragment(0.0f); float[] c03 = ctx.mmaFragment(0.0f);
        float[] c04 = ctx.mmaFragment(0.0f); float[] c05 = ctx.mmaFragment(0.0f);
        float[] c06 = ctx.mmaFragment(0.0f); float[] c07 = ctx.mmaFragment(0.0f);
        float[] c10 = ctx.mmaFragment(0.0f); float[] c11 = ctx.mmaFragment(0.0f);
        float[] c12 = ctx.mmaFragment(0.0f); float[] c13 = ctx.mmaFragment(0.0f);
        float[] c14 = ctx.mmaFragment(0.0f); float[] c15 = ctx.mmaFragment(0.0f);
        float[] c16 = ctx.mmaFragment(0.0f); float[] c17 = ctx.mmaFragment(0.0f);

        int numKSteps = K / BK;
        for (int kStep = 0; kStep < numKSteps; kStep++) {
            int kBase = kStep * BK;
            for (int idx = tid; idx < BM * BK / 2; idx += THREADS_PER_BLOCK) {
                int m_row = idx / (BK / 2);
                int k_pair = idx % (BK / 2);
                int k_base = k_pair * 2;
                int gA = (blockRow + m_row) * K + (kBase + k_base);
                ctx.asyncCopyToLocal(aTile, m_row * (BK / 2) + k_pair, A, gA);
            }
            for (int idx = tid; idx < BK * BN / 2; idx += THREADS_PER_BLOCK) {
                int subTileId = idx / 64;
                int intInSub = idx % 64;
                int k_row = intInSub / 4;
                int j_pair = intInSub % 4;
                int j_base = j_pair * 2;
                int col_in_block = subTileId * 8 + j_base;
                int gB = (kBase + k_row) * N + (blockCol + col_in_block);
                ctx.asyncCopyToLocal(bTile, idx, B, gB);
            }
            ctx.asyncCopyCommit();
            ctx.asyncCopyWaitGroup(0);
            ctx.localBarrier();

            int aOff0 = warpM * 1024;
            int aOff1 = warpM * 1024 + 512;
            HalfFloat[] a0 = ctx.mmaLoadA(aTile, BK, aOff0);
            HalfFloat[] a1 = ctx.mmaLoadA(aTile, BK, aOff1);
            int bBase = warpN * 8;
            HalfFloat[] b0 = ctx.mmaLoadB(bTile, BK, (bBase + 0) * B_SUBTILE_BYTES);
            HalfFloat[] b1 = ctx.mmaLoadB(bTile, BK, (bBase + 1) * B_SUBTILE_BYTES);
            HalfFloat[] b2 = ctx.mmaLoadB(bTile, BK, (bBase + 2) * B_SUBTILE_BYTES);
            HalfFloat[] b3 = ctx.mmaLoadB(bTile, BK, (bBase + 3) * B_SUBTILE_BYTES);
            HalfFloat[] b4 = ctx.mmaLoadB(bTile, BK, (bBase + 4) * B_SUBTILE_BYTES);
            HalfFloat[] b5 = ctx.mmaLoadB(bTile, BK, (bBase + 5) * B_SUBTILE_BYTES);
            HalfFloat[] b6 = ctx.mmaLoadB(bTile, BK, (bBase + 6) * B_SUBTILE_BYTES);
            HalfFloat[] b7 = ctx.mmaLoadB(bTile, BK, (bBase + 7) * B_SUBTILE_BYTES);

            c00 = ctx.mma(a0, b0, c00, MMAShape.M16N8K16);
            c01 = ctx.mma(a0, b1, c01, MMAShape.M16N8K16);
            c02 = ctx.mma(a0, b2, c02, MMAShape.M16N8K16);
            c03 = ctx.mma(a0, b3, c03, MMAShape.M16N8K16);
            c04 = ctx.mma(a0, b4, c04, MMAShape.M16N8K16);
            c05 = ctx.mma(a0, b5, c05, MMAShape.M16N8K16);
            c06 = ctx.mma(a0, b6, c06, MMAShape.M16N8K16);
            c07 = ctx.mma(a0, b7, c07, MMAShape.M16N8K16);
            c10 = ctx.mma(a1, b0, c10, MMAShape.M16N8K16);
            c11 = ctx.mma(a1, b1, c11, MMAShape.M16N8K16);
            c12 = ctx.mma(a1, b2, c12, MMAShape.M16N8K16);
            c13 = ctx.mma(a1, b3, c13, MMAShape.M16N8K16);
            c14 = ctx.mma(a1, b4, c14, MMAShape.M16N8K16);
            c15 = ctx.mma(a1, b5, c15, MMAShape.M16N8K16);
            c16 = ctx.mma(a1, b6, c16, MMAShape.M16N8K16);
            c17 = ctx.mma(a1, b7, c17, MMAShape.M16N8K16);
            ctx.localBarrier();
        }

        int rBase = blockRow + warpM * WM;
        int cBase = blockCol + warpN * WN;
        ctx.mmaStore(c00, C, rBase + 0,  cBase + 0,  N);
        ctx.mmaStore(c01, C, rBase + 0,  cBase + 8,  N);
        ctx.mmaStore(c02, C, rBase + 0,  cBase + 16, N);
        ctx.mmaStore(c03, C, rBase + 0,  cBase + 24, N);
        ctx.mmaStore(c04, C, rBase + 0,  cBase + 32, N);
        ctx.mmaStore(c05, C, rBase + 0,  cBase + 40, N);
        ctx.mmaStore(c06, C, rBase + 0,  cBase + 48, N);
        ctx.mmaStore(c07, C, rBase + 0,  cBase + 56, N);
        ctx.mmaStore(c10, C, rBase + 16, cBase + 0,  N);
        ctx.mmaStore(c11, C, rBase + 16, cBase + 8,  N);
        ctx.mmaStore(c12, C, rBase + 16, cBase + 16, N);
        ctx.mmaStore(c13, C, rBase + 16, cBase + 24, N);
        ctx.mmaStore(c14, C, rBase + 16, cBase + 32, N);
        ctx.mmaStore(c15, C, rBase + 16, cBase + 40, N);
        ctx.mmaStore(c16, C, rBase + 16, cBase + 48, N);
        ctx.mmaStore(c17, C, rBase + 16, cBase + 56, N);
    }


    // KERNEL 2b: multi-warp MMA non-swizzled, B in [N, K] row-major
    // (matches GPULlama3 W1/W3 layout: weights stored as [output_dim, input_dim])
    public static void gemmMMA_NK(KernelContext ctx,
                                  HalfFloatArray A, HalfFloatArray B, FloatArray C,
                                  int M, int N, int K) {
        int tid = ctx.localIdx;
        int warpId = tid / WARP_SIZE;
        int warpM = warpId / WARPS_N;
        int warpN = warpId % WARPS_N;
        int blockRow = BM * ctx.groupIdx;
        int blockCol = BN * ctx.groupIdy;

        int[] aTile = ctx.allocateIntLocalArray(BM * BK / 2);
        int[] bTile = ctx.allocateIntLocalArray(BK * BN / 2);

        float[] c00 = ctx.mmaFragment(0.0f); float[] c01 = ctx.mmaFragment(0.0f);
        float[] c02 = ctx.mmaFragment(0.0f); float[] c03 = ctx.mmaFragment(0.0f);
        float[] c04 = ctx.mmaFragment(0.0f); float[] c05 = ctx.mmaFragment(0.0f);
        float[] c06 = ctx.mmaFragment(0.0f); float[] c07 = ctx.mmaFragment(0.0f);
        float[] c10 = ctx.mmaFragment(0.0f); float[] c11 = ctx.mmaFragment(0.0f);
        float[] c12 = ctx.mmaFragment(0.0f); float[] c13 = ctx.mmaFragment(0.0f);
        float[] c14 = ctx.mmaFragment(0.0f); float[] c15 = ctx.mmaFragment(0.0f);
        float[] c16 = ctx.mmaFragment(0.0f); float[] c17 = ctx.mmaFragment(0.0f);

        int numKSteps = K / BK;
        for (int kStep = 0; kStep < numKSteps; kStep++) {
            int kBase = kStep * BK;
            // A load: A is [M, K] row-major
            for (int idx = tid; idx < BM * BK / 2; idx += THREADS_PER_BLOCK) {
                int m_row = idx / (BK / 2);
                int k_pair = idx % (BK / 2);
                int k_base = k_pair * 2;
                int gA = (blockRow + m_row) * K + (kBase + k_base);
                int lo = A.get(gA).getHalfFloatValue() & 0xFFFF;
                int hi = A.get(gA + 1).getHalfFloatValue() & 0xFFFF;
                aTile[m_row * (BK / 2) + k_pair] = lo | (hi << 16);
            }
            // B load: B is [N, K] row-major.
            for (int idx = tid; idx < BK * BN / 2; idx += THREADS_PER_BLOCK) {
                int subTileId = idx / 64;
                int intInSub = idx % 64;
                int k_row = intInSub / 4;
                int j_pair = intInSub % 4;
                int j_base = j_pair * 2;
                int col_in_block = subTileId * 8 + j_base;
                // B[col, k] is at col * K + k in [N, K] row-major.
                int gB0 = (blockCol + col_in_block)     * K + (kBase + k_row);
                int gB1 = (blockCol + col_in_block + 1) * K + (kBase + k_row);
                int lo = B.get(gB0).getHalfFloatValue() & 0xFFFF;
                int hi = B.get(gB1).getHalfFloatValue() & 0xFFFF;
                bTile[idx] = lo | (hi << 16);
            }
            ctx.localBarrier();

            int aOff0 = warpM * 1024;
            int aOff1 = warpM * 1024 + 512;
            HalfFloat[] a0 = ctx.mmaLoadA(aTile, BK, aOff0);
            HalfFloat[] a1 = ctx.mmaLoadA(aTile, BK, aOff1);
            int bBase = warpN * 8;
            HalfFloat[] b0 = ctx.mmaLoadB(bTile, BK, (bBase + 0) * B_SUBTILE_BYTES);
            HalfFloat[] b1 = ctx.mmaLoadB(bTile, BK, (bBase + 1) * B_SUBTILE_BYTES);
            HalfFloat[] b2 = ctx.mmaLoadB(bTile, BK, (bBase + 2) * B_SUBTILE_BYTES);
            HalfFloat[] b3 = ctx.mmaLoadB(bTile, BK, (bBase + 3) * B_SUBTILE_BYTES);
            HalfFloat[] b4 = ctx.mmaLoadB(bTile, BK, (bBase + 4) * B_SUBTILE_BYTES);
            HalfFloat[] b5 = ctx.mmaLoadB(bTile, BK, (bBase + 5) * B_SUBTILE_BYTES);
            HalfFloat[] b6 = ctx.mmaLoadB(bTile, BK, (bBase + 6) * B_SUBTILE_BYTES);
            HalfFloat[] b7 = ctx.mmaLoadB(bTile, BK, (bBase + 7) * B_SUBTILE_BYTES);

            c00 = ctx.mma(a0, b0, c00, MMAShape.M16N8K16);
            c01 = ctx.mma(a0, b1, c01, MMAShape.M16N8K16);
            c02 = ctx.mma(a0, b2, c02, MMAShape.M16N8K16);
            c03 = ctx.mma(a0, b3, c03, MMAShape.M16N8K16);
            c04 = ctx.mma(a0, b4, c04, MMAShape.M16N8K16);
            c05 = ctx.mma(a0, b5, c05, MMAShape.M16N8K16);
            c06 = ctx.mma(a0, b6, c06, MMAShape.M16N8K16);
            c07 = ctx.mma(a0, b7, c07, MMAShape.M16N8K16);
            c10 = ctx.mma(a1, b0, c10, MMAShape.M16N8K16);
            c11 = ctx.mma(a1, b1, c11, MMAShape.M16N8K16);
            c12 = ctx.mma(a1, b2, c12, MMAShape.M16N8K16);
            c13 = ctx.mma(a1, b3, c13, MMAShape.M16N8K16);
            c14 = ctx.mma(a1, b4, c14, MMAShape.M16N8K16);
            c15 = ctx.mma(a1, b5, c15, MMAShape.M16N8K16);
            c16 = ctx.mma(a1, b6, c16, MMAShape.M16N8K16);
            c17 = ctx.mma(a1, b7, c17, MMAShape.M16N8K16);
            ctx.localBarrier();
        }

        int rBase = blockRow + warpM * WM;
        int cBase = blockCol + warpN * WN;
        ctx.mmaStore(c00, C, rBase + 0,  cBase + 0,  N);
        ctx.mmaStore(c01, C, rBase + 0,  cBase + 8,  N);
        ctx.mmaStore(c02, C, rBase + 0,  cBase + 16, N);
        ctx.mmaStore(c03, C, rBase + 0,  cBase + 24, N);
        ctx.mmaStore(c04, C, rBase + 0,  cBase + 32, N);
        ctx.mmaStore(c05, C, rBase + 0,  cBase + 40, N);
        ctx.mmaStore(c06, C, rBase + 0,  cBase + 48, N);
        ctx.mmaStore(c07, C, rBase + 0,  cBase + 56, N);
        ctx.mmaStore(c10, C, rBase + 16, cBase + 0,  N);
        ctx.mmaStore(c11, C, rBase + 16, cBase + 8,  N);
        ctx.mmaStore(c12, C, rBase + 16, cBase + 16, N);
        ctx.mmaStore(c13, C, rBase + 16, cBase + 24, N);
        ctx.mmaStore(c14, C, rBase + 16, cBase + 32, N);
        ctx.mmaStore(c15, C, rBase + 16, cBase + 40, N);
        ctx.mmaStore(c16, C, rBase + 16, cBase + 48, N);
        ctx.mmaStore(c17, C, rBase + 16, cBase + 56, N);
    }

    // KERNEL 3: multi-warp MMA swizzled (B is [K, N] row-major)
    public static void gemmMMASwizzled(KernelContext ctx,
                                       HalfFloatArray A, HalfFloatArray B, FloatArray C,
                                       int M, int N, int K) {
        int tid = ctx.localIdx;
        int warpId = tid / WARP_SIZE;
        int warpM = warpId / WARPS_N;
        int warpN = warpId % WARPS_N;
        int blockRow = BM * ctx.groupIdx;
        int blockCol = BN * ctx.groupIdy;

        int[] aTile = ctx.allocateIntLocalArray(BM * BK / 2);
        HalfFloat[] bTile = ctx.allocateHalfFloatLocalArray(BK * BN);

        float[] c00 = ctx.mmaFragment(0.0f); float[] c01 = ctx.mmaFragment(0.0f);
        float[] c02 = ctx.mmaFragment(0.0f); float[] c03 = ctx.mmaFragment(0.0f);
        float[] c04 = ctx.mmaFragment(0.0f); float[] c05 = ctx.mmaFragment(0.0f);
        float[] c06 = ctx.mmaFragment(0.0f); float[] c07 = ctx.mmaFragment(0.0f);
        float[] c10 = ctx.mmaFragment(0.0f); float[] c11 = ctx.mmaFragment(0.0f);
        float[] c12 = ctx.mmaFragment(0.0f); float[] c13 = ctx.mmaFragment(0.0f);
        float[] c14 = ctx.mmaFragment(0.0f); float[] c15 = ctx.mmaFragment(0.0f);
        float[] c16 = ctx.mmaFragment(0.0f); float[] c17 = ctx.mmaFragment(0.0f);

        int numKSteps = K / BK;
        for (int kStep = 0; kStep < numKSteps; kStep++) {
            int kBase = kStep * BK;
            for (int idx = tid; idx < BM * BK / 2; idx += THREADS_PER_BLOCK) {
                int m_row = idx / (BK / 2);
                int k_pair = idx % (BK / 2);
                int k_base = k_pair * 2;
                int gA = (blockRow + m_row) * K + (kBase + k_base);
                int lo = A.get(gA).getHalfFloatValue() & 0xFFFF;
                int hi = A.get(gA + 1).getHalfFloatValue() & 0xFFFF;
                aTile[m_row * (BK / 2) + k_pair] = lo | (hi << 16);
            }
            for (int idx = tid; idx < BK * BN; idx += THREADS_PER_BLOCK) {
                int subTileId = idx / 128;
                int inSub     = idx % 128;
                int k_row     = inSub / 8;
                int j         = inSub % 8;
                int col_in_block = subTileId * 8 + j;
                HalfFloat val = B.get((kBase + k_row) * N + (blockCol + col_in_block));
                ctx.mmaStoreBSwizzled(bTile, k_row, j, 8, val, subTileId * B_SUBTILE_BYTES);
            }
            ctx.localBarrier();

            int aOff0 = warpM * 1024;
            int aOff1 = warpM * 1024 + 512;
            HalfFloat[] a0 = ctx.mmaLoadA(aTile, BK, aOff0);
            HalfFloat[] a1 = ctx.mmaLoadA(aTile, BK, aOff1);
            int bBase = warpN * 8;
            HalfFloat[] b0 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 0) * B_SUBTILE_BYTES);
            HalfFloat[] b1 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 1) * B_SUBTILE_BYTES);
            HalfFloat[] b2 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 2) * B_SUBTILE_BYTES);
            HalfFloat[] b3 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 3) * B_SUBTILE_BYTES);
            HalfFloat[] b4 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 4) * B_SUBTILE_BYTES);
            HalfFloat[] b5 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 5) * B_SUBTILE_BYTES);
            HalfFloat[] b6 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 6) * B_SUBTILE_BYTES);
            HalfFloat[] b7 = ctx.mmaLoadBSwizzled(bTile, BK, (bBase + 7) * B_SUBTILE_BYTES);

            c00 = ctx.mma(a0, b0, c00, MMAShape.M16N8K16);
            c01 = ctx.mma(a0, b1, c01, MMAShape.M16N8K16);
            c02 = ctx.mma(a0, b2, c02, MMAShape.M16N8K16);
            c03 = ctx.mma(a0, b3, c03, MMAShape.M16N8K16);
            c04 = ctx.mma(a0, b4, c04, MMAShape.M16N8K16);
            c05 = ctx.mma(a0, b5, c05, MMAShape.M16N8K16);
            c06 = ctx.mma(a0, b6, c06, MMAShape.M16N8K16);
            c07 = ctx.mma(a0, b7, c07, MMAShape.M16N8K16);
            c10 = ctx.mma(a1, b0, c10, MMAShape.M16N8K16);
            c11 = ctx.mma(a1, b1, c11, MMAShape.M16N8K16);
            c12 = ctx.mma(a1, b2, c12, MMAShape.M16N8K16);
            c13 = ctx.mma(a1, b3, c13, MMAShape.M16N8K16);
            c14 = ctx.mma(a1, b4, c14, MMAShape.M16N8K16);
            c15 = ctx.mma(a1, b5, c15, MMAShape.M16N8K16);
            c16 = ctx.mma(a1, b6, c16, MMAShape.M16N8K16);
            c17 = ctx.mma(a1, b7, c17, MMAShape.M16N8K16);
            ctx.localBarrier();
        }

        int rBase = blockRow + warpM * WM;
        int cBase = blockCol + warpN * WN;
        ctx.mmaStore(c00, C, rBase + 0,  cBase + 0,  N);
        ctx.mmaStore(c01, C, rBase + 0,  cBase + 8,  N);
        ctx.mmaStore(c02, C, rBase + 0,  cBase + 16, N);
        ctx.mmaStore(c03, C, rBase + 0,  cBase + 24, N);
        ctx.mmaStore(c04, C, rBase + 0,  cBase + 32, N);
        ctx.mmaStore(c05, C, rBase + 0,  cBase + 40, N);
        ctx.mmaStore(c06, C, rBase + 0,  cBase + 48, N);
        ctx.mmaStore(c07, C, rBase + 0,  cBase + 56, N);
        ctx.mmaStore(c10, C, rBase + 16, cBase + 0,  N);
        ctx.mmaStore(c11, C, rBase + 16, cBase + 8,  N);
        ctx.mmaStore(c12, C, rBase + 16, cBase + 16, N);
        ctx.mmaStore(c13, C, rBase + 16, cBase + 24, N);
        ctx.mmaStore(c14, C, rBase + 16, cBase + 32, N);
        ctx.mmaStore(c15, C, rBase + 16, cBase + 40, N);
        ctx.mmaStore(c16, C, rBase + 16, cBase + 48, N);
        ctx.mmaStore(c17, C, rBase + 16, cBase + 56, N);
    }

    /** Reference: C = A * B where A is [M, K] row-major and B is [K, N] row-major. */
    private static void gemmReference(HalfFloatArray A, HalfFloatArray B, float[] ref,
                                      int M, int N, int K) {
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < K; k++) {
                    sum += A.get(i * K + k).getFloat32() * B.get(k * N + j).getFloat32();
                }
                ref[i * N + j] = sum;
            }
        }
    }

    private static HalfFloatArray randomFp16(int size, float lo, float hi) {
        HalfFloatArray arr = new HalfFloatArray(size);
        float range = hi - lo;
        for (int i = 0; i < size; i++) {
            arr.set(i, new HalfFloat(lo + RNG.nextFloat() * range));
        }
        return arr;
    }

    /** Builds B_NK ([N, K] row-major) from B_KN ([K, N] row-major) so both kernels see the same logical matrix. */
    private static HalfFloatArray transposeKNtoNK(HalfFloatArray B_KN, int N, int K) {
        HalfFloatArray B_NK = new HalfFloatArray(N * K);
        for (int k = 0; k < K; k++) {
            for (int n = 0; n < N; n++) {
                B_NK.set(n * K + k, B_KN.get(k * N + n));
            }
        }
        return B_NK;
    }

    private static boolean validate(String name, FloatArray got, float[] ref, int M, int N) {
        float maxRel = 0.0f;
        int bad = 0;
        for (int i = 0; i < M * N; i++) {
            float r = ref[i];
            float g = got.get(i);
            float denom = Math.max(Math.abs(r), 1.0f);
            float rel = Math.abs(r - g) / denom;
            maxRel = Math.max(maxRel, rel);
            if (rel > REL_TOLERANCE) bad++;
        }
        boolean ok = bad == 0;
        System.out.printf("  [%s] validation %s (max rel err %.4f, %d/%d cells out of tol)%n",
                name, ok ? "PASSED" : "FAILED", maxRel, bad, M * N);
        return ok;
    }

    private static double gflops(long avgNanos, int M, int N, int K) {
        double flops = 2.0 * M * N * K;
        return (flops * 1e-9) / (avgNanos * 1e-9);
    }

    private static void report(String name, ArrayList<Long> timers, int M, int N, int K) {
        LongSummaryStatistics s = timers.stream().mapToLong(Long::longValue).summaryStatistics();
        System.out.printf("%s:%n", name);
        System.out.printf("  Avg: %.3f ms  Min: %.3f ms  Max: %.3f ms  Perf: %.1f GFLOP/s%n",
                s.getAverage() / 1e6, s.getMin() / 1e6, s.getMax() / 1e6,
                gflops((long) s.getAverage(), M, N, K));
    }

    /** Parses CLI args into [M, N, K]. Accepts <size>, <M N K>, or nothing. */
    private static int[] parseDims(String[] args) {
        if (args.length >= 3) {
            try {
                return new int[] {
                        Integer.parseInt(args[0]),
                        Integer.parseInt(args[1]),
                        Integer.parseInt(args[2])
                };
            } catch (NumberFormatException e) {
                System.err.println("Bad args, using defaults M=N=K=2048");
            }
        } else if (args.length >= 1) {
            try {
                int s = Integer.parseInt(args[0]);
                return new int[] { s, s, s };
            } catch (NumberFormatException e) {
                System.err.println("Bad arg, using defaults M=N=K=2048");
            }
        }
        return new int[] { 2048, 2048, 2048 };
    }

    private static boolean isPTXorCUDABackend() {
        int driverIndex = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex();
        TornadoVMBackendType backend = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(driverIndex);
        return backend == TornadoVMBackendType.PTX || backend == TornadoVMBackendType.CUDA;
    }

    public static void main(String[] args) {
        if (!isPTXorCUDABackend()) {
            System.out.println("MMA instructions are only supported for the PTX and CUDA backends.");
            return;
        }

        int[] dims = parseDims(args);
        final int M = dims[0], N = dims[1], K = dims[2];

        System.out.println("Multi-warp MMA GEMM Benchmark");
        System.out.println("=============================");
        System.out.printf("Shape: M=%d N=%d K=%d   Warmup: %d   Iters: %d%n",
                M, N, K, WARM_UP_ITERATIONS, BENCHMARK_ITERATIONS);

        HalfFloatArray A    = randomFp16(M * K, -1.0f, 1.0f);
        HalfFloatArray B_KN = randomFp16(K * N, -1.0f, 1.0f);
        HalfFloatArray B_NK = transposeKNtoNK(B_KN, N, K);  // same logical matrix, [N, K] layout

        FloatArray cBaseline = new FloatArray(M * N);
        FloatArray cMMA      = new FloatArray(M * N);
        FloatArray cCpAsync  = new FloatArray(M * N);
        FloatArray cMMA_NK   = new FloatArray(M * N);
        FloatArray cSwizzled = new FloatArray(M * N);

        float[] ref = new float[M * N];
        System.out.println("Computing CPU reference...");
        gemmReference(A, B_KN, ref, M, N, K);

        WorkerGrid2D wgBaseline = new WorkerGrid2D(M, N);
        wgBaseline.setLocalWork(TS, TS, 1);
        GridScheduler gsBaseline = new GridScheduler("s_base.gemm", wgBaseline);
        TaskGraph tgBaseline = new TaskGraph("s_base")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B_KN)
                .task("gemm", MatrixMultiplicationMMA::gemmBaselineFp16,
                        new KernelContext(), A, B_KN, cBaseline, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cBaseline);
        ImmutableTaskGraph itgBaseline = tgBaseline.snapshot();

        WorkerGrid2D wgMMA = new WorkerGrid2D((M / BM) * THREADS_PER_BLOCK, (N / BN));
        wgMMA.setLocalWork(THREADS_PER_BLOCK, 1, 1);
        GridScheduler gsMMA = new GridScheduler("s_mma.gemm", wgMMA);
        TaskGraph tgMMA = new TaskGraph("s_mma")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B_KN)
                .task("gemm", MatrixMultiplicationMMA::gemmMMA,
                        new KernelContext(), A, B_KN, cMMA, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cMMA);
        ImmutableTaskGraph itgMMA = tgMMA.snapshot();

        WorkerGrid2D wgCpAsync = new WorkerGrid2D((M / BM) * THREADS_PER_BLOCK, (N / BN));
        wgCpAsync.setLocalWork(THREADS_PER_BLOCK, 1, 1);
        GridScheduler gsCpAsync = new GridScheduler("s_mma_cpasync.gemm", wgCpAsync);
        TaskGraph tgCpAsync = new TaskGraph("s_mma_cpasync")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B_KN)
                .task("gemm", MatrixMultiplicationMMA::gemmMMACpAsync,
                        new KernelContext(), A, B_KN, cCpAsync, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cCpAsync);
        ImmutableTaskGraph itgCpAsync = tgCpAsync.snapshot();

        WorkerGrid2D wgMMA_NK = new WorkerGrid2D((M / BM) * THREADS_PER_BLOCK, (N / BN));
        wgMMA_NK.setLocalWork(THREADS_PER_BLOCK, 1, 1);
        GridScheduler gsMMA_NK = new GridScheduler("s_mma_nk.gemm", wgMMA_NK);
        TaskGraph tgMMA_NK = new TaskGraph("s_mma_nk")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B_NK)
                .task("gemm", MatrixMultiplicationMMA::gemmMMA_NK,
                        new KernelContext(), A, B_NK, cMMA_NK, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cMMA_NK);
        ImmutableTaskGraph itgMMA_NK = tgMMA_NK.snapshot();

        WorkerGrid2D wgSwz = new WorkerGrid2D((M / BM) * THREADS_PER_BLOCK, (N / BN));
        wgSwz.setLocalWork(THREADS_PER_BLOCK, 1, 1);
        GridScheduler gsSwz = new GridScheduler("s_swz.gemm", wgSwz);
        TaskGraph tgSwz = new TaskGraph("s_swz")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B_KN)
                .task("gemm", MatrixMultiplicationMMA::gemmMMASwizzled,
                        new KernelContext(), A, B_KN, cSwizzled, M, N, K)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cSwizzled);
        ImmutableTaskGraph itgSwz = tgSwz.snapshot();

        TornadoExecutionPlan planBaseline = new TornadoExecutionPlan(itgBaseline);
        TornadoExecutionPlan planMMA      = new TornadoExecutionPlan(itgMMA);
        TornadoExecutionPlan planCpAsync  = new TornadoExecutionPlan(itgCpAsync);
        TornadoExecutionPlan planMMA_NK   = new TornadoExecutionPlan(itgMMA_NK);
        TornadoExecutionPlan planSwz      = new TornadoExecutionPlan(itgSwz);

        System.out.println("Warming up...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            planBaseline.withGridScheduler(gsBaseline).execute();
            planMMA.withGridScheduler(gsMMA).execute();
            planCpAsync.withGridScheduler(gsCpAsync).execute();
            planMMA_NK.withGridScheduler(gsMMA_NK).execute();
            planSwz.withGridScheduler(gsSwz).execute();
        }

        System.out.println("Validating...");
        validate("baseline",    cBaseline, ref, M, N);
        validate("mma (B=KN)",  cMMA,      ref, M, N);
        validate("mma+cp.async", cCpAsync,  ref, M, N);
        validate("mma (B=NK)",  cMMA_NK,   ref, M, N);
        validate("mma+swizzle", cSwizzled, ref, M, N);

        ArrayList<Long> tB  = new ArrayList<>();
        ArrayList<Long> tM  = new ArrayList<>();
        ArrayList<Long> tCA = new ArrayList<>();
        ArrayList<Long> tNK = new ArrayList<>();
        ArrayList<Long> tS  = new ArrayList<>();
        System.out.println("Benchmarking...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long s0 = System.nanoTime();
            planBaseline.withGridScheduler(gsBaseline).execute();
            tB.add(System.nanoTime() - s0);
            long s1 = System.nanoTime();
            planMMA.withGridScheduler(gsMMA).execute();
            tM.add(System.nanoTime() - s1);
            long sCA = System.nanoTime();
            planCpAsync.withGridScheduler(gsCpAsync).execute();
            tCA.add(System.nanoTime() - sCA);
            long s2 = System.nanoTime();
            planMMA_NK.withGridScheduler(gsMMA_NK).execute();
            tNK.add(System.nanoTime() - s2);
            long s3 = System.nanoTime();
            planSwz.withGridScheduler(gsSwz).execute();
            tS.add(System.nanoTime() - s3);
        }

        System.out.println("\nPerformance Results:");
        System.out.println("====================");
        report("Baseline fp16 (tiled, no MMA)", tB,  M, N, K);
        report("MMA fp16 (B=KN row-major)",    tM,  M, N, K);
        report("MMA fp16 (cp.async, B=KN)",    tCA, M, N, K);
        report("MMA fp16 (B=NK row-major)",    tNK, M, N, K);
        report("MMA fp16 (swizzled, B=KN)",    tS,  M, N, K);

        double avgB  = tB.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgM  = tM.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgCA = tCA.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgNK = tNK.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgS  = tS.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println("\nSpeedups:");
        System.out.printf("  MMA(KN) vs baseline:          %.2fx%n", avgB / avgM);
        System.out.printf("  MMA(NK) vs baseline:          %.2fx%n", avgB / avgNK);
        System.out.printf("  MMA+cp.async vs MMA(KN):      %.2fx  (async tile loads)%n", avgM / avgCA);
        System.out.printf("  MMA(NK) vs MMA(KN):           %.2fx  (cost of NK layout)%n", avgM / avgNK);
        System.out.printf("  MMA+swizzle vs baseline:      %.2fx%n", avgB / avgS);
    }

}
