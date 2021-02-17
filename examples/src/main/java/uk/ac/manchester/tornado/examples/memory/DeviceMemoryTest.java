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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class DeviceMemoryTest {

    public static String humanReadableByteCount(long bytes, boolean si) {
        final int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        final String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");

        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static void main(final String[] args) {

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        final TornadoMemoryProvider mm = device.getDeviceContext().getMemoryManager();

        final long heapSize = mm.getHeapSize() - 1024;

        final int numWords = (int) (heapSize >> 2);

        System.out.printf("device memory test:\n\tdevice: %s\n\tmax heap=%s\n\tnum words=%d\n", device.getPhysicalDevice().getDeviceName(), humanReadableByteCount(heapSize, false), numWords);

        final int[] data = new int[numWords];

        final TaskSchedule schedule = new TaskSchedule("s0").streamIn(data).task("t0", DeviceMemoryTest::fill, data).streamOut(data);

        schedule.warmup();

        intialise(data);
        schedule.execute();

        validate(data);

    }

    private static void fill(int[] data) {
        for (@Parallel int i = 0; i < data.length; i++) {
            data[i] = i;
        }
    }

    private static void intialise(int[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
    }

    private static void validate(int[] data) {
        int errors = 0;
        int first = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != i) {
                errors++;
                if (first == -1) {
                    first = i;
                }
            }
        }
        System.out.printf("data=%s, errors=%d, first=%d\n", humanReadableByteCount(data.length << 2, false), errors, first);
    }

}
