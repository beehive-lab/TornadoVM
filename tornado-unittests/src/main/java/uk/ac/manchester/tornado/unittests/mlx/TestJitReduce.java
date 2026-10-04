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

import java.util.Arrays;
import java.util.function.ToDoubleFunction;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxReduce;
import uk.ac.manchester.tornado.mlx.jit.JitReduce;

/**
 * The KernelContext JIT counterparts of the MLX reductions, float32: each case runs the MLX
 * library task and the JIT kernels in one graph on the same input, and checks both against a Java
 * reference. Every reduction is exercised over the whole array (small, and large enough for the
 * two-pass JIT path), over one axis (contiguous rows and strided columns) and over two adjacent
 * axes.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitReduce
 * </code>
 */
public class TestJitReduce extends MlxTestBase {

    static final int WHOLE = 0;
    static final int AXIS = 1;
    static final int AXES = 2;

    private static final int T = JitReduce.THREADS;

    /** {form, outer, len1, len2, inner}: the shapes every reduction is checked on. */
    private static final int[][] CASES = { { WHOLE, 1, 1027, 1, 1 }, { WHOLE, 1, 1 << 20, 1, 1 }, { AXIS, 37, 513, 1, 1 }, { AXIS, 7, 65, 1, 1 }, { AXES, 5, 12, 9, 1 }, { AXES, 3, 6, 5, 1 } };

    interface MlxForms {
        void add(TaskGraph g, int form, FloatArray x, FloatArray out, int outer, int len1, int len2, int inner);
    }

    interface JitForm {
        void add(TaskGraph g, GridScheduler gs, FloatArray x, FloatArray out, int outer, int len, int inner);
    }

    static WorkerGrid1D groups(int groups, int threads) {
        WorkerGrid1D grid = new WorkerGrid1D(groups * threads);
        grid.setLocalWork(threads, 1, 1);
        return grid;
    }

    /** Whole-array reductions large enough to be worth two passes. */
    static boolean twoPass(int outputs, int len) {
        return outputs == 1 && len > 16 * T;
    }

    /** Adds the JIT tasks for SUM ... MIN (or a mean) of x viewed as [outer, len, inner]. */
    static void jitReduce(TaskGraph g, GridScheduler gs, FloatArray x, FloatArray out, int outer, int len, int inner, int op, boolean mean) {
        int outputs = outer * inner;
        float scale = mean ? 1.0f / len : 1.0f;
        if (twoPass(outputs, len)) {
            FloatArray partials = new FloatArray(JitReduce.PARTIAL_GROUPS);
            g.task("p", JitReduce::reducePartial, new KernelContext(), x, partials, len, op);
            g.task("m", JitReduce::reduceRows, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS, op, scale);
            gs.addWorkerGrid(g.getTaskGraphName() + ".p", groups(JitReduce.PARTIAL_GROUPS, T));
            gs.addWorkerGrid(g.getTaskGraphName() + ".m", groups(1, T));
        } else if (inner == 1) {
            g.task("j", JitReduce::reduceRows, new KernelContext(), x, out, len, op, scale);
            gs.addWorkerGrid(g.getTaskGraphName() + ".j", groups(outer, T));
        } else {
            g.task("j", JitReduce::reduceColumns, new KernelContext(), x, out, outputs, len, inner, op, scale);
            gs.addWorkerGrid(g.getTaskGraphName() + ".j", TestJitElementwise.grid1D(outputs));
        }
    }

    static void jitLogsumexp(TaskGraph g, GridScheduler gs, FloatArray x, FloatArray out, int outer, int len, int inner) {
        int outputs = outer * inner;
        if (twoPass(outputs, len)) {
            FloatArray partials = new FloatArray(2 * JitReduce.PARTIAL_GROUPS);
            g.task("p", JitReduce::logsumexpPartial, new KernelContext(), x, partials, len);
            g.task("m", JitReduce::logsumexpMerge, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS);
            gs.addWorkerGrid(g.getTaskGraphName() + ".p", groups(JitReduce.PARTIAL_GROUPS, T));
            gs.addWorkerGrid(g.getTaskGraphName() + ".m", groups(1, T));
        } else {
            g.task("j", JitReduce::logsumexp, new KernelContext(), x, out, len, inner);
            gs.addWorkerGrid(g.getTaskGraphName() + ".j", groups(outputs, T));
        }
    }

    static void jitVariance(TaskGraph g, GridScheduler gs, FloatArray x, FloatArray out, int outer, int len, int inner, int ddof, boolean std) {
        int outputs = outer * inner;
        if (twoPass(outputs, len)) {
            FloatArray partials = new FloatArray(3 * JitReduce.PARTIAL_GROUPS);
            g.task("p", JitReduce::variancePartial, new KernelContext(), x, partials, len);
            g.task("m", JitReduce::varianceMerge, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS, len, ddof, std ? 1 : 0);
            gs.addWorkerGrid(g.getTaskGraphName() + ".p", groups(JitReduce.PARTIAL_GROUPS, T));
            gs.addWorkerGrid(g.getTaskGraphName() + ".m", groups(1, T));
        } else {
            g.task("j", JitReduce::variance, new KernelContext(), x, out, len, inner, ddof, std ? 1 : 0);
            gs.addWorkerGrid(g.getTaskGraphName() + ".j", groups(outputs, T));
        }
    }

    static void jitAllAny(TaskGraph g, GridScheduler gs, FloatArray x, ByteArray out, int outer, int len, int inner, int op) {
        int outputs = outer * inner;
        if (twoPass(outputs, len)) {
            FloatArray partials = new FloatArray(JitReduce.PARTIAL_GROUPS);
            g.task("p", JitReduce::reducePartial, new KernelContext(), x, partials, len, op);
            g.task("m", JitReduce::allAny, new KernelContext(), partials, out, JitReduce.PARTIAL_GROUPS, 1, op);
            gs.addWorkerGrid(g.getTaskGraphName() + ".p", groups(JitReduce.PARTIAL_GROUPS, T));
            gs.addWorkerGrid(g.getTaskGraphName() + ".m", groups(1, T));
        } else {
            g.task("j", JitReduce::allAny, new KernelContext(), x, out, len, inner, op);
            gs.addWorkerGrid(g.getTaskGraphName() + ".j", groups(outputs, T));
        }
    }

    static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /** The reference over each [o, :, j] slice of x viewed as [outer, len, inner]. */
    private static double[] reference(float[] x, int outer, int len, int inner, ToDoubleFunction<double[]> f) {
        double[] out = new double[outer * inner];
        double[] v = new double[len];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                for (int i = 0; i < len; i++) {
                    v[i] = x[(o * len + i) * inner + j];
                }
                out[o * inner + j] = f.applyAsDouble(v);
            }
        }
        return out;
    }

    private static double prod(double[] v) {
        double p = 1;
        for (double d : v) {
            p *= d;
        }
        return p;
    }

    private static double logsumexp(double[] v) {
        double m = Arrays.stream(v).max().getAsDouble();
        return m + Math.log(Arrays.stream(v).map(d -> Math.exp(d - m)).sum());
    }

    private static double variance(double[] v, int ddof) {
        double mean = Arrays.stream(v).average().getAsDouble();
        return Arrays.stream(v).map(d -> (d - mean) * (d - mean)).sum() / (v.length - ddof);
    }

    private static double median(double[] v) {
        double[] s = v.clone();
        Arrays.sort(s);
        int mid = s.length / 2;
        return (s.length & 1) == 1 ? s[mid] : 0.5 * (s[mid - 1] + s[mid]);
    }

    private static int argmin(double[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) {
            if (v[i] < v[best]) {
                best = i;
            }
        }
        return best;
    }

    private static void forms(String name, MlxForms mlx, JitForm jit, ToDoubleFunction<double[]> ref, float lo, float hi, double relTol, double absTol) throws TornadoExecutionPlanException {
        for (int[] c : CASES) {
            int form = c[0];
            int outer = c[1];
            int len = c[2] * c[3];
            int inner = c[4];
            float[] xv = values(outer * len * inner, lo, hi, 31L + len);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray outMlx = new FloatArray(outer * inner);
            FloatArray outJit = new FloatArray(outer * inner);
            TaskGraph g = new TaskGraph("r").transferToDevice(DataTransferMode.FIRST_EXECUTION, x);
            GridScheduler gs = new GridScheduler();
            mlx.add(g, form, x, outMlx, outer, c[2], c[3], inner);
            jit.add(g, gs, x, outJit, outer, len, inner);
            g.transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
                plan.withGridScheduler(gs).execute();
            }
            double[] expected = reference(xv, outer, len, inner, ref);
            String what = name + " " + Arrays.toString(c);
            assertAllClose(what + " JIT", expected, outJit, relTol, absTol);
            assertAllClose(what + " MLX", expected, outMlx, relTol, absTol);
        }
    }

    @Test
    public void testSum() throws TornadoExecutionPlanException {
        forms("sum", (g, form, x, out, o, l1, l2, in) -> {
            switch (form) {
                case WHOLE -> g.libraryTask("mlx", MlxReduce::sum, x, out);
                case AXIS -> g.libraryTask("mlx", MlxReduce::sumAxis, x, out, o, l1, in);
                default -> g.libraryTask("mlx", MlxReduce::sumAxes, x, out, o, l1, l2, in);
            }
        }, (g, gs, x, out, o, len, in) -> jitReduce(g, gs, x, out, o, len, in, JitReduce.SUM, false), v -> Arrays.stream(v).sum(), -1f, 1f, 0.0001, 0.001);
    }

    @Test
    public void testProd() throws TornadoExecutionPlanException {
        forms("prod", (g, form, x, out, o, l1, l2, in) -> {
            switch (form) {
                case WHOLE -> g.libraryTask("mlx", MlxReduce::prod, x, out);
                case AXIS -> g.libraryTask("mlx", MlxReduce::prodAxis, x, out, o, l1, in);
                default -> g.libraryTask("mlx", MlxReduce::prodAxes, x, out, o, l1, l2, in);
            }
        }, (g, gs, x, out, o, len, in) -> jitReduce(g, gs, x, out, o, len, in, JitReduce.PROD, false), v -> prod(v), 0.999f, 1.001f, 0.001, 1e-06);
    }

    @Test
    public void testMax() throws TornadoExecutionPlanException {
        forms("max", (g, form, x, out, o, l1, l2, in) -> {
            switch (form) {
                case WHOLE -> g.libraryTask("mlx", MlxReduce::max, x, out);
                case AXIS -> g.libraryTask("mlx", MlxReduce::maxAxis, x, out, o, l1, in);
                default -> g.libraryTask("mlx", MlxReduce::maxAxes, x, out, o, l1, l2, in);
            }
        }, (g, gs, x, out, o, len, in) -> jitReduce(g, gs, x, out, o, len, in, JitReduce.MAX, false), v -> Arrays.stream(v).max().getAsDouble(), -10f, 10f, 0, 0);
    }

    @Test
    public void testMin() throws TornadoExecutionPlanException {
        forms("min", (g, form, x, out, o, l1, l2, in) -> {
            switch (form) {
                case WHOLE -> g.libraryTask("mlx", MlxReduce::min, x, out);
                case AXIS -> g.libraryTask("mlx", MlxReduce::minAxis, x, out, o, l1, in);
                default -> g.libraryTask("mlx", MlxReduce::minAxes, x, out, o, l1, l2, in);
            }
        }, (g, gs, x, out, o, len, in) -> jitReduce(g, gs, x, out, o, len, in, JitReduce.MIN, false), v -> Arrays.stream(v).min().getAsDouble(), -10f, 10f, 0, 0);
    }

    @Test
    public void testMean() throws TornadoExecutionPlanException {
        forms("mean", (g, form, x, out, o, l1, l2, in) -> {
            switch (form) {
                case WHOLE -> g.libraryTask("mlx", MlxReduce::mean, x, out);
                case AXIS -> g.libraryTask("mlx", MlxReduce::meanAxis, x, out, o, l1, in);
                default -> g.libraryTask("mlx", MlxReduce::meanAxes, x, out, o, l1, l2, in);
            }
        }, (g, gs, x, out, o, len, in) -> jitReduce(g, gs, x, out, o, len, in, JitReduce.SUM, true), v -> Arrays.stream(v).average().getAsDouble(), -1f, 1f, 0.0001, 1e-05);
    }

    @Test
    public void testLogsumexp() throws TornadoExecutionPlanException {
        forms("logsumexp", (g, form, x, out, o, l1, l2, in) -> {
            switch (form) {
                case WHOLE -> g.libraryTask("mlx", MlxReduce::logsumexp, x, out);
                case AXIS -> g.libraryTask("mlx", MlxReduce::logsumexpAxis, x, out, o, l1, in);
                default -> g.libraryTask("mlx", MlxReduce::logsumexpAxes, x, out, o, l1, l2, in);
            }
        }, (g, gs, x, out, o, len, in) -> jitLogsumexp(g, gs, x, out, o, len, in), v -> logsumexp(v), -5f, 5f, 1e-05, 1e-05);
    }

    @Test
    public void testVarAndStd() throws TornadoExecutionPlanException {
        for (int ddof : new int[] { 0, 1 }) {
            final int d = ddof;
            forms("var ddof=" + d, (g, form, x, out, o, l1, l2, in) -> {
                switch (form) {
                    case WHOLE -> g.libraryTask("mlx", MlxReduce::var, x, out, d);
                    case AXIS -> g.libraryTask("mlx", MlxReduce::varAxis, x, out, o, l1, in, d);
                    default -> g.libraryTask("mlx", MlxReduce::varAxes, x, out, o, l1, l2, in, d);
                }
            }, (g, gs, x, out, o, len, in) -> jitVariance(g, gs, x, out, o, len, in, d, false), v -> variance(v, d), -2f, 2f, 1e-4, 1e-5);
            forms("std ddof=" + d, (g, form, x, out, o, l1, l2, in) -> {
                switch (form) {
                    case WHOLE -> g.libraryTask("mlx", MlxReduce::std, x, out, d);
                    case AXIS -> g.libraryTask("mlx", MlxReduce::stdAxis, x, out, o, l1, in, d);
                    default -> g.libraryTask("mlx", MlxReduce::stdAxes, x, out, o, l1, l2, in, d);
                }
            }, (g, gs, x, out, o, len, in) -> jitVariance(g, gs, x, out, o, len, in, d, true), v -> Math.sqrt(variance(v, d)), -2f, 2f, 1e-4, 1e-5);
        }
    }

    @Test
    public void testArgmin() throws TornadoExecutionPlanException {
        for (int[] c : CASES) {
            if (c[0] == AXES) {
                continue;
            }
            int outer = c[1];
            int len = c[2];
            int inner = c[4];
            float[] xv = values(outer * len * inner, -10, 10, 51L + len);
            FloatArray x = FloatArray.fromArray(xv);
            IntArray outMlx = new IntArray(outer * inner);
            IntArray outJit = new IntArray(outer * inner);
            TaskGraph g = new TaskGraph("am").transferToDevice(DataTransferMode.FIRST_EXECUTION, x);
            if (c[0] == WHOLE) {
                g.libraryTask("mlx", MlxReduce::argmin, x, outMlx);
            } else {
                g.libraryTask("mlx", MlxReduce::argminAxis, x, outMlx, outer, len, inner);
            }
            g.task("j", JitReduce::argmin, new KernelContext(), x, outJit, len, inner).transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
                plan.withGridScheduler(new GridScheduler("am.j", groups(outer * inner, T))).execute();
            }
            double[] expected = reference(xv, outer, len, inner, TestJitReduce::argmin);
            for (int i = 0; i < expected.length; i++) {
                assertEquals("argmin " + Arrays.toString(c) + " JIT " + i, (int) expected[i], outJit.get(i));
                assertEquals("argmin " + Arrays.toString(c) + " MLX " + i, (int) expected[i], outMlx.get(i));
            }
        }
    }

    @Test
    public void testAllAndAny() throws TornadoExecutionPlanException {
        for (int op : new int[] { JitReduce.ALL, JitReduce.ANY }) {
            for (int[] c : CASES) {
                int form = c[0];
                int outer = c[1];
                int len = c[2] * c[3];
                int inner = c[4];
                int n = outer * len * inner;
                float[] xv = new float[n];
                // Mostly zeros (for any) or mostly non-zeros (for all), with a few exceptions per slice.
                java.util.Random r = new java.util.Random(61L + n);
                for (int i = 0; i < n; i++) {
                    boolean rare = r.nextInt(4 * len) == 0;
                    xv[i] = (op == JitReduce.ANY) == rare ? 1.5f : 0.0f;
                }
                FloatArray x = FloatArray.fromArray(xv);
                ByteArray outMlx = new ByteArray(outer * inner);
                ByteArray outJit = new ByteArray(outer * inner);
                TaskGraph g = new TaskGraph("aa").transferToDevice(DataTransferMode.FIRST_EXECUTION, x);
                boolean any = op == JitReduce.ANY;
                if (any) {
                    switch (form) {
                        case WHOLE -> g.libraryTask("mlx", MlxReduce::any, x, outMlx);
                        case AXIS -> g.libraryTask("mlx", MlxReduce::anyAxis, x, outMlx, outer, c[2], inner);
                        default -> g.libraryTask("mlx", MlxReduce::anyAxes, x, outMlx, outer, c[2], c[3], inner);
                    }
                } else {
                    switch (form) {
                        case WHOLE -> g.libraryTask("mlx", MlxReduce::all, x, outMlx);
                        case AXIS -> g.libraryTask("mlx", MlxReduce::allAxis, x, outMlx, outer, c[2], inner);
                        default -> g.libraryTask("mlx", MlxReduce::allAxes, x, outMlx, outer, c[2], c[3], inner);
                    }
                }
                GridScheduler gs = new GridScheduler();
                jitAllAny(g, gs, x, outJit, outer, len, inner, op);
                g.transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
                try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
                    plan.withGridScheduler(gs).execute();
                }
                double[] expected = reference(xv, outer, len, inner, v -> any ? (Arrays.stream(v).anyMatch(d -> d != 0) ? 1 : 0) : (Arrays.stream(v).allMatch(d -> d != 0) ? 1 : 0));
                for (int i = 0; i < expected.length; i++) {
                    assertEquals((any ? "any " : "all ") + Arrays.toString(c) + " JIT " + i, (int) expected[i], outJit.get(i));
                    assertEquals((any ? "any " : "all ") + Arrays.toString(c) + " MLX " + i, (int) expected[i], outMlx.get(i));
                }
            }
        }
    }
}
