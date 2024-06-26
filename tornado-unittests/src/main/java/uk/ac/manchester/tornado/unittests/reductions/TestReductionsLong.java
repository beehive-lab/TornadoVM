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
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsLong
 * </code>
 */
public class TestReductionsLong extends TornadoTestBase {

    private static final int SIZE = 4096;

    private static void reductionAnnotation(LongArray input, @Reduce LongArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    @Test
    public void testReductionSum() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(SIZE);
        LongArray result = new LongArray(1);
        result.init(0);

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLong::reductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray sequential = new LongArray(1);
        reductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    private static void multReductionAnnotation(LongArray input, @Reduce LongArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) * input.get(i));
        }
    }

    @Test
    public void testMultiplicationReduction() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(64);
        LongArray result = new LongArray(1);

        input.set(10, new Random().nextInt(100));
        input.set(50, new Random().nextInt(100));

        final int neutral = 1;
        result.init(neutral);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLong::multReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray sequential = new LongArray(1);
        sequential.init(0);
        multReductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0));
    }

    private static void maxReductionAnnotation(LongArray input, @Reduce LongArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i)));
        }
    }

    @Test
    public void testMaxReduction() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(SIZE);

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, r.nextInt(10000));
        });

        LongArray result = new LongArray(1);
        result.init(Long.MIN_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLong::maxReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray sequential = new LongArray(1);
        maxReductionAnnotation(input, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    private static void minReductionAnnotation(LongArray input, @Reduce LongArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i)));
        }
    }

    @Test
    public void testMinReduction() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(SIZE);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx + 1);
        });

        LongArray result = new LongArray(1);
        result.init(Integer.MAX_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsLong::minReductionAnnotation, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray sequential = new LongArray(1);
        sequential.init(Long.MAX_VALUE);
        minReductionAnnotation(input, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

    private static void maxReductionAnnotation2(LongArray input, @Reduce LongArray result) {
        result.set(0, Long.MIN_VALUE + 1);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i) * 2));
        }
    }

    @Test
    public void testMaxReduction2() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(SIZE);
        LongArray result = new LongArray(1);
        IntStream.range(0, SIZE).forEach(idx -> {
            input.set(idx, idx);
        });

        long neutral = Long.MIN_VALUE;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLong::maxReductionAnnotation2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray sequential = new LongArray(1);
        sequential.init(neutral);
        maxReductionAnnotation2(input, sequential);
        assertEquals(sequential.get(0), result.get(0));
    }

    private static void minReductionAnnotation2(LongArray input, @Reduce LongArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i) * 50));
        }
    }

    @Test
    public void testMinReduction2() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(SIZE);
        LongArray result = new LongArray(1);

        IntStream.range(0, SIZE).parallel().forEach(idx -> {
            input.set(idx, 100);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLong::minReductionAnnotation2, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        LongArray sequential = new LongArray(1);
        minReductionAnnotation2(input, sequential);

        assertEquals(sequential.get(0), result.get(0));
    }

}
