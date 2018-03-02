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

import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.util.Arrays;

import uk.ac.manchester.tornado.common.DeviceObjectState;
import uk.ac.manchester.tornado.drivers.opencl.OpenCL;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.api.GlobalObjectState;

public class DataMovementTest {

    private static void printArray(int[] array) {
        System.out.printf("array = [");
        for (int value : array) {
            System.out.printf("%d ", value);
        }
        System.out.println("]");
    }

    public static void main(String[] args) {

        int size = args.length == 1 ? Integer.parseInt(args[0]) : 16;
        int[] array = new int[size];
        Arrays.setAll(array, (index) -> index);
        printArray(array);

        OCLTornadoDevice device = OpenCL.defaultDevice();

        GlobalObjectState state = getTornadoRuntime().resolveObject(array);
        DeviceObjectState deviceState = state.getDeviceState(device);

        int writeEvent = device.ensurePresent(array, deviceState);
        if (writeEvent != -1) {
            device.resolveEvent(writeEvent).waitOn();
        }

        Arrays.fill(array, -1);
        printArray(array);

        int readEvent = device.streamOut(array, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        printArray(array);

//		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
//		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
    }

}
