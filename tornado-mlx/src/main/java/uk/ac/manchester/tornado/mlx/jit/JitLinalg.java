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

/**
 * JIT counterparts of the MLX linear-algebra operations ({@link uk.ac.manchester.tornado.mlx.MlxLinalg}),
 * float32: cross products, one thread per vector, and norms, one threadgroup of {@link #THREADS}
 * threads per output.
 */
public final class JitLinalg {

    /** Threads per threadgroup. */
    public static final int THREADS = 256;

    private JitLinalg() {
    }

    // ---------------------------------------------------------------- vectors and norms

    /** out[i] = a[i] x b[i] for count 3-vectors; one thread per vector. */
    @JitBaseline("mlx_linalg_cross")
    public static void crossProduct(KernelContext ctx, FloatArray a, FloatArray b, FloatArray out, int count) {
        int i = ctx.globalIdx;
        if (i < count) {
            int p = 3 * i;
            float a0 = a.get(p);
            float a1 = a.get(p + 1);
            float a2 = a.get(p + 2);
            float b0 = b.get(p);
            float b1 = b.get(p + 1);
            float b2 = b.get(p + 2);
            out.set(p, a1 * b2 - a2 * b1);
            out.set(p + 1, a2 * b0 - a0 * b2);
            out.set(p + 2, a0 * b1 - a1 * b0);
        }
    }

    /**
     * out[row] = the {@code ord}-norm of x[row, :]: sum |x|^ord ^ (1/ord), with ord = +/-infinity
     * giving max/min |x| and ord = 0 the count of non-zeros. One threadgroup per row; used with
     * len = rows * cols for the Frobenius norm of a matrix.
     */
    @JitBaseline({ "mlx_linalg_norm", "mlx_linalg_norm_l2", "mlx_linalg_norm_matrix" })
    public static void normRows(KernelContext ctx, FloatArray x, FloatArray out, int len, float ord) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int row = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = row * len;
        boolean isMax = ord > 3.0e38f;
        boolean isMin = ord < -3.0e38f;
        float acc = isMin ? Float.MAX_VALUE : 0.0f;
        for (int i = tid; i < len; i += THREADS) {
            float v = TornadoMath.abs(x.get(base + i));
            if (isMax) {
                acc = TornadoMath.max(acc, v);
            } else if (isMin) {
                acc = TornadoMath.min(acc, v);
            } else if (ord == 0.0f) {
                acc += v != 0.0f ? 1.0f : 0.0f;
            } else if (ord == 1.0f) {
                acc += v;
            } else if (ord == 2.0f) {
                acc += v * v;
            } else if (v != 0.0f) {
                acc += TornadoMath.exp(ord * TornadoMath.log(v));
            }
        }
        red[tid] = acc;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                if (isMax) {
                    red[tid] = TornadoMath.max(red[tid], red[tid + s]);
                } else if (isMin) {
                    red[tid] = TornadoMath.min(red[tid], red[tid + s]);
                } else {
                    red[tid] += red[tid + s];
                }
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            float r = red[0];
            if (ord == 2.0f) {
                r = TornadoMath.sqrt(r);
            } else if (!isMax && !isMin && ord != 0.0f && ord != 1.0f && r != 0.0f) {
                r = TornadoMath.exp(TornadoMath.log(r) / ord);
            }
            out.set(row, r);
        }
    }
}
