/*
 * Copyright (c) 2013-2020, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsLong extends TornadoTestBase {

    private static final int SIZE = 4096;

    private static void reductionAnnotation(long[] input, @Reduce long[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testReductionSum() {
        long[] input = new long[SIZE];
        long[] result = new long[] { 0 };

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(input)
                .task("t0", TestReductionsLong::reductionAnnotation, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        long[] sequential = new long[1];
        reductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    private static void multReductionAnnotation(long[] input, @Reduce long[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    @Test
    public void testMultiplicationReduction() {
        long[] input = new long[64];
        long[] result = new long[] { 1 };

        input[10] = new Random().nextInt(100);
        input[50] = new Random().nextInt(100);

        final int neutral = 1;
        Arrays.fill(result, neutral);

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(input)
                .task("t0", TestReductionsLong::multReductionAnnotation, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        long[] sequential = new long[] { 1 };
        multReductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    private static void maxReductionAnnotation(long[] input, @Reduce long[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.max(result[0], input[i]);
        }
    }

    @Test
    public void testMaxReduction() {
        long[] input = new long[SIZE];

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = r.nextInt(10000);
        });

        long[] result = new long[1];

        Arrays.fill(result, Long.MIN_VALUE);

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(input)
                .task("t0", TestReductionsLong::maxReductionAnnotation, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        long[] sequential = new long[1];
        maxReductionAnnotation(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    private static void minReductionAnnotation(long[] input, @Reduce long[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.min(result[0], input[i]);
        }
    }

    @Test
    public void testMinReduction() {
        long[] input = new long[SIZE];
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx + 1;
        });

        long[] result = new long[1];
        Arrays.fill(result, Integer.MAX_VALUE);

        //@formatter:off
        new TaskGraph("s0")
                .task("t0", TestReductionsLong::minReductionAnnotation, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        long[] sequential = new long[] { Long.MAX_VALUE };
        minReductionAnnotation(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    private static void maxReductionAnnotation2(long[] input, @Reduce long[] result) {
        result[0] = Long.MIN_VALUE + 1;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.max(result[0], input[i] * 2);
        }
    }

    @Test
    public void testMaxReduction2() {
        long[] input = new long[SIZE];
        long[] result = new long[1];
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx;
        });

        long neutral = Long.MIN_VALUE;

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(input)
                .task("t0", TestReductionsLong::maxReductionAnnotation2, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        long[] sequential = new long[] { neutral };
        maxReductionAnnotation2(input, sequential);
        assertEquals(sequential[0], result[0]);
    }

    private static void minReductionAnnotation2(long[] input, @Reduce long[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.min(result[0], input[i] * 50);
        }
    }

    @Test
    public void testMinReduction2() {
        long[] input = new long[SIZE];
        long[] result = new long[1];

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input[idx] = 100;
        });

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(input)
                .task("t0", TestReductionsLong::minReductionAnnotation2, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        long[] sequential = new long[1];
        minReductionAnnotation2(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

}
