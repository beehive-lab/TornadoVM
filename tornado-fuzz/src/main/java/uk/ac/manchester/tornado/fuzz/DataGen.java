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
package uk.ac.manchester.tornado.fuzz;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

/**
 * Fills TornadoVM off-heap arrays with edge-value-biased data. Edge values
 * (0, +/-1, type MIN/MAX, and for floats +/-Inf, NaN, denormals) are where the
 * code generator's cast/convert/relational paths are most likely to break.
 */
public final class DataGen {

    public enum Profile {
        /** Only boundary values. */
        EDGE,
        /** Only uniform random values. */
        RANDOM,
        /** ~30% edge values sprinkled into random data. */
        MIXED
    }

    private static final int[] INT_EDGES = { 0, 1, -1, 2, -2, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE - 1, 255, -255 };
    private static final long[] LONG_EDGES = { 0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, (long) Integer.MAX_VALUE, (long) Integer.MIN_VALUE };
    private static final float[] FLOAT_EDGES = { 0f, -0f, 1f, -1f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, 0.5f, (float) Math.PI };
    private static final double[] DOUBLE_EDGES = { 0d, -0d, 1d, -1d, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, Double.MIN_VALUE, Double.MAX_VALUE, 0.5d, Math.PI };

    private DataGen() {
    }

    private static boolean useEdge(Profile p, RandomGen rng) {
        return switch (p) {
            case EDGE -> true;
            case RANDOM -> false;
            case MIXED -> rng.nextInt(10) < 3;
        };
    }

    public static void fill(IntArray a, RandomGen rng, Profile p) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, useEdge(p, rng) ? rng.pick(INT_EDGES) : rng.nextIntBetween(-100000, 100000));
        }
    }

    public static void fill(LongArray a, RandomGen rng, Profile p) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, useEdge(p, rng) ? LONG_EDGES[rng.nextInt(LONG_EDGES.length)] : rng.nextLong());
        }
    }

    public static void fill(FloatArray a, RandomGen rng, Profile p) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, useEdge(p, rng) ? FLOAT_EDGES[rng.nextInt(FLOAT_EDGES.length)] : (rng.nextFloat() - 0.5f) * 2000f);
        }
    }

    public static void fill(DoubleArray a, RandomGen rng, Profile p) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, useEdge(p, rng) ? DOUBLE_EDGES[rng.nextInt(DOUBLE_EDGES.length)] : (rng.nextDouble() - 0.5d) * 2000d);
        }
    }

    /** A non-zero, non-NaN variant for divisors (avoid trivially uninteresting div-by-zero traps). */
    public static void fillNonZero(IntArray a, RandomGen rng, Profile p) {
        fill(a, rng, p);
        for (int i = 0; i < a.getSize(); i++) {
            if (a.get(i) == 0) {
                a.set(i, 1);
            }
        }
    }
}
