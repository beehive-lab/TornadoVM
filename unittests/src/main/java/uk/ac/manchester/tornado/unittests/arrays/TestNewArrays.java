/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestNewArrays extends TornadoTestBase {

    public static void initializeToOneParallel(int[] a) {
        int[] testArray = new int[128];
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1;
            testArray[i] = 2;
        }
        a[0] = a[0] + testArray[a.length];
        a[a.length] = a[a.length] + testArray[0];
    }

    @Test
    public void testInitArrayParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestNewArrays::initializeToOneParallel, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    public static void initializeToOne(int[] a) {
        int[] testArray = new int[128];
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
            testArray[i] = 2;
        }
        a[0] = a[0] + testArray[a.length];
        a[a.length] = a[a.length] + testArray[0];
    }

    @Test
    public void testInitArrayNotParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestNewArrays::initializeToOne, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    private static void reductionAddFloats(float[] input, @Reduce float[] result) {
        result[0] = 0.0f;
        float[] testFloatSum = new float[256];
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
            // testFloatSum[i] = 1;
        }
        // result[0] = result[0] = testFloatSum[testFloatSum.length];
    }

    @Test
    public void testInitArrayWithReductions() {
        float[] input = new float[8192];
        float[] result = new float[1];
        final int neutral = 0;
        Arrays.fill(result, neutral);

        Random r = new Random();
        IntStream.range(0, input.length).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        TaskSchedule task = new TaskSchedule("s0") //
                .streamIn(input) //
                .task("t0", TestNewArrays::reductionAddFloats, input, result) //
                .streamOut(result); //

        task.execute();

        float[] sequential = new float[1];
        reductionAddFloats(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0], 0.1f);
    }
}
