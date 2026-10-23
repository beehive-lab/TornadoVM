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
package uk.ac.manchester.tornado.tc.selftest;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

/**
 * The self-check's Java kernels, one TornadoVM feature each, written as TornadoVM users write them.
 * Every check runs the same method on the JVM and compares.
 */
public final class Kernels {

    private static final DeviceKernel DOUBLE = DeviceKernel.of(Kernels::doubleChild);

    private Kernels() {
    }

    /** A 1-D {@code @Parallel} loop; launched with fewer threads than elements, so each thread strides. */
    public static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    /** Two nested {@code @Parallel} loops with scalar bounds: the inner loop is x, the outer y. */
    public static void matrixAdd(FloatArray a, FloatArray b, FloatArray c, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                c.set(i * cols + j, a.get(i * cols + j) * 2.0f + b.get(i * cols + j));
            }
        }
    }

    /** Divergent control flow inside a parallel loop, and a math call. */
    public static void branches(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            float x = in.get(i);
            float y;
            if (x > 0) {
                y = TornadoMath.sqrt(x) * 2.0f;
            } else if (x < -2.0f) {
                y = x * x;
            } else {
                y = -x;
            }
            out.set(i, y);
        }
    }

    /** A work-group reduction in local memory with barriers (KernelContext). */
    public static void blockSum(KernelContext context, FloatArray in, FloatArray out) {
        int lid = context.localIdx;
        int size = context.localGroupSizeX;
        float[] scratch = context.allocateFloatLocalArray(256);
        scratch[lid] = in.get(context.globalIdx);
        for (int stride = size / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (lid < stride) {
                scratch[lid] += scratch[lid + stride];
            }
        }
        if (lid == 0) {
            out.set(context.groupIdx, scratch[0]);
        }
    }

    /** A {@code @Reduce} reduction: the runtime combines the work-groups' partial sums. */
    public static void reduceSum(IntArray in, @Reduce IntArray result) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            result.set(0, result.get(0) + in.get(i));
        }
    }

    /** Global atomics: every thread counts its element into one of 16 bins. */
    public static void histogram(KernelContext context, IntArray in, IntArray bins) {
        int i = context.globalIdx;
        if (i < in.getSize()) {
            context.atomicAdd(bins, in.get(i) & 15, 1);
        }
    }

    /** 64-bit integer arithmetic, wrapping as Java defines it. */
    public static void longs(LongArray in, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            long x = in.get(i);
            out.set(i, x * 0x9E3779B97F4A7C15L + (x >>> 7) - (x << 13) ^ Long.MIN_VALUE);
        }
    }

    /** Double precision. */
    public static void doubles(DoubleArray in, DoubleArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            double x = in.get(i);
            out.set(i, x * x / 3.0 + 1.0 / (x + 0.5));
        }
    }

    /** Half precision, each operation rounded to half as Java's HalfFloat does. */
    public static void halves(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, HalfFloat.add(HalfFloat.mult(a.get(i), b.get(i)), a.get(i)));
        }
    }

    /** Java's integer semantics where C differs: MIN_VALUE / -1, remainders of negatives, shifts, overflow. */
    public static void integerSemantics(IntArray a, IntArray b, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            int x = a.get(i);
            int y = b.get(i);
            int q = y == 0 ? 0 : x / y;
            int r = y == 0 ? 0 : x % y;
            out.set(i, q ^ (r * 31) ^ (x >>> (y & 31)) ^ (x << y) ^ (x + Integer.MAX_VALUE));
        }
    }

    /** Java's float-to-integer conversions: NaN is 0, out-of-range values saturate. */
    public static void conversions(FloatArray in, IntArray ints, LongArray longs) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            ints.set(i, (int) in.get(i));
            longs.set(i, (long) (in.get(i) * 4.0e9f));
        }
    }

    /** Math.min/max: NaN wins, and -0.0 is smaller than +0.0. */
    public static void minMax(FloatArray a, FloatArray b, FloatArray min, FloatArray max) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            min.set(i, Math.min(a.get(i), b.get(i)));
            max.set(i, Math.max(a.get(i), b.get(i)));
        }
    }

    /** {@code new int[]} inside a loop starts from zero on every iteration. */
    public static void privateArrays(IntArray out) {
        for (@Parallel int id = 0; id < out.getSize(); id++) {
            int s = 0;
            for (int k = 0; k < 8; k++) {
                int[] t = new int[2];
                t[0] += k + id;
                t[1] += 1;
                s += t[0] * 10 + t[1];
            }
            out.set(id, s);
        }
    }

    /** IEEE edge cases a strict plan must keep: denormals, NaN from inf - inf, signed zeros. */
    public static void ieeeEdges(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            float x = in.get(i);
            out.set(4 * i, 0.0f + x);
            out.set(4 * i + 1, x - x);
            out.set(4 * i + 2, x * 0.0f);
            out.set(4 * i + 3, x / x);
        }
    }

    /** A warp-wide sum (32-lane warps): every lane gets the sum of its warp. */
    public static void warpSum(KernelContext context, FloatArray in, FloatArray out) {
        int i = context.globalIdx;
        out.set(i, context.simdSum(in.get(i)));
    }

    private static void doubleChild(KernelContext context, IntArray a, int n) {
        int i = context.globalIdx;
        if (i < n) {
            a.set(i, a.get(i) * 2);
        }
    }

    /** A kernel that launches a kernel (CUDA dynamic parallelism): thread 0 doubles every element. */
    public static void launchFromDevice(KernelContext context, IntArray a, int n) {
        if (context.globalIdx == 0) {
            context.launch(DOUBLE, n, 128, a, n);
        }
    }
}
