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

import java.util.List;
import java.util.Random;

import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.mm.TornadoGlobalObjectState;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class DataMovementTest2 {

    public static void main(String[] args) {

        int sizeX = args.length == 2 ? Integer.parseInt(args[0]) : 16;
        int sizeY = args.length == 2 ? Integer.parseInt(args[1]) : 16;

        ImageFloat image = new ImageFloat(sizeX, sizeY);
        final Random rand = new Random();

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                image.set(x, y, rand.nextFloat());
            }
        }

        System.out.println("Before: ");
        System.out.printf(image.toString());

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TornadoGlobalObjectState state = TornadoRuntime.getTornadoRuntime().resolveObject(image);
        TornadoDeviceObjectState deviceState = state.getDeviceState(device);

        List<Integer> writeEvent = device.ensurePresent(image, deviceState, null, 0, 0);
        if (writeEvent != null) {
            for (Integer e : writeEvent) {
                device.resolveEvent(e).waitOn();
            }
        }

        image.fill(-1);
        System.out.println("Reset: ");
        System.out.printf(image.toString());

        int readEvent = device.streamOut(image, 0, deviceState, null);
        device.resolveEvent(readEvent).waitOn();

        System.out.println("After: ");
        System.out.printf(image.toString());

    }

}
