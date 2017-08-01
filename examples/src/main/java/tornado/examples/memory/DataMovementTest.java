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

import java.util.Arrays;
import tornado.common.TornadoDevice.BlockingMode;
import tornado.common.TornadoDevice.CacheMode;
import tornado.common.TornadoDevice.SharingMode;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.runtime.OCLTornadoDevice;

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
        device.ensureLoaded();

        device.read(BlockingMode.BLOCKING, SharingMode.EXCLUSIVE, CacheMode.NON_CACHEABLE, array, null);

        Arrays.fill(array, -1);
        printArray(array);

        device.write(BlockingMode.BLOCKING, CacheMode.NON_CACHEABLE, array, null);

        printArray(array);

//		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
//		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
    }

}
