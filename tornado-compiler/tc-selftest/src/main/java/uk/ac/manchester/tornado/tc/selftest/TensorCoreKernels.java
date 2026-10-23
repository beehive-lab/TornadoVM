/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.selftest;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Warp-level tensor-core GEMMs (mma.sync), one warp per 16x16 output tile: F16 inputs with m16n8k16,
 * and FP8 e4m3 inputs with m16n8k32. The same kernels as the MMA unit tests
 * (TestMatrixMultiplicationMMA, TestMatrixMultiplicationMMAFP8).
 */
public final class TensorCoreKernels {

    static final int WMMA_M = 16;
    static final int WMMA_N = 16;
    static final int WMMA_K = 16;
    static final int FP8_K = 32;
    static final int WARP_SIZE = 32;

    private TensorCoreKernels() {
    }

    public static void gemmF16(KernelContext ctx,
                                  HalfFloatArray a, HalfFloatArray b, FloatArray c,
                                  int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        // Packed shared tiles: 2 f16 per int = b16 pairs
        // A: 16 rows x 8 packed ints = 128 (was 256 floats)
        // B: 8 cols x 8 packed ints = 64 per panel (was 128 floats)
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
                int kRow  = idx / 4;        // 0..15
                int jPair = idx % 4;        // 0..3 (which pair of j: 0,1 / 2,3 / 4,5 / 6,7)
                int jBase = jPair * 2;

                int gL0 = (kBase + kRow) * dimN + tileCol + jBase;
                int gL1 = (kBase + kRow) * dimN + tileCol + jBase + 1;
                int loLeft = b.get(gL0).getHalfFloatValue() & 0xFFFF;
                int hiLeft = b.get(gL1).getHalfFloatValue() & 0xFFFF;
                bTile0[kRow * 4 + jPair] = loLeft | (hiLeft << 16);

                int gR0 = (kBase + kRow) * dimN + tileCol + 8 + jBase;
                int gR1 = (kBase + kRow) * dimN + tileCol + 8 + jBase + 1;
                int loRight = b.get(gR0).getHalfFloatValue() & 0xFFFF;
                int hiRight = b.get(gR1).getHalfFloatValue() & 0xFFFF;
                bTile1[kRow * 4 + jPair] = loRight | (hiRight << 16);
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

    public static void gemmFp8E4M3(KernelContext ctx,
                                      FP8Array a, FP8Array b, FloatArray c,
                                      int dimM, int dimN, int dimK) {

        int warpId = ctx.groupIdx;
        int lane   = ctx.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow   = (warpId / numTilesN) * WMMA_M;
        int tileCol   = (warpId % numTilesN) * WMMA_N;

        // Byte-packed shared tiles, identical layout to the int8 m16n8k32 path:
        // A: 16x32 fp8 bytes = 128 ints; B: 8 cols x 32 k = 64 ints per panel.
        int[] aTile  = ctx.allocateIntLocalArray(WMMA_M * FP8_K / 2);
        int[] bTile0 = ctx.allocateIntLocalArray(FP8_K * 8 / 4);
        int[] bTile1 = ctx.allocateIntLocalArray(FP8_K * 8 / 4);

        float[] fragC0 = ctx.mmaFragment(0.0f);
        float[] fragC1 = ctx.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += FP8_K) {

            // Cooperative load A: pack 4 adjacent fp8 bytes into one int
            for (int idx = lane; idx < (WMMA_M * FP8_K) / 4; idx += WARP_SIZE) {
                int elemBase = idx * 4;
                int r  = elemBase / FP8_K;
                int kk = elemBase % FP8_K;
                int base = (tileRow + r) * dimK + kBase + kk;
                int packed = (a.get(base)     & 0xFF)
                        | ((a.get(base + 1) & 0xFF) << 8)
                        | ((a.get(base + 2) & 0xFF) << 16)
                        | ((a.get(base + 3) & 0xFF) << 24);
                aTile[r * (FP8_K / 4) + kk / 4] = packed;
            }

            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow  = idx / 4;        // 0..15
                int jPair = idx % 4;        // 0..3
                int jBase = jPair * 2;     // 0, 2, 4, 6
                int kBaseInner = 2 * kRow;      // first of the pair of K-values

                // Left panel (cols tileCol..tileCol+7)
                int bL0 = b.get((kBase + kBaseInner)     * dimN + tileCol + jBase)     & 0xFF;
                int bL1 = b.get((kBase + kBaseInner + 1) * dimN + tileCol + jBase)     & 0xFF;
                int bL2 = b.get((kBase + kBaseInner)     * dimN + tileCol + jBase + 1) & 0xFF;
                int bL3 = b.get((kBase + kBaseInner + 1) * dimN + tileCol + jBase + 1) & 0xFF;
                bTile0[kRow * 4 + jPair] = bL0 | (bL1 << 8) | (bL2 << 16) | (bL3 << 24);

                // Right panel (cols tileCol+8..tileCol+15)
                int bR0 = b.get((kBase + kBaseInner)     * dimN + tileCol + 8 + jBase)     & 0xFF;
                int bR1 = b.get((kBase + kBaseInner + 1) * dimN + tileCol + 8 + jBase)     & 0xFF;
                int bR2 = b.get((kBase + kBaseInner)     * dimN + tileCol + 8 + jBase + 1) & 0xFF;
                int bR3 = b.get((kBase + kBaseInner + 1) * dimN + tileCol + 8 + jBase + 1) & 0xFF;
                bTile1[kRow * 4 + jPair] = bR0 | (bR1 << 8) | (bR2 << 16) | (bR3 << 24);
            }

            ctx.localBarrier();

            byte[] fragA = ctx.mmaLoadAFP8(aTile, FP8_K);

            byte[] fragB0 = ctx.mmaLoadBFP8(bTile0, FP8_K);
            fragC0 = ctx.mmaFP8E4M3(fragA, fragB0, fragC0, MMAShape.M16N8K32);

            byte[] fragB1 = ctx.mmaLoadBFP8(bTile1, FP8_K);
            fragC1 = ctx.mmaFP8E4M3(fragA, fragB1, fragC1, MMAShape.M16N8K32);

            ctx.localBarrier();
        }

        ctx.mmaStore(fragC0, c, tileRow, tileCol,     dimN);
        ctx.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }
}
