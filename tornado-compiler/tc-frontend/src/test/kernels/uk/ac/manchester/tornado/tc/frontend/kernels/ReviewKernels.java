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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

/** Kernels for the design-review findings that compiled wrong or failed (design-review.md). */
public final class ReviewKernels {
    // TP-8: reductions are IR trees (sum, and max with Java NaN semantics)
    public static void sum(FloatArray in, @Reduce FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(0, out.get(0) + in.get(i));
        }
    }

    public static void maxOf(FloatArray in, @Reduce FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(0, Math.max(out.get(0), in.get(i)));
        }
    }

    // TP-2: a reduction into out[1], not out[0]
    public static void reduceAtOne(FloatArray in, @Reduce FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(1, out.get(1) + in.get(i));
        }
    }
    // TP-4: two sibling parallel loops
    public static void siblings(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + 1);
        }
        for (@Parallel int j = 0; j < b.getSize(); j++) {
            b.set(j, b.get(j) + 1);
        }
    }
    // FE-4: a vector setter in a device helper
    static void bump(Float4 v) {
        v.setX(v.getX() + 1f);
    }
    public static void helperSetter(FloatArray out) {
        Float4 v = new Float4(1f, 2f, 3f, 4f);
        bump(v);
        out.set(0, v.getX());
    }
    // A matrix the kernel only writes is never observed: dropped with its writes.
    public static void writeOnlyMatrix(FloatArray out) {
        Matrix2DFloat f = new Matrix2DFloat(4, 4);
        for (@Parallel int i = 0; i < 4; i++) {
            f.set(i, i, 10);
            out.set(i, i);
        }
    }
    // ... but one it reads is refused.
    public static void readMatrix(FloatArray out) {
        Matrix2DFloat f = new Matrix2DFloat(4, 4);
        f.set(0, 0, 10);
        out.set(0, f.get(0, 0));
    }
    // TP-5: a length read through a ternary
    public static void lengthThroughMerge(FloatArray a, FloatArray b, IntArray out, int pick) {
        FloatArray x = pick > 0 ? a : b;
        out.set(0, x.getSize());
    }
    // TP-14: a scalar parameter used early in the entry block
    public static void scalarEarly(IntArray out, int n) {
        out.set(0, n * 2);
    }
    // TP-3: an index expression that is also a value, in a batch
    public static void batchShared(IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            int k = i + 1;
            out.set(k - 1, k);
        }
    }
    // FE-5: a vector setter after a branch that defined another reference slot
    public static void setterAfterBranch(FloatArray out, int c) {
        if (c > 0) {
            FloatArray x = out;
            x.set(0, 1f);
        }
        Float4 v = new Float4(1f, 2f, 3f, 4f);
        v.setX(5f);
        out.set(1, v.getX());
    }
}
