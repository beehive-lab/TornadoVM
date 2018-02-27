/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.examples.memory;

import java.util.Arrays;
import tornado.common.DeviceObjectState;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.runtime.OCLTornadoDevice;
import tornado.runtime.api.GlobalObjectState;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

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
