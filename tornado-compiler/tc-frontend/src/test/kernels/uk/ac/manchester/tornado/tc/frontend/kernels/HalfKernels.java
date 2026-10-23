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
