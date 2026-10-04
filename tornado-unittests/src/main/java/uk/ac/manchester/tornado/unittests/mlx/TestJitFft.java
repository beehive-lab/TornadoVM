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
import uk.ac.manchester.tornado.mlx.MlxFft;
import uk.ac.manchester.tornado.mlx.jit.JitFft;

/**
 * The MLX FFTs and their KernelContext JIT counterparts (radix-2 FFTs for power-of-two lengths,
 * direct DFTs otherwise, applied axis by axis for 2D and 3D transforms), run in one graph on the
 * same inputs and checked against a double-precision DFT.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitFft
 * </code>
 */
public class TestJitFft extends MlxTestBase {

    private static int counter;

    private static boolean powerOfTwo(int n) {
        return n <= JitFft.MAX_N && (n & (n - 1)) == 0;
    }

    private static int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    /** Scale for one axis of length n under a normalisation mode. */
    static float scale(int norm, int n, boolean inverse) {
        return switch (norm) {
            case MlxFft.ORTHO -> (float) (1.0 / Math.sqrt(n));
            case MlxFft.FORWARD -> inverse ? 1.0f : 1.0f / n;
            default -> inverse ? 1.0f / n : 1.0f;
        };
    }

    /**
     * Adds one JIT pass over the middle axis of in viewed as [lines / inner, inLen, inner]: a
     * radix-2 FFT for power-of-two n, a direct DFT otherwise.
     */
    static void pass(TaskGraph g, GridScheduler gs, FloatArray in, FloatArray out, int lines, int n, int inLen, int outLen, int inner, int inKind, boolean realOut, boolean inverse,
            float scale) {
        String t = "p" + (counter++);
        int ro = realOut ? 1 : 0;
        int inv = inverse ? 1 : 0;
        if (powerOfTwo(n)) {
            g.task(t, JitFft::fft, new KernelContext(), in, out, n, log2(n), inLen, outLen, inner, inKind, ro, inv, scale);
            gs.addWorkerGrid(g.getTaskGraphName() + "." + t, TestJitReduce.groups(lines, JitFft.THREADS));
        } else {
            g.task(t, JitFft::dft, new KernelContext(), in, out, lines, n, inLen, outLen, inner, inKind, ro, inv, scale);
            gs.addWorkerGrid(g.getTaskGraphName() + "." + t, TestJitElementwise.grid1D(lines * outLen));
        }
    }

    // ---------------------------------------------------------------- reference

    /**
     * DFT along the middle axis of complex data (re, im arrays) viewed as [outer, len, inner],
     * zero-padded or cropped to n, with a scale.
     */
    static double[][] dft(double[] re, double[] im, int outer, int len, int inner, int n, boolean inverse, double scale) {
        double[] or = new double[outer * n * inner];
        double[] oi = new double[outer * n * inner];
        double sign = inverse ? 1 : -1;
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                for (int k = 0; k < n; k++) {
                    double sr = 0;
                    double si = 0;
                    for (int i = 0; i < Math.min(len, n); i++) {
                        double angle = sign * 2 * Math.PI * ((long) i * k % n) / n;
                        double xr = re[(o * len + i) * inner + j];
                        double xi = im[(o * len + i) * inner + j];
                        sr += xr * Math.cos(angle) - xi * Math.sin(angle);
                        si += xr * Math.sin(angle) + xi * Math.cos(angle);
                    }
                    or[(o * n + k) * inner + j] = sr * scale;
                    oi[(o * n + k) * inner + j] = si * scale;
                }
            }
        }
        return new double[][] { or, oi };
    }

    /** Keeps the first m entries of the middle axis ([outer, n, inner] to [outer, m, inner]). */
    static double[] crop(double[] v, int outer, int n, int inner, int m) {
        double[] out = new double[outer * m * inner];
        for (int o = 0; o < outer; o++) {
            for (int k = 0; k < m; k++) {
                for (int j = 0; j < inner; j++) {
                    out[(o * m + k) * inner + j] = v[(o * n + k) * inner + j];
                }
            }
        }
        return out;
    }

    /** Full Hermitian spectrum [outer, n, inner] from its first n / 2 + 1 coefficients. */
    static double[][] hermitian(double[] re, double[] im, int outer, int half, int inner, int n) {
        double[] fr = new double[outer * n * inner];
        double[] fi = new double[outer * n * inner];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                for (int i = 0; i < n; i++) {
                    int k = i <= n / 2 ? i : n - i;
                    if (k < half) {
                        fr[(o * n + i) * inner + j] = re[(o * half + k) * inner + j];
                        fi[(o * n + i) * inner + j] = (i > n / 2 ? -1 : 1) * im[(o * half + k) * inner + j];
                    }
                }
            }
        }
        return new double[][] { fr, fi };
    }

    static float[] interleave(double[] re, double[] im) {
        float[] v = new float[2 * re.length];
        for (int i = 0; i < re.length; i++) {
            v[2 * i] = (float) re[i];
            v[2 * i + 1] = (float) im[i];
        }
        return v;
    }

    static double[][] split(float[] v) {
        double[] re = new double[v.length / 2];
        double[] im = new double[v.length / 2];
        for (int i = 0; i < re.length; i++) {
            re[i] = v[2 * i];
            im[i] = v[2 * i + 1];
        }
        return new double[][] { re, im };
    }

    static double[] toDouble(float[] v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return d;
    }

    private static void execute(TaskGraph g, GridScheduler gs) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(g.snapshot())) {
            plan.withGridScheduler(gs).execute();
        }
    }

    private static void both(String what, double[] expected, FloatArray mlx, FloatArray jit, double tol) {
        assertAllClose(what + " JIT", expected, jit, tol, tol);
        assertAllClose(what + " MLX", expected, mlx, tol, tol);
    }

    // ---------------------------------------------------------------- 1D

    @Test
    public void testFftAndIfft() throws TornadoExecutionPlanException {
        final int rows = 3;
        int[][] cases = { { 256, 256 }, { 100, 100 } };
        for (int[] c : cases) {
            int len = c[0];
            int n = c[1];
            for (int norm : new int[] { MlxFft.BACKWARD, MlxFft.ORTHO, MlxFft.FORWARD }) {
                for (boolean inverse : new boolean[] { false, true }) {
                    float[] xv = values(2 * rows * len, -1, 1, 361L + len);
                    FloatArray x = FloatArray.fromArray(xv);
                    FloatArray outMlx = new FloatArray(2 * rows * n);
                    FloatArray outJit = new FloatArray(2 * rows * n);
                    TaskGraph g = new TaskGraph("f1").transferToDevice(DataTransferMode.FIRST_EXECUTION, x);
                    if (inverse) {
                        g.libraryTask("m", MlxFft::ifft, x, outMlx, rows, len, n, norm);
                    } else {
                        g.libraryTask("m", MlxFft::fft, x, outMlx, rows, len, n, norm);
                    }
                    GridScheduler gs = new GridScheduler();
                    pass(g, gs, x, outJit, rows, n, len, n, 1, JitFft.COMPLEX, false, inverse, scale(norm, n, inverse));
                    g.transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
                    execute(g, gs);
                    double[][] in = split(xv);
                    double[][] e = dft(in[0], in[1], rows, len, 1, n, inverse, scale(norm, n, inverse));
                    both((inverse ? "ifft" : "fft") + " len=" + len + " n=" + n + " norm=" + norm, toDouble(interleave(e[0], e[1])), outMlx, outJit, 2e-3);
                }
            }
        }
    }

    @Test
    public void testRfftAndIrfft() throws TornadoExecutionPlanException {
        final int rows = 4;
        for (int n : new int[] { 256, 90 }) {
            int half = n / 2 + 1;
            float[] xv = values(rows * n, -1, 1, 371L + n);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray specMlx = new FloatArray(2 * rows * half);
            FloatArray specJit = new FloatArray(2 * rows * half);
            FloatArray backMlx = new FloatArray(rows * n);
            FloatArray backJit = new FloatArray(rows * n);
            TaskGraph g = new TaskGraph("rf").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                    .libraryTask("m1", MlxFft::rfft, x, specMlx, rows, n, n, MlxFft.BACKWARD) //
                    .libraryTask("m2", MlxFft::irfft, specMlx, backMlx, rows, half, n, MlxFft.BACKWARD);
            GridScheduler gs = new GridScheduler();
            pass(g, gs, x, specJit, rows, n, n, half, 1, JitFft.REAL, false, false, 1f);
            pass(g, gs, specJit, backJit, rows, n, half, n, 1, JitFft.HERMITIAN, true, true, 1f / n);
            g.transferToHost(DataTransferMode.EVERY_EXECUTION, specMlx, specJit, backMlx, backJit);
            execute(g, gs);
            double[][] e = dft(toDouble(xv), new double[xv.length], rows, n, 1, n, false, 1);
            both("rfft n=" + n, toDouble(interleave(crop(e[0], rows, n, 1, half), crop(e[1], rows, n, 1, half))), specMlx, specJit, 2e-3);
            both("irfft(rfft(x)) n=" + n, toDouble(xv), backMlx, backJit, 1e-4);
        }
    }

    // ---------------------------------------------------------------- 2D and 3D

    @Test
    public void testFft2AndIfft2() throws TornadoExecutionPlanException {
        final int batch = 2;
        for (int[] hw : new int[][] { { 16, 32 }, { 12, 10 } }) {
            int h = hw[0];
            int w = hw[1];
            for (boolean inverse : new boolean[] { false, true }) {
                float[] xv = values(2 * batch * h * w, -1, 1, 381L + h * w);
                FloatArray x = FloatArray.fromArray(xv);
                FloatArray tmp = new FloatArray(2 * batch * h * w);
                FloatArray outMlx = new FloatArray(2 * batch * h * w);
                FloatArray outJit = new FloatArray(2 * batch * h * w);
                TaskGraph g = new TaskGraph("f2").transferToDevice(DataTransferMode.FIRST_EXECUTION, x);
                if (inverse) {
                    g.libraryTask("m", MlxFft::ifft2, x, outMlx, batch, h, w, MlxFft.BACKWARD);
                } else {
                    g.libraryTask("m", MlxFft::fft2, x, outMlx, batch, h, w, MlxFft.BACKWARD);
                }
                GridScheduler gs = new GridScheduler();
                pass(g, gs, x, tmp, batch * h, w, w, w, 1, JitFft.COMPLEX, false, inverse, scale(MlxFft.BACKWARD, w, inverse));
                pass(g, gs, tmp, outJit, batch * w, h, h, h, w, JitFft.COMPLEX, false, inverse, scale(MlxFft.BACKWARD, h, inverse));
                g.transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
                execute(g, gs);
                double[][] in = split(xv);
                double[][] e1 = dft(in[0], in[1], batch * h, w, 1, w, inverse, scale(MlxFft.BACKWARD, w, inverse));
                double[][] e2 = dft(e1[0], e1[1], batch, h, w, h, inverse, scale(MlxFft.BACKWARD, h, inverse));
                both((inverse ? "ifft2 " : "fft2 ") + h + "x" + w, toDouble(interleave(e2[0], e2[1])), outMlx, outJit, 2e-3);
            }
        }
    }

    @Test
    public void testRfft2AndIrfft2() throws TornadoExecutionPlanException {
        final int batch = 2;
        final int h = 16;
        final int w = 32;
        final int wh = w / 2 + 1;
        float[] xv = values(batch * h * w, -1, 1, 391);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray tmp1 = new FloatArray(2 * batch * h * wh);
        FloatArray tmp2 = new FloatArray(2 * batch * h * wh);
        FloatArray specMlx = new FloatArray(2 * batch * h * wh);
        FloatArray specJit = new FloatArray(2 * batch * h * wh);
        FloatArray backMlx = new FloatArray(batch * h * w);
        FloatArray backJit = new FloatArray(batch * h * w);
        TaskGraph g = new TaskGraph("r2").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("m1", MlxFft::rfft2, x, specMlx, batch, h, w, MlxFft.BACKWARD) //
                .libraryTask("m2", MlxFft::irfft2, specMlx, backMlx, batch, h, w, MlxFft.BACKWARD);
        GridScheduler gs = new GridScheduler();
        // Forward: real rows (w to w / 2 + 1), then columns. Inverse: columns, then Hermitian rows back to w reals.
        pass(g, gs, x, tmp1, batch * h, w, w, wh, 1, JitFft.REAL, false, false, 1f);
        pass(g, gs, tmp1, specJit, batch * wh, h, h, h, wh, JitFft.COMPLEX, false, false, 1f);
        pass(g, gs, specJit, tmp2, batch * wh, h, h, h, wh, JitFft.COMPLEX, false, true, 1f / h);
        pass(g, gs, tmp2, backJit, batch * h, w, wh, w, 1, JitFft.HERMITIAN, true, true, 1f / w);
        g.transferToHost(DataTransferMode.EVERY_EXECUTION, specMlx, specJit, backMlx, backJit);
        execute(g, gs);
        double[][] e1 = dft(toDouble(xv), new double[xv.length], batch * h, w, 1, w, false, 1);
        double[] r1 = crop(e1[0], batch * h, w, 1, wh);
        double[] i1 = crop(e1[1], batch * h, w, 1, wh);
        double[][] e2 = dft(r1, i1, batch, h, wh, h, false, 1);
        both("rfft2", toDouble(interleave(e2[0], e2[1])), specMlx, specJit, 5e-3);
        both("irfft2(rfft2(x))", toDouble(xv), backMlx, backJit, 1e-4);
    }

    @Test
    public void testFftnFamily() throws TornadoExecutionPlanException {
        final int batch = 1;
        final int d = 8;
        final int h = 8;
        final int w = 16;
        final int wh = w / 2 + 1;
        int n = batch * d * h * w;
        float[] cv = values(2 * n, -1, 1, 401);
        float[] rv = values(n, -1, 1, 402);
        FloatArray xc = FloatArray.fromArray(cv);
        FloatArray xr = FloatArray.fromArray(rv);
        FloatArray fMlx = new FloatArray(2 * n);
        FloatArray fJit = new FloatArray(2 * n);
        FloatArray iMlx = new FloatArray(2 * n);
        FloatArray iJit = new FloatArray(2 * n);
        FloatArray rMlx = new FloatArray(2 * batch * d * h * wh);
        FloatArray rJit = new FloatArray(2 * batch * d * h * wh);
        FloatArray backMlx = new FloatArray(n);
        FloatArray backJit = new FloatArray(n);
        FloatArray t1 = new FloatArray(2 * n);
        FloatArray t2 = new FloatArray(2 * n);
        FloatArray t3 = new FloatArray(2 * n);
        FloatArray t4 = new FloatArray(2 * n);
        FloatArray t5 = new FloatArray(2 * batch * d * h * wh);
        FloatArray t6 = new FloatArray(2 * batch * d * h * wh);
        FloatArray t7 = new FloatArray(2 * batch * d * h * wh);
        FloatArray t8 = new FloatArray(2 * batch * d * h * wh);
        TaskGraph g = new TaskGraph("fn").transferToDevice(DataTransferMode.FIRST_EXECUTION, xc, xr) //
                .libraryTask("m1", MlxFft::fftn, xc, fMlx, batch, d, h, w, MlxFft.BACKWARD) //
                .libraryTask("m2", MlxFft::ifftn, xc, iMlx, batch, d, h, w, MlxFft.BACKWARD) //
                .libraryTask("m3", MlxFft::rfftn, xr, rMlx, batch, d, h, w, MlxFft.BACKWARD) //
                .libraryTask("m4", MlxFft::irfftn, rMlx, backMlx, batch, d, h, w, MlxFft.BACKWARD);
        GridScheduler gs = new GridScheduler();
        // fftn and ifftn: w (contiguous), then h (stride w), then d (stride h * w).
        pass(g, gs, xc, t1, batch * d * h, w, w, w, 1, JitFft.COMPLEX, false, false, 1f);
        pass(g, gs, t1, t2, batch * d * w, h, h, h, w, JitFft.COMPLEX, false, false, 1f);
        pass(g, gs, t2, fJit, batch * h * w, d, d, d, h * w, JitFft.COMPLEX, false, false, 1f);
        pass(g, gs, xc, t3, batch * d * h, w, w, w, 1, JitFft.COMPLEX, false, true, 1f / w);
        pass(g, gs, t3, t4, batch * d * w, h, h, h, w, JitFft.COMPLEX, false, true, 1f / h);
        pass(g, gs, t4, iJit, batch * h * w, d, d, d, h * w, JitFft.COMPLEX, false, true, 1f / d);
        // rfftn: real w, then h, then d; irfftn: d, h, then Hermitian w.
        pass(g, gs, xr, t5, batch * d * h, w, w, wh, 1, JitFft.REAL, false, false, 1f);
        pass(g, gs, t5, t6, batch * d * wh, h, h, h, wh, JitFft.COMPLEX, false, false, 1f);
        pass(g, gs, t6, rJit, batch * h * wh, d, d, d, h * wh, JitFft.COMPLEX, false, false, 1f);
        pass(g, gs, rJit, t7, batch * h * wh, d, d, d, h * wh, JitFft.COMPLEX, false, true, 1f / d);
        pass(g, gs, t7, t8, batch * d * wh, h, h, h, wh, JitFft.COMPLEX, false, true, 1f / h);
        pass(g, gs, t8, backJit, batch * d * h, w, wh, w, 1, JitFft.HERMITIAN, true, true, 1f / w);
        g.transferToHost(DataTransferMode.EVERY_EXECUTION, fMlx, fJit, iMlx, iJit, rMlx, rJit, backMlx, backJit);
        execute(g, gs);
        double[][] in = split(cv);
        for (boolean inverse : new boolean[] { false, true }) {
            double[][] a = dft(in[0], in[1], batch * d * h, w, 1, w, inverse, scale(MlxFft.BACKWARD, w, inverse));
            double[][] b = dft(a[0], a[1], batch * d, h, w, h, inverse, scale(MlxFft.BACKWARD, h, inverse));
            double[][] c = dft(b[0], b[1], batch, d, h * w, d, inverse, scale(MlxFft.BACKWARD, d, inverse));
            both(inverse ? "ifftn" : "fftn", toDouble(interleave(c[0], c[1])), inverse ? iMlx : fMlx, inverse ? iJit : fJit, 5e-3);
        }
        double[][] a = dft(toDouble(rv), new double[n], batch * d * h, w, 1, w, false, 1);
        double[][] b = dft(crop(a[0], batch * d * h, w, 1, wh), crop(a[1], batch * d * h, w, 1, wh), batch * d, h, wh, h, false, 1);
        double[][] c = dft(b[0], b[1], batch, d, h * wh, d, false, 1);
        both("rfftn", toDouble(interleave(c[0], c[1])), rMlx, rJit, 5e-3);
        both("irfftn(rfftn(x))", toDouble(rv), backMlx, backJit, 1e-4);
    }

    // ---------------------------------------------------------------- shifts and frequencies

    @Test
    public void testShiftsAndFrequencies() throws TornadoExecutionPlanException {
        final int rows = 3;
        for (int len : new int[] { 8, 9 }) {
            float[] xv = values(rows * len, -1, 1, 411L + len);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray sMlx = new FloatArray(rows * len);
            FloatArray sJit = new FloatArray(rows * len);
            FloatArray iMlx = new FloatArray(rows * len);
            FloatArray iJit = new FloatArray(rows * len);
            FloatArray fMlx = new FloatArray(len);
            FloatArray fJit = new FloatArray(len);
            FloatArray rfMlx = new FloatArray(len / 2 + 1);
            FloatArray rfJit = new FloatArray(len / 2 + 1);
            TaskGraph g = new TaskGraph("sh").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                    .libraryTask("m1", MlxFft::fftshift, x, sMlx, rows, len) //
                    .libraryTask("m2", MlxFft::ifftshift, x, iMlx, rows, len) //
                    .libraryTask("m3", MlxFft::fftfreq, fMlx, len, 0.5f) //
                    .libraryTask("m4", MlxFft::rfftfreq, rfMlx, len, 0.5f) //
                    .task("j1", JitFft::roll, new KernelContext(), x, sJit, rows * len, len, len / 2) //
                    .task("j2", JitFft::roll, new KernelContext(), x, iJit, rows * len, len, -(len / 2)) //
                    .task("j3", JitFft::frequencies, new KernelContext(), fJit, len, len, 0.5f, 0) //
                    .task("j4", JitFft::frequencies, new KernelContext(), rfJit, len / 2 + 1, len, 0.5f, 1) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, sMlx, sJit, iMlx, iJit, fMlx, fJit, rfMlx, rfJit);
            GridScheduler gs = new GridScheduler();
            for (String t : new String[] { "j1", "j2", "j3", "j4" }) {
                gs.addWorkerGrid("sh." + t, TestJitElementwise.grid1D(rows * len));
            }
            execute(g, gs);
            double[] eS = new double[rows * len];
            double[] eI = new double[rows * len];
            for (int r = 0; r < rows; r++) {
                for (int i = 0; i < len; i++) {
                    eS[r * len + (i + len / 2) % len] = xv[r * len + i];
                    eI[r * len + i] = xv[r * len + (i + len / 2) % len];
                }
            }
            double[] eF = new double[len];
            for (int i = 0; i < len; i++) {
                eF[i] = (i < (len + 1) / 2 ? i : i - len) / (0.5 * len);
            }
            double[] eR = new double[len / 2 + 1];
            for (int i = 0; i <= len / 2; i++) {
                eR[i] = i / (0.5 * len);
            }
            both("fftshift len=" + len, eS, sMlx, sJit, 0);
            both("ifftshift len=" + len, eI, iMlx, iJit, 0);
            both("fftfreq n=" + len, eF, fMlx, fJit, 1e-6);
            both("rfftfreq n=" + len, eR, rfMlx, rfJit, 1e-6);
        }
    }

    @Test
    public void testHalfFftshift() throws TornadoExecutionPlanException {
        final int rows = 2;
        final int len = 7;
        float[] xv = values(rows * len, -5, 5, 421);
        HalfFloatArray x = half(xv);
        HalfFloatArray out = new HalfFloatArray(rows * len);
        run(new TaskGraph("hs").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("m", MlxFft::fftshift, x, out, rows, len) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        float[] w = widen(x);
        double[] e = new double[rows * len];
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < len; i++) {
                e[r * len + (i + len / 2) % len] = w[r * len + i];
            }
        }
        assertAllClose("fftshift float16", e, out, 0, 0);
    }
}
