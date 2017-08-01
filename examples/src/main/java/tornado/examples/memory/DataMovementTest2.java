/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.examples.memory;

import java.util.Random;
import tornado.collections.types.ImageFloat;
import tornado.common.TornadoDevice.BlockingMode;
import tornado.common.TornadoDevice.CacheMode;
import tornado.common.TornadoDevice.SharingMode;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.runtime.OCLTornadoDevice;

public class DataMovementTest2 {

    private static void printArray(int[] array) {
        System.out.printf("array = [");
        for (int value : array) {
            System.out.printf("%d ", value);
        }
        System.out.println("]");
    }

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

        OCLTornadoDevice device = OpenCL.defaultDevice();
        device.ensureLoaded();

        device.read(BlockingMode.BLOCKING, SharingMode.EXCLUSIVE, CacheMode.NON_CACHEABLE, image, null);

        image.fill(-1);
        System.out.println("Reset: ");
        System.out.printf(image.toString());

        device.write(BlockingMode.BLOCKING, CacheMode.NON_CACHEABLE, image, null);

        System.out.println("After: ");
        System.out.printf(image.toString());

//		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
//		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
    }

}
