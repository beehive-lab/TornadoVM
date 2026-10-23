/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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

/** Scalar Java semantics the frontend must translate exactly. */
public final class Scalars {

    private Scalars() {
    }

    public static int arithmetic(int a, int b) {
        int x = a * 3 + b;
        x = x - (a / b) + (a % b);
        return x >>> 2 ^ (x << 1) | (x >> 3) & 7;
    }

    public static long widening(int a, long b, float f, double d) {
        long r = a + b;
        r += (long) f;
        r -= (long) d;
        return r + (int) f + (short) a + (byte) a + (char) a;
    }

    public static int floatCompare(float x, float y) {
        if (x < y) {
            return -1;
        }
        if (x >= y) {
            return 1;
        }
        return 0;
    }

    public static int ternary(int a, int b) {
        return a > b ? a - b : b - a;
    }

    public static int loops(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if ((i + j) % 3 == 0) {
                    continue;
                }
                sum += j;
            }
        }
        return sum;
    }

    public static int sw(int k) {
        switch (k) {
            case 1:
                return 10;
            case 2:
            case 3:
                return 20;
            case 100:
                return 30;
            default:
                return -1;
        }
    }

    public static double math(double x, float y) {
        return Math.sqrt(x) + Math.abs(y) + Math.min(x, 2.0);
    }

    public static int localArray(int n) {
        int[] tmp = new int[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = i * n;
        }
        return tmp[n & 7];
    }

    private static float square(float x) {
        return x * x;
    }

    private static float clampSquare(float x, float limit) {
        float s = square(x);
        return s > limit ? limit : s;
    }

    public static float helpers(float x) {
        return clampSquare(x, 100f) + square(2f);
    }

    public static int recursive(int n) {
        return n <= 1 ? 1 : n * recursive(n - 1);
    }
}
