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
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX convolution library tasks: 1D and 2D convolutions, transposed convolutions
 * and the general 2D convolution with input dilation and kernel flip. Layouts are channels-last as
 * in MLX: x[n, h, w, cin] and weights[cout, kh, kw, cin / groups]. Each test checks the task against
 * a direct convolution in Java. Skipped unless the default device is on the Metal backend and
 * mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxConvolution
 * </code>
 */
public class TestMlxConvolution extends MlxTestBase {

    /** Output length of one axis: the input dilated, padded, and swept by the dilated kernel. */
    private static int outLength(int in, int k, int stride, int padLo, int padHi, int kernelDilation, int inputDilation) {
        int dilatedIn = (in - 1) * inputDilation + 1;
        return (dilatedIn + padLo + padHi - kernelDilation * (k - 1) - 1) / stride + 1;
    }

    /**
     * Direct 2D convolution of x[n, h, w, cin] with weights[cout, kh, kw, cin / groups], in double
     * precision, as MLX's conv_general computes it.
     */
    private static double[] convJava(float[] x, float[] weights, int n, int h, int w, int cin, int cout, int kh, int kw, int stride, int padLo, int padHi, int kernelDilation,
            int inputDilation, int groups, boolean flip) {
        int oh = outLength(h, kh, stride, padLo, padHi, kernelDilation, inputDilation);
        int ow = outLength(w, kw, stride, padLo, padHi, kernelDilation, inputDilation);
        int cinG = cin / groups;
        int coutG = cout / groups;
        double[] y = new double[n * oh * ow * cout];
        for (int b = 0; b < n; b++) {
            for (int i = 0; i < oh; i++) {
                for (int j = 0; j < ow; j++) {
                    for (int co = 0; co < cout; co++) {
                        int group = co / coutG;
                        double acc = 0;
                        for (int a = 0; a < kh; a++) {
                            int ph = i * stride - padLo + a * kernelDilation;
                            if (ph < 0 || ph % inputDilation != 0 || ph / inputDilation >= h) {
                                continue;
                            }
                            for (int c = 0; c < kw; c++) {
                                int pw = j * stride - padLo + c * kernelDilation;
                                if (pw < 0 || pw % inputDilation != 0 || pw / inputDilation >= w) {
                                    continue;
                                }
                                int wa = flip ? kh - 1 - a : a;
                                int wc = flip ? kw - 1 - c : c;
                                for (int ci = 0; ci < cinG; ci++) {
                                    acc += (double) x[((b * h + ph / inputDilation) * w + pw / inputDilation) * cin + group * cinG + ci] * weights[((co * kh + wa) * kw + wc) * cinG + ci];
                                }
                            }
                        }
                        y[((b * oh + i) * ow + j) * cout + co] = acc;
                    }
                }
            }
        }
        return y;
    }

    /** 1D convolution: {@link #convJava} on [n, 1, len, cin] with no padding or dilation on the unit axis. */
    private static double[] conv1dJava(float[] x, float[] weights, int n, int len, int cin, int cout, int k, int stride, int padLo, int padHi, int kernelDilation,
            int inputDilation, int groups, boolean flip) {
        int outLen = outLength(len, k, stride, padLo, padHi, kernelDilation, inputDilation);
        int cinG = cin / groups;
        int coutG = cout / groups;
        double[] y = new double[n * outLen * cout];
        for (int b = 0; b < n; b++) {
            for (int o = 0; o < outLen; o++) {
                for (int co = 0; co < cout; co++) {
                    int group = co / coutG;
                    double acc = 0;
                    for (int a = 0; a < k; a++) {
                        int p = o * stride - padLo + a * kernelDilation;
                        if (p < 0 || p % inputDilation != 0 || p / inputDilation >= len) {
                            continue;
                        }
                        int wa = flip ? k - 1 - a : a;
                        for (int ci = 0; ci < cinG; ci++) {
                            acc += (double) x[(b * len + p / inputDilation) * cin + group * cinG + ci] * weights[(co * k + wa) * cinG + ci];
                        }
                    }
                    y[(b * outLen + o) * cout + co] = acc;
                }
            }
        }
        return y;
    }

    @Test
    public void testConv1d() throws TornadoExecutionPlanException {
        // n = 2, len = 33, cin = 6, cout = 8, k = 5, stride 2, padding 2, dilation 1, groups 2
        final int n = 2;
        final int len = 33;
        final int cin = 6;
        final int cout = 8;
        final int k = 5;
        final int groups = 2;
        float[] xv = values(n * len * cin, -1, 1, 1);
        float[] wv = values(cout * k * (cin / groups), -1, 1, 2);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray w = FloatArray.fromArray(wv);
        double[] expected = conv1dJava(xv, wv, n, len, cin, cout, k, 2, 2, 2, 1, 1, groups, false);
        FloatArray output = new FloatArray(expected.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .libraryTask("conv1d", Mlx::conv1d, x, w, output, n, len, cin, cout, k, 2, 2, 1, groups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("conv1d", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testConv2d() throws TornadoExecutionPlanException {
        // n = 2, 17x13, cin = 4, cout = 6, 3x3 kernel, stride 1, padding 1, dilation 2
        final int n = 2;
        final int h = 17;
        final int w = 13;
        final int cin = 4;
        final int cout = 6;
        float[] xv = values(n * h * w * cin, -1, 1, 3);
        float[] wv = values(cout * 3 * 3 * cin, -1, 1, 4);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray weights = FloatArray.fromArray(wv);
        double[] expected = convJava(xv, wv, n, h, w, cin, cout, 3, 3, 1, 1, 1, 2, 1, 1, false);
        FloatArray output = new FloatArray(expected.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights) //
                .libraryTask("conv2d", Mlx::conv2d, x, weights, output, n, h, w, cin, cout, 3, 3, 1, 1, 2, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("conv2d", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testConvTranspose1d() throws TornadoExecutionPlanException {
        // MLX computes a transposed convolution as a flipped convolution of the input dilated by the
        // stride, padded by dilation * (k - 1) - padding (plus the output padding on the high side).
        final int n = 2;
        final int len = 9;
        final int cin = 4;
        final int cout = 6;
        final int k = 3;
        final int padding = 1;
        float[] xv = values(n * len * cin, -1, 1, 5);
        float[] wv = values(cout * k * cin, -1, 1, 6);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray w = FloatArray.fromArray(wv);
        int lo = k - 1 - padding;
        double[] expected = conv1dJava(xv, wv, n, len, cin, cout, k, 1, lo, lo, 1, 1, 1, true);
        FloatArray output = new FloatArray(expected.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w) //
                .libraryTask("convTranspose1d", Mlx::convTranspose1d, x, w, output, n, len, cin, cout, k, 1, padding, 1, 0, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("convTranspose1d", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testConvTranspose2d() throws TornadoExecutionPlanException {
        // n = 1, 6x5, 16 -> 16 channels, 3x3 kernel, stride 2, padding 1: input dilation 2.
        final int n = 1;
        final int h = 6;
        final int w = 5;
        final int channels = 16;
        final int stride = 2;
        final int padding = 1;
        float[] xv = values(n * h * w * channels, -1, 1, 7);
        float[] wv = values(channels * 3 * 3 * channels, -1, 1, 8);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray weights = FloatArray.fromArray(wv);
        int lo = 3 - 1 - padding;
        double[] expected = convJava(xv, wv, n, h, w, channels, channels, 3, 3, 1, lo, lo, 1, stride, 1, true);
        FloatArray output = new FloatArray(expected.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights) //
                .libraryTask("convTranspose2d", Mlx::convTranspose2d, x, weights, output, n, h, w, channels, channels, 3, 3, stride, padding, 1, 0, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("convTranspose2d", expected, output, 1e-4, 1e-4);
    }

    @Test
    public void testConvGeneral2d() throws TornadoExecutionPlanException {
        // 9x8 input, 3 -> 5 channels, 3x2 kernel, padding (1, 2), input dilation 2, with and without flip.
        final int h = 9;
        final int w = 8;
        final int cin = 3;
        final int cout = 5;
        float[] xv = values(h * w * cin, -1, 1, 9);
        float[] wv = values(cout * 3 * 2 * cin, -1, 1, 10);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray weights = FloatArray.fromArray(wv);
        for (boolean flip : new boolean[] { false, true }) {
            double[] expected = convJava(xv, wv, 1, h, w, cin, cout, 3, 2, 1, 1, 2, 1, 2, 1, flip);
            FloatArray output = new FloatArray(expected.length);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights) //
                    .libraryTask("convGeneral2d", Mlx::convGeneral2d, x, weights, output, 1, h, w, cin, cout, 3, 2, 1, 1, 2, 1, 2, 1, flip) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            assertAllClose("convGeneral2d flip=" + flip, expected, output, 1e-4, 1e-4);
        }
    }

    @Test
    public void testConv2dHalf() throws TornadoExecutionPlanException {
        final int h = 10;
        final int w = 10;
        final int cin = 16;
        final int cout = 4;
        HalfFloatArray x = half(values(h * w * cin, -1, 1, 11));
        HalfFloatArray weights = half(values(cout * 3 * 3 * cin, -1, 1, 12));
        double[] expected = convJava(widen(x), widen(weights), 1, h, w, cin, cout, 3, 3, 1, 1, 1, 1, 1, 1, false);
        HalfFloatArray output = new HalfFloatArray(expected.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weights) //
                .libraryTask("conv2d", Mlx::conv2d, x, weights, output, 1, h, w, cin, cout, 3, 3, 1, 1, 1, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("conv2d float16", expected, output, 1e-2, 1e-2);
    }
}
