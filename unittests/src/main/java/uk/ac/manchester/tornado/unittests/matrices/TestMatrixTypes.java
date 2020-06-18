/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.collections.types.Matrix2DDouble;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DInt;
import uk.ac.manchester.tornado.api.collections.types.Matrix3DFloat;
import uk.ac.manchester.tornado.api.collections.types.Matrix3DFloat4;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMatrixTypes extends TornadoTestBase {

    public static void computeMatrixSum(Matrix2DFloat a, Matrix2DFloat b, final int N) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                b.set(i, j, a.get(i, j) + a.get(i, j));
            }
        }
    }

    public static void computeMatrixSum(Matrix3DFloat a, Matrix3DFloat b, final int N) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                for (@Parallel int k = 0; k < N; k++) {
                    b.set(i, j, k, a.get(i, j, k) + a.get(i, j, k));
                }
            }
        }
    }

    public static void computeMatrixSum(Matrix3DFloat a, Matrix3DFloat b, final int X, final int Y, final int Z) {
        for (@Parallel int i = 0; i < X; i++) {
            for (@Parallel int j = 0; j < Y; j++) {
                for (@Parallel int k = 0; k < Z; k++) {
                    b.set(i, j, k, a.get(i, j, k) + a.get(i, j, k));
                }
            }
        }
    }

    public static void computeMatrixSum(Matrix2DFloat a, Matrix2DFloat b, final int X, final int Y) {
        for (@Parallel int i = 0; i < X; i++) {
            for (@Parallel int j = 0; j < Y; j++) {
                b.set(i, j, a.get(i, j) + a.get(i, j));
            }
        }
    }

    public static void computeMatrixSum(Matrix2DInt a, Matrix2DInt b, final int X, final int Y) {
        for (@Parallel int i = 0; i < X; i++) {
            for (@Parallel int j = 0; j < Y; j++) {
                b.set(i, j, a.get(i, j) + a.get(i, j));
            }
        }
    }

    public static void computeMatrixSum(Matrix2DDouble a, Matrix2DDouble b, final int X, final int Y) {
        for (@Parallel int i = 0; i < X; i++) {
            for (@Parallel int j = 0; j < Y; j++) {
                b.set(i, j, a.get(i, j) + a.get(i, j));
            }
        }
    }

    public static void computeMatrixMultiplication(Matrix2DFloat a, Matrix2DFloat b, Matrix2DFloat c) {
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
    public static void computeMatrixSum(Matrix2DFloat4 a, Matrix2DFloat4 b, final int X, final int Y) {
        for (@Parallel int i = 0; i < X; i++) {
            for (@Parallel int j = 0; j < Y; j++) {
                b.set(i, j, Float4.add(a.get(i, j), a.get(i, j)));
            }
        }
    }

    public static void computeMatrixSum(Matrix3DFloat4 a, Matrix3DFloat4 b, final int X, final int Y, final int Z) {
        for (@Parallel int i = 0; i < X; i++) {
            for (@Parallel int j = 0; j < Y; j++) {
                for (@Parallel int k = 0; k < Z; k++) {
                    b.set(i, j, k, Float4.add(a.get(i, j, k), a.get(i, j, k)));
                }
            }
        }
    }

    @Test
    public void testMatrix01() {
        final int N = 256;
        Matrix2DFloat matrixA = new Matrix2DFloat(N, N);
        Matrix2DFloat matrixB = new Matrix2DFloat(N, N);
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matrixA.set(i, j, r.nextFloat());
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, N);
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
        final int N = 256;
        float[][] a = new float[N][N];
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                a[i][j] = r.nextFloat();
            }
        }
        Matrix2DFloat matrixA = new Matrix2DFloat(a);
        Matrix2DFloat matrixB = new Matrix2DFloat(N, N);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, N);
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
        final int N = 256;
        Matrix2DFloat matrixA = new Matrix2DFloat(N, N);
        Matrix2DFloat matrixB = new Matrix2DFloat(N, N);
        Matrix2DFloat matrixC = new Matrix2DFloat(N, N);
        Matrix2DFloat sequential = new Matrix2DFloat(N, N);
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
        final int N = 256;
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
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, N);
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

    public static void testMatrix2DVectorType(final int X, final int Y) {
        Matrix2DFloat4 matrixA = new Matrix2DFloat4(X, Y);
        Matrix2DFloat4 matrixB = new Matrix2DFloat4(X, Y);
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                Float4 vector = new Float4();
                for (int k = 0; k < vector.size(); k++) {
                    vector.set(k, r.nextFloat());
                }
                matrixA.set(i, j, vector);
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
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
     * This test checks the {@linkplain Matrix2DFloat4} type. Each position in a 2D
     * matrix is an explicit Vector4 in OpenCL.
     */
    @Test
    public void testMatrix05() {
        final int X = 512;
        testMatrix2DVectorType(X, X);
    }

    @Test
    public void testMatrix06() {
        final int X = 512;
        final int Y = 128;
        testMatrix2DVectorType(X, Y);
    }

    @Test
    public void testMatrix07() {
        final int X = 512;
        final int Y = 128;
        testMatrix2DVectorType(Y, X);
    }

    public static void testMatrix3DVectorType(final int X, final int Y, final int Z) {
        Matrix3DFloat4 matrixA = new Matrix3DFloat4(X, Y, Z);
        Matrix3DFloat4 matrixB = new Matrix3DFloat4(X, Y, Z);
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                for (int k = 0; k < Z; k++) {
                    Float4 vector = new Float4();
                    for (int v = 0; v < vector.size(); v++) {
                        vector.set(v, r.nextFloat());
                    }
                    matrixA.set(i, j, k, vector);
                }
            }
        }

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y, Z);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                for (int k = 0; k < Z; k++) {
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

    @Test
    public void testMatrix08() {
        final int X = 128;
        testMatrix3DVectorType(X, X, X);
    }

    @Test
    public void testMatrix09() {
        final int X = 128;
        final int Y = 64;
        final int Z = 2;
        testMatrix3DVectorType(X, Y, Z);
    }

    @Test
    public void testMatrix10() {
        final int X = 128;
        final int Y = 64;
        final int Z = 2;
        testMatrix3DVectorType(Y, X, Z);
    }

    /**
     * This test checks the {@linkplain Matrix3DFloat4} type. Each position in a 3D
     * matrix is an explicit Vector4 in OpenCL.
     */
    @Test
    public void testMatrix11() {
        final int SMALL_SIZE = 128;
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
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, SMALL_SIZE, SMALL_SIZE, SMALL_SIZE);
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

    @Test
    public void testMatrix12() {
        final int X = 480;
        final int Y = 854;
        final int Z = 3;

        float[][][] a = new float[X][Y][Z];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                for (int k = 0; k < Z; k++) {
                    a[i][j][k] = r.nextFloat();
                }
            }
        }
        Matrix3DFloat matrixA = new Matrix3DFloat(a);
        Matrix3DFloat matrixB = new Matrix3DFloat(X, Y, Z);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y, Z);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                for (int k = 0; k < Z; k++) {
                    assertEquals(matrixA.get(i, j, k) + matrixA.get(i, j, k), matrixB.get(i, j, k), 0.01f);
                }
            }
        }
    }

    private static void testMatricesFloats(final int X, final int Y) {
        float[][] a = new float[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextFloat();
            }
        }
        Matrix2DFloat matrixA = new Matrix2DFloat(a);
        Matrix2DFloat matrixB = new Matrix2DFloat(X, Y);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j), 0.01f);
            }
        }
    }

    @Test
    public void testMatrix13() {
        final int X = 854;
        final int Y = 480;
        testMatricesFloats(X, Y);
    }

    @Test
    public void testMatrix14() {
        final int X = 854;
        final int Y = 480;
        testMatricesFloats(Y, X);
    }

    @Test
    public void testMatrix15() {
        final int X = 854;
        testMatricesFloats(X, X);
    }

    private static void testMatrixIntegers(final int X, final int Y) {
        int[][] a = new int[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextInt();
            }
        }
        Matrix2DInt matrixA = new Matrix2DInt(a);
        Matrix2DInt matrixB = new Matrix2DInt(X, Y);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j));
            }
        }
    }

    @Test
    public void testMatrix16() {
        testMatrixIntegers(640, 480);
    }

    @Test
    public void testMatrix17() {
        testMatrixIntegers(480, 640);
    }

    @Test
    public void testMatrix18() {
        testMatrixIntegers(640, 640);
    }

    private static void testMatrixDoubles(final int X, final int Y) {
        double[][] a = new double[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextInt();
            }
        }
        Matrix2DDouble matrixA = new Matrix2DDouble(a);
        Matrix2DDouble matrixB = new Matrix2DDouble(X, Y);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j), 0.01);
            }
        }
    }

    @Test
    public void testMatrix19() {
        testMatrixDoubles(640, 480);
    }

    @Test
    public void testMatrix20() {
        testMatrixDoubles(480, 640);
    }

    @Test
    public void testMatrix21() {
        testMatrixDoubles(640, 640);
    }

    @Test
    public void testMatrix22() {
        final int Y = 2160;
        final int X = 3840;

        float[][] a = new float[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextFloat();
            }
        }
        Matrix2DFloat matrixA = new Matrix2DFloat(a);
        Matrix2DFloat matrixB = new Matrix2DFloat(X, Y);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j), 0.01f);
            }
        }
    }

    @Test
    public void testMatrix23() {
        final int X = 2160;
        final int Y = 3840;

        float[][] a = new float[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextFloat();
            }
        }
        Matrix2DFloat matrixA = new Matrix2DFloat(a);
        Matrix2DFloat matrixB = new Matrix2DFloat(X, Y);
        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("t0", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y);
        ts.streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j), 0.01f);
            }
        }
    }

}
