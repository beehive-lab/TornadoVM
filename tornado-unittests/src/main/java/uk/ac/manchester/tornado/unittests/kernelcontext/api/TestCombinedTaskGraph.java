/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class check that TornadoVM TaskSchedule API can
 * combine multiple tasks, which can either exploit the {@link KernelContext}
 * features or adhere to the original TornadoVM annotations
 * {@link uk.ac.manchester.tornado.api.annotations.Parallel} or
 * {@link uk.ac.manchester.tornado.api.annotations.Reduce}.
 * <p>
 * The following tests implement a single TaskSchedule that has three
 * consecutive tasks: t0: Vector Addition, t1: Vector Multiplication and t2:
 * Vector Subtraction.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestCombinedTaskGraph
 * </code>
 */
public class TestCombinedTaskGraph extends TornadoTestBase {

    /**
     * Method that performs the vector addition of two arrays and stores the result
     * in a third array. This method uses the
     * {@link uk.ac.manchester.tornado.api.annotations.Parallel} annotation.
     *
     * @param a
     *     input array
     * @param b
     *     input array
     * @param c
     *     output array
     */
    public static void vectorAddV1(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    /**
     * Method that performs the vector addition of two arrays and stores the result
     * in a third array. This method uses the {@link KernelContext} thread
     * identifier.
     *
     * @param a
     *     input array
     * @param b
     *     input array
     * @param c
     *     output array
     */
    public static void vectorAddV2(KernelContext context, IntArray a, IntArray b, IntArray c) {
        c.set(context.globalIdx, a.get(context.globalIdx) + b.get(context.globalIdx));
    }

    /**
     * Method that performs the vector multiplication of two arrays and stores the
     * result in a third array. This method uses the
     * {@link uk.ac.manchester.tornado.api.annotations.Parallel} annotation.
     *
     * @param a
     *     input array
     * @param b
     *     input array
     * @param c
     *     output array
     */
    public static void vectorMulV1(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    /**
     * Method that performs the vector multiplication of two arrays and stores the
     * result in a third array. This method uses the {@link KernelContext} thread
     * identifier.
     *
     * @param a
     *     input array
     * @param b
     *     input array
     * @param c
     *     output array
     */
    public static void vectorMulV2(KernelContext context, IntArray a, IntArray b, IntArray c) {
        c.set(context.globalIdx, a.get(context.globalIdx) * b.get(context.globalIdx));
    }

    /**
     * Method that performs the vector subtraction of two arrays and stores the
     * result in a third array. This method uses the
     * {@link uk.ac.manchester.tornado.api.annotations.Parallel} annotation.
     *
     * @param a
     *     input array
     * @param b
     *     input array
     * @param c
     *     output array
     */
    public static void vectorSubV1(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
        }
    }

    /**
     * Method that performs the vector subtraction of two arrays and stores the
     * result in a third array. This method uses the {@link KernelContext} thread
     * identifier.
     *
     * @param a
     *     input array
     * @param b
     *     input array
     * @param c
     *     output array
     */
    public static void vectorSubV2(KernelContext context, IntArray a, IntArray b, IntArray c) {
        c.set(context.globalIdx, a.get(context.globalIdx) - b.get(context.globalIdx));
    }

    /**
     * In this test, all tasks use the TaskSchedule API, and only t0 uses the
     * {@link GridScheduler} and {@link WorkerGrid} to deploy a specific number of
     * threads.
     */
    @Test
    public void combinedAPI01() throws TornadoExecutionPlanException {
        final int size = 16;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray cTornado = new IntArray(size);
        IntArray cJava = new IntArray(size);

        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, i));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s01.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s01") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestCombinedTaskGraph::vectorAddV1, a, b, cTornado) //
                .task("t1", TestCombinedTaskGraph::vectorMulV1, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskGraph::vectorSubV1, cTornado, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i));
        }
    }

    /**
     * In this test, all tasks use the {@link KernelContext} within the TaskSchedule
     * API, and all tasks share the same {@link GridScheduler} and
     * {@link WorkerGrid} to deploy a specific number of threads.
     */
    @Test
    public void combinedAPI02() throws TornadoExecutionPlanException {
        final int size = 16;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray cTornado = new IntArray(size);
        IntArray cJava = new IntArray(size);

        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, i));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s02.t0", worker);
        gridScheduler.addWorkerGrid("s02.t1", worker);
        gridScheduler.addWorkerGrid("s02.t2", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s02") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestCombinedTaskGraph::vectorAddV2, context, a, b, cTornado) //
                .task("t1", TestCombinedTaskGraph::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskGraph::vectorSubV2, context, cTornado, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i));
        }
    }

    /**
     * In this test, all tasks use the {@link KernelContext} within the TaskSchedule
     * API, and tasks t1 and t2 share the same {@link GridScheduler} and
     * {@link WorkerGrid} to deploy a specific number of threads.
     */
    @Test
    public void combinedAPI03() throws TornadoExecutionPlanException {
        final int size = 16;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray cTornado = new IntArray(size);
        IntArray cJava = new IntArray(size);

        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, i));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s03.t1", worker);
        gridScheduler.addWorkerGrid("s03.t2", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s03") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestCombinedTaskGraph::vectorAddV1, a, b, cTornado) //
                .task("t1", TestCombinedTaskGraph::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskGraph::vectorSubV2, context, cTornado, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i));
        }
    }

    /**
     * In this test, t0 and t1 use the {@link KernelContext} within the TaskSchedule
     * API, and share the same {@link GridScheduler} and {@link WorkerGrid} to
     * deploy a specific number of threads. While, t2 uses the TaskSchedule API.
     */
    @Test
    public void combinedAPI04() throws TornadoExecutionPlanException {
        final int size = 16;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray cTornado = new IntArray(size);
        IntArray cJava = new IntArray(size);

        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, i));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s04.t0", worker);
        gridScheduler.addWorkerGrid("s04.t1", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s04") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestCombinedTaskGraph::vectorAddV2, context, a, b, cTornado) //
                .task("t1", TestCombinedTaskGraph::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskGraph::vectorSubV1, cTornado, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i));
        }
    }

    /**
     * In this test, t0 and t1 use the {@link KernelContext} within the TaskSchedule
     * API, and use separate {@link GridScheduler} and {@link WorkerGrid} to deploy
     * different number of threads. While, t2 uses the TaskSchedule API.
     */
    @Test
    public void combinedAPI05() throws TornadoExecutionPlanException {
        final int size = 16;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray cTornado = new IntArray(size);
        IntArray cJava = new IntArray(size);

        IntStream.range(0, a.getSize()).sequential().forEach(i -> a.set(i, i));
        IntStream.range(0, b.getSize()).sequential().forEach(i -> b.set(i, i));

        WorkerGrid workerT0 = new WorkerGrid1D(size);
        WorkerGrid workerT1 = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s05.t0", workerT0);
        gridScheduler.addWorkerGrid("s05.t1", workerT1);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s05") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestCombinedTaskGraph::vectorAddV2, context, a, b, cTornado) //
                .task("t1", TestCombinedTaskGraph::vectorMulV2, context, cTornado, b, cTornado) //
                .task("t2", TestCombinedTaskGraph::vectorSubV1, cTornado, b, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        // Change the dimension of the Grids
        workerT0.setGlobalWork(size, 1, 1);
        workerT0.setLocalWork(size / 2, 1, 1);
        workerT1.setGlobalWork(size, 1, 1);
        workerT1.setLocalWorkToNull();

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        vectorAddV1(a, b, cJava);
        vectorMulV1(cJava, b, cJava);
        vectorSubV1(cJava, b, cJava);

        for (int i = 0; i < size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i));
        }
    }
}
