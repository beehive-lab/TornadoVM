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
package uk.ac.manchester.tornado.unittests.codegen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Float and double bit reinterpretation ({@link Float#floatToRawIntBits}, {@link Float#intBitsToFloat},
 * {@link Double#doubleToRawLongBits} and {@link Double#longBitsToDouble})
 * and float comparisons on NaNs, infinities and signed zeros, which must follow Java semantics: a
 * comparison with a NaN operand is false, so its negation is true. Subnormals are left out, since
 * devices may flush them to zero.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.codegen.TestFloatBitsAndComparisons
 * </code>
 */
public class TestFloatBitsAndComparisons extends TornadoTestBase {

    private static final int N = 256;

    private static final float[] SPECIALS = { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.0f, -0.0f, 1.0f, -1.0f, Float.MIN_NORMAL, Float.MAX_VALUE, 2.5f };

    private static final double[] DOUBLE_SPECIALS = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, 1.0, -1.0, Double.MIN_NORMAL, Double.MAX_VALUE, 2.5 };

    public static void floatBits(KernelContext context, FloatArray a, IntArray bits, FloatArray negated, int n) {
        int i = context.globalIdx;
        if (i < n) {
            int b = Float.floatToRawIntBits(a.get(i));
            bits.set(i, b);
            negated.set(i, Float.intBitsToFloat(b ^ 0x80000000));
        }
    }

    public static void doubleBits(KernelContext context, DoubleArray a, LongArray bits, DoubleArray negated, int n) {
        int i = context.globalIdx;
        if (i < n) {
            long b = Double.doubleToRawLongBits(a.get(i));
            bits.set(i, b);
            negated.set(i, Double.longBitsToDouble(b ^ 0x8000000000000000L));
        }
    }

    /** One bit per comparison: 1 less, 2 not less, 4 greater-or-equal, 8 less-or-equal, 16 not greater, 32 equal. */
    public static void compare(KernelContext context, FloatArray a, FloatArray b, IntArray out, int n) {
        int i = context.globalIdx;
        if (i < n) {
            float x = a.get(i);
            float y = b.get(i);
            int m = 0;
            if (x < y) {
                m |= 1;
            }
            if (!(x < y)) {
                m |= 2;
            }
            if (x >= y) {
                m |= 4;
            }
            if (x <= y) {
                m |= 8;
            }
            if (!(x > y)) {
                m |= 16;
            }
            if (x == y) {
                m |= 32;
            }
            out.set(i, m);
        }
    }

    private static int reference(float x, float y) {
        return (x < y ? 1 : 0) | (!(x < y) ? 2 : 0) | (x >= y ? 4 : 0) | (x <= y ? 8 : 0) | (!(x > y) ? 16 : 0) | (x == y ? 32 : 0);
    }

    private static void execute(TaskGraph g) throws TornadoExecutionPlanException {
        WorkerGrid1D grid = new WorkerGrid1D(N);
        grid.setLocalWork(64, 1, 1);
        GridScheduler gs = new GridScheduler(g.getTaskGraphName() + ".t", grid);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withGridScheduler(gs).execute();
        }
    }

    @Test
    public void testFloatBits() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i < SPECIALS.length ? SPECIALS[i] : (i - 100) * 0.37f);
        }
        IntArray bits = new IntArray(N);
        FloatArray negated = new FloatArray(N);
        TaskGraph g = new TaskGraph("bits").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t", TestFloatBitsAndComparisons::floatBits, new KernelContext(), a, bits, negated, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bits, negated);
        execute(g);
        for (int i = 0; i < N; i++) {
            int expected = Float.floatToRawIntBits(a.get(i));
            assertEquals("bits of element " + i, expected, bits.get(i));
            assertEquals("negated bits of element " + i, expected ^ 0x80000000, Float.floatToRawIntBits(negated.get(i)));
        }
    }

    @Test
    public void testDoubleBits() throws TornadoExecutionPlanException {
        // Metal has no double type.
        assertNotBackend(TornadoVMBackendType.METAL);
        DoubleArray a = new DoubleArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i < DOUBLE_SPECIALS.length ? DOUBLE_SPECIALS[i] : (i - 100) * 0.37);
        }
        LongArray bits = new LongArray(N);
        DoubleArray negated = new DoubleArray(N);
        TaskGraph g = new TaskGraph("dbits").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t", TestFloatBitsAndComparisons::doubleBits, new KernelContext(), a, bits, negated, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bits, negated);
        execute(g);
        for (int i = 0; i < N; i++) {
            long expected = Double.doubleToRawLongBits(a.get(i));
            assertEquals("bits of element " + i, expected, bits.get(i));
            assertEquals("negated bits of element " + i, expected ^ 0x8000000000000000L, Double.doubleToRawLongBits(negated.get(i)));
        }
    }

    @Test
    public void testComparisonsWithSpecialValues() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        FloatArray b = new FloatArray(N);
        int k = SPECIALS.length;
        for (int i = 0; i < N; i++) {
            a.set(i, SPECIALS[i % k]);
            b.set(i, SPECIALS[(i / k) % k]);
        }
        IntArray out = new IntArray(N);
        TaskGraph g = new TaskGraph("cmp").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t", TestFloatBitsAndComparisons::compare, new KernelContext(), a, b, out, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        execute(g);
        for (int i = 0; i < N; i++) {
            assertEquals("comparisons of " + a.get(i) + " and " + b.get(i), reference(a.get(i), b.get(i)), out.get(i));
        }
    }
}
