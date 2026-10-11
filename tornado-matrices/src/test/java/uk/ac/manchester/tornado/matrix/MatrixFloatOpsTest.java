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
package uk.ac.manchester.tornado.matrix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;

public class MatrixFloatOpsTest {

    private static Matrix4x4Float matrix(float[][] values) {
        Matrix4x4Float m = new Matrix4x4Float();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m.set(i, j, values[i][j]);
            }
        }
        return m;
    }

    @Test
    public void inverseOfAPose() {
        // A rigid transform (rotation about z by 90 degrees, then a translation): its inverse is known.
        Matrix4x4Float m = matrix(new float[][] { { 0, -1, 0, 1 }, { 1, 0, 0, 2 }, { 0, 0, 1, 3 }, { 0, 0, 0, 1 } });
        MatrixFloatOps.inverse(m);
        float[][] expected = { { 0, 1, 0, -2 }, { -1, 0, 0, 1 }, { 0, 0, 1, -3 }, { 0, 0, 0, 1 } };
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals("(" + i + "," + j + ")", expected[i][j], m.get(i, j), 1e-6f);
            }
        }
    }

    @Test
    public void inverseTimesMatrixIsIdentity() {
        float[][] values = { { 4, 7, 2, 3 }, { 0, 5, 1, 9 }, { 6, 2, 8, 1 }, { 3, 3, 3, 4 } };
        Matrix4x4Float inv = matrix(values);
        MatrixFloatOps.inverse(inv);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += values[i][k] * inv.get(k, j);
                }
                assertEquals("(" + i + "," + j + ")", i == j ? 1.0 : 0.0, sum, 1e-5);
            }
        }
    }

    @Test
    public void singularMatrixIsLeftUnchanged() {
        float[][] values = { { 1, 2, 3, 4 }, { 2, 4, 6, 8 }, { 0, 1, 0, 1 }, { 5, 0, 1, 2 } };
        Matrix4x4Float m = matrix(values);
        MatrixFloatOps.inverse(m);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(values[i][j], m.get(i, j), 0f);
            }
        }
    }
}
