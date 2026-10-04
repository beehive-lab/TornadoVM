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
package uk.ac.manchester.tornado.mlx.jit;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;

/**
 * JIT counterparts of the MLX indexing operations ({@link uk.ac.manchester.tornado.mlx.MlxIndex}),
 * float32. Reads are one thread per output element. Operations that modify a copy of {@code x} run
 * {@link #copy} first and then a scatter kernel with one thread per update; additions use atomics,
 * while set, max, min and prod assume distinct target positions (no float atomics for them).
 */
public final class JitIndex {

    public static final int SET = 0;
    public static final int ADD = 1;
    public static final int MAX = 2;
    public static final int MIN = 3;
    public static final int PROD = 4;

    private static final int GEMM_BLOCK = 32;
    private static final int GEMM_THREADS = 128;
    private static final int TILE = 8;
    private static final int SIMD_GROUP = 32;

    private JitIndex() {
    }

    private static float apply(float old, float update, int op) {
        float r = update;
        if (op == MAX) {
            r = TornadoMath.max(old, update);
        } else if (op == MIN) {
            r = TornadoMath.min(old, update);
        } else if (op == PROD) {
            r = old * update;
        }
        return r;
    }

    /** out[i] = x[i]: the first step of every operation that updates a copy. */
    @JitBaseline({ "mlx_slice_update", "mlx_slice_update_add", "mlx_slice_update_prod" })
    public static void copy(KernelContext ctx, FloatArray x, FloatArray out, int n) {
        int i = ctx.globalIdx;
        if (i < n) {
            out.set(i, x.get(i));
        }
    }

    /** out[r, c] = x[r0 + r * rowStep, c0 + c * colStep]; outRows * outCols threads. */
    @JitBaseline("mlx_slice")
    public static void slice(KernelContext ctx, FloatArray x, FloatArray out, int cols, int r0, int rowStep, int c0, int colStep, int outRows, int outCols) {
        int t = ctx.globalIdx;
        if (t < outRows * outCols) {
            int r = t / outCols;
            int c = t % outCols;
            out.set(t, x.get((r0 + r * rowStep) * cols + c0 + c * colStep));
        }
    }

    /** out[r0 + r, c0 + c] op= update[r, c]; updateRows * updateCols threads; after {@link #copy}. */
    @JitBaseline({ "mlx_slice_update", "mlx_slice_update_add", "mlx_slice_update_prod" })
    public static void sliceUpdate(KernelContext ctx, FloatArray update, FloatArray out, int cols, int r0, int c0, int updateRows, int updateCols, int op) {
        int t = ctx.globalIdx;
        if (t < updateRows * updateCols) {
            int p = (r0 + t / updateCols) * cols + c0 + t % updateCols;
            float u = update.get(t);
            out.set(p, op == ADD ? out.get(p) + u : apply(out.get(p), u, op));
        }
    }

    /**
     * out[i] = a[lhs[i]] @ b[rhs[i]] for a[*, m, k], b[*, k, n], out[count, m, n]: the simdgroup
     * GEMM of {@link JitBlas#gemm} with per-batch offsets; one threadgroup of 128 threads per
     * 32 x 32 output tile. {@code m} and {@code n} must be multiples of 32 and {@code k} of 8.
     */
    @JitBaseline(value = "mlx_gather_mm", source = "tornado-examples/.../compute/MatrixMultiplySimdgroup.java#gemmTiled (gathered batches)")
    public static void gatherMm(KernelContext ctx, FloatArray a, FloatArray b, IntArray lhs, IntArray rhs, FloatArray c, int m, int n, int k) {
        float[] as = ctx.allocateFloatLocalArray(256);
        float[] bs = ctx.allocateFloatLocalArray(256);
        int tilesPerRow = n / GEMM_BLOCK;
        int tilesPerMatrix = (m / GEMM_BLOCK) * tilesPerRow;
        int batch = ctx.groupIdx / tilesPerMatrix;
        int tile = ctx.groupIdx % tilesPerMatrix;
        int aOff = lhs.get(batch) * m * k;
        int bOff = rhs.get(batch) * k * n;
        int cOff = batch * m * n;
        int rowBase = (tile / tilesPerRow) * GEMM_BLOCK;
        int colBase = (tile % tilesPerRow) * GEMM_BLOCK;
        int tid = ctx.localIdx;
        int sgRow = (tid / SIMD_GROUP) / 2;
        int sgCol = (tid / SIMD_GROUP) % 2;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int kb = 0; kb < k; kb += TILE) {
            for (int e = tid; e < 256; e += GEMM_THREADS) {
                as[e] = a.get(aOff + (rowBase + e / TILE) * k + (kb + e % TILE));
            }
            for (int e = tid; e < 256; e += GEMM_THREADS) {
                bs[e] = b.get(bOff + (kb + e / GEMM_BLOCK) * n + (colBase + e % GEMM_BLOCK));
            }
            ctx.localBarrier();
            Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(as, (sgRow * 2) * 64, TILE);
            Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(as, (sgRow * 2 + 1) * 64, TILE);
            Matrix8x8Float b0 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2) * TILE, GEMM_BLOCK);
            Matrix8x8Float b1 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2 + 1) * TILE, GEMM_BLOCK);
            acc00 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b0, acc00);
            acc01 = ctx.simdgroupMatrixMultiplyAccumulate(a0, b1, acc01);
            acc10 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b0, acc10);
            acc11 = ctx.simdgroupMatrixMultiplyAccumulate(a1, b1, acc11);
            ctx.localBarrier();
        }
        int cr0 = rowBase + (sgRow * 2) * TILE;
        int cr1 = rowBase + (sgRow * 2 + 1) * TILE;
        int cc0 = colBase + (sgCol * 2) * TILE;
        int cc1 = colBase + (sgCol * 2 + 1) * TILE;
        ctx.simdgroupMatrixStore(acc00, c, cOff + cr0 * n + cc0, n);
        ctx.simdgroupMatrixStore(acc01, c, cOff + cr0 * n + cc1, n);
        ctx.simdgroupMatrixStore(acc10, c, cOff + cr1 * n + cc0, n);
        ctx.simdgroupMatrixStore(acc11, c, cOff + cr1 * n + cc1, n);
    }
}
