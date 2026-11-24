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
package uk.ac.manchester.tornado.tc.frontend.kernels;

/**
 * Per-row functions for the null-aware and error modes of a device function (cudf::transform with
 * {@code null_aware::YES}, and its error-code result), and methods those modes must still refuse.
 * Boxed parameters and results are SQL values that may be null; an ArithmeticException is the row's
 * error, as Spark's ANSI mode fails the query.
 */
public final class NullableUdfKernels {

    private NullableUdfKernels() {
    }

    // ---- null-aware

    public static Integer plusOne(Integer a) {
        return a == null ? null : a + 1;
    }

    public static Long coalesce(Long a, Long b) {
        return a != null ? a : b;
    }

    public static Double ratio(Long a, Integer b) {
        if (a == null || b == null || b == 0) {
            return null;
        }
        return a.doubleValue() / b;
    }

    public static Boolean isPositive(Double d) {
        return d == null ? null : d > 0;
    }

    /** Unboxes without a check: a null argument is Java's NullPointerException, the row's error. */
    public static int unboxed(Integer a) {
        return a * 3;
    }

    /** A primitive argument of a null-aware function: a null makes the result null without a call. */
    public static long plainTwice(long a) {
        return a * 2;
    }

    /** A string row and a boxed result: no length is a null. */
    public static Integer nullableLength(byte[] s) {
        return s.length == 0 ? null : s.length;
    }

    public static Short shortSum(Short a, Byte b, Character c) {
        if (a == null || b == null || c == null) {
            return null;
        }
        return (short) (a.shortValue() + b.byteValue() + c.charValue());
    }

    // ---- errors (ArithmeticException)

    public static int addExact(int a, int b) {
        return Math.addExact(a, b);
    }

    public static long multiplyExact(long a, long b) {
        return Math.multiplyExact(a, b);
    }

    public static int toIntExact(long a) {
        return Math.toIntExact(a);
    }

    public static int divide(int a, int b) {
        return a / b;
    }

    public static long remainder(long a, long b) {
        return a % b;
    }

    public static int floorMod(long a, int n) {
        return (int) Math.floorMod(a, (long) n);
    }

    public static int checked(int a) {
        if (a < 0) {
            throw new ArithmeticException("negative");
        }
        return a;
    }

    /** Both modes: nulls in, and overflow out. */
    public static Integer safeAdd(Integer a, Integer b) {
        if (a == null || b == null) {
            return null;
        }
        return Math.addExact(a, b);
    }

    // ---- refused

    public static int notArithmetic(int a) {
        if (a < 0) {
            throw new IllegalStateException();
        }
        return a;
    }

    public static int sameBox(Integer a, Integer b) {
        return a.equals(b) ? 1 : 0;
    }
}
