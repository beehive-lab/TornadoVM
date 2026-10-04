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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.MlxFft;

/**
 * Unit tests for the MLX FFT library tasks: complex and real FFTs and their inverses in one, two and
 * three dimensions, fftshift, ifftshift, fftfreq and rfftfreq. Complex values are interleaved
 * {@code (re, im)} pairs. Each test checks the task against a direct DFT in Java. Skipped unless the
 * default device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxFft
 * </code>
 */
public class TestMlxFft extends MlxTestBase {

    private static final int ROWS = 3;
    private static final int N = 256;
    private static final int BATCH = 2;
    private static final int H = 16;
    private static final int W = 32;
    private static final int D = 8;

    /** DFT along the middle axis of complex data viewed as [outer, len, inner], with a scale. */
    private static double[][] dftJava(double[] re, double[] im, int outer, int len, int inner, boolean inverse, double scale) {
        double[] outRe = new double[outer * len * inner];
        double[] outIm = new double[outer * len * inner];
        double sign = inverse ? 1 : -1;
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                for (int k = 0; k < len; k++) {
                    double sr = 0;
                    double si = 0;
                    for (int i = 0; i < len; i++) {
                        double angle = sign * 2 * Math.PI * ((long) i * k % len) / len;
                        double xr = re[(o * len + i) * inner + j];
                        double xi = im[(o * len + i) * inner + j];
                        sr += xr * Math.cos(angle) - xi * Math.sin(angle);
                        si += xr * Math.sin(angle) + xi * Math.cos(angle);
                    }
                    outRe[(o * len + k) * inner + j] = sr * scale;
                    outIm[(o * len + k) * inner + j] = si * scale;
                }
            }
        }
        return new double[][] { outRe, outIm };
    }

    /** Keeps the first m entries of the middle axis ([outer, n, inner] to [outer, m, inner]). */
    private static double[] crop(double[] v, int outer, int n, int inner, int m) {
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

    private static double[] interleave(double[] re, double[] im) {
        double[] v = new double[2 * re.length];
        for (int i = 0; i < re.length; i++) {
            v[2 * i] = re[i];
            v[2 * i + 1] = im[i];
        }
        return v;
    }

    private static double[][] split(float[] v) {
        double[] re = new double[v.length / 2];
        double[] im = new double[v.length / 2];
        for (int i = 0; i < re.length; i++) {
            re[i] = v[2 * i];
            im[i] = v[2 * i + 1];
        }
        return new double[][] { re, im };
    }

    private static double[] toDouble(float[] v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return d;
    }

    private static float[] toFloat(double[] v) {
        float[] f = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            f[i] = (float) v[i];
        }
        return f;
    }

    /** fft2 of complex x[batch, h, w] in Java: rows, then columns. */
    private static double[][] fft2Java(double[] re, double[] im, int batch, int h, int w, boolean inverse) {
        double[][] rows = dftJava(re, im, batch * h, w, 1, inverse, inverse ? 1.0 / w : 1);
        return dftJava(rows[0], rows[1], batch, h, w, inverse, inverse ? 1.0 / h : 1);
    }

    /** rfft2 of real x[batch, h, w] in Java: the first w / 2 + 1 columns of the full fft2. */
    private static double[][] rfft2Java(float[] x, int batch, int h, int w) {
        int half = w / 2 + 1;
        double[][] rows = dftJava(toDouble(x), new double[x.length], batch * h, w, 1, false, 1);
        return dftJava(crop(rows[0], batch * h, w, 1, half), crop(rows[1], batch * h, w, 1, half), batch, h, half, false, 1);
    }

    /** fftn of complex x[batch, d, h, w] in Java: w, then h, then d. */
    private static double[][] fftnJava(double[] re, double[] im, int batch, int d, int h, int w, boolean inverse) {
        double[][] a = dftJava(re, im, batch * d * h, w, 1, inverse, inverse ? 1.0 / w : 1);
        double[][] b = dftJava(a[0], a[1], batch * d, h, w, inverse, inverse ? 1.0 / h : 1);
        return dftJava(b[0], b[1], batch, d, h * w, inverse, inverse ? 1.0 / d : 1);
    }

    /** rfftn of real x[batch, d, h, w] in Java. */
    private static double[][] rfftnJava(float[] x, int batch, int d, int h, int w) {
        int half = w / 2 + 1;
        double[][] a = dftJava(toDouble(x), new double[x.length], batch * d * h, w, 1, false, 1);
        double[][] b = dftJava(crop(a[0], batch * d * h, w, 1, half), crop(a[1], batch * d * h, w, 1, half), batch * d, h, half, false, 1);
        return dftJava(b[0], b[1], batch, d, h * half, false, 1);
    }

    private static void complex1d(boolean inverse, int len, int norm, double scale) throws TornadoExecutionPlanException {
        float[] xv = values(2 * ROWS * len, -1, 1, 1);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * ROWS * len);

        TaskGraph taskGraph = new TaskGraph("g").transferToDevice(DataTransferMode.EVERY_EXECUTION, x);
        if (inverse) {
            taskGraph.libraryTask("ifft", MlxFft::ifft, x, output, ROWS, len, len, norm);
        } else {
            taskGraph.libraryTask("fft", MlxFft::fft, x, output, ROWS, len, len, norm);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] in = split(xv);
        double[][] expected = dftJava(in[0], in[1], ROWS, len, 1, inverse, scale);
        assertAllClose((inverse ? "ifft" : "fft") + " len=" + len, interleave(expected[0], expected[1]), output, 2e-3, 2e-3);
    }

    // ---------------------------------------------------------------- 1D

    @Test
    public void testFft() throws TornadoExecutionPlanException {
        complex1d(false, N, MlxFft.BACKWARD, 1);
    }

    @Test
    public void testIfft() throws TornadoExecutionPlanException {
        complex1d(true, N, MlxFft.BACKWARD, 1.0 / N);
    }

    @Test
    public void testFftOrthoNorm() throws TornadoExecutionPlanException {
        complex1d(false, N, MlxFft.ORTHO, 1.0 / Math.sqrt(N));
    }

    @Test
    public void testFftForwardNorm() throws TornadoExecutionPlanException {
        complex1d(false, N, MlxFft.FORWARD, 1.0 / N);
    }

    @Test
    public void testFftNonPowerOfTwo() throws TornadoExecutionPlanException {
        complex1d(false, 100, MlxFft.BACKWARD, 1);
    }

    @Test
    public void testRfft() throws TornadoExecutionPlanException {
        final int half = N / 2 + 1;
        float[] xv = values(ROWS * N, -1, 1, 2);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * ROWS * half);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("rfft", MlxFft::rfft, x, output, ROWS, N, N, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] full = dftJava(toDouble(xv), new double[xv.length], ROWS, N, 1, false, 1);
        assertAllClose("rfft", interleave(crop(full[0], ROWS, N, 1, half), crop(full[1], ROWS, N, 1, half)), output, 2e-3, 2e-3);
    }

    @Test
    public void testIrfft() throws TornadoExecutionPlanException {
        // The input is the half spectrum of a real signal, computed in Java; irfft must return the signal.
        final int half = N / 2 + 1;
        float[] signal = values(ROWS * N, -1, 1, 3);
        double[][] full = dftJava(toDouble(signal), new double[signal.length], ROWS, N, 1, false, 1);
        FloatArray spectrum = FloatArray.fromArray(toFloat(interleave(crop(full[0], ROWS, N, 1, half), crop(full[1], ROWS, N, 1, half))));
        FloatArray output = new FloatArray(ROWS * N);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, spectrum) //
                .libraryTask("irfft", MlxFft::irfft, spectrum, output, ROWS, half, N, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("irfft", toDouble(signal), output, 1e-4, 1e-4);
    }

    // ---------------------------------------------------------------- 2D

    @Test
    public void testFft2() throws TornadoExecutionPlanException {
        float[] xv = values(2 * BATCH * H * W, -1, 1, 4);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * BATCH * H * W);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("fft2", MlxFft::fft2, x, output, BATCH, H, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] in = split(xv);
        double[][] expected = fft2Java(in[0], in[1], BATCH, H, W, false);
        assertAllClose("fft2", interleave(expected[0], expected[1]), output, 2e-3, 2e-3);
    }

    @Test
    public void testIfft2() throws TornadoExecutionPlanException {
        float[] xv = values(2 * BATCH * H * W, -1, 1, 5);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * BATCH * H * W);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("ifft2", MlxFft::ifft2, x, output, BATCH, H, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] in = split(xv);
        double[][] expected = fft2Java(in[0], in[1], BATCH, H, W, true);
        assertAllClose("ifft2", interleave(expected[0], expected[1]), output, 2e-3, 2e-3);
    }

    @Test
    public void testRfft2() throws TornadoExecutionPlanException {
        final int half = W / 2 + 1;
        float[] xv = values(BATCH * H * W, -1, 1, 6);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * BATCH * H * half);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("rfft2", MlxFft::rfft2, x, output, BATCH, H, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] expected = rfft2Java(xv, BATCH, H, W);
        assertAllClose("rfft2", interleave(expected[0], expected[1]), output, 5e-3, 5e-3);
    }

    @Test
    public void testIrfft2() throws TornadoExecutionPlanException {
        float[] signal = values(BATCH * H * W, -1, 1, 7);
        double[][] spec = rfft2Java(signal, BATCH, H, W);
        FloatArray spectrum = FloatArray.fromArray(toFloat(interleave(spec[0], spec[1])));
        FloatArray output = new FloatArray(BATCH * H * W);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, spectrum) //
                .libraryTask("irfft2", MlxFft::irfft2, spectrum, output, BATCH, H, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("irfft2", toDouble(signal), output, 1e-4, 1e-4);
    }

    // ---------------------------------------------------------------- 3D

    @Test
    public void testFftn() throws TornadoExecutionPlanException {
        final int n = D * D * W;
        float[] xv = values(2 * n, -1, 1, 8);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("fftn", MlxFft::fftn, x, output, 1, D, D, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] in = split(xv);
        double[][] expected = fftnJava(in[0], in[1], 1, D, D, W, false);
        assertAllClose("fftn", interleave(expected[0], expected[1]), output, 5e-3, 5e-3);
    }

    @Test
    public void testIfftn() throws TornadoExecutionPlanException {
        final int n = D * D * W;
        float[] xv = values(2 * n, -1, 1, 9);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("ifftn", MlxFft::ifftn, x, output, 1, D, D, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] in = split(xv);
        double[][] expected = fftnJava(in[0], in[1], 1, D, D, W, true);
        assertAllClose("ifftn", interleave(expected[0], expected[1]), output, 5e-3, 5e-3);
    }

    @Test
    public void testRfftn() throws TornadoExecutionPlanException {
        final int half = W / 2 + 1;
        float[] xv = values(D * D * W, -1, 1, 10);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(2 * D * D * half);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("rfftn", MlxFft::rfftn, x, output, 1, D, D, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[][] expected = rfftnJava(xv, 1, D, D, W);
        assertAllClose("rfftn", interleave(expected[0], expected[1]), output, 5e-3, 5e-3);
    }

    @Test
    public void testIrfftn() throws TornadoExecutionPlanException {
        float[] signal = values(D * D * W, -1, 1, 11);
        double[][] spec = rfftnJava(signal, 1, D, D, W);
        FloatArray spectrum = FloatArray.fromArray(toFloat(interleave(spec[0], spec[1])));
        FloatArray output = new FloatArray(D * D * W);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, spectrum) //
                .libraryTask("irfftn", MlxFft::irfftn, spectrum, output, 1, D, D, W, MlxFft.BACKWARD) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("irfftn", toDouble(signal), output, 1e-4, 1e-4);
    }

    // ---------------------------------------------------------------- shifts and frequencies

    @Test
    public void testFftshift() throws TornadoExecutionPlanException {
        for (int len : new int[] { 8, 9 }) {
            float[] xv = values(ROWS * len, -1, 1, 12);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray output = new FloatArray(ROWS * len);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                    .libraryTask("fftshift", MlxFft::fftshift, x, output, ROWS, len) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double[] expected = new double[ROWS * len];
            for (int r = 0; r < ROWS; r++) {
                for (int i = 0; i < len; i++) {
                    expected[r * len + (i + len / 2) % len] = xv[r * len + i];
                }
            }
            assertAllClose("fftshift len=" + len, expected, output, 0, 0);
        }
    }

    @Test
    public void testIfftshift() throws TornadoExecutionPlanException {
        for (int len : new int[] { 8, 9 }) {
            float[] xv = values(ROWS * len, -1, 1, 13);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray output = new FloatArray(ROWS * len);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                    .libraryTask("ifftshift", MlxFft::ifftshift, x, output, ROWS, len) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double[] expected = new double[ROWS * len];
            for (int r = 0; r < ROWS; r++) {
                for (int i = 0; i < len; i++) {
                    expected[r * len + i] = xv[r * len + (i + len / 2) % len];
                }
            }
            assertAllClose("ifftshift len=" + len, expected, output, 0, 0);
        }
    }

    @Test
    public void testFftfreq() throws TornadoExecutionPlanException {
        for (int n : new int[] { 8, 9 }) {
            FloatArray output = new FloatArray(n);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                    .libraryTask("fftfreq", MlxFft::fftfreq, output, n, 0.5f) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double[] expected = new double[n];
            for (int i = 0; i < n; i++) {
                expected[i] = (i < (n + 1) / 2 ? i : i - n) / (0.5 * n);
            }
            assertAllClose("fftfreq n=" + n, expected, output, 1e-6, 1e-6);
        }
    }

    @Test
    public void testRfftfreq() throws TornadoExecutionPlanException {
        for (int n : new int[] { 8, 9 }) {
            FloatArray output = new FloatArray(n / 2 + 1);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                    .libraryTask("rfftfreq", MlxFft::rfftfreq, output, n, 0.5f) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            double[] expected = new double[n / 2 + 1];
            for (int i = 0; i <= n / 2; i++) {
                expected[i] = i / (0.5 * n);
            }
            assertAllClose("rfftfreq n=" + n, expected, output, 1e-6, 1e-6);
        }
    }

    @Test
    public void testFftshiftHalf() throws TornadoExecutionPlanException {
        final int rows = 2;
        final int len = 7;
        HalfFloatArray x = half(values(rows * len, -5, 5, 14));
        HalfFloatArray output = new HalfFloatArray(rows * len);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("fftshift", MlxFft::fftshift, x, output, rows, len) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] w = widen(x);
        double[] expected = new double[rows * len];
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < len; i++) {
                expected[r * len + (i + len / 2) % len] = w[r * len + i];
            }
        }
        assertAllClose("fftshift float16", expected, output, 0, 0);
    }
}
