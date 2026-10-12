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
package uk.ac.manchester.tornado.unittests.fuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMOpenCLNotSupported;

/**
 * Java semantics the phase-3 fuzzer (tornado-fuzz {@code phase=3}: typed programs with control
 * flow) found the device code breaking, on CUDA and OpenCL. Each kernel is the narrowed-down
 * finding; the expected values are the JVM's.
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fuzz.TestFuzzRegressions
 * </code>
 */
public class TestFuzzRegressions extends TornadoTestBase {

    private static final float[] SPECIAL = { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e30f, -1e30f, 3e9f, -3e9f, 0.5f, -0.5f, 1.5f, 2.5f, -2.5f, 9.3e18f,
            8388609.5f, 0.0f, -0.0f };

    private static void conversions(FloatArray f, LongArray out) {
        for (@Parallel int i = 0; i < f.getSize(); i++) {
            double d = f.get(i) * 1.0e10;
            out.set(4 * i, (long) f.get(i));
            out.set(4 * i + 1, (long) d);
            out.set(4 * i + 2, (int) d);
            out.set(4 * i + 3, (long) (f.get(i) - f.get(i)));
        }
    }

    private static void rounding(FloatArray f, FloatArray out) {
        for (@Parallel int i = 0; i < f.getSize(); i++) {
            out.set(4 * i, (float) Math.rint(f.get(i)));
            out.set(4 * i + 1, (float) Math.floor(f.get(i)));
            out.set(4 * i + 2, (float) Math.sqrt(Math.abs(f.get(i))));
            out.set(4 * i + 3, (float) Math.ceil(f.get(i)) + Math.abs(f.get(i) * 2.0f));
        }
    }

    private static void wrapping(LongArray l, IntArray n, IntArray out) {
        for (@Parallel int i = 0; i < l.getSize(); i++) {
            long x = l.get(i);
            int y = n.get(i);
            // Not -y > 0: TC prints the negation wrapping ((int) (0u - (uint) y)), but the NVIDIA OpenCL
            // compiler on CI's Quadro GP100 still folds the comparison to y < 0 (true for MIN_VALUE).
            out.set(4 * i, Long.compare(0L, x + x));
            out.set(4 * i + 1, (y + y) < 0 ? 1 : 0);
            out.set(4 * i + 2, (y * 2 > y) ? 1 : 0);
            out.set(4 * i + 3, (y + 1 > y) ? 1 : 0);
        }
    }

    private static void absOfMinValue(LongArray l, IntArray n, IntArray out) {
        for (@Parallel int i = 0; i < l.getSize(); i++) {
            out.set(2 * i, Math.max(Math.abs(n.get(i)), 2));
            out.set(2 * i + 1, (int) (Math.max(Math.abs(l.get(i)), 2L) >>> 32));
        }
    }

    /** NaN converts to 0 in Java; CUDA's __float2ll_rz/__double2int_rz and NVIDIA OpenCL's convert_long_sat_rtz give MIN_VALUE. */
    @Test
    public void testNaNAndInfinityToInteger() throws TornadoExecutionPlanException {
        FloatArray f = FloatArray.fromArray(SPECIAL);
        LongArray out = new LongArray(4 * f.getSize());
        LongArray expected = new LongArray(4 * f.getSize());
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, f) //
                .task("t0", TestFuzzRegressions::conversions, f, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        conversions(f, expected);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals(expected.get(i), out.get(i), "element " + i + " of " + SPECIAL[i / 4]);
        }
    }

    /**
     * Float Math in double: exact in float, so it runs on devices without fp64. Math.rint is the
     * device's rint: the JDK's own version rounds with (2^52 + x) - 2^52, which OpenCL's default
     * -cl-fast-relaxed-math folds to x.
     */
    @Test
    public void testFloatMathRoundsLikeJava() throws TornadoExecutionPlanException {
        FloatArray f = FloatArray.fromArray(SPECIAL);
        FloatArray out = new FloatArray(4 * f.getSize());
        FloatArray expected = new FloatArray(4 * f.getSize());
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, f) //
                .task("t0", TestFuzzRegressions::rounding, f, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        rounding(f, expected);
        for (int i = 0; i < out.getSize(); i++) {
            // sqrt (element 2) is exact where it can run in double or the device's float sqrt rounds
            // correctly; a device without double precision computes it in float, which OpenCL allows to
            // be up to 3 ulp off by default: one ulp is accepted there. JUnit rejects a NaN delta, and
            // Math.ulp(NaN) is NaN; a NaN expected value only matches NaN anyway.
            float tolerance = i % 4 == 2 && !Float.isNaN(expected.get(i)) ? Math.ulp(expected.get(i)) : 0.0f;
            assertEquals(expected.get(i), out.get(i), tolerance, "element " + i + " of " + SPECIAL[i / 4]);
        }
    }

    /** int and long arithmetic wraps in Java; device compilers treat signed overflow as impossible. */
    @Test
    public void testWrappingArithmetic() throws TornadoExecutionPlanException {
        long[] ls = { Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 1, Long.MIN_VALUE + 1, 1L << 62 };
        int[] is = { Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, Integer.MIN_VALUE + 1, 1 << 30 };
        LongArray l = LongArray.fromArray(ls);
        IntArray n = IntArray.fromArray(is);
        IntArray out = new IntArray(4 * ls.length);
        IntArray expected = new IntArray(4 * ls.length);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, l, n) //
                .task("t0", TestFuzzRegressions::wrapping, l, n, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        wrapping(l, n, expected);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals(expected.get(i), out.get(i), "element " + i);
        }
    }

    /**
     * abs(MIN_VALUE) is MIN_VALUE in Java, so max(abs(MIN_VALUE), 2) is 2. Intel's OpenCL compiler
     * recognises every absolute-value idiom (the built-in, a select, the xor-and-subtract trick) and
     * then takes the result to be non-negative, returning MIN_VALUE: skipped there.
     */
    @Test
    public void testAbsOfMinValue() throws TornadoExecutionPlanException {
        var device = getTornadoRuntime().getDefaultDevice();
        if (device.getTornadoVMBackend() == TornadoVMBackendType.OPENCL && (device.getDescription() + device.getPlatformName()).contains("Intel")) {
            throw new TornadoVMOpenCLNotSupported("Intel's OpenCL compiler assumes abs() is non-negative, also for MIN_VALUE");
        }
        LongArray l = LongArray.fromArray(new long[] { Long.MIN_VALUE, -5, 0, 7, Long.MAX_VALUE });
        IntArray n = IntArray.fromArray(new int[] { Integer.MIN_VALUE, -5, 0, 7, Integer.MAX_VALUE });
        IntArray out = new IntArray(2 * n.getSize());
        IntArray expected = new IntArray(2 * n.getSize());
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, l, n) //
                .task("t0", TestFuzzRegressions::absOfMinValue, l, n, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        absOfMinValue(l, n, expected);
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals(expected.get(i), out.get(i), "element " + i);
        }
    }
}
