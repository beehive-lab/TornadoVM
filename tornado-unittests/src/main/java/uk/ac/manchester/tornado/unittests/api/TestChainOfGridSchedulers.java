/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestChainOfGridSchedulers
 * </code>
 * </p>
 */
public class TestChainOfGridSchedulers extends TornadoTestBase {
    /**
     * Task 1: Simple vector addition
     */
    public static void vectorAdd(KernelContext context, FloatArray a, FloatArray b, FloatArray c) {
        int idx = context.globalIdx;
        if (idx < c.getSize()) {
            c.set(idx, a.get(idx) + b.get(idx));
        }
    }

    /**
     * Task 2: Simple vector multiplication
     */
    public static void vectorMul(KernelContext context, FloatArray a, FloatArray b, FloatArray c) {
        int idx = context.globalIdx;
        if (idx < c.getSize()) {
            c.set(idx, a.get(idx) * b.get(idx));
        }
    }

    @Test
    public void testMultipleTaskGraphs() throws TornadoExecutionPlanException {
        final int size = 1024;

        // Allocate data arrays
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c1 = new FloatArray(size); // Output for TaskGraph 1
        FloatArray c2 = new FloatArray(size); // Output for TaskGraph 2

        // Initialize input arrays
        Random r = new Random(71);
        for (int i = 0; i < size; i++) {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
        }

        KernelContext context = new KernelContext();

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestChainOfGridSchedulers::vectorAdd, context, a, b, c1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c1);

        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorMul", TestChainOfGridSchedulers::vectorMul, context, a, b, c2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c2); //

        // Create the worker grid for the task Graph 1: Vector Addition
        WorkerGrid worker1 = new WorkerGrid1D(size);
        worker1.setLocalWork(256, 1, 1); // 256 threads per work-group

        // Create the worker grid for the task Graph 2: Vector Multiplication
        WorkerGrid worker2 = new WorkerGrid1D(size);
        worker2.setLocalWork(128, 1, 1); // 128 threads per work-group (different from TaskGraph 1)

        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("s0.vectorAdd", worker1);
        grid.addWorkerGrid("s1.vectorMul", worker2);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            // The order in which we specify the GridSchedulers and the graph selection should not matter.
            // Using the witGridScheduler().withGraph(x) and the reverse withGraph(x).witGridScheduler()
            // are equivalent.
            // A Grid-scheduler must be shared across all graphs within an execution plan.

            // Execute TaskGraph 0
            executionPlan.withGridScheduler(grid).withGraph(0).execute();

            // Execute TaskGraph 1
            executionPlan.withGridScheduler(grid).withGraph(1).execute();
        }

        // Verify results 
        for (int i = 0; i < size; i++) {
            float expected = a.get(i) + b.get(i);
            float actual = c1.get(i);
            assertEquals(expected, actual, 1e-6f);

            expected = a.get(i) * b.get(i);
            actual = c2.get(i);
            assertEquals(expected, actual, 1e-6f);
        }

    }

    /**
     * Same test as {@link #testMultipleTaskGraphs()} but with reverse order when selecting the graph and the grid scheduler.
     * 
     * @throws TornadoExecutionPlanException
     */
    @Test
    public void testMultipleTaskGraphsSchedulerReverse() throws TornadoExecutionPlanException {
        final int size = 1024;

        // Allocate data arrays
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c1 = new FloatArray(size); // Output for TaskGraph 1
        FloatArray c2 = new FloatArray(size); // Output for TaskGraph 2

        c1.init(0.0f);
        c2.init(0.0f);

        // Initialize input arrays
        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
        }

        KernelContext context = new KernelContext();

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestChainOfGridSchedulers::vectorAdd, context, a, b, c1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c1);

        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorMul", TestChainOfGridSchedulers::vectorMul, context, a, b, c2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c2); //

        // Create the worker grid for the task Graph 1: Vector Addition
        WorkerGrid worker1 = new WorkerGrid1D(size);
        worker1.setLocalWork(256, 1, 1); // 256 threads per work-group

        // Create the worker grid for the task Graph 2: Vector Multiplication
        WorkerGrid worker2 = new WorkerGrid1D(size);
        worker2.setLocalWork(128, 1, 1); // 128 threads per work-group (different from TaskGraph 1)

        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("s0.vectorAdd", worker1);
        grid.addWorkerGrid("s1.vectorMul", worker2);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            // The order in which we specify the GridSchedulers and the graph selection should not matter.
            // Using the witGridScheduler().withGraph(x) and the reverse withGraph(x).witGridScheduler()
            // are equivalent.
            // A Grid-scheduler must be shared across all graphs within an execution plan.

            // Execute TaskGraph 0
            executionPlan.withGraph(0).withGridScheduler(grid).execute();

            // Execute TaskGraph 1
            executionPlan.withGridScheduler(grid).withGraph(1).execute();
        }

        // Verify results 
        for (int i = 0; i < size; i++) {
            float expected = a.get(i) + b.get(i);
            float actual = c1.get(i);
            assertEquals(expected, actual, DELTA);

            expected = a.get(i) * b.get(i);
            actual = c2.get(i);
            assertEquals(expected, actual, DELTA);
        }
    }
}
