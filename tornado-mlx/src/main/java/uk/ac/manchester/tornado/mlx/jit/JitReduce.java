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

/**
 * JIT counterparts of the MLX reductions ({@link uk.ac.manchester.tornado.mlx.MlxReduce}), float32.
 * A reduction views its input as {@code [outer, len, inner]} and reduces the middle axis, which
 * covers whole-array ({@code outer = inner = 1}), single-axis and adjacent-axes reductions:
 * <ul>
 * <li><b>rows</b> ({@code inner == 1}): one threadgroup of {@link #THREADS} threads per output,
 * a strided loop and a tree reduction in threadgroup memory;</li>
 * <li><b>columns</b> ({@code inner > 1}): one thread per output, looping over {@code len} with
 * stride {@code inner}, so neighbouring threads read neighbouring addresses;</li>
 * <li><b>whole array</b>: a partial pass ({@link #PARTIAL_GROUPS} threadgroups, each reducing a
 * grid-strided share) and a one-threadgroup merge.</li>
 * </ul>
 * The sum family takes an operation code ({@link #SUM} ... {@link #ANY}); logsumexp and
 * variance merge (max, sum of exponentials) and (count, mean, M2) pairs.
 */
public final class JitReduce {

    /** Threads per threadgroup (a power of two). */
    public static final int THREADS = 256;
    /** Threadgroups of the partial pass of a whole-array reduction. */
    public static final int PARTIAL_GROUPS = 256;

    public static final int SUM = 0;
    public static final int PROD = 1;
    public static final int MAX = 2;
    public static final int MIN = 3;
    /** Logical AND of {@code x != 0}; results are 0 or 1. */
    public static final int ALL = 4;
    /** Logical OR of {@code x != 0}; results are 0 or 1. */
    public static final int ANY = 5;

    private JitReduce() {
    }

    private static float identity(int op) {
        float id = 0.0f;
        if (op == PROD || op == ALL) {
            id = 1.0f;
        } else if (op == MAX) {
            id = Float.NEGATIVE_INFINITY;
        } else if (op == MIN) {
            id = Float.POSITIVE_INFINITY;
        }
        return id;
    }

    /** The value an element contributes: itself, or its truth value for ALL and ANY. */
    private static float lift(float v, int op) {
        float r = v;
        if (op >= ALL) {
            r = v != 0.0f ? 1.0f : 0.0f;
        }
        return r;
    }

    private static float combine(float a, float b, int op) {
        float r;
        if (op == SUM) {
            r = a + b;
        } else if (op == PROD) {
            r = a * b;
        } else if (op == MAX || op == ANY) {
            r = TornadoMath.max(a, b);
        } else {
            r = TornadoMath.min(a, b);
        }
        return r;
    }

    // ---------------------------------------------------------------- sum, prod, max, min, mean, all, any

    /**
     * out[row] = scale * reduce(x[row, :]), one threadgroup per row; {@code scale} is 1/len for a
     * mean and 1 otherwise.
     */
    @JitBaseline({ "mlx_sum", "mlx_sum_axis", "mlx_sum_axes", "mlx_prod", "mlx_prod_axis", "mlx_prod_axes", "mlx_max", "mlx_max_axis", "mlx_max_axes", "mlx_min", "mlx_min_axis",
            "mlx_min_axes", "mlx_mean", "mlx_mean_axis", "mlx_mean_axes" })
    public static void reduceRows(KernelContext ctx, FloatArray x, FloatArray out, int len, int op, float scale) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int row = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = row * len;
        float acc = identity(op);
        for (int i = tid; i < len; i += THREADS) {
            acc = combine(acc, lift(x.get(base + i), op), op);
        }
        red[tid] = acc;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] = combine(red[tid], red[tid + s], op);
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            out.set(row, red[0] * scale);
        }
    }

    /** out[o, j] = scale * reduce(x[o, :, j]), one thread per output. */
    @JitBaseline({ "mlx_sum_axis", "mlx_sum_axes", "mlx_prod_axis", "mlx_prod_axes", "mlx_max_axis", "mlx_max_axes", "mlx_min_axis", "mlx_min_axes", "mlx_mean_axis", "mlx_mean_axes" })
    public static void reduceColumns(KernelContext ctx, FloatArray x, FloatArray out, int outputs, int len, int inner, int op, float scale) {
        int idx = ctx.globalIdx;
        if (idx < outputs) {
            int o = idx / inner;
            int j = idx % inner;
            int base = o * len * inner + j;
            float acc = identity(op);
            for (int i = 0; i < len; i++) {
                acc = combine(acc, lift(x.get(base + i * inner), op), op);
            }
            out.set(idx, acc * scale);
        }
    }

    /**
     * First pass of a whole-array reduction: threadgroup g reduces elements g * THREADS + t, then
     * every (groups * THREADS)-th, into partials[g]. Merge with {@link #reduceRows} (len = groups).
     */
    @JitBaseline({ "mlx_sum", "mlx_prod", "mlx_max", "mlx_min", "mlx_mean", "mlx_all", "mlx_any" })
    public static void reducePartial(KernelContext ctx, FloatArray x, FloatArray partials, int n, int op) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int tid = ctx.localIdx;
        int stride = ctx.globalGroupSizeX;
        float acc = identity(op);
        for (int i = ctx.globalIdx; i < n; i += stride) {
            acc = combine(acc, lift(x.get(i), op), op);
        }
        red[tid] = acc;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] = combine(red[tid], red[tid + s], op);
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(ctx.groupIdx, red[0]);
        }
    }

    /**
     * out[o, j] = all/any(x[o, :, j] != 0) as a byte, one threadgroup per output (strided reads
     * when {@code inner > 1}). For a whole array, run {@link #reducePartial} with ALL or ANY first
     * and apply this to the partials.
     */
    @JitBaseline({ "mlx_all", "mlx_all_axis", "mlx_all_axes", "mlx_any", "mlx_any_axis", "mlx_any_axes" })
    public static void allAny(KernelContext ctx, FloatArray x, ByteArray out, int len, int inner, int op) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int idx = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = (idx / inner) * len * inner + idx % inner;
        float acc = identity(op);
        for (int i = tid; i < len; i += THREADS) {
            acc = combine(acc, lift(x.get(base + i * inner), op), op);
        }
        red[tid] = acc;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] = combine(red[tid], red[tid + s], op);
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            out.set(idx, (byte) (red[0] != 0.0f ? 1 : 0));
        }
    }

    // ---------------------------------------------------------------- logsumexp

    /**
     * out[o, j] = log(sum(exp(x[o, :, j]))), one threadgroup per output: the max, then the sum of
     * exponentials shifted by it.
     */
    @JitBaseline({ "mlx_logsumexp", "mlx_logsumexp_axis", "mlx_logsumexp_axes" })
    public static void logsumexp(KernelContext ctx, FloatArray x, FloatArray out, int len, int inner) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int idx = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = (idx / inner) * len * inner + idx % inner;
        float m = Float.NEGATIVE_INFINITY;
        for (int i = tid; i < len; i += THREADS) {
            m = TornadoMath.max(m, x.get(base + i * inner));
        }
        red[tid] = m;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] = TornadoMath.max(red[tid], red[tid + s]);
            }
            ctx.localBarrier();
        }
        float max = red[0];
        ctx.localBarrier();
        float sum = 0.0f;
        for (int i = tid; i < len; i += THREADS) {
            sum += TornadoMath.exp(x.get(base + i * inner) - max);
        }
        red[tid] = sum;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            out.set(idx, max + TornadoMath.log(red[0]));
        }
    }

    /**
     * First pass of a whole-array logsumexp: each threadgroup writes its (max, sum of exp(x - max))
     * to partials[2g], partials[2g + 1]. Merge with {@link #logsumexpMerge}.
     */
    @JitBaseline("mlx_logsumexp")
    public static void logsumexpPartial(KernelContext ctx, FloatArray x, FloatArray partials, int n) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int tid = ctx.localIdx;
        int stride = ctx.globalGroupSizeX;
        float m = Float.NEGATIVE_INFINITY;
        for (int i = ctx.globalIdx; i < n; i += stride) {
            m = TornadoMath.max(m, x.get(i));
        }
        red[tid] = m;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] = TornadoMath.max(red[tid], red[tid + s]);
            }
            ctx.localBarrier();
        }
        float max = red[0];
        ctx.localBarrier();
        float sum = 0.0f;
        for (int i = ctx.globalIdx; i < n; i += stride) {
            sum += TornadoMath.exp(x.get(i) - max);
        }
        red[tid] = sum;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(2 * ctx.groupIdx, max);
            partials.set(2 * ctx.groupIdx + 1, red[0]);
        }
    }

    /** Merges {@code groups} (max, sum) pairs into out[0]; one threadgroup. */
    @JitBaseline("mlx_logsumexp")
    public static void logsumexpMerge(KernelContext ctx, FloatArray partials, FloatArray out, int groups) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int tid = ctx.localIdx;
        float m = Float.NEGATIVE_INFINITY;
        for (int g = tid; g < groups; g += THREADS) {
            m = TornadoMath.max(m, partials.get(2 * g));
        }
        red[tid] = m;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] = TornadoMath.max(red[tid], red[tid + s]);
            }
            ctx.localBarrier();
        }
        float max = red[0];
        ctx.localBarrier();
        float sum = 0.0f;
        for (int g = tid; g < groups; g += THREADS) {
            float pm = partials.get(2 * g);
            if (pm > Float.NEGATIVE_INFINITY) {
                sum += partials.get(2 * g + 1) * TornadoMath.exp(pm - max);
            }
        }
        red[tid] = sum;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            out.set(0, max + TornadoMath.log(red[0]));
        }
    }

    // ---------------------------------------------------------------- variance and standard deviation

    /**
     * out[o, j] = var(x[o, :, j]) with {@code ddof} (or its square root if {@code sqrt != 0}), one
     * threadgroup per output, two passes (mean, then squared deviations).
     */
    @JitBaseline({ "mlx_var", "mlx_var_axis", "mlx_var_axes", "mlx_std", "mlx_std_axis", "mlx_std_axes" })
    public static void variance(KernelContext ctx, FloatArray x, FloatArray out, int len, int inner, int ddof, int sqrt) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int idx = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = (idx / inner) * len * inner + idx % inner;
        float sum = 0.0f;
        for (int i = tid; i < len; i += THREADS) {
            sum += x.get(base + i * inner);
        }
        red[tid] = sum;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        float mean = red[0] / len;
        ctx.localBarrier();
        float sq = 0.0f;
        for (int i = tid; i < len; i += THREADS) {
            float d = x.get(base + i * inner) - mean;
            sq += d * d;
        }
        red[tid] = sq;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            float v = red[0] / (len - ddof);
            out.set(idx, sqrt != 0 ? TornadoMath.sqrt(v) : v);
        }
    }

    /**
     * First pass of a whole-array variance: each threadgroup writes the (count, mean, M2) of its
     * share to partials[3g .. 3g + 2]. Merge with {@link #varianceMerge}.
     */
    @JitBaseline({ "mlx_var", "mlx_std" })
    public static void variancePartial(KernelContext ctx, FloatArray x, FloatArray partials, int n) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        float[] cnt = ctx.allocateFloatLocalArray(THREADS);
        int tid = ctx.localIdx;
        int stride = ctx.globalGroupSizeX;
        float sum = 0.0f;
        float count = 0.0f;
        for (int i = ctx.globalIdx; i < n; i += stride) {
            sum += x.get(i);
            count += 1.0f;
        }
        red[tid] = sum;
        cnt[tid] = count;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
                cnt[tid] += cnt[tid + s];
            }
            ctx.localBarrier();
        }
        float total = cnt[0];
        float mean = total > 0.0f ? red[0] / total : 0.0f;
        ctx.localBarrier();
        float sq = 0.0f;
        for (int i = ctx.globalIdx; i < n; i += stride) {
            float d = x.get(i) - mean;
            sq += d * d;
        }
        red[tid] = sq;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partials.set(3 * ctx.groupIdx, total);
            partials.set(3 * ctx.groupIdx + 1, mean);
            partials.set(3 * ctx.groupIdx + 2, red[0]);
        }
    }

    /**
     * Merges {@code groups} (count, mean, M2) triples into the variance (or standard deviation) of
     * the whole array: the grand mean first, then M2 + count * (mean - grand mean)^2 summed (Chan et
     * al.); one threadgroup.
     */
    @JitBaseline({ "mlx_var", "mlx_std" })
    public static void varianceMerge(KernelContext ctx, FloatArray partials, FloatArray out, int groups, int n, int ddof, int sqrt) {
        float[] red = ctx.allocateFloatLocalArray(THREADS);
        int tid = ctx.localIdx;
        float sum = 0.0f;
        for (int g = tid; g < groups; g += THREADS) {
            sum += partials.get(3 * g) * partials.get(3 * g + 1);
        }
        red[tid] = sum;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        float mean = red[0] / n;
        ctx.localBarrier();
        float m2 = 0.0f;
        for (int g = tid; g < groups; g += THREADS) {
            float d = partials.get(3 * g + 1) - mean;
            m2 += partials.get(3 * g + 2) + partials.get(3 * g) * d * d;
        }
        red[tid] = m2;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                red[tid] += red[tid + s];
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            float v = red[0] / (n - ddof);
            out.set(0, sqrt != 0 ? TornadoMath.sqrt(v) : v);
        }
    }

    // ---------------------------------------------------------------- argmin, median

    /** out[o, j] = index of the smallest x[o, :, j] (the first on ties), one threadgroup per output. */
    @JitBaseline({ "mlx_argmin", "mlx_argmin_axis" })
    public static void argmin(KernelContext ctx, FloatArray x, IntArray out, int len, int inner) {
        float[] vals = ctx.allocateFloatLocalArray(THREADS);
        int[] idxs = ctx.allocateIntLocalArray(THREADS);
        int idx = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = (idx / inner) * len * inner + idx % inner;
        float best = Float.POSITIVE_INFINITY;
        int bestIdx = len;
        for (int i = tid; i < len; i += THREADS) {
            float v = x.get(base + i * inner);
            if (v < best) {
                best = v;
                bestIdx = i;
            }
        }
        vals[tid] = best;
        idxs[tid] = bestIdx;
        ctx.localBarrier();
        for (int s = THREADS / 2; s > 0; s >>= 1) {
            if (tid < s) {
                float other = vals[tid + s];
                int otherIdx = idxs[tid + s];
                if (other < vals[tid] || (other == vals[tid] && otherIdx < idxs[tid])) {
                    vals[tid] = other;
                    idxs[tid] = otherIdx;
                }
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            out.set(idx, idxs[0]);
        }
    }

}
