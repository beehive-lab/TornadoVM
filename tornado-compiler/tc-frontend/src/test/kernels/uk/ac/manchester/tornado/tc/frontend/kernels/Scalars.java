/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
