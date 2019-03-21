/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples;

import java.math.BigDecimal;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * Run with:
 * 
 * tornado uk.ac.manchester.tornado.examples.Init <size>
 * 
 */
public class InitBatch {

    private static final boolean CHECK = true;

    public static void compute(float[] array) {
        for (@Parallel int i = 0; i < array.length; i++) {
            array[i] = array[i] + 100;
        }
    }

    public static void main(String[] args) {

        int size = 300000000;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }

        BigDecimal bytesToAllocate = new BigDecimal(((float) ((long) (size) * 4) * (float) 1E-6));
        System.out.println("Running with size: " + size);
        System.out.println("Input size: " + bytesToAllocate + " (MB)");
        float[] array = new float[size];
        Arrays.fill(array, 1.0f);

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        long maxDeviceMemory = device.getMaxAllocMemory();
        double mb = maxDeviceMemory * 1E-6;
        System.out.println("Maximum alloc device memory: " + mb + " (MB)");

        TaskSchedule ts = new TaskSchedule("s0");
        // @formatter:off
        ts.batch("512MB")
          .task("t0", InitBatch::compute, array)
          .streamOut((Object) array);
        // @formatter:on
        ts.execute();

        if (CHECK) {
            boolean check = true;
            for (float v : array) {
                if (v != 101.0f) {
                    check = false;
                    System.out.println("Result got: " + v);
                    break;
                }
            }
            if (!check) {
                System.out.println("Result is wrong");
            } else {
                System.out.println("Result is correct");
            }
        }
    }
}
