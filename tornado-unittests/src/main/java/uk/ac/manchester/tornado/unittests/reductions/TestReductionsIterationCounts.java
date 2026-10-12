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
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Reductions whose number of iterations is not a power of two, or whose loop does not start at 0.
 * On a GPU these are launched on the next power of two, and threads past the loop bound
 * contribute the reduction's neutral element. Each case runs several times, since the defects it
 * guards against (stale local-memory slots) show up intermittently.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionsIterationCounts
 * </code>
 */
public class TestReductionsIterationCounts extends TornadoTestBase {

    private static final int REPETITIONS = 5;

    /** Terms computed from the index only: {@code (i % 5) - 2}, small integers so float sums are exact. */
    public static void sumOfIndexTerms(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + ((i % 5) - 2));
        }
    }

    public static void sumOfIndexTermsFrom1(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 1; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i) + ((i % 5) - 2));
        }
    }

    public static void sumFrom1(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 1; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    public static void sumFrom5(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 5; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    public static void sumIntsFrom3(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 3; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    public static void productInts(IntArray input, @Reduce IntArray result) {
        result.set(0, 1);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) * input.get(i));
        }
    }

    public static void minLongs(LongArray input, @Reduce LongArray result) {
        result.set(0, Long.MAX_VALUE);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, TornadoMath.min(result.get(0), input.get(i)));
        }
    }

    public static void maxFrom2(FloatArray input, @Reduce FloatArray result) {
        result.set(0, -1.0f);
        for (@Parallel int i = 2; i < input.getSize(); i++) {
            result.set(0, TornadoMath.max(result.get(0), input.get(i)));
        }
    }

    public static void sumOfDoubleIndexTerms(DoubleArray input, @Reduce DoubleArray result) {
        result.set(0, 0.0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i) + 1.0 / (i + 1));
        }
    }

    /** Values i % 7 + 1: small integers, so float sums are exact and every element matters. */
    private static FloatArray values(int n) {
        FloatArray a = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, i % 7 + 1);
        }
        return a;
    }

    private static float runFloat(TornadoFunctions.Task2<FloatArray, FloatArray> kernel, FloatArray input, float neutral) throws TornadoExecutionPlanException {
        FloatArray result = new FloatArray(1);
        // The runtime takes the neutral element from the result's value at execution.
        result.init(neutral);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", kernel, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        return result.get(0);
    }

    private static void checkIndexTerms(int n) throws TornadoExecutionPlanException {
        float expected = 0;
        for (int i = 0; i < n; i++) {
            expected += (i % 5) - 2;
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            assertEquals("n " + n + ", run " + rep, expected, runFloat(TestReductionsIterationCounts::sumOfIndexTerms, new FloatArray(n), 0.0f), 0.0f);
        }
    }

    private static void checkSumFrom(TornadoFunctions.Task2<FloatArray, FloatArray> kernel, int start, int n) throws TornadoExecutionPlanException {
        FloatArray input = values(n);
        float expected = 0;
        for (int i = start; i < n; i++) {
            expected += input.get(i);
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            assertEquals("start " + start + ", n " + n + ", run " + rep, expected, runFloat(kernel, input, 0.0f), 0.0f);
        }
    }

    @Test
    public void testIndexTermsNonPowerOfTwo() throws TornadoExecutionPlanException {
        // One iteration past a power of two: the worst case for padding.
        checkIndexTerms(4097);
    }

    @Test
    public void testIndexTermsSmallCounts() throws TornadoExecutionPlanException {
        checkIndexTerms(1);
        checkIndexTerms(3);
        checkIndexTerms(257);
    }

    @Test
    public void testIndexTermsFrom1() throws TornadoExecutionPlanException {
        // 32767 iterations from index 1, the shape of TestReductionsFloats#testComputePi.
        final int n = 32768;
        FloatArray input = values(n);
        float expected = 0;
        for (int i = 1; i < n; i++) {
            expected += input.get(i) + ((i % 5) - 2);
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            assertEquals("run " + rep, expected, runFloat(TestReductionsIterationCounts::sumOfIndexTermsFrom1, input, 0.0f), 0.0f);
        }
    }

    @Test
    public void testSumFrom1() throws TornadoExecutionPlanException {
        checkSumFrom(TestReductionsIterationCounts::sumFrom1, 1, 32768);
        checkSumFrom(TestReductionsIterationCounts::sumFrom1, 1, 32769);
        checkSumFrom(TestReductionsIterationCounts::sumFrom1, 1, 1000);
    }

    @Test
    public void testSumFrom5() throws TornadoExecutionPlanException {
        checkSumFrom(TestReductionsIterationCounts::sumFrom5, 5, 32768);
        checkSumFrom(TestReductionsIterationCounts::sumFrom5, 5, 4096);
    }

    @Test
    public void testSumIntsFrom3() throws TornadoExecutionPlanException {
        final int n = 65536;
        IntArray input = new IntArray(n);
        for (int i = 0; i < n; i++) {
            input.set(i, i % 11 - 5);
        }
        int expected = 0;
        for (int i = 3; i < n; i++) {
            expected += input.get(i);
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            IntArray result = new IntArray(1);
            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                    .task("t0", TestReductionsIterationCounts::sumIntsFrom3, input, result) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.execute();
            }
            assertEquals("run " + rep, expected, result.get(0));
        }
    }

    @Test
    public void testProductNonPowerOfTwo() throws TornadoExecutionPlanException {
        // 1025 elements, 2 at every multiple of 128 (9 of them), 1 elsewhere: product 512.
        final int n = 1025;
        IntArray input = new IntArray(n);
        for (int i = 0; i < n; i++) {
            input.set(i, i % 128 == 0 ? 2 : 1);
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            IntArray result = new IntArray(1);
            // The runtime takes the neutral element from the result's value at execution.
            result.init(1);
            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                    .task("t0", TestReductionsIterationCounts::productInts, input, result) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.execute();
            }
            assertEquals("run " + rep, 512, result.get(0));
        }
    }

    @Test
    public void testMinLongsNonPowerOfTwo() throws TornadoExecutionPlanException {
        final int n = 3001;
        LongArray input = new LongArray(n);
        for (int i = 0; i < n; i++) {
            input.set(i, 1_000_000_000_000L + (i * 7919L) % 3001);
        }
        long expected = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            expected = Math.min(expected, input.get(i));
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            LongArray result = new LongArray(1);
            // The runtime takes the neutral element from the result's value at execution.
            result.init(Long.MAX_VALUE);
            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                    .task("t0", TestReductionsIterationCounts::minLongs, input, result) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.execute();
            }
            assertEquals("run " + rep, expected, result.get(0));
        }
    }

    @Test
    public void testMaxFrom2() throws TornadoExecutionPlanException {
        final int n = 16384;
        FloatArray input = values(n);
        // The largest values sit before the loop start and must be ignored.
        input.set(0, 1000.0f);
        input.set(1, 1000.0f);
        for (int rep = 0; rep < REPETITIONS; rep++) {
            assertEquals("run " + rep, 7.0f, runFloat(TestReductionsIterationCounts::maxFrom2, input, -1.0f), 0.0f);
        }
    }

    @Test
    public void testDoubleIndexTermsNonPowerOfTwo() throws TornadoExecutionPlanException {
        // Metal has no double type.
        assertNotBackend(TornadoVMBackendType.METAL);
        final int n = 5000;
        DoubleArray input = new DoubleArray(n);
        input.init(1.0);
        double expected = 0;
        for (int i = 0; i < n; i++) {
            expected += 1.0 + 1.0 / (i + 1);
        }
        for (int rep = 0; rep < REPETITIONS; rep++) {
            DoubleArray result = new DoubleArray(1);
            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                    .task("t0", TestReductionsIterationCounts::sumOfDoubleIndexTerms, input, result) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.execute();
            }
            assertEquals("run " + rep, expected, result.get(0), 1e-9);
        }
    }
}
