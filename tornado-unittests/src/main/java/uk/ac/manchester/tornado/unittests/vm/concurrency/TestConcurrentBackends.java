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
package uk.ac.manchester.tornado.unittests.vm.concurrency;

import org.junit.BeforeClass;
import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksSingleDevice;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestConcurrentBackends {

    private static final int NUM_ELEMENTS = 8192;
    private static int[] a;
    private static int[] b;

    @BeforeClass
    public static void setUp() {
        int drivers = TornadoRuntime.getTornadoRuntime().getNumDrivers();

        if (drivers < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 backends with at least 1 device enabled");

        }

        a = new int[NUM_ELEMENTS];
        b = new int[NUM_ELEMENTS];
        Arrays.fill(a, 30);
        Arrays.fill(b, 1);

        System.setProperty("s0.t0.device", "0:1");
        System.setProperty("s0.t1.device", "1:0");
        System.setProperty("tornado.parallel.interpreters", "False");

    }

    @Test
    public void testTwoBackendsSerial() {

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b)//
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);//

        runAndVerifyResults(taskGraph);

    }

    @Test
    public void testTwoBackendsConcurrent() {
        System.setProperty("tornado.parallel.interpreters", "True");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksSingleDevice::task0Initialization, b)//
                .task("t1", TestMultipleTasksSingleDevice::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);//

        runAndVerifyResults(taskGraph);

    }

    private static void runAndVerifyResults(TaskGraph taskGraph) {
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(360, a[i]);
            assertEquals(10, b[i]);
        }
    }
}
