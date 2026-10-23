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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Reductions submitted as method tasks ({@code TaskGraph.task(id, Method, args)}), whose parameter
 * list holds only the kernel's arguments (no lambda in slot 0).
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsMethodTask
 * </code>
 */
public class TestReductionsMethodTask extends TornadoTestBase {

    public static void sum(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    public static void maxFrom1(IntArray input, @Reduce IntArray result) {
        result.set(0, Integer.MIN_VALUE);
        for (@Parallel int i = 1; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i)));
        }
    }

    public static void sumJavaArray(float[] input, @Reduce float[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    private static Method kernel(String name, Class<?>... types) throws NoSuchMethodException {
        return TestReductionsMethodTask.class.getMethod(name, types);
    }

    private static void run(Method kernel, Object input, Object result) throws TornadoExecutionPlanException {
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", kernel, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    @Test
    public void testSumNonPowerOfTwo() throws Exception {
        int n = 4097;
        FloatArray input = new FloatArray(n);
        float expected = 0;
        for (int i = 0; i < n; i++) {
            input.set(i, i % 7 + 1);
            expected += input.get(i);
        }
        FloatArray result = new FloatArray(1);
        run(kernel("sum", FloatArray.class, FloatArray.class), input, result);
        assertEquals(expected, result.get(0), 0.0f);
    }

    @Test
    public void testMaxFrom1() throws Exception {
        int n = 3001;
        IntArray input = new IntArray(n);
        int expected = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            input.set(i, (i * 37) % 1009 - (i == 0 ? -5000 : 0));
            if (i >= 1) {
                expected = Math.max(expected, input.get(i));
            }
        }
        IntArray result = new IntArray(1);
        run(kernel("maxFrom1", IntArray.class, IntArray.class), input, result);
        assertEquals(expected, result.get(0));
    }

    @Test
    public void testSumJavaArray() throws Exception {
        int n = 8192;
        float[] input = new float[n];
        float expected = 0;
        for (int i = 0; i < n; i++) {
            input[i] = i % 5;
            expected += input[i];
        }
        float[] result = new float[1];
        run(kernel("sumJavaArray", float[].class, float[].class), input, result);
        assertEquals(expected, result[0], 0.0f);
    }
}
