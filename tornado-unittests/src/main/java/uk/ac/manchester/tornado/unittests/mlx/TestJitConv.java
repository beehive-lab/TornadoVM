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
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxConv;
import uk.ac.manchester.tornado.mlx.jit.JitConv;

/**
 * The MLX convolutions and their KernelContext JIT counterpart (one direct-convolution kernel),
 * run in one graph on the same inputs and checked against a Java reference: 1D, 2D and 3D, with
 * strides, padding, dilation and groups; transposed convolutions; and the general form with input
 * dilation, asymmetric padding and kernel flipping.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestJitConv
 * </code>
 */
public class TestJitConv extends MlxTestBase {

    interface MlxConvTask {
        void add(TaskGraph g, FloatArray x, FloatArray w, FloatArray out);
    }

    /** Direct convolution over a {@link JitConv#geometry} in double precision. */
    private static float[] reference(float[] x, float[] w, int[] g) {
        int n = g[0];
        int[] in = { g[1], g[2], g[3] };
        int cin = g[4];
        int[] out = { g[5], g[6], g[7] };
        int cout = g[8];
        int[] k = { g[9], g[10], g[11] };
        int groups = g[24];
        boolean flip = g[25] != 0;
        int cinG = cin / groups;
        int coutG = cout / groups;
        float[] y = new float[n * out[0] * out[1] * out[2] * cout];
        int[] pos = new int[3];
        for (int b = 0; b < n; b++) {
            for (int od = 0; od < out[0]; od++) {
                for (int oh = 0; oh < out[1]; oh++) {
                    for (int ow = 0; ow < out[2]; ow++) {
                        for (int co = 0; co < cout; co++) {
                            double acc = 0;
                            int group = co / coutG;
                            for (int a = 0; a < k[0]; a++) {
                                for (int bb = 0; bb < k[1]; bb++) {
                                    for (int c = 0; c < k[2]; c++) {
                                        int[] o = { od, oh, ow };
                                        int[] kk = { a, bb, c };
                                        boolean valid = true;
                                        for (int ax = 0; ax < 3; ax++) {
                                            int p = o[ax] * g[12 + ax] - g[15 + ax] + kk[ax] * g[18 + ax];
                                            int dil = g[21 + ax];
                                            if (p < 0 || p % dil != 0 || p / dil >= in[ax]) {
                                                valid = false;
                                                break;
                                            }
                                            pos[ax] = p / dil;
                                        }
                                        if (!valid) {
                                            continue;
                                        }
                                        int wa = flip ? k[0] - 1 - a : a;
                                        int wb = flip ? k[1] - 1 - bb : bb;
                                        int wc = flip ? k[2] - 1 - c : c;
                                        for (int ci = 0; ci < cinG; ci++) {
                                            acc += x[(((b * in[0] + pos[0]) * in[1] + pos[1]) * in[2] + pos[2]) * cin + group * cinG + ci] * w[(((co * k[0] + wa) * k[1] + wb) * k[2] + wc)
                                                    * cinG + ci];
                                        }
                                    }
                                }
                            }
                            y[(((b * out[0] + od) * out[1] + oh) * out[2] + ow) * cout + co] = (float) acc;
                        }
                    }
                }
            }
        }
        return y;
    }

    private static void check(String name, int[] geometry, MlxConvTask mlx) throws TornadoExecutionPlanException {
        int inputs = geometry[0] * geometry[1] * geometry[2] * geometry[3] * geometry[4];
        int weights = geometry[8] * geometry[9] * geometry[10] * geometry[11] * (geometry[4] / geometry[24]);
        int outputs = JitConv.outputs(geometry);
        float[] xv = values(inputs, -1, 1, 231L + inputs);
        float[] wv = values(weights, -1, 1, 232L + weights);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray w = FloatArray.fromArray(wv);
        IntArray g = IntArray.fromArray(geometry);
        FloatArray outMlx = new FloatArray(outputs);
        FloatArray outJit = new FloatArray(outputs);
        TaskGraph graph = new TaskGraph("cv").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w, g);
        mlx.add(graph, x, w, outMlx);
        graph.task("j", JitConv::conv, new KernelContext(), x, w, outJit, g, outputs).transferToHost(DataTransferMode.EVERY_EXECUTION, outMlx, outJit);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("cv.j", TestJitElementwise.grid1D(outputs))).execute();
        }
        float[] expected = reference(xv, wv, geometry);
        for (int i = 0; i < outputs; i++) {
            assertClose(name + " JIT", i, expected[i], outJit.get(i), 1e-4, 1e-4);
            assertClose(name + " MLX", i, expected[i], outMlx.get(i), 1e-4, 1e-4);
        }
    }

    private static int[] same(int v) {
        return new int[] { v, v, v };
    }

    /** Geometry of a forward convolution; unused leading axes have size 1 and no padding. */
    static int[] forward(int n, int[] in, int cin, int[] k, int cout, int stride, int padding, int dilation, int groups, int dims) {
        int[] s = same(1);
        int[] p = same(0);
        int[] d = same(1);
        for (int a = 3 - dims; a < 3; a++) {
            s[a] = stride;
            p[a] = padding;
            d[a] = dilation;
        }
        return JitConv.geometry(n, in, cin, k, cout, s, p, p, d, same(1), groups, false);
    }

    /** Geometry of a transposed convolution, as MLX computes it: the input dilated by the stride, the kernel flipped. */
    static int[] transposed(int n, int[] in, int cin, int[] k, int cout, int stride, int padding, int dilation, int outputPadding, int groups, int dims) {
        int[] lo = same(0);
        int[] hi = same(0);
        int[] d = same(1);
        int[] inDil = same(1);
        for (int a = 3 - dims; a < 3; a++) {
            lo[a] = dilation * (k[a] - 1) - padding;
            hi[a] = lo[a] + outputPadding;
            d[a] = dilation;
            inDil[a] = stride;
        }
        return JitConv.geometry(n, in, cin, k, cout, same(1), lo, hi, d, inDil, groups, true);
    }

    @Test
    public void testConv1d() throws TornadoExecutionPlanException {
        check("conv1d", forward(2, new int[] { 1, 1, 33 }, 6, new int[] { 1, 1, 5 }, 8, 2, 2, 1, 2, 1), //
                (g, x, w, out) -> g.libraryTask("mlx", MlxConv::conv1d, x, w, out, 2, 33, 6, 8, 5, 2, 2, 1, 2));
    }

    @Test
    public void testConv2d() throws TornadoExecutionPlanException {
        check("conv2d", forward(2, new int[] { 1, 17, 13 }, 4, new int[] { 1, 3, 3 }, 6, 1, 1, 2, 1, 2), //
                (g, x, w, out) -> g.libraryTask("mlx", MlxConv::conv2d, x, w, out, 2, 17, 13, 4, 6, 3, 3, 1, 1, 2, 1));
    }

    @Test
    public void testConvTranspose1d() throws TornadoExecutionPlanException {
        check("convTranspose1d", transposed(2, new int[] { 1, 1, 9 }, 4, new int[] { 1, 1, 3 }, 6, 1, 1, 1, 0, 1, 1), //
                (g, x, w, out) -> g.libraryTask("mlx", MlxConv::convTranspose1d, x, w, out, 2, 9, 4, 6, 3, 1, 1, 1, 0, 1));
    }

    @Test
    public void testConvTranspose2d() throws TornadoExecutionPlanException {
        check("convTranspose2d", transposed(1, new int[] { 1, 6, 5 }, 16, new int[] { 1, 3, 3 }, 16, 2, 1, 1, 0, 1, 2), //
                (g, x, w, out) -> g.libraryTask("mlx", MlxConv::convTranspose2d, x, w, out, 1, 6, 5, 16, 16, 3, 3, 2, 1, 1, 0, 1));
    }

    @Test
    public void testConvGeneral() throws TornadoExecutionPlanException {
        for (boolean flip : new boolean[] { false, true }) {
            int[] geometry = JitConv.geometry(1, new int[] { 1, 9, 8 }, 3, new int[] { 1, 3, 2 }, 5, same(1), new int[] { 0, 1, 1 }, new int[] { 0, 2, 2 }, same(1), new int[] { 1, 2, 2 }, 1,
                    flip);
            check("convGeneral2d flip=" + flip, geometry, (g, x, w, out) -> g.libraryTask("mlx", MlxConv::convGeneral2d, x, w, out, 1, 9, 8, 3, 5, 3, 2, 1, 1, 2, 1, 2, 1, flip));
        }
    }

    @Test
    public void testConv2dHalf() throws TornadoExecutionPlanException {
        int[] geometry = forward(1, new int[] { 1, 10, 10 }, 16, new int[] { 1, 3, 3 }, 4, 1, 1, 1, 1, 2);
        float[] xv = values(10 * 10 * 16, -1, 1, 241);
        float[] wv = values(4 * 3 * 3 * 16, -1, 1, 242);
        HalfFloatArray x = half(xv);
        HalfFloatArray w = half(wv);
        HalfFloatArray out = new HalfFloatArray(JitConv.outputs(geometry));
        run(new TaskGraph("ch").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, w) //
                .libraryTask("mlx", MlxConv::conv2d, x, w, out, 1, 10, 10, 16, 4, 3, 3, 1, 1, 1, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        float[] expected = reference(widen(x), widen(w), geometry);
        double[] e = new double[expected.length];
        for (int i = 0; i < e.length; i++) {
            e[i] = expected[i];
        }
        assertAllClose("conv2d float16", e, out, 1e-2, 1e-2);
    }
}
