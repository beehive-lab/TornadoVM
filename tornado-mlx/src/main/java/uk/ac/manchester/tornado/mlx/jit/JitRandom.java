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
 * JIT counterparts of the MLX random samplers ({@link uk.ac.manchester.tornado.mlx.MlxRandom}):
 * counter-based generation with the Threefry-2x32 hash (20 rounds, the generator family MLX uses),
 * one hash per value and stream, turned into each distribution by inverse transforms. The values
 * differ from MLX's (the key schedule and counter layout are not MLX's) but follow the same
 * distributions.
 */
public final class JitRandom {

    private static final float TWO_PI = 6.283185307179586f;
    private static final float SQRT2 = 1.4142135623730951f;

    /** Threads per threadgroup of {@link #categorical}. */
    public static final int THREADS = 256;

    private JitRandom() {
    }

    private static int rotl(int x, int r) {
        // Masked signed shift: the same bits as an unsigned right shift.
        return (x << r) | ((x >> (32 - r)) & ((1 << r) - 1));
    }

    /** First word of Threefry-2x32(key = (seed, stream), counter = (i, 0)). */
    private static int threefry(int seed, int stream, int i) {
        int k0 = seed;
        int k1 = stream;
        int k2 = 0x1BD11BDA ^ k0 ^ k1;
        int x0 = i + k0;
        int x1 = k1;
        for (int block = 0; block < 5; block++) {
            for (int r = 0; r < 4; r++) {
                int rot = (block & 1) == 0 ? (r == 0 ? 13 : r == 1 ? 15 : r == 2 ? 26 : 6) : (r == 0 ? 17 : r == 1 ? 29 : r == 2 ? 16 : 24);
                x0 += x1;
                x1 = rotl(x1, rot);
                x1 ^= x0;
            }
            int s = block + 1;
            int a = s % 3 == 0 ? k0 : s % 3 == 1 ? k1 : k2;
            int b = (s + 1) % 3 == 0 ? k0 : (s + 1) % 3 == 1 ? k1 : k2;
            x0 += a;
            x1 += b + s;
        }
        return x0;
    }

    /** A float uniform in [0, 1) from the top 23 bits of a hash. */
    private static float unit(int bits) {
        return Float.intBitsToFloat(0x3f800000 | ((bits >> 9) & 0x7fffff)) - 1.0f;
    }

    /** A float uniform in (0, 1). */
    private static float openUnit(int bits) {
        return (((bits >> 9) & 0x7fffff) + 0.5f) * 1.1920928955078125e-7f;
    }

    private static float erf(float x) {
        float t = 1.0f / (1.0f + 0.5f * TornadoMath.abs(x));
        float y = t * TornadoMath.exp(-x * x - 1.26551223f + t * (1.00002368f + t * (0.37409196f + t * (0.09678418f + t * (-0.18628806f + t * (0.27886807f + t * (-1.13520398f + t
                * (1.48851587f + t * (-0.82215223f + t * 0.17087277f)))))))));
        return x >= 0 ? 1.0f - y : y - 1.0f;
    }

    private static float erfinv(float x) {
        float w = -TornadoMath.log((1.0f - x) * (1.0f + x));
        float p;
        if (w < 5.0f) {
            w = w - 2.5f;
            p = 2.81022636e-08f;
            p = 3.43273939e-07f + p * w;
            p = -3.5233877e-06f + p * w;
            p = -4.39150654e-06f + p * w;
            p = 0.00021858087f + p * w;
            p = -0.00125372503f + p * w;
            p = -0.00417768164f + p * w;
            p = 0.246640727f + p * w;
            p = 1.50140941f + p * w;
        } else {
            w = TornadoMath.sqrt(w) - 3.0f;
            p = -0.000200214257f;
            p = 0.000100950558f + p * w;
            p = 0.00134934322f + p * w;
            p = -0.00367342844f + p * w;
            p = 0.00573950773f + p * w;
            p = -0.0076224613f + p * w;
            p = 0.00943887047f + p * w;
            p = 1.00167406f + p * w;
            p = 2.83297682f + p * w;
        }
        return p * x;
    }

    /** A standard normal value by Box-Muller from two streams. */
    private static float standardNormal(int seed, int i) {
        float u1 = openUnit(threefry(seed, 1, i));
        float u2 = unit(threefry(seed, 2, i));
        return TornadoMath.sqrt(-2.0f * TornadoMath.log(u1)) * TornadoMath.cos(TWO_PI * u2);
    }

    @JitBaseline("mlx_random_bits")
    public static void bits(KernelContext ctx, IntArray out, int n, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            out.set(i, threefry(seed, 0, i));
        }
    }

    @JitBaseline("mlx_random_uniform")
    public static void uniformRange(KernelContext ctx, FloatArray out, int n, float low, float high, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            out.set(i, low + (high - low) * unit(threefry(seed, 0, i)));
        }
    }

    /** loc + scale * N(0, 1); per-element loc and scale arrays of length n when {@code broadcast != 0}. */
    @JitBaseline({ "mlx_random_normal", "mlx_random_normal_broadcast" })
    public static void normal(KernelContext ctx, FloatArray out, FloatArray locs, FloatArray scales, int n, float loc, float scale, int broadcast, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            float z = standardNormal(seed, i);
            float l = broadcast != 0 ? locs.get(i) : loc;
            float s = broadcast != 0 ? scales.get(i) : scale;
            out.set(i, l + s * z);
        }
    }

    @JitBaseline("mlx_random_bernoulli")
    public static void bernoulli(KernelContext ctx, FloatArray p, ByteArray out, int n, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            out.set(i, (byte) (unit(threefry(seed, 0, i)) < p.get(i) ? 1 : 0));
        }
    }

    @JitBaseline("mlx_random_randint")
    public static void randint(KernelContext ctx, IntArray out, int n, int low, int high, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            int v = low + (int) TornadoMath.floor(unit(threefry(seed, 0, i)) * (high - low));
            out.set(i, v < high ? v : high - 1);
        }
    }

    /** N(0, 1) restricted to [lower, upper], by inverting the normal CDF between its values at the bounds. */
    @JitBaseline("mlx_random_truncated_normal")
    public static void truncatedNormal(KernelContext ctx, FloatArray out, int n, float lower, float upper, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            float lo = erf(lower / SQRT2);
            float hi = erf(upper / SQRT2);
            float u = openUnit(threefry(seed, 0, i));
            float v = SQRT2 * erfinv(lo + u * (hi - lo));
            out.set(i, TornadoMath.min(TornadoMath.max(v, lower), upper));
        }
    }

    @JitBaseline("mlx_random_gumbel")
    public static void gumbel(KernelContext ctx, FloatArray out, int n, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            out.set(i, -TornadoMath.log(-TornadoMath.log(openUnit(threefry(seed, 0, i)))));
        }
    }

    @JitBaseline("mlx_random_laplace")
    public static void laplace(KernelContext ctx, FloatArray out, int n, float loc, float scale, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            float u = openUnit(threefry(seed, 0, i)) - 0.5f;
            float s = u < 0 ? -1.0f : 1.0f;
            out.set(i, loc - scale * s * TornadoMath.log(1.0f - 2.0f * TornadoMath.abs(u)));
        }
    }

    /**
     * Samples from the softmax of each row of logits [rows, classes] by the Gumbel-max trick; out
     * [samples, rows]. One threadgroup of {@link #THREADS} threads per sample: each thread keeps the
     * best perturbed logit over a strided subset of the classes, then a tree reduction picks the
     * group's maximum (ties to the lower class).
     */
    @JitBaseline({ "mlx_random_categorical", "mlx_random_categorical_shape", "mlx_random_categorical_num_samples" })
    public static void categorical(KernelContext ctx, FloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        float[] bestValue = ctx.allocateFloatLocalArray(THREADS);
        int[] bestClass = ctx.allocateIntLocalArray(THREADS);
        int t = ctx.groupIdx;
        int tid = ctx.localIdx;
        int base = (t % rows) * classes;
        float best = -Float.MAX_VALUE;
        int arg = classes;
        for (int c = tid; c < classes; c += THREADS) {
            float v = logits.get(base + c) - TornadoMath.log(-TornadoMath.log(openUnit(threefry(seed, t, c))));
            if (v > best) {
                best = v;
                arg = c;
            }
        }
        bestValue[tid] = best;
        bestClass[tid] = arg;
        ctx.localBarrier();
        for (int stride = THREADS / 2; stride > 0; stride >>= 1) {
            if (tid < stride) {
                float other = bestValue[tid + stride];
                int otherClass = bestClass[tid + stride];
                if (other > bestValue[tid] || (other == bestValue[tid] && otherClass < bestClass[tid])) {
                    bestValue[tid] = other;
                    bestClass[tid] = otherClass;
                }
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            out.set(t, bestClass[0] < classes ? bestClass[0] : 0);
        }
    }

    /**
     * The first pass of {@link #categorical} for few samples over wide rows: the row of each sample is
     * split into {@code chunks} contiguous slices, one threadgroup per slice, and each group writes its
     * best perturbed logit and class to {@code partialValue} and {@code partialClass} [samples,
     * chunks]. {@link #categoricalMerge} then picks each sample's maximum. The draws are the same as
     * {@link #categorical}'s.
     */
    @JitBaseline({ "mlx_random_categorical", "mlx_random_categorical_shape", "mlx_random_categorical_num_samples" })
    public static void categoricalChunked(KernelContext ctx, FloatArray logits, FloatArray partialValue, IntArray partialClass, int rows, int classes, int chunks, int seed) {
        float[] bestValue = ctx.allocateFloatLocalArray(THREADS);
        int[] bestClass = ctx.allocateIntLocalArray(THREADS);
        int group = ctx.groupIdx;
        int t = group / chunks;
        int tid = ctx.localIdx;
        int base = (t % rows) * classes;
        int len = (classes + chunks - 1) / chunks;
        int start = (group % chunks) * len;
        int end = TornadoMath.min(start + len, classes);
        float best = -Float.MAX_VALUE;
        int arg = classes;
        for (int c = start + tid; c < end; c += THREADS) {
            float v = logits.get(base + c) - TornadoMath.log(-TornadoMath.log(openUnit(threefry(seed, t, c))));
            if (v > best) {
                best = v;
                arg = c;
            }
        }
        bestValue[tid] = best;
        bestClass[tid] = arg;
        ctx.localBarrier();
        for (int stride = THREADS / 2; stride > 0; stride >>= 1) {
            if (tid < stride) {
                float other = bestValue[tid + stride];
                int otherClass = bestClass[tid + stride];
                if (other > bestValue[tid] || (other == bestValue[tid] && otherClass < bestClass[tid])) {
                    bestValue[tid] = other;
                    bestClass[tid] = otherClass;
                }
            }
            ctx.localBarrier();
        }
        if (tid == 0) {
            partialValue.set(group, bestValue[0]);
            partialClass.set(group, bestClass[0]);
        }
    }

    /** The second pass of {@link #categoricalChunked}: one thread per sample; chunks are in class order, so the first maximum wins ties. */
    @JitBaseline({ "mlx_random_categorical", "mlx_random_categorical_shape", "mlx_random_categorical_num_samples" })
    public static void categoricalMerge(KernelContext ctx, FloatArray partialValue, IntArray partialClass, IntArray out, int count, int chunks, int classes) {
        int t = ctx.globalIdx;
        if (t < count) {
            float best = -Float.MAX_VALUE;
            int arg = classes;
            for (int k = 0; k < chunks; k++) {
                float v = partialValue.get(t * chunks + k);
                if (v > best) {
                    best = v;
                    arg = partialClass.get(t * chunks + k);
                }
            }
            out.set(t, arg < classes ? arg : 0);
        }
    }

    /** Random sort keys, uniform in [0, 1): sorting them gives a random permutation. */
    @JitBaseline("mlx_random_permutation_arange")
    public static void sortKeys(KernelContext ctx, FloatArray keys, int n, int seed) {
        int i = ctx.globalIdx;
        if (i < n) {
            keys.set(i, unit(threefry(seed, 0, i)));
        }
    }

}
