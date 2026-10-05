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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX random-sampling library tasks: bits, uniform, normal, randint, bernoulli,
 * truncated normal, Gumbel, Laplace, categorical and permutations. Draws come from an MLX key made
 * from the seed, so each test checks the distribution (range, moments, frequencies) of a large
 * sample rather than individual values. Skipped unless the default device is on the Metal backend
 * and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxRandom
 * </code>
 */
public class TestMlxRandom extends MlxTestBase {

    private static final int SIZE = 16384;
    private static final int SEED = 42;
    private static final int CLASSES = 5;
    private static final float[] LOGITS_ROW = { 0.5f, -1.0f, 1.5f, 0.0f, 0.25f };

    private static double mean(FloatArray a) {
        double s = 0;
        for (int i = 0; i < a.getSize(); i++) {
            s += a.get(i);
        }
        return s / a.getSize();
    }

    private static double variance(FloatArray a) {
        double m = mean(a);
        double s = 0;
        for (int i = 0; i < a.getSize(); i++) {
            s += (a.get(i) - m) * (a.get(i) - m);
        }
        return s / (a.getSize() - 1);
    }

    private static void assertMoments(String what, FloatArray a, double mean, double variance) {
        for (int i = 0; i < a.getSize(); i++) {
            assertTrue(what + " element " + i + " is finite", Float.isFinite(a.get(i)));
        }
        assertEquals(what + " mean", mean, mean(a), 0.05 * Math.sqrt(variance) + 0.01);
        assertEquals(what + " variance", variance, variance(a), 0.08 * variance);
    }

    private static void assertRange(String what, FloatArray a, float low, float high) {
        for (int i = 0; i < a.getSize(); i++) {
            assertTrue(what + " element " + i + " = " + a.get(i), a.get(i) >= low && a.get(i) <= high);
        }
    }

    /** Standard normal density. */
    private static double phi(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }

    private static double[] softmaxJava() {
        double[] p = new double[CLASSES];
        double s = 0;
        for (int c = 0; c < CLASSES; c++) {
            p[c] = Math.exp(LOGITS_ROW[c]);
            s += p[c];
        }
        for (int c = 0; c < CLASSES; c++) {
            p[c] /= s;
        }
        return p;
    }

    /** logits[rows, CLASSES]; row r is LOGITS_ROW shifted by r % 7, which leaves its softmax unchanged. */
    private static FloatArray logits(int rows) {
        FloatArray l = new FloatArray(rows * CLASSES);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < CLASSES; c++) {
                l.set(r * CLASSES + c, LOGITS_ROW[c] + r % 7);
            }
        }
        return l;
    }

    /** Checks class frequencies against the softmax of LOGITS_ROW. */
    private static void assertCategorical(String what, IntArray out) {
        double[] p = softmaxJava();
        int[] counts = new int[CLASSES];
        for (int i = 0; i < out.getSize(); i++) {
            int v = out.get(i);
            assertTrue(what + " element " + i + " = " + v, v >= 0 && v < CLASSES);
            counts[v]++;
        }
        for (int c = 0; c < CLASSES; c++) {
            assertEquals(what + " frequency of class " + c, p[c], counts[c] / (double) out.getSize(), 0.02);
        }
    }

    @Test
    public void testBits() throws TornadoExecutionPlanException {
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("bits", Mlx::bits, output, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int b = 0; b < 32; b++) {
            int ones = 0;
            for (int i = 0; i < SIZE; i++) {
                ones += (output.get(i) >> b) & 1;
            }
            assertEquals("bit " + b + " frequency", 0.5, ones / (double) SIZE, 0.02);
        }
    }

    @Test
    public void testUniform() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("uniform", Mlx::uniform, output, -2.0f, 3.0f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertRange("uniform", output, -2, 3);
        assertMoments("uniform", output, 0.5, 25.0 / 12);
    }

    @Test
    public void testNormal() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("normal", Mlx::normal, output, 1.0f, 2.0f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertMoments("normal", output, 1, 4);
    }

    @Test
    public void testNormalBroadcast() throws TornadoExecutionPlanException {
        // Even elements: mean -3, std 0.5; odd elements: mean 3, std 2.
        FloatArray loc = new FloatArray(SIZE);
        FloatArray scale = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            loc.set(i, i % 2 == 0 ? -3 : 3);
            scale.set(i, i % 2 == 0 ? 0.5f : 2);
        }
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, loc, scale) //
                .libraryTask("normal", Mlx::normalBroadcast, loc, scale, output, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int parity = 0; parity < 2; parity++) {
            FloatArray half = new FloatArray(SIZE / 2);
            for (int i = 0; i < SIZE / 2; i++) {
                half.set(i, output.get(2 * i + parity));
            }
            assertMoments("parity " + parity, half, parity == 0 ? -3 : 3, parity == 0 ? 0.25 : 4);
        }
    }

    @Test
    public void testRandint() throws TornadoExecutionPlanException {
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("randint", Mlx::randint, output, -3, 5, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        int[] counts = new int[8];
        for (int i = 0; i < SIZE; i++) {
            int v = output.get(i);
            assertTrue("element " + i + " = " + v, v >= -3 && v < 5);
            counts[v + 3]++;
        }
        for (int k = 0; k < 8; k++) {
            assertEquals("frequency of " + (k - 3), 0.125, counts[k] / (double) SIZE, 0.015);
        }
    }

    @Test
    public void testBernoulli() throws TornadoExecutionPlanException {
        // p cycles through 0, 1/4, 1/2, 3/4 and 1.
        FloatArray p = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            p.set(i, (i % 5) / 4.0f);
        }
        ByteArray output = new ByteArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, p) //
                .libraryTask("bernoulli", Mlx::bernoulli, p, output, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        int[] ones = new int[5];
        for (int i = 0; i < SIZE; i++) {
            assertTrue("element " + i, output.get(i) == 0 || output.get(i) == 1);
            ones[i % 5] += output.get(i);
        }
        for (int k = 0; k < 5; k++) {
            assertEquals("p=" + k / 4.0, k / 4.0, ones[k] / (SIZE / 5.0), 0.035);
        }
    }

    @Test
    public void testTruncatedNormal() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("truncatedNormal", Mlx::truncatedNormal, output, -1.0f, 2.0f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Moments of the standard normal truncated to [-1, 2]; z = Phi(2) - Phi(-1).
        double z = 0.8185946141203637;
        double m = (phi(-1) - phi(2)) / z;
        double v = 1 + (-1 * phi(-1) - 2 * phi(2)) / z - m * m;
        assertRange("truncatedNormal", output, -1, 2);
        assertMoments("truncatedNormal", output, m, v);
    }

    @Test
    public void testGumbel() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("gumbel", Mlx::gumbel, output, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Mean: the Euler-Mascheroni constant; variance pi^2 / 6.
        assertMoments("gumbel", output, 0.5772156649, Math.PI * Math.PI / 6);
    }

    @Test
    public void testLaplace() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("laplace", Mlx::laplace, output, 1.0f, 0.5f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // Variance 2 * scale^2.
        assertMoments("laplace", output, 1, 0.5);
    }

    @Test
    public void testCategorical() throws TornadoExecutionPlanException {
        // One draw per row of SIZE rows.
        FloatArray l = logits(SIZE);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, l) //
                .libraryTask("categorical", Mlx::categorical, l, output, SIZE, CLASSES, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertCategorical("categorical", output);
    }

    @Test
    public void testCategoricalSamples() throws TornadoExecutionPlanException {
        // SIZE / 4 draws from each of 4 rows, as out[rows, samples].
        final int rows = 4;
        final int samples = SIZE / rows;
        FloatArray l = logits(rows);
        IntArray output = new IntArray(rows * samples);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, l) //
                .libraryTask("categorical", Mlx::categoricalSamples, l, output, rows, CLASSES, samples, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertCategorical("categoricalSamples", output);
    }

    @Test
    public void testCategoricalShape() throws TornadoExecutionPlanException {
        // The same draws laid out as out[samples, rows].
        final int rows = 4;
        final int samples = SIZE / rows;
        FloatArray l = logits(rows);
        IntArray output = new IntArray(samples * rows);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, l) //
                .libraryTask("categorical", Mlx::categoricalShape, l, output, rows, CLASSES, samples, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertCategorical("categoricalShape", output);
    }

    @Test
    public void testPermutationArange() throws TornadoExecutionPlanException {
        final int n = 2000;
        IntArray output = new IntArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("permutation", Mlx::permutationArange, output, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        boolean[] seen = new boolean[n];
        int fixed = 0;
        for (int i = 0; i < n; i++) {
            int v = output.get(i);
            assertTrue("element " + i + " = " + v, v >= 0 && v < n && !seen[v]);
            seen[v] = true;
            fixed += v == i ? 1 : 0;
        }
        // A random permutation has one fixed point on average.
        assertTrue(fixed + " fixed points", fixed < 10);
    }

    @Test
    public void testSameSeedSameValues() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(SIZE);
        FloatArray b = new FloatArray(SIZE);
        FloatArray c = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .libraryTask("a", Mlx::uniform, a, 0.0f, 1.0f, 7) //
                .libraryTask("b", Mlx::uniform, b, 0.0f, 1.0f, 7) //
                .libraryTask("c", Mlx::uniform, c, 0.0f, 1.0f, 8) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        int same = 0;
        for (int i = 0; i < SIZE; i++) {
            assertEquals("same seed, element " + i, a.get(i), b.get(i), 0.0f);
            same += a.get(i) == c.get(i) ? 1 : 0;
        }
        assertTrue("different seeds share " + same + " values", same < 10);
    }
}
