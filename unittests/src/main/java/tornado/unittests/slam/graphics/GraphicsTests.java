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

import static org.junit.Assert.assertEquals;
import static tornado.collections.types.Float3.dot;

import java.util.Random;

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

                final Float3 vertex = (depth > 0) ? Float3.mult(rotate(invK, pix), depth) : new Float3(0f, 0f, 0f);
                // Float3 vertex = null;
                // if (depth > 0) {
                // Float3 rotate = rotate(invK, pix);
                // Float3 mult = Float3.mult(rotate, depth);
                // vertex = mult;
                // } else {
                // vertex = new Float3(0f, 0f, 0f);
                // }

                verticies.set(x, y, vertex);
            }
        }
    }

    private static void testPhiNode(ImageFloat3 verticies, ImageFloat depths, Matrix4x4Float invK) {
        final float depth = depths.get(0, 0);
        final Float3 pix = new Float3(0, 0, 1f);
        final Float3 vertex = (depth > 0) ? Float3.mult(rotate(invK, pix), depth) : new Float3(0f, 0f, 0f);
        verticies.set(0, 0, vertex);
    }

    private static final Float3 rotate(Matrix4x4Float m, Float3 v) {
        final Float3 result = new Float3(dot(m.row(0).asFloat3(), v), dot(m.row(1).asFloat3(), v), dot(m.row(2).asFloat3(), v));
        return result;
    }

    private static void testRotate(Matrix4x4Float m, VectorFloat3 v, VectorFloat3 result) {
        for (@Parallel int i = 0; i < v.getLength(); i++) {
            Float3 r = rotate(m, v.get(i));
            result.set(i, r);
        }
    }

    @Test
    public void testRotate() {
        final int size = 4;
        Random r = new Random();

        Matrix4x4Float matrix4 = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                matrix4.set(i, j, j + r.nextFloat());
            }
        }

        VectorFloat3 vector3 = new VectorFloat3(size);
        VectorFloat3 result = new VectorFloat3(size);
        VectorFloat3 sequential = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            vector3.set(i, new Float3(1f, 2f, 3f));
        }

        // Sequential execution
        testRotate(matrix4, vector3, sequential);

        // @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testRotate, matrix4, vector3, result)
            .streamOut(result)
            .execute();        
        // @formatter:on

        for (int i = 0; i < size; i++) {
            Float3 o = result.get(i);
            Float3 s = sequential.get(i);
            assertEquals(s.getS0(), o.getS0(), 0.001);
            assertEquals(s.getS1(), o.getS1(), 0.001);
            assertEquals(s.getS1(), o.getS1(), 0.001);
        }
    }

    @Test
    public void testDepth2Vertex() {

        final int size = 4;
        Random r = new Random();

        Matrix4x4Float matrix4 = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                matrix4.set(i, j, j + r.nextFloat());
            }
        }

        ImageFloat3 vertext = new ImageFloat3(size, size);
        ImageFloat depth = new ImageFloat(size, size);

        for (int i = 0; i < size; i++) {
            depth.set(i, r.nextFloat());
            for (int j = 0; j < size; j++) {
                vertext.set(i, j, new Float3(1f, 2f, 3f));
            }
        }

        // @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::depth2vertex, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

    }

    @Test
    public void testPhiNode() {

        final int size = 4;
        Random r = new Random();

        Matrix4x4Float matrix4 = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                matrix4.set(i, j, j + r.nextFloat());
            }
        }

        ImageFloat3 vertext = new ImageFloat3(size, size);
        ImageFloat depth = new ImageFloat(size, size);

        for (int i = 0; i < size; i++) {
            depth.set(i, r.nextFloat());
            for (int j = 0; j < size; j++) {
                vertext.set(i, j, new Float3(1f, 2f, 3f));
            }
        }

        // @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testPhiNode, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

    }

}
