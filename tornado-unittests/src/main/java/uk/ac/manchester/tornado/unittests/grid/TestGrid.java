/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.grid;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DInt;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.matrices.TestMatrixTypes;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V --debug uk.ac.manchester.tornado.unittests.grid.TestGrid
 * </code>
 *
 */
public class TestGrid extends TornadoTestBase {
    // CHECKSTYLE:OFF

    final int NUM_ELEMENTS = 4096;

    private static void matrixMultiplication(final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get((i * size) + k) * B.get((k * size) + j);
                }
                C.set((i * size) + j, sum);
            }
        }
    }

    private static void testMatrixIntegers(final int X, final int Y) throws TornadoExecutionPlanException {
        int[][] a = new int[X][Y];
        Random r = new Random();
        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                a[i][j] = r.nextInt();
            }
        }
        Matrix2DInt matrixA = new Matrix2DInt(a);
        Matrix2DInt matrixB = new Matrix2DInt(X, Y);
        TaskGraph taskGraph = new TaskGraph("foo") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA) //
                .task("bar", TestMatrixTypes::computeMatrixSum, matrixA, matrixB, X, Y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixB);

        WorkerGrid2D worker = new WorkerGrid2D(X, Y);
        GridScheduler gridScheduler = new GridScheduler("foo.bar", worker);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                assertEquals(matrixA.get(i, j) + matrixA.get(i, j), matrixB.get(i, j));
            }
        }
    }

    @Test
    public void testDynamicGrid01() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(NUM_ELEMENTS);
        FloatArray b = new FloatArray(NUM_ELEMENTS);
        FloatArray c = new FloatArray(NUM_ELEMENTS);

        IntStream.range(0, NUM_ELEMENTS).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        // Set the Grid with 4096 threads
        WorkerGrid1D worker = new WorkerGrid1D(4096);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();

            // Change the Grid
            worker.setGlobalWork(512, 1, 1);
            executionPlan.execute();
        }

        for (int i = 0; i < 512; i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testDynamicGrid02() throws TornadoExecutionPlanException {
        final int numElements = 256;
        FloatArray a = new FloatArray(numElements * numElements);
        FloatArray b = new FloatArray(numElements * numElements);
        FloatArray c = new FloatArray(numElements * numElements);
        FloatArray seq = new FloatArray(numElements * numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t1", TestGrid::matrixMultiplication, a, b, c, numElements) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        WorkerGrid2D worker = new WorkerGrid2D(numElements, numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t1", worker);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.withGridScheduler(gridScheduler) //
                    .execute();

            worker.setLocalWork(32, 32, 1);
            executor.execute();
        }

        matrixMultiplication(a, b, seq, numElements);

        for (int i = 0; i < numElements; i++) {
            for (int j = 0; j < numElements; j++) {
                assertEquals(seq.get(i * numElements + j), c.get(i * numElements + j), 0.1f);
            }

        }
    }

    @Test
    public void testDynamicGrid03() throws TornadoExecutionPlanException {
        testMatrixIntegers(256, 128);
    }

    /**
     * Test with multiple tasks within a task-scheduler sharing the same worker
     * grid.
     */
    @Test
    public void testDynamicGrid04() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(NUM_ELEMENTS);
        FloatArray b = new FloatArray(NUM_ELEMENTS);
        FloatArray c = new FloatArray(NUM_ELEMENTS);

        IntStream.range(0, NUM_ELEMENTS).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .task("t1", TestArrays::vectorAddFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c); //

        // Set the Grid with 4096 threads
        WorkerGrid1D worker = new WorkerGrid1D(4096);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        gridScheduler.addWorkerGrid("s0.t1", worker); // share the same worker
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.withGridScheduler(gridScheduler) //
                    .execute();

            // Change the Grid
            worker.setGlobalWork(512, 1, 1);
            executor.execute();
        }

        for (int i = 0; i < 512; i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testOutOfRangeDimensions() throws TornadoExecutionPlanException {
        int N = 512;

        FloatArray matrixA = new FloatArray(N * N);
        FloatArray matrixB = new FloatArray(N * N);
        FloatArray matrixC = new FloatArray(N * N);

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA.set(idx, 2.5f);
            matrixB.set(idx, 3.5f);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("mxm", TestGrid::matrixMultiplication, matrixA, matrixB, matrixC, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        WorkerGrid2D worker = new WorkerGrid2D(N, N);
        GridScheduler gridScheduler = new GridScheduler("s0.mxm", worker);
        worker.setGlobalWork(N, N, 1);
        worker.setLocalWork(256, 256, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.withGridScheduler(gridScheduler) //
                    .execute();
        }
    }
    // CHECKSTYLE:ON
}
