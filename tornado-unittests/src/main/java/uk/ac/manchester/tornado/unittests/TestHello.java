/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.TestHello
 * </code>
 */
public class TestHello extends TornadoTestBase {

    private static void printHello(final int n) {
        for (@Parallel int i = 0; i < n; i++) {
            Debug.printf("hello\n");
        }
    }

    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void simple(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + 1;
        }
    }

    public void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * 2;
        }
    }

    public static void compute(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * 2;
        }
    }

    @Test
    public void testHello() {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestHello::printHello, 8);
        assertNotNull(taskGraph);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        try {
            executionPlan.execute();
            assertTrue("Task was executed.", true);
        } catch (Exception e) {
            assertTrue("Task was not executed.", false);
        }
    }

    @Test
    public void testVectorAddition() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.001);
        }
    }

    /**
     * How to test:
     *
     * <code>
     *     $  tornado-test -V -J"-Dtornado.print.bytecodes=True" uk.ac.manchester.tornado.unittests.TestHello#testSimpleCompute
     * </code>
     */
    @Test
    public void testSimpleCompute() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TestHello t = new TestHello();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", t::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

    @Test
    public void testSimpleCompute2() {
        int numElements = 256;
        int[] a = new int[numElements];
        int[] b = new int[numElements];

        Arrays.fill(a, 10);

        TestHello t = new TestHello();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", t::compute, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < b.length; i++) {
            assertEquals(a[i] * 2, b[i]);
        }
    }

    @Test
    public void testSimpleInOut() {
        int numElements = 256;
        int[] a = new int[numElements];

        Arrays.fill(a, 10);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestHello::compute, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int j : a) {
            assertEquals(20, j);
        }
    }

}
