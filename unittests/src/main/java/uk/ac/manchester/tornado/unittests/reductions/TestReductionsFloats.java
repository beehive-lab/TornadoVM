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

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.api.Reduce;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils;
import uk.ac.manchester.tornado.runtime.utils.TornadoUtils.TornadoDeviceType;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsFloats extends TornadoTestBase {

    private static final int SIZE = 8192;

    private static final int SIZE2 = 32;

    public static void reductionAddFloats(float[] input, @Reduce float[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSumFloats() {
        float[] input = new float[SIZE];

        int numGroups = TornadoUtils.getSizeReduction(SIZE, TornadoDeviceType.GPU);

        float[] result = new float[numGroups];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
		TaskSchedule task = new TaskSchedule("s0")
			.streamIn(input)
			.task("t0", TestReductionsFloats::reductionAddFloats, input, result)
			.streamOut(result);
		//@formatter:on

        task.execute();

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        float[] sequential = new float[1];
        reductionAddFloats(input, sequential);

        // System.out.println(Arrays.toString(result));

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void reductionAddFloats2(float[] input, @Reduce float[] result) {
        float error = 2f;
        for (@Parallel int i = 0; i < input.length; i++) {
            float v = (error * input[i]);
            result[0] += v;
        }
    }

    public static void reductionAddFloats3(float[] inputA, float[] inputB, @Reduce float[] result) {
        float error = 2f;
        for (@Parallel int i = 0; i < inputA.length; i++) {
            result[0] += (error * (inputA[i] + inputB[i]));
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void testSumFloats2() {
        float[] input = new float[SIZE2];

        int numGroups = 1;
        if (SIZE2 > 256) {
            numGroups = SIZE2 / 256;
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
        IntStream.range(0, SIZE2).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsFloats::reductionAddFloats2, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        float[] sequential = new float[1];
        reductionAddFloats2(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.01f);
    }

    @Test
    public void testSumFloats3() {
        float[] input = new float[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        float[] result = new float[numGroups];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsFloats::reductionAddFloats2, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        float[] sequential = new float[1];
        reductionAddFloats2(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    @Test
    public void testSumFloats4() {
        float[] inputA = new float[SIZE];
        float[] inputB = new float[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        float[] result = new float[numGroups];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA[i] = r.nextFloat();
            inputB[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(inputA, inputB)
            .task("t0", TestReductionsFloats::reductionAddFloats3, inputA, inputB, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        float[] sequential = new float[1];
        reductionAddFloats3(inputA, inputB, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void multiplyFloats(float[] input, @Reduce float[] result) {
        result[0] = 1.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    @Test
    public void testMultFloats() {
        float[] input = new float[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        float[] result = new float[32];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = 1.0f;
        });

        input[0] = r.nextFloat();
        input[10] = r.nextFloat();
        input[11] = r.nextFloat();

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsFloats::multiplyFloats, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < numGroups; i++) {
            result[0] *= result[i];
        }

        float[] sequential = new float[1];
        multiplyFloats(input, sequential);

        // System.out.println(Arrays.toString(result));

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void reductionAddFloatsConditionally(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            float v = 0.0f;
            if (input[0] == -1) {
                v = 1.0f;
            }
            result[0] += v;
        }
    }

    // This is currently not supported
    @Ignore
    @SuppressWarnings("unused")
    @Test
    public void testSumFloatsCondition() {
        float[] input = new float[SIZE2];

        int numGroups = 1;
        if (SIZE2 > 256) {
            numGroups = SIZE2 / 256;
        }
        float[] result = new float[numGroups];

        Random r = new Random();
        IntStream.range(0, SIZE2).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsFloats::reductionAddFloatsConditionally, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < numGroups; i++) {
            result[1] += result[i];
        }

        float[] sequential = new float[1];
        reductionAddFloatsConditionally(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.01f);
    }

    public static void computePi(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 1; i < input.length; i++) {
            float value = 0;
            if (i != 0) {
                value = (float) (Math.pow(-1, i + 1) / (2 * i - 1));
                result[0] += value + input[i];
            }
        }
    }

    @Test
    public void testComputePi() {
        int N = 512;

        float[] input = new float[N];
        IntStream.range(0, N).sequential().forEach(i -> {
            input[i] = 0;
        });

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        float[] result = new float[32];

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestReductionsFloats::computePi, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < numGroups; i++) {
            result[0] *= result[i];
        }

        System.out.println("Final Result: " + (result[0] * 4));

    }

}
