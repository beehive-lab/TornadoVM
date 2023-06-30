/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

/**
 * Testing TornadoVM with multiple independent tasks on different devices. The
 * {@link TaskGraph} contains more than one task. If multiple devices are not
 * specified by the user, then the default device is used.
 * <p>
 * The user needs to specify the target device for each task as follows:
 * </p>
 * 
 * <pre>
 * -Ds0.t0.device=0:0 -Ds0.t0.device=0:1
 * </pre>
 * <p>
 * How to run?
 * </p>
 * 
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices
 * </pre>
 **/
public class TestMultipleTasksMultipleDevices extends TornadoTestBase {
    private static final int NUM_ELEMENTS = 8192;

    private static int[] a;
    private static int[] b;
    private static int[] c;
    private static int[] d;
    private static int[] e;

    public static void task0Initialization(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = i;
        }
    }

    public static void task1Multiplication(int[] a, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * i;
        }
    }

    public static void task2Saxpy(int[] a, int[] b, int[] c, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = alpha * a[i] + b[i];
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() {
        assertAvailableDevices(2);

        a = new int[NUM_ELEMENTS];
        b = new int[NUM_ELEMENTS];
        c = new int[NUM_ELEMENTS];
        d = new int[NUM_ELEMENTS];
        e = new int[NUM_ELEMENTS];

        IntStream.range(0, NUM_ELEMENTS).forEach(i -> {
            a[i] = 30;
            b[i] = 1;
            c[i] = 120;
            e[i] = i;
        });

    }

    private static void assertAvailableDevices(int limit) {
        if (TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount() < limit) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least + " + limit + " devices enabled");
        }
    }

    @Test
    public void testTwoTasksTwoDevices() {
        System.setProperty("s0.t0.device", "0:0");
        System.setProperty("s0.t1.device", "0:1");
        TaskGraph taskGraph = new TaskGraph("s0")//
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b) //
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(30L * i, a[i]);
            assertEquals(i, b[i]);
        }
    }

    @Test
    public void testThreeTasksTwoDevices() {
        System.setProperty("s0.t0.device", "0:1");
        System.setProperty("s0.t1.device", "0:0");
        System.setProperty("s0.t2.device", "0:1");

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c, e) //
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b) //
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12) //
                .task("t2", TestMultipleTasksMultipleDevices::task2Saxpy, c, e, d, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, d); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(30L * i, a[i]);
            assertEquals(i, b[i]);
            assertEquals(12L * c[i] + e[i], d[i]);
        }
    }

}
