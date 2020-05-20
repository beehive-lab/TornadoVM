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
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestNewArrays extends TornadoTestBase {

    public static void initializeToOneParallel(float[] a) {
        float[] testArray = new float[16];
        for (@Parallel int i = 0; i < a.length; i++) {
            if (i == 0) {
                testArray[0] = 2;
            } else if (i == 5) {
                testArray[1] = 3;
            }
            a[i] = 1;
        }
        a[0] = a[0] + testArray[0];
    }

    @Test
    public void testInitNewArrayParallel() {
        final int N = 128;
        float[] data = new float[N];
        float[] dataSeq = new float[N];

        IntStream.range(0, N).parallel().forEach(i -> {
            data[i] = (int) Math.random();
            dataSeq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestNewArrays::initializeToOneParallel, data);
        s0.streamOut(data);
        s0.execute();

        initializeToOneParallel(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq[i], data[i], 0.1);
        }
    }

    public static void initializeToOne(int[] a) {
        int[] testArray = new int[128];
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
            testArray[i] = 2;
        }
        a[0] = a[0] + testArray[0];
        a[125] = a[125] + testArray[0];
    }

    @Test
    public void testInitNewArrayNotParallel() {
        final int N = 128;
        int[] data = new int[N];
        int[] dataSeq = new int[N];

        IntStream.range(0, N).parallel().forEach(i -> {
            data[i] = (int) Math.random();
            dataSeq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestNewArrays::initializeToOne, data);
        s0.streamOut(data);
        s0.execute();

        initializeToOne(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq[i], data[i], 0.1);
        }
    }

    public static void initializeToOneParallelScope(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < 256; j++) {
                if (j % 2 == 0) {
                    acc[2] = j;
                }
            }
            a[i] = acc[2];
        }
    }

    @Test
    public void testInitNewArrayInsideParallel() {
        final int N = 256;
        float[] data = new float[N];
        float[] dataSeq = new float[N];

        IntStream.range(0, N).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            dataSeq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestNewArrays::initializeToOneParallelScope, data);
        s0.streamOut(data);
        s0.execute();

        initializeToOneParallelScope(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq[i], data[i], 0.1);
        }
    }

    public static void initializeToOneParallelScopeComplex(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < 256; j++) {
                if (j % 2 == 0) {
                    acc[2] = j;
                }
            }
            a[i] = acc[2] + a[i];
        }
    }

    @Test
    public void testInitNewArrayInsideParallelWithComplexAccesses() {
        final int N = 256;
        float[] data = new float[N];
        float[] dataSeq = new float[N];

        IntStream.range(0, N).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            dataSeq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestNewArrays::initializeToOneParallelScopeComplex, data);
        s0.streamOut(data);
        s0.execute();

        initializeToOneParallelScopeComplex(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq[i], data[i], 0.1);
        }
    }

    private static void reductionAddFloats(float[] input, @Reduce float[] result) {
        result[0] = 0.0f;
        float[] testFloatSum = new float[256];
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
            testFloatSum[i] = 1;
        }
        result[0] = result[0] + testFloatSum[testFloatSum.length];
    }

    @TornadoNotSupported
    public void testIniNewtArrayWithReductions() {
        float[] input = new float[1024];
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
