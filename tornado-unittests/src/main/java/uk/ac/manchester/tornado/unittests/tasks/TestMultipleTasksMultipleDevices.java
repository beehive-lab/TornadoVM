/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

/**
 * Test running two and three tasks in parallel on two devices on the same
 * backend.
 *
 * How to test?
 *
 * <code>
 * tornado-test -V --fullDebug --debug --printBytecodes
 * --jvm="-Dtornado.concurrent.devices=true -Ds0.t0.device=0:0 -Ds0.t1.device=0:0 -Ds0.t2.device=1:0 " uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices
 * </code>
 */
public class TestMultipleTasksMultipleDevices extends TornadoTestBase {
    private static final int NUM_ELEMENTS = 8192;

    private static final String[] DEVICES_FOR_TASKS = { "s0.t0.device", "s0.t1.device", "s0.t2.device" };
    // Statically assigns tasks to devices 0:0 and 0:1 of the default backend.
    private static final String[] DEFAULT_DEVICES = { "0:0", "0:1", "0:0" };

    private static IntArray a;
    private static IntArray b;
    private static IntArray c;
    private static IntArray d;
    private static IntArray e;

    public static void task0Initialization(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, i);
        }
    }

    public static void task1Multiplication(IntArray a, int alpha) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) * i);
        }
    }

    public static void task2Saxpy(IntArray a, IntArray b, IntArray c, int alpha) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, alpha * a.get(i) + b.get(i));
        }
    }

    public static void taskMultiplication(IntArray a, IntArray b, int alpha) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, b.get(i) * i);
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() {
        assertAvailableDevices();
        setDefaultDevices();
        System.setProperty("tornado.concurrent.devices", "True");

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

    private static void assertAvailableDevices() {
        String driverAndDevice = System.getProperty("tornado.unittests.device", "0:0");
        String[] parts = driverAndDevice.split(":");
        int backendIndex = Integer.parseInt(parts[0]);

        if (TornadoRuntimeProvider.getTornadoRuntime().getBackend(backendIndex).getNumDevices() < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 devices enabled on backend " + backendIndex);
        }
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
    public void testTwoTasksTwoDevices() throws TornadoExecutionPlanException {
        TaskGraph taskGraph = new TaskGraph("s0")//
                .task("t0", TestMultipleTasksMultipleDevices::task0Initialization, b) //
                .task("t1", TestMultipleTasksMultipleDevices::task1Multiplication, a, 12) //
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
    public void testThreeTasksTwoDevices() throws TornadoExecutionPlanException {
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
    public void testTwoTasksTwoDevicesSharedReadOnlyRead() throws TornadoExecutionPlanException {
        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, d, c) //
                .task("t0", TestMultipleTasksMultipleDevices::taskMultiplication, a, b, 12) //
                .task("t1", TestMultipleTasksMultipleDevices::task2Saxpy, c, b, d, 12) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, c, d); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals((b.get(i) * i), a.get(i));
            assertEquals(12L * c.get(i) + b.get(i), d.get(i));
        }
    }

}
