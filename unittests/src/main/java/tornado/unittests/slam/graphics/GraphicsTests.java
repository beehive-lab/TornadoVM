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
 * Authors: Juan Fumero
 *
 */

package tornado.unittests.slam.graphics;

import static tornado.collections.types.Float3.dot;
import static tornado.collections.types.Float3.mult;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.collections.types.Float3;
import tornado.collections.types.ImageFloat;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TornadoTestBase;

public class GraphicsTests extends TornadoTestBase {

    private static void depth2vertex(ImageFloat3 verticies, ImageFloat depths, Matrix4x4Float invK) {
        for (@Parallel int y = 0; y < depths.Y(); y++) {
            for (@Parallel int x = 0; x < depths.X(); x++) {

                final float depth = depths.get(x, y);
                final Float3 pix = new Float3(x, y, 1f);

                final Float3 vertex = (depth > 0) ? mult(rotate(invK, pix), depth) : new Float3();

                verticies.set(x, y, vertex);
            }
        }
    }

    private static final Float3 rotate(Matrix4x4Float m, Float3 x) {
        final Float3 result = new Float3(dot(m.row(0).asFloat3(), x), dot(m.row(1).asFloat3(), x), dot(m.row(2).asFloat3(), x));
        return result;
    }

    private static void testRotate(Matrix4x4Float m, VectorFloat3 x, VectorFloat3 result) {
        for (int i = 0; i < x.size(); i++) {
            Float3 r = rotate(m, x.get(i));
            result.set(i, r);
        }
    }

    @Test
    public void testRotate() {

        final int size = 16;
        Matrix4x4Float m = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m.set(i, j, (float) j);
            }
        }

        VectorFloat3 f3 = new VectorFloat3(size);
        VectorFloat3 result = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            f3.set(i, new Float3(1f, 2f, 3f));
        }

        TaskSchedule t0 = new TaskSchedule("t0");
        t0.task("s0", GraphicsTests::testRotate, m, f3, result);
        t0.streamIn(result);
        t0.execute();

        for (int i = 0; i < size; i++) {
            System.out.println(result.get(i));
        }

    }

}
