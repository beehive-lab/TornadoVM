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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.TornadoRuntimeInterface;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

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

    public static TornadoRuntimeInterface getTornadoRuntime() {
        return TornadoRuntime.getTornadoRuntime();
    }

    /**
     * Check if enough devices are available
     */
    @Before
    public void enoughDevices() {
        super.before();
        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        if (driver.getDeviceCount() < 2) {
            throw new UnsupportedConfigurationException("Not enough devices to run tests");
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
     *
     * @throws Exception
     */
    @Test
    public void testArrayMigration() {

        final int numElements = 8;
        final int numKernels = 1;

        int[] data = new int[numElements];

        int initValue = 0;

        TaskSchedule s0 = new TaskSchedule("s0");
        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestsVirtualLayer::accumulator, data, 1);
        }
        s0.streamOut(data);

        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        s0.mapAllTo(driver.getDevice(0));
        s0.execute();

        for (int i = 0; i < numElements; i++) {
            assertEquals((initValue + numKernels), data[i]);
        }

        initValue += numKernels;

        s0.mapAllTo(driver.getDevice(1));
        s0.execute();

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

        TaskSchedule s0 = new TaskSchedule("s0");

        s0.task("t0", TestsVirtualLayer::saxpy, alpha, x, y).streamOut(y);
        s0.streamOut(y);

        s0.mapAllTo(driver.getDevice(0));
        s0.execute();

        for (int i = 0; i < numElements; i++) {
            assertEquals((alpha * 450), y[i], 0.001f);
        }

        s0.mapAllTo(driver.getDevice(1));
        s0.execute();

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

        TaskSchedule s0 = new TaskSchedule("s0");

        // This test only is executed once (the first task)

        // Assign task to device 0
        s0.setDevice(driver.getDevice(0));
        s0.task("t0", TestsVirtualLayer::testA, data, 1);
        s0.streamOut(data);
        s0.execute();

        // Assign another task to device 1
        s0.setDevice(driver.getDevice(1));
        s0.task("t1", TestsVirtualLayer::testA, data, 10);
        s0.streamOut(data);
        s0.execute();
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

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.setDevice(driver.getDevice(0));
        s0.task("t0", TestsVirtualLayer::testA, data, 1);
        s0.setDevice(driver.getDevice(1));
        s0.task("t1", TestsVirtualLayer::testA, data, 10);
        s0.streamOut(data);
        s0.execute();

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
        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        final int N = 128;
        int[] dataA = new int[N];
        int[] dataB = new int[N];

        Arrays.fill(dataA, 100);
        Arrays.fill(dataB, 200);

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestsVirtualLayer::testA, dataA, 1);
        s0.task("t1", TestsVirtualLayer::testA, dataB, 10);
        s0.streamOut(dataA);
        s0.streamOut(dataB);
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(101, dataA[i]);
            assertEquals(210, dataB[i]);
        }
    }

    /**
     * It creates one task scheduler and one task. Then it executes the same task in
     * different devices.
     *
     * The task is just one instance for all the devices. The loop iterates over the
     * devices under the same Tornado Driver and executes the task.
     */
    @Test
    public void testDynamicDeviceSwitch() {
        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        final int N = 128;
        int[] data = new int[N];

        Arrays.fill(data, 100);

        int totalNumDevices = 0;

        final int numDrivers = getTornadoRuntime().getNumDrivers();
        for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {

            String taskScheduleName = "s" + driverIndex;

            TaskSchedule s0 = new TaskSchedule(taskScheduleName);
            driver = getTornadoRuntime().getDriver(driverIndex);

            final int numDevices = driver.getDeviceCount();
            totalNumDevices += numDevices;

            String taskName = "t0";

            // It creates one task scheduler with one task. This task is shared
            // across devices.

            //@formatter:off
            s0.task(taskName, TestsVirtualLayer::testA, data, 1)
              .streamOut(data);
            //@formatter:on

            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                String propertyDevice = "s" + driverIndex + "." + taskName + ".device";
                String value = driverIndex + ":" + deviceIndex;

                // XXX: the set property should be optional.
                // Tornado.setProperty(propertyDevice, value);
                s0.setDevice(driver.getDevice(deviceIndex));
                s0.execute();
            }
        }

        for (int i = 0; i < N; i++) {
            assertEquals(100 + totalNumDevices, data[i]);
        }
    }

    /**
     * It creates two task schedulers and two tasks and executes them on different
     * devices.
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

        TaskSchedule s0 = new TaskSchedule("s0");
        TornadoRuntime.setProperty("s0.t0.device", "0:0");
        // s0.setDevice(tornadoDriver.getDevice(1)); /// XXX: fix this call
        s0.task("t0", TestsVirtualLayer::testA, dataA, 1);
        s0.streamOut(dataA);
        s0.execute();

        TaskSchedule s1 = new TaskSchedule("s1");
        TornadoRuntime.setProperty("s1.t1.device", "0:1");
        // s1.setDevice(tornadoDriver.getDevice(0));
        s1.task("t1", TestsVirtualLayer::testA, dataB, 1);
        s1.streamOut(dataB);
        s1.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(dataA[i], dataB[i]);
        }
    }

}
