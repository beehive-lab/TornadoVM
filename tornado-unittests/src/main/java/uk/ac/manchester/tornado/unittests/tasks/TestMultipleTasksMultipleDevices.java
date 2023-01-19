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
import static org.junit.Assert.assertTrue;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * Testing TornadoVM with multiple independent tasks on different devices. The
 * {@link TaskGraph} contains more than one task. If multiple devices are not
 * specified by the user, then the default device is used.
 * <p>
 * The user needs to specify the target device for each task as follows:
 * </p>
 * <code>
 *  -Ds0.t0.device=0:0 -Ds0.t0.device=0:1
 *</code>
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices
 * </code>
 **/
public class TestMultipleTasksMultipleDevices {

    @Test
    public void testTwoTasksTwoDevices() {
        final int numElements = 8192;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 10;
        });

        if (devices == 1) {
            assertTrue("This test needs at least 2 OpenCL-compatible devices.", devices == 1);
        } else {
            System.setProperty("tornado.debug", "true");
            System.setProperty("s0.t0.device", "0:1");
            System.setProperty("s0.t1.device", "0:0");
        }

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
    public void testThreeTasksThreeDevices() {
        final int numElements = 2048;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];
        int[] d = new int[numElements];
        int devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 10;
            c[i] = 120;
        });

        if (devices < 3) {
            assertTrue("This test needs at least 2 OpenCL-compatible devices.", devices < 3);
        } else {
            System.setProperty("tornado.debug", "true");
            System.setProperty("s0.t0.device", "0:1");
            System.setProperty("s0.t1.device", "0:0");
            System.setProperty("s0.t2.device", "0:2");
        }

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
