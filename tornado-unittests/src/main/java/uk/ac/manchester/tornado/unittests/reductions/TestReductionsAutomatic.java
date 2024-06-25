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
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsAutomatic
 * </code>
 */
public class TestReductionsAutomatic extends TornadoTestBase {

    public static void test(IntArray input, @Reduce IntArray output) {
        output.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(0, output.get(0) + input.get(i));
        }
    }

    private static void testFloat(FloatArray input, @Reduce FloatArray output) {
        output.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(0, output.get(0) + input.get(i));
        }
    }

    @Test
    public void testIrregularSize01() throws TornadoExecutionPlanException {

        final int size = 33554432 + 15;
        IntArray input = new IntArray(size);
        IntArray result = new IntArray(1);
        result.init(0);

        IntStream.range(0, size).parallel().forEach(i -> input.set(i, i));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("t0", TestReductionsAutomatic::test, input, result)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        IntArray sequential = new IntArray(1);
        sequential.init(0);
        test(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    private void testIrregular(final int inputSize) throws TornadoExecutionPlanException {

        FloatArray input = new FloatArray(inputSize);
        FloatArray result = new FloatArray(1);
        result.init(0.0f);

        Random r = new Random();
        IntStream.range(0, inputSize).parallel().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("t0", TestReductionsAutomatic::testFloat, input, result)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        testFloat(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }

    @Test
    public void testIrregularSize02() throws TornadoExecutionPlanException {
        testIrregular(2130);
        testIrregular(18);
    }

    @Test
    public void testIrregularSize03() throws TornadoExecutionPlanException {
        int[] dataSizes = new int[11];
        Random r = new Random();
        IntStream.range(0, dataSizes.length).forEach(idx -> dataSizes[idx] = r.nextInt(1000));
        for (Integer size : dataSizes) {
            if (size != 0) {
                testIrregular(size);
            }
        }
    }

    private static void testDouble(DoubleArray input, @Reduce DoubleArray output) {
        output.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(0, output.get(0) + input.get(i));
        }
    }

    @Test
    public void testIrregularSize04() throws TornadoExecutionPlanException {
        final int size = 17;
        DoubleArray input = new DoubleArray(size);
        DoubleArray result = new DoubleArray(1);
        result.init(0);

        IntStream.range(0, size).parallel().forEach(i -> {
            input.set(i, i);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("t0", TestReductionsAutomatic::testDouble, input, result)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        DoubleArray sequential = new DoubleArray(1);
        testDouble(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.01);
    }
}
