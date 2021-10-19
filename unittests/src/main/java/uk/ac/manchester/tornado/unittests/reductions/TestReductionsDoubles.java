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

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsDoubles extends TornadoTestBase {

    private static final int SIZE_LARGE = 65536;
    private static final int SIZE = 8192;
    private static final int SIZE2 = 32;

    private static void reductionAddDoubles(double[] input, @Reduce double[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSumDoubles() {
        double[] input = new double[SIZE];
        double[] result = new double[1];

        Random r = new Random();
        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
		TaskSchedule task = new TaskSchedule("s0")
			.task("t0", TestReductionsDoubles::reductionAddDoubles, input, result)
			.streamOut(result);
		//@formatter:on

        task.execute();

        double[] sequential = new double[1];
        reductionAddDoubles(input, sequential);

        assertEquals(sequential[0], result[0], 0.01f);
    }

    private static double myFunction(double a, double b) {
        return a + b;
    }

    private static void reductionWithFunctionCall(double[] input, @Reduce double[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = myFunction(input[i], result[0]);
        }
    }

    @Test
    public void testSumWithFunctionCall() {
        double[] input = new double[SIZE];
        double[] result = new double[1];

        Random r = new Random();
        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
                .task("t0", TestReductionsDoubles::reductionWithFunctionCall, input, result)
                .streamOut(result);
        //@formatter:on

        task.execute();

        double[] sequential = new double[1];
        reductionWithFunctionCall(input, sequential);

        assertEquals(sequential[0], result[0], 0.01f);
    }

    private static void reductionAddDoublesLarge(double[] input, @Reduce double[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSumDoublesLarge() {
        double[] input = new double[SIZE_LARGE];
        double[] result = new double[257];

        Random r = new Random();
        IntStream.range(0, SIZE_LARGE).parallel().forEach(i -> {
            input[i] = r.nextDouble();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
                .task("t0", TestReductionsDoubles::reductionAddDoublesLarge, input, result)
                .streamOut(result);
        //@formatter:on

        task.execute();

        double[] sequential = new double[1];
        reductionAddDoubles(input, sequential);

        assertEquals(sequential[0], result[0], 0.01f);
    }

    private static void reductionAddDoubles2(double[] input, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < input.length; i++) {
            double v = (error * input[i]);
            result[0] += v;
        }
    }

    private static void reductionAddDoubles3(double[] input, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < input.length; i++) {
            double v = (error * input[i]);
            result[0] += v;
        }
    }

    private static void reductionAddDoubles4(double[] inputA, double[] inputB, @Reduce double[] result) {
        double error = 2f;
        for (@Parallel int i = 0; i < inputA.length; i++) {
            result[0] += (error * (inputA[i] + inputB[i]));
        }
    }

    @Test
    public void testSumDoubles2() {
        double[] input = new double[SIZE2];
        double[] result = new double[1];

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

        double[] sequential = new double[1];
        reductionAddDoubles2(input, sequential);
        assertEquals(sequential[0], result[0], 0.01f);
    }

    @Test
    public void testSumDoubles3() {
        double[] input = new double[SIZE];
        double[] result = new double[1];

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

        double[] sequential = new double[1];
        reductionAddDoubles2(input, sequential);

        assertEquals(sequential[0], result[0], 0.1f);
    }

    @Test
    public void testSumDoubles4() {
        double[] inputA = new double[SIZE];
        double[] inputB = new double[SIZE];
        double[] result = new double[1];

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

        double[] sequential = new double[1];
        reductionAddDoubles4(inputA, inputB, sequential);
        assertEquals(sequential[0], result[0], 0.1f);
    }

    private static void multiplyDoubles(double[] input, @Reduce double[] result) {
        result[0] = 1.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    @Test
    public void testMultDoubles() {
        double[] input = new double[SIZE];
        double[] result = new double[1];

        Arrays.fill(result, 1.0);

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = 1.0;
        });

        input[10] = r.nextDouble();
        input[12] = r.nextDouble();

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t1", TestReductionsDoubles::multiplyDoubles, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        double[] sequential = new double[1];
        multiplyDoubles(input, sequential);
        assertEquals(sequential[0], result[0], 0.1f);
    }

    private static void maxReductionAnnotation(double[] input, @Reduce double[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.max(result[0], input[i]);
        }
    }

    @Test
    public void testMaxReduction() {
        double[] input = new double[SIZE];

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = r.nextDouble();
        });

        double[] result = new double[1];

        Arrays.fill(result, Double.MIN_VALUE);

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::maxReductionAnnotation, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        double[] sequential = new double[] { Double.MIN_VALUE };
        maxReductionAnnotation(input, sequential);

        assertEquals(sequential[0], result[0], 0.01);
    }

    private static void minReductionAnnotation(double[] input, @Reduce double[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.min(result[0], input[i]);
        }
    }

    @Test
    public void testMinReduction() {
        double[] input = new double[SIZE];

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = r.nextDouble();
        });

        double[] result = new double[1];

        Arrays.fill(result, Double.MAX_VALUE);

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsDoubles::minReductionAnnotation, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        double[] sequential = new double[] { Double.MAX_VALUE };
        minReductionAnnotation(input, sequential);

        assertEquals(sequential[0], result[0], 0.01);
    }

    private static void tornadoRemoveOutliers(final double[] values, @Reduce double[] result) {
        final double sqrt = TornadoMath.sqrt(12.2321 / values.length);
        final double min = result[0] - (2 * sqrt);
        final double max = result[0] + (2 * sqrt);

        // Reduce with filter
        for (@Parallel int i = 0; i < values.length; i++) {
            if (values[i] > max || values[i] < min) {
                result[0]++;
            }
        }
    }

    @TornadoNotSupported
    public void testRemoveOutliers() {
        double[] input = new double[SIZE];

        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = 2.0;
        });

        double[] result = new double[1];

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(input)
                .task("t0", TestReductionsDoubles::tornadoRemoveOutliers, input, result)
                .streamOut(result)
                .execute();
        //@formatter:on

        double[] sequential = new double[1];
        tornadoRemoveOutliers(input, sequential);

        assertEquals(sequential[0], result[0], 0.01);
    }

    private static void prepareTornadoSumForMeanComputation(final double[] values, @Reduce double[] result) {
        for (@Parallel int i = 0; i < values.length; i++) {
            result[0] += values[i];
        }
    }

    private static void computeMapWithReduceValue(final double[] values, @Reduce double[] result) {
        for (@Parallel int i = 0; i < values.length; i++) {
            values[i] = result[0] + i;
        }
    }

    @Test
    public void testMultipleReductions() {
        double[] data = new double[SIZE];
        double[] sequentialReduce = new double[1];
        double[] sequentialResult = new double[data.length];

        IntStream.range(0, data.length).forEach(idx -> {
            data[idx] = Math.random();
            sequentialResult[idx] = data[idx];
        });

        double[] reduceResult = new double[1];

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(data)
                .task("t0", TestReductionsDoubles::prepareTornadoSumForMeanComputation, data, reduceResult)
                .task("t1", TestReductionsDoubles::computeMapWithReduceValue, data, reduceResult)
                .streamOut(reduceResult, data)
                .execute();
        //@formatter:on

        prepareTornadoSumForMeanComputation(sequentialResult, sequentialReduce);
        computeMapWithReduceValue(sequentialResult, sequentialReduce);

        for (int i = 0; i < data.length; i++) {
            assertEquals(sequentialResult[i], data[i], 0.01);
        }
    }

    private static void computeStandardDeviation(final double[] values, final double[] sum, @Reduce final double[] std) {
        final double s = sum[0] / values.length;
        for (@Parallel int i = 0; i < values.length; i++) {
            std[0] += TornadoMath.pow(values[i] - s, 2);
        }
    }

    @Test
    public void testMultipleReductions2() {

        double[] data = new double[32];
        double[] data2 = new double[32];

        double[] resultSum = new double[1];
        double[] resultStd = new double[1];

        double[] sequentialSum = new double[1];
        double[] sequentialStd = new double[1];
        double[] sequentialData = new double[data.length];

        IntStream.range(0, data.length).forEach(idx -> {
            data[idx] = Math.random();
            data2[idx] = data[idx];
            sequentialData[idx] = data[idx];
        });

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(data)
                .task("t0", TestReductionsDoubles::prepareTornadoSumForMeanComputation, data, resultSum)
                .task("t1", TestReductionsDoubles::computeStandardDeviation, data2, resultSum, resultStd)
                .streamOut(resultSum, resultStd)
                .execute();
        //@formatter:on

        prepareTornadoSumForMeanComputation(sequentialData, sequentialSum);
        computeStandardDeviation(sequentialData, sequentialSum, sequentialStd);

        assertEquals(sequentialStd[0], resultStd[0], 0.01);

    }

    private static void prepareTornadoSum(final double[] values, @Reduce double[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < values.length; i++) {
            result[0] += values[i];
        }
    }

    private static void computeMax(final double[] values, final double[] sum, @Reduce final double[] std) {
        final double s = sum[0] / values.length;
        for (@Parallel int i = 0; i < values.length; i++) {
            std[0] += values[i] + s;
        }
    }

    private static void compute2(final double[] values, @Reduce final double[] std) {
        std[0] = 0;
        for (@Parallel int i = 0; i < values.length; i++) {
            std[0] += values[i];
        }
    }

    @Test
    public void testMultipleReductions3() {

        final int size = 8;
        double[] data = new double[size];

        double[] resultSum = new double[1];
        double[] resultStd = new double[1];

        double[] sequentialSum = new double[1];
        double[] sequentialStd = new double[1];
        double[] sequentialData = new double[data.length];

        Random r = new Random();
        IntStream.range(0, data.length).forEach(idx -> {
            data[idx] = r.nextDouble();
            sequentialData[idx] = data[idx];
        });

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(data)
                .task("t0", TestReductionsDoubles::prepareTornadoSum, data, resultSum)
                .task("t1", TestReductionsDoubles::compute2, data, resultStd)
                .streamOut(resultSum, resultStd)
                .execute();
        //@formatter:on

        prepareTornadoSum(sequentialData, sequentialSum);
        compute2(sequentialData, sequentialStd);

        assertEquals(sequentialSum[0], resultSum[0], 0.01);
        assertEquals(sequentialStd[0], resultStd[0], 0.01);
    }

    private static void maxReductionAnnotation2(double[] input, @Reduce double[] result, double neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.max(result[0], input[i] * 100);
        }
    }

    @Test
    public void testMultipleReductions4() {

        final int size = 8;
        double[] data1 = new double[size];
        double[] data2 = new double[size];

        double[] resultStd1 = new double[1];
        double[] resultStd2 = new double[1];

        double[] sequentialStd1 = new double[1];
        double[] sequentialStd2 = new double[1];
        double[] sequentialData1 = new double[data1.length];
        double[] sequentialData2 = new double[data2.length];

        Random r = new Random();
        IntStream.range(0, data1.length).forEach(idx -> {
            data1[idx] = r.nextDouble();
            sequentialData1[idx] = data1[idx];
        });

        IntStream.range(0, data2.length).forEach(idx -> {
            data2[idx] = r.nextDouble();
            sequentialData2[idx] = data2[idx];
        });

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(data1, data2)
                .task("t1", TestReductionsDoubles::compute2, data1, resultStd1)
                .task("t2", TestReductionsDoubles::compute2, data2, resultStd2)
                .streamOut(resultStd1, resultStd2)
                .execute();
        //@formatter:on

        compute2(sequentialData1, sequentialStd1);
        compute2(sequentialData2, sequentialStd2);

        assertEquals(sequentialStd1[0], resultStd1[0], 0.01);
        assertEquals(sequentialStd2[0], resultStd2[0], 0.01);
    }

    @Test
    public void testMultipleReductions5() {

        final int size = 8;
        double[] data1 = new double[size];
        double[] data2 = new double[size];
        double[] data3 = new double[size];

        double[] resultStd1 = new double[1];
        double[] resultStd2 = new double[1];
        double[] resultStd3 = new double[1];

        double[] sequentialStd1 = new double[1];
        double[] sequentialStd2 = new double[1];
        double[] sequentialStd3 = new double[1];
        double[] sequentialData1 = new double[data1.length];
        double[] sequentialData2 = new double[data2.length];
        double[] sequentialData3 = new double[data3.length];

        Random r = new Random();
        IntStream.range(0, data1.length).forEach(idx -> {
            data1[idx] = r.nextDouble();
            sequentialData1[idx] = data1[idx];
        });

        IntStream.range(0, data2.length).forEach(idx -> {
            data2[idx] = r.nextDouble();
            sequentialData2[idx] = data2[idx];
        });

        IntStream.range(0, data3.length).forEach(idx -> {
            data3[idx] = r.nextDouble();
            sequentialData3[idx] = data3[idx];
        });

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(data1, data2, data3)
                .task("t1", TestReductionsDoubles::compute2, data1, resultStd1)
                .task("t2", TestReductionsDoubles::compute2, data2, resultStd2)
                .task("t3", TestReductionsDoubles::compute2, data3, resultStd3)
                .streamOut(resultStd1, resultStd2, resultStd3)
                .execute();
        //@formatter:on

        compute2(sequentialData1, sequentialStd1);
        compute2(sequentialData2, sequentialStd2);
        compute2(sequentialData3, sequentialStd3);

        assertEquals(sequentialStd1[0], resultStd1[0], 0.01);
        assertEquals(sequentialStd2[0], resultStd2[0], 0.01);
        assertEquals(sequentialStd3[0], resultStd3[0], 0.01);
    }

    @Test
    public void testMaxReduction2() {
        double[] input = new double[SIZE];
        double[] result = new double[1];
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx;
        });

        double neutral = Double.MIN_VALUE + 1;
        Arrays.fill(result, neutral);

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(input)
                .task("t0", TestReductionsDoubles::maxReductionAnnotation2, input, result, neutral)
                .streamOut(result)
                .execute();
        //@formatter:on

        double[] sequential = new double[] { neutral };
        maxReductionAnnotation2(input, sequential, neutral);

        assertEquals(sequential[0], result[0], 0.01f);
    }

    private static void minReductionAnnotation2(double[] input, @Reduce double[] result, double neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = TornadoMath.min(result[0], input[i] * 50);
        }
    }

    @Test
    public void testMinReduction2() {
        double[] input = new double[SIZE];
        double[] result = new double[1];

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input[idx] = 100;
        });

        Arrays.fill(result, Double.MAX_VALUE);

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(input)
                .task("t0", TestReductionsDoubles::minReductionAnnotation2, input, result, Double.MAX_VALUE)
                .streamOut(result)
                .execute();
        //@formatter:on

        double[] sequential = new double[1];
        minReductionAnnotation2(input, sequential, Double.MAX_VALUE);

        assertEquals(sequential[0], result[0], 0.01f);
    }

}
