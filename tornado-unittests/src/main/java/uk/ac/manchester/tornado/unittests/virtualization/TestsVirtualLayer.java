/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.virtualization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.virtualization.TestsVirtualLayer
 * </code>
 */
public class TestsVirtualLayer extends TornadoTestBase {

    public static void accumulator(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
        }
    }

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void testA(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] + value;
        }
    }

    public static void testB(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * value;
        }
    }

    /**
     * Check if enough devices are available
     */
    @Before
    public void enoughDevices() {
        super.before();
        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        if (driver.getDeviceCount() < 2) {
            throw new TornadoVMMultiDeviceNotSupported("Not enough devices to run tests");
        }
    }

    /**
     * Test there are at least two OpenCL devices available
     */
    @Test
    public void testDevices() {
        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        assertNotNull(driver.getDevice(0));
        assertNotNull(driver.getDevice(1));
    }

    @Test
    public void testDriverAndDevices() {
        int numDrivers = getTornadoRuntime().getNumDrivers();
        for (int i = 0; i < numDrivers; i++) {
            TornadoDriver driver = getTornadoRuntime().getDriver(i);
            assertNotNull(driver);
            int numDevices = driver.getDeviceCount();
            for (int j = 0; j < numDevices; j++) {
                assertNotNull(driver.getDevice(j));
            }
        }
    }

    /**
     * Test to change execution from one device to another (migration).
     */
    @Test
    public void testArrayMigration() {

        final int numElements = 8;
        final int numKernels = 1;

        int[] data = new int[numElements];

        int initValue = 0;

        TaskGraph taskGraph = new TaskGraph("s0");
        for (int i = 0; i < numKernels; i++) {
            taskGraph.task("t" + i, TestsVirtualLayer::accumulator, data, 1);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withDevice(driver.getDevice(0));
        executionPlan.execute();

        for (int i = 0; i < numElements; i++) {
            assertEquals((initValue + numKernels), data[i]);
        }

        initValue += numKernels;

        // Reuse the immutable graph and the executionPlan to change the device for the
        // new
        // execution.
        executionPlan.withDevice(driver.getDevice(1));
        executionPlan.execute();

        for (int i = 0; i < numElements; i++) {
            assertEquals((initValue + numKernels), data[i]);
        }
    }

    @Test
    public void testTaskMigration() {

        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        final int numElements = 512;
        final float alpha = 2f;

        final float[] x = new float[numElements];
        final float[] y = new float[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> x[i] = 450);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", TestsVirtualLayer::saxpy, alpha, x, y).transferToHost(DataTransferMode.EVERY_EXECUTION, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(driver.getDevice(0)) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals((alpha * 450), y[i], 0.001f);
        }

        executionPlan.withDevice(driver.getDevice(1)) //
                .execute(); //

        for (int i = 0; i < numElements; i++) {
            assertEquals((alpha * 450), y[i], 0.001f);
        }
    }

    @Ignore
    public void testVirtualLayer01() {

        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        /*
         * The following expression is not correct for Tornado to execute on different
         * devices.
         */
        final int N = 128;
        int[] data = new int[N];
        Arrays.fill(data, 100);

        // This test only is executed once (the first task)
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestsVirtualLayer::testA, data, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        // Assign Immutable Task Graph to device 0
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
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

    /**
     * This test is not legal in Tornado. This test executes everything on the same
     * device, even if the user forces to change. A task schedule is always executed
     * on the same device. Device can change once the task is executed.
     */
    @Ignore
    public void testVirtualLayer02() {

        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        final int N = 128;
        int[] data = new int[N];
        Arrays.fill(data, 100);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestsVirtualLayer::testA, data, 1);

        // Assign Immutable Task Graph to device 0
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(driver.getDevice(0)) //
                .execute();

        executionPlan.withDevice(driver.getDevice(1)) //
                .execute();

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t1", TestsVirtualLayer::testA, data, 10) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        executionPlan.withDevice(driver.getDevice(0)) //
                .execute();

        for (int i = 0; i < N; i++) {
            assertEquals(111, data[i]);
        }
    }

    /**
     * Tasks within the same task schedules are always executed on the same device.
     * Currently, it is not possible to change device for a single tasks in a group
     * of tasks.
     *
     */
    @Test
    public void testVirtualLayer03() {

        final int N = 128;
        int[] dataA = new int[N];
        int[] dataB = new int[N];

        Arrays.fill(dataA, 100);
        Arrays.fill(dataB, 200);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataA, dataB)//
                .task("t0", TestsVirtualLayer::testA, dataA, 1) //
                .task("t1", TestsVirtualLayer::testA, dataB, 10) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(101, dataA[i]);
            assertEquals(210, dataB[i]);
        }
    }

    /**
     * It creates one task graph with one task. Then, it executes the same task
     * graph via an executionPlan on different devices.
     *
     * <p>
     * The task is just one instance for all the devices. The loop iterates over the
     * devices under the same Tornado Driver and executes the task.
     * </p>
     */
    @Test
    public void testDynamicDeviceSwitch() {

        final int N = 128;
        int[] data = new int[N];

        Arrays.fill(data, 100);

        int totalNumDevices = 0;

        final int numDrivers = getTornadoRuntime().getNumDrivers();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {

            String taskScheduleName = "s" + driverIndex;

            TaskGraph taskGraph = new TaskGraph(taskScheduleName);
            TornadoDriver driver = getTornadoRuntime().getDriver(driverIndex);

            final int numDevices = driver.getDeviceCount();
            totalNumDevices += numDevices;

            String taskName = "t0";

            taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                    .task(taskName, TestsVirtualLayer::testA, data, 1) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

            // Common immutable object for the graph
            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

            // Common executionPlan for all permutations of devices
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                executionPlan.withDevice(driver.getDevice(deviceIndex)) //
                        .execute();
            }
        }

        for (int i = 0; i < N; i++) {
            assertEquals(100 + totalNumDevices, data[i]);
        }
    }

    /**
     * It creates two task graphs and two tasks and executes them on different
     * devices using different executionPlans.
     */
    @Test
    public void testSchedulerDevices() {
        TornadoDriver tornadoDriver = getTornadoRuntime().getDriver(0);

        final int N = 128;
        int[] dataA = new int[N];
        int[] dataB = new int[N];

        Arrays.fill(dataA, 100);
        Arrays.fill(dataB, 100);

        if (tornadoDriver.getDeviceCount() < 2) {
            assertFalse("The current driver has less than 2 devices", true);
        }

        TornadoRuntime.setProperty("s0.t0.device", "0:0");
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, dataA, dataB) //
                .task("t0", TestsVirtualLayer::testA, dataA, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataA);

        // Common immutable object for the graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Common executionPlan for all permutations of devices
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        TornadoRuntime.setProperty("s1.t1.device", "0:1");
        TaskGraph taskGraph2 = new TaskGraph("s1") //
                .task("t1", TestsVirtualLayer::testA, dataB, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dataB);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();

        // Common executionPlan for all permutations of devices
        TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(immutableTaskGraph2);
        executionPlan2.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(dataA[i], dataB[i]);
        }
    }

}
