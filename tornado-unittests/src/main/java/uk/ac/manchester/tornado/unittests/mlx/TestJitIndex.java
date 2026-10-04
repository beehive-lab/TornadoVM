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

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxIndex;
import uk.ac.manchester.tornado.mlx.jit.JitIndex;

/**
 * The MLX indexing operations and their KernelContext JIT counterparts, run in one graph on the
 * same inputs and checked against Java references. Set, max, min and prod scatters use distinct
 * target positions (the JIT kernels have no float atomics for them); additive scatters repeat
 * positions.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitIndex
 * </code>
 */
public class TestJitIndex extends MlxTestBase {

    private static final int ROWS = 40;
    private static final int COLS = 24;

    private static int[] randomInts(int n, int bound, long seed) {
        Random r = new Random(seed);
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = r.nextInt(bound);
        }
        return v;
    }

    /** The first n of a random permutation of [0, bound). */
    private static int[] distinct(int n, int bound, long seed) {
        int[] p = new int[bound];
        for (int i = 0; i < bound; i++) {
            p[i] = i;
        }
        Random r = new Random(seed);
        for (int i = bound - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        int[] out = new int[n];
        System.arraycopy(p, 0, out, 0, n);
        return out;
    }

    private static void execute(TaskGraph g, GridScheduler gs) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withGridScheduler(gs).execute();
        }
    }

    private static void assertExact(String what, float[] expected, FloatArray mlx, FloatArray jit, double relTol) {
        for (int i = 0; i < expected.length; i++) {
            assertClose(what + " JIT", i, expected[i], jit.get(i), relTol, relTol);
            assertClose(what + " MLX", i, expected[i], mlx.get(i), relTol, relTol);
        }
    }

    private static float applyRef(float old, float u, int op) {
        return switch (op) {
            case JitIndex.SET -> u;
            case JitIndex.ADD -> old + u;
            case JitIndex.MAX -> Math.max(old, u);
            case JitIndex.MIN -> Math.min(old, u);
            default -> old * u;
        };
    }

    @Test
    public void testSlices() throws TornadoExecutionPlanException {
        final int r0 = 3;
        final int r1 = 37;
        final int rs = 2;
        final int c0 = 1;
        final int c1 = 23;
        final int cs = 3;
        final int outRows = (r1 - r0 + rs - 1) / rs;
        final int outCols = (c1 - c0 + cs - 1) / cs;
        float[] xv = values(ROWS * COLS, -10, 10, 171);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray slMlx = new FloatArray(outRows * outCols);
        FloatArray slJit = new FloatArray(outRows * outCols);
        TaskGraph g = new TaskGraph("sl").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("m1", MlxIndex::slice, x, slMlx, ROWS, COLS, r0, r1, rs, c0, c1, cs) //
                .task("j1", JitIndex::slice, new KernelContext(), x, slJit, COLS, r0, rs, c0, cs, outRows, outCols) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, slMlx, slJit);
        GridScheduler gs = new GridScheduler();
        gs.addWorkerGrid("sl.j1", TestJitElementwise.grid1D(outRows * outCols));
        execute(g, gs);
        float[] eSl = new float[outRows * outCols];
        for (int r = 0; r < outRows; r++) {
            for (int c = 0; c < outCols; c++) {
                eSl[r * outCols + c] = xv[(r0 + r * rs) * COLS + c0 + c * cs];
            }
        }
        assertExact("slice", eSl, slMlx, slJit, 0);
    }

    @Test
    public void testSliceUpdates() throws TornadoExecutionPlanException {
        final int r0 = 5;
        final int c0 = 4;
        final int ur = 9;
        final int uc = 10;
        for (int op : new int[] { JitIndex.SET, JitIndex.ADD, JitIndex.PROD }) {
            float[] xv = values(ROWS * COLS, 1, 2, 181);
            float[] uv = values(ur * uc, 0.5f, 2.5f, 182);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray u = FloatArray.fromArray(uv);
            FloatArray outMlx = new FloatArray(xv.length);
            FloatArray outJit = new FloatArray(xv.length);
            TaskGraph g = new TaskGraph("su").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, u);
            switch (op) {
                case JitIndex.SET -> g.libraryTask("m", MlxIndex::sliceUpdate, x, u, outMlx, ROWS, COLS, r0, c0, ur, uc);
                case JitIndex.ADD -> g.libraryTask("m", MlxIndex::sliceUpdateAdd, x, u, outMlx, ROWS, COLS, r0, c0, ur, uc);
                default -> g.libraryTask("m", MlxIndex::sliceUpdateProd, x, u, outMlx, ROWS, COLS, r0, c0, ur, uc);
            }
            g.task("c", JitIndex::copy, new KernelContext(), x, outJit, xv.length) //
                    .task("s", JitIndex::sliceUpdate, new KernelContext(), u, outJit, COLS, r0, c0, ur, uc, op) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
            GridScheduler gs = new GridScheduler();
            gs.addWorkerGrid("su.c", TestJitElementwise.grid1D(xv.length));
            gs.addWorkerGrid("su.s", TestJitElementwise.grid1D(ur * uc));
            execute(g, gs);
            float[] expected = xv.clone();
            for (int r = 0; r < ur; r++) {
                for (int c = 0; c < uc; c++) {
                    int p = (r0 + r) * COLS + c0 + c;
                    expected[p] = applyRef(expected[p], uv[r * uc + c], op);
                }
            }
            assertExact("sliceUpdate op=" + op, expected, outMlx, outJit, 1e-6);
        }
    }

    @Test
    public void testGatherMm() throws TornadoExecutionPlanException {
        final int batchesA = 4;
        final int batchesB = 3;
        final int m = 64;
        final int k = 32;
        final int n = 96;
        int[] lhs = { 3, 0, 1, 2, 3 };
        int[] rhs = { 2, 0, 1, 1, 2 };
        int count = lhs.length;
        float[] av = values(batchesA * m * k, -1, 1, 211);
        float[] bv = values(batchesB * k * n, -1, 1, 212);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        IntArray li = IntArray.fromArray(lhs);
        IntArray ri = IntArray.fromArray(rhs);
        FloatArray outMlx = new FloatArray(count * m * n);
        FloatArray outJit = new FloatArray(count * m * n);
        TaskGraph g = new TaskGraph("gm").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, li, ri) //
                .libraryTask("m", MlxIndex::gatherMm, a, b, li, ri, outMlx, batchesA, batchesB, m, k, n) //
                .task("j", JitIndex::gatherMm, new KernelContext(), a, b, li, ri, outJit, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        execute(g, new GridScheduler("gm.j", TestJitReduce.groups(count * (m / 32) * (n / 32), 128)));
        float[] expected = new float[count * m * n];
        for (int i = 0; i < count; i++) {
            for (int r = 0; r < m; r++) {
                for (int c = 0; c < n; c++) {
                    double s = 0;
                    for (int p = 0; p < k; p++) {
                        s += av[(lhs[i] * m + r) * k + p] * bv[(rhs[i] * k + p) * n + c];
                    }
                    expected[(i * m + r) * n + c] = (float) s;
                }
            }
        }
        assertExact("gatherMm", expected, outMlx, outJit, 1e-4);
    }

}
