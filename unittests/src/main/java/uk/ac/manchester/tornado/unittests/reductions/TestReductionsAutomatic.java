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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsAutomatic extends TornadoTestBase {

    public static void test(int[] input, @Reduce int[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    public static void testFloat(float[] input, @Reduce float[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    public static void testConstant(int[] input, @Reduce int[] output) {
        for (@Parallel int i = 0; i < 20; i++) {
            int value = (input[i] + 10);
            output[0] += value;
        }
    }

    @Test
    public void testIrregularSize01() {

        final int size = 18;
        int[] input = new int[size];
        int[] result = new int[] { 0 };

        IntStream.range(0, size).parallel().forEach(i -> {
            input[i] = i;
        });

        System.out.println(Arrays.toString(input));

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsAutomatic::test, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        System.out.println("FINAL RESULT: " + Arrays.toString(result));

        int[] sequential = new int[1];
        TestReductionsAutomatic.test(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testIrregularSize02() {

        final int size = 18;
        float[] input = new float[size];
        float[] result = new float[] { 0.0f };

        Random r = new Random();
        IntStream.range(0, size).parallel().forEach(i -> {
            input[i] = i;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsAutomatic::testFloat, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        System.out.println("FINAL RESULT: " + Arrays.toString(result));

        float[] sequential = new float[1];
        TestReductionsAutomatic.testFloat(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.01f);
    }

}
