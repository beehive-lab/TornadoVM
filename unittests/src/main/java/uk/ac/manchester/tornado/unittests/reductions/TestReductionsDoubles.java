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

package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
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

    public double[] allocResultArray(int numGroups) {
        TornadoDeviceType deviceType = getDefaultDeviceType();
        double[] result = null;
        switch (deviceType) {
            case CPU:
                result = new double[Runtime.getRuntime().availableProcessors()];
                break;
            case GPU:
            case ACCELERATOR:
                result = new double[numGroups];
                break;
            default:
                break;
        }
        return result;
    }

    @Test
    public void testSumDoubles() {
        double[] input = new double[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = allocResultArray(numGroups);

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
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void reductionAddDoubles2(double[] input, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < input.length; i++) {
            double v = (error * input[i]);
            result[0] += v;
        }
    }

    public static void reductionAddDoubles3(double[] input, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < input.length; i++) {
            double v = (error * input[i]);
            result[0] += v;
        }
    }

    public static void reductionAddDoubles4(double[] inputA, double[] inputB, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < inputA.length; i++) {
            result[0] += (error * (inputA[i] + inputB[i]));
        }
    }

    @Test
    public void testSumDoubles2() {
        double[] input = new double[SIZE2];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = allocResultArray(numGroups);

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
        assertEquals(sequential[0], result[0], 0.01f);
    }

    @Test
    public void testSumDoubles3() {
        double[] input = new double[SIZE];

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = allocResultArray(numGroups);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::reductionAddDoubles3, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        double[] sequential = new double[1];
        reductionAddDoubles2(input, sequential);

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
        double[] result = allocResultArray(numGroups);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA[i] = r.nextDouble();
            inputB[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(inputA, inputB)
            .task("t0", TestReductionsDoubles::reductionAddDoubles4, inputA, inputB, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        double[] sequential = new double[1];
        reductionAddDoubles4(inputA, inputB, sequential);
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
        double[] result = allocResultArray(numGroups);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = 1.0;
        });

        Arrays.fill(result, 1.0);

        input[10] = r.nextDouble();
        input[12] = r.nextDouble();

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::multiplyDoubles, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < result.length; i++) {
            result[0] *= result[i];
        }

        double[] sequential = new double[1];
        multiplyDoubles(input, sequential);
        assertEquals(sequential[0], result[0], 0.1f);
    }

    public static void maxReductionAnnotation(double[] input, @Reduce double[] result, int neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.max(result[0], input[i]);
        }
    }

    @Test
    public void testMaxReduction() {
        double[] input = new double[SIZE];

        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx;
        });

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = allocResultArray(numGroups);

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::maxReductionAnnotation, input, result, Integer.MIN_VALUE)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < result.length; i++) {
            result[0] = Math.max(result[0], result[i]);
        }

        double[] sequential = new double[1];
        maxReductionAnnotation(input, sequential, Integer.MIN_VALUE);

        assertEquals(sequential[0], result[0], 0.01);
    }

    public static void minReductionAnnotation(double[] input, @Reduce double[] result, int neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.min(result[0], input[i]);
        }
    }

    @Test
    public void testMinReduction() {
        double[] input = new double[SIZE];

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input[idx] = idx;
        });

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        double[] result = allocResultArray(numGroups);

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::minReductionAnnotation, input, result, Integer.MAX_VALUE)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < result.length; i++) {
            result[0] = Math.min(result[0], result[i]);
        }

        double[] sequential = new double[1];
        minReductionAnnotation(input, sequential, Integer.MAX_VALUE);

        assertEquals(sequential[0], result[0], 0.01);
    }

}
