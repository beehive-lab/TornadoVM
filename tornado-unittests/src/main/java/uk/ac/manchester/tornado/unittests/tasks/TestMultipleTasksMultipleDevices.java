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

import org.junit.BeforeClass;
import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * Testing TornadoVM with multiple independent tasks on different devices. The {@link TaskGraph} contains more than one task. If multiple devices are not specified by the user, then the default device
 * is used.
 * <p>
 * The user needs to specify the target device for each task as follows:
 * </p>
 * <pre>
 * -Ds0.t0.device=0:0 -Ds0.t0.device=0:1
 * </pre>
 * <p>
 * How to run?
 * </p>
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices
 * </pre>
 **/
public class TestMultipleTasksMultipleDevices {
    private static int devices;
    private static int numElements;
    private static int[] a;
    private static int[] b;
    private static int[] c;
    private static int[] d;

    @BeforeClass
    public static void setUpBeforeClass() {
        devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        numElements = 8192;

        if (devices < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 devices in the current backend.");
        }

        a = new int[numElements];
        b = new int[numElements];
        c = new int[numElements];
        d = new int[numElements];

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 1;
            c[i] = 120;
        });

    }

    @Test
    public void testTwoTasksTwoDevices() {
        System.setProperty("s0.t0.device", "0:0");
        System.setProperty("s0.t1.device", "0:1");
        TaskGraph taskGraph = new TaskGraph("s0")//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(360, a[i]);
            assertEquals(10, b[i]);
        }
    }

    @Test
    public void testThreeTasksTwoDevices() {
        System.setProperty("s0.t0.device", "0:0");
        System.setProperty("s0.t1.device", "0:1");
        System.setProperty("s0.t2.device", "0:0");

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b) //
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12) //
                .task("t2", TestMultipleTasksSingleDevice::task2Saxpy, c, c, d, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, d); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(360, a[i]);
            assertEquals(10, b[i]);
            assertEquals((12 * 120) + 120, d[i]);
        }
    }
}
