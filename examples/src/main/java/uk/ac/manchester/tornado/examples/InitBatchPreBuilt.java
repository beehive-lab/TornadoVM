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

package uk.ac.manchester.tornado.examples;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.examples.common.Messages;

/**
 * Run with:
 * 
 * tornado uk.ac.manchester.tornado.examples.InitBatchPre <size>
 * 
 */
public class InitBatchPreBuilt {

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
        float[] arrayA = new float[size];
        float[] arrayB = new float[size];

        IntStream.range(0, arrayA.length).sequential().forEach(idx -> arrayA[idx] = idx);
        // Arrays.fill(array, 1.0f);

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        long maxDeviceMemory = device.getMaxAllocMemory();
        double mb = maxDeviceMemory * 1E-6;
        System.out.println("Maximum alloc device memory: " + mb + " (MB)");

        TornadoDevice defaultDevice = TornadoRuntime.getTornadoRuntime().getDefaultDevice();

        // @formatter:off
        new TaskSchedule("s0")
            .batch("100MB")
            .prebuiltTask("t0", 
                        "compute", 
                        "/tmp/kernel.cl",
                        new Object[] { arrayA, arrayB },
                        new Access[] { Access.READ, Access.WRITE }, 
                        defaultDevice,
                        new int[] { 25000000 })  // Number of elements in 100MB
            .streamOut(arrayB)
            .execute();
        // @formatter:on

        if (CHECK) {
            boolean check = true;
            for (int i = 0; i < arrayB.length; i++) {
                float v = arrayB[i];
                if (Math.abs(arrayB[i] - (arrayA[i] + 100)) > 0.1f) {
                    check = false;
                    System.out.println("Result got: " + v + " in INDEX: " + i + " expected: " + (i + 100));
                    break;
                }
            }
            if (!check) {
                System.out.println(Messages.WRONG);
            } else {
                System.out.println(Messages.CORRECT);
            }
        }
    }
}
