/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.tile;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.enums.MMAShape;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * jTile - a minimal, cuTile/Tilus-inspired <b>tile</b> programming surface for TornadoVM.
 *
 * <p>
 * Milestone M1 models a tile at its finest granularity: each work-item owns one lane
 * (the element at {@link KernelContext#globalIdx}), and the operations below -
 * {@link #load}, {@link #store}, {@link #add}, {@link #mul}, {@link #scale} - form the
 * elementwise tile vocabulary. They are plain scalar helpers that inline into the per-thread
 * kernel graph, so they JIT-compile on every TornadoVM backend with no new compiler support.
 * </p>
 *
 * <p>
 * Multi-element shared/register tiles (a work-group cooperatively owning a {@code tileSize}-wide
 * strip, and 2-D tensor-core tiles) require the tile value to survive across operations, which
 * TornadoVM cannot yet trace through method boundaries for local memory. That is the subject of
 * milestone M2 (tensor-core GEMM, reusing the existing MMA intrinsics) and the Phase-2 compiler
 * work (first-class {@code Tile} IR nodes). See the jTile design plan.
 * </p>
 *
 * <p>
 * Example (SAXPY, {@code c = alpha*a + b}), launched with global work {@code N}:
 * </p>
 *
 * <pre>
 * public static void saxpy(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, float alpha) {
 *     float ta = Tile.load(ctx, a);
 *     float tb = Tile.load(ctx, b);
 *     Tile.store(ctx, c, Tile.add(Tile.scale(ta, alpha), tb));
 * }
 * </pre>
 */
public final class Tile {

    private static final int WMMA_M = 16;
    private static final int WMMA_N = 16;
    private static final int WMMA_K = 16;
    private static final int WARP_SIZE = 32;

    private Tile() {
    }

    /**
     * Tile-level tensor-core GEMM: {@code C[M x N] = A[M x K] * B[K x N]}, the jTile analog of
     * cuTile's {@code dot} (milestone M2, CUDA backend only).
     *
     * <p>
     * One work-group (one warp of {@value #WARP_SIZE} lanes) computes one {@value #WMMA_M}x
     * {@value #WMMA_N} output tile via NVIDIA {@code mma.sync} tensor-core instructions, looping
     * over K in {@value #WMMA_K}-wide steps. {@code A}/{@code B} are row-major fp16
     * ({@link HalfFloatArray}); {@code C} is a row-major fp32 accumulator. All MMA fragments stay
     * within this method (they cannot cross inlined-method boundaries in the sketcher), so the
     * whole tensor-core dot is exposed as a single tile-level call.
     * </p>
     *
     * <p>
     * Launch: {@code localWork = 32}, {@code globalWork = (M/16)*(N/16)*32}. {@code M}, {@code N}
     * must be multiples of 16 and {@code K} a multiple of 16. Requires the CUDA backend (tensor
     * cores); unsupported on OpenCL/Metal.
     * </p>
     */
    public static void matmul(KernelContext context, HalfFloatArray a, HalfFloatArray b, FloatArray c, int dimM, int dimN, int dimK) {
        int warpId = context.groupIdx;
        int lane = context.localIdx;

        int numTilesN = dimN / WMMA_N;
        int tileRow = (warpId / numTilesN) * WMMA_M;
        int tileCol = (warpId % numTilesN) * WMMA_N;

        // Packed shared tiles: 2 fp16 per int.
        int[] aTile = context.allocateIntLocalArray(WMMA_M * WMMA_K / 2);
        int[] bTile0 = context.allocateIntLocalArray(WMMA_K * WMMA_N / 2);
        int[] bTile1 = context.allocateIntLocalArray(WMMA_K * WMMA_N / 2);

        float[] fragC0 = context.mmaFragment(0.0f);
        float[] fragC1 = context.mmaFragment(0.0f);

        for (int kBase = 0; kBase < dimK; kBase += WMMA_K) {
            // Cooperative load of A: pack 2 adjacent fp16 into one int.
            for (int idx = lane; idx < (WMMA_M * WMMA_K) / 2; idx += WARP_SIZE) {
                int elemBase = idx * 2;
                int r = elemBase / WMMA_K;
                int kk = elemBase % WMMA_K;
                int globalBase = (tileRow + r) * dimK + kBase + kk;
                int lo = a.get(globalBase).getHalfFloatValue() & 0xFFFF;
                int hi = a.get(globalBase + 1).getHalfFloatValue() & 0xFFFF;
                aTile[r * (WMMA_K / 2) + kk / 2] = lo | (hi << 16);
            }

            // Cooperative load of B: two 16x8 panels, transposed per 8-column panel.
            for (int idx = lane; idx < 64; idx += WARP_SIZE) {
                int kRow = idx / 4;
                int jPair = idx % 4;
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
            context.localBarrier();

            HalfFloat[] fragA = context.mmaLoadA(aTile, WMMA_K);
            HalfFloat[] fragB0 = context.mmaLoadB(bTile0, WMMA_K);
            fragC0 = context.mma(fragA, fragB0, fragC0, MMAShape.M16N8K16);
            HalfFloat[] fragB1 = context.mmaLoadB(bTile1, WMMA_K);
            fragC1 = context.mma(fragA, fragB1, fragC1, MMAShape.M16N8K16);

            context.localBarrier();
        }

        context.mmaStore(fragC0, c, tileRow, tileCol, dimN);
        context.mmaStore(fragC1, c, tileRow, tileCol + 8, dimN);
    }

    /**
     * Loads this work-item's lane of {@code src} (element {@link KernelContext#globalIdx}).
     */
    public static float load(KernelContext context, FloatArray src) {
        return src.get(context.globalIdx);
    }

    /**
     * Stores {@code value} into this work-item's lane of {@code dst} (element
     * {@link KernelContext#globalIdx}).
     */
    public static void store(KernelContext context, FloatArray dst, float value) {
        dst.set(context.globalIdx, value);
    }

    /**
     * Element-wise tile sum, {@code x + y}.
     */
    public static float add(float x, float y) {
        return x + y;
    }

    /**
     * Element-wise tile product, {@code x * y}.
     */
    public static float mul(float x, float y) {
        return x * y;
    }

    /**
     * Scales a tile lane by a scalar, {@code x * s}.
     */
    public static float scale(float x, float s) {
        return x * s;
    }
}
