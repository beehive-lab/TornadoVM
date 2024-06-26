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

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsDoubles
 * </code>
 */
public class TestReductionsDoubles extends TornadoTestBase {

    private static final int SIZE_LARGE = 65536;
    private static final int SIZE = 8192;
    private static final int SIZE2 = 32;

    private static void reductionAddDoubles(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    @Test
    public void testSumDoubles() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input.set(i, r.nextDouble());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::reductionAddDoubles, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        reductionAddDoubles(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static double myFunction(double a, double b) {
        return a + b;
    }

    private static void reductionWithFunctionCall(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, myFunction(input.get(i), result.get(0)));
        }
    }

    @Test
    public void testSumWithFunctionCall() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input.set(i, r.nextDouble());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::reductionWithFunctionCall, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        reductionWithFunctionCall(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void reductionAddDoublesLarge(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    @Test
    public void testSumDoublesLarge() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE_LARGE);
        DoubleArray result = new DoubleArray(257);

        Random r = new Random();
        IntStream.range(0, SIZE_LARGE).parallel().forEach(i -> {
            input.set(i, r.nextDouble());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::reductionAddDoublesLarge, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        reductionAddDoubles(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void reductionAddDoubles2(DoubleArray input, @Reduce DoubleArray result) {
        double error = 2f;
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            double v = (error * input.get(i));
            result.set(0, result.get(0) + v);
        }
    }

    private static void reductionAddDoubles3(DoubleArray input, @Reduce DoubleArray result) {
        double error = 2f;
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            double v = (error * input.get(i));
            result.set(0, result.get(0) + v);
        }
    }

    private static void reductionAddDoubles4(DoubleArray inputA, DoubleArray inputB, @Reduce DoubleArray result) {
        double error = 2f;
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < inputA.getSize(); i++) {
            result.set(0, result.get(0) + (error * (inputA.get(i) + inputB.get(i))));
        }
    }

    @Test
    public void testSumDoubles2() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE2);
        DoubleArray result = new DoubleArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE2).sequential().forEach(i -> {
            input.set(i, r.nextDouble());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::reductionAddDoubles2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        reductionAddDoubles2(input, sequential);
        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    @Test
    public void testSumDoubles3() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input.set(i, r.nextDouble());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::reductionAddDoubles3, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        reductionAddDoubles3(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    @Test
    public void testSumDoubles4() throws TornadoExecutionPlanException {
        DoubleArray inputA = new DoubleArray(SIZE);
        DoubleArray inputB = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA.set(i, r.nextDouble());
            inputB.set(i, r.nextDouble());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA, inputB) //
                .task("t0", TestReductionsDoubles::reductionAddDoubles4, inputA, inputB, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        reductionAddDoubles4(inputA, inputB, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    private static void multiplyDoubles(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 1.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) * input.get(i));
        }
    }

    @Test
    public void testMultDoubles() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);

        result.init(1.0);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input.set(i, 1.0);
        });

        input.set(10, r.nextDouble());
        input.set(12, r.nextDouble());

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t1", TestReductionsDoubles::multiplyDoubles, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        multiplyDoubles(input, sequential);
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    private static void maxReductionAnnotation(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i)));
        }
    }

    @Test
    public void testMaxReduction() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, r.nextDouble());
        });

        DoubleArray result = new DoubleArray(1);

        result.init(Double.MIN_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::maxReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        sequential.init(Double.MIN_VALUE);

        maxReductionAnnotation(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01);
    }

    private static void minReductionAnnotation(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i)));
        }
    }

    @Test
    public void testMinReduction() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, r.nextDouble());
        });

        DoubleArray result = new DoubleArray(1);

        result.init(Double.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::minReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        sequential.init(Double.MAX_VALUE);

        minReductionAnnotation(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01);
    }

    private static void tornadoRemoveOutliers(final DoubleArray values, @Reduce DoubleArray result) {
        final double sqrt = TornadoMath.sqrt(12.2321 / values.getSize());
        final double min = result.get(0) - (2 * sqrt);
        final double max = result.get(0) + (2 * sqrt);

        // Reduce with filter
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            if (values.get(i) > max || values.get(i) < min) {
                result.set(0, result.get(0) + 1);
            }
        }
    }

    @TornadoNotSupported
    public void testRemoveOutliers() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);

        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, 2.0);
        });

        DoubleArray result = new DoubleArray(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::tornadoRemoveOutliers, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        tornadoRemoveOutliers(input, sequential);

        assertEquals(sequential.get(0), result.get(0), 0.01);
    }

    private static void prepareTornadoSumForMeanComputation(final DoubleArray values, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            result.set(0, result.get(0) + values.get(i));
        }
    }

    private static void computeMapWithReduceValue(final DoubleArray values, @Reduce DoubleArray result) {
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            values.set(i, result.get(0) + i);
        }
    }

    @Test
    public void testMultipleReductions() throws TornadoExecutionPlanException {
        DoubleArray data = new DoubleArray(SIZE);
        DoubleArray sequentialReduce = new DoubleArray(1);
        DoubleArray sequentialResult = new DoubleArray(data.getSize());

        IntStream.range(0, data.getSize()).forEach(idx -> {
            data.set(idx, Math.random());
            sequentialResult.set(idx, data.get(idx));
        });

        DoubleArray reduceResult = new DoubleArray(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestReductionsDoubles::prepareTornadoSumForMeanComputation, data, reduceResult) //
                .task("t1", TestReductionsDoubles::computeMapWithReduceValue, data, reduceResult) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data, reduceResult);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        prepareTornadoSumForMeanComputation(sequentialResult, sequentialReduce);
        computeMapWithReduceValue(sequentialResult, sequentialReduce);

        for (int i = 0; i < data.getSize(); i++) {
            assertEquals(sequentialResult.get(i), data.get(i), 0.01f);
        }
    }

    private static void computeStandardDeviation(final DoubleArray values, final DoubleArray sum, @Reduce final DoubleArray std) {
        final double s = sum.get(0) / values.getSize();
        std.set(0, 0.0f);
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            std.set(0, std.get(0) + TornadoMath.pow(values.get(i) - s, 2));
        }
    }

    @Test
    public void testMultipleReductions2() throws TornadoExecutionPlanException {

        DoubleArray data = new DoubleArray(32);
        DoubleArray data2 = new DoubleArray(32);

        DoubleArray resultSum = new DoubleArray(1);
        DoubleArray resultStd = new DoubleArray(1);

        DoubleArray sequentialSum = new DoubleArray(1);
        DoubleArray sequentialStd = new DoubleArray(1);
        DoubleArray sequentialData = new DoubleArray(data.getSize());

        IntStream.range(0, data.getSize()).forEach(idx -> {
            data.set(idx, Math.random());
            data2.set(idx, data.get(idx));
            sequentialData.set(idx, data.get(idx));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data, data2)//
                .task("t0", TestReductionsDoubles::prepareTornadoSumForMeanComputation, data, resultSum)//
                .task("t1", TestReductionsDoubles::computeStandardDeviation, data2, resultSum, resultStd)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultSum, resultStd);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        prepareTornadoSumForMeanComputation(sequentialData, sequentialSum);
        computeStandardDeviation(sequentialData, sequentialSum, sequentialStd);

        assertEquals(sequentialStd.get(0), resultStd.get(0), 0.01);

    }

    private static void prepareTornadoSum(final DoubleArray values, @Reduce DoubleArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            result.set(0, result.get(0) + values.get(i));
        }
    }

    private static void compute2(final DoubleArray values, @Reduce final DoubleArray std) {
        std.set(0, 0);
        for (@Parallel int i = 0; i < values.getSize(); i++) {
            std.set(0, std.get(0) + values.get(i));
        }
    }

    @Test
    public void testMultipleReductions3() throws TornadoExecutionPlanException {

        final int size = 8;
        DoubleArray data = new DoubleArray(size);

        DoubleArray resultSum = new DoubleArray(1);
        DoubleArray resultStd = new DoubleArray(1);

        DoubleArray sequentialSum = new DoubleArray(1);
        DoubleArray sequentialStd = new DoubleArray(1);
        DoubleArray sequentialData = new DoubleArray(data.getSize());

        Random r = new Random();
        IntStream.range(0, data.getSize()).forEach(idx -> {
            data.set(idx, r.nextDouble());
            sequentialData.set(idx, data.get(idx));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestReductionsDoubles::prepareTornadoSum, data, resultSum) //
                .task("t1", TestReductionsDoubles::compute2, data, resultStd) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultSum, resultStd);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        prepareTornadoSum(sequentialData, sequentialSum);
        compute2(sequentialData, sequentialStd);

        assertEquals(sequentialSum.get(0), resultSum.get(0), 0.01);
        assertEquals(sequentialStd.get(0), resultStd.get(0), 0.01);
    }

    private static void maxReductionAnnotation2(DoubleArray input, @Reduce DoubleArray result, double neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i) * 100));
        }
    }

    @Test
    public void testMultipleReductions4() throws TornadoExecutionPlanException {

        final int size = 8;
        DoubleArray data1 = new DoubleArray(size);
        DoubleArray data2 = new DoubleArray(size);

        DoubleArray resultStd1 = new DoubleArray(1);
        DoubleArray resultStd2 = new DoubleArray(1);

        DoubleArray sequentialStd1 = new DoubleArray(1);
        DoubleArray sequentialStd2 = new DoubleArray(1);
        DoubleArray sequentialData1 = new DoubleArray(data1.getSize());
        DoubleArray sequentialData2 = new DoubleArray(data2.getSize());

        Random r = new Random();
        IntStream.range(0, data1.getSize()).forEach(idx -> {
            data1.set(idx, r.nextDouble());
            sequentialData1.set(idx, data1.get(idx));
        });

        IntStream.range(0, data2.getSize()).forEach(idx -> {
            data2.set(idx, r.nextDouble());
            sequentialData2.set(idx, data2.get(idx));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data1, data2) //
                .task("t1", TestReductionsDoubles::compute2, data1, resultStd1) //
                .task("t2", TestReductionsDoubles::compute2, data2, resultStd2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultStd1, resultStd2);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        compute2(sequentialData1, sequentialStd1);
        compute2(sequentialData2, sequentialStd2);

        assertEquals(sequentialStd1.get(0), resultStd1.get(0), 0.01);
        assertEquals(sequentialStd2.get(0), resultStd2.get(0), 0.01);
    }

    @Test
    public void testMultipleReductions5() throws TornadoExecutionPlanException {

        final int size = 8;
        DoubleArray data1 = new DoubleArray(size);
        DoubleArray data2 = new DoubleArray(size);
        DoubleArray data3 = new DoubleArray(size);

        DoubleArray resultStd1 = new DoubleArray(1);
        DoubleArray resultStd2 = new DoubleArray(1);
        DoubleArray resultStd3 = new DoubleArray(1);

        DoubleArray sequentialStd1 = new DoubleArray(1);
        DoubleArray sequentialStd2 = new DoubleArray(1);
        DoubleArray sequentialStd3 = new DoubleArray(1);
        DoubleArray sequentialData1 = new DoubleArray(data1.getSize());
        DoubleArray sequentialData2 = new DoubleArray(data2.getSize());
        DoubleArray sequentialData3 = new DoubleArray(data3.getSize());

        Random r = new Random();
        IntStream.range(0, data1.getSize()).forEach(idx -> {
            data1.set(idx, r.nextDouble());
            sequentialData1.set(idx, data1.get(idx));
        });

        IntStream.range(0, data2.getSize()).forEach(idx -> {
            data2.set(idx, r.nextDouble());
            sequentialData2.set(idx, data2.get(idx));
        });

        IntStream.range(0, data3.getSize()).forEach(idx -> {
            data3.set(idx, r.nextDouble());
            sequentialData3.set(idx, data3.get(idx));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data1, data2, data3) //
                .task("t1", TestReductionsDoubles::compute2, data1, resultStd1) //
                .task("t2", TestReductionsDoubles::compute2, data2, resultStd2) //
                .task("t3", TestReductionsDoubles::compute2, data3, resultStd3) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultStd1, resultStd2, resultStd3);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        compute2(sequentialData1, sequentialStd1);
        compute2(sequentialData2, sequentialStd2);
        compute2(sequentialData3, sequentialStd3);

        assertEquals(sequentialStd1.get(0), resultStd1.get(0), 0.01);
        assertEquals(sequentialStd2.get(0), resultStd2.get(0), 0.01);
        assertEquals(sequentialStd3.get(0), resultStd3.get(0), 0.01);
    }

    @Test
    public void testMaxReduction2() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx);
        });

        double neutral = Double.MIN_VALUE + 1;
        result.init(neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::maxReductionAnnotation2, input, result, neutral) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        sequential.init(neutral);

        maxReductionAnnotation2(input, sequential, neutral);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

    private static void minReductionAnnotation2(DoubleArray input, @Reduce DoubleArray result, double neutral) {
        result.set(0, neutral);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i) * 50));
        }
    }

    @Test
    public void testMinReduction2() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray result = new DoubleArray(1);

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input.set(idx, 100);
        });

        result.init(Double.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsDoubles::minReductionAnnotation2, input, result, Double.MAX_VALUE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        minReductionAnnotation2(input, sequential, Double.MAX_VALUE);

        assertEquals(sequential.get(0), result.get(0), 0.01f);
    }

}
