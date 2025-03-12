
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

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
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
        for (int i = 0; i < size; i++) {
            a.set(i, (float) i);
            b.set(i, (float) i * 2);
        }

        KernelContext context = new KernelContext();

        // formatter: off
        TaskGraph tg1 = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b).task("vectorAdd", TestChainOfGridSchedulers::vectorAdd, context, a, b, c1).transferToHost(
                DataTransferMode.EVERY_EXECUTION, c1);

        TaskGraph tg2 = new TaskGraph("s1").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b).task("vectorMul", TestChainOfGridSchedulers::vectorMul, context, a, b, c2).transferToHost(
                DataTransferMode.EVERY_EXECUTION, c2);

        // Create kernel context
        // Task Graph 1: Vector Addition
        WorkerGrid worker1 = new WorkerGrid1D(size);
        worker1.setGlobalWork(size, 1, 1);
        worker1.setLocalWork(256, 1, 1); // 256 threads per work-group
        GridScheduler gridScheduler1 = new GridScheduler("s0.vectorAdd", worker1);

        // Task Graph 2: Vector Multiplication
        WorkerGrid worker2 = new WorkerGrid1D(size);
        worker2.setGlobalWork(size, 1, 1);
        worker2.setLocalWork(128, 1, 1); // 128 threads per work-group (different from TaskGraph 1)
        GridScheduler gridScheduler2 = new GridScheduler("s1.vectorMul", worker2);
        // formatter: on

        // Create immutable task graphs
        ImmutableTaskGraph itg1 = tg1.snapshot();
        ImmutableTaskGraph itg2 = tg2.snapshot();

        // formatter: off
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg1, itg2)) {

            executionPlan.withGraph(0).withGridScheduler(gridScheduler1).execute(); // Execute TaskGraph 1
            executionPlan.withGraph(1).withGridScheduler(gridScheduler2).execute(); // Execute TaskGraph 2
        }
        // formatter: off

        // Verify results for TaskGraph 1 (Vector Addition)
        for (int i = 0; i < size; i++) {
            float expected = a.get(i) + b.get(i);
            float actual = c1.get(i);
            assertEquals("Mismatch at index " + i + " for TaskGraph 1", expected, actual, 1e-6f);
        }

        // Verify results for TaskGraph 2 (Vector Multiplication)
        for (int i = 0; i < size; i++) {
            float expected = a.get(i) * b.get(i);
            float actual = c2.get(i);
            assertEquals("Mismatch at index " + i + " for TaskGraph 2", expected, actual, 1e-6f);
        }
    }
}
