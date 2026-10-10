/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Launches that depend on each other must see each other's results even when a backend queues them
 * without waiting in between (the Metal backend encodes the kernels of a task graph into shared
 * command buffers), and data the host writes between executions must reach the kernels of the next
 * execution only.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestDependentLaunches
 * </code>
 */
public class TestDependentLaunches extends TornadoTestBase {

    private static final int SIZE = 4096;

    private static final int LOCAL = 128;

    public static void increment(KernelContext context, IntArray data) {
        int id = context.globalIdx;
        if (id < data.getSize()) {
            data.set(id, data.get(id) + 1);
        }
    }

    public static void scale(KernelContext context, IntArray input, IntArray output, int factor) {
        int id = context.globalIdx;
        if (id < input.getSize()) {
            output.set(id, input.get(id) * factor);
        }
    }

    private static WorkerGrid worker() {
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(LOCAL, 1, 1);
        return worker;
    }

    /** A hundred launches in one graph, each reading what the previous one wrote. */
    @Test
    public void testChainOfDependentLaunches() throws TornadoExecutionPlanException {
        final int launches = 100;
        IntArray data = new IntArray(SIZE);
        data.init(0);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        GridScheduler gridScheduler = new GridScheduler();
        for (int i = 0; i < launches; i++) {
            taskGraph.task("t" + i, TestDependentLaunches::increment, new KernelContext(), data);
            gridScheduler.addWorkerGrid("s0.t" + i, worker());
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(launches, data.get(i));
            }
            executionPlan.withGridScheduler(gridScheduler).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(2 * launches, data.get(i));
            }
        }
    }

    /** The host rewrites the input between executions; each execution must see its own values. */
    @Test
    public void testHostWritesBetweenExecutions() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        IntArray middle = new IntArray(SIZE);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestDependentLaunches::scale, new KernelContext(), input, middle, 2) //
                .task("t1", TestDependentLaunches::scale, new KernelContext(), middle, output, 3) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s0.t0", worker());
        gridScheduler.addWorkerGrid("s0.t1", worker());

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int round = 1; round <= 5; round++) {
                for (int i = 0; i < SIZE; i++) {
                    input.set(i, i + round);
                }
                executionPlan.withGridScheduler(gridScheduler).execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals("round " + round + " element " + i, 6 * (i + round), output.get(i));
                }
            }
        }
    }
}
