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

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;

/** HalfFloat as a value: no object survives translation. */
public final class HalfKernels {

    private HalfKernels() {
    }

    public static void scale(HalfFloatArray a, FloatArray out, float k) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            var h = HalfFloat.mult(a.get(i), new HalfFloat(k));
            out.set(i, h.getFloat32());
        }
    }

    public static void bits(ShortArray in, HalfFloatArray out, ShortArray back) {
        for (@Parallel long i = 0; i < in.getSizeLong(); i++) {
            var h = new HalfFloat(in.get(i));
            out.set(i, h);
            back.set(i, h.getHalfFloatValue());
        }
    }

    /** The local array is read after a branch merge, through a block argument of the merge block. */
    public static void localAfterBranch(KernelContext context, FloatArray out) {
        HalfFloat[] local = context.allocateHalfFloatLocalArray(32);
        if (context.localIdx == 0) {
            local[0] = new HalfFloat(1.5f);
        }
        context.localBarrier();
        out.set(context.localIdx, local[0].getFloat32());
    }
}
