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

package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.api.Reduce;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsDoubles extends TornadoTestBase {

    private static final int SIZE = 8192;

    private static final int SIZE2 = 32;

    public static void reductionAddDoubles(double[] input, @Reduce double[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSumDoubles() {
        double[] input = new double[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = null;

        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new double[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_DEFAULT:
                break;
            case CL_DEVICE_TYPE_GPU:
                result = new double[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
		TaskSchedule task = new TaskSchedule("s0")
			.streamIn(input)
			.task("t0", TestReductionsDoubles::reductionAddDoubles, input, result)
			.streamOut(result);
		//@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        double[] sequential = new double[1];
        reductionAddDoubles(input, sequential);

        // System.out.println(Arrays.toString(result));

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void reductionAddDoubles2(double[] input, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < input.length; i++) {
            double v = (error * input[i]);
            result[0] += v;
        }
    }

    public static void reductionAddDoubles3(double[] inputA, double[] inputB, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < inputA.length; i++) {
            result[0] += (error * (inputA[i] + inputB[i]));
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void testSumDoubles2() {
        double[] input = new double[SIZE2];

        int numGroups = 1;
        if (SIZE2 > 256) {
            numGroups = SIZE2 / 256;
        }
        double[] result = null;

        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new double[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_DEFAULT:
                break;
            case CL_DEVICE_TYPE_GPU:
                result = new double[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();
        IntStream.range(0, SIZE2).sequential().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::reductionAddDoubles2, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        double[] sequential = new double[1];
        reductionAddDoubles2(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.01f);
    }

    @Test
    public void testSumDoubles3() {
        double[] input = new double[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = new double[numGroups];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::reductionAddDoubles2, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        double[] sequential = new double[1];
        reductionAddDoubles2(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    @Test
    public void testSumdoubles4() {
        double[] inputA = new double[SIZE];
        double[] inputB = new double[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = new double[numGroups];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA[i] = r.nextDouble();
            inputB[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(inputA, inputB)
            .task("t0", TestReductionsDoubles::reductionAddDoubles3, inputA, inputB, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        double[] sequential = new double[1];
        reductionAddDoubles3(inputA, inputB, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void multiplyDoubles(double[] input, @Reduce double[] result) {
        result[0] = 1.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    @Test
    public void testMultdoubles() {
        double[] input = new double[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = new double[32];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = 1.0f;
        });

        input[0] = r.nextDouble();
        input[10] = r.nextDouble();
        input[11] = r.nextDouble();

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::multiplyDoubles, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < numGroups; i++) {
            result[0] *= result[i];
        }

        double[] sequential = new double[1];
        multiplyDoubles(input, sequential);

        // System.out.println(Arrays.toString(result));

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

}
