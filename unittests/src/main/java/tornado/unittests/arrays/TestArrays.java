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

package tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import tornado.api.Parallel;
import tornado.runtime.TornadoDriver;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TornadoTestBase;

public class TestArrays extends TornadoTestBase {

    @Before
    public void before() {
        final TornadoDriver driver = getTornadoRuntime().getDriver(0);
        driver.getDefaultDevice().reset();
    }

    public static void addAccumulator(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
        }
    }

    public static void vectorAddDouble(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddFloat(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddInteger(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddLong(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddShort(short[] a, short[] b, short[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = (short) (a[i] + b[i]);
        }
    }

    @Test
    public void testWarmUp() {

        final int N = 128;
        int numKernels = 8;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestArrays::addAccumulator, data, 1);
        }

        s0.streamOut(data).warmup();

        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data[i], 0.0001);
        }
    }

    public static void initializeSequential(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    @Test
    public void testInitNotParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeSequential, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    public static void initializeToOneParallel(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    @Test
    public void testInitParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeToOneParallel, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    @Test
    public void testAdd() {

        final int N = 128;
        int numKernels = 8;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestArrays::addAccumulator, data, 1);
        }

        s0.streamOut(data).execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data[i], 0.0001);
        }
    }

    @Test
    public void testVectorAdditionDouble() {
        final int numElements = 4096;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        //@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddDouble, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testVectorAdditionFloat() {
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        //@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddFloat, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testVectorAdditionInteger() {
        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddInteger, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testVectorAdditionLong() {
        final int numElements = 4096;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a[i] = i;
            b[i] = i;
        });

        //@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddLong, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    @Test
    public void testVectorAdditionShort() {
        final int numElements = 4096;
        short[] a = new short[numElements];
        short[] b = new short[numElements];
        short[] c = new short[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a[i] = 10;
            b[i] = 11;
        });

        //@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddShort, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    public static void fillMatrix(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            Arrays.fill(values[i], i);
        }
    }

    @Ignore
    @Test
    public void testFillMatrix() {
        final int numElements = 16;
        int[][] a = new int[numElements][numElements];

        //@formatter:off
		TaskSchedule t = new TaskSchedule("s0")
	             .task("t0", TestArrays::fillMatrix, a)
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

    public static void fillMatrix2(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                values[i][j] = i;
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
	             .task("t0", TestArrays::fillMatrix2, a)
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

    public static void fillMatrix3(int[][] values) {
        for (@Parallel int i = 0; i < values.length; i++) {
            for (@Parallel int j = 0; j < values.length; j++) {
                values[i][j] = i;
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
	             .task("t0", TestArrays::fillMatrix3, a)
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

    public static void matrixVector(float[] matrix, float[] vector, float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                result[i] += matrix[i * size + j] * vector[j];
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
	             .task("t0", TestArrays::matrixVector, matrix, vector, result, N)
	             .streamOut(result);
	    //@formatter:on
        t.warmup();
        t.execute();

        matrixVector(matrix, vector, resultSeq, N);

        for (int i = 0; i < vector.length; i++) {
            assertEquals(resultSeq[i], result[i], 0.001);
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
	             .task("t0", TestArrays::matrixVector, matrixA, matrixB, matrixC, N)
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
