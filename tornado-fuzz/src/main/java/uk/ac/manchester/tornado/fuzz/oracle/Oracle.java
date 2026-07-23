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
package uk.ac.manchester.tornado.fuzz.oracle;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

/**
 * Compares a CUDA-backend result against the JVM sequential golden reference.
 * Integers are compared exactly; floating point uses a relative+absolute
 * tolerance with NaN==NaN and signed-infinity treated as equal (these encode
 * legitimate GPU/JVM rounding differences, not codegen bugs).
 */
public final class Oracle {

    private static final float FLOAT_REL = 1e-3f;
    private static final float FLOAT_ABS = 1e-3f;
    private static final double DOUBLE_REL = 1e-9;
    private static final double DOUBLE_ABS = 1e-9;
    private static final int MAX_DETAIL = 16;

    private Oracle() {
    }

    public static Diff compare(IntArray expected, IntArray actual) {
        StringBuilder detail = new StringBuilder();
        int first = -1;
        int count = 0;
        for (int i = 0; i < expected.getSize(); i++) {
            if (expected.get(i) != actual.get(i)) {
                if (first < 0) {
                    first = i;
                }
                if (count < MAX_DETAIL) {
                    detail.append(String.format("[%d] expected=%d actual=%d%n", i, expected.get(i), actual.get(i)));
                }
                count++;
            }
        }
        return first < 0 ? null : new Diff(first, Integer.toString(expected.get(first)), Integer.toString(actual.get(first)), count, detail.toString());
    }

    public static Diff compare(LongArray expected, LongArray actual) {
        StringBuilder detail = new StringBuilder();
        int first = -1;
        int count = 0;
        for (int i = 0; i < expected.getSize(); i++) {
            if (expected.get(i) != actual.get(i)) {
                if (first < 0) {
                    first = i;
                }
                if (count < MAX_DETAIL) {
                    detail.append(String.format("[%d] expected=%d actual=%d%n", i, expected.get(i), actual.get(i)));
                }
                count++;
            }
        }
        return first < 0 ? null : new Diff(first, Long.toString(expected.get(first)), Long.toString(actual.get(first)), count, detail.toString());
    }

    public static Diff compare(FloatArray expected, FloatArray actual) {
        StringBuilder detail = new StringBuilder();
        int first = -1;
        int count = 0;
        for (int i = 0; i < expected.getSize(); i++) {
            float e = expected.get(i);
            float a = actual.get(i);
            if (!floatEqual(e, a)) {
                if (first < 0) {
                    first = i;
                }
                if (count < MAX_DETAIL) {
                    detail.append(String.format("[%d] expected=%s (0x%08x) actual=%s (0x%08x)%n", i, Float.toString(e), Float.floatToRawIntBits(e), Float.toString(a), Float.floatToRawIntBits(a)));
                }
                count++;
            }
        }
        return first < 0 ? null : new Diff(first, Float.toString(expected.get(first)), Float.toString(actual.get(first)), count, detail.toString());
    }

    public static Diff compare(DoubleArray expected, DoubleArray actual) {
        StringBuilder detail = new StringBuilder();
        int first = -1;
        int count = 0;
        for (int i = 0; i < expected.getSize(); i++) {
            double e = expected.get(i);
            double a = actual.get(i);
            if (!doubleEqual(e, a)) {
                if (first < 0) {
                    first = i;
                }
                if (count < MAX_DETAIL) {
                    detail.append(String.format("[%d] expected=%s (0x%016x) actual=%s (0x%016x)%n", i, Double.toString(e), Double.doubleToRawLongBits(e), Double.toString(a), Double.doubleToRawLongBits(a)));
                }
                count++;
            }
        }
        return first < 0 ? null : new Diff(first, Double.toString(expected.get(first)), Double.toString(actual.get(first)), count, detail.toString());
    }

    private static boolean floatEqual(float e, float a) {
        if (Float.isNaN(e) && Float.isNaN(a)) {
            return true;
        }
        if (e == a) {
            return true; // covers same-sign infinities and exact matches
        }
        if (Float.isInfinite(e) || Float.isInfinite(a)) {
            return false;
        }
        float diff = Math.abs(e - a);
        return diff <= FLOAT_ABS + FLOAT_REL * Math.abs(e);
    }

    private static boolean doubleEqual(double e, double a) {
        if (Double.isNaN(e) && Double.isNaN(a)) {
            return true;
        }
        if (e == a) {
            return true;
        }
        if (Double.isInfinite(e) || Double.isInfinite(a)) {
            return false;
        }
        double diff = Math.abs(e - a);
        return diff <= DOUBLE_ABS + DOUBLE_REL * Math.abs(e);
    }
}
