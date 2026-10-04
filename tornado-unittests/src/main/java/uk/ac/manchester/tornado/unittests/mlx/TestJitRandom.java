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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxRandom;
import uk.ac.manchester.tornado.mlx.jit.JitRandom;
import uk.ac.manchester.tornado.mlx.jit.JitSort;

/**
 * The MLX random samplers and their KernelContext JIT counterparts. The two generators draw
 * different values, so each is checked on its own against the distribution: ranges, means and
 * variances, frequencies, covariances and permutation validity, on enough samples that the
 * tolerances are several standard errors wide.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitRandom
 * </code>
 */
public class TestJitRandom extends MlxTestBase {

    private static final int N = 16384;
    private static final int SEED = 42;

    private static void execute(TaskGraph g, Object... grids) throws TornadoExecutionPlanException {
        GridScheduler gs = new GridScheduler();
        for (int i = 0; i < grids.length; i += 2) {
            gs.addWorkerGrid(g.getTaskGraphName() + "." + grids[i], (WorkerGrid) grids[i + 1]);
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withGridScheduler(gs).execute();
        }
    }

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

    /** Runs a graph whose JIT task "j" takes one thread per output element. */
    private static void runPair(TaskGraph g, int threads) throws TornadoExecutionPlanException {
        execute(g, "j", TestJitElementwise.grid1D(threads));
    }

    @Test
    public void testBits() throws TornadoExecutionPlanException {
        IntArray outMlx = new IntArray(N);
        IntArray outJit = new IntArray(N);
        TaskGraph g = new TaskGraph("bits") //
                .libraryTask("m", MlxRandom::bits, outMlx, SEED) //
                .task("j", JitRandom::bits, new KernelContext(), outJit, N, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        for (IntArray out : new IntArray[] { outMlx, outJit }) {
            for (int b = 0; b < 32; b++) {
                int ones = 0;
                for (int i = 0; i < N; i++) {
                    ones += (out.get(i) >> b) & 1;
                }
                assertEquals((out == outMlx ? "MLX" : "JIT") + " bit " + b + " frequency", 0.5, ones / (double) N, 0.02);
            }
        }
    }

    @Test
    public void testUniform() throws TornadoExecutionPlanException {
        FloatArray outMlx = new FloatArray(N);
        FloatArray outJit = new FloatArray(N);
        TaskGraph g = new TaskGraph("uni") //
                .libraryTask("m", MlxRandom::uniform, outMlx, -2.0f, 3.0f, SEED) //
                .task("j", JitRandom::uniformRange, new KernelContext(), outJit, N, -2.0f, 3.0f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        assertRange("uniform MLX", outMlx, -2, 3);
        assertRange("uniform JIT", outJit, -2, 3);
        assertMoments("uniform MLX", outMlx, 0.5, 25.0 / 12);
        assertMoments("uniform JIT", outJit, 0.5, 25.0 / 12);
    }

    @Test
    public void testNormal() throws TornadoExecutionPlanException {
        FloatArray outMlx = new FloatArray(N);
        FloatArray outJit = new FloatArray(N);
        FloatArray unused = new FloatArray(1);
        TaskGraph g = new TaskGraph("nrm") //
                .libraryTask("m", MlxRandom::normal, outMlx, 1.0f, 2.0f, SEED) //
                .task("j", JitRandom::normal, new KernelContext(), outJit, unused, unused, N, 1.0f, 2.0f, 0, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        assertMoments("normal MLX", outMlx, 1, 4);
        assertMoments("normal JIT", outJit, 1, 4);
    }

    @Test
    public void testNormalBroadcast() throws TornadoExecutionPlanException {
        FloatArray loc = new FloatArray(N);
        FloatArray scale = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            loc.set(i, i % 2 == 0 ? -3 : 3);
            scale.set(i, i % 2 == 0 ? 0.5f : 2);
        }
        FloatArray outMlx = new FloatArray(N);
        FloatArray outJit = new FloatArray(N);
        TaskGraph g = new TaskGraph("nrmb").transferToDevice(DataTransferMode.FIRST_EXECUTION, loc, scale) //
                .libraryTask("m", MlxRandom::normalBroadcast, loc, scale, outMlx, SEED) //
                .task("j", JitRandom::normal, new KernelContext(), outJit, loc, scale, N, 0.0f, 1.0f, 1, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        for (FloatArray out : new FloatArray[] { outMlx, outJit }) {
            String who = out == outMlx ? "MLX" : "JIT";
            for (int parity = 0; parity < 2; parity++) {
                FloatArray half = new FloatArray(N / 2);
                for (int i = 0; i < N / 2; i++) {
                    half.set(i, out.get(2 * i + parity));
                }
                assertMoments("normalBroadcast " + who + " parity " + parity, half, parity == 0 ? -3 : 3, parity == 0 ? 0.25 : 4);
            }
        }
    }

    @Test
    public void testBernoulli() throws TornadoExecutionPlanException {
        FloatArray p = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            p.set(i, (i % 5) / 4.0f);
        }
        ByteArray outMlx = new ByteArray(N);
        ByteArray outJit = new ByteArray(N);
        TaskGraph g = new TaskGraph("brn").transferToDevice(DataTransferMode.FIRST_EXECUTION, p) //
                .libraryTask("m", MlxRandom::bernoulli, p, outMlx, SEED) //
                .task("j", JitRandom::bernoulli, new KernelContext(), p, outJit, N, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        for (ByteArray out : new ByteArray[] { outMlx, outJit }) {
            String who = out == outMlx ? "MLX" : "JIT";
            int[] ones = new int[5];
            for (int i = 0; i < N; i++) {
                assertTrue(who + " bernoulli element " + i, out.get(i) == 0 || out.get(i) == 1);
                ones[i % 5] += out.get(i);
            }
            for (int k = 0; k < 5; k++) {
                assertEquals(who + " bernoulli p=" + k / 4.0, k / 4.0, ones[k] / (N / 5.0), 0.035);
            }
        }
    }

    @Test
    public void testRandint() throws TornadoExecutionPlanException {
        IntArray outMlx = new IntArray(N);
        IntArray outJit = new IntArray(N);
        TaskGraph g = new TaskGraph("rint") //
                .libraryTask("m", MlxRandom::randint, outMlx, -3, 5, SEED) //
                .task("j", JitRandom::randint, new KernelContext(), outJit, N, -3, 5, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        for (IntArray out : new IntArray[] { outMlx, outJit }) {
            String who = out == outMlx ? "MLX" : "JIT";
            int[] counts = new int[8];
            for (int i = 0; i < N; i++) {
                int v = out.get(i);
                assertTrue(who + " randint element " + i + " = " + v, v >= -3 && v < 5);
                counts[v + 3]++;
            }
            for (int k = 0; k < 8; k++) {
                assertEquals(who + " randint frequency of " + (k - 3), 0.125, counts[k] / (double) N, 0.015);
            }
        }
    }

    private static double phi(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }

    @Test
    public void testTruncatedNormal() throws TornadoExecutionPlanException {
        FloatArray outMlx = new FloatArray(N);
        FloatArray outJit = new FloatArray(N);
        TaskGraph g = new TaskGraph("tnrm") //
                .libraryTask("m", MlxRandom::truncatedNormal, outMlx, -1.0f, 2.0f, SEED) //
                .task("j", JitRandom::truncatedNormal, new KernelContext(), outJit, N, -1.0f, 2.0f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        // Phi(2) - Phi(-1)
        double z = 0.8185946141203637;
        double m = (phi(-1) - phi(2)) / z;
        double v = 1 + (-1 * phi(-1) - 2 * phi(2)) / z - m * m;
        assertRange("truncatedNormal MLX", outMlx, -1, 2);
        assertRange("truncatedNormal JIT", outJit, -1, 2);
        assertMoments("truncatedNormal MLX", outMlx, m, v);
        assertMoments("truncatedNormal JIT", outJit, m, v);
    }

    @Test
    public void testGumbel() throws TornadoExecutionPlanException {
        FloatArray outMlx = new FloatArray(N);
        FloatArray outJit = new FloatArray(N);
        TaskGraph g = new TaskGraph("gmb") //
                .libraryTask("m", MlxRandom::gumbel, outMlx, SEED) //
                .task("j", JitRandom::gumbel, new KernelContext(), outJit, N, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        assertMoments("gumbel MLX", outMlx, 0.5772156649, Math.PI * Math.PI / 6);
        assertMoments("gumbel JIT", outJit, 0.5772156649, Math.PI * Math.PI / 6);
    }

    @Test
    public void testLaplace() throws TornadoExecutionPlanException {
        FloatArray outMlx = new FloatArray(N);
        FloatArray outJit = new FloatArray(N);
        TaskGraph g = new TaskGraph("lpl") //
                .libraryTask("m", MlxRandom::laplace, outMlx, 1.0f, 0.5f, SEED) //
                .task("j", JitRandom::laplace, new KernelContext(), outJit, N, 1.0f, 0.5f, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        runPair(g, N);
        assertMoments("laplace MLX", outMlx, 1, 0.5);
        assertMoments("laplace JIT", outJit, 1, 0.5);
    }

    private static final int CLASSES = 5;
    private static final float[] LOGITS_ROW = { 0.5f, -1.0f, 1.5f, 0.0f, 0.25f };

    private static double[] softmax() {
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

    /** logits [rows, CLASSES], row r shifted by r (softmax unchanged) so rows are distinct inputs. */
    private static FloatArray logits(int rows) {
        FloatArray l = new FloatArray(rows * CLASSES);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < CLASSES; c++) {
                l.set(r * CLASSES + c, LOGITS_ROW[c] + r % 7);
            }
        }
        return l;
    }

    /** Checks class frequencies against the softmax (every row has the same softmax). */
    private static void assertCategorical(String what, IntArray out) {
        double[] p = softmax();
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
    public void testCategorical() throws TornadoExecutionPlanException {
        int rows = 4;
        int samples = N / rows;
        FloatArray l1 = logits(N);
        FloatArray l4 = logits(rows);
        IntArray one = new IntArray(N);
        IntArray many = new IntArray(rows * samples);
        IntArray shaped = new IntArray(samples * rows);
        IntArray outJit = new IntArray(samples * rows);
        TaskGraph g = new TaskGraph("cat").transferToDevice(DataTransferMode.FIRST_EXECUTION, l1, l4) //
                .libraryTask("m1", MlxRandom::categorical, l1, one, N, CLASSES, SEED) //
                .libraryTask("m2", MlxRandom::categoricalSamples, l4, many, rows, CLASSES, samples, SEED) //
                .libraryTask("m3", MlxRandom::categoricalShape, l4, shaped, rows, CLASSES, samples, SEED) //
                .task("j", JitRandom::categorical, new KernelContext(), l4, outJit, rows, CLASSES, samples, SEED) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, one, many, shaped, outJit);
        execute(g, "j", TestJitReduce.groups(samples * rows, JitRandom.THREADS));
        assertCategorical("categorical MLX", one);
        assertCategorical("categoricalSamples MLX", many);
        assertCategorical("categoricalShape MLX", shaped);
        assertCategorical("categorical JIT", outJit);
    }

    /**
     * A wide row (more classes than threads, so every thread strides over several) whose softmax
     * puts most of its mass on four classes spread across the row, including the last one. The
     * split-row JIT kernels (7 uneven slices) must draw exactly what the single-pass kernel draws.
     */
    @Test
    public void testCategoricalWideRow() throws TornadoExecutionPlanException {
        int classes = 50000;
        int samples = 4096;
        int[] hot = { 3, 12345, 31000, classes - 1 };
        float[] hotLogits = { 12.0f, 11.0f, 10.5f, 11.5f };
        FloatArray logits = new FloatArray(classes);
        for (int k = 0; k < hot.length; k++) {
            logits.set(hot[k], hotLogits[k]);
        }
        double sum = classes - hot.length;
        for (float l : hotLogits) {
            sum += Math.exp(l);
        }
        int chunks = 7;
        IntArray outJit = new IntArray(samples);
        FloatArray partialValue = new FloatArray(samples * chunks);
        IntArray partialClass = new IntArray(samples * chunks);
        IntArray outChunked = new IntArray(samples);
        TaskGraph g = new TaskGraph("catw").transferToDevice(DataTransferMode.FIRST_EXECUTION, logits) //
                .task("j", JitRandom::categorical, new KernelContext(), logits, outJit, 1, classes, samples, SEED) //
                .task("p", JitRandom::categoricalChunked, new KernelContext(), logits, partialValue, partialClass, 1, classes, chunks, SEED) //
                .task("r", JitRandom::categoricalMerge, new KernelContext(), partialValue, partialClass, outChunked, samples, chunks, classes) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outJit, outChunked);
        execute(g, "j", TestJitReduce.groups(samples, JitRandom.THREADS), "p", TestJitReduce.groups(samples * chunks, JitRandom.THREADS), "r", TestJitElementwise.grid1D(samples));
        for (int i = 0; i < samples; i++) {
            assertEquals("chunked draw " + i, outJit.get(i), outChunked.get(i));
        }
        for (IntArray out : new IntArray[] { outJit }) {
            String who = "JIT";
            int[] counts = new int[hot.length + 1];
            for (int i = 0; i < samples; i++) {
                int v = out.get(i);
                assertTrue(who + " element " + i + " = " + v, v >= 0 && v < classes);
                int k = 0;
                while (k < hot.length && hot[k] != v) {
                    k++;
                }
                counts[k]++;
            }
            for (int k = 0; k <= hot.length; k++) {
                double p = k < hot.length ? Math.exp(hotLogits[k]) / sum : (classes - hot.length) / sum;
                assertEquals(who + " frequency of " + (k < hot.length ? "class " + hot[k] : "the other classes"), p, counts[k] / (double) samples, 0.03);
            }
        }
    }

    @Test
    public void testPermutation() throws TornadoExecutionPlanException {
        int n = 2000;
        IntArray arangeMlx = new IntArray(n);
        FloatArray keys = new FloatArray(n);
        FloatArray values = new FloatArray(n);
        IntArray indicesJit = new IntArray(n);
        TaskGraph g = new TaskGraph("perm") //
                .libraryTask("m2", MlxRandom::permutationArange, arangeMlx, SEED) //
                .task("k", JitRandom::sortKeys, new KernelContext(), keys, n, SEED) //
                .task("s", JitSort::sortSlices, new KernelContext(), keys, values, indicesJit, n, 1, TestJitReduce.nextPowerOfTwo(n), 0, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arangeMlx, indicesJit);
        execute(g, "k", TestJitElementwise.grid1D(n), "s", TestJitReduce.groups(1, JitSort.THREADS));
        int[] b = new int[n];
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            b[i] = arangeMlx.get(i);
            c[i] = indicesJit.get(i);
        }
        assertPermutation("permutationArange MLX", b, n);
        assertPermutation("permutation JIT", c, n);
    }

    private static void assertPermutation(String what, int[] v, int n) {
        boolean[] seen = new boolean[n];
        int fixed = 0;
        for (int i = 0; i < n; i++) {
            assertTrue(what + " element " + i + " = " + v[i], v[i] >= 0 && v[i] < n && !seen[v[i]]);
            seen[v[i]] = true;
            fixed += v[i] == i ? 1 : 0;
        }
        // A random permutation has one fixed point on average.
        assertTrue(what + " has " + fixed + " fixed points", fixed < 10);
    }

    @Test
    public void testSeedsDiffer() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        FloatArray b = new FloatArray(N);
        FloatArray c = new FloatArray(N);
        TaskGraph g = new TaskGraph("seeds") //
                .libraryTask("m1", MlxRandom::uniform, a, 0.0f, 1.0f, 7) //
                .libraryTask("m2", MlxRandom::uniform, b, 0.0f, 1.0f, 7) //
                .libraryTask("m3", MlxRandom::uniform, c, 0.0f, 1.0f, 8) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.execute();
        }
        int same = 0;
        for (int i = 0; i < N; i++) {
            assertEquals("same seed, element " + i, a.get(i), b.get(i), 0.0f);
            same += a.get(i) == c.get(i) ? 1 : 0;
        }
        assertTrue("different seeds share " + same + " values", same < 10);
    }
}
