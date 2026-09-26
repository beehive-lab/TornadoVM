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
package uk.ac.manchester.tornado.mlx.benchmarks;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.mlx.MlxOptions;
import uk.ac.manchester.tornado.mlx.benchmarks.MlxBenchmarks.TaskAdder;
import uk.ac.manchester.tornado.mlx.benchmarks.MlxBenchmarks.Timing;
import uk.ac.manchester.tornado.mlx.jit.JitBlas;
import uk.ac.manchester.tornado.mlx.jit.JitElementwise;
import uk.ac.manchester.tornado.mlx.jit.JitFast;
import uk.ac.manchester.tornado.mlx.jit.JitQuantized;
import uk.ac.manchester.tornado.mlx.jit.JitReductions;
import uk.ac.manchester.tornado.mlx.provider.MlxC;
import uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;

/**
 * Per-operation evaluation: every bound MLX operation as an {@code apple/mlx} library task against
 * its KernelContext JIT baseline from {@code uk.ac.manchester.tornado.mlx.jit}, at a decode-sized
 * and a large shape. Both variants run as TornadoVM tasks on the same buffers; the report gives the
 * one-task execute() time and the marginal cost of one more task in a chain of
 * {@value MlxBenchmarks#CHAIN}.
 *
 * <pre>
 * tornado -m tornado.mlx/uk.ac.manchester.tornado.mlx.benchmarks.OpEvaluation [--only family] [--out dir] [--probe]
 * </pre>
 */
public final class OpEvaluation {

    private static final int LOCAL = 256;
    private static final int VOCAB = 151936;
    private static final int GROUP_SIZE = 64;

    private OpEvaluation() {
    }

    /**
     * One case. {@code mlx} is TornadoVM/MLX as it runs by default (in-place MLX kernels on shared buffers when
     * {@code inPlace}, otherwise the MLX C API); {@code mlxc} is the MLX C API on the GPU with its copies in and
     * out; {@code cpu} is MLX on the CPU; {@code javaUs} is sequential Java (NaN when the case has none).
     */
    record Result(String family, String op, String shape, String regime, String dtype, String jitKernel, Timing mlx, double mlxMarginalUs, Timing jit, double jitMarginalUs, double bytes,
            double flops, boolean inPlace, String device, Timing mlxc, double mlxcMarginalUs, Timing cpu, double cpuMarginalUs, double javaUs) {

        /** JIT time over MLX time, by marginal cost (above 1: MLX is faster). */
        double ratio() {
            return jitMarginalUs / mlxMarginalUs;
        }
    }

    private static final List<Result> RESULTS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static int graphCounter;
    private static String only;
    private static Path outDir;

    // ---------------------------------------------------------------- measurement

    private static double[] time(Object[] inputs, Object[] outputs, TaskAdder adder, boolean usesGrid, int chainLength) {
        String base = "ev" + (graphCounter++);
        Timing one = MlxBenchmarks.graphOf(base + "a", 1, inputs, outputs, adder, usesGrid);
        Timing chain = MlxBenchmarks.graphOf(base + "b", chainLength, inputs, outputs, adder, usesGrid);
        double marginal = Math.max((chain.medianUs() - one.medianUs()) / (chainLength - 1), 0.0);
        return new double[] { one.medianUs(), one.p10Us(), one.p90Us(), marginal };
    }

    private static final int JAVA_MIN_WARMUP = 3;
    private static final int JAVA_MAX_WARMUP = 5_000;
    private static final long JAVA_WARMUP_NS = 1_000_000_000L;
    private static final int JAVA_RUNS = 21;
    private static final long JAVA_BUDGET_NS = 3_000_000_000L;

    /**
     * Median of timed runs of a sequential Java reference. Warm-up runs for about a second (at least 3 calls) so that
     * the JVM has compiled the loop with C2 before timing starts; fewer timed runs are taken when a call is slow.
     */
    private static double timeJava(Runnable java) {
        long started = System.nanoTime();
        for (int i = 0; i < JAVA_MAX_WARMUP && (i < JAVA_MIN_WARMUP || System.nanoTime() - started < JAVA_WARMUP_NS); i++) {
            java.run();
        }
        long[] samples = new long[JAVA_RUNS];
        int count = 0;
        started = System.nanoTime();
        while (count < JAVA_RUNS && (count < 3 || System.nanoTime() - started < JAVA_BUDGET_NS)) {
            long t0 = System.nanoTime();
            java.run();
            samples[count++] = System.nanoTime() - t0;
        }
        long[] taken = Arrays.copyOf(samples, count);
        Arrays.sort(taken);
        return taken[count / 2] / 1e3;
    }

    private static double[] timeMlx(Object[] inputs, Object[] outputs, TaskAdder adder, int chainLength, MlxOptions options) {
        MlxLibraryProvider.setDefaultOptions(options);
        try {
            return time(inputs, outputs, adder, false, chainLength);
        } finally {
            MlxLibraryProvider.setDefaultOptions(null);
        }
    }

    /** MLX on the CPU, or NaN where MLX has no CPU implementation of the operation. */
    private static double[] timeCpu(Object[] inputs, Object[] outputs, TaskAdder adder, int chainLength) {
        try {
            return timeMlx(inputs, outputs, adder, chainLength, MlxOptions.cpu());
        } catch (RuntimeException | TornadoInternalError e) {
            System.out.println("  (no MLX CPU timing: " + e.getMessage() + ")");
            return new double[] { Double.NaN, Double.NaN, Double.NaN, Double.NaN };
        }
    }

    /** A CSV number, empty when not measured. */
    private static String num(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Timing timing(double[] t) {
        return new Timing(t[0], t[1], t[2]);
    }

    static void compare(String family, String op, String shape, String regime, String dtype, String jitKernel, Object[] inputs, Object[] outputs, TaskAdder mlx, TaskAdder jit,
            double bytes, double flops) {
        compare(family, op, shape, regime, dtype, jitKernel, inputs, outputs, mlx, jit, bytes, flops, MlxBenchmarks.CHAIN, null);
    }

    /** {@link #compare} with a sequential Java reference of the operation. */
    static void compare(String family, String op, String shape, String regime, String dtype, String jitKernel, Object[] inputs, Object[] outputs, TaskAdder mlx, TaskAdder jit,
            double bytes, double flops, Runnable java) {
        compare(family, op, shape, regime, dtype, jitKernel, inputs, outputs, mlx, jit, bytes, flops, MlxBenchmarks.CHAIN, java);
    }

    static void compare(String family, String op, String shape, String regime, String dtype, String jitKernel, Object[] inputs, Object[] outputs, TaskAdder mlx, TaskAdder jit,
            double bytes, double flops, int chainLength) {
        compare(family, op, shape, regime, dtype, jitKernel, inputs, outputs, mlx, jit, bytes, flops, chainLength, null);
    }

    /**
     * {@link #compare} with a shorter chain, for JIT baselines of many tasks: a TornadoVM task graph
     * holds at most 1024 nodes, which a chain of 8 multi-kernel operations can exceed.
     */
    static void compare(String family, String op, String shape, String regime, String dtype, String jitKernel, Object[] inputs, Object[] outputs, TaskAdder mlx, TaskAdder jit,
            double bytes, double flops, int chainLength, Runnable java) {
        if (!wants(family)) {
            return;
        }
        double[] m;
        double[] mc;
        double[] cpu;
        double[] j;
        boolean inPlace;
        MlxOptions.Device device;
        double javaUs;
        try {
            m = timeMlx(inputs, outputs, mlx, chainLength, MlxOptions.gpu());
            inPlace = MlxLibraryProvider.lastInPlace();
            device = MlxLibraryProvider.lastDevice();
            // Operations MLX only runs on the CPU, and ones without an in-place kernel, take the same path in every variant.
            mc = inPlace ? timeMlx(inputs, outputs, mlx, chainLength, MlxOptions.gpu().inPlaceKernels(false)) : m;
            cpu = device == MlxOptions.Device.CPU ? m : timeCpu(inputs, outputs, mlx, chainLength);
            j = time(inputs, outputs, jit, true, chainLength);
            javaUs = java == null ? Double.NaN : timeJava(java);
        } catch (RuntimeException | TornadoInternalError e) {
            // One broken case must not end a long run: report it and carry on.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String reason = root == e ? e.toString() : e + " (cause: " + root + ")";
            FAILURES.add(family + " " + op + " " + shape + ": " + reason);
            System.out.printf(Locale.ROOT, "  %-12s %-20s %-18s FAILED %s%n", family, op, shape, reason);
            return;
        }
        Result r = new Result(family, op, shape, regime, dtype, jitKernel, timing(m), m[3], timing(j), j[3], bytes, flops, inPlace, device.name().toLowerCase(Locale.ROOT), timing(mc), mc[3],
                timing(cpu), cpu[3], javaUs);
        RESULTS.add(r);
        saveCsv();
        System.out.printf(Locale.ROOT, "  %-12s %-20s %-18s %-7s %-3s %-8s mlx %9.1f us (+%8.1f)  mlx-c %9.1f (+%8.1f)  cpu %9.1f (+%8.1f)  jit %9.1f us (+%8.1f)  java %9.1f  jit/mlx %5.2fx%n",
                family, op, shape, regime, r.device(), inPlace ? "in-place" : "c-api", m[0], m[3], mc[0], mc[3], cpu[0], cpu[3], j[0], j[3], javaUs, r.ratio());
    }

    /** Whether a family is selected by {@code --only} (the fixed-cost probe always runs). */
    static boolean wants(String family) {
        return only == null || only.equalsIgnoreCase(family) || "floor".equals(family);
    }

    /** A JIT task with its worker grid. */
    static TaskAdder jit(Supplier<WorkerGrid> grid, TaskAdder task) {
        return (g, gs, gn, t) -> {
            gs.addWorkerGrid(gn + "." + t, grid.get());
            task.add(g, gs, gn, t);
        };
    }

    static WorkerGrid1D grid1D(int threads) {
        WorkerGrid1D grid = new WorkerGrid1D((threads + LOCAL - 1) / LOCAL * LOCAL);
        grid.setLocalWork(LOCAL, 1, 1);
        return grid;
    }

    static WorkerGrid1D groups(int groups, int threadsPerGroup) {
        WorkerGrid1D grid = new WorkerGrid1D(groups * threadsPerGroup);
        grid.setLocalWork(threadsPerGroup, 1, 1);
        return grid;
    }

    static FloatArray floats(int n, long seed) {
        return MlxBenchmarks.randomFloat(n, 1f, seed);
    }

    static IntArray randomInts(int n, long seed) {
        Random r = new Random(seed);
        IntArray a = new IntArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, r.nextInt());
        }
        return a;
    }

    private static String regime(int n) {
        return n <= 8192 ? "decode" : "large";
    }

    // ---------------------------------------------------------------- element-wise

    /** A sequential Java loop over the case's arrays. */
    interface JavaBinary {
        void run(FloatArray a, FloatArray b, FloatArray c);
    }

    interface JavaUnary {
        void run(FloatArray a, FloatArray out);
    }

    private static void binary(String op, LibraryTask3<FloatArray, FloatArray, FloatArray> mlx, Task5<KernelContext, FloatArray, FloatArray, FloatArray, Integer> jit, JavaBinary java) {
        for (int n : new int[] { 4096, 1 << 24 }) {
            FloatArray a = floats(n, 1);
            FloatArray b = MlxBenchmarks.randomFloat(n, 1f, 2);
            for (int i = 0; i < n; i++) {
                b.set(i, b.get(i) + 2f); // keep divisors away from zero
            }
            FloatArray c = new FloatArray(n);
            compare("elementwise", op, Integer.toString(n), regime(n), "f32", "JitElementwise#" + op, new Object[] { a, b }, new Object[] { c }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, b, c), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, jit, new KernelContext(), a, b, c, n)), 12.0 * n, n, () -> java.run(a, b, c));
        }
    }

    private static void unary(String op, LibraryTask2<FloatArray, FloatArray> mlx, Task4<KernelContext, FloatArray, FloatArray, Integer> jit, JavaUnary java) {
        for (int n : new int[] { 4096, 1 << 24 }) {
            FloatArray a = MlxBenchmarks.randomFloat(n, 1f, 3);
            for (int i = 0; i < n; i++) {
                a.set(i, a.get(i) + 1.5f); // positive, for sqrt and rsqrt
            }
            FloatArray out = new FloatArray(n);
            compare("elementwise", op, Integer.toString(n), regime(n), "f32", "JitElementwise#" + op, new Object[] { a }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, mlx, a, out), //
                    jit(() -> grid1D(n), (g, gs, gn, t) -> g.task(t, jit, new KernelContext(), a, out, n)), 8.0 * n, n, () -> java.run(a, out));
        }
    }

    /** The fixed per-task cost of each variant: a one-element add. */
    private static void floor() {
        FloatArray a = floats(1, 1);
        FloatArray b = floats(1, 2);
        FloatArray c = new FloatArray(1);
        compare("floor", "add", "1", "floor", "f32", "JitElementwise#add", new Object[] { a, b }, new Object[] { c }, //
                (g, gs, gn, t) -> g.libraryTask(t, Mlx::add, a, b, c), //
                jit(() -> grid1D(1), (g, gs, gn, t) -> g.task(t, JitElementwise::add, new KernelContext(), a, b, c, 1)), 12.0, 1.0, () -> JavaReference.add(a, b, c));
    }

    private static void elementwise() {
        binary("add", Mlx::add, JitElementwise::add, JavaReference::add);
        binary("subtract", Mlx::subtract, JitElementwise::subtract, JavaReference::subtract);
        binary("multiply", Mlx::multiply, JitElementwise::multiply, JavaReference::multiply);
        binary("divide", Mlx::divide, JitElementwise::divide, JavaReference::divide);
        binary("maximum", Mlx::maximum, JitElementwise::maximum, JavaReference::maximum);
        binary("minimum", Mlx::minimum, JitElementwise::minimum, JavaReference::minimum);
        unary("negative", Mlx::negative, JitElementwise::negative, JavaReference::negative);
        unary("exp", Mlx::exp, JitElementwise::exp, JavaReference::exp);
        unary("tanh", Mlx::tanh, JitElementwise::tanh, JavaReference::tanh);
        unary("erf", Mlx::erf, JitElementwise::erf, JavaReference::erf);
        unary("sigmoid", Mlx::sigmoid, JitElementwise::sigmoid, JavaReference::sigmoid);
        unary("sqrt", Mlx::sqrt, JitElementwise::sqrt, JavaReference::sqrt);
        unary("rsqrt", Mlx::rsqrt, JitElementwise::rsqrt, JavaReference::rsqrt);
        unary("square", Mlx::square, JitElementwise::square, JavaReference::square);
    }

    // ---------------------------------------------------------------- BLAS

    private static WorkerGrid1D gemmGrid(int m, int n) {
        return groups((m / JitBlas.GEMM_BLOCK) * (n / JitBlas.GEMM_BLOCK), JitBlas.GEMM_THREADS);
    }

    private static void blas() {
        for (int[] s : new int[][] { { 128, 4096, 4096 }, { 512, 4096, 4096 } }) {
            int m = s[0];
            int k = s[1];
            int n = s[2];
            FloatArray a = floats(m * k, 4);
            FloatArray b = floats(k * n, 5);
            FloatArray c = new FloatArray(m * n);
            compare("blas", "matmul", m + "x" + k + "x" + n, "prefill", "f32", "JitBlas#gemm", new Object[] { a, b }, new Object[] { c }, //
                    (g, gs, gn, t) -> g.libraryTask(t, Mlx::matmul, a, b, c, m, k, n), //
                    jit(() -> gemmGrid(m, n), (g, gs, gn, t) -> g.task(t, JitBlas::gemm, new KernelContext(), a, b, c, m, n, k)), 4.0 * (m * k + k * n + m * n), 2.0 * m * n * k,
                    () -> JavaReference.matmul(a, b, c, m, k, n));
        }
        for (int[] s : new int[][] { { 1, 4096, 4096 }, { 1, 4096, 14336 }, { 128, 4096, 4096 } }) {
            int m = s[0];
            int k = s[1];
            int n = s[2];
            FloatArray a = floats(m * k, 6);
            FloatArray w = floats(n * k, 7);
            FloatArray c = new FloatArray(m * n);
            boolean gemv = m == 1;
            TaskAdder jit = gemv //
                    ? jit(() -> groups(n, 32), (g, gs, gn, t) -> g.task(t, JitBlas::gemv, new KernelContext(), a, w, c, k)) //
                    : jit(() -> gemmGrid(m, n), (g, gs, gn, t) -> g.task(t, JitBlas::gemmTransposed, new KernelContext(), a, w, c, m, n, k));
            compare("blas", "matmulTransposed", m + "x" + k + "x" + n, gemv ? "decode" : "prefill", "f32", gemv ? "JitBlas#gemv" : "JitBlas#gemmTransposed", new Object[] { a, w },
                    new Object[] { c }, (g, gs, gn, t) -> g.libraryTask(t, Mlx::matmulTransposed, a, w, c, m, k, n), jit, 4.0 * (m * k + n * k + m * n), 2.0 * m * n * k,
                    () -> JavaReference.matmulTransposed(a, w, c, m, k, n));
        }
        for (int[] s : new int[][] { { 1, 4096, 4096 }, { 128, 4096, 4096 } }) {
            int m = s[0];
            int k = s[1];
            int n = s[2];
            HalfFloatArray a = MlxBenchmarks.randomHalf(m * k, 1f, 8);
            HalfFloatArray w = MlxBenchmarks.randomHalf(n * k, 1f, 9);
            HalfFloatArray cMlx = new HalfFloatArray(m * n);
            FloatArray cJit = new FloatArray(m * n);
            boolean gemv = m == 1;
            TaskAdder jit = gemv //
                    ? jit(() -> groups(n, 32), (g, gs, gn, t) -> g.task(t, JitBlas::gemvF16, new KernelContext(), a, w, cJit, k)) //
                    : jit(() -> gemmGrid(m, n), (g, gs, gn, t) -> g.task(t, JitBlas::gemmTransposedF16, new KernelContext(), a, w, cJit, m, n, k));
            compare("blas", "matmulTransposed", m + "x" + k + "x" + n, gemv ? "decode" : "prefill", "f16", gemv ? "JitBlas#gemvF16" : "JitBlas#gemmTransposedF16", new Object[] { a, w },
                    new Object[] { cMlx, cJit }, (g, gs, gn, t) -> g.libraryTask(t, Mlx::matmulTransposed, a, w, cMlx, m, k, n), jit, 2.0 * (m * k + n * k + m * n), 2.0 * m * n * k,
                    () -> JavaReference.matmulTransposed(a, w, cMlx, m, k, n));
        }
        addmmCase();
    }

    // ---------------------------------------------------------------- mlx.fast

    private static void fast() {
        final float eps = 1e-5f;
        for (int rows : new int[] { 1, 512 }) {
            final int dim = 4096;
            FloatArray x = floats(rows * dim, 13);
            FloatArray w = floats(dim, 14);
            FloatArray bias = floats(dim, 15);
            FloatArray out = new FloatArray(rows * dim);
            String shape = rows + "x" + dim;
            String regime = rows == 1 ? "decode" : "prefill";
            compare("fast", "rmsNorm", shape, regime, "f32", "JitFast#rmsNorm", new Object[] { x, w }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, Mlx::rmsNorm, x, w, out, rows, dim, eps), //
                    jit(() -> groups(rows, JitFast.NORM_THREADS), (g, gs, gn, t) -> g.task(t, JitFast::rmsNorm, new KernelContext(), x, w, out, dim, eps)), 8.0 * rows * dim + 4.0 * dim,
                    4.0 * rows * dim, () -> JavaReference.rmsNorm(x, w, out, rows, dim, eps));
            compare("fast", "layerNorm", shape, regime, "f32", "JitFast#layerNorm", new Object[] { x, w, bias }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, Mlx::layerNorm, x, w, bias, out, rows, dim, eps), //
                    jit(() -> groups(rows, JitFast.NORM_THREADS), (g, gs, gn, t) -> g.task(t, JitFast::layerNorm, new KernelContext(), x, w, bias, out, dim, eps)),
                    8.0 * rows * dim + 8.0 * dim, 8.0 * rows * dim, () -> JavaReference.layerNorm(x, w, bias, out, rows, dim, eps));
        }
        final int heads = 32;
        final int headDim = 128;
        final float base = 500000f;
        for (int seqLen : new int[] { 1, 512 }) {
            int rows = heads * seqLen;
            FloatArray x = floats(rows * headDim, 16);
            FloatArray out = new FloatArray(rows * headDim);
            IntArray offset = IntArray.fromElements(100);
            String shape = heads + "x" + seqLen + "x" + headDim;
            String regime = seqLen == 1 ? "decode" : "prefill";
            compare("fast", "rope", shape, regime, "f32", "JitFast#rope", new Object[] { x }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, Mlx::rope, x, out, 1, heads, seqLen, headDim, headDim, false, base, 1f, 100), //
                    jit(() -> groups(rows, headDim / 2), (g, gs, gn, t) -> g.task(t, JitFast::rope, new KernelContext(), x, out, rows, seqLen, headDim, headDim, 0, base, 1f, 100)),
                    8.0 * rows * headDim, 6.0 * rows * headDim, () -> JavaReference.rope(x, out, heads, seqLen, headDim, base, 1f, 100));
            compare("fast", "ropeDynamic", shape, regime, "f32", "JitFast#ropeDynamic", new Object[] { x, offset }, new Object[] { out }, //
                    (g, gs, gn, t) -> g.libraryTask(t, Mlx::ropeDynamic, x, offset, out, 1, heads, seqLen, headDim, headDim, false, base, 1f), //
                    jit(() -> groups(rows, headDim / 2), (g, gs, gn, t) -> g.task(t, JitFast::ropeDynamic, new KernelContext(), x, offset, out, rows, seqLen, headDim, headDim, 0, base,
                            1f)), 8.0 * rows * headDim, 6.0 * rows * headDim, () -> JavaReference.rope(x, out, heads, seqLen, headDim, base, 1f, offset.get(0)));
        }
        final int qHeads = 32;
        final int kvHeads = 8;
        final float scale = (float) (1.0 / Math.sqrt(headDim));
        for (int kvLen : new int[] { 1024, 4096 }) {
            FloatArray q = floats(qHeads * headDim, 17);
            FloatArray keys = floats(kvHeads * kvLen * headDim, 18);
            FloatArray values = floats(kvHeads * kvLen * headDim, 19);
            FloatArray out = new FloatArray(qHeads * headDim);
            compare("fast", "sdpa", qHeads + "/" + kvHeads + "x" + kvLen + "x" + headDim, "decode", "f32", "JitFast#attentionDecode", new Object[] { q, keys, values },
                    new Object[] { out }, (g, gs, gn, t) -> g.libraryTask(t, Mlx::scaledDotProductAttention, q, keys, values, out, 1, qHeads, kvHeads, 1, kvLen, headDim, scale, false), //
                    jit(() -> groups(qHeads, JitFast.ATTENTION_THREADS), (g, gs, gn, t) -> g.task(t, JitFast::attentionDecode, new KernelContext(), q, keys, values, out, qHeads, kvHeads,
                            kvLen, headDim, scale)), 8.0 * kvHeads * kvLen * headDim, 4.0 * qHeads * kvLen * headDim,
                    () -> JavaReference.attentionDecode(q, keys, values, out, qHeads, kvHeads, kvLen, headDim, scale));
        }
    }

    // ---------------------------------------------------------------- reductions

    private static void softmaxCase(String op, int rows, int cols, String shape, String regime, TaskAdder mlx, FloatArray x, FloatArray out) {
        compare("reductions", op, shape, regime, "f32", "JitReductions#softmaxRows", new Object[] { x }, new Object[] { out }, mlx, //
                jit(() -> groups(rows, JitReductions.ROW_THREADS), (g, gs, gn, t) -> g.task(t, JitReductions::softmaxRows, new KernelContext(), x, out, cols)), 8.0 * rows * cols,
                4.0 * rows * cols, () -> JavaReference.softmaxRows(x, out, rows, cols));
    }

    private static void reductions() {
        softmaxWhole();
        softmaxRowsCase();
        softmaxAxesCase();
        for (int[] s : new int[][] { { 1, VOCAB }, { 64, 32000 } }) {
            int rows = s[0];
            int cols = s[1];
            FloatArray x = floats(rows * cols, 23);
            IntArray out = new IntArray(rows);
            TaskAdder mlx = rows == 1 ? (g, gs, gn, t) -> g.libraryTask(t, Mlx::argmax, x, out) : (g, gs, gn, t) -> g.libraryTask(t, Mlx::argmaxRows, x, out, rows, cols);
            compare("reductions", rows == 1 ? "argmax" : "argmaxRows", rows == 1 ? Integer.toString(cols) : rows + "x" + cols, rows == 1 ? "decode" : "batch", "f32",
                    "JitReductions#argmaxRows", new Object[] { x }, new Object[] { out }, mlx, //
                    jit(() -> groups(rows, JitReductions.ROW_THREADS), (g, gs, gn, t) -> g.task(t, JitReductions::argmaxRows, new KernelContext(), x, out, cols)), 4.0 * rows * cols,
                    rows * (double) cols, () -> JavaReference.argmaxRows(x, out, rows, cols));
        }
        for (int[] s : new int[][] { { 1, VOCAB, 50 }, { 32, 32000, 40 } }) {
            int rows = s[0];
            int cols = s[1];
            int k = s[2];
            FloatArray x = floats(rows * cols, 24);
            FloatArray out = new FloatArray(rows * k);
            TaskAdder mlx = rows == 1 ? (g, gs, gn, t) -> g.libraryTask(t, Mlx::topk, x, out, k) : (g, gs, gn, t) -> g.libraryTask(t, Mlx::topkRows, x, out, rows, cols, k);
            compare("reductions", rows == 1 ? "topk" : "topkRows", (rows == 1 ? Integer.toString(cols) : rows + "x" + cols) + " k" + k, rows == 1 ? "decode" : "batch", "f32",
                    "JitReductions#topkRows", new Object[] { x }, new Object[] { out }, mlx, //
                    jit(() -> groups(rows, JitReductions.TOPK_THREADS), (g, gs, gn, t) -> g.task(t, JitReductions::topkRows, new KernelContext(), x, out, cols, k)),
                    4.0 * rows * cols, rows * (double) cols, () -> JavaReference.topkRows(x, out, rows, cols, k));
        }
    }

    // ---------------------------------------------------------------- quantization

    private static void quantized() {
        quantizeDequantize();
        for (int[] s : new int[][] { { 1, 4096, 4096, 4 }, { 1, 4096, 14336, 4 }, { 1, 4096, 4096, 8 }, { 16, 4096, 4096, 4 } }) {
            int m = s[0];
            int k = s[1];
            int n = s[2];
            int bits = s[3];
            int groups = n * k / GROUP_SIZE;
            FloatArray x = floats(m * k, 26);
            IntArray wq = randomInts(n * k * bits / 32, 27);
            FloatArray scales = MlxBenchmarks.randomFloat(groups, 0.01f, 28);
            FloatArray biases = MlxBenchmarks.randomFloat(groups, 0.01f, 29);
            FloatArray y = new FloatArray(m * n);
            compare("quantized", "quantizedMatmul", m + "x" + k + "x" + n + " q" + bits, m == 1 ? "decode" : "prefill", "f32", "JitQuantized#quantizedMatmul",
                    new Object[] { x, wq, scales, biases }, new Object[] { y }, //
                    (g, gs, gn, t) -> g.libraryTask(t, Mlx::quantizedMatmul, x, wq, scales, biases, y, m, k, n, GROUP_SIZE, bits), //
                    jit(() -> groups(m * n, JitQuantized.SIMD_THREADS), (g, gs, gn, t) -> g.task(t, JitQuantized::quantizedMatmul, new KernelContext(), x, wq, scales, biases, y, k, n,
                            GROUP_SIZE, bits)), (double) n * k * bits / 8 + 8.0 * groups + 4.0 * (m * k + m * n), 2.0 * m * n * k);
        }
        gatherQmmCase();
    }

    private static void addmmCase() {
        int m = 128;
        int k = 4096;
        int n = 4096;
        FloatArray cIn = floats(m * n, 10);
        FloatArray a = floats(m * k, 11);
        FloatArray b = floats(k * n, 12);
        FloatArray out = new FloatArray(m * n);
        compare("blas", "addmm", m + "x" + k + "x" + n, "prefill", "f32", "JitBlas#addmm", new Object[] { cIn, a, b }, new Object[] { out }, //
                (g, gs, gn, t) -> g.libraryTask(t, Mlx::addmm, cIn, a, b, out, m, k, n, 1.0f, 1.0f), //
                jit(() -> gemmGrid(m, n), (g, gs, gn, t) -> g.task(t, JitBlas::addmm, new KernelContext(), cIn, a, b, out, m, n, k, 1.0f, 1.0f)), 4.0 * (m * k + k * n + 2 * m * n),
                2.0 * m * n * k, () -> JavaReference.addmm(cIn, a, b, out, m, k, n, 1.0f, 1.0f));
    }

    private static void softmaxWhole() {
        FloatArray x = floats(VOCAB, 20);
        FloatArray out = new FloatArray(VOCAB);
        softmaxCase("softmax", 1, VOCAB, Integer.toString(VOCAB), "decode", (g, gs, gn, t) -> g.libraryTask(t, Mlx::softmax, x, out), x, out);
    }

    private static void softmaxRowsCase() {
        int rows = 512;
        int cols = 4096;
        FloatArray x = floats(rows * cols, 21);
        FloatArray out = new FloatArray(rows * cols);
        softmaxCase("softmaxRows", rows, cols, rows + "x" + cols, "prefill", (g, gs, gn, t) -> g.libraryTask(t, Mlx::softmaxRows, x, out, rows, cols), x, out);
    }

    private static void softmaxAxesCase() {
        int d0 = 32;
        int d1 = 8;
        int d2 = 512;
        FloatArray x = floats(d0 * d1 * d2, 22);
        FloatArray out = new FloatArray(d0 * d1 * d2);
        softmaxCase("softmaxLastTwoAxes", d0, d1 * d2, d0 + "x" + d1 + "x" + d2, "prefill", (g, gs, gn, t) -> g.libraryTask(t, Mlx::softmaxLastTwoAxes, x, out, d0, d1, d2), x, out);
    }

    private static void quantizeDequantize() {
        final int rows = 4096;
        final int cols = 4096;
        final int bits = 4;
        int groups = rows * cols / GROUP_SIZE;
        int words = rows * cols * bits / 32;
        FloatArray w = floats(rows * cols, 25);
        IntArray wq = new IntArray(words);
        FloatArray scales = new FloatArray(groups);
        FloatArray biases = new FloatArray(groups);
        FloatArray back = new FloatArray(rows * cols);
        String shape = rows + "x" + cols + " q4 g" + GROUP_SIZE;
        compare("quantized", "quantize", shape, "load", "f32", "JitQuantized#quantize", new Object[] { w }, new Object[] { wq, scales, biases }, //
                (g, gs, gn, t) -> g.libraryTask(t, Mlx::quantize, w, wq, scales, biases, rows, cols, GROUP_SIZE, bits), //
                jit(() -> grid1D(groups), (g, gs, gn, t) -> g.task(t, JitQuantized::quantize, new KernelContext(), w, wq, scales, biases, groups, GROUP_SIZE, bits)),
                4.0 * rows * cols + 4.0 * words + 8.0 * groups, 3.0 * rows * cols);
        compare("quantized", "dequantize", shape, "load", "f32", "JitQuantized#dequantize", new Object[] { wq, scales, biases }, new Object[] { back }, //
                (g, gs, gn, t) -> g.libraryTask(t, Mlx::dequantize, wq, scales, biases, back, rows, cols, GROUP_SIZE, bits), //
                jit(() -> grid1D(words), (g, gs, gn, t) -> g.task(t, JitQuantized::dequantize, new KernelContext(), wq, scales, biases, back, words, GROUP_SIZE, bits)),
                4.0 * rows * cols + 4.0 * words + 8.0 * groups, 2.0 * rows * cols);
    }

    private static void gatherQmmCase() {
        // MoE decode: 8 of 16 experts selected, one token.
        final int batches = 8;
        final int experts = 16;
        final int m = 1;
        final int k = 4096;
        final int n = 1536;
        final int bits = 4;
        int groups = experts * n * k / GROUP_SIZE;
        FloatArray x = floats(batches * m * k, 30);
        IntArray wq = randomInts(experts * n * k * bits / 32, 31);
        FloatArray scales = MlxBenchmarks.randomFloat(groups, 0.01f, 32);
        FloatArray biases = MlxBenchmarks.randomFloat(groups, 0.01f, 33);
        IntArray lhs = IntArray.fromElements(0, 1, 2, 3, 4, 5, 6, 7);
        IntArray rhs = IntArray.fromElements(3, 14, 0, 7, 9, 2, 11, 5);
        FloatArray y = new FloatArray(batches * m * n);
        compare("quantized", "gatherQmm", batches + "of" + experts + "x" + k + "x" + n + " q" + bits, "decode", "f32", "JitQuantized#gatherQmm",
                new Object[] { x, wq, scales, biases, lhs, rhs }, new Object[] { y }, //
                (g, gs, gn, t) -> g.libraryTask(t, Mlx::gatherQmm, x, wq, scales, biases, lhs, rhs, y, batches, experts, m, k, n, GROUP_SIZE, bits), //
                jit(() -> groups(batches * m * n, JitQuantized.SIMD_THREADS), (g, gs, gn, t) -> g.task(t, JitQuantized::gatherQmm, new KernelContext(), x, wq, scales, biases, lhs, rhs, y,
                        m, k, n, GROUP_SIZE, bits)), (double) batches * n * k * bits / 8 + 8.0 * batches * n * k / GROUP_SIZE, 2.0 * batches * m * n * k);
    }
    /** MLX alone (MLX-owned arrays, no TornadoVM) for the large element-wise shape. */
    private static void probe() {
        int n = 1 << 24;
        MemorySegment ma = MlxBenchmarks.mlxCopy(floats(n, 1), MlxBenchmarks.MLX_FLOAT32, n);
        MemorySegment mb = MlxBenchmarks.mlxCopy(floats(n, 2), MlxBenchmarks.MLX_FLOAT32, n);
        MlxBenchmarks.measureMlxAlone("add", Integer.toString(n), "f32", (st, ar) -> MlxBenchmarks.op(ar, res -> MlxC.mlx_add(res, ma, mb, st)), 12.0 * n, n);
        MlxBenchmarks.measureMlxAlone("exp", Integer.toString(n), "f32", (st, ar) -> MlxBenchmarks.op(ar, res -> MlxC.mlx_exp(res, ma, st)), 8.0 * n, n);
        MlxBenchmarks.measureMlxAlone("copy", Integer.toString(n), "f32", (st, ar) -> MlxBenchmarks.op(ar, res -> MlxC.mlx_contiguous(res, ma, true, st)), 8.0 * n, 0);
    }

    // ---------------------------------------------------------------- report

    /** A CSV field, quoted when it contains a comma or a quote. */
    private static String quote(String field) {
        return field.contains(",") || field.contains("\"") ? "\"" + field.replace("\"", "\"\"") + "\"" : field;
    }

    private static void writeCsv(PrintStream out) {
        out.println("family,op,shape,regime,dtype,jit_kernel,mlx_execute_us,mlx_p10_us,mlx_p90_us,mlx_marginal_us,jit_execute_us,jit_p10_us,jit_p90_us,jit_marginal_us,bytes,flops,"
                + "device,route,mlxc_execute_us,mlxc_marginal_us,cpu_execute_us,cpu_marginal_us,java_us");
        for (Result r : RESULTS) {
            out.printf(Locale.ROOT, "%s,%s,%s,%s,%s,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.0f,%.0f,%s,%s,%.1f,%.1f,%s,%s,%s%n", quote(r.family()), quote(r.op()), quote(r.shape()),
                    quote(r.regime()), quote(r.dtype()), quote(r.jitKernel()), r.mlx().medianUs(), r.mlx().p10Us(), r.mlx().p90Us(), r.mlxMarginalUs(), r.jit().medianUs(), r.jit().p10Us(),
                    r.jit().p90Us(), r.jitMarginalUs(), r.bytes(), r.flops(), r.device(), r.inPlace() ? "in-place" : "c-api", r.mlxc().medianUs(), r.mlxcMarginalUs(), num(r.cpu().medianUs()),
                    num(r.cpuMarginalUs()), num(r.javaUs()));
        }
    }

    /** Rewrites the CSV after every case, so that a run that dies part-way keeps what it measured. */
    private static void saveCsv() {
        if (outDir == null) {
            return;
        }
        try {
            Files.createDirectories(outDir);
            try (PrintStream csv = new PrintStream(Files.newOutputStream(outDir.resolve("op-evaluation.csv")))) {
                writeCsv(csv);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--only" -> only = args[++i];
                case "--out" -> outDir = Path.of(args[++i]);
                case "--probe" -> {
                    probe();
                    return;
                }
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        System.out.println("MLX vs KernelContext JIT, per operation (execute = one-task graph, + = marginal task in a chain of " + MlxBenchmarks.CHAIN + ")");
        floor();
        elementwise();
        blas();
        fast();
        reductions();
        quantized();
        Tier2Cases.all();
        Tier3Cases.all();
        if (!FAILURES.isEmpty()) {
            System.out.println(FAILURES.size() + " cases failed:");
            FAILURES.forEach(f -> System.out.println("  " + f));
        }
        if (outDir != null) {
            saveCsv();
            System.out.println("wrote " + outDir.resolve("op-evaluation.csv"));
        } else {
            writeCsv(System.out);
        }
    }
}
