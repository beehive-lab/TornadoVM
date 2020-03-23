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

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMatrices extends TornadoTestBase {

    public static void fillMatrix(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            Arrays.fill(values[i], i);
        }
    }

    public static void fillMatrix2(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                values[i][j] = i;
            }
        }
    }

    public static void fillMatrix3(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            for (@Parallel int j = 0; j < values.length; j++) {
                values[i][j] = i;
            }
        }
    }

    public static void matrixVector(float[] matrix, float[] vector, float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float sum = 0.0f;
            for (int j = 0; j < size; j++) {
                sum += matrix[i * size + j] * vector[j];
            }
            result[i] = sum;
        }
    }

    public static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    @Ignore
    @Test
    public void testFillMatrix() {
        final int numElements = 16;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::fillMatrix, a)
                .streamOut(new Object[]{a});
	    //@formatter:on
        t.warmup();
        t.execute();

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(i, a[i][j]);
            }
        }
    }

    @Ignore
    @Test
    public void testFillMatrix2() {
        final int numElements = 16;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
		        .task("t0", TestMatrices::fillMatrix2, a)
		        .streamOut(new Object[]{a});
	    //@formatter:on

        t.warmup();
        t.execute();

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(i, a[i][j]);
            }
        }
    }

    @Ignore
    @Test
    public void testFillMatrix3() {
        final int numElements = 16;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
		        .task("t0", TestMatrices::fillMatrix3, a)
		        .streamOut(new Object[]{a});
		//@formatter:on

        t.warmup();
        t.execute();

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(i, a[i][j]);
            }
        }
    }

    @Test
    public void testMatrixVector() {
        final int N = 4;
        float[] matrix = new float[N * N];
        float[] vector = new float[N];
        float[] result = new float[N];
        float[] resultSeq = new float[N];

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(idx -> {
            vector[idx] = r.nextFloat();
        });
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrix[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixVector, matrix, vector, result, N)
                .streamOut(result);
        //@formatter:on
        t.execute();

        matrixVector(matrix, vector, resultSeq, N);

        for (int i = 0; i < vector.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.01f);
        }
    }

    public static void copyMatrix2D(final float[][] matrixA, final float[][] matrixB) {
        for (@Parallel int i = 0; i < matrixA.length; i++) {
            for (@Parallel int j = 0; j < matrixA[i].length; j++) {
                matrixB[i][j] = matrixA[i][j];
            }
        }
    }

    @Test
    @Ignore
    public void testCopyMatrix2D() {
        final int N = 32;
        float[][] matrixA = new float[N][N];
        float[][] matrixB = new float[N][N];

        Random random = new Random();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matrixA[i][j] = random.nextFloat();
            }
        }

        TaskSchedule ts = new TaskSchedule("s0").task("s0", TestMatrices::copyMatrix2D, matrixA, matrixB).streamOut(matrixB);
        ts.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(matrixA[i][j], matrixB[i][j], 0.01);
            }
        }
    }

    @Test
    public void testMatrixMultiplication() {
        final int N = 64;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];
        float[] resultSeq = new float[N * N];

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", TestMatrices::matrixMultiplication, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < N; k++) {
                    sum += matrixA[(i * N) + k] * matrixB[(k * N) + j];
                }
                resultSeq[(i * N) + j] = sum;
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixC[i * N + j], 0.1);
            }
        }
    }

}
