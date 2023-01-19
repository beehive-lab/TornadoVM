/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers
 * </code>
 */
public class TestReductionsIntegers extends TornadoTestBase {

    private static final int SMALL_SIZE = 512;
    private static final int BIG_SIZE = 1024;

    private static final int LARGE_SIZE = 262144;
    private static final int SIZE = 4096;

    /**
     * First approach: use annotations in the user code to identify the reduction
     * variables. This is a similar approach to OpenMP and OpenACC.
     *
     * @param input
     * @param result
     */
    private static void reductionAnnotation(int[] input, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    private static void reductionAnnotationConstant(int[] input, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < SMALL_SIZE; i++) {
            result[0] += input[i];
        }
    }

    private static void reductionAnnotation2(int[] input, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    private static void reductionAnnotationLarge(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    private static void multReductionAnnotation(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    private static void maxReductionAnnotation(int[] input, @Reduce int[] result, int neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.max(result[0], input[i]);
        }
    }

    private static void minReductionAnnotation(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.min(result[0], input[i]);
        }
    }

    private static void reductionSequentialSmall(int[] input, int[] result) {
        int acc = 0; // neutral element for the addition
        for (int i = 0; i < input.length; i++) {
            acc += input[i];
        }
        result[0] = acc;
    }

    private static void reduction01(int[] a, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < a.length; i++) {
            result[0] += a[i];
        }
    }

    private static void mapReduce01(int[] a, int[] b, int[] c, @Reduce int[] result) {

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }

        // barrier needed

        // reduction
        result[0] = 0;
        for (@Parallel int i = 0; i < c.length; i++) {
            result[0] += c[i];
        }
    }

    private static void map01(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    private static void reduce01(int[] c, @Reduce int[] result) {
        // reduction
        result[0] = 0;
        for (@Parallel int i = 0; i < c.length; i++) {
            result[0] += c[i];
        }
    }

    private static void map02(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] * a[i];
        }
    }

    private static void reduce02(int[] b, @Reduce int[] result) {
        // reduction
        result[0] = 0;
        for (@Parallel int i = 0; i < b.length; i++) {
            result[0] += b[i];
        }
    }

    /**
     * We reuse one of the input values
     *
     * @param a
     * @param b
     * @param result
     */
    private static void mapReduce2(int[] a, int[] b, @Reduce int[] result) {
        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * b[i];
        }

        for (@Parallel int i = 0; i < a.length; i++) {
            result[0] += a[i];
        }
    }

    private static void reductionAddInts2(int[] input, @Reduce int[] result) {
        int error = 2;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += (error + input[i]);
        }
    }

    private static void reductionAddInts3(int[] inputA, int[] inputB, @Reduce int[] result) {
        for (@Parallel int i = 0; i < inputA.length; i++) {
            result[0] += (inputA[i] + inputB[i]);
        }
    }

    private static void maxReductionAnnotation2(int[] input, @Reduce int[] result, int neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.max(result[0], input[i] * 100);
        }
    }

    private static void minReductionAnnotation2(int[] input, @Reduce int[] result, int neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.min(result[0], input[i] * 50);
        }
    }

    @Test
    public void testReductionSimple() {

        int[] input = new int[SMALL_SIZE];
        int[] result = new int[] { 0 };

        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testReductionIntsLarge() {

        int[] input = new int[LARGE_SIZE];
        int[] result = new int[256];
        final int neutral = 0;
        Arrays.fill(result, neutral);

        IntStream.range(0, input.length).parallel().forEach(i -> {
            input[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotationLarge, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionAnnotationLarge(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testReductionAnnotation() {
        int[] input = new int[SIZE];
        int[] result = new int[] { 0 };

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotation2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionAnnotation2(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testReductionConstant() {
        int[] input = new int[SMALL_SIZE];
        int[] result = new int[] { 0 };

        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotationConstant, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionAnnotationConstant(input, sequential);
        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMultiplicationReduction() {
        int[] input = new int[64];
        int[] result = new int[] { 1 };

        input[10] = new Random().nextInt(100);
        input[50] = new Random().nextInt(100);

        final int neutral = 1;
        Arrays.fill(result, neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::multReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[] { 1 };
        multReductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMaxReduction() {
        int[] input = new int[SIZE];

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = r.nextInt(10000);
        });

        int[] result = new int[1];

        int neutral = Integer.MIN_VALUE + 1;
        Arrays.fill(result, neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::maxReductionAnnotation, input, result, neutral) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        maxReductionAnnotation(input, sequential, neutral);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMinReduction() {
        int[] input = new int[SIZE];
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx + 1;
        });

        int[] result = new int[1];
        Arrays.fill(result, Integer.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::minReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[] { Integer.MAX_VALUE };
        minReductionAnnotation(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testSequentialReduction() {
        int[] input = new int[SMALL_SIZE * 2];
        int[] result = new int[] { 0 };

        Random r = new Random();
        IntStream.range(0, SMALL_SIZE * 2).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionSequentialSmall, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionSequentialSmall(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    @Test
    public void testReduction01() {
        int[] input = new int[SMALL_SIZE];
        int[] result = new int[] { 0 };

        Random r = new Random();
        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reduction01, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reduction01(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMapReduce() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];
        int[] c = new int[BIG_SIZE];
        int[] result = new int[] { 0 };

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = 10;
            b[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("t0", TestReductionsIntegers::map01, a, b, c) //
                .task("t1", TestReductionsIntegers::reduce01, c, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[BIG_SIZE];
        mapReduce01(a, b, c, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMapReduce3() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];
        int[] seq = new int[BIG_SIZE];
        int[] result = new int[] { 0 };

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = 10;
            b[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestReductionsIntegers::map02, a, b) //
                .task("t1", TestReductionsIntegers::reduce02, b, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[BIG_SIZE];
        map02(a, seq);
        reduce02(seq, sequential);

        assertEquals(sequential[0], result[0]);
    }

    /**
     * Currently we cannot do this due to synchronisation between the first part and
     * the second part, unless an explicit barrier is used.
     */
    @Ignore
    public void testMapReduceSameKernel() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];
        int[] c = new int[BIG_SIZE];
        int[] result = new int[] { 0 };

        Random r = new Random();

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("t0", TestReductionsIntegers::mapReduce01, a, b, c, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[BIG_SIZE];
        mapReduce01(a, b, c, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Ignore
    public void testMapReduce2() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];
        int[] result = new int[] { 0 };

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = 1;
            b[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestReductionsIntegers::mapReduce2, a, b, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[BIG_SIZE];
        mapReduce2(a, b, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testSumInts2() {
        int[] input = new int[SMALL_SIZE];
        int[] result = new int[1];

        IntStream.range(0, SMALL_SIZE).sequential().forEach(i -> {
            input[i] = 2;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAddInts2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionAddInts2(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testSumInts3() {
        int[] inputA = new int[SIZE];
        int[] inputB = new int[SIZE];
        int[] result = new int[1];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA[i] = r.nextInt();
            inputB[i] = r.nextInt();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA, inputB) //
                .task("t0", TestReductionsIntegers::reductionAddInts3, inputA, inputB, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        reductionAddInts3(inputA, inputB, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMaxReduction2() {
        int[] input = new int[SIZE];
        int[] result = new int[1];
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx;
        });

        int neutral = Integer.MIN_VALUE + 1;
        Arrays.fill(result, neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::maxReductionAnnotation2, input, result, neutral) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[] { neutral };
        maxReductionAnnotation2(input, sequential, neutral);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testMinReduction2() {
        int[] input = new int[SIZE];
        int[] result = new int[1];

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input[idx] = 100;
        });

        Arrays.fill(result, Integer.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::minReductionAnnotation2, input, result, Integer.MAX_VALUE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        int[] sequential = new int[1];
        minReductionAnnotation2(input, sequential, Integer.MAX_VALUE);
        assertEquals(sequential[0], result[0]);
    }
}
