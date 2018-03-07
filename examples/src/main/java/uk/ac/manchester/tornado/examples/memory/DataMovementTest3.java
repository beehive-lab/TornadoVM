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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples.memory;

import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.util.Arrays;

import uk.ac.manchester.tornado.common.DeviceObjectState;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.api.GlobalObjectState;

public class DataMovementTest3 {

    private static void printArray(int[][] values) {
        for (int i = 0; i < values.length; i++) {
            System.out.printf("%d| %s\n", i, Arrays.toString(values[i]));
        }
    }

    public static void main(String[] args) {

        int size = args.length == 1 ? Integer.parseInt(args[0]) : 8;
        int[][] array = new int[size][size];
        for (int i = 0; i < size; i++) {
            Arrays.setAll(array[i], (index) -> index);
        }
        printArray(array);

        OCLTornadoDevice device = OpenCL.defaultDevice();

        GlobalObjectState state = getTornadoRuntime().resolveObject(array);
        DeviceObjectState deviceState = state.getDeviceState(device);

        int writeEvent = device.ensurePresent(array, deviceState);
        if (writeEvent != -1) {
            device.resolveEvent(writeEvent).waitOn();
        }

        for (int i = 0; i < size; i++) {
            Arrays.fill(array[i], -1);
        }
        printArray(array);

        int readEvent = device.streamOut(array, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        printArray(array);

//		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
//		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
    }

}
