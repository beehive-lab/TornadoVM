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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/** Loop-parallel kernels in the {@code @Parallel} style. */
public final class ParallelKernels {

    private ParallelKernels() {
    }

    public static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void matrixAdd(FloatArray a, FloatArray b, FloatArray c, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                c.set(i * cols + j, a.get(i * cols + j) + b.get(i * cols + j));
            }
        }
    }

    public static void saxpyWithSequentialInner(FloatArray x, FloatArray y, float alpha, int n, int repeat) {
        for (@Parallel int i = 0; i < n; i++) {
            float acc = y.get(i);
            for (int r = 0; r < repeat; r++) {
                acc += alpha * x.get(i);
            }
            y.set(i, acc);
        }
    }

    /** Escape-time loop with a float test and a float select (the juliaset benchmark's shape). */
    public static void escapeTime(int size, FloatArray hue, FloatArray brightness) {
        for (@Parallel int ix = 0; ix < size; ix++) {
            for (@Parallel int jx = 0; jx < size; jx++) {
                float zx = 1.5f * (ix - size / 2) / (0.5f * size);
                float zy = (jx - size / 2) / (0.5f * size);
                float k = 100;
                while (zx * zx + zy * zy < 4 && k > 0) {
                    float tmp = zx * zx - zy * zy - 0.7f;
                    zy = 2.0f * zx * zy + 0.27f;
                    zx = tmp;
                    k--;
                }
                hue.set(ix * size + jx, 100 / k);
                brightness.set(ix * size + jx, k > 0 ? 1 : 0);
            }
        }
    }

    /** A vector type as a value: a parameter, a static helper, a setter, lane reads. */
    public static void vectorValue(uk.ac.manchester.tornado.api.types.vectors.Float4 v, FloatArray out) {
        var w = uk.ac.manchester.tornado.api.types.vectors.Float4.mult(v, 2f);
        w.setY(0f);
        out.set(0, w.getX() + w.getY() + w.getW());
    }
}
