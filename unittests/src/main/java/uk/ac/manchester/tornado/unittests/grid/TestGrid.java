/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DInt;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.matrices.TestMatrixTypes;

public class TestGrid extends TornadoTestBase {

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
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .streamOut(c); //

        // Set the Grid with 4096 threads
        WorkerGrid1D worker = new WorkerGrid1D(4096);
        GridTask gridTask = new GridTask();
        gridTask.set("s0.t0", worker);
        ts.execute(gridTask);

        // Change the Grid
        worker.setGlobalWork(512, 1, 1);
        ts.execute(gridTask);

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
        GridTask gridTask = new GridTask();
        gridTask.set("s0.t1", worker);
        ts.execute(gridTask);

        worker.setGlobalWork(512, 512, 1);
        ts.execute(gridTask);

        matrixMultiplication(a, b, seq, numElements);

        for (int i = 0; i < numElements; i++) {
            for (int j = 0; j < numElements; j++) {
                assertEquals(seq[i * numElements + j], c[i * numElements + j], 0.01f);
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
        GridTask gridTask = new GridTask();
        gridTask.set("foo.bar", worker);
        ts.execute(gridTask);

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
}
