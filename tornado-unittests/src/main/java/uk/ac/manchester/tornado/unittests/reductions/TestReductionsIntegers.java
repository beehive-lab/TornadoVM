/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers
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
    private static void reductionAnnotation(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    private static void reductionAnnotationConstant(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < SMALL_SIZE; i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    private static void reductionAnnotation2(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    private static void reductionAnnotationLarge(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    private static void multReductionAnnotation(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) * input.get(i));
        }
    }

    private static void maxReductionAnnotation(IntArray input, @Reduce IntArray result, int neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i)));
        }
    }

    private static void minReductionAnnotation(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i)));
        }
    }

    private static void reductionSequentialSmall(IntArray input, IntArray result) {
        int acc = 0; // neutral element for the addition
        for (int i = 0; i < input.getSize(); i++) {
            acc += input.get(i);
        }
        result.set(0, acc);
    }

    private static void reduction01(IntArray a, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            result.set(0, result.get(0) + a.get(i));
        }
    }

    private static void mapReduce01(IntArray a, IntArray b, IntArray c, @Reduce IntArray result) {

        // map
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }

        // barrier needed

        // reduction
        result.set(0, 0);
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            result.set(0, result.get(0) + c.get(i));
        }
    }

    private static void map01(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    private static void reduce01(IntArray c, @Reduce IntArray result) {
        // reduction
        result.set(0, 0);
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            result.set(0, result.get(0) + c.get(i));
        }
    }

    private static void map02(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) * a.get(i));
        }
    }

    private static void reduce02(IntArray b, @Reduce IntArray result) {
        // reduction
        result.set(0, 0);
        for (@Parallel int i = 0; i < b.getSize(); i++) {
            result.set(0, result.get(0) + b.get(i));
        }
    }

    /**
     * We reuse one of the input values.
     *
     * @param a
     * @param b
     * @param result
     */
    private static void mapReduce2(IntArray a, IntArray b, @Reduce IntArray result) {
        // map
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(0) * b.get(i));
        }

        result.set(0, 0);
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            result.set(0, result.get(0) + a.get(i));
        }
    }

    private static void reductionAddInts2(IntArray input, @Reduce IntArray result) {
        int error = 2;
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + (error + input.get(i)));
        }
    }

    private static void reductionAddInts3(IntArray inputA, IntArray inputB, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < inputA.getSize(); i++) {
            result.set(0, result.get(0) + (inputA.get(i) + inputB.get(i)));
        }
    }

    private static void maxReductionAnnotation2(IntArray input, @Reduce IntArray result, int neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i) * 100));
        }
    }

    private static void minReductionAnnotation2(IntArray input, @Reduce IntArray result, int neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i) * 50));
        }
    }

    @Test
    public void testReductionSimple() throws TornadoExecutionPlanException {

        IntArray input = new IntArray(SMALL_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testReductionIntsLarge() throws TornadoExecutionPlanException {

        IntArray input = new IntArray(LARGE_SIZE);
        IntArray result = new IntArray(256);
        final int neutral = 0;
        result.init(neutral);

        IntStream.range(0, input.getSize()).parallel().forEach(i -> {
            input.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotationLarge, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionAnnotationLarge(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testReductionAnnotation() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotation2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionAnnotation2(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testReductionConstant() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SMALL_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAnnotationConstant, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionAnnotationConstant(input, sequential);
        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMultiplicationReduction() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(64);
        IntArray result = new IntArray(1);
        result.init(0);

        input.set(10, new Random().nextInt(100));
        input.set(50, new Random().nextInt(100));

        final int neutral = 1;
        result.init(neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::multReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        sequential.init(0);

        multReductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMaxReduction() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, r.nextInt(10000));
        });

        IntArray result = new IntArray(1);

        int neutral = Integer.MIN_VALUE + 1;
        result.init(neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::maxReductionAnnotation, input, result, neutral) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        IntArray sequential = new IntArray(1);
        maxReductionAnnotation(input, sequential, neutral);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMinReduction() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx + 1);
        });

        IntArray result = new IntArray(1);
        result.init(Integer.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::minReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        sequential.init(Integer.MAX_VALUE);
        minReductionAnnotation(input, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testSequentialReduction() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SMALL_SIZE * 2);
        IntArray result = new IntArray(1);
        result.init(0);

        Random r = new Random();
        IntStream.range(0, SMALL_SIZE * 2).parallel().forEach(i -> {
            input.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionSequentialSmall, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionSequentialSmall(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.001f);
    }

    @Test
    public void testReduction01() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SMALL_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        Random r = new Random();
        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reduction01, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reduction01(input, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMapReduce() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(BIG_SIZE);
        IntArray b = new IntArray(BIG_SIZE);
        IntArray c = new IntArray(BIG_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a.set(i, 10);
            b.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("t0", TestReductionsIntegers::map01, a, b, c) //
                .task("t1", TestReductionsIntegers::reduce01, c, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(BIG_SIZE);
        mapReduce01(a, b, c, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMapReduce3() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(BIG_SIZE);
        IntArray b = new IntArray(BIG_SIZE);
        IntArray seq = new IntArray(BIG_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a.set(i, 10);
            b.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestReductionsIntegers::map02, a, b) //
                .task("t1", TestReductionsIntegers::reduce02, b, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(BIG_SIZE);
        map02(a, seq);
        reduce02(seq, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    /**
     * Currently we cannot do this due to synchronisation between the first part and
     * the second part, unless an explicit barrier is used.
     */
    @Ignore
    public void testMapReduceSameKernel() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(BIG_SIZE);
        IntArray b = new IntArray(BIG_SIZE);
        IntArray c = new IntArray(BIG_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        Random r = new Random();

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("t0", TestReductionsIntegers::mapReduce01, a, b, c, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(BIG_SIZE);
        mapReduce01(a, b, c, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Ignore
    public void testMapReduce2() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(BIG_SIZE);
        IntArray b = new IntArray(BIG_SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a.set(i, 1);
            b.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestReductionsIntegers::mapReduce2, a, b, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(BIG_SIZE);
        mapReduce2(a, b, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testSumInts2() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SMALL_SIZE);
        IntArray result = new IntArray(1);

        IntStream.range(0, SMALL_SIZE).sequential().forEach(i -> {
            input.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::reductionAddInts2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionAddInts2(input, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testSumInts3() throws TornadoExecutionPlanException {
        IntArray inputA = new IntArray(SIZE);
        IntArray inputB = new IntArray(SIZE);
        IntArray result = new IntArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA.set(i, r.nextInt());
            inputB.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA, inputB) //
                .task("t0", TestReductionsIntegers::reductionAddInts3, inputA, inputB, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        reductionAddInts3(inputA, inputB, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMaxReduction2() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        IntArray result = new IntArray(1);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx);
        });

        int neutral = Integer.MIN_VALUE + 1;
        result.init(neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::maxReductionAnnotation2, input, result, neutral) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        sequential.init(0);
        maxReductionAnnotation2(input, sequential, neutral);

        assertEquals(sequential.get(0), result.get(0));
    }

    @Test
    public void testMinReduction2() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input.set(idx, 100);
        });

        result.init(Integer.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegers::minReductionAnnotation2, input, result, Integer.MAX_VALUE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        minReductionAnnotation2(input, sequential, Integer.MAX_VALUE);
        assertEquals(sequential.get(0), result.get(0));
    }
}
