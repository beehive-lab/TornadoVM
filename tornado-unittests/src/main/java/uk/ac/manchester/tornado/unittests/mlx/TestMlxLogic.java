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
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX logic library tasks: comparisons, value classification, bitwise and
 * logical operations, and closeness tests. Boolean results are 0 or 1 in a {@code ByteArray}. Each
 * test checks the task against a sequential Java reference. Skipped unless the default device is on
 * the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxLogic
 * </code>
 */
public class TestMlxLogic extends MlxTestBase {

    private static final int SIZE = 1027;

    interface FloatPredicate {
        boolean test(float x, float y);
    }

    /** Values in [-5, 5) with NaNs, infinities and copies of {@code partner} mixed in. */
    private static float[] specials(long seed, float[] partner) {
        Random random = new Random(seed);
        float[] v = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            v[i] = switch (i % 11) {
                case 3 -> Float.NaN;
                case 5 -> Float.POSITIVE_INFINITY;
                case 7 -> Float.NEGATIVE_INFINITY;
                case 9 -> partner != null ? partner[i] : 1.0f;
                default -> 10 * random.nextFloat() - 5;
            };
        }
        return v;
    }

    private static void comparison(LibraryTask3<FloatArray, FloatArray, ByteArray> mlx, FloatPredicate ref) throws TornadoExecutionPlanException {
        float[] bv = specials(1, null);
        float[] av = specials(2, bv);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("compare", mlx, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(av[i] + " vs " + bv[i], ref.test(av[i], bv[i]) ? 1 : 0, output.get(i));
        }
    }

    private static void classification(LibraryTask2<FloatArray, ByteArray> mlx, Predicate<Float> ref) throws TornadoExecutionPlanException {
        float[] av = specials(3, null);
        FloatArray a = FloatArray.fromArray(av);
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("classify", mlx, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("x=" + av[i], ref.test(av[i]) ? 1 : 0, output.get(i));
        }
    }

    private static void bitwise(LibraryTask3<IntArray, IntArray, IntArray> mlx, IntBinaryOperator ref, boolean shift) throws TornadoExecutionPlanException {
        Random random = new Random(4);
        int[] av = new int[SIZE];
        int[] bv = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            av[i] = random.nextInt();
            bv[i] = shift ? random.nextInt(31) : random.nextInt();
        }
        IntArray a = IntArray.fromArray(av);
        IntArray b = IntArray.fromArray(bv);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("bitwise", mlx, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(av[i] + ", " + bv[i], ref.applyAsInt(av[i], bv[i]), output.get(i));
        }
    }

    /** NumPy's isclose: |x - y| &lt;= atol + rtol * |y|, infinities equal only to themselves. */
    private static boolean closeJava(float x, float y, float rtol, float atol, boolean equalNan) {
        if (Float.isNaN(x) || Float.isNaN(y)) {
            return equalNan && Float.isNaN(x) && Float.isNaN(y);
        }
        if (Float.isInfinite(x) || Float.isInfinite(y)) {
            return x == y;
        }
        return Math.abs(x - y) <= atol + rtol * Math.abs(y);
    }

    /** {@code b}, with every fourth finite element nudged by a little or by a lot. */
    private static float[] nudged(float[] b) {
        float[] a = b.clone();
        for (int i = 0; i < SIZE; i += 4) {
            if (Float.isFinite(a[i])) {
                a[i] += (i % 8 == 0 ? 1e-6f : 1e-1f) * (1 + Math.abs(a[i]));
            }
        }
        return a;
    }

    private static byte[] randomBools(long seed) {
        Random random = new Random(seed);
        byte[] v = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            v[i] = (byte) (random.nextBoolean() ? random.nextInt(5) + 1 : 0);
        }
        return v;
    }

    @Test
    public void testEqual() throws TornadoExecutionPlanException {
        comparison(Mlx::equal, (x, y) -> x == y);
    }

    @Test
    public void testNotEqual() throws TornadoExecutionPlanException {
        comparison(Mlx::notEqual, (x, y) -> x != y);
    }

    @Test
    public void testGreater() throws TornadoExecutionPlanException {
        comparison(Mlx::greater, (x, y) -> x > y);
    }

    @Test
    public void testGreaterEqual() throws TornadoExecutionPlanException {
        comparison(Mlx::greaterEqual, (x, y) -> x >= y);
    }

    @Test
    public void testLess() throws TornadoExecutionPlanException {
        comparison(Mlx::less, (x, y) -> x < y);
    }

    @Test
    public void testLessEqual() throws TornadoExecutionPlanException {
        comparison(Mlx::lessEqual, (x, y) -> x <= y);
    }

    @Test
    public void testIsfinite() throws TornadoExecutionPlanException {
        classification(Mlx::isfinite, x -> Float.isFinite(x));
    }

    @Test
    public void testIsinf() throws TornadoExecutionPlanException {
        classification(Mlx::isinf, x -> Float.isInfinite(x));
    }

    @Test
    public void testIsnan() throws TornadoExecutionPlanException {
        classification(Mlx::isnan, x -> Float.isNaN(x));
    }

    @Test
    public void testIsneginf() throws TornadoExecutionPlanException {
        classification(Mlx::isneginf, x -> x == Float.NEGATIVE_INFINITY);
    }

    @Test
    public void testIsposinf() throws TornadoExecutionPlanException {
        classification(Mlx::isposinf, x -> x == Float.POSITIVE_INFINITY);
    }

    @Test
    public void testBitwiseAnd() throws TornadoExecutionPlanException {
        bitwise(Mlx::bitwiseAnd, (x, y) -> x & y, false);
    }

    @Test
    public void testBitwiseOr() throws TornadoExecutionPlanException {
        bitwise(Mlx::bitwiseOr, (x, y) -> x | y, false);
    }

    @Test
    public void testBitwiseXor() throws TornadoExecutionPlanException {
        bitwise(Mlx::bitwiseXor, (x, y) -> x ^ y, false);
    }

    @Test
    public void testLeftShift() throws TornadoExecutionPlanException {
        bitwise(Mlx::leftShift, (x, y) -> x << y, true);
    }

    @Test
    public void testRightShift() throws TornadoExecutionPlanException {
        bitwise(Mlx::rightShift, (x, y) -> x >> y, true);
    }

    @Test
    public void testBitwiseInvert() throws TornadoExecutionPlanException {
        Random random = new Random(5);
        int[] av = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            av[i] = random.nextInt();
        }
        IntArray a = IntArray.fromArray(av);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("invert", Mlx::bitwiseInvert, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(~av[i], output.get(i));
        }
    }

    @Test
    public void testLogicalAnd() throws TornadoExecutionPlanException {
        byte[] av = randomBools(6);
        byte[] bv = randomBools(7);
        ByteArray a = ByteArray.fromArray(av);
        ByteArray b = ByteArray.fromArray(bv);
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("and", Mlx::logicalAnd, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(av[i] != 0 && bv[i] != 0 ? 1 : 0, output.get(i));
        }
    }

    @Test
    public void testLogicalOr() throws TornadoExecutionPlanException {
        byte[] av = randomBools(8);
        byte[] bv = randomBools(9);
        ByteArray a = ByteArray.fromArray(av);
        ByteArray b = ByteArray.fromArray(bv);
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("or", Mlx::logicalOr, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(av[i] != 0 || bv[i] != 0 ? 1 : 0, output.get(i));
        }
    }

    @Test
    public void testLogicalNot() throws TornadoExecutionPlanException {
        byte[] av = randomBools(10);
        ByteArray a = ByteArray.fromArray(av);
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("not", Mlx::logicalNot, a, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(av[i] == 0 ? 1 : 0, output.get(i));
        }
    }

    @Test
    public void testIsclose() throws TornadoExecutionPlanException {
        float[] bv = specials(11, null);
        float[] av = nudged(bv);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        ByteArray output = new ByteArray(SIZE);
        ByteArray outputEqualNan = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("isclose", Mlx::isclose, a, b, output, 1e-3f, 1e-5f, false) //
                .libraryTask("iscloseNan", Mlx::isclose, a, b, outputEqualNan, 1e-3f, 1e-5f, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, outputEqualNan);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, closeJava(av[i], bv[i], 1e-3f, 1e-5f, false) ? 1 : 0, output.get(i));
            assertEquals("equalNan element " + i, closeJava(av[i], bv[i], 1e-3f, 1e-5f, true) ? 1 : 0, outputEqualNan.get(i));
        }
    }

    @Test
    public void testAllclose() throws TornadoExecutionPlanException {
        float[] bv = specials(12, null);
        float[] av = nudged(bv);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray same = FloatArray.fromArray(bv.clone());
        ByteArray differs = new ByteArray(1);
        ByteArray sameNoNan = new ByteArray(1);
        ByteArray sameEqualNan = new ByteArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, same) //
                .libraryTask("differs", Mlx::allclose, a, b, differs, 1e-3f, 1e-5f, true) //
                .libraryTask("same", Mlx::allclose, same, b, sameNoNan, 1e-3f, 1e-5f, false) //
                .libraryTask("sameNan", Mlx::allclose, same, b, sameEqualNan, 1e-3f, 1e-5f, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, differs, sameNoNan, sameEqualNan);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        boolean all = true;
        for (int i = 0; i < SIZE; i++) {
            all &= closeJava(av[i], bv[i], 1e-3f, 1e-5f, true);
        }
        assertEquals("allclose(a, b)", all ? 1 : 0, differs.get(0));
        // same and b hold the same values, NaNs included, so only NaN handling decides.
        assertEquals("allclose(b, b) without equalNan", 0, sameNoNan.get(0));
        assertEquals("allclose(b, b) with equalNan", 1, sameEqualNan.get(0));
    }

    @Test
    public void testArrayEqual() throws TornadoExecutionPlanException {
        float[] bv = specials(13, null);
        float[] av = nudged(bv);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray same = FloatArray.fromArray(bv.clone());
        ByteArray differs = new ByteArray(1);
        ByteArray sameNoNan = new ByteArray(1);
        ByteArray sameEqualNan = new ByteArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, same) //
                .libraryTask("differs", Mlx::arrayEqual, a, b, differs, true) //
                .libraryTask("same", Mlx::arrayEqual, same, b, sameNoNan, false) //
                .libraryTask("sameNan", Mlx::arrayEqual, same, b, sameEqualNan, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, differs, sameNoNan, sameEqualNan);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals("arrayEqual(a, b)", 0, differs.get(0));
        assertEquals("arrayEqual(b, b) without equalNan", 0, sameNoNan.get(0));
        assertEquals("arrayEqual(b, b) with equalNan", 1, sameEqualNan.get(0));
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testLessHalf() throws TornadoExecutionPlanException {
        HalfFloatArray a = half(values(SIZE, -2, 2, 14));
        HalfFloatArray b = half(values(SIZE, -2, 2, 15));
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("less", Mlx::less, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] x = widen(a);
        float[] y = widen(b);
        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, x[i] < y[i] ? 1 : 0, output.get(i));
        }
    }

    @Test
    public void testEqualInt() throws TornadoExecutionPlanException {
        int[] av = new int[SIZE];
        int[] bv = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            av[i] = i % 7;
            bv[i] = i % 5;
        }
        IntArray a = IntArray.fromArray(av);
        IntArray b = IntArray.fromArray(bv);
        ByteArray output = new ByteArray(SIZE);
        ByteArray self = new ByteArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("equal", Mlx::equal, a, b, output) //
                .libraryTask("arrayEqual", Mlx::arrayEqual, a, a, self, false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, self);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, av[i] == bv[i] ? 1 : 0, output.get(i));
        }
        assertEquals("arrayEqual(a, a)", 1, self.get(0));
    }

    @Test
    public void testLessWithNaNsIsFalse() throws TornadoExecutionPlanException {
        final int n = 100003;
        float[] av = values(n, -1, 1, 16);
        float[] bv = values(n, -1, 1, 17);
        for (int i = 0; i < n; i += 13) {
            av[i] = Float.NaN;
        }
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        ByteArray output = new ByteArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("less", Mlx::less, a, b, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("element " + i, av[i] < bv[i] ? 1 : 0, output.get(i));
        }
    }
}
