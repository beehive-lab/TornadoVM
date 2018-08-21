/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero
 *
 */

package uk.ac.manchester.tornado.examples.reductions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.drivers.opencl.OCLDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;

public class ReductionAddFloats {

    private static final int MAX_ITERATIONS = 101;

    public static void reductionAddFloats(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    public double computeMedian(ArrayList<Long> input) {
        Collections.sort(input);
        double middle = input.size() / 2;
        if (input.size() % 2 == 1) {
            middle = (input.get(input.size() / 2) + input.get(input.size() / 2 - 1)) / 2;
        }
        return middle;
    }

    public OCLDeviceType getDefaultDeviceType() {
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        OCLTornadoDevice defaultDevice = (OCLTornadoDevice) driver.getDefaultDevice();
        OCLDevice device = defaultDevice.getDevice();
        return device.getDeviceType();
    }

    public void run(int size) {
        float[] input = new float[size];

        int numGroups = 1;
        if (size > 256) {
            numGroups = size / 256;
        }
        float[] result = null;

        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new float[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_DEFAULT:
                break;
            case CL_DEVICE_TYPE_GPU:
                result = new float[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();
        IntStream.range(0, size).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", ReductionAddFloats::reductionAddFloats, input, result)
            .streamOut(result);
        //@formatter:on

        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < MAX_ITERATIONS; i++) {

            long start = System.nanoTime();
            task.execute();
            long end = System.nanoTime();

            for (int j = 1; j < result.length; j++) {
                result[0] += result[j];
            }

            timers.add((end - start));
        }

        System.out.println("Median TotalTime: " + computeMedian(timers));
    }

    public static void main(String[] args) {
        int inputSize = 8192;
        if (args.length > 0) {
            inputSize = Integer.parseInt(args[0]);
        }
        System.out.println("Size = " + inputSize);
        new ReductionAddFloats().run(inputSize);
    }
}
