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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackendImpl;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVRuntimeType;
import uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVTornadoDevice;
import uk.ac.manchester.tornado.runtime.common.XPUDeviceBufferState;
import uk.ac.manchester.tornado.runtime.tasks.DataObjectState;

/**
 * Test copies within TornadoVM and OpenCL Runtime/Level Zero Runtime.
 *
 * How to run?
 *
 * <code>
 * $ tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestVM
 * </code>
 */
public class TestVM {

    public TornadoDevice invokeSPIRVBackend(SPIRVRuntimeType spirvRuntime) {
        // Get the backend from TornadoVM
        SPIRVBackend spirvBackend = TornadoRuntimeProvider.getTornadoRuntime().getBackend(SPIRVBackendImpl.class).getBackend(spirvRuntime);
        System.out.println("Query SPIR_V Runtime: " + spirvBackend);
        return spirvBackend.getDeviceContext().asMapping();
    }

    public void runWithTornadoVM(SPIRVTornadoDevice device, int[] a, int[] b, int[] c) {

        System.out.println("Running Runtime For Buffer creation and copy");

        // We allocate buffer A
        DataObjectState stateA = new DataObjectState();
        XPUDeviceBufferState objectStateA = stateA.getDeviceBufferState(device);

        // We allocate buffer B
        DataObjectState stateB = new DataObjectState();
        XPUDeviceBufferState objectStateB = stateB.getDeviceBufferState(device);

        // We allocate buffer C
        DataObjectState stateC = new DataObjectState();
        XPUDeviceBufferState objectStateC = stateC.getDeviceBufferState(device);

        // Allocate a
        device.allocate(a, 0, objectStateA);

        final long executionPlanId = 0;

        // Copy-in buffer A
        device.ensurePresent(executionPlanId, a, objectStateA, null, 0, 0);

        // Allocate buffer B
        device.allocate(b, 0, objectStateB);

        // Allocate buffer c
        device.allocate(c, 0, objectStateC);

        // Stream IN buffer C
        device.streamIn(executionPlanId, c, 0, 0, objectStateC, null);

        // Copy
        // b <- device-buffer(regionA)
        device.moveDataFromDeviceBufferToHost(executionPlanId, objectStateA, b);

        // // Copy Back Data
        device.streamOutBlocking(executionPlanId, a, 0, objectStateA, null);

        // Add a barrier
        device.enqueueBarrier(executionPlanId);

        // Flush and execute all pending in the command queue
        device.flush(executionPlanId);

        System.out.println(Arrays.toString(b));

        // De-alloc
        device.deallocate(objectStateA);
        device.deallocate(objectStateB);
        device.deallocate(objectStateC);

    }

    public void test(SPIRVRuntimeType runtime) {
        TornadoDevice device = invokeSPIRVBackend(runtime);
        int[] a = new int[64];
        int[] b = new int[64];
        int[] c = new int[64];

        Arrays.fill(a, 100);
        Arrays.fill(b, 0);
        Arrays.fill(c, 50);

        if (device instanceof SPIRVTornadoDevice) {
            runWithTornadoVM((SPIRVTornadoDevice) device, a, b, c);
        }
    }

    public static void main(String[] args) {
        System.out.print("Running Native: uk.ac.manchester.tornado.drivers.spirv.tests.TestVM");
        new TestVM().test(SPIRVRuntimeType.OPENCL);
        new TestVM().test(SPIRVRuntimeType.LEVEL_ZERO);
    }
}
