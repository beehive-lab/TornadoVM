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

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/** Basic kernels (vector add, if/else, block sum), as TornadoVM users write them. */
public final class BasicKernels {

    private BasicKernels() {
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
