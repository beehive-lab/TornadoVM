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
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxMath;

/**
 * The float16, bfloat16 and int32 forms of the MLX element-wise math library tasks, against Java
 * references. (The float32 forms are covered, with their JIT counterparts, by {@link TestJitMath}.)
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxMath
 * </code>
 */
public class TestMlxMath extends MlxTestBase {

    private static final int N = 1027;

    private static void unaryHalf(String name, LibraryTask2<HalfFloatArray, HalfFloatArray> mlx, DoubleUnaryOperator ref, float lo, float hi) throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(N, lo, hi, 21));
        HalfFloatArray out = new HalfFloatArray(N);
        run(new TaskGraph("h1").transferToDevice(DataTransferMode.FIRST_EXECUTION, a).libraryTask("t", mlx, a, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        float[] x = widen(a);
        double[] expected = new double[N];
        for (int i = 0; i < N; i++) {
            expected[i] = ref.applyAsDouble(x[i]);
        }
        assertAllClose(name + " float16", expected, out, 2e-3, 2e-3);
    }

    private static void unaryBf16(String name, LibraryTask2<BFloat16Array, BFloat16Array> mlx, DoubleUnaryOperator ref, float lo, float hi) throws TornadoExecutionPlanException {
        BFloat16Array a = bf16(values(N, lo, hi, 22));
        BFloat16Array out = new BFloat16Array(N);
        run(new TaskGraph("b1").transferToDevice(DataTransferMode.FIRST_EXECUTION, a).libraryTask("t", mlx, a, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        float[] x = widen(a);
        double[] expected = new double[N];
        for (int i = 0; i < N; i++) {
            expected[i] = ref.applyAsDouble(x[i]);
        }
        assertAllClose(name + " bfloat16", expected, out, 1.6e-2, 1e-2);
    }

    private static void binaryHalf(String name, LibraryTask3<HalfFloatArray, HalfFloatArray, HalfFloatArray> mlx, DoubleBinaryOperator ref, float aLo, float aHi, float bLo, float bHi)
            throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(N, aLo, aHi, 23));
        HalfFloatArray b = half(values(N, bLo, bHi, 24));
        HalfFloatArray c = new HalfFloatArray(N);
        run(new TaskGraph("h2").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b).libraryTask("t", mlx, a, b, c).transferToHost(DataTransferMode.EVERY_EXECUTION, c));
        float[] x = widen(a);
        float[] y = widen(b);
        double[] expected = new double[N];
        for (int i = 0; i < N; i++) {
            expected[i] = ref.applyAsDouble(x[i], y[i]);
        }
        assertAllClose(name + " float16", expected, c, 2e-3, 2e-3);
    }

    @Test
    public void testUnaryHalf() throws TornadoExecutionPlanException {
        unaryHalf("sin", MlxMath::sin, Math::sin, -4, 4);
        unaryHalf("log", MlxMath::log, Math::log, 0.1f, 10);
        unaryHalf("sinh", MlxMath::sinh, Math::sinh, -3, 3);
        unaryHalf("arctanh", MlxMath::arctanh, x -> 0.5 * Math.log((1 + x) / (1 - x)), -0.9f, 0.9f);
        unaryHalf("floor", MlxMath::floor, Math::floor, -10, 10);
    }

    @Test
    public void testUnaryBFloat16() throws TornadoExecutionPlanException {
        unaryBf16("cos", MlxMath::cos, Math::cos, -4, 4);
        unaryBf16("log2", MlxMath::log2, x -> Math.log(x) / Math.log(2), 0.1f, 10);
        unaryBf16("expm1", MlxMath::expm1, Math::expm1, -3, 3);
        unaryBf16("degrees", MlxMath::degrees, Math::toDegrees, -3, 3);
    }

    @Test
    public void testBinaryHalf() throws TornadoExecutionPlanException {
        binaryHalf("power", MlxMath::power, Math::pow, 0.5f, 2, -2, 2);
        binaryHalf("arctan2", MlxMath::arctan2, Math::atan2, -5, 5, -5, 5);
        binaryHalf("logaddexp", MlxMath::logaddexp, (x, y) -> Math.max(x, y) + Math.log1p(Math.exp(-Math.abs(x - y))), -5, 5, -5, 5);
    }

    @Test
    public void testRoundHalf() throws TornadoExecutionPlanException {
        float[] v = values(N, -50, 50, 25);
        float[] ties = { 0.5f, 1.5f, 2.5f, -0.5f, -2.5f };
        System.arraycopy(ties, 0, v, 0, ties.length);
        HalfFloatArray a = half(v);
        HalfFloatArray out = new HalfFloatArray(N);
        run(new TaskGraph("rh").transferToDevice(DataTransferMode.FIRST_EXECUTION, a).libraryTask("t", MlxMath::round, a, out, 0).transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        float[] x = widen(a);
        for (int i = 0; i < N; i++) {
            assertEquals("round float16 x=" + x[i], Math.rint(x[i]), out.get(i).getFloat32(), 0.0);
        }
    }

    private static int[] ints(int n, int lo, int hi, long seed) {
        Random r = new Random(seed);
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = lo + r.nextInt(hi - lo);
        }
        return v;
    }

    @Test
    public void testIntUnary() throws TornadoExecutionPlanException {
        int[] av = ints(N, -1000, 1000, 26);
        IntArray a = IntArray.fromArray(av);
        IntArray clip = new IntArray(N);
        run(new TaskGraph("iu").transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .libraryTask("clip", MlxMath::clip, a, clip, -100, 250) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, clip));
        for (int i = 0; i < N; i++) {
            assertEquals("clip int32 " + i, Math.min(Math.max(av[i], -100), 250), clip.get(i));
        }
    }

    @Test
    public void testIntDivision() throws TornadoExecutionPlanException {
        int[] av = ints(N, -1000, 1000, 27);
        int[] bv = ints(N, 1, 50, 28);
        for (int i = 0; i < N; i += 2) {
            bv[i] = -bv[i];
        }
        IntArray a = IntArray.fromArray(av);
        IntArray b = IntArray.fromArray(bv);
        IntArray floorDiv = new IntArray(N);
        IntArray q = new IntArray(N);
        IntArray r = new IntArray(N);
        run(new TaskGraph("id").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("fd", MlxMath::floorDivide, a, b, floorDiv) //
                .libraryTask("dm", MlxMath::divmod, a, b, q, r) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, floorDiv, q, r));
        for (int i = 0; i < N; i++) {
            // MLX divides integers as C does (toward zero), so floor_divide truncates for int32.
            assertEquals("floor_divide int32 " + av[i] + " / " + bv[i], av[i] / bv[i], floorDiv.get(i));
            assertEquals("divmod quotient int32 " + av[i] + " / " + bv[i], av[i] / bv[i], q.get(i));
            assertEquals("divmod remainder int32 " + av[i] + " % " + bv[i], Math.floorMod(av[i], bv[i]), r.get(i));
        }
    }

    @Test
    public void testWhereBFloat16() throws TornadoExecutionPlanException {
        BFloat16Array x = bf16(values(N, -5, 5, 29));
        BFloat16Array y = bf16(values(N, -5, 5, 30));
        ByteArray cond = new ByteArray(N);
        for (int i = 0; i < N; i++) {
            cond.set(i, (byte) (i % 2));
        }
        BFloat16Array out = new BFloat16Array(N);
        run(new TaskGraph("wb").transferToDevice(DataTransferMode.FIRST_EXECUTION, cond, x, y).libraryTask("t", MlxMath::where, cond, x, y, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        float[] xv = widen(x);
        float[] yv = widen(y);
        double[] expected = new double[N];
        for (int i = 0; i < N; i++) {
            expected[i] = i % 2 == 1 ? xv[i] : yv[i];
        }
        assertAllClose("where bfloat16", expected, out, 0, 0);
    }
}
