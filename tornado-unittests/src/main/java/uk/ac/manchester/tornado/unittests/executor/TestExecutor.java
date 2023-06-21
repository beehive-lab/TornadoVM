/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoProfilerResult;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
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

    @Test
    public void test01() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // 1. Task Graph Definition
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an execution plan
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Select the default device for the execution plan. This is optional: if no
        // device is specified, TornadoVM will launch kernels on the default device.
        // However, developers could print device information from the default device.
        TornadoDevice defaultDevice = TornadoExecutionPlan.DEFAULT_DEVICE;

        // e.g., Query the device name
        String deviceName = defaultDevice.getPhysicalDevice().getDeviceName();
        assertNotNull(deviceName);

        // 4. Add optimizations to the execution plan
        executorPlan.withProfiler(ProfilerMode.SILENT) //
                .withWarmUp() //
                .withDevice(defaultDevice) //
                .withDefaultScheduler();

        // 5. Execute all Immutable Task Graphs associated with an executor plan
        TornadoExecutionResult executionResult = executorPlan.execute();

        // 6. Obtain profiler result (only if the execution plan enabled the profiler).
        TornadoProfilerResult profilerResult = executionResult.getProfilerResult();

        assertNotNull(profilerResult);

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }

    }

    /**
     * Test to launch multiple times the same executor.
     */
    @Test
    public void test02() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // 1. Task Graph Definition
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an executor and build an execution plan
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // 4. Execute all Immutable Task Graphs associated with an executor
        for (int i = 0; i < 10; i++) {
            executorPlan.execute();
        }

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }

    }

    /**
     * Test to try to break mutability.
     */
    @Test
    public void test03() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        final int INIT_A = 1;
        final int INIT_B = 2;

        Arrays.fill(a, INIT_A);
        Arrays.fill(b, INIT_B);

        // 1. Task Graph Definition
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an executor and build an execution plan
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // 4. Execute all Immutable Task Graphs associated with an executor
        executorPlan.execute();

        // 5. We check for the result
        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }

        // 6. We try to modify the mutable task-graph before execution
        int[] d = new int[numElements];
        Arrays.fill(d, 10);
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, d);

        // 7. Run the executor but with the already declared immutable task-graph. It
        // should not be any recompilation.
        executorPlan.execute();

        // 8. We check for the result. It should be the same as in step 6.
        for (int i = 0; i < c.length; i++) {
            assertEquals(INIT_A + INIT_B, c[i]);
        }
    }

    /**
     * Test to show how to program states of data movement across different
     * executors. A -> B -> A
     */
    @Test
    public void test04() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        final int INIT_A = 0;

        Arrays.fill(a, INIT_A);

        // 1. Task Graph Definition with A -> B
        TaskGraph tg = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestHello::simple, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        // 3. Create an executor and build an execution plan
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // 4. Execute all Immutable Task Graphs associated with an executor
        executorPlan.execute();

        // 5. Create a second task-graph with B->A
        TaskGraph tg2 = new TaskGraph("graph2") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b) //
                .task("t0", TestHello::simple, b, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph2 = tg2.snapshot();
        TornadoExecutionPlan executorPlan2 = new TornadoExecutionPlan(immutableTaskGraph2);

        final int ITERATIONS = 10;
        for (int i = 0; i < ITERATIONS; i++) {
            executorPlan.execute(); // A -> B
            executorPlan2.execute(); // B -> A
        }

        // 8. We check for the result. It should be the same as in step 6.
        for (int i = 0; i < a.length; i++) {
            assertEquals(INIT_A + 2 * ITERATIONS, a[i]);
        }

    }
}
