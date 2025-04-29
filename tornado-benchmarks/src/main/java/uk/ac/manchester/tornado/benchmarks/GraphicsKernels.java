/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.benchmarks;

import static uk.ac.manchester.tornado.api.types.vectors.Float4.add;

import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;

public final class GraphicsKernels {
    // CHECKSTYLE:OFF

    // Parameters for the algorithm used
    private static final int MAX_ITERATIONS = 1000;
    private static final float ZOOM = 1;
    private static final float CX = -0.7f;
    private static final float CY = 0.27015f;
    private static final float MOVE_X = 0;
    private static final float MOVE_Y = 0;

    public static void rotateVector(VectorFloat3 output, Matrix4x4Float m, VectorFloat3 input) {
        for (@Parallel int i = 0; i < output.getLength(); i++) {
            final Float3 x = input.get(i);
            final Float3 y = TornadoMath.rotate(m, x);
            output.set(i, y);
        }
    }

    public static void dotVector(VectorFloat3 A, VectorFloat3 B, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            final Float3 a = A.get(i);
            final Float3 b = B.get(i);
            c.set(i, Float3.dot(a, b));
        }
    }

    public static void rotateImage(ImageFloat3 output, Matrix4x4Float m, ImageFloat3 input) {
        for (@Parallel int i = 0; i < output.Y(); i++) {
            for (@Parallel int j = 0; j < output.X(); j++) {
                final Float3 x = input.get(j, i);
                final Float3 y = TornadoMath.rotate(m, x);
                output.set(j, i, y);
            }
        }
    }

    public static void rotateImageStreams(ImageFloat3 output, Matrix4x4Float m, ImageFloat3 input) {
        IntStream.range(0, output.X() * output.Y()).parallel().forEach((int index) -> {
            final int j = index % output.X();
            final int i = index / output.X();
            final Float3 x = input.get(j, i);
            final Float3 y = TornadoMath.rotate(m, x);
            output.set(j, i, y);
        });
    }

    public static void dotImage(ImageFloat3 A, ImageFloat3 B, ImageFloat C) {
        for (@Parallel int i = 0; i < C.Y(); i++) {
            for (@Parallel int j = 0; j < C.X(); j++) {
                final Float3 a = A.get(j, i);
                final Float3 b = B.get(j, i);
                C.set(j, i, Float3.dot(a, b));
            }
        }
    }

    public static void addImage(ImageFloat4 a, ImageFloat4 b, ImageFloat4 c) {
        for (@Parallel int i = 0; i < c.Y(); i++) {
            for (@Parallel int j = 0; j < c.X(); j++) {
                c.set(j, i, add(a.get(j, i), b.get(j, i)));
            }
        }
    }

    public static void convolveImageArray(final FloatArray input, final FloatArray filter, final FloatArray output, final int iW, final int iH, final int fW, final int fH) {
        int u;
        int v;
        final int filterX2 = fW / 2;
        final int filterY2 = fH / 2;
        for (@Parallel int y = 0; y < iH; y++) {
            for (@Parallel int x = 0; x < iW; x++) {
                float sum = 0.0f;
                for (v = 0; v < fH; v++) {
                    for (u = 0; u < fW; u++) {
                        if ((((y - filterY2) + v) >= 0) && ((y + v) < iH)) {
                            if ((((x - filterX2) + u) >= 0) && ((x + u) < iW)) {
                                sum += filter.get((v * fW) + u) * input.get((((y - filterY2) + v) * iW) + ((x - filterX2) + u));
                            }
                        }
                    }
                }
                output.set((y * iW) + x, sum);
            }
        }
    }

    public static void convolveImage(final ImageFloat input, final ImageFloat filter, final ImageFloat output) {
        int u;
        int v;
        final int filterX2 = filter.X() / 2;
        final int filterY2 = filter.Y() / 2;
        for (@Parallel int y = 0; y < output.Y(); y++) {
            for (@Parallel int x = 0; x < output.X(); x++) {
                float sum = 0.0f;
                for (v = 0; v < filter.Y(); v++) {
                    for (u = 0; u < filter.X(); u++) {
                        if ((((y - filterY2) + v) >= 0) && ((y + v) < output.Y())) {
                            if ((((x - filterX2) + u) >= 0) && ((x + u) < output.X())) {
                                sum += filter.get(u, v) * input.get(x - filterX2 + u, y - filterY2 + v);
                            }
                        }
                    }
                }
                output.set(x, y, sum);
            }
        }
    }

    public static void convolveImageStreams(final ImageFloat input, final ImageFloat filter, final ImageFloat output) {
        final int filterX2 = filter.X() / 2;
        final int filterY2 = filter.Y() / 2;
        IntStream.range(0, output.X() * output.Y()).parallel().forEach((int index) -> {
            final int x = index % output.X();
            final int y = index / output.X();
            float sum = 0.0f;
            for (int v = 0; v < filter.Y(); v++) {
                for (int u = 0; u < filter.X(); u++) {
                    if ((((y - filterY2) + v) >= 0) && ((y + v) < output.Y())) {
                        if ((((x - filterX2) + u) >= 0) && ((x + u) < output.X())) {
                            sum += filter.get(u, v) * input.get(x - filterX2 + u, y - filterY2 + v);
                        }
                    }
                }
            }
            output.set(x, y, sum);
        });
    }

    public static void juliaSetTornado(int size, FloatArray hue, FloatArray brightness) {
        for (@Parallel int ix = 0; ix < size; ix++) {
            for (@Parallel int jx = 0; jx < size; jx++) {
                float zx = 1.5f * (ix - size / 2) / (0.5f * ZOOM * size) + MOVE_X;
                float zy = (jx - size / 2) / (0.5f * ZOOM * size) + MOVE_Y;
                float k = MAX_ITERATIONS;
                while (zx * zx + zy * zy < 4 && k > 0) {
                    float tmp = zx * zx - zy * zy + CX;
                    zy = 2.0f * zx * zy + CY;
                    zx = tmp;
                    k--;
                }
                hue.set(ix * size + jx, (MAX_ITERATIONS / k));
                brightness.set(ix * size + jx, k > 0 ? 1 : 0);
            }
        }
    }
    // CHECKSTYLE:ON
}
