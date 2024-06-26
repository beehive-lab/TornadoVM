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
package uk.ac.manchester.tornado.unittests.vm.concurrency;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices;

/**
 * Test running two and three tasks in serial and concurrent on two devices and
 * three devices on different backends if are available. You need to build with
 * multiple backends (e.g. make graal-jdk-21 BACKEND=opencl,ptx,spirv).
 *
 * <p>
 * How to test?:
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V --fullDebug --debug --printBytecodes --jvm="-Ds0.t0.device=0:0 -Ds0.t1.device=1:0 -Ds0.t2.device=2:0 " uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentBackends
 * </code>
 * </p>
 */
public class TestConcurrentBackends extends TornadoTestBase {
    private static final String[] DEVICES_FOR_TASKS = { "s0.t0.device", "s0.t1.device", "s0.t2.device" };
    // Statically assigns tasks to devices 0:0 and 0:1 of the default backend.
    private static final String[] DEFAULT_DEVICES = { "0:0", "1:0", "2:0" };

    private static final int NUM_ELEMENTS = 8192;

    private static IntArray a;
    private static IntArray b;
    private static IntArray c;
    private static IntArray d;
    private static IntArray e;

    @BeforeClass
    public static void setUp() {
        setDefaultDevices();

        a = new IntArray(NUM_ELEMENTS);
        b = new IntArray(NUM_ELEMENTS);
        c = new IntArray(NUM_ELEMENTS);
        d = new IntArray(NUM_ELEMENTS);
        e = new IntArray(NUM_ELEMENTS);

        IntStream.range(0, NUM_ELEMENTS).forEach(i -> {
            a.set(i, 30 + i);
            b.set(i, 1 + i);
            c.set(i, 120 + i);
            e.set(i, i);
        });
    }

    /**
     * It sets the default device values for tasks if they are not already set.
     */
    public static void setDefaultDevices() {
        for (int i = 0; i < DEVICES_FOR_TASKS.length; i++) {
            String taskProperty = DEVICES_FOR_TASKS[i];
            String defaultDevice = DEFAULT_DEVICES[i];

            if (System.getProperty(taskProperty) == null) {
                System.setProperty(taskProperty, defaultDevice);
            }
        }
    }

    @Test
    public void testTwoBackendsSerial() throws TornadoExecutionPlanException {
        assertAvailableDrivers(2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b)//
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals((30L + i) * i, a.get(i));
            assertEquals(i, b.get(i));
        }
    }

    @Test
    public void testTwoBackendsConcurrent() throws TornadoExecutionPlanException {
        assertAvailableDrivers(2);

        System.setProperty("tornado.concurrent.devices", "True");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b)//
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals((30L + i) * i, a.get(i));
            assertEquals(i, b.get(i));
        }
    }

    @Test
    public void testThreeBackendsConcurrent() throws TornadoExecutionPlanException {
        assertAvailableDrivers(3);

        System.setProperty("tornado.concurrent.devices", "True");

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c, e) //
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b) //
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12) //
                .task("t2", TestMultipleTasksMultipleDevices::task2Saxpy, c, e, d, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, d); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals((30L + i) * i, a.get(i));
            assertEquals(i, b.get(i));
            assertEquals(12L * c.get(i) + e.get(i), d.get(i));
        }
    }

    @Test
    public void testThreeBackendsSerial() throws TornadoExecutionPlanException {
        assertAvailableDrivers(3);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c, e) //
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b) //
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12) //
                .task("t2", TestMultipleTasksMultipleDevices::task2Saxpy, c, e, d, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, d); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals((30L + i) * i, a.get(i));
            assertEquals(i, b.get(i));
            assertEquals(12L * c.get(i) + e.get(i), d.get(i));
        }
    }

    private void assertAvailableDrivers(int limit) {
        if (TornadoRuntimeProvider.getTornadoRuntime().getNumBackends() < limit) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least + " + limit + "backends with at least 1 device enabled");
        }
    }

}
