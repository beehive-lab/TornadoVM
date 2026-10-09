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
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/** The three C3 design-test kernels, as TornadoVM users write them. */
public final class C3Kernels {

    private C3Kernels() {
    }

    public static void add(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c) {
        for (int i = ctx.globalIdx; i < a.getSize(); i += ctx.globalGroupSizeX) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void compute(KernelContext ctx, FloatArray in, FloatArray out) {
        int i = ctx.globalIdx;
        if (i < in.getSize()) {
            float x = in.get(i);
            float v;
            if (x > 0) {
                v = TornadoMath.sqrt(x) * 2;
            } else {
                v = -x;
            }
            out.set(i, v);
        }
    }

    public static void blockSum(KernelContext ctx, FloatArray in, FloatArray out) {
        int gid = ctx.globalIdx;
        int lid = ctx.localIdx;
        int grp = ctx.groupIdx;
        int lsz = ctx.localGroupSizeX;
        float[] scratch = ctx.allocateFloatLocalArray(256);
        scratch[lid] = in.get(gid);
        ctx.localBarrier();
        for (int s = lsz / 2; s > 0; s /= 2) {
            if (lid < s) {
                scratch[lid] += scratch[lid + s];
            }
            ctx.localBarrier();
        }
        if (lid == 0) {
            out.set(grp, scratch[0]);
        }
    }
}
