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
import uk.ac.manchester.tornado.api.TornadoExecutor;
import uk.ac.manchester.tornado.api.TornadoExecutorPlan;
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
                .transferToHost(c);

        // 2. Create an immutable task graph
        ImmutableTaskGraph immutableTaskGraph = tg.close();

        // 3. Create an executor and build an execution plan
        TornadoExecutorPlan executorPlan = new TornadoExecutor(immutableTaskGraph).build();

        // 4. Add optimizations to the execution plan
        executorPlan.withReusableBuffers() //
                .withDynamicReconfiguration();//

        // 5. Execute all Immutable Task Graphs associated with an executor
        executorPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }

    }
}
