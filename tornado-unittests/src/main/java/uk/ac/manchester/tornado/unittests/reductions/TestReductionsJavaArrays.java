/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Reductions into a Java array ({@code @Reduce float[]}) rather than a TornadoVM native array. The
 * compiler must lower them like any reduction: compiled as a plain parallel loop, every thread
 * writes the same element and the result is wrong.
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsJavaArrays
 * </code>
 */
public class TestReductionsJavaArrays extends TornadoTestBase {

    private static final int SIZE = 1 << 18;

    private static void sumFloats(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    private static void maxFloats(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.max(result[0], input[i]);
        }
    }

    private static void sumInts(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    private static void minInts(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.min(result[0], input[i]);
        }
    }

    @Test
    public void testSumFloats() throws TornadoExecutionPlanException {
        float[] input = new float[SIZE];
        float[] result = new float[1];
        for (int i = 0; i < SIZE; i++) {
            input[i] = i % 10;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsJavaArrays::sumFloats, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        float expected = 0;
        for (float v : input) {
            expected += v;
        }
        assertEquals(expected, result[0], 0.01f);
    }

    @Test
    public void testMaxFloats() throws TornadoExecutionPlanException {
        float[] input = new float[SIZE];
        float[] result = { Float.NEGATIVE_INFINITY };
        for (int i = 0; i < SIZE; i++) {
            input[i] = (i * 7919) % 100003;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsJavaArrays::maxFloats, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        float expected = Float.NEGATIVE_INFINITY;
        for (float v : input) {
            expected = Math.max(expected, v);
        }
        assertEquals(expected, result[0], 0.0f);
    }

    @Test
    public void testSumInts() throws TornadoExecutionPlanException {
        int[] input = new int[SIZE];
        int[] result = new int[1];
        for (int i = 0; i < SIZE; i++) {
            input[i] = i % 7 - 3;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsJavaArrays::sumInts, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        int expected = 0;
        for (int v : input) {
            expected += v;
        }
        assertEquals(expected, result[0]);
    }

    @Test
    public void testMinInts() throws TornadoExecutionPlanException {
        int[] input = new int[SIZE];
        int[] result = { Integer.MAX_VALUE };
        for (int i = 0; i < SIZE; i++) {
            input[i] = (i * 7919) % 100003 - 50000;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsJavaArrays::minInts, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        int expected = Integer.MAX_VALUE;
        for (int v : input) {
            expected = Math.min(expected, v);
        }
        assertEquals(expected, result[0]);
    }
}
