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
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask6;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxReductions;

/**
 * Unit tests for the MLX reduction library tasks: sum, prod, max, min, mean, var, std, logsumexp,
 * all and any over a whole array, one axis or two adjacent axes; argmin, argmax and softmax. The
 * axis forms view the input as {@code [outer, len, inner]} (or {@code [outer, len1, len2, inner]})
 * and reduce the middle; {@code inner} is 1. Each test checks the task against a sequential Java
 * reference. Skipped unless the default device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxReductions
 * </code>
 */
public class TestMlxReductions extends MlxTestBase {

    private static final int SIZE = 1027;
    private static final int OUTER = 37;
    private static final int LEN = 513;
    private static final int LEN1 = 12;
    private static final int LEN2 = 9;

    private static double sumJava(double[] v) {
        return Arrays.stream(v).sum();
    }

    private static double prodJava(double[] v) {
        return Arrays.stream(v).reduce(1.0, (a, b) -> a * b);
    }

    private static double maxJava(double[] v) {
        return Arrays.stream(v).max().getAsDouble();
    }

    private static double minJava(double[] v) {
        return Arrays.stream(v).min().getAsDouble();
    }

    private static double meanJava(double[] v) {
        return Arrays.stream(v).average().getAsDouble();
    }

    private static double varJava(double[] v, int ddof) {
        double mean = meanJava(v);
        return Arrays.stream(v).map(d -> (d - mean) * (d - mean)).sum() / (v.length - ddof);
    }

    private static double logsumexpJava(double[] v) {
        double max = maxJava(v);
        return max + Math.log(Arrays.stream(v).map(d -> Math.exp(d - max)).sum());
    }

    /** Row {@code r} of {@code x} viewed as [rows, len], widened to double. */
    private static double[] row(float[] x, int r, int len) {
        double[] v = new double[len];
        for (int i = 0; i < len; i++) {
            v[i] = x[r * len + i];
        }
        return v;
    }

    /** Reduces each of the {@code rows} rows of {@code x} with {@code ref}. */
    private static double[] rowsJava(float[] x, int rows, int len, ToDoubleFunction<double[]> ref) {
        double[] out = new double[rows];
        for (int r = 0; r < rows; r++) {
            out[r] = ref.applyAsDouble(row(x, r, len));
        }
        return out;
    }

    private static void whole(LibraryTask2<FloatArray, FloatArray> mlx, ToDoubleFunction<double[]> ref, float lo, float hi, double relTol, double absTol)
            throws TornadoExecutionPlanException {
        float[] xv = values(SIZE, lo, hi, 1);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("reduce", mlx, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertClose("whole", 0, ref.applyAsDouble(row(xv, 0, SIZE)), output.get(0), relTol, absTol);
    }

    private static void axis(LibraryTask5<FloatArray, FloatArray, Integer, Integer, Integer> mlx, ToDoubleFunction<double[]> ref, float lo, float hi, double relTol, double absTol)
            throws TornadoExecutionPlanException {
        float[] xv = values(OUTER * LEN, lo, hi, 2);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(OUTER);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("reduce", mlx, x, output, OUTER, LEN, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("axis", rowsJava(xv, OUTER, LEN, ref), output, relTol, absTol);
    }

    private static void axes(LibraryTask6<FloatArray, FloatArray, Integer, Integer, Integer, Integer> mlx, ToDoubleFunction<double[]> ref, float lo, float hi, double relTol,
            double absTol) throws TornadoExecutionPlanException {
        float[] xv = values(OUTER * LEN1 * LEN2, lo, hi, 3);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(OUTER);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("reduce", mlx, x, output, OUTER, LEN1, LEN2, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("axes", rowsJava(xv, OUTER, LEN1 * LEN2, ref), output, relTol, absTol);
    }

    /** Values that are zero with probability 1/4. */
    private static float[] sparse(int n, long seed) {
        Random random = new Random(seed);
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            v[i] = random.nextInt(4) == 0 ? 0f : random.nextFloat() + 0.5f;
        }
        return v;
    }

    /** Checks an all/any output of {@code rows} rows of length {@code len} against {@code ref}. */
    private static void assertBools(float[] xv, int rows, int len, Predicate<double[]> ref, ByteArray output) {
        for (int r = 0; r < rows; r++) {
            assertEquals("row " + r, ref.test(row(xv, r, len)) ? 1 : 0, output.get(r));
        }
    }

    private static boolean allJava(double[] v) {
        return Arrays.stream(v).allMatch(d -> d != 0);
    }

    private static boolean anyJava(double[] v) {
        return Arrays.stream(v).anyMatch(d -> d != 0);
    }

    private static int argminJava(double[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) {
            if (v[i] < v[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int argmaxJava(double[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) {
            if (v[i] > v[best]) {
                best = i;
            }
        }
        return best;
    }

    /** Softmax of each consecutive group of {@code group} values. */
    private static double[] softmaxJava(float[] x, int group) {
        double[] out = new double[x.length];
        for (int start = 0; start < x.length; start += group) {
            double max = Double.NEGATIVE_INFINITY;
            for (int i = start; i < start + group; i++) {
                max = Math.max(max, x[i]);
            }
            double sum = 0;
            for (int i = start; i < start + group; i++) {
                out[i] = Math.exp(x[i] - max);
                sum += out[i];
            }
            for (int i = start; i < start + group; i++) {
                out[i] /= sum;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- whole array

    @Test
    public void testSum() throws TornadoExecutionPlanException {
        whole(MlxReductions::sum, TestMlxReductions::sumJava, -1, 1, 1e-4, 1e-3);
    }

    @Test
    public void testProd() throws TornadoExecutionPlanException {
        whole(MlxReductions::prod, TestMlxReductions::prodJava, 0.99f, 1.01f, 1e-4, 1e-4);
    }

    @Test
    public void testMax() throws TornadoExecutionPlanException {
        whole(MlxReductions::max, TestMlxReductions::maxJava, -1, 1, 0, 0);
    }

    @Test
    public void testMin() throws TornadoExecutionPlanException {
        whole(MlxReductions::min, TestMlxReductions::minJava, -1, 1, 0, 0);
    }

    @Test
    public void testMean() throws TornadoExecutionPlanException {
        whole(MlxReductions::mean, TestMlxReductions::meanJava, -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testVar() throws TornadoExecutionPlanException {
        whole((x, out) -> MlxReductions.var(x, out, 0), v -> varJava(v, 0), -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testStd() throws TornadoExecutionPlanException {
        whole((x, out) -> MlxReductions.std(x, out, 1), v -> Math.sqrt(varJava(v, 1)), -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testLogsumexp() throws TornadoExecutionPlanException {
        whole(MlxReductions::logsumexp, TestMlxReductions::logsumexpJava, -5, 5, 1e-5, 1e-5);
    }

    @Test
    public void testAll() throws TornadoExecutionPlanException {
        float[] sparse = sparse(SIZE, 4);
        float[] dense = values(SIZE, 0.5f, 1.5f, 5);
        FloatArray x = FloatArray.fromArray(sparse);
        FloatArray y = FloatArray.fromArray(dense);
        ByteArray allSparse = new ByteArray(1);
        ByteArray allDense = new ByteArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .libraryTask("allSparse", MlxReductions::all, x, allSparse) //
                .libraryTask("allDense", MlxReductions::all, y, allDense) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, allSparse, allDense);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals("all(sparse)", 0, allSparse.get(0));
        assertEquals("all(dense)", 1, allDense.get(0));
    }

    @Test
    public void testAny() throws TornadoExecutionPlanException {
        FloatArray zeros = new FloatArray(SIZE);
        FloatArray oneNonZero = new FloatArray(SIZE);
        oneNonZero.set(SIZE - 1, 3f);
        ByteArray anyZeros = new ByteArray(1);
        ByteArray anyOne = new ByteArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, zeros, oneNonZero) //
                .libraryTask("anyZeros", MlxReductions::any, zeros, anyZeros) //
                .libraryTask("anyOne", MlxReductions::any, oneNonZero, anyOne) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, anyZeros, anyOne);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals("any(zeros)", 0, anyZeros.get(0));
        assertEquals("any(one non-zero)", 1, anyOne.get(0));
    }

    @Test
    public void testArgmin() throws TornadoExecutionPlanException {
        float[] xv = values(SIZE, -10, 10, 6);
        xv[777] = -42f;
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argmin", MlxReductions::argmin, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals(777, output.get(0));
    }

    @Test
    public void testArgmax() throws TornadoExecutionPlanException {
        // A vocabulary-sized array, as in LLM greedy decoding.
        float[] xv = values(151936, -10, 10, 7);
        xv[98765] = 42f;
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argmax", MlxReductions::argmax, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals(98765, output.get(0));
    }

    // ---------------------------------------------------------------- one axis

    @Test
    public void testSumAxis() throws TornadoExecutionPlanException {
        axis(MlxReductions::sumAxis, TestMlxReductions::sumJava, -1, 1, 1e-4, 1e-3);
    }

    @Test
    public void testProdAxis() throws TornadoExecutionPlanException {
        axis(MlxReductions::prodAxis, TestMlxReductions::prodJava, 0.99f, 1.01f, 1e-4, 1e-4);
    }

    @Test
    public void testMaxAxis() throws TornadoExecutionPlanException {
        axis(MlxReductions::maxAxis, TestMlxReductions::maxJava, -1, 1, 0, 0);
    }

    @Test
    public void testMinAxis() throws TornadoExecutionPlanException {
        axis(MlxReductions::minAxis, TestMlxReductions::minJava, -1, 1, 0, 0);
    }

    @Test
    public void testMeanAxis() throws TornadoExecutionPlanException {
        axis(MlxReductions::meanAxis, TestMlxReductions::meanJava, -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testVarAxis() throws TornadoExecutionPlanException {
        axis((x, out, o, l, i) -> MlxReductions.varAxis(x, out, o, l, i, 0), v -> varJava(v, 0), -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testStdAxis() throws TornadoExecutionPlanException {
        axis((x, out, o, l, i) -> MlxReductions.stdAxis(x, out, o, l, i, 1), v -> Math.sqrt(varJava(v, 1)), -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testLogsumexpAxis() throws TornadoExecutionPlanException {
        axis(MlxReductions::logsumexpAxis, TestMlxReductions::logsumexpJava, -5, 5, 1e-5, 1e-5);
    }

    @Test
    public void testAllAxisAndAnyAxis() throws TornadoExecutionPlanException {
        // Rows of 8 make some rows all non-zero and some all zero.
        final int len = 8;
        float[] xv = sparse(OUTER * len, 8);
        for (int i = 0; i < len; i++) {
            xv[i] = 0f;
        }
        FloatArray x = FloatArray.fromArray(xv);
        ByteArray all = new ByteArray(OUTER);
        ByteArray any = new ByteArray(OUTER);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("all", MlxReductions::allAxis, x, all, OUTER, len, 1) //
                .libraryTask("any", MlxReductions::anyAxis, x, any, OUTER, len, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, all, any);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertBools(xv, OUTER, len, TestMlxReductions::allJava, all);
        assertBools(xv, OUTER, len, TestMlxReductions::anyJava, any);
    }

    @Test
    public void testArgminAxis() throws TornadoExecutionPlanException {
        float[] xv = values(OUTER * LEN, -10, 10, 9);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(OUTER);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argmin", MlxReductions::argminAxis, x, output, OUTER, LEN, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < OUTER; r++) {
            assertEquals("row " + r, argminJava(row(xv, r, LEN)), output.get(r));
        }
    }

    @Test
    public void testArgmaxRows() throws TornadoExecutionPlanException {
        final int rows = 6;
        final int cols = 1001;
        float[] xv = values(rows * cols, -10, 10, 10);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(rows);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argmax", MlxReductions::argmaxRows, x, output, rows, cols) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int r = 0; r < rows; r++) {
            assertEquals("row " + r, argmaxJava(row(xv, r, cols)), output.get(r));
        }
    }

    // ---------------------------------------------------------------- two adjacent axes

    @Test
    public void testSumAxes() throws TornadoExecutionPlanException {
        axes(MlxReductions::sumAxes, TestMlxReductions::sumJava, -1, 1, 1e-4, 1e-3);
    }

    @Test
    public void testProdAxes() throws TornadoExecutionPlanException {
        axes(MlxReductions::prodAxes, TestMlxReductions::prodJava, 0.99f, 1.01f, 1e-4, 1e-4);
    }

    @Test
    public void testMaxAxes() throws TornadoExecutionPlanException {
        axes(MlxReductions::maxAxes, TestMlxReductions::maxJava, -1, 1, 0, 0);
    }

    @Test
    public void testMinAxes() throws TornadoExecutionPlanException {
        axes(MlxReductions::minAxes, TestMlxReductions::minJava, -1, 1, 0, 0);
    }

    @Test
    public void testMeanAxes() throws TornadoExecutionPlanException {
        axes(MlxReductions::meanAxes, TestMlxReductions::meanJava, -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testVarAxes() throws TornadoExecutionPlanException {
        axes((x, out, o, l1, l2, i) -> MlxReductions.varAxes(x, out, o, l1, l2, i, 0), v -> varJava(v, 0), -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testStdAxes() throws TornadoExecutionPlanException {
        axes((x, out, o, l1, l2, i) -> MlxReductions.stdAxes(x, out, o, l1, l2, i, 1), v -> Math.sqrt(varJava(v, 1)), -1, 1, 1e-4, 1e-5);
    }

    @Test
    public void testLogsumexpAxes() throws TornadoExecutionPlanException {
        axes(MlxReductions::logsumexpAxes, TestMlxReductions::logsumexpJava, -5, 5, 1e-5, 1e-5);
    }

    @Test
    public void testAllAxesAndAnyAxes() throws TornadoExecutionPlanException {
        final int len1 = 2;
        final int len2 = 4;
        float[] xv = sparse(OUTER * len1 * len2, 11);
        for (int i = 0; i < len1 * len2; i++) {
            xv[i] = 0f;
        }
        FloatArray x = FloatArray.fromArray(xv);
        ByteArray all = new ByteArray(OUTER);
        ByteArray any = new ByteArray(OUTER);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("all", MlxReductions::allAxes, x, all, OUTER, len1, len2, 1) //
                .libraryTask("any", MlxReductions::anyAxes, x, any, OUTER, len1, len2, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, all, any);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertBools(xv, OUTER, len1 * len2, TestMlxReductions::allJava, all);
        assertBools(xv, OUTER, len1 * len2, TestMlxReductions::anyJava, any);
    }

    // ---------------------------------------------------------------- softmax

    @Test
    public void testSoftmax() throws TornadoExecutionPlanException {
        float[] xv = values(1000, -5, 5, 12);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("softmax", MlxReductions::softmax, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("softmax", softmaxJava(xv, xv.length), output, 1e-5, 1e-8);
    }

    @Test
    public void testSoftmaxRows() throws TornadoExecutionPlanException {
        final int rows = 5;
        final int cols = 333;
        float[] xv = values(rows * cols, -8, 8, 13);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("softmax", MlxReductions::softmaxRows, x, output, rows, cols) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("softmaxRows", softmaxJava(xv, cols), output, 1e-5, 1e-8);
    }

    @Test
    public void testSoftmaxLastTwoAxes() throws TornadoExecutionPlanException {
        final int d0 = 3;
        final int d1 = 4;
        final int d2 = 50;
        float[] xv = values(d0 * d1 * d2, -4, 4, 14);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("softmax", MlxReductions::softmaxLastTwoAxes, x, output, d0, d1, d2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("softmaxLastTwoAxes", softmaxJava(xv, d1 * d2), output, 1e-5, 1e-8);
    }

    // ---------------------------------------------------------------- other types and argument forms

    @Test
    public void testSoftmaxHalf() throws TornadoExecutionPlanException {
        HalfFloatArray x = half(values(1000, -5, 5, 15));
        HalfFloatArray output = new HalfFloatArray(1000);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("softmax", MlxReductions::softmax, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("softmax float16", softmaxJava(widen(x), 1000), output, 2e-3, 1e-6);
    }

    @Test
    public void testSumAxisHalf() throws TornadoExecutionPlanException {
        final int outer = 6;
        final int len = 50;
        HalfFloatArray x = half(values(outer * len, -1, 1, 16));
        HalfFloatArray output = new HalfFloatArray(outer);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sum", MlxReductions::sumAxis, x, output, outer, len, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("sumAxis float16", rowsJava(widen(x), outer, len, TestMlxReductions::sumJava), output, 3e-3, 1e-2);
    }

    @Test
    public void testLogsumexpBFloat16() throws TornadoExecutionPlanException {
        final int n = 4099;
        BFloat16Array x = bf16(values(n, -3, 3, 17));
        BFloat16Array output = new BFloat16Array(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("logsumexp", MlxReductions::logsumexp, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("logsumexp bfloat16", new double[] { logsumexpJava(row(widen(x), 0, n)) }, output, 1e-2, 1e-2);
    }

    @Test
    public void testSumAxesInt() throws TornadoExecutionPlanException {
        final int outer = 4;
        final int len1 = 5;
        final int len2 = 6;
        Random random = new Random(18);
        int[] xv = new int[outer * len1 * len2];
        for (int i = 0; i < xv.length; i++) {
            xv[i] = random.nextInt(19) - 9;
        }
        IntArray x = IntArray.fromArray(xv);
        IntArray output = new IntArray(outer);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sum", MlxReductions::sumAxes, x, output, outer, len1, len2, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int o = 0; o < outer; o++) {
            int sum = 0;
            for (int i = 0; i < len1 * len2; i++) {
                sum += xv[o * len1 * len2 + i];
            }
            assertEquals("row " + o, sum, output.get(o));
        }
    }

    @Test
    public void testAxisReductionOverInnerAxisFails() {
        // Only trailing axes reduce in place: inner > 1 has no TornadoVM/MLX kernel.
        FloatArray x = FloatArray.fromArray(values(4 * 8 * 3, -1, 1, 19));
        FloatArray output = new FloatArray(4 * 3);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sum", MlxReductions::sumAxis, x, output, 4, 8, 3) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        assertThrows(RuntimeException.class, () -> {
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }
        });
    }
}
