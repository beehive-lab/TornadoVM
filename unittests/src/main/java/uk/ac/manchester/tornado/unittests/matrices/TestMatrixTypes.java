/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.unittests.matrices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.collections.types.Matrix3DFloat;
import uk.ac.manchester.tornado.api.collections.types.Matrix3DFloat4;
import uk.ac.manchester.tornado.api.collections.types.MatrixFloat;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMatrixTypes extends TornadoTestBase {

    private static final int N = 256;

    private static final int SMALL_SIZE = 128;

    public static void computeMatrixSum(MatrixFloat a, MatrixFloat b) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                b.set(i, j, a.get(i, j) + a.get(i, j));
            }
        }
    }

    public static void computeMatrixSum(Matrix3DFloat a, Matrix3DFloat b) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                for (@Parallel int k = 0; k < N; k++) {
                    b.set(i, j, k, a.get(i, j, j) + a.get(i, j, k));
                }
            }
        }
    }

    public static void computeMatrixMultiplication(MatrixFloat a, MatrixFloat b, MatrixFloat c) {
        for (@Parallel int i = 0; i < a.M(); i++) {
            for (@Parallel int j = 0; j < a.N(); j++) {
                float sum = 0.0f;
                for (int k = 0; k < a.N(); k++) {
                    sum += a.get(i, k) + a.get(k, j);
                }
                c.set(i, j, sum);
            }
        }
    }

    /**
     * Computing with Matrix and vector types.
     * 
     * @param a
     * @param b
     */
    public static void computeMatrixSum(Matrix2DFloat4 a, Matrix2DFloat4 b) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                b.set(i, j, Float4.add(a.get(i, j), a.get(i, j)));
            }
        }
    }

    public static void computeMatrixSum(Matrix3DFloat4 a, Matrix3DFloat4 b, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                for (@Parallel int k = 0; k < size; k++) {
                    b.set(i, j, k, Float4.add(a.get(i, j, k), a.get(i, j, k)));
                }
            }
        }
    }

    @Test
    public void testMatrix01() {
        MatrixFloat matrixA = new MatrixFloat(N, N);
        MatrixFloat matrixB = new MatrixFloat(N, N);
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matrixA.set(i, j, r.nextFloat());
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j), 0.01f);
            }
        }
    }

    @Test
    public void testMatrix02() {
        float[][] a = new float[N][N];
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                a[i][j] = r.nextFloat();
            }
        }
        MatrixFloat matrixA = new MatrixFloat(a);
        MatrixFloat matrixB = new MatrixFloat(N, N);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j), 0.01f);
            }
        }
    }

    @Test
    public void testMatrix03() {
        MatrixFloat matrixA = new MatrixFloat(N, N);
        MatrixFloat matrixB = new MatrixFloat(N, N);
        MatrixFloat matrixC = new MatrixFloat(N, N);
        MatrixFloat sequential = new MatrixFloat(N, N);
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matrixA.set(i, j, r.nextFloat());
                matrixB.set(i, j, r.nextFloat());
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixMultiplication, matrixA, matrixB, matrixC);
        ts.streamOut(matrixC);
        ts.execute();

        computeMatrixMultiplication(matrixA, matrixB, sequential);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(sequential.get(i, j), matrixC.get(i, j), 0.01f);
            }
        }
    }

    @Test
    public void testMatrix04() {
        Matrix3DFloat matrixA = new Matrix3DFloat(N, N, N);
        Matrix3DFloat matrixB = new Matrix3DFloat(N, N, N);
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                for (int k = 0; k < N; k++) {
                    matrixA.set(i, j, k, r.nextFloat());
                }
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                for (int k = 0; k < N; k++) {
                    assertEquals(matrixA.get(i, j, k) + matrixA.get(i, j, k), matrixB.get(i, j, k), 0.01f);
                }
            }
        }
    }

    /**
     * This test checks the {@linkplain Matrix2DFloat4} type. Each position in a
     * 2D matrix is an explicit Vector4 in OpenCL.
     */
    @Test
    public void testMatrix05() {
        Matrix2DFloat4 matrixA = new Matrix2DFloat4(N, N);
        Matrix2DFloat4 matrixB = new Matrix2DFloat4(N, N);
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                Float4 vector = new Float4();
                for (int k = 0; k < vector.size(); k++) {
                    vector.set(k, r.nextFloat());
                }
                matrixA.set(i, j, vector);
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                Float4 expected = Float4.add(matrixA.get(i, j), matrixA.get(i, j));
                if (Float4.isEqual(expected, matrixB.get(i, j))) {
                    assertTrue(true);
                } else {
                    assertTrue(false);
                }
            }
        }
    }

    /**
     * This test checks the {@linkplain Matrix3DFloat4} type. Each position in a
     * 3D matrix is an explicit Vector4 in OpenCL.
     */
    @Test
    public void testMatrix06() {
        Matrix3DFloat4 matrixA = new Matrix3DFloat4(SMALL_SIZE, SMALL_SIZE, SMALL_SIZE);
        Matrix3DFloat4 matrixB = new Matrix3DFloat4(SMALL_SIZE, SMALL_SIZE, SMALL_SIZE);
        Random r = new Random();
        for (int i = 0; i < SMALL_SIZE; i++) {
            for (int j = 0; j < SMALL_SIZE; j++) {
                for (int k = 0; k < SMALL_SIZE; k++) {
                    Float4 vector = new Float4();
                    for (int v = 0; v < vector.size(); v++) {
                        vector.set(v, r.nextFloat());
                    }
                    matrixA.set(i, j, k, vector);
                }
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, SMALL_SIZE);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < SMALL_SIZE; i++) {
            for (int j = 0; j < SMALL_SIZE; j++) {
                for (int k = 0; k < SMALL_SIZE; k++) {
                    Float4 expected = Float4.add(matrixA.get(i, j, k), matrixA.get(i, j, k));
                    if (!Float4.isEqual(expected, matrixB.get(i, j, k))) {
                        assertTrue(false);
                    } else {
                        assertTrue(true);
                    }
                }
            }
        }
    }

}
