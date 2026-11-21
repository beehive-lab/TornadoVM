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
package uk.ac.manchester.tornado.unittests.tc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Comparisons of a value whose sign is known except for NaN ({@code Math.abs(x)}, {@code x * x},
 * sums of squares, negated absolute values) with a constant on the other side of zero. Java answers
 * them by the value, so a NaN operand makes an ordered comparison false. NVIDIA's PTX assembler folds
 * {@code fabsf(x) > -1.0f} and {@code x * x > -1.0f} to true for float (found by tornado-fuzz, seed
 * 100953); TC decides these comparisons itself (Canonicalizer) and the device code must match the JVM.
 * The plans are strict: OpenCL's default {@code -cl-fast-relaxed-math} lets the compiler assume no NaN.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tc.TestNaNSignComparisons
 * </code>
 */
public class TestNaNSignComparisons extends TornadoTestBase {

    // No subnormals: Apple GPUs flush float32 subnormals to zero even with strict floating point, so
    // abs(MIN_VALUE) > 0 is false there for a reason unrelated to NaN.
    private static final float[] FLOATS = { Float.NaN, -Float.NaN, 0.0f, -0.0f, 1.5f, -1.5f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL, -3.0e38f };
    private static final int CASES = 11;

    /** Each comparison as one bit of the result, so one kernel checks them all. */
    private static int bits(float x, float y) {
        int r = 0;
        r |= (Math.abs(x) > -1.0f ? 1 : 0);
        r |= (Math.abs(x) >= 0.0f ? 1 : 0) << 1;
        r |= (-1.0f < Math.abs(x) ? 1 : 0) << 2;
        r |= (-Math.abs(x) < 1.0f ? 1 : 0) << 3;
        r |= (x * x > -1.0f ? 1 : 0) << 4;
        r |= (x * x + y * y < -4.0f ? 1 : 0) << 5;
        r |= (!(Math.abs(x) <= -1.0f) ? 1 : 0) << 6;
        r |= (Math.abs(Math.min(x, x)) > -1.0f ? 1 : 0) << 7;
        r |= (Math.abs(x) > 0.0f ? 1 : 0) << 8;
        // the other side is not a constant in the IR, but the device compiler evaluates it (seed 301998
        // had -(float) Math.log(3.4e38f); sqrt keeps the kernel free of fp64)
        r |= (-((float) Math.sqrt(4.0f)) < Math.abs((float) Math.sqrt(Math.abs(x))) ? 1 : 0) << 9;
        r |= (x * x > -y * y - 1.0f ? 1 : 0) << 10;
        return r;
    }

    public static void parallelFloats(FloatArray x, FloatArray y, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, bits(x.get(i), y.get(i)));
        }
    }

    public static void contextFloats(KernelContext context, FloatArray x, FloatArray y, IntArray out) {
        int i = context.globalIdx;
        if (i < out.getSize()) {
            out.set(i, bits(x.get(i), y.get(i)));
        }
    }

    public static void parallelDoubles(DoubleArray x, IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            double v = x.get(i);
            int r = 0;
            r |= (Math.abs(v) > -1.0 ? 1 : 0);
            r |= (Math.sqrt(v * v) >= 0.0 ? 1 : 0) << 1;
            r |= (v * v < -1.0 ? 1 : 0) << 2;
            out.set(i, r);
        }
    }

    private static FloatArray floats(boolean second) {
        FloatArray a = new FloatArray(FLOATS.length);
        for (int i = 0; i < FLOATS.length; i++) {
            a.set(i, second ? FLOATS[FLOATS.length - 1 - i] : FLOATS[i]);
        }
        return a;
    }

    private static void check(IntArray expected, IntArray actual) {
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(Integer.toBinaryString(expected.get(i)), Integer.toBinaryString(actual.get(i)), "input " + FLOATS[i] + " (bits: one per comparison, " + CASES + " cases)");
        }
    }

    @Test
    public void testParallelFloatComparisons() throws TornadoExecutionPlanException {
        FloatArray x = floats(false);
        FloatArray y = floats(true);
        IntArray out = new IntArray(FLOATS.length);
        IntArray expected = new IntArray(FLOATS.length);
        parallelFloats(x, y, expected);
        TaskGraph graph = new TaskGraph("s0").transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .task("t0", TestNaNSignComparisons::parallelFloats, x, y, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withStrictFloatingPoint().execute();
        }
        check(expected, out);
    }

    @Test
    public void testKernelContextFloatComparisons() throws TornadoExecutionPlanException {
        FloatArray x = floats(false);
        FloatArray y = floats(true);
        IntArray out = new IntArray(FLOATS.length);
        IntArray expected = new IntArray(FLOATS.length);
        parallelFloats(x, y, expected);
        KernelContext context = new KernelContext();
        var grid = new uk.ac.manchester.tornado.api.WorkerGrid1D(FLOATS.length);
        var scheduler = new uk.ac.manchester.tornado.api.GridScheduler("s1.t0", grid);
        TaskGraph graph = new TaskGraph("s1").transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .task("t0", TestNaNSignComparisons::contextFloats, context, x, y, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withStrictFloatingPoint().withGridScheduler(scheduler).execute();
        }
        check(expected, out);
    }

    @Test
    public void testParallelDoubleComparisons() throws TornadoExecutionPlanException {
        DoubleArray x = new DoubleArray(FLOATS.length);
        for (int i = 0; i < FLOATS.length; i++) {
            x.set(i, FLOATS[i]);
        }
        IntArray out = new IntArray(FLOATS.length);
        IntArray expected = new IntArray(FLOATS.length);
        parallelDoubles(x, expected);
        TaskGraph graph = new TaskGraph("s2").transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("t0", TestNaNSignComparisons::parallelDoubles, x, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withStrictFloatingPoint().execute();
        }
        check(expected, out);
    }
}
