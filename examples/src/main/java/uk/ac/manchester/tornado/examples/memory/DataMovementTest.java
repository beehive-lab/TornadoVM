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

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();

        TornadoGlobalObjectState state = TornadoRuntime.getTornadoRuntime().resolveObject(array);
        TornadoDeviceObjectState deviceState = state.getDeviceState(device);

        List<Integer> writeEvent = device.ensurePresent(array, deviceState, null, 0, 0);
        if (writeEvent != null) {
            for (Integer e : writeEvent) {
                device.resolveEvent(e).waitOn();
            }
        }

        Arrays.fill(array, -1);
        printArray(array);

        int readEvent = device.streamOut(array, 0, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        printArray(array);

    }

}
