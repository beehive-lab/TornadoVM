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

import java.util.concurrent.atomic.AtomicInteger;

import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Per-row functions compiled as device functions (the {@code cudf::transform} shape), and methods a
 * device function must refuse. The accepted ones are the shapes of the cudf-spark UDF spike: a
 * data-dependent loop, an integer hash with wraparound and {@code floorMod}, a {@code Math}-heavy
 * double function and several arguments of different widths.
 */
public final class UdfKernels {

    private UdfKernels() {
    }

    /** Steps of the Collatz sequence from x to 1. */
    public static int collatz(long x) {
        int steps = 0;
        while (x != 1) {
            x = (x & 1) == 0 ? x >> 1 : 3 * x + 1;
            steps++;
        }
        return steps;
    }

    /** Murmur3_x86_32 of a long (seed 0, as Guava's murmur3_32(0).hashLong), bucketed into 1000. */
    public static int bucket(long user) {
        int h = mix(0, (int) user);
        h = mix(h, (int) (user >>> 32));
        h ^= 8;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return Math.floorMod(h, 1000);
    }

    private static int mix(int h, int k) {
        k *= 0xcc9e2d51;
        k = Integer.rotateLeft(k, 15);
        k *= 0x1b873593;
        h ^= k;
        h = Integer.rotateLeft(h, 13);
        return h * 5 + 0xe6546b64;
    }

    public static double fp(double x) {
        return Math.log1p(x) * Math.sqrt(x) / (1 + Math.exp(-x)) + Math.sin(x) * Math.pow(x, 0.3);
    }

    public static double axpy(double a, double x, double y) {
        return a * x + y;
    }

    /** Several inputs of different widths; Java's int division and Math.min need helpers. */
    public static long mixed(int a, long b, short c, byte d) {
        return a / (c | 1) + b * d + Math.min(a, c);
    }

    public static double minimum(double a, double b) {
        return Math.min(a, b);
    }

    /** A narrow result: the out pointer is the column's element type. */
    public static short narrow(int a) {
        return (short) (a * 3);
    }

    public static boolean isEven(long a) {
        return (a & 1) == 0;
    }

    /** A private array is the function's own: allowed. */
    public static int table(int i) {
        int[] t = new int[4];
        t[0] = 3;
        t[1] = 5;
        t[2] = 7;
        t[3] = 11;
        return t[i & 3];
    }

    // ---- refused

    public static int sum(int[] a) {
        return a[0];
    }

    public static void nothing(int a) {
    }

    public int instance(int a) {
        return a;
    }

    public static int atomic(int a) {
        AtomicInteger counter = new AtomicInteger(0);
        counter.incrementAndGet();
        return a;
    }

    public static int parallel(int n) {
        int acc = 0;
        for (@Parallel int i = 0; i < n; i++) {
            acc += i;
        }
        return acc;
    }
}
