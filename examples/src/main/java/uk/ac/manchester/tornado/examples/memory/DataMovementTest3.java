/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.memory;

import java.util.Arrays;
import java.util.List;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoGlobalObjectState;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

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

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TornadoGlobalObjectState state = TornadoRuntime.getTornadoRuntime().resolveObject(array);
        TornadoDeviceObjectState deviceState = state.getDeviceState(device);

        List<Integer> writeEvent = device.ensurePresent(array, deviceState, null, 0, 0);
        if (writeEvent != null) {
            for (Integer e : writeEvent) {
                device.resolveEvent(e).waitOn();
            }
        }

        for (int i = 0; i < size; i++) {
            Arrays.fill(array[i], -1);
        }
        printArray(array);

        int readEvent = device.streamOut(array, 0, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        printArray(array);
    }
}
