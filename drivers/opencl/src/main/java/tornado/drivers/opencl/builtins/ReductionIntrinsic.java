/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.builtins;

import tornado.api.ReductionOp;
import tornado.collections.types.Float2;
import tornado.collections.types.Float4;
import tornado.collections.types.VectorFloat4;

import static tornado.drivers.opencl.builtins.Intrinsics.*;

public class ReductionIntrinsic {

    public static <T> T op(T a, T b) {
        return a;
    }

    public static void reduceLocalThreadsPrimitive4(float[] global, float[] local, float value) {

        final int index = getLocalId(0);
        local[index] = value;
        localBarrier();

        VectorFloat4 values = new VectorFloat4(local);
        Float4 a = values.get(index);
        for (int i = values.getLength() >> 1; i > 0; i >>= 1) {
            if (index < i) {
                Float4 b = values.get(index + i);
                a = op(a, b);
                values.set(index, a);
            }
            localBarrier();
        }

        Float2 x = op(a.getHi(), a.getLo());
        global[getGroupId(0)] = op(x.getX(), x.getY());
    }

    public static float reduceGF4(ReductionOp op, float[] global) {

        final int index = getGlobalId(0);
        VectorFloat4 values = new VectorFloat4(global);
        Float4 a = values.get(index);
        for (int i = values.getLength() >> 1; i > 0; i >>= 1) {
            if (index < i) {
                Float4 b = values.get(index + i);
                a = Intrinsics.op(op, a, b);
                values.set(index, a);
            }
            localBarrier();
        }

        Float2 x = Intrinsics.op(op, a.getHi(), a.getLo());
        return Intrinsics.op(op, x.getX(), x.getY());
    }

}
