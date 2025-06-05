/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoProfilerResult;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.executor.TestExecutor
 * </code>
 * </p>
 */
public class TestExecutor extends TornadoTestBase {
    // CHECKSTYLE:OFF
    @Test
    public void test01() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        // 1. Task Graph Definition
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an execution plan
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Select the default device for the execution plan. This is optional: if no
            // device is specified, TornadoVM will launch kernels on the default device.
            // However, developers could print device information from the default device.
            TornadoDevice defaultDevice = TornadoExecutionPlan.DEFAULT_DEVICE;

            // e.g., Query the device name
            String deviceName = defaultDevice.getPhysicalDevice().getDeviceName();
            assertNotNull(deviceName);

            // 4. Add optimizations to the execution plan
            executionPlan.withProfiler(ProfilerMode.SILENT) //
                    .withPreCompilation() //
                    .withDevice(defaultDevice) //
                    .withDefaultScheduler();

            // 5. Execute all Immutable Task Graphs associated with an executor plan
            TornadoExecutionResult executionResult = executionPlan.execute();

            // 6. Obtain profiler result (only if the execution plan enabled the profiler).
            TornadoProfilerResult profilerResult = executionResult.getProfilerResult();

            assertNotNull(profilerResult);

            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(a.get(i) + b.get(i), c.get(i));
            }

        }

    }

    /**
     * Test to launch multiple times the same executor.
     */
    @Test
    public void test02() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        // 1. Task Graph Definition
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an executor and build an execution plan
        try (TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // 4. Execute all Immutable Task Graphs associated with an executor
            for (int i = 0; i < 10; i++) {
                executorPlan.execute();
            }
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }

    }

    /**
     * Test to try to break mutability.
     */
    @Test
    public void test03() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        final int INIT_A = 1;
        final int INIT_B = 2;

        a.init(INIT_A);
        b.init(INIT_B);

        // 1. Task Graph Definition
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an executor and build an execution plan
        try (TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // 4. Execute all Immutable Task Graphs associated with an executor
            executorPlan.execute();

            // 5. We check for the result
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(a.get(i) + b.get(i), c.get(i));
            }

            // 6. We try to modify the mutable task-graph before execution
            int[] d = new int[numElements];
            Arrays.fill(d, 10);
            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, d);

            // 7. Run the executor but with the already declared immutable task-graph. It
            // should not be any recompilation.
            executorPlan.execute();

        }

        // 8. We check for the result. It should be the same as in step 6.
        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(INIT_A + INIT_B, c.get(i));
        }
    }

    /**
     * Test to show how to program states of data movement across different executors. A -> B -> A
     */
    @Test
    public void test04() throws TornadoExecutionPlanException {
        int numElements = 16;
        final int ITERATIONS = 10;

        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);

        final int INIT_A = 0;

        a.init(INIT_A);

        // 1. Task Graph Definition with A -> B
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestHello::simple, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an executor and build an execution plan
        try (TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // 4. Execute all Immutable Task Graphs associated with an executor
            executorPlan.execute();

            // 5. Create a second task-graph with B->A
            TaskGraph tg2 = new TaskGraph("graph2") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, b) //
                    .task("t0", TestHello::simple, b, a) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

            ImmutableTaskGraph immutableTaskGraph2 = tg2.snapshot();
            try (TornadoExecutionPlan executorPlan2 = new TornadoExecutionPlan(immutableTaskGraph2)) {
                for (int i = 0; i < ITERATIONS; i++) {
                    executorPlan.execute(); // A -> B
                    executorPlan2.execute(); // B -> A
                }
            }
        }

        // 8. We check for the result. It should be the same as in step 6.
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(INIT_A + 2 * ITERATIONS, a.get(i));
        }

    }

    @Test
    public void test05() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {

            executionPlan.execute();
            executionPlan.printTraceExecutionPlan();
            try {
                String traceExecutionPlan = executionPlan.getTraceExecutionPlan();
            } catch (NullPointerException e) {
                fail();
            }

            TornadoDevice device = TornadoExecutionPlan.getDevice(0, 0);

            WorkerGrid workerGrid = new WorkerGrid1D(16);
            GridScheduler grid = new GridScheduler("s0.t0", workerGrid);

            // Testing multiple functions to invoke the print logic plan later
            var trace = executionPlan.withPreCompilation() //
                    .withDevice(device) //
                    .withGridScheduler(grid) //
                    .withThreadInfo() //
                    .withProfiler(ProfilerMode.SILENT);

            // When we call execute(), then it records the path 
            executionPlan.execute();

            // Print/dump the execution plan and see all optimizations that were enabled/disabled
            trace.printTraceExecutionPlan();

            // Print the plan. It must be the same as the trace variable
            executionPlan.printTraceExecutionPlan();

            String trace1 = trace.getTraceExecutionPlan();
            String trace2 = executionPlan.getTraceExecutionPlan();
            assertEquals(trace1, trace2);

            TornadoExecutionResult planResult = executionPlan.getPlanResult(0);
            System.out.println("After the execution");
            System.out.println(planResult.getProfilerResult().getTraceExecutionPlan());

        }
    }

    /**
     * Test Multi-Graphs in an execution plan. An execution plan can hold and launch
     * multiple immutable task graphs. This tests shows how to execute individual immutable
     * task graphs in any order.
     * 
     * @throws TornadoExecutionPlanException
     */
    @Test
    public void test06() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t1", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            // Select graph 1 (tg2) to execute
            // Once selected, every time we call the execute method,
            // TornadoVM will launch the passed task-graph.
            executionPlan.withGraph(1).execute();
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(a.get(i) + b.get(i), c.get(i));
            }

            // Select the graph 0 (tg1) to execute
            executionPlan.withGraph(0).execute();
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(a.get(i) + b.get(i), c.get(i));
            }

            // Select all graphs (tg1 and tg2) to execute.
            // Since we selected individual task-graphs, we should be
            // able to reverse this action and invoke all task-graph
            // again. This is achieved with the `withAllGraphs` from the
            // execution plan.
            executionPlan.withAllGraphs().execute();
        }
    }

    /**
     * Test Multi-Graphs in an execution plan. Based on the {@link #test06()}, this test checks
     * task-graphs with different tasks and data.
     *
     * @throws TornadoExecutionPlanException
     */
    @Test
    public void test07() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        }

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Set dependency from graph tg1 to tg2 

        TaskGraph tg2 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, c) //
                .task("t1", TestHello::compute, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            // Select graph 0 (tg1) and run
            executionPlan.withGraph(0).execute();
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals(a.get(i) + b.get(i), c.get(i));
            }

            // Select the graph 1 (tg2) and run
            executionPlan.withGraph(1).execute();
            for (int i = 0; i < c.getSize(); i++) {
                assertEquals((a.get(i) + b.get(i)) * 2, c.get(i));
            }
        }
    }

    // CHECKSTYLE:ON
}
