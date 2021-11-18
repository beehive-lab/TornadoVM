/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv.tests;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDriver;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;

/**
 * Test copies within TornadoVM and Level Zero driver.
 * 
 * How to run?
 * 
 * <code>
 *     $ tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestVM
 * </code>
 */
public class TestVM {

    public TornadoDevice invokeSPIRVBackend() {

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultBackend();

        // Get a Device
        TornadoDevice device = tornadoRuntime.getDriver(SPIRVDriver.class).getDefaultDevice();

        System.out.println("Selecting Device: " + device.getPhysicalDevice().getDeviceName());

        System.out.println("BACKEND: " + spirvBackend);

        return device;

    }

    public void runWithTornadoVM(SPIRVTornadoDevice device, int[] a, int[] b, int[] c) {

        System.out.println("Running Runtime For Buffer creation and copy");

        // We allocate buffer A
        GlobalObjectState stateA = new GlobalObjectState();
        DeviceObjectState objectStateA = stateA.getDeviceState(device);

        // We allocate buffer B
        GlobalObjectState stateB = new GlobalObjectState();
        DeviceObjectState objectStateB = stateB.getDeviceState(device);

        // We allocate buffer C
        GlobalObjectState stateC = new GlobalObjectState();
        DeviceObjectState objectStateC = stateC.getDeviceState(device);

        // Copy-in buffer
        device.ensurePresent(a, objectStateA, null, 0, 0);

        // Allocate buffer B
        device.ensureAllocated(b, 0, objectStateB);

        // Stream IN
        device.streamIn(c, 0, 0, objectStateC, null);

        // b <- device-buffer(regionA)
        device.moveDataFromDeviceBufferToHost(objectStateA, b);

        // // Copy Back Data
        device.streamOutBlocking(a, 0, objectStateA, null);

        // Add a barrier
        // device.enqueueBarrier();

        // Flush and execute all pending in the command queue
        device.flush();

        System.out.println(Arrays.toString(b));

    }

    public void test() {
        TornadoDevice device = invokeSPIRVBackend();
        int[] a = new int[64];
        int[] b = new int[64];
        int[] c = new int[64];

        Arrays.fill(a, 100);
        Arrays.fill(b, 0);

        if (device instanceof SPIRVTornadoDevice) {
            runWithTornadoVM((SPIRVTornadoDevice) device, a, b, c);
        }
    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestVM");
        new TestVM().test();
    }
}
