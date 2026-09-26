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
package uk.ac.manchester.tornado.mlx.benchmarks;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Sequential plain-Java references for the Tier 2 and Tier 3 cases of {@link OpEvaluation}: each
 * computes what the matching MLX task computes, on the same TornadoVM arrays through get and set,
 * and writes into the case's output. Every element-wise operation has its own loop, so no loop
 * calls a shared lambda per element. Reductions and scans view their input as
 * {@code [outer, len, inner]} and run along the middle axis, as {@code MlxReduce} does; long sums
 * accumulate in double.
 */
final class JavaTierReferences {

    private JavaTierReferences() {
    }

    /** A reference of a one-input float operation. */
    interface Unary {
        void run(FloatArray a, FloatArray out);
    }

    /** A reference of a two-input float operation. */
    interface Binary {
        void run(FloatArray a, FloatArray b, FloatArray c);
    }

    /** A reference of a float comparison into 0 or 1. */
    interface Compare {
        void run(FloatArray a, FloatArray b, ByteArray out);
    }

    /** A reference of a float classification into 0 or 1. */
    interface Classify {
        void run(FloatArray a, ByteArray out);
    }

    /** A reference of a two-input int32 operation. */
    interface Bitwise {
        void run(IntArray a, IntArray b, IntArray out);
    }

    // ---------------------------------------------------------------- unary math

    static void abs(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, Math.abs(a.get(i)));
        }
    }

    static void arccos(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.acos(a.get(i)));
        }
    }

    static void arccosh(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            double x = a.get(i);
            out.set(i, (float) Math.log(x + Math.sqrt(x * x - 1.0)));
        }
    }

    static void arcsin(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.asin(a.get(i)));
        }
    }

    static void arcsinh(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            double x = a.get(i);
            double y = Math.log(Math.abs(x) + Math.sqrt(x * x + 1.0));
            out.set(i, (float) (x < 0 ? -y : y));
        }
    }

    static void arctan(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.atan(a.get(i)));
        }
    }

    static void arctanh(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            double x = a.get(i);
            out.set(i, (float) (0.5 * Math.log((1.0 + x) / (1.0 - x))));
        }
    }

    static void ceil(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.ceil(a.get(i)));
        }
    }

    static void cos(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.cos(a.get(i)));
        }
    }

    static void cosh(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.cosh(a.get(i)));
        }
    }

    static void degrees(FloatArray a, FloatArray out) {
        final float scale = (float) (180.0 / Math.PI);
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * scale);
        }
    }

    /** The inverse error function, by Giles' single-precision approximation (Java has none built in). */
    static void erfinv(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            float x = a.get(i);
            float w = (float) -Math.log((1.0f - x) * (1.0f + x));
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
                w = (float) Math.sqrt(w) - 3.0f;
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
            out.set(i, p * x);
        }
    }

    static void expm1(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.expm1(a.get(i)));
        }
    }

    static void floor(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.floor(a.get(i)));
        }
    }

    static void log(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.log(a.get(i)));
        }
    }

    static void log10(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.log10(a.get(i)));
        }
    }

    static void log1p(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.log1p(a.get(i)));
        }
    }

    static void log2(FloatArray a, FloatArray out) {
        final double invLn2 = 1.0 / Math.log(2.0);
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) (Math.log(a.get(i)) * invLn2));
        }
    }

    static void radians(FloatArray a, FloatArray out) {
        final float scale = (float) (Math.PI / 180.0);
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * scale);
        }
    }

    static void reciprocal(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, 1.0f / a.get(i));
        }
    }

    static void sign(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, Math.signum(a.get(i)));
        }
    }

    static void sin(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.sin(a.get(i)));
        }
    }

    static void sinh(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.sinh(a.get(i)));
        }
    }

    static void tan(FloatArray a, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.tan(a.get(i)));
        }
    }

    /** Rounds to {@code decimals} decimal places, ties to even. */
    static void round(FloatArray a, FloatArray out, int decimals) {
        float scale = 1.0f;
        for (int k = 0; k < decimals; k++) {
            scale *= 10.0f;
        }
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, (float) Math.rint(a.get(i) * scale) / scale);
        }
    }

    /** {@code out = min(max(a, lo), hi)}. */
    static void clip(FloatArray a, FloatArray out, float lo, float hi) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(i, Math.min(Math.max(a.get(i), lo), hi));
        }
    }

    /** {@code out = condition != 0 ? x : y}. */
    static void where(ByteArray condition, FloatArray x, FloatArray y, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, condition.get(i) != 0 ? x.get(i) : y.get(i));
        }
    }

    // ---------------------------------------------------------------- binary math

    static void arctan2(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, (float) Math.atan2(a.get(i), b.get(i)));
        }
    }

    static void floorDivide(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, (float) Math.floor(a.get(i) / b.get(i)));
        }
    }

    static void logaddexp(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            float x = a.get(i);
            float y = b.get(i);
            c.set(i, (float) (Math.max(x, y) + Math.log1p(Math.exp(-Math.abs(x - y)))));
        }
    }

    static void power(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, (float) Math.pow(a.get(i), b.get(i)));
        }
    }

    /** The remainder with the sign of the divisor, as Python and MLX define it. */
    static void remainder(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            float x = a.get(i);
            float y = b.get(i);
            float r = x % y;
            if (r != 0.0f && (r < 0.0f) != (y < 0.0f)) {
                r += y;
            }
            c.set(i, r);
        }
    }

    /** MLX's divmod: the quotient rounded toward zero and the remainder with the sign of the divisor. */
    static void divmod(FloatArray a, FloatArray b, FloatArray quotient, FloatArray remainder) {
        for (int i = 0; i < a.getSize(); i++) {
            float x = a.get(i);
            float y = b.get(i);
            float q = (float) (int) (x / y);
            float r = x % y;
            if (r != 0.0f && (r < 0.0f) != (y < 0.0f)) {
                r += y;
            }
            quotient.set(i, q);
            remainder.set(i, r);
        }
    }

    // ---------------------------------------------------------------- comparisons, classification and logic

    static void equal(FloatArray a, FloatArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) == b.get(i) ? 1 : 0));
        }
    }

    static void notEqual(FloatArray a, FloatArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) != b.get(i) ? 1 : 0));
        }
    }

    static void greater(FloatArray a, FloatArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) > b.get(i) ? 1 : 0));
        }
    }

    static void greaterEqual(FloatArray a, FloatArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) >= b.get(i) ? 1 : 0));
        }
    }

    static void less(FloatArray a, FloatArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) < b.get(i) ? 1 : 0));
        }
    }

    static void lessEqual(FloatArray a, FloatArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) <= b.get(i) ? 1 : 0));
        }
    }

    static void isfinite(FloatArray a, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (Float.isFinite(a.get(i)) ? 1 : 0));
        }
    }

    static void isinf(FloatArray a, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (Float.isInfinite(a.get(i)) ? 1 : 0));
        }
    }

    static void isnan(FloatArray a, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (Float.isNaN(a.get(i)) ? 1 : 0));
        }
    }

    static void isneginf(FloatArray a, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) == Float.NEGATIVE_INFINITY ? 1 : 0));
        }
    }

    static void isposinf(FloatArray a, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) == Float.POSITIVE_INFINITY ? 1 : 0));
        }
    }

    static void bitwiseAnd(IntArray a, IntArray b, IntArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) & b.get(i));
        }
    }

    static void bitwiseOr(IntArray a, IntArray b, IntArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) | b.get(i));
        }
    }

    static void bitwiseXor(IntArray a, IntArray b, IntArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) ^ b.get(i));
        }
    }

    static void leftShift(IntArray a, IntArray b, IntArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) << b.get(i));
        }
    }

    /** Arithmetic right shift. */
    static void rightShift(IntArray a, IntArray b, IntArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) >> b.get(i));
        }
    }

    static void bitwiseInvert(IntArray a, IntArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, ~a.get(i));
        }
    }

    /** Whether x and y are close: NaNs only when {@code equalNan}, infinities only when equal. */
    private static boolean close(float x, float y, float rtol, float atol, boolean equalNan) {
        if (Float.isNaN(x) || Float.isNaN(y)) {
            return equalNan && Float.isNaN(x) && Float.isNaN(y);
        }
        if (Float.isInfinite(x) || Float.isInfinite(y)) {
            return x == y;
        }
        return Math.abs(x - y) <= atol + rtol * Math.abs(y);
    }

    static void isclose(FloatArray a, FloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (close(a.get(i), b.get(i), rtol, atol, equalNan) ? 1 : 0));
        }
    }

    static void allclose(FloatArray a, FloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        boolean all = true;
        for (int i = 0; i < a.getSize() && all; i++) {
            all = close(a.get(i), b.get(i), rtol, atol, equalNan);
        }
        out.set(0, (byte) (all ? 1 : 0));
    }

    static void arrayEqual(FloatArray a, FloatArray b, ByteArray out) {
        boolean all = a.getSize() == b.getSize();
        for (int i = 0; i < a.getSize() && all; i++) {
            all = a.get(i) == b.get(i);
        }
        out.set(0, (byte) (all ? 1 : 0));
    }

    static void logicalAnd(ByteArray a, ByteArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) != 0 && b.get(i) != 0 ? 1 : 0));
        }
    }

    static void logicalOr(ByteArray a, ByteArray b, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) != 0 || b.get(i) != 0 ? 1 : 0));
        }
    }

    static void logicalNot(ByteArray a, ByteArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (byte) (a.get(i) == 0 ? 1 : 0));
        }
    }

    static void nanToNum(FloatArray a, FloatArray out, float nan, float posinf, float neginf) {
        for (int i = 0; i < out.getSize(); i++) {
            float v = a.get(i);
            if (Float.isNaN(v)) {
                v = nan;
            } else if (v == Float.POSITIVE_INFINITY) {
                v = posinf;
            } else if (v == Float.NEGATIVE_INFINITY) {
                v = neginf;
            }
            out.set(i, v);
        }
    }

    /** {@code out[i]} = the real ({@code part == 0}) or imaginary part of complex {@code z[i]} (interleaved pairs). */
    static void complexPart(FloatArray z, FloatArray out, int part) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, z.get(2 * i + part));
        }
    }

    static void conjugate(FloatArray z, FloatArray out) {
        for (int i = 0; i < z.getSize(); i += 2) {
            out.set(i, z.get(i));
            out.set(i + 1, -z.get(i + 1));
        }
    }

    // ---------------------------------------------------------------- reductions over the middle axis of [outer, len, inner]

    static void sum(FloatArray x, FloatArray out, int outer, int len, int inner) {
        sumScaled(x, out, outer, len, inner, 1.0);
    }

    static void mean(FloatArray x, FloatArray out, int outer, int len, int inner) {
        sumScaled(x, out, outer, len, inner, 1.0 / len);
    }

    private static void sumScaled(FloatArray x, FloatArray out, int outer, int len, int inner, double scale) {
        if (inner == 1) {
            for (int o = 0; o < outer; o++) {
                int base = o * len;
                double s = 0.0;
                for (int l = 0; l < len; l++) {
                    s += x.get(base + l);
                }
                out.set(o, (float) (s * scale));
            }
            return;
        }
        double[] acc = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, 0.0);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] += x.get(row + j);
                }
            }
            for (int j = 0; j < inner; j++) {
                out.set(o * inner + j, (float) (acc[j] * scale));
            }
        }
    }

    static void prod(FloatArray x, FloatArray out, int outer, int len, int inner) {
        if (inner == 1) {
            for (int o = 0; o < outer; o++) {
                int base = o * len;
                double p = 1.0;
                for (int l = 0; l < len; l++) {
                    p *= x.get(base + l);
                }
                out.set(o, (float) p);
            }
            return;
        }
        double[] acc = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, 1.0);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] *= x.get(row + j);
                }
            }
            for (int j = 0; j < inner; j++) {
                out.set(o * inner + j, (float) acc[j]);
            }
        }
    }

    static void max(FloatArray x, FloatArray out, int outer, int len, int inner) {
        if (inner == 1) {
            for (int o = 0; o < outer; o++) {
                int base = o * len;
                float m = Float.NEGATIVE_INFINITY;
                for (int l = 0; l < len; l++) {
                    m = Math.max(m, x.get(base + l));
                }
                out.set(o, m);
            }
            return;
        }
        float[] acc = new float[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, Float.NEGATIVE_INFINITY);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] = Math.max(acc[j], x.get(row + j));
                }
            }
            for (int j = 0; j < inner; j++) {
                out.set(o * inner + j, acc[j]);
            }
        }
    }

    static void min(FloatArray x, FloatArray out, int outer, int len, int inner) {
        if (inner == 1) {
            for (int o = 0; o < outer; o++) {
                int base = o * len;
                float m = Float.POSITIVE_INFINITY;
                for (int l = 0; l < len; l++) {
                    m = Math.min(m, x.get(base + l));
                }
                out.set(o, m);
            }
            return;
        }
        float[] acc = new float[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, Float.POSITIVE_INFINITY);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] = Math.min(acc[j], x.get(row + j));
                }
            }
            for (int j = 0; j < inner; j++) {
                out.set(o * inner + j, acc[j]);
            }
        }
    }

    /** log(sum(exp(x))) in two passes: the maximum, then the sum of exp(x - max). */
    static void logsumexp(FloatArray x, FloatArray out, int outer, int len, int inner) {
        if (inner == 1) {
            for (int o = 0; o < outer; o++) {
                int base = o * len;
                float m = Float.NEGATIVE_INFINITY;
                for (int l = 0; l < len; l++) {
                    m = Math.max(m, x.get(base + l));
                }
                double s = 0.0;
                for (int l = 0; l < len; l++) {
                    s += Math.exp(x.get(base + l) - m);
                }
                out.set(o, (float) (m + Math.log(s)));
            }
            return;
        }
        float[] mx = new float[inner];
        double[] acc = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(mx, Float.NEGATIVE_INFINITY);
            Arrays.fill(acc, 0.0);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    mx[j] = Math.max(mx[j], x.get(row + j));
                }
            }
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] += Math.exp(x.get(row + j) - mx[j]);
                }
            }
            for (int j = 0; j < inner; j++) {
                out.set(o * inner + j, (float) (mx[j] + Math.log(acc[j])));
            }
        }
    }

    static void var(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof) {
        variance(x, out, outer, len, inner, ddof, false);
    }

    static void std(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof) {
        variance(x, out, outer, len, inner, ddof, true);
    }

    /** The variance (divided by {@code len - ddof}) in two passes, or its square root. */
    private static void variance(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof, boolean sqrt) {
        double divisor = len - ddof;
        if (inner == 1) {
            for (int o = 0; o < outer; o++) {
                int base = o * len;
                double s = 0.0;
                for (int l = 0; l < len; l++) {
                    s += x.get(base + l);
                }
                double mu = s / len;
                double m2 = 0.0;
                for (int l = 0; l < len; l++) {
                    double d = x.get(base + l) - mu;
                    m2 += d * d;
                }
                double v = m2 / divisor;
                out.set(o, (float) (sqrt ? Math.sqrt(v) : v));
            }
            return;
        }
        double[] mu = new double[inner];
        double[] m2 = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(mu, 0.0);
            Arrays.fill(m2, 0.0);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    mu[j] += x.get(row + j);
                }
            }
            for (int j = 0; j < inner; j++) {
                mu[j] /= len;
            }
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    double d = x.get(row + j) - mu[j];
                    m2[j] += d * d;
                }
            }
            for (int j = 0; j < inner; j++) {
                double v = m2[j] / divisor;
                out.set(o * inner + j, (float) (sqrt ? Math.sqrt(v) : v));
            }
        }
    }

    /** 1 where every element of the slice is non-zero (the whole slice is scanned, as MLX does). */
    static void all(FloatArray x, ByteArray out, int outer, int len, int inner) {
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                boolean r = true;
                for (int l = 0; l < len; l++) {
                    r &= x.get((o * len + l) * inner + j) != 0.0f;
                }
                out.set(o * inner + j, (byte) (r ? 1 : 0));
            }
        }
    }

    /** 1 where any element of the slice is non-zero (the whole slice is scanned, as MLX does). */
    static void any(FloatArray x, ByteArray out, int outer, int len, int inner) {
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                boolean r = false;
                for (int l = 0; l < len; l++) {
                    r |= x.get((o * len + l) * inner + j) != 0.0f;
                }
                out.set(o * inner + j, (byte) (r ? 1 : 0));
            }
        }
    }

    /** The index within the slice of its smallest element, the first on ties. */
    static void argmin(FloatArray x, IntArray out, int outer, int len, int inner) {
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int base = o * len * inner + j;
                float best = x.get(base);
                int index = 0;
                for (int l = 1; l < len; l++) {
                    float v = x.get(base + l * inner);
                    if (v < best) {
                        best = v;
                        index = l;
                    }
                }
                out.set(o * inner + j, index);
            }
        }
    }

    /** The median of each slice (the mean of the two middle values for even {@code len}). */
    static void median(FloatArray x, FloatArray out, int outer, int len, int inner) {
        float[] v = new float[len];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int base = o * len * inner + j;
                for (int l = 0; l < len; l++) {
                    v[l] = x.get(base + l * inner);
                }
                Arrays.sort(v);
                float m = (len & 1) == 1 ? v[len / 2] : 0.5f * (v[len / 2 - 1] + v[len / 2]);
                out.set(o * inner + j, m);
            }
        }
    }

    // ---------------------------------------------------------------- inclusive forward scans over the middle axis of [outer, len, inner]

    static void cumsum(FloatArray x, FloatArray out, int outer, int len, int inner) {
        double[] acc = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, 0.0);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] += x.get(row + j);
                    out.set(row + j, (float) acc[j]);
                }
            }
        }
    }

    static void cumprod(FloatArray x, FloatArray out, int outer, int len, int inner) {
        double[] acc = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, 1.0);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] *= x.get(row + j);
                    out.set(row + j, (float) acc[j]);
                }
            }
        }
    }

    static void cummax(FloatArray x, FloatArray out, int outer, int len, int inner) {
        float[] acc = new float[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, Float.NEGATIVE_INFINITY);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] = Math.max(acc[j], x.get(row + j));
                    out.set(row + j, acc[j]);
                }
            }
        }
    }

    static void cummin(FloatArray x, FloatArray out, int outer, int len, int inner) {
        float[] acc = new float[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, Float.POSITIVE_INFINITY);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    acc[j] = Math.min(acc[j], x.get(row + j));
                    out.set(row + j, acc[j]);
                }
            }
        }
    }

    /** Running log(sum(exp(x))), each step a numerically stable logaddexp. */
    static void logcumsumexp(FloatArray x, FloatArray out, int outer, int len, int inner) {
        double[] acc = new double[inner];
        for (int o = 0; o < outer; o++) {
            Arrays.fill(acc, Double.NEGATIVE_INFINITY);
            for (int l = 0; l < len; l++) {
                int row = (o * len + l) * inner;
                for (int j = 0; j < inner; j++) {
                    double v = x.get(row + j);
                    double a = acc[j];
                    acc[j] = a == Double.NEGATIVE_INFINITY ? v : Math.max(a, v) + Math.log1p(Math.exp(-Math.abs(a - v)));
                    out.set(row + j, (float) acc[j]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- sort and partition over the middle axis of [outer, len, inner]

    /** A long ordered as (value of x, index): the float bits made sortable as a signed int, above the index. */
    private static long key(float x, int index) {
        int bits = Float.floatToRawIntBits(x);
        bits ^= (bits >> 31) & 0x7fffffff;
        return ((long) bits << 32) | index;
    }

    static void sort(FloatArray x, FloatArray out) {
        sortAxis(x, out, 1, x.getSize(), 1);
    }

    static void sortAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        float[] v = new float[len];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int base = o * len * inner + j;
                for (int l = 0; l < len; l++) {
                    v[l] = x.get(base + l * inner);
                }
                Arrays.sort(v);
                for (int l = 0; l < len; l++) {
                    out.set(base + l * inner, v[l]);
                }
            }
        }
    }

    static void argsort(FloatArray x, IntArray out) {
        argsortAxis(x, out, 1, x.getSize(), 1);
    }

    static void argsortAxis(FloatArray x, IntArray out, int outer, int len, int inner) {
        long[] k = new long[len];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int base = o * len * inner + j;
                for (int l = 0; l < len; l++) {
                    k[l] = key(x.get(base + l * inner), l);
                }
                Arrays.sort(k);
                for (int l = 0; l < len; l++) {
                    out.set(base + l * inner, (int) k[l]);
                }
            }
        }
    }

    static void partition(FloatArray x, FloatArray out, int kth) {
        partitionAxis(x, out, 1, x.getSize(), 1, kth);
    }

    static void partitionAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int kth) {
        float[] v = new float[len];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int base = o * len * inner + j;
                for (int l = 0; l < len; l++) {
                    v[l] = x.get(base + l * inner);
                }
                select(v, kth);
                for (int l = 0; l < len; l++) {
                    out.set(base + l * inner, v[l]);
                }
            }
        }
    }

    static void argpartition(FloatArray x, IntArray out, int kth) {
        argpartitionAxis(x, out, 1, x.getSize(), 1, kth);
    }

    static void argpartitionAxis(FloatArray x, IntArray out, int outer, int len, int inner, int kth) {
        long[] k = new long[len];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int base = o * len * inner + j;
                for (int l = 0; l < len; l++) {
                    k[l] = key(x.get(base + l * inner), l);
                }
                select(k, kth);
                for (int l = 0; l < len; l++) {
                    out.set(base + l * inner, (int) k[l]);
                }
            }
        }
    }

    /** Quickselect: reorders a so that a[kth] is in its sorted place, smaller before and larger after. */
    private static void select(float[] a, int kth) {
        int lo = 0;
        int hi = a.length - 1;
        while (hi > lo) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < a[lo]) {
                swap(a, lo, mid);
            }
            if (a[hi] < a[lo]) {
                swap(a, lo, hi);
            }
            if (a[hi] < a[mid]) {
                swap(a, mid, hi);
            }
            float pivot = a[mid];
            int i = lo;
            int j = hi;
            while (i <= j) {
                while (a[i] < pivot) {
                    i++;
                }
                while (a[j] > pivot) {
                    j--;
                }
                if (i <= j) {
                    swap(a, i, j);
                    i++;
                    j--;
                }
            }
            if (kth <= j) {
                hi = j;
            } else if (kth >= i) {
                lo = i;
            } else {
                return;
            }
        }
    }

    /** {@link #select(float[], int)} over sort keys. */
    private static void select(long[] a, int kth) {
        int lo = 0;
        int hi = a.length - 1;
        while (hi > lo) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < a[lo]) {
                swap(a, lo, mid);
            }
            if (a[hi] < a[lo]) {
                swap(a, lo, hi);
            }
            if (a[hi] < a[mid]) {
                swap(a, mid, hi);
            }
            long pivot = a[mid];
            int i = lo;
            int j = hi;
            while (i <= j) {
                while (a[i] < pivot) {
                    i++;
                }
                while (a[j] > pivot) {
                    j--;
                }
                if (i <= j) {
                    swap(a, i, j);
                    i++;
                    j--;
                }
            }
            if (kth <= j) {
                hi = j;
            } else if (kth >= i) {
                lo = i;
            } else {
                return;
            }
        }
    }

    private static void swap(float[] a, int i, int j) {
        float t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static void swap(long[] a, int i, int j) {
        long t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    // ---------------------------------------------------------------- products

    /** {@code c[m, n] = a[m, k] @ b[k, n]}, row by row with a float accumulator row. */
    static void matmul(FloatArray a, FloatArray b, FloatArray c, int m, int k, int n) {
        batchedMatmul(a, b, c, 1, m, k, n);
    }

    /** {@code c[batch, m, n] = a[batch, m, k] @ b[batch, k, n]}. */
    static void batchedMatmul(FloatArray a, FloatArray b, FloatArray c, int batch, int m, int k, int n) {
        float[] acc = new float[n];
        for (int bt = 0; bt < batch; bt++) {
            int aBase = bt * m * k;
            int bBase = bt * k * n;
            int cBase = bt * m * n;
            for (int i = 0; i < m; i++) {
                Arrays.fill(acc, 0.0f);
                for (int p = 0; p < k; p++) {
                    float av = a.get(aBase + i * k + p);
                    int row = bBase + p * n;
                    for (int j = 0; j < n; j++) {
                        acc[j] += av * b.get(row + j);
                    }
                }
                for (int j = 0; j < n; j++) {
                    c.set(cBase + i * n + j, acc[j]);
                }
            }
        }
    }

    /** {@code out[0]} = the dot product of a and b. */
    static void inner(FloatArray a, FloatArray b, FloatArray out) {
        double s = 0.0;
        for (int i = 0; i < a.getSize(); i++) {
            s += a.get(i) * b.get(i);
        }
        out.set(0, (float) s);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    static void outer(FloatArray a, FloatArray b, FloatArray out) {
        int n = b.getSize();
        for (int i = 0; i < a.getSize(); i++) {
            float av = a.get(i);
            for (int j = 0; j < n; j++) {
                out.set(i * n + j, av * b.get(j));
            }
        }
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}. */
    static void kron(FloatArray a, FloatArray b, FloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        int cols = colsA * colsB;
        for (int i = 0; i < rowsA; i++) {
            for (int k = 0; k < rowsB; k++) {
                int row = (i * rowsB + k) * cols;
                for (int j = 0; j < colsA; j++) {
                    float av = a.get(i * colsA + j);
                    for (int l = 0; l < colsB; l++) {
                        out.set(row + j * colsB + l, av * b.get(k * colsB + l));
                    }
                }
            }
        }
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]} over the blocks the masks allow, zero in masked-out output blocks. */
    static void blockMaskedMm(FloatArray a, FloatArray b, ByteArray maskOut, ByteArray maskLhs, ByteArray maskRhs, FloatArray c, int m, int k, int n, int bs) {
        int kBlocks = k / bs;
        int nBlocks = n / bs;
        float[] acc = new float[n];
        for (int i = 0; i < m; i++) {
            int bi = i / bs;
            Arrays.fill(acc, 0.0f);
            for (int bk = 0; bk < kBlocks; bk++) {
                if (maskLhs.get(bi * kBlocks + bk) == 0) {
                    continue;
                }
                for (int bj = 0; bj < nBlocks; bj++) {
                    if (maskRhs.get(bk * nBlocks + bj) == 0) {
                        continue;
                    }
                    for (int p = bk * bs; p < (bk + 1) * bs; p++) {
                        float av = a.get(i * k + p);
                        int row = p * n;
                        for (int j = bj * bs; j < (bj + 1) * bs; j++) {
                            acc[j] += av * b.get(row + j);
                        }
                    }
                }
            }
            for (int j = 0; j < n; j++) {
                c.set(i * n + j, maskOut.get(bi * nBlocks + j / bs) != 0 ? acc[j] : 0.0f);
            }
        }
    }

    /** {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s = (k0, k1)}. */
    static void segmentedMm(FloatArray a, FloatArray b, IntArray segments, FloatArray out, int m, int k, int n) {
        int count = segments.getSize() / 2;
        float[] acc = new float[n];
        for (int s = 0; s < count; s++) {
            int k0 = segments.get(2 * s);
            int k1 = segments.get(2 * s + 1);
            for (int i = 0; i < m; i++) {
                Arrays.fill(acc, 0.0f);
                for (int p = k0; p < k1; p++) {
                    float av = a.get(i * k + p);
                    int row = p * n;
                    for (int j = 0; j < n; j++) {
                        acc[j] += av * b.get(row + j);
                    }
                }
                int base = (s * m + i) * n;
                for (int j = 0; j < n; j++) {
                    out.set(base + j, acc[j]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- shape copies

    /** A copy of x into out. */
    static void copy(FloatArray x, FloatArray out) {
        for (int i = 0; i < x.getSize(); i++) {
            out.set(i, x.get(i));
        }
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    static void permute3(FloatArray x, FloatArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        int[] dims = { d0, d1, d2 };
        int[] strides = { d1 * d2, d2, 1 };
        int e0 = dims[p0];
        int e1 = dims[p1];
        int e2 = dims[p2];
        int s0 = strides[p0];
        int s1 = strides[p1];
        int s2 = strides[p2];
        int o = 0;
        for (int i0 = 0; i0 < e0; i0++) {
            for (int i1 = 0; i1 < e1; i1++) {
                int src = i0 * s0 + i1 * s1;
                for (int i2 = 0; i2 < e2; i2++) {
                    out.set(o++, x.get(src + i2 * s2));
                }
            }
        }
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    static void broadcastTo(FloatArray x, FloatArray out, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out.set(r * cols + c, x.get(c));
            }
        }
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    static void broadcastArrays(FloatArray a, FloatArray b, FloatArray outA, FloatArray outB, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            float bv = b.get(r);
            for (int c = 0; c < cols; c++) {
                outA.set(r * cols + c, a.get(c));
                outB.set(r * cols + c, bv);
            }
        }
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]}. */
    static void asStrided(FloatArray x, FloatArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out.set(r * cols + c, x.get(offset + r * rowStride + c * colStride));
            }
        }
    }

    /** {@code x} converted to float16. */
    static void astype(FloatArray x, HalfFloatArray out) {
        for (int i = 0; i < x.getSize(); i++) {
            out.set(i, new HalfFloat(x.get(i)));
        }
    }

    /** The bits of {@code x} reinterpreted as int32. */
    static void view(FloatArray x, IntArray out) {
        for (int i = 0; i < x.getSize(); i++) {
            out.set(i, Float.floatToRawIntBits(x.get(i)));
        }
    }

    /** {@code out} = {@code a} followed by {@code b} (also stack along a new first axis). */
    static void concatenate(FloatArray a, FloatArray b, FloatArray out) {
        int na = a.getSize();
        for (int i = 0; i < na; i++) {
            out.set(i, a.get(i));
        }
        for (int i = 0; i < b.getSize(); i++) {
            out.set(na + i, b.get(i));
        }
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    static void concatenateAxis(FloatArray a, FloatArray b, FloatArray out, int rows, int colsA, int colsB) {
        int cols = colsA + colsB;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < colsA; c++) {
                out.set(r * cols + c, a.get(r * colsA + c));
            }
            for (int c = 0; c < colsB; c++) {
                out.set(r * cols + colsA + c, b.get(r * colsB + c));
            }
        }
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    static void stackAxis(FloatArray a, FloatArray b, FloatArray out) {
        for (int i = 0; i < a.getSize(); i++) {
            out.set(2 * i, a.get(i));
            out.set(2 * i + 1, b.get(i));
        }
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    static void splitSections(FloatArray x, FloatArray first, FloatArray second, int rows, int cols, int index) {
        int rest = cols - index;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < index; c++) {
                first.set(r * index + c, x.get(r * cols + c));
            }
            for (int c = 0; c < rest; c++) {
                second.set(r * rest + c, x.get(r * cols + index + c));
            }
        }
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    static void repeat(FloatArray x, FloatArray out, int times) {
        for (int i = 0; i < x.getSize(); i++) {
            float v = x.get(i);
            for (int t = 0; t < times; t++) {
                out.set(i * times + t, v);
            }
        }
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    static void repeatAxis(FloatArray x, FloatArray out, int rows, int cols, int times) {
        for (int r = 0; r < rows; r++) {
            for (int t = 0; t < times; t++) {
                int dst = (r * times + t) * cols;
                for (int c = 0; c < cols; c++) {
                    out.set(dst + c, x.get(r * cols + c));
                }
            }
        }
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    static void tile(FloatArray x, FloatArray out, int rows, int cols, int repsRows, int repsCols) {
        int outCols = cols * repsCols;
        for (int r = 0; r < rows * repsRows; r++) {
            int src = (r % rows) * cols;
            for (int c = 0; c < outCols; c++) {
                out.set(r * outCols + c, x.get(src + c % cols));
            }
        }
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    static void roll(FloatArray x, FloatArray out, int shift) {
        int n = x.getSize();
        int s = Math.floorMod(shift, n);
        for (int i = 0; i < n; i++) {
            out.set(i + s < n ? i + s : i + s - n, x.get(i));
        }
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    static void rollAxes(FloatArray x, FloatArray out, int rows, int cols, int shiftRows, int shiftCols) {
        int sr = Math.floorMod(shiftRows, rows);
        int sc = Math.floorMod(shiftCols, cols);
        for (int r = 0; r < rows; r++) {
            int dst = ((r + sr) % rows) * cols;
            for (int c = 0; c < cols; c++) {
                out.set(dst + (c + sc) % cols, x.get(r * cols + c));
            }
        }
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    static void pad(FloatArray x, FloatArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        int outCols = left + cols + right;
        int outRows = top + rows + bottom;
        for (int r = 0; r < outRows; r++) {
            int sr = r - top;
            for (int c = 0; c < outCols; c++) {
                int sc = c - left;
                out.set(r * outCols + c, sr >= 0 && sr < rows && sc >= 0 && sc < cols ? x.get(sr * cols + sc) : value);
            }
        }
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    static void slice(FloatArray x, FloatArray out, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        int o = 0;
        for (int r = r0; r < r1; r += rowStep) {
            for (int c = c0; c < c1; c += colStep) {
                out.set(o++, x.get(r * cols + c));
            }
        }
    }

    /** {@code out = x[start[0] : start[0] + sliceRows, :]} for {@code x} viewed as {@code [rows, cols]}. */
    static void sliceRowsAt(FloatArray x, IntArray start, FloatArray out, int cols, int sliceRows) {
        int base = start.get(0) * cols;
        for (int i = 0; i < sliceRows * cols; i++) {
            out.set(i, x.get(base + i));
        }
    }
}
