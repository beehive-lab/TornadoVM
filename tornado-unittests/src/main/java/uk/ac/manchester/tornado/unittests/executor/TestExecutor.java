/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutor;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 * <p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.executor.TestExecutor
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

        // 3. Create an executor and build an execution plan
        TornadoExecutionPlan executorPlan = new TornadoExecutor(immutableTaskGraph).build();

        // 4. Add optimizations to the execution plan
        executorPlan.withWarmUp() //
                .withDefaultDevice() //
                .withDefaultScheduler();

        // 5. Execute all Immutable Task Graphs associated with an executor
        executorPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }

    }

    /**
     * Test to launch multiple times the same executor
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
        TornadoExecutionPlan executorPlan = new TornadoExecutor(immutableTaskGraph).build();

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
        TornadoExecutionPlan executorPlan = new TornadoExecutor(immutableTaskGraph).build();

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
}
