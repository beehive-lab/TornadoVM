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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxProducts;
import uk.ac.manchester.tornado.mlx.jit.JitBlas;
import uk.ac.manchester.tornado.mlx.jit.JitProducts;
import uk.ac.manchester.tornado.mlx.jit.JitReduce;

/**
 * The MLX tensor and special products and their KernelContext JIT counterparts, run in one graph
 * and checked against Java references.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitProducts
 * </code>
 */
public class TestJitProducts extends MlxTestBase {

    private static void execute(TaskGraph g, GridScheduler gs) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withGridScheduler(gs).execute();
        }
    }

    private static void both(String what, double[] expected, FloatArray mlx, FloatArray jit, double tol) {
        assertAllClose(what + " MLX", expected, mlx, tol, tol);
        assertAllClose(what + " JIT", expected, jit, tol, tol);
    }

    /** a [m, k] @ b [k, n] in double, with a and b at the given offsets. */
    private static double[] matmul(float[] a, int aOff, float[] b, int bOff, int m, int k, int n) {
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double s = 0;
                for (int p = 0; p < k; p++) {
                    s += a[aOff + i * k + p] * b[bOff + p * n + j];
                }
                c[i * n + j] = s;
            }
        }
        return c;
    }

    @Test
    public void testEinsumAndTensordot() throws TornadoExecutionPlanException {
        final int batch = 3;
        final int m = 64;
        final int k = 32;
        final int n = 96;
        float[] av = values(batch * m * k, -1, 1, 1);
        float[] bv = values(batch * k * n, -1, 1, 2);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        // tensordot takes one matrix each: the first batch entries.
        FloatArray a1 = FloatArray.fromArray(java.util.Arrays.copyOf(av, m * k));
        FloatArray b1 = FloatArray.fromArray(java.util.Arrays.copyOf(bv, k * n));
        FloatArray eM = new FloatArray(batch * m * n);
        FloatArray eJ = new FloatArray(batch * m * n);
        FloatArray tM = new FloatArray(m * n);
        FloatArray tJ = new FloatArray(m * n);
        FloatArray uM = new FloatArray(m * n);
        FloatArray uJ = new FloatArray(m * n);
        TaskGraph g = new TaskGraph("es").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, a1, b1) //
                .libraryTask("m1", MlxProducts::einsumBatchedMatmul, a, b, eM, batch, m, k, n) //
                .libraryTask("m2", MlxProducts::tensordot, a1, b1, tM, m, 4, 8, n) //
                .libraryTask("m3", MlxProducts::tensordotAxis, a1, b1, uM, m, k, n) //
                .task("j1", JitProducts::batchedGemm, new KernelContext(), a, b, eJ, m, n, k) //
                .task("j2", JitBlas::gemm, new KernelContext(), a1, b1, tJ, m, n, 32) //
                .task("j3", JitBlas::gemm, new KernelContext(), a1, b1, uJ, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, eM, eJ, tM, tJ, uM, uJ);
        GridScheduler gs = new GridScheduler();
        gs.addWorkerGrid("es.j1", TestJitReduce.groups(batch * (m / 32) * (n / 32), 128));
        gs.addWorkerGrid("es.j2", TestJitReduce.groups((m / 32) * (n / 32), 128));
        gs.addWorkerGrid("es.j3", TestJitReduce.groups((m / 32) * (n / 32), 128));
        execute(g, gs);
        double[] e = new double[batch * m * n];
        for (int bb = 0; bb < batch; bb++) {
            System.arraycopy(matmul(av, bb * m * k, bv, bb * k * n, m, k, n), 0, e, bb * m * n, m * n);
        }
        both("einsum bij,bjk->bik", e, eM, eJ, 1e-4);
        // tensordot over a[m, 4, 8] and b[4, 8, n] is the product of a[m, 32] and b[32, n].
        double[] t = matmul(av, 0, bv, 0, m, 32, n);
        both("tensordot", t, tM, tJ, 1e-4);
        both("tensordotAxis", matmul(av, 0, bv, 0, m, k, n), uM, uJ, 1e-4);
    }

    @Test
    public void testInnerOuterKron() throws TornadoExecutionPlanException {
        final int n = 100_000;
        float[] av = values(n, -1, 1, 3);
        float[] bv = values(n, -1, 1, 4);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray inM = new FloatArray(1);
        FloatArray inJ = new FloatArray(1);
        FloatArray partials = new FloatArray(JitReduce.PARTIAL_GROUPS);
        final int om = 37;
        final int on = 53;
        FloatArray oa = FloatArray.fromArray(values(om, -2, 2, 5));
        FloatArray ob = FloatArray.fromArray(values(on, -2, 2, 6));
        FloatArray outM = new FloatArray(om * on);
        FloatArray outJ = new FloatArray(om * on);
        FloatArray ka = FloatArray.fromArray(values(5 * 4, -2, 2, 7));
        FloatArray kb = FloatArray.fromArray(values(3 * 6, -2, 2, 8));
        FloatArray krM = new FloatArray(15 * 24);
        FloatArray krJ = new FloatArray(15 * 24);
        TaskGraph g = new TaskGraph("io").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, oa, ob, ka, kb) //
                .libraryTask("m1", MlxProducts::inner, a, b, inM) //
                .libraryTask("m2", MlxProducts::outer, oa, ob, outM) //
                .libraryTask("m3", MlxProducts::kron, ka, kb, krM, 5, 4, 3, 6) //
                .task("p", JitProducts::dotPartial, new KernelContext(), a, b, partials, n) //
                .task("q", JitReduce::reduceRows, new KernelContext(), partials, inJ, JitReduce.PARTIAL_GROUPS, JitReduce.SUM, 1.0f) //
                .task("j2", JitProducts::outer, new KernelContext(), oa, ob, outJ, om, on) //
                .task("j3", JitProducts::kron, new KernelContext(), ka, kb, krJ, 5, 4, 3, 6) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, inM, inJ, outM, outJ, krM, krJ);
        GridScheduler gs = new GridScheduler();
        gs.addWorkerGrid("io.p", TestJitReduce.groups(JitReduce.PARTIAL_GROUPS, 256));
        gs.addWorkerGrid("io.q", TestJitReduce.groups(1, 256));
        gs.addWorkerGrid("io.j2", TestJitElementwise.grid1D(om * on));
        gs.addWorkerGrid("io.j3", TestJitElementwise.grid1D(15 * 24));
        execute(g, gs);
        double dot = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) av[i] * bv[i];
        }
        both("inner", new double[] { dot }, inM, inJ, 1e-3);
        double[] eo = new double[om * on];
        for (int t = 0; t < eo.length; t++) {
            eo[t] = (double) oa.get(t / on) * ob.get(t % on);
        }
        both("outer", eo, outM, outJ, 1e-6);
        double[] ek = new double[15 * 24];
        for (int t = 0; t < ek.length; t++) {
            int r = t / 24;
            int c = t % 24;
            ek[t] = (double) ka.get((r / 3) * 4 + c / 6) * kb.get((r % 3) * 6 + c % 6);
        }
        both("kron", ek, krM, krJ, 1e-6);
    }

    @Test
    public void testSegmented() throws TornadoExecutionPlanException {
        final int sm = 16;
        final int sk = 64;
        final int sn = 24;
        int[] segs = { 0, 10, 10, 64, 5, 5, 20, 40 };
        IntArray segments = IntArray.fromArray(segs);
        FloatArray sa = FloatArray.fromArray(values(sm * sk, -1, 1, 12));
        FloatArray sb = FloatArray.fromArray(values(sk * sn, -1, 1, 13));
        FloatArray sM = new FloatArray(4 * sm * sn);
        FloatArray sJ = new FloatArray(4 * sm * sn);
        TaskGraph g = new TaskGraph("bm").transferToDevice(DataTransferMode.FIRST_EXECUTION, segments, sa, sb) //
                .libraryTask("m2", MlxProducts::segmentedMm, sa, sb, segments, sM, sm, sk, sn) //
                .task("j2", JitProducts::segmentedGemm, new KernelContext(), sa, sb, segments, sJ, 4, sm, sn, sk) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sM, sJ);
        GridScheduler gs = new GridScheduler();
        gs.addWorkerGrid("bm.j2", TestJitElementwise.grid1D(4 * sm * sn));
        execute(g, gs);
        double[] es = new double[4 * sm * sn];
        for (int s = 0; s < 4; s++) {
            for (int i = 0; i < sm; i++) {
                for (int j = 0; j < sn; j++) {
                    double acc = 0;
                    for (int p = segs[2 * s]; p < segs[2 * s + 1]; p++) {
                        acc += sa.get(i * sk + p) * sb.get(p * sn + j);
                    }
                    es[(s * sm + i) * sn + j] = acc;
                }
            }
        }
        both("segmentedMm", es, sM, sJ, 1e-4);
    }

    @Test
    public void testQqmmMxfp8() throws TornadoExecutionPlanException {
        final int m = 1;
        final int k = 256;
        final int n = 64;
        float[] xv = values(m * k, -1, 1, 16);
        float[] wv = values(n * k, -1, 1, 17);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray w = FloatArray.fromArray(wv);
        IntArray wq = new IntArray(n * k / 4);
        ByteArray scales = new ByteArray(n * k / 32);
        FloatArray yM = new FloatArray(m * n);
        FloatArray yJ = new FloatArray(m * n);
        TaskGraph g = new TaskGraph("qq").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .libraryTask("q", MlxProducts::quantizeMx, w, wq, scales, n, k, 0) //
                .libraryTask("m", MlxProducts::qqmm, x, wq, scales, yM, m, k, n, 0) //
                .task("j", JitProducts::qqmm, new KernelContext(), x, wq, scales, yJ, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, yM, yJ);
        execute(g, new GridScheduler("qq.j", TestJitReduce.groups(m * n, 32)));
        double worstVsMlx = 0;
        double worstVsFloat = 0;
        double scale = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double s = 0;
                for (int p = 0; p < k; p++) {
                    s += xv[i * k + p] * wv[j * k + p];
                }
                int o = i * n + j;
                scale = Math.max(scale, Math.abs(s));
                worstVsMlx = Math.max(worstVsMlx, Math.abs(yM.get(o) - yJ.get(o)));
                worstVsFloat = Math.max(worstVsFloat, Math.abs(yJ.get(o) - s));
            }
        }
        // Both quantize x the same way, so they agree closely; 8-bit floats keep about 2 significant digits.
        assertTrue("qqmm JIT vs MLX differ by " + worstVsMlx, worstVsMlx <= 1e-3 * Math.max(scale, 1));
        assertTrue("qqmm vs float product differ by " + worstVsFloat, worstVsFloat <= 0.1 * Math.max(scale, 1));
    }
}
