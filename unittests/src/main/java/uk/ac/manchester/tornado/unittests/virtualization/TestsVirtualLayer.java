/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero, Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.unittests.virtualization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.common.Tornado;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class TestsVirtualLayer {

    public static void acc(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
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
        final int numKernels = 8;

        int[] data = new int[numElements];
        int initValue = 0;

        TaskSchedule s0 = new TaskSchedule("s0");
        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestsVirtualLayer::acc, data, 1);
        }
        s0.streamOut(data);

        TornadoDriver driver = getTornadoRuntime().getDriver(0);

        if (driver.getDeviceCount() < 2) {
            assertFalse("The current driver has less than 2 devices", true);
        }

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
    public void testVirtualLayer01() {
        /*
         * The following expression is not correct for Tornado to execute on
         * different devices.
         */
        final int N = 128;

        int[] data = new int[N];
        Arrays.fill(data, 100);

        TornadoDriver driver = getTornadoRuntime().getDriver(0);
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

    @Test
    public void testVirtualLayer02() {
        final int N = 128;
        int[] data = new int[N];

        Arrays.fill(data, 100);
        TornadoDriver driver = getTornadoRuntime().getDriver(0);
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

    @Test
    public void testVirtualLayer03() {
        final int N = 128;
        int[] dataA = new int[N];
        int[] dataB = new int[N];

        Arrays.fill(dataA, 100);
        Arrays.fill(dataB, 200);
        TornadoDriver driver = getTornadoRuntime().getDriver(0);
        TaskSchedule s0 = new TaskSchedule("s0");

        s0.setDevice(driver.getDevice(0));
        s0.task("t0", TestsVirtualLayer::testA, dataA, 1);
        s0.setDevice(driver.getDevice(1));
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
     * It creates one task scheduler and one task. Then it executes the same
     * task in different devices.
     * 
     * The task is just one instance for all the devices. The loop iterates over
     * the devices under the same Tornado Driver and executes the task.
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

            TaskSchedule s0 = new TaskSchedule(taskScheduleName);
            final TornadoDriver driver = getTornadoRuntime().getDriver(driverIndex);

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

                System.out.println("Setting device: " + propertyDevice + "=" + value);

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
     * It creates two task schedulers and two tasks and executes them on
     * different devices.
     */
    @Test
    public void testSchedulerDevices() {
        final int N = 128;
        int[] dataA = new int[N];
        int[] dataB = new int[N];

        Arrays.fill(dataA, 100);
        Arrays.fill(dataB, 100);

        TornadoDriver tornadoDriver = getTornadoRuntime().getDriver(0);
        if (tornadoDriver.getDeviceCount() < 2) {
            assertFalse("The current driver has less than 2 devices", true);
        }

        TaskSchedule s0 = new TaskSchedule("s0");
        Tornado.setProperty("s0.t0.device", "0:0");
        // s0.setDevice(tornadoDriver.getDevice(1)); /// XXX: fix this call
        s0.task("t0", TestsVirtualLayer::testA, dataA, 1);
        s0.streamOut(dataA);
        s0.execute();

        TaskSchedule s1 = new TaskSchedule("s1");
        Tornado.setProperty("s1.t1.device", "0:1");
        // s1.setDevice(tornadoDriver.getDevice(0));
        s1.task("t1", TestsVirtualLayer::testA, dataB, 1);
        s1.streamOut(dataB);
        s1.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(dataA[i], dataB[i]);
        }
    }

}
