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

import static org.junit.Assert.fail;

import java.util.Random;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.BFloat16;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMetalNotSupported;

/**
 * Element-wise MLX library tasks in every dtype they are bound for, checked against a Java
 * reference. Sizes cover one element, a length that is not a multiple of 32, and a large array.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxElementwise
 * </code>
 */
public class TestMlxElementwise extends TornadoTestBase {

    private static final int N = 1027;

    @Before
    public void mlxMustBeAvailable() {
        TornadoVMBackendType backend = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backend != TornadoVMBackendType.METAL) {
            assertNotBackend(backend, "MLX library tasks require the Metal backend (default device is " + backend + ")");
        }
        if (!MlxLibraryProvider.isAvailable()) {
            throw new TornadoVMMetalNotSupported("mlx.metallib is not available on this host");
        }
    }

    // ---------------------------------------------------------------- inputs and tolerances

    /** Input values in [lo, hi). */
    private static float[] values(int n, float lo, float hi, long seed) {
        Random random = new Random(seed);
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            v[i] = lo + (hi - lo) * random.nextFloat();
        }
        return v;
    }

    private static void close(String what, int i, double expected, double actual, double relTol, double absTol) {
        if (Double.isNaN(expected) ? !Double.isNaN(actual) : Math.abs(expected - actual) > absTol + relTol * Math.abs(expected)) {
            fail(what + " element " + i + ": expected " + expected + " but was " + actual);
        }
    }

    private static void run(TaskGraph taskGraph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    /** erf with |error| < 1.2e-7 (Numerical Recipes erfc Chebyshev fit). */
    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double y = t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223
                + t * 0.17087277)))))))));
        return x >= 0 ? 1.0 - y : y - 1.0;
    }

    // ---------------------------------------------------------------- binary

    private static void binary(String name, LibraryTask3<FloatArray, FloatArray, FloatArray> f32, LibraryTask3<HalfFloatArray, HalfFloatArray, HalfFloatArray> f16,
            LibraryTask3<BFloat16Array, BFloat16Array, BFloat16Array> bf16, DoubleBinaryOperator ref,
            float lo, float hi) throws TornadoExecutionPlanException {
        for (int n : new int[] { 1, N, 1 << 20 }) {
            float[] av = values(n, lo, hi, 1);
            float[] bv = values(n, lo, hi, 2);
            FloatArray a = FloatArray.fromArray(av);
            FloatArray b = FloatArray.fromArray(bv);
            FloatArray c = new FloatArray(n);
            run(new TaskGraph("f32").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b).libraryTask("t", f32, a, b, c).transferToHost(DataTransferMode.EVERY_EXECUTION, c));
            for (int i = 0; i < n; i++) {
                close(name + " float32 n=" + n, i, (float) ref.applyAsDouble(av[i], bv[i]), c.get(i), 1e-6, 1e-7);
            }
        }

        float[] av = values(N, lo, hi, 3);
        float[] bv = values(N, lo, hi, 4);
        HalfFloatArray ha = new HalfFloatArray(N);
        HalfFloatArray hb = new HalfFloatArray(N);
        HalfFloatArray hc = new HalfFloatArray(N);
        BFloat16Array ba = new BFloat16Array(N);
        BFloat16Array bb = new BFloat16Array(N);
        BFloat16Array bc = new BFloat16Array(N);
        for (int i = 0; i < N; i++) {
            ha.set(i, new HalfFloat(av[i]));
            hb.set(i, new HalfFloat(bv[i]));
            ba.set(i, BFloat16.bf16FromFloat(av[i]));
            bb.set(i, BFloat16.bf16FromFloat(bv[i]));
        }
        run(new TaskGraph("f16").transferToDevice(DataTransferMode.FIRST_EXECUTION, ha, hb).libraryTask("t", f16, ha, hb, hc).transferToHost(DataTransferMode.EVERY_EXECUTION, hc));
        run(new TaskGraph("bf16").transferToDevice(DataTransferMode.FIRST_EXECUTION, ba, bb).libraryTask("t", bf16, ba, bb, bc).transferToHost(DataTransferMode.EVERY_EXECUTION, bc));
        for (int i = 0; i < N; i++) {
            double h = ref.applyAsDouble(ha.get(i).getFloat32(), hb.get(i).getFloat32());
            close(name + " float16", i, h, hc.get(i).getFloat32(), 1e-3, 1e-3);
            double bref = ref.applyAsDouble(BFloat16.bf16ToFloat(ba.get(i)), BFloat16.bf16ToFloat(bb.get(i)));
            close(name + " bfloat16", i, bref, BFloat16.bf16ToFloat(bc.get(i)), 8e-3, 8e-3);
        }
    }

    @Test
    public void testAdd() throws TornadoExecutionPlanException {
        binary("add", Mlx::add, Mlx::add, Mlx::add, (x, y) -> x + y, -10, 10);
    }

    @Test
    public void testSubtract() throws TornadoExecutionPlanException {
        binary("subtract", Mlx::subtract, Mlx::subtract, Mlx::subtract, (x, y) -> x - y, -10, 10);
    }

    @Test
    public void testMultiply() throws TornadoExecutionPlanException {
        binary("multiply", Mlx::multiply, Mlx::multiply, Mlx::multiply, (x, y) -> x * y, -10, 10);
    }

    @Test
    public void testDivide() throws TornadoExecutionPlanException {
        binary("divide", Mlx::divide, Mlx::divide, Mlx::divide, (x, y) -> x / y, 0.5f, 10);
    }

    @Test
    public void testMaximum() throws TornadoExecutionPlanException {
        binary("maximum", Mlx::maximum, Mlx::maximum, Mlx::maximum, Math::max, -10, 10);
    }

    @Test
    public void testMinimum() throws TornadoExecutionPlanException {
        binary("minimum", Mlx::minimum, Mlx::minimum, Mlx::minimum, Math::min, -10, 10);
    }

    // ---------------------------------------------------------------- unary

    private static void unary(String name, LibraryTask2<FloatArray, FloatArray> f32, LibraryTask2<HalfFloatArray, HalfFloatArray> f16, LibraryTask2<BFloat16Array, BFloat16Array> bf16,
            DoubleUnaryOperator ref, float lo, float hi) throws TornadoExecutionPlanException {
        for (int n : new int[] { 1, N, 1 << 20 }) {
            float[] av = values(n, lo, hi, 11);
            FloatArray a = FloatArray.fromArray(av);
            FloatArray out = new FloatArray(n);
            run(new TaskGraph("f32").transferToDevice(DataTransferMode.FIRST_EXECUTION, a).libraryTask("t", f32, a, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out));
            for (int i = 0; i < n; i++) {
                close(name + " float32 n=" + n, i, ref.applyAsDouble(av[i]), out.get(i), 2e-6, 1e-6);
            }
        }
        float[] av = values(N, lo, hi, 12);
        HalfFloatArray ha = new HalfFloatArray(N);
        HalfFloatArray hout = new HalfFloatArray(N);
        BFloat16Array ba = new BFloat16Array(N);
        BFloat16Array bout = new BFloat16Array(N);
        for (int i = 0; i < N; i++) {
            ha.set(i, new HalfFloat(av[i]));
            ba.set(i, BFloat16.bf16FromFloat(av[i]));
        }
        run(new TaskGraph("f16").transferToDevice(DataTransferMode.FIRST_EXECUTION, ha).libraryTask("t", f16, ha, hout).transferToHost(DataTransferMode.EVERY_EXECUTION, hout));
        run(new TaskGraph("bf16").transferToDevice(DataTransferMode.FIRST_EXECUTION, ba).libraryTask("t", bf16, ba, bout).transferToHost(DataTransferMode.EVERY_EXECUTION, bout));
        for (int i = 0; i < N; i++) {
            close(name + " float16", i, ref.applyAsDouble(ha.get(i).getFloat32()), hout.get(i).getFloat32(), 2e-3, 2e-3);
            close(name + " bfloat16", i, ref.applyAsDouble(BFloat16.bf16ToFloat(ba.get(i))), BFloat16.bf16ToFloat(bout.get(i)), 1.6e-2, 1.6e-2);
        }
    }

    @Test
    public void testNegative() throws TornadoExecutionPlanException {
        unary("negative", Mlx::negative, Mlx::negative, Mlx::negative, x -> -x, -10, 10);
    }

    @Test
    public void testExp() throws TornadoExecutionPlanException {
        unary("exp", Mlx::exp, Mlx::exp, Mlx::exp, Math::exp, -8, 8);
    }

    @Test
    public void testTanh() throws TornadoExecutionPlanException {
        unary("tanh", Mlx::tanh, Mlx::tanh, Mlx::tanh, Math::tanh, -5, 5);
    }

    @Test
    public void testErf() throws TornadoExecutionPlanException {
        unary("erf", Mlx::erf, Mlx::erf, Mlx::erf, TestMlxElementwise::erf, -3, 3);
    }

    @Test
    public void testSigmoid() throws TornadoExecutionPlanException {
        unary("sigmoid", Mlx::sigmoid, Mlx::sigmoid, Mlx::sigmoid, x -> 1.0 / (1.0 + Math.exp(-x)), -10, 10);
    }

    @Test
    public void testSqrt() throws TornadoExecutionPlanException {
        unary("sqrt", Mlx::sqrt, Mlx::sqrt, Mlx::sqrt, Math::sqrt, 0, 100);
    }

    @Test
    public void testRsqrt() throws TornadoExecutionPlanException {
        unary("rsqrt", Mlx::rsqrt, Mlx::rsqrt, Mlx::rsqrt, x -> 1.0 / Math.sqrt(x), 0.01f, 100);
    }

    @Test
    public void testSquare() throws TornadoExecutionPlanException {
        unary("square", Mlx::square, Mlx::square, Mlx::square, x -> x * x, -10, 10);
    }
}
