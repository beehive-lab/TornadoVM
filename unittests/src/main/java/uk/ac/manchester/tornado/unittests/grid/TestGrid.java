/*
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.grid;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DInt;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.matrices.TestMatrixTypes;

/**
 * How to run?
 * 
 * <code>
 * tornado-test.py -V --debug uk.ac.manchester.tornado.unittests.grid.TestGrid 
 * </code>
 * 
 */
public class TestGrid extends TornadoTestBase {

    final int NUM_ELEMENTS = 4096;

    private static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
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
    public void testDynamicGrid01() {
        float[] a = new float[NUM_ELEMENTS];
        float[] b = new float[NUM_ELEMENTS];
        float[] c = new float[NUM_ELEMENTS];

        IntStream.range(0, NUM_ELEMENTS).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .streamOut(c); //

        // Set the Grid with 4096 threads
        WorkerGrid1D worker = new WorkerGrid1D(4096);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        ts.execute(gridScheduler);

        // Change the Grid
        worker.setGlobalWork(512, 1, 1);
        ts.execute(gridScheduler);

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.01f);
        }
    }

    @Test
    public void testDynamicGrid02() {
        final int numElements = 256;
        float[] a = new float[numElements * numElements];
        float[] b = new float[numElements * numElements];
        float[] c = new float[numElements * numElements];
        float[] seq = new float[numElements * numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t1", TestGrid::matrixMultiplication, a, b, c, numElements) //
                .streamOut(c); //

        WorkerGrid2D worker = new WorkerGrid2D(numElements, numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t1", worker);
        ts.execute(gridScheduler);

        worker.setLocalWork(32, 32, 1);
        ts.execute(gridScheduler);

        matrixMultiplication(a, b, seq, numElements);

        for (int i = 0; i < numElements; i++) {
            for (int j = 0; j < numElements; j++) {
                assertEquals(seq[i * numElements + j], c[i * numElements + j], 0.1f);
            }

        }
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
        TaskSchedule ts = new TaskSchedule("foo") //
                .task("bar", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y) //
                .streamOut(matrixB);

        WorkerGrid2D worker = new WorkerGrid2D(X, Y);
        GridScheduler gridScheduler = new GridScheduler("foo.bar", worker);
        ts.execute(gridScheduler);

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j));
            }
        }
    }

    @Test
    public void testDynamicGrid03() {
        testMatrixIntegers(256, 128);
    }

    /**
     * Test with multiple tasks within a task-scheduler sharing the same worker
     * grid.
     */
    @Test
    public void testDynamicGrid04() {
        float[] a = new float[NUM_ELEMENTS];
        float[] b = new float[NUM_ELEMENTS];
        float[] c = new float[NUM_ELEMENTS];

        IntStream.range(0, NUM_ELEMENTS).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .task("t1", TestArrays::vectorAddFloat, a, b, c) //
                .streamOut(c); //

        // Set the Grid with 4096 threads
        WorkerGrid1D worker = new WorkerGrid1D(4096);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        gridScheduler.setWorkerGrid("s0.t1", worker); // share the same worker
        ts.execute(gridScheduler);

        // Change the Grid
        worker.setGlobalWork(512, 1, 1);
        ts.execute(gridScheduler);

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.01f);
        }
    }

    @Test
    public void testOutOfRangeDimensions() {
        int N = 512;

        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixC = new float[N * N];

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = 2.5f;
            matrixB[idx] = 3.5f;
        });

        TaskSchedule s0 = new TaskSchedule("s0") //
                .task("mxm", TestGrid::matrixMultiplication, matrixA, matrixB, matrixC, N) //
                .streamOut(matrixC);

        WorkerGrid2D worker = new WorkerGrid2D(N, N);
        GridScheduler gridScheduler = new GridScheduler("s0.mxm", worker);
        worker.setGlobalWork(N, N, 1);
        worker.setLocalWork(256, 256, 1);

        s0.execute(gridScheduler);
    }
}
