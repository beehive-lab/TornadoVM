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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsFloats
 * </code>
 */
public class TestReductionsFloats extends TornadoTestBase {

    private static final int LARGE_SIZE = 65536;
    private static final int PI_SIZE = 32768;
    private static final int SIZE = 8192;
    private static final int SIZE2 = 32;

    private static void reductionAddFloats(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    @Test
    public void testSumFloats() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);
        final int neutral = 0;
        result.init(neutral);

        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::reductionAddFloats, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloats(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    private static void reductionAddFloatsConstant(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < SIZE; i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    @Test
    public void testSumFloatsConstant() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);
        final int neutral = 0;
        result.init(neutral);

        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::reductionAddFloatsConstant, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloatsConstant(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    private static void reductionAddFloatsLarge(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    @Test
    public void testSumFloatsLarge() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(LARGE_SIZE);
        FloatArray result = new FloatArray(1024);
        final int neutral = 0;
        result.init(neutral);

        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::reductionAddFloatsLarge, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloatsLarge(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 1.f);
    }

    private static void reductionAddFloats2(FloatArray input, @Reduce FloatArray result) {
        float error = 2f;
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float v = (error * input.get(i));
            result.set(0, result.get(0) + v);
        }
    }

    private static void reductionAddFloats3(FloatArray input, @Reduce FloatArray result) {
        float error = 2f;
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float v = (error * input.get(i));
            result.set(0, result.get(0) + v);
        }
    }

    private static void reductionAddFloats4(FloatArray inputA, FloatArray inputB, @Reduce FloatArray result) {
        float error = 2f;
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < inputA.getSize(); i++) {
            result.set(0, result.get(0) + (error * (inputA.get(i) + inputB.get(i))));
        }
    }

    @Test
    public void testSumFloats2() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::reductionAddFloats3, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloats3(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    @Test
    public void testSumFloats3() throws TornadoExecutionPlanException {
        FloatArray inputA = new FloatArray(SIZE);
        FloatArray inputB = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA.set(i, r.nextFloat());
            inputB.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA, inputB) //
                .task("t0", TestReductionsFloats::reductionAddFloats4, inputA, inputB, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloats4(inputA, inputB, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    private static void multiplyFloats(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 1.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) * input.get(i));
        }
    }

    private static void computeSum(final FloatArray values, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            result.set(0, result.get(0) + values.get(i));
        }
    }

    private static void computeAvg(final int length, FloatArray result) {
        result.set(0, result.get(0) / length);
    }

    @Test
    public void testComputeAverage() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("tSum", TestReductionsFloats::computeSum, input, result) //
                .task("tAverage", TestReductionsFloats::computeAvg, input.getSize(), result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        computeSum(input, sequential);
        computeAvg(input.getSize(), sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    @Test
    public void testMultFloats() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);
        final int neutral = 1;
        result.init(neutral);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input.set(i, 1.0f);
        });

        input.set(10, r.nextFloat());
        input.set(12, r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::multiplyFloats, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        sequential.init(1.0f);
        multiplyFloats(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    private static void reductionAddFloatsConditionally(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float v = 0.0f;
            if (input.get(0) == -1) {
                v = 1.0f;
            }
            result.set(0, result.get(0) + v);
        }
    }

    // This is currently not supported
    @Ignore
    public void testSumFloatsCondition() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE2);
        FloatArray result = new FloatArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE2).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::reductionAddFloatsConditionally, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloatsConditionally(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void computePi(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 1; i < input.getSize(); i++) {
            float value = input.get(i) + (float) (TornadoMath.pow(-1, i + 1) / (2 * i - 1));
            result.set(0, result.get(0) + value);
        }
    }

    @Test
    public void testComputePi() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(PI_SIZE);
        FloatArray result = new FloatArray(1);

        IntStream.range(0, PI_SIZE).sequential().forEach(i -> {
            input.set(i, 0);
        });

        result.init(0.0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsFloats::computePi, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        final float piValue = result.get(0) * 4;

        assertEquals(3.14, piValue, 0.01f);
    }

    private static void maxReductionAnnotation(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i)));
        }
    }

    @Test
    public void testMaxReduction() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx);
        });

        result.init(Float.MIN_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::maxReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        sequential.init(Float.MIN_VALUE);

        maxReductionAnnotation(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void maxReductionAnnotation2(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i) * 100));
        }
    }

    @Test
    public void testMaxReduction2() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx);
        });

        result.init(Float.MIN_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::maxReductionAnnotation2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        sequential.init(Float.MIN_VALUE);

        maxReductionAnnotation2(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void minReductionAnnotation(FloatArray input, @Reduce FloatArray result, float neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i)));
        }
    }

    @Test
    public void testMinReduction() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input.set(idx, idx);
        });

        result.init(Float.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::minReductionAnnotation, input, result, Float.MAX_VALUE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        minReductionAnnotation(input, sequential, Float.MAX_VALUE);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void minReductionAnnotation2(FloatArray input, @Reduce FloatArray result, float neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i) * 50));
        }
    }

    @Test
    public void testMinReduction2() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray result = new FloatArray(1);

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input.set(idx, 100);
        });

        result.init(Float.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsFloats::minReductionAnnotation2, input, result, Float.MAX_VALUE)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        minReductionAnnotation2(input, sequential, Float.MAX_VALUE);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    public static float f(float x) {
        return (1 / ((x + 1) * TornadoMath.sqrt(x * TornadoMath.exp(x))));
    }

    public static void integrationTornado(FloatArray input, @Reduce FloatArray sum, final float a, final float b) {
        final int size = input.getSize();
        sum.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float value = f(a + (((i + 1)) * ((b - a) / size)));
            sum.set(0, sum.get(0) + (input.get(i) + value));
        }
    }

    @Test
    public void testIntegrate() throws TornadoExecutionPlanException {
        int size = 8192;
        FloatArray input = new FloatArray(size);
        FloatArray result = new FloatArray(1);
        FloatArray resultSeq = new FloatArray(1);
        result.init(0.0f);

        final float a = -1;
        final float b = 1;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("t0", TestReductionsFloats::integrationTornado, input, result, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        IntStream.range(0, size).sequential().forEach(idx -> {
            input.set(idx, 0);
        });

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            // We run for 10 times
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }
        integrationTornado(input, resultSeq, a, b);

        float finalValueTornado = ((b - a) / size) * result.get(0);
        float finalValueSeq = ((b - a) / size) * resultSeq.get(0);

        assertEquals(finalValueSeq, finalValueTornado, 0.01f);
    }

}
