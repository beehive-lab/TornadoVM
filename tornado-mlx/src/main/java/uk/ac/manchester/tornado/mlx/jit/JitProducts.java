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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;

/**
 * JIT counterparts of the MLX tensor and special products ({@link uk.ac.manchester.tornado.mlx.MlxProducts}),
 * float32. The matrix products use the simdgroup-matrix GEMM of {@link JitBlas#gemm} (128 threads
 * per 32 x 32 output tile; m and n multiples of 32, k of 8). 8-bit floats are E4M3 with saturation
 * at 448, as MLX converts them; their bits are built with {@code Float.floatToRawIntBits} and
 * {@code Float.intBitsToFloat}.
 */
public final class JitProducts {

    private static final int BLOCK = 32;
    private static final int GEMM_THREADS = 128;
    private static final int TILE = 8;
    private static final int SIMD = 32;
    private static final int THREADS = 256;

    private JitProducts() {
    }

    /** c[b] = a[b] @ b[b] for a [batch, m, k], b [batch, k, n]; batch * (m/32) * (n/32) threadgroups of 128. */
    @JitBaseline(value = "mlx_einsum", source = "tornado-examples/.../compute/MatrixMultiplySimdgroup.java#gemmTiled (batched)")
    public static void batchedGemm(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        float[] as = ctx.allocateFloatLocalArray(256);
        float[] bs = ctx.allocateFloatLocalArray(256);
        int tilesPerRow = n / BLOCK;
        int tilesPerMatrix = (m / BLOCK) * tilesPerRow;
        int batch = ctx.groupIdx / tilesPerMatrix;
        int tile = ctx.groupIdx % tilesPerMatrix;
        int aOff = batch * m * k;
        int bOff = batch * k * n;
        int cOff = batch * m * n;
        int rowBase = (tile / tilesPerRow) * BLOCK;
        int colBase = (tile % tilesPerRow) * BLOCK;
        int tid = ctx.localIdx;
        int sgRow = (tid / SIMD) / 2;
        int sgCol = (tid / SIMD) % 2;
        Matrix8x8Float acc00 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc01 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc10 = ctx.simdgroupMatrixZero();
        Matrix8x8Float acc11 = ctx.simdgroupMatrixZero();
        for (int kb = 0; kb < k; kb += TILE) {
            for (int e = tid; e < 256; e += GEMM_THREADS) {
                as[e] = a.get(aOff + (rowBase + e / TILE) * k + (kb + e % TILE));
            }
            for (int e = tid; e < 256; e += GEMM_THREADS) {
                bs[e] = b.get(bOff + (kb + e / BLOCK) * n + (colBase + e % BLOCK));
            }
            ctx.localBarrier();
            Matrix8x8Float a0 = ctx.simdgroupMatrixLoad(as, (sgRow * 2) * 64, TILE);
            Matrix8x8Float a1 = ctx.simdgroupMatrixLoad(as, (sgRow * 2 + 1) * 64, TILE);
            Matrix8x8Float b0 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2) * TILE, BLOCK);
            Matrix8x8Float b1 = ctx.simdgroupMatrixLoad(bs, (sgCol * 2 + 1) * TILE, BLOCK);
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

    /** out[s, i, j] = sum over k in [segments[2s], segments[2s + 1]) of a[i, k] * b[k, j]; one thread per output. */
    @JitBaseline("mlx_segmented_mm")
    public static void segmentedGemm(KernelContext ctx, FloatArray a, FloatArray b, IntArray segments, FloatArray out, int count, int m, int n, int k) {
        int t = ctx.globalIdx;
        if (t < count * m * n) {
            int s = t / (m * n);
            int i = (t / n) % m;
            int j = t % n;
            int k0 = segments.get(2 * s);
            int k1 = segments.get(2 * s + 1);
            float acc = 0.0f;
            for (int p = k0; p < k1; p++) {
                acc += a.get(i * k + p) * b.get(p * n + j);
            }
            out.set(t, acc);
        }
    }

    /** First pass of a dot product: partials[g] = the dot product of threadgroup g's grid-strided share. */
    @JitBaseline("mlx_inner")
    public static void dotPartial(KernelContext ctx, FloatArray a, FloatArray b, FloatArray partials, int n) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int tid = ctx.localIdx;
        float acc = 0.0f;
        for (int i = ctx.globalIdx; i < n; i += ctx.globalGroupSizeX) {
            acc += a.get(i) * b.get(i);
        }
        red[tid] = acc;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(ctx.groupIdx, red[0]);
        }
    }

    @JitBaseline("mlx_outer")
    public static void outer(KernelContext ctx, FloatArray a, FloatArray b, FloatArray out, int m, int n) {
        int t = ctx.globalIdx;
        if (t < m * n) {
            out.set(t, a.get(t / n) * b.get(t % n));
        }
    }

    @JitBaseline("mlx_kron")
    public static void kron(KernelContext ctx, FloatArray a, FloatArray b, FloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        int cols = colsA * colsB;
        int t = ctx.globalIdx;
        if (t < rowsA * rowsB * cols) {
            int r = t / cols;
            int c = t % cols;
            out.set(t, a.get((r / rowsB) * colsA + c / colsB) * b.get((r % rowsB) * colsB + c % colsB));
        }
    }

    /** E4M3 bits of x: round to nearest even, saturating at +/-448; NaN also saturates, as MLX's conversion does. */
    private static int encodeFp8(float x) {
        int bits = Float.floatToRawIntBits(x);
        int sign = ((bits >> 31) & 1) << 7;
        int code;
        if ((bits & 0x7f800000) == 0x7f800000) {
            code = 0x7e;
        } else {
            float ax = TornadoMath.abs(x);
            if (ax >= 448.0f) {
                code = 0x7e;
            } else {
                int e = ((bits >> 23) & 0xff) - 127;
                if (e < -6) {
                    // Subnormal: multiples of 2^-9.
                    float v = ax * 512.0f;
                    float r = TornadoMath.floor(v);
                    float f = v - r;
                    int q = (int) r;
                    if (f > 0.5f || (f == 0.5f && (q & 1) == 1)) {
                        q++;
                    }
                    code = q;
                } else {
                    int mant = bits & 0x7fffff;
                    int q = mant >> 20;
                    int rem = mant & 0xfffff;
                    int e8 = e + 7;
                    if (rem > 0x80000 || (rem == 0x80000 && (q & 1) == 1)) {
                        q++;
                        if (q == 8) {
                            q = 0;
                            e8++;
                        }
                    }
                    code = (e8 << 3) | q;
                    if (code > 0x7e) {
                        code = 0x7e;
                    }
                }
            }
        }
        return sign | code;
    }

    /** The value of E4M3 bits b (0 to 255). */
    private static float decodeFp8(int b) {
        int e = (b >> 3) & 0xf;
        int mant = b & 7;
        float mag;
        if (e == 0) {
            mag = mant * 0.001953125f;
        } else {
            mag = (1.0f + mant * 0.125f) * Float.intBitsToFloat((e - 7 + 127) << 23);
        }
        return (b & 0x80) != 0 ? -mag : mag;
    }

    /**
     * out[m, n] = mxfp8(x) @ w^T, one simdgroup per output: each step of 32 k values is one MX group;
     * the lanes find the group's largest magnitude with shuffles, quantize their x value to E4M3 with
     * the shared power-of-two scale, and multiply it by their decoded weight (w packed four bytes per
     * word, one E8M0 scale per group of 32).
     */
    @JitBaseline("mlx_qqmm")
    public static void qqmm(KernelContext ctx, FloatArray x, IntArray w, ByteArray scales, FloatArray out, int n, int k) {
        int o = ctx.groupIdx;
        int lane = ctx.localIdx;
        int row = o / n;
        int col = o % n;
        int groups = k / SIMD;
        float acc = 0.0f;
        for (int g = 0; g < groups; g++) {
            int kk = g * SIMD + lane;
            float xv = x.get(row * k + kk);
            float amax = TornadoMath.abs(xv);
            for (int delta = 16; delta > 0; delta >>= 1) {
                amax = TornadoMath.max(amax, ctx.simdShuffleDown(amax, delta));
            }
            amax = ctx.simdBroadcastFirst(amax);
            float q = 0.0f;
            if (amax > 0.0f) {
                // Shared scale 2^(floor(log2(amax)) - 8): 8 is the largest E4M3 exponent.
                int e = ((Float.floatToRawIntBits(amax) >> 23) & 0xff) - 127 - 8;
                float scale = Float.intBitsToFloat((e + 127) << 23);
                q = decodeFp8(encodeFp8(xv / scale)) * scale;
            }
            int word = w.get((col * k + kk) / 4);
            int wb = (word >> (8 * (kk % 4))) & 0xff;
            float ws = Float.intBitsToFloat((scales.get(col * groups + g) & 0xff) << 23);
            acc += q * decodeFp8(wb) * ws;
        }
        float sum = ctx.simdSum(acc);
        if (lane == 0) {
            out.set(o, sum);
        }
    }
}
