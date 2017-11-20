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
 */

package tornado.unittests.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TornadoTestBase;

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
            for (int j = 0; j < size; j++) {
                result[i] += matrix[i * size + j] * vector[j];
            }
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
        final int N = 32;
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
        t.warmup();
        t.execute();

        matrixVector(matrix, vector, resultSeq, N);

        for (int i = 0; i < vector.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.001);
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
                .task("t0", TestMatrices::matrixVector, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.warmup();
        t.execute();

        matrixMultiplication(matrixA, matrixB, resultSeq, N);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixC[i * N + j], 0.1);
            }
        }
    }
}
