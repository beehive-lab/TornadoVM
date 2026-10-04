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
package uk.ac.manchester.tornado.mlx.jit;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * JIT counterpart of the MLX convolutions ({@link uk.ac.manchester.tornado.mlx.MlxConv}), float32,
 * channels-last as in MLX: input {@code [N, D, H, W, Cin]}, weight {@code [Cout, KD, KH, KW, Cin /
 * groups]}, output {@code [N, OD, OH, OW, Cout]} (1D and 2D convolutions have {@code D = H = 1} or
 * {@code D = 1}). A single direct-convolution kernel, one thread per output element, covers every
 * form: a transposed convolution is a convolution of the input dilated by the stride with the
 * flipped kernel, as MLX computes it. The geometry travels in an int array built by
 * {@link #geometry}.
 */
public final class JitConv {

    /** Indices into the geometry array. */
    static final int N = 0;
    static final int D = 1;
    static final int H = 2;
    static final int W = 3;
    static final int CIN = 4;
    static final int OD = 5;
    static final int OH = 6;
    static final int OW = 7;
    static final int COUT = 8;
    static final int KD = 9;
    static final int KH = 10;
    static final int KW = 11;
    /** Per axis (d, h, w): stride at 12, low padding at 15, kernel dilation at 18, input dilation at 21. */
    static final int STRIDE = 12;
    static final int PAD = 15;
    static final int DIL = 18;
    static final int IN_DIL = 21;
    static final int GROUPS = 24;
    static final int FLIP = 25;
    static final int GEOMETRY_LENGTH = 26;

    private JitConv() {
    }

    /**
     * The geometry of a convolution for {@link #conv}. Each per-axis array is {d, h, w}; use 1 for
     * the sizes and strides, and 0 for the padding, of unused leading axes.
     */
    public static int[] geometry(int n, int[] in, int cin, int[] kernel, int cout, int[] stride, int[] padLo, int[] padHi, int[] dilation, int[] inputDilation, int groups,
            boolean flip) {
        int[] g = new int[GEOMETRY_LENGTH];
        g[N] = n;
        g[CIN] = cin;
        g[COUT] = cout;
        g[GROUPS] = groups;
        g[FLIP] = flip ? 1 : 0;
        for (int a = 0; a < 3; a++) {
            g[D + a] = in[a];
            g[KD + a] = kernel[a];
            g[STRIDE + a] = stride[a];
            g[PAD + a] = padLo[a];
            g[DIL + a] = dilation[a];
            g[IN_DIL + a] = inputDilation[a];
            int dilatedIn = (in[a] - 1) * inputDilation[a] + 1;
            int span = dilation[a] * (kernel[a] - 1) + 1;
            g[OD + a] = (dilatedIn + padLo[a] + padHi[a] - span) / stride[a] + 1;
        }
        return g;
    }

    /** Number of output elements of a geometry. */
    public static int outputs(int[] g) {
        return g[N] * g[OD] * g[OH] * g[OW] * g[COUT];
    }

    /** One output element per thread; launch with at least {@link #outputs} threads. */
    @JitBaseline({ "mlx_conv1d", "mlx_conv2d", "mlx_conv_general", "mlx_conv_transpose1d", "mlx_conv_transpose2d" })
    public static void conv(KernelContext ctx, FloatArray x, FloatArray w, FloatArray out, IntArray g, int outputs) {
        int t = ctx.globalIdx;
        if (t < outputs) {
            int inD = g.get(D);
            int inH = g.get(H);
            int inW = g.get(W);
            int cin = g.get(CIN);
            int outD = g.get(OD);
            int outH = g.get(OH);
            int outW = g.get(OW);
            int cout = g.get(COUT);
            int kd = g.get(KD);
            int kh = g.get(KH);
            int kw = g.get(KW);
            int cinG = cin / g.get(GROUPS);
            int coutG = cout / g.get(GROUPS);
            int flip = g.get(FLIP);

            int co = t % cout;
            int ow = (t / cout) % outW;
            int oh = (t / (cout * outW)) % outH;
            int od = (t / (cout * outW * outH)) % outD;
            int n = t / (cout * outW * outH * outD);
            int group = co / coutG;

            float acc = 0.0f;
            for (int a = 0; a < kd; a++) {
                int pd = od * g.get(STRIDE) - g.get(PAD) + a * g.get(DIL);
                int dd = g.get(IN_DIL);
                if (pd >= 0 && pd % dd == 0 && pd / dd < inD) {
                    int id = pd / dd;
                    for (int b = 0; b < kh; b++) {
                        int ph = oh * g.get(STRIDE + 1) - g.get(PAD + 1) + b * g.get(DIL + 1);
                        int dh = g.get(IN_DIL + 1);
                        if (ph >= 0 && ph % dh == 0 && ph / dh < inH) {
                            int ih = ph / dh;
                            for (int c = 0; c < kw; c++) {
                                int pw = ow * g.get(STRIDE + 2) - g.get(PAD + 2) + c * g.get(DIL + 2);
                                int dw = g.get(IN_DIL + 2);
                                if (pw >= 0 && pw % dw == 0 && pw / dw < inW) {
                                    int iw = pw / dw;
                                    int wa = flip != 0 ? kd - 1 - a : a;
                                    int wb = flip != 0 ? kh - 1 - b : b;
                                    int wc = flip != 0 ? kw - 1 - c : c;
                                    int xBase = (((n * inD + id) * inH + ih) * inW + iw) * cin + group * cinG;
                                    int wBase = (((co * kd + wa) * kh + wb) * kw + wc) * cinG;
                                    for (int ci = 0; ci < cinG; ci++) {
                                        acc += x.get(xBase + ci) * w.get(wBase + ci);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            out.set(t, acc);
        }
    }
}
