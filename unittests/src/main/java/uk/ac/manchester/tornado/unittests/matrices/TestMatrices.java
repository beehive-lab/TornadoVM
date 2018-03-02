/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Michalis Papadimitriou
 *
 */

package uk.ac.manchester.tornado.unittests.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
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
                .task("t0", TestMatrices::matrixMultiplication, matrixA, matrixB, matrixC, N)
                .streamOut(matrixC);
        //@formatter:on
        t.warmup();
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
