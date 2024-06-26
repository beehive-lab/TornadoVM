/*
 * Copyright (c) 2013-2022, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.virtualization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.virtualization.TestsVirtualLayer
 * </code>
 */
public class TestsVirtualLayer extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void accumulator(IntArray a, int value) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + value);
        }
    }

    public static void saxpy(float alpha, FloatArray x, FloatArray y) {
        for (@Parallel int i = 0; i < y.getSize(); i++) {
            y.set(i, alpha * x.get(i));
        }
    }

    public static void testA(IntArray a, int value) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + value);
        }
    }

    public static void testB(IntArray a, int value) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) * value);
        }
    }

    /**
     * Check if enough devices are available
     */
    @Before
    public void enoughDevices() {
        super.before();
        TornadoBackend driver = getTornadoRuntime().getBackend(0);
        if (driver.getNumDevices() < 2) {
            throw new TornadoVMMultiDeviceNotSupported("Not enough devices to run tests");
        }
    }

    /**
     * Test there are at least two OpenCL devices available.
     */
    @Test
    public void testDevices() {
        TornadoBackend driver = getTornadoRuntime().getBackend(0);
        assertNotNull(driver.getDevice(0));
        assertNotNull(driver.getDevice(1));
    }

    @Test
    public void testDriverAndDevices() {
        int numDrivers = getTornadoRuntime().getNumBackends();
        for (int i = 0; i < numDrivers; i++) {
            TornadoBackend driver = getTornadoRuntime().getBackend(i);
            assertNotNull(driver);
            int numDevices = driver.getNumDevices();
            for (int j = 0; j < numDevices; j++) {
                assertNotNull(driver.getDevice(j));
            }
        }
    }

    /**
     * Test to change execution from one device to another (migration).
     */
    @Test
    public void testArrayMigration() throws TornadoExecutionPlanException {

        final int numElements = 8;
        final int numKernels = 1;

        IntArray data = new IntArray(numElements);

        int initValue = 0;

        TaskGraph taskGraph = new TaskGraph("s0");
        for (int i = 0; i < numKernels; i++) {
            taskGraph.task("t" + i, TestsVirtualLayer::accumulator, data, 1);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        TornadoBackend driver = getTornadoRuntime().getBackend(0);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            executionPlan.withDevice(driver.getDevice(0));
            executionPlan.execute();

            for (int i = 0; i < numElements; i++) {
                assertEquals((initValue + numKernels), data.get(i));
            }

            initValue += numKernels;

            // Reuse the immutable graph and the executionPlan to change the device for the
            // new
            // execution.
            executionPlan.withDevice(driver.getDevice(1));
            executionPlan.execute();
        }

        for (int i = 0; i < numElements; i++) {
            assertEquals((initValue + numKernels), data.get(i));
        }
    }

    @Test
    public void testTaskMigration() throws TornadoExecutionPlanException {

        TornadoBackend driver = getTornadoRuntime().getBackend(0);

        final int numElements = 512;
        final float alpha = 2f;

        final FloatArray x = new FloatArray(numElements);
        final FloatArray y = new FloatArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(i -> x.set(i, 450));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", TestsVirtualLayer::saxpy, alpha, x, y).transferToHost(DataTransferMode.EVERY_EXECUTION, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDevice(driver.getDevice(0)) //
                    .execute(); //

            for (int i = 0; i < numElements; i++) {
                assertEquals((alpha * 450), y.get(i), 0.001f);
            }

            executionPlan.withDevice(driver.getDevice(1)) //
                    .execute(); //
        }
        for (int i = 0; i < numElements; i++) {
            assertEquals((alpha * 450), y.get(i), 0.001f);
        }
    }

    @Ignore
    public void testVirtualLayer01() throws TornadoExecutionPlanException {

        TornadoBackend driver = getTornadoRuntime().getBackend(0);
        /*
         * The following expression is not correct for Tornado to execute on different
         * devices.
         */
        final int N = 128;
        IntArray data = new IntArray(N);
        data.init(100);

        // This test only is executed once (the first task)
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestsVirtualLayer::testA, data, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        // Assign Immutable Task Graph to device 0
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDevice(driver.getDevice(0)) //
                    .execute();

            // Assign executionPlan to device 1
            executionPlan.withDevice(driver.getDevice(1)) //
                    .execute();

            // The following task-graph mutation won't have any effect on the executionPlan
            // because the executionPlan only dispatches immutable task-graphs
            taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                    .task("t1", TestsVirtualLayer::testA, data, 10) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

            executionPlan.execute();
        }
    }

    /**
     * This test is not legal in Tornado. This test executes everything on the same device, even if the user forces to change. A task schedule is always executed on the same device. Device can change
     * once the task is executed.
     */
    @Ignore
    public void testVirtualLayer02() throws TornadoExecutionPlanException {

        TornadoBackend driver = getTornadoRuntime().getBackend(0);

        final int N = 128;
        IntArray data = new IntArray(N);
        data.init(100);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestsVirtualLayer::testA, data, 1);

        // Assign Immutable Task Graph to device 0
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDevice(driver.getDevice(0)) //
                    .execute();

            executionPlan.withDevice(driver.getDevice(1)) //
                    .execute();

            taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                    .task("t1", TestsVirtualLayer::testA, data, 10) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

            executionPlan.withDevice(driver.getDevice(0)) //
                    .execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(111, data.get(i));
        }
    }

    /**
     * Tasks within the same task schedules are always executed on the same device. Currently, it is not possible to change device for a single tasks in a group of tasks.
     */
    @Test
    public void testVirtualLayer03() throws TornadoExecutionPlanException {

        final int N = 128;
        IntArray dataA = new IntArray(N);
        IntArray dataB = new IntArray(N);

        dataA.init(100);
        dataB.init(200);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataA, dataB)//
                .task("t0", TestsVirtualLayer::testA, dataA, 1) //
                .task("t1", TestsVirtualLayer::testA, dataB, 10) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(101, dataA.get(i));
            assertEquals(210, dataB.get(i));
        }
    }

    /**
     * It creates one task graph with one task. Then, it executes the same task graph via an executionPlan on different devices.
     *
     * <p>
     * The task is just one instance for all the devices. The loop iterates over the devices under the same Tornado Driver and executes the task.
     * </p>
     */
    @Test
    public void testDynamicDeviceSwitch() throws TornadoExecutionPlanException {

        final int N = 128;
        IntArray data = new IntArray(N);

        data.init(100);

        int totalNumDevices = 0;

        final int numDrivers = getTornadoRuntime().getNumBackends();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {

            String taskScheduleName = "s" + driverIndex;

            TaskGraph taskGraph = new TaskGraph(taskScheduleName);
            TornadoBackend driver = getTornadoRuntime().getBackend(driverIndex);

            final int numDevices = driver.getNumDevices();
            totalNumDevices += numDevices;

            String taskName = "t0";

            taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                    .task(taskName, TestsVirtualLayer::testA, data, 1) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

            // Common immutable object for the graph
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

            // Common executionPlan for all permutations of devices
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                    executionPlan.withDevice(driver.getDevice(deviceIndex)) //
                            .execute();
                }
            }
        }

        for (int i = 0; i < N; i++) {
            assertEquals(100 + totalNumDevices, data.get(i));
        }
    }

    /**
     * It creates two task graphs and two tasks and executes them on different devices using different executionPlans.
     */
    @Test
    public void testSchedulerDevices() throws TornadoExecutionPlanException {
        TornadoBackend tornadoDriver = getTornadoRuntime().getBackend(0);

        final int N = 128;
        IntArray dataA = new IntArray(N);
        IntArray dataB = new IntArray(N);

        dataA.init(100);
        dataB.init(100);

        if (tornadoDriver.getNumDevices() < 2) {
            fail("The current driver has less than 2 devices");
        }

        TornadoRuntimeProvider.setProperty("s0.t0.device", "0:0");
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataA, dataB) //
                .task("t0", TestsVirtualLayer::testA, dataA, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataA);

        // Common immutable object for the graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Common executionPlan for all permutations of devices
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        TornadoRuntimeProvider.setProperty("s1.t1.device", "0:1");
        TaskGraph taskGraph2 = new TaskGraph("s1") //
                .task("t1", TestsVirtualLayer::testA, dataB, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataB);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();

        // Common executionPlan for all permutations of devices
        try (TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(immutableTaskGraph2)) {
            executionPlan2.execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(dataA.get(i), dataB.get(i));
        }
    }
    // CHECKSTYLE:ON
}
