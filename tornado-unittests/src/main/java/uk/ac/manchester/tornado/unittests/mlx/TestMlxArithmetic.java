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
package uk.ac.manchester.tornado.unittests.mlx;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX arithmetic library tasks: element-wise arithmetic, exponential,
 * logarithmic, trigonometric and hyperbolic functions, rounding, clip, where, nan_to_num and complex
 * parts. Each test checks the task against a sequential Java reference. Skipped unless the default
 * device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxArithmetic
 * </code>
 */
public class TestMlxArithmetic extends MlxTestBase {

    private static final int SIZE = 1027;

    /** Runs {@code mlx(a, out)} on SIZE values in [lo, hi) and checks it against {@code ref}. */
    private static void unary(LibraryTask2<FloatArray, FloatArray> mlx, DoubleUnaryOperator ref, float lo, float hi, double relTol, double absTol) throws TornadoExecutionPlanException {
        float[] av = values(SIZE, lo, hi, 1);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("unary", mlx, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertClose("x=" + av[i], i, ref.applyAsDouble(av[i]), output.get(i), relTol, absTol);
        }
    }

    /** Runs {@code mlx(a, b, c)} on SIZE value pairs and checks it against {@code ref}. */
    private static void binary(LibraryTask3<FloatArray, FloatArray, FloatArray> mlx, DoubleBinaryOperator ref, float aLo, float aHi, float bLo, float bHi, double relTol,
            double absTol) throws TornadoExecutionPlanException {
        float[] av = values(SIZE, aLo, aHi, 2);
        float[] bv = values(SIZE, bLo, bHi, 3);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("binary", mlx, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertClose("x=" + av[i] + " y=" + bv[i], i, ref.applyAsDouble(av[i], bv[i]), output.get(i), relTol, absTol);
        }
    }

    /** erf with |error| &lt; 1.2e-7 (Numerical Recipes erfc Chebyshev fit). */
    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double y = t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223
                + t * 0.17087277)))))))));
        return x >= 0 ? 1.0 - y : y - 1.0;
    }

    /** erf^-1 by Newton's method on {@link #erf}. */
    private static double erfinv(double y) {
        double x = 0;
        for (int k = 0; k < 60; k++) {
            x -= (erf(x) - y) / (2 / Math.sqrt(Math.PI) * Math.exp(-x * x));
        }
        return x;
    }

    private static double logaddexp(double x, double y) {
        return Math.max(x, y) + Math.log1p(Math.exp(-Math.abs(x - y)));
    }

    private static int[] ints(int n, int lo, int hi, long seed) {
        Random random = new Random(seed);
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = lo + random.nextInt(hi - lo);
        }
        return v;
    }

    // ---------------------------------------------------------------- Mlx

    @Test
    public void testAdd() throws TornadoExecutionPlanException {
        binary(Mlx::add, (x, y) -> x + y, -10, 10, -10, 10, 1e-6, 1e-7);
    }

    @Test
    public void testSubtract() throws TornadoExecutionPlanException {
        binary(Mlx::subtract, (x, y) -> x - y, -10, 10, -10, 10, 1e-6, 1e-7);
    }

    @Test
    public void testMultiply() throws TornadoExecutionPlanException {
        binary(Mlx::multiply, (x, y) -> x * y, -10, 10, -10, 10, 1e-6, 1e-7);
    }

    @Test
    public void testDivide() throws TornadoExecutionPlanException {
        binary(Mlx::divide, (x, y) -> x / y, -10, 10, 0.5f, 10, 1e-6, 1e-7);
    }

    @Test
    public void testMaximum() throws TornadoExecutionPlanException {
        binary(Mlx::maximum, Math::max, -10, 10, -10, 10, 0, 0);
    }

    @Test
    public void testMinimum() throws TornadoExecutionPlanException {
        binary(Mlx::minimum, Math::min, -10, 10, -10, 10, 0, 0);
    }

    @Test
    public void testNegative() throws TornadoExecutionPlanException {
        unary(Mlx::negative, x -> -x, -10, 10, 0, 0);
    }

    @Test
    public void testSquare() throws TornadoExecutionPlanException {
        unary(Mlx::square, x -> x * x, -10, 10, 1e-6, 1e-7);
    }

    @Test
    public void testSqrt() throws TornadoExecutionPlanException {
        unary(Mlx::sqrt, Math::sqrt, 0, 100, 2e-6, 1e-6);
    }

    @Test
    public void testRsqrt() throws TornadoExecutionPlanException {
        unary(Mlx::rsqrt, x -> 1.0 / Math.sqrt(x), 0.01f, 100, 2e-6, 1e-6);
    }

    @Test
    public void testExp() throws TornadoExecutionPlanException {
        unary(Mlx::exp, Math::exp, -8, 8, 2e-6, 1e-6);
    }

    @Test
    public void testTanh() throws TornadoExecutionPlanException {
        unary(Mlx::tanh, Math::tanh, -5, 5, 2e-6, 1e-6);
    }

    @Test
    public void testErf() throws TornadoExecutionPlanException {
        unary(Mlx::erf, TestMlxArithmetic::erf, -3, 3, 2e-6, 1e-6);
    }

    @Test
    public void testSigmoid() throws TornadoExecutionPlanException {
        unary(Mlx::sigmoid, x -> 1.0 / (1.0 + Math.exp(-x)), -10, 10, 2e-6, 1e-6);
    }

    // ---------------------------------------------------------------- MlxMath

    @Test
    public void testAbs() throws TornadoExecutionPlanException {
        unary(Mlx::abs, Math::abs, -10, 10, 0, 0);
    }

    @Test
    public void testSign() throws TornadoExecutionPlanException {
        unary(Mlx::sign, Math::signum, -10, 10, 0, 0);
    }

    @Test
    public void testCeil() throws TornadoExecutionPlanException {
        unary(Mlx::ceil, Math::ceil, -10, 10, 0, 0);
    }

    @Test
    public void testFloor() throws TornadoExecutionPlanException {
        unary(Mlx::floor, Math::floor, -10, 10, 0, 0);
    }

    @Test
    public void testReciprocal() throws TornadoExecutionPlanException {
        unary(Mlx::reciprocal, x -> 1.0 / x, 0.5f, 10, 1e-6, 1e-7);
    }

    @Test
    public void testExpm1() throws TornadoExecutionPlanException {
        unary(Mlx::expm1, Math::expm1, -5, 5, 3e-5, 1e-6);
    }

    @Test
    public void testLog() throws TornadoExecutionPlanException {
        unary(Mlx::log, Math::log, 0.01f, 10, 2e-6, 1e-6);
    }

    @Test
    public void testLog1p() throws TornadoExecutionPlanException {
        unary(Mlx::log1p, Math::log1p, -0.9f, 10, 1e-5, 1e-6);
    }

    @Test
    public void testLog2() throws TornadoExecutionPlanException {
        unary(Mlx::log2, x -> Math.log(x) / Math.log(2), 0.01f, 10, 2e-6, 1e-6);
    }

    @Test
    public void testLog10() throws TornadoExecutionPlanException {
        unary(Mlx::log10, Math::log10, 0.01f, 10, 2e-6, 1e-6);
    }

    @Test
    public void testSin() throws TornadoExecutionPlanException {
        unary(Mlx::sin, Math::sin, -10, 10, 2e-6, 1e-6);
    }

    @Test
    public void testCos() throws TornadoExecutionPlanException {
        unary(Mlx::cos, Math::cos, -10, 10, 2e-6, 1e-6);
    }

    @Test
    public void testTan() throws TornadoExecutionPlanException {
        unary(Mlx::tan, Math::tan, -1.5f, 1.5f, 1e-5, 1e-6);
    }

    @Test
    public void testArcsin() throws TornadoExecutionPlanException {
        unary(Mlx::arcsin, Math::asin, -1, 1, 2e-6, 1e-6);
    }

    @Test
    public void testArccos() throws TornadoExecutionPlanException {
        unary(Mlx::arccos, Math::acos, -1, 1, 2e-6, 1e-6);
    }

    @Test
    public void testArctan() throws TornadoExecutionPlanException {
        unary(Mlx::arctan, Math::atan, -10, 10, 2e-6, 1e-6);
    }

    @Test
    public void testSinh() throws TornadoExecutionPlanException {
        unary(Mlx::sinh, Math::sinh, -5, 5, 1e-5, 1e-6);
    }

    @Test
    public void testCosh() throws TornadoExecutionPlanException {
        unary(Mlx::cosh, Math::cosh, -5, 5, 2e-6, 1e-6);
    }

    @Test
    public void testArcsinh() throws TornadoExecutionPlanException {
        unary(Mlx::arcsinh, x -> Math.log(x + Math.sqrt(x * x + 1)), -10, 10, 1e-5, 1e-6);
    }

    @Test
    public void testArccosh() throws TornadoExecutionPlanException {
        unary(Mlx::arccosh, x -> Math.log(x + Math.sqrt(x * x - 1)), 1, 10, 2e-6, 1e-6);
    }

    @Test
    public void testArctanh() throws TornadoExecutionPlanException {
        unary(Mlx::arctanh, x -> 0.5 * Math.log((1 + x) / (1 - x)), -0.99f, 0.99f, 1e-5, 1e-6);
    }

    @Test
    public void testDegrees() throws TornadoExecutionPlanException {
        unary(Mlx::degrees, Math::toDegrees, -10, 10, 1e-6, 1e-6);
    }

    @Test
    public void testRadians() throws TornadoExecutionPlanException {
        unary(Mlx::radians, Math::toRadians, -10, 10, 1e-6, 1e-7);
    }

    @Test
    public void testErfinv() throws TornadoExecutionPlanException {
        unary(Mlx::erfinv, TestMlxArithmetic::erfinv, -0.99f, 0.99f, 1e-5, 1e-6);
    }

    @Test
    public void testArctan2() throws TornadoExecutionPlanException {
        binary(Mlx::arctan2, Math::atan2, -10, 10, -10, 10, 2e-6, 1e-6);
    }

    @Test
    public void testPower() throws TornadoExecutionPlanException {
        binary(Mlx::power, Math::pow, 0.1f, 5, -3, 3, 1e-5, 1e-6);
    }

    @Test
    public void testLogaddexp() throws TornadoExecutionPlanException {
        binary(Mlx::logaddexp, TestMlxArithmetic::logaddexp, -10, 10, -10, 10, 2e-6, 1e-6);
    }

    @Test
    public void testRemainder() throws TornadoExecutionPlanException {
        binary(Mlx::remainder, (x, y) -> x - y * Math.floor(x / y), -10, 10, 0.5f, 10, 1e-5, 1e-5);
    }

    @Test
    public void testFloorDivide() throws TornadoExecutionPlanException {
        binary(Mlx::floorDivide, (x, y) -> Math.floor(x / y), -10, 10, 0.5f, 10, 0, 0);
    }

    @Test
    public void testDivmod() throws TornadoExecutionPlanException {
        float[] av = values(SIZE, -10, 10, 4);
        float[] bv = values(SIZE, 0.5f, 10, 5);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray quotient = new FloatArray(SIZE);
        FloatArray remainder = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("divmod", Mlx::divmod, a, b, quotient, remainder) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, quotient, remainder);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("quotient " + i, (int) (av[i] / bv[i]), quotient.get(i), 0f);
            assertClose("remainder", i, av[i] - bv[i] * Math.floor(av[i] / bv[i]), remainder.get(i), 1e-5, 1e-5);
        }
    }

    @Test
    public void testRound() throws TornadoExecutionPlanException {
        float[] av = values(SIZE, -100, 100, 6);
        float[] ties = { 0.5f, 1.5f, 2.5f, -0.5f, -1.5f, -2.5f, 3.5f, 4.5f };
        System.arraycopy(ties, 0, av, 0, ties.length);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray whole = new FloatArray(SIZE);
        FloatArray twoDecimals = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("round0", Mlx::round, a, whole, 0) //
                .libraryTask("round2", Mlx::round, a, twoDecimals, 2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, whole, twoDecimals);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("round(0) x=" + av[i], Math.rint(av[i]), whole.get(i), 0.0);
            assertClose("round(2) x=" + av[i], i, (float) Math.rint(av[i] * 100f) / 100f, twoDecimals.get(i), 1e-6, 1e-6);
        }
    }

    @Test
    public void testClip() throws TornadoExecutionPlanException {
        float[] av = values(SIZE, -10, 10, 7);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("clip", Mlx::clip, a, output, -2.5f, 4f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(Math.min(Math.max(av[i], -2.5f), 4f), output.get(i), 0f);
        }
    }

    @Test
    public void testWhere() throws TornadoExecutionPlanException {
        float[] xv = values(SIZE, -10, 10, 8);
        float[] yv = values(SIZE, -10, 10, 9);
        ByteArray condition = new ByteArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            condition.set(i, (byte) (i % 3 == 0 ? 1 : 0));
        }
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray y = FloatArray.fromArray(yv);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, condition, x, y) //
                .libraryTask("where", Mlx::where, condition, x, y, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(i % 3 == 0 ? xv[i] : yv[i], output.get(i), 0f);
        }
    }

    // ---------------------------------------------------------------- value operations of the logic group

    @Test
    public void testNanToNum() throws TornadoExecutionPlanException {
        float[] av = values(SIZE, -5, 5, 10);
        for (int i = 0; i < SIZE; i += 3) {
            av[i] = switch (i % 9) {
                case 0 -> Float.NaN;
                case 3 -> Float.POSITIVE_INFINITY;
                default -> Float.NEGATIVE_INFINITY;
            };
        }
        FloatArray a = FloatArray.fromArray(av);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("nanToNum", Mlx::nanToNum, a, output, 0.5f, 1e30f, -1e30f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            float expected = Float.isNaN(av[i]) ? 0.5f : av[i] == Float.POSITIVE_INFINITY ? 1e30f : av[i] == Float.NEGATIVE_INFINITY ? -1e30f : av[i];
            assertEquals("element " + i, expected, output.get(i), 0f);
        }
    }

    @Test
    public void testRealImagConjugate() throws TornadoExecutionPlanException {
        // SIZE complex values, interleaved (re, im).
        float[] zv = values(2 * SIZE, -3, 3, 11);
        FloatArray z = FloatArray.fromArray(zv);
        FloatArray real = new FloatArray(SIZE);
        FloatArray imag = new FloatArray(SIZE);
        FloatArray conjugate = new FloatArray(2 * SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, z) //
                .libraryTask("real", Mlx::real, z, real) //
                .libraryTask("imag", Mlx::imag, z, imag) //
                .libraryTask("conjugate", Mlx::conjugate, z, conjugate) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, real, imag, conjugate);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("real " + i, zv[2 * i], real.get(i), 0f);
            assertEquals("imag " + i, zv[2 * i + 1], imag.get(i), 0f);
            assertEquals("conjugate re " + i, zv[2 * i], conjugate.get(2 * i), 0f);
            assertEquals("conjugate im " + i, -zv[2 * i + 1], conjugate.get(2 * i + 1), 0f);
        }
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testAddHalf() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(SIZE, -10, 10, 12));
        HalfFloatArray b = half(values(SIZE, -10, 10, 13));
        HalfFloatArray output = new HalfFloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        float[] y = widen(b);
        double[] expected = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = x[i] + y[i];
        }
        assertAllClose("add float16", expected, output, 1e-3, 1e-3);
    }

    @Test
    public void testMultiplyBFloat16() throws TornadoExecutionPlanException {
        BFloat16Array a = bf16(values(SIZE, -4, 4, 14));
        BFloat16Array b = bf16(values(SIZE, -4, 4, 15));
        BFloat16Array output = new BFloat16Array(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("multiply", Mlx::multiply, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        float[] y = widen(b);
        double[] expected = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = x[i] * y[i];
        }
        assertAllClose("multiply bfloat16", expected, output, 8e-3, 8e-3);
    }

    @Test
    public void testExpHalf() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(SIZE, -3, 3, 16));
        HalfFloatArray output = new HalfFloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("exp", Mlx::exp, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        double[] expected = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = Math.exp(x[i]);
        }
        assertAllClose("exp float16", expected, output, 2e-3, 2e-3);
    }

    @Test
    public void testSinBFloat16() throws TornadoExecutionPlanException {
        BFloat16Array a = bf16(values(SIZE, -4, 4, 17));
        BFloat16Array output = new BFloat16Array(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("sin", Mlx::sin, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        double[] expected = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = Math.sin(x[i]);
        }
        assertAllClose("sin bfloat16", expected, output, 1.6e-2, 1e-2);
    }

    @Test
    public void testPowerHalf() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(SIZE, 0.5f, 2, 18));
        HalfFloatArray b = half(values(SIZE, -2, 2, 19));
        HalfFloatArray output = new HalfFloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("power", Mlx::power, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        float[] y = widen(b);
        double[] expected = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = Math.pow(x[i], y[i]);
        }
        assertAllClose("power float16", expected, output, 2e-3, 2e-3);
    }

    @Test
    public void testRoundHalfRoundsTiesToEven() throws TornadoExecutionPlanException {
        float[] v = values(SIZE, -50, 50, 20);
        float[] ties = { 0.5f, 1.5f, 2.5f, -0.5f, -2.5f };
        System.arraycopy(ties, 0, v, 0, ties.length);
        HalfFloatArray a = half(v);
        HalfFloatArray output = new HalfFloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("round", Mlx::round, a, output, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        for (int i = 0; i < SIZE; i++) {
            assertEquals("round float16 x=" + x[i], Math.rint(x[i]), output.get(i).getFloat32(), 0.0);
        }
    }

    @Test
    public void testWhereBFloat16() throws TornadoExecutionPlanException {
        BFloat16Array x = bf16(values(SIZE, -5, 5, 21));
        BFloat16Array y = bf16(values(SIZE, -5, 5, 22));
        ByteArray condition = new ByteArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            condition.set(i, (byte) (i % 2));
        }
        BFloat16Array output = new BFloat16Array(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, condition, x, y) //
                .libraryTask("where", Mlx::where, condition, x, y, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] xv = widen(x);
        float[] yv = widen(y);
        double[] expected = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            expected[i] = i % 2 == 1 ? xv[i] : yv[i];
        }
        assertAllClose("where bfloat16", expected, output, 0, 0);
    }

    @Test
    public void testClipInt() throws TornadoExecutionPlanException {
        int[] av = ints(SIZE, -1000, 1000, 23);
        IntArray a = IntArray.fromArray(av);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("clip", Mlx::clip, a, output, -100, 250) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, Math.min(Math.max(av[i], -100), 250), output.get(i));
        }
    }

    @Test
    public void testIntDivisionTruncatesTowardZero() throws TornadoExecutionPlanException {
        int[] av = ints(SIZE, -1000, 1000, 24);
        int[] bv = ints(SIZE, 1, 50, 25);
        for (int i = 0; i < SIZE; i += 2) {
            bv[i] = -bv[i];
        }
        IntArray a = IntArray.fromArray(av);
        IntArray b = IntArray.fromArray(bv);
        IntArray floorDivide = new IntArray(SIZE);
        IntArray quotient = new IntArray(SIZE);
        IntArray remainder = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("floorDivide", Mlx::floorDivide, a, b, floorDivide) //
                .libraryTask("divmod", Mlx::divmod, a, b, quotient, remainder) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, floorDivide, quotient, remainder);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // MLX divides integers as C does (toward zero), so floor_divide truncates for int32.
        for (int i = 0; i < SIZE; i++) {
            assertEquals(av[i] + " / " + bv[i], av[i] / bv[i], floorDivide.get(i));
            assertEquals("divmod quotient " + av[i] + " / " + bv[i], av[i] / bv[i], quotient.get(i));
            assertEquals("divmod remainder " + av[i] + " % " + bv[i], Math.floorMod(av[i], bv[i]), remainder.get(i));
        }
    }

    @Test
    public void testAddAboveWorkPerThreadThreshold() throws TornadoExecutionPlanException {
        // From 65,536 elements MLX's element-wise kernels process several elements per thread; 65,537
        // leaves an uneven tail.
        for (int n : new int[] { 65535, 65536, 65537, 1 << 20 }) {
            FloatArray a = FloatArray.fromArray(values(n, -5, 5, 26));
            FloatArray b = FloatArray.fromArray(values(n, -5, 5, 27));
            FloatArray output = new FloatArray(n);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .libraryTask("add", Mlx::add, a, b, output) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            for (int i = 0; i < n; i++) {
                assertEquals("n=" + n + " element " + i, a.get(i) + b.get(i), output.get(i), 0f);
            }
        }
    }
}
