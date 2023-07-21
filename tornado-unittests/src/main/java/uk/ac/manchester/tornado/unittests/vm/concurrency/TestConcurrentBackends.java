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

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices;

/**
 * This tests currently failing for configuration that includes SPIRV.
 */
public class TestConcurrentBackends extends TornadoTestBase {

    private static final int NUM_ELEMENTS = 8192;

    private static int[] a;
    private static int[] b;
    private static int[] c;
    private static int[] d;
    private static int[] e;

    @BeforeClass
    public static void setUp() {

        a = new int[NUM_ELEMENTS];
        b = new int[NUM_ELEMENTS];
        c = new int[NUM_ELEMENTS];
        d = new int[NUM_ELEMENTS];
        e = new int[NUM_ELEMENTS];

        IntStream.range(0, NUM_ELEMENTS).forEach(i -> {
            a[i] = 30 + i;
            b[i] = 1 + i;
            c[i] = 120 + i;
            e[i] = i;
        });

        System.setProperty("s0.t0.device", "0:1");
        System.setProperty("s0.t1.device", "1:0");
        System.setProperty("tornado.concurrent.devices", "False");

    }

    @Test
    public void testTwoBackendsSerial() {
        assertAvailableDrivers(2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b)//
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);//

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, b[i]);
            assertEquals((30 + i) * i, a[i]);
        }
    }

    @Test
    public void testTwoBackendsConcurrent() {
        assertAvailableDrivers(2);

        System.setProperty("tornado.concurrent.devices", "True");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b)//
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b);//

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
            assertEquals(i, b[i]);
            assertEquals((30 + i) * i, a[i]);
        }
    }

    @Test
    public void testThreeBackendsConcurrent() {
        assertAvailableDrivers(3);

        System.setProperty("s0.t0.device", "0:0");
        System.setProperty("s0.t1.device", "1:0");
        System.setProperty("s0.t2.device", "2:0");

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
            assertEquals(30 * i, a[i]);
            assertEquals(i, b[i]);
            assertEquals(12L * c[i] + e[i], d[i]);
        }
    }

    @Test
    public void testThreeBackendsSerial() {

        assertAvailableDrivers(3);

        System.setProperty("s0.t0.device", "0:0");
        System.setProperty("s0.t1.device", "1:0");
        System.setProperty("s0.t2.device", "2:0");

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

    private void assertAvailableDrivers(int limit) {
        if (TornadoRuntime.getTornadoRuntime().getNumDrivers() < limit) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least + " + limit + "backends with at least 1 device enabled");
        }
    }

}
