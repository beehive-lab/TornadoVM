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

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.MlxLinalg;
import uk.ac.manchester.tornado.mlx.jit.JitLinalg;

/**
 * The MLX linear-algebra operations and their KernelContext JIT counterparts, run in one graph
 * on the same batched inputs and checked against double-precision Java references: cross
 * products and norms.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitLinalg
 * </code>
 */
public class TestJitLinalg extends MlxTestBase {

    static GridScheduler perMatrix(String task, int batch) {
        return new GridScheduler(task, TestJitReduce.groups(batch, JitLinalg.THREADS));
    }

    private static void execute(TaskGraph g, GridScheduler gs) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withGridScheduler(gs).execute();
        }
    }

    private static void both(String what, double[] expected, FloatArray mlx, FloatArray jit, double relTol, double absTol) {
        assertAllClose(what + " JIT", expected, jit, relTol, absTol);
        assertAllClose(what + " MLX", expected, mlx, relTol, absTol);
    }

    @Test
    public void testCrossAndNorms() throws TornadoExecutionPlanException {
        final int count = 1000;
        final int rows = 37;
        final int cols = 300;
        final int batch = 6;
        float[] av = values(count * 3, -3, 3, 251);
        float[] bv = values(count * 3, -3, 3, 252);
        float[] xv = values(rows * cols, -2, 2, 253);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray crossMlx = new FloatArray(count * 3);
        FloatArray crossJit = new FloatArray(count * 3);
        float[] ords = { 1f, 2f, 3f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY };
        TaskGraph g = new TaskGraph("cn").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, x) //
                .libraryTask("m", MlxLinalg::cross, a, b, crossMlx, count) //
                .task("j", JitLinalg::crossProduct, new KernelContext(), a, b, crossJit, count) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, crossMlx, crossJit);
        execute(g, new GridScheduler("cn.j", TestJitElementwise.grid1D(count)));
        double[] eCross = new double[count * 3];
        for (int i = 0; i < count; i++) {
            int p = 3 * i;
            eCross[p] = (double) av[p + 1] * bv[p + 2] - (double) av[p + 2] * bv[p + 1];
            eCross[p + 1] = (double) av[p + 2] * bv[p] - (double) av[p] * bv[p + 2];
            eCross[p + 2] = (double) av[p] * bv[p + 1] - (double) av[p + 1] * bv[p];
        }
        both("cross", eCross, crossMlx, crossJit, 1e-5, 1e-5);

        for (float ord : ords) {
            FloatArray nMlx = new FloatArray(rows);
            FloatArray nJit = new FloatArray(rows);
            TaskGraph gn = new TaskGraph("nr").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                    .libraryTask("m", MlxLinalg::norm, x, nMlx, rows, cols, ord) //
                    .task("j", JitLinalg::normRows, new KernelContext(), x, nJit, cols, ord) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, nMlx, nJit);
            execute(gn, perMatrix("nr.j", rows));
            double[] e = new double[rows];
            for (int r = 0; r < rows; r++) {
                double acc = ord == Float.NEGATIVE_INFINITY ? Double.MAX_VALUE : 0;
                for (int c = 0; c < cols; c++) {
                    double v = Math.abs(xv[r * cols + c]);
                    acc = ord == Float.POSITIVE_INFINITY ? Math.max(acc, v) : ord == Float.NEGATIVE_INFINITY ? Math.min(acc, v) : acc + Math.pow(v, ord);
                }
                e[r] = Double.isInfinite(ord) ? acc : Math.pow(acc, 1.0 / ord);
            }
            both("norm ord=" + ord, e, nMlx, nJit, 1e-4, 1e-5);
        }

        FloatArray l2Mlx = new FloatArray(rows);
        FloatArray l2Jit = new FloatArray(rows);
        FloatArray froMlx = new FloatArray(batch);
        FloatArray froJit = new FloatArray(batch);
        int mr = rows * cols / batch / 50;
        TaskGraph g2 = new TaskGraph("l2").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("m1", MlxLinalg::l2Norm, x, l2Mlx, rows, cols) //
                .libraryTask("m2", MlxLinalg::frobeniusNorm, x, froMlx, batch, mr, 50) //
                .task("j1", JitLinalg::normRows, new KernelContext(), x, l2Jit, cols, 2f) //
                .task("j2", JitLinalg::normRows, new KernelContext(), x, froJit, mr * 50, 2f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, l2Mlx, l2Jit, froMlx, froJit);
        GridScheduler gs = new GridScheduler();
        gs.addWorkerGrid("l2.j1", TestJitReduce.groups(rows, JitLinalg.THREADS));
        gs.addWorkerGrid("l2.j2", TestJitReduce.groups(batch, JitLinalg.THREADS));
        execute(g2, gs);
        double[] eL2 = new double[rows];
        for (int r = 0; r < rows; r++) {
            double s = 0;
            for (int c = 0; c < cols; c++) {
                s += (double) xv[r * cols + c] * xv[r * cols + c];
            }
            eL2[r] = Math.sqrt(s);
        }
        double[] eFro = new double[batch];
        for (int bb = 0; bb < batch; bb++) {
            double s = 0;
            for (int i = 0; i < mr * 50; i++) {
                s += (double) xv[bb * mr * 50 + i] * xv[bb * mr * 50 + i];
            }
            eFro[bb] = Math.sqrt(s);
        }
        both("l2Norm", eL2, l2Mlx, l2Jit, 1e-5, 1e-5);
        both("frobeniusNorm", eFro, froMlx, froJit, 1e-5, 1e-5);
    }

    @Test
    public void testHalfCrossAndNorm() throws TornadoExecutionPlanException {
        final int count = 100;
        float[] av = values(count * 3, -2, 2, 291);
        float[] bv = values(count * 3, -2, 2, 292);
        HalfFloatArray a = half(av);
        HalfFloatArray b = half(bv);
        HalfFloatArray cross = new HalfFloatArray(count * 3);
        HalfFloatArray l2 = new HalfFloatArray(count);
        run(new TaskGraph("hc").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("c", MlxLinalg::cross, a, b, cross, count) //
                .libraryTask("n", MlxLinalg::l2Norm, a, l2, count, 3) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cross, l2));
        float[] x = widen(a);
        float[] y = widen(b);
        double[] eCross = new double[count * 3];
        double[] eL2 = new double[count];
        for (int i = 0; i < count; i++) {
            int p = 3 * i;
            eCross[p] = x[p + 1] * y[p + 2] - x[p + 2] * y[p + 1];
            eCross[p + 1] = x[p + 2] * y[p] - x[p] * y[p + 2];
            eCross[p + 2] = x[p] * y[p + 1] - x[p + 1] * y[p];
            eL2[i] = Math.sqrt(x[p] * x[p] + x[p + 1] * x[p + 1] + x[p + 2] * x[p + 2]);
        }
        assertAllClose("cross float16", eCross, cross, 5e-3, 5e-3);
        assertAllClose("l2Norm float16", eL2, l2, 2e-3, 2e-3);
    }
}
