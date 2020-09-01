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
package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsAutomatic extends TornadoTestBase {

    public static void test(int[] input, @Reduce int[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    private static void testFloat(float[] input, @Reduce float[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    @Test
    public void testIrregularSize01() {

        final int size = 33554432 + 15;
        int[] input = new int[size];
        int[] result = new int[] { 0 };

        IntStream.range(0, size).parallel().forEach(i -> input[i] = i);

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsAutomatic::test, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        int[] sequential = new int[1];
        test(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    private void testIrregular(final int inputSize) {

        float[] input = new float[inputSize];
        float[] result = new float[] { 0.0f };

        Random r = new Random();
        IntStream.range(0, inputSize).parallel().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsAutomatic::testFloat, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        float[] sequential = new float[1];
        testFloat(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }

    @Test
    public void testIrregularSize02() {
        testIrregular(2130);
        testIrregular(18);
    }

    @Test
    public void testIrregularSize03() {
        int[] dataSizes = new int[11];
        Random r = new Random();
        IntStream.range(0, dataSizes.length).forEach(idx -> dataSizes[idx] = r.nextInt(1000));
        for (Integer size : dataSizes) {
            if (size != 0) {
                testIrregular(size);
            }
        }
    }

    private static void testDouble(double[] input, @Reduce double[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    @Test
    public void testIrregularSize04() {
        final int size = 17;
        double[] input = new double[size];
        double[] result = new double[] { 0 };

        IntStream.range(0, size).parallel().forEach(i -> {
            input[i] = i;
        });

        //@formatter:off
            TaskSchedule task = new TaskSchedule("s0")
                .streamIn(input)
                .task("t0", TestReductionsAutomatic::testDouble, input, result)
                .streamOut(result);
            //@formatter:on

        task.execute();

        double[] sequential = new double[1];
        testDouble(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.01);
    }
}
