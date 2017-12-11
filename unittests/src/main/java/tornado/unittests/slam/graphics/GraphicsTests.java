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
import static tornado.collections.types.Float3.length;
import static tornado.collections.types.Float3.normalise;

import java.util.Random;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.collections.graphics.GraphicsMath;
import tornado.collections.types.Float3;
import tornado.collections.types.Float4;
import tornado.collections.types.ImageFloat;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.Short2;
import tornado.collections.types.VectorFloat3;
import tornado.collections.types.VolumeShort2;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TornadoTestBase;

public class GraphicsTests extends TornadoTestBase {

    private static void testPhiNode(ImageFloat3 verticies, ImageFloat depths, Matrix4x4Float invK) {
        final float depth = depths.get(0, 0);
        final Float3 pix = new Float3(0, 0, 1f);
        final Float3 vertex = (depth > 0) ? Float3.mult(rotate(invK, pix), depth) : new Float3(0f, 0f, 0f);
        verticies.set(0, 0, vertex);
    }

    private static void testPhiNode2(ImageFloat3 verticies, ImageFloat depths, Matrix4x4Float invK) {
        final float depth = depths.get(0, 0);
        final Float3 pix = new Float3(0, 0, 1f);
        final Float3 vertex = (depth > 0) ? Float3.mult(rotate(invK, pix), depth) : new Float3();
        verticies.set(0, 0, vertex);
    }

    private static final Float3 rotate(Matrix4x4Float m, Float3 v) {
        final Float3 result = new Float3(Float3.dot(m.row(0).asFloat3(), v), Float3.dot(m.row(1).asFloat3(), v), Float3.dot(m.row(2).asFloat3(), v));
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

        ImageFloat3 sequential = new ImageFloat3(size, size);

        for (int i = 0; i < size; i++) {
            depth.set(i, r.nextFloat());
            for (int j = 0; j < size; j++) {
                vertext.set(i, j, new Float3(1f, 2f, 3f));
            }
        }

        // Sequential execution
        GraphicsMath.depth2vertex(sequential, depth, matrix4);

        // @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsMath::depth2vertex, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

        for (int i = 0; i < size; i++) {
            Float3 o = vertext.get(i);
            Float3 s = sequential.get(i);
            assertEquals(s.getS0(), o.getS0(), 0.001);
            assertEquals(s.getS1(), o.getS1(), 0.001);
            assertEquals(s.getS1(), o.getS1(), 0.001);
        }

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

    @Test
    public void testPhiNode2() {

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
            .task("s0", GraphicsTests::testPhiNode2, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

    }

    public static void computeRigidTransform(Matrix4x4Float matrix, VectorFloat3 points, VectorFloat3 output) {
        for (@Parallel int i = 0; i < points.getLength(); i++) {
            Float3 p = GraphicsMath.rigidTransform(matrix, points.get(i));
            output.set(i, p);
        }
    }

    @Test
    public void testRigidTrasform() {

        final int size = 4;
        Random r = new Random();

        Matrix4x4Float matrix4 = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                matrix4.set(i, j, j + r.nextFloat());
            }
        }

        VectorFloat3 point = new VectorFloat3(size);
        for (int i = 0; i < 4; i++) {
            point.set(i, new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat()));
        }

        VectorFloat3 sequential = new VectorFloat3(size);
        VectorFloat3 output = new VectorFloat3(size);

        // Sequential execution
        computeRigidTransform(matrix4, point, sequential);

        // @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::computeRigidTransform, matrix4, point, output)
            .streamOut(output)
            .execute();        
        // @formatter:on

        for (int i = 0; i < size; i++) {
            Float3 o = output.get(i);
            Float3 s = sequential.get(i);
            assertEquals(s.getS0(), o.getS0(), 0.001);
            assertEquals(s.getS1(), o.getS1(), 0.001);
            assertEquals(s.getS1(), o.getS1(), 0.001);
        }
    }

    // Ray Cast testing

    private static final float INVALID = -2;

    public static final void raycast(ImageFloat3 verticies, ImageFloat3 normals, VolumeShort2 volume, Float3 volumeDims, Matrix4x4Float view, float nearPlane, float farPlane, float largeStep,
            float smallStep) {

        for (@Parallel int y = 0; y < verticies.Y(); y++) {
            for (@Parallel int x = 0; x < verticies.X(); x++) {
                final Float4 hit = GraphicsMath.raycastPoint(volume, volumeDims, x, y, view, nearPlane, farPlane, smallStep, largeStep);

                final Float3 normal;
                final Float3 position;
                if (hit.getW() > 0f) {
                    position = hit.asFloat3();

                    // final Float3 surfNorm = VolumeOps.grad(volume,
                    // volumeDims, position);

                    final Float3 surfNorm = new Float3(0f, 0f, 0f);

                    if (length(surfNorm) != 0) {
                        normal = normalise(surfNorm);
                    } else {
                        normal = new Float3(INVALID, 0f, 0f);
                    }
                } else {
                    normal = new Float3(INVALID, 0f, 0f);
                    position = new Float3();
                }

                verticies.set(x, y, position);
                normals.set(x, y, normal);
            }
        }
    }

    @Test
    public void raycastTest() {

        final int size = 4;
        Random r = new Random();

        Matrix4x4Float view = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                view.set(i, j, j + r.nextFloat());
            }
        }

        ImageFloat3 verticies = new ImageFloat3(size, size);
        ImageFloat3 normals = new ImageFloat3(size, size);
        VolumeShort2 volume = new VolumeShort2(size, size, size);
        Float3 volumeDims = new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat());

        float nearPlane = r.nextFloat();
        float farPlane = r.nextFloat();
        float largeStep = r.nextFloat();
        float smallStep = r.nextFloat();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                verticies.set(i, j, new Float3(1f, 2f, 3f));
                normals.set(i, j, new Float3(1f, 2f, 3f));
            }
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    volume.set(i, j, k, new Short2((short) 1, (short) 2));
                }
            }
        }

        // @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::raycast, verticies, normals, volume, volumeDims, view, nearPlane, farPlane, largeStep, smallStep)
            .streamOut(verticies, normals)
            .execute();        
        // @formatter:on

    }

}
