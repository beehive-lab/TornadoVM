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
package uk.ac.manchester.tornado.mlx;

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * MLX convolutions (Tier 2) as TornadoVM library tasks, channels-last as in MLX. Stride, padding and
 * dilation apply equally to every spatial axis (MLX accepts them per axis; one value keeps the
 * argument lists within the library-task arity).
 */
public final class MlxConv {

    private MlxConv() {
    }

    /**
     * 1D convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code out[n, outLen,
     * cout]}.
     */
    @MlxOp("mlx_conv1d")
    public static LibraryTaskDescriptor conv1d(FloatArray x, FloatArray w, FloatArray out, int n, int len, int cin, int cout, int k, int stride, int padding, int dilation,
            int groups) {
        return Mlx.task("conv1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups);
    }

    /**
     * 1D convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code out[n, outLen,
     * cout]}.
     */
    @MlxOp("mlx_conv1d")
    public static LibraryTaskDescriptor conv1d(HalfFloatArray x, HalfFloatArray w, HalfFloatArray out, int n, int len, int cin, int cout, int k, int stride, int padding,
            int dilation, int groups) {
        return Mlx.task("conv1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups);
    }

    /**
     * 1D convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code out[n, outLen,
     * cout]}.
     */
    @MlxOp("mlx_conv1d")
    public static LibraryTaskDescriptor conv1d(BFloat16Array x, BFloat16Array w, BFloat16Array out, int n, int len, int cin, int cout, int k, int stride, int padding,
            int dilation, int groups) {
        return Mlx.task("conv1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups);
    }

    /**
     * 2D convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]}, {@code
     * out[n, outH, outW, cout]}.
     */
    @MlxOp("mlx_conv2d")
    public static LibraryTaskDescriptor conv2d(FloatArray x, FloatArray weight, FloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padding, int dilation, int groups) {
        return Mlx.task("conv2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups);
    }

    /**
     * 2D convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]}, {@code
     * out[n, outH, outW, cout]}.
     */
    @MlxOp("mlx_conv2d")
    public static LibraryTaskDescriptor conv2d(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padding, int dilation, int groups) {
        return Mlx.task("conv2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups);
    }

    /**
     * 2D convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]}, {@code
     * out[n, outH, outW, cout]}.
     */
    @MlxOp("mlx_conv2d")
    public static LibraryTaskDescriptor conv2d(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padding, int dilation, int groups) {
        return Mlx.task("conv2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups);
    }

    /**
     * 1D transposed convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code
     * out[n, (len - 1) * stride - 2 * padding + dilation * (k - 1) + outputPadding + 1, cout]}.
     */
    @MlxOp("mlx_conv_transpose1d")
    public static LibraryTaskDescriptor convTranspose1d(FloatArray x, FloatArray w, FloatArray out, int n, int len, int cin, int cout, int k, int stride, int padding,
            int dilation, int outputPadding, int groups) {
        return Mlx.task("conv_transpose1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 1D transposed convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code
     * out[n, (len - 1) * stride - 2 * padding + dilation * (k - 1) + outputPadding + 1, cout]}.
     */
    @MlxOp("mlx_conv_transpose1d")
    public static LibraryTaskDescriptor convTranspose1d(HalfFloatArray x, HalfFloatArray w, HalfFloatArray out, int n, int len, int cin, int cout, int k, int stride,
            int padding, int dilation, int outputPadding, int groups) {
        return Mlx.task("conv_transpose1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 1D transposed convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code
     * out[n, (len - 1) * stride - 2 * padding + dilation * (k - 1) + outputPadding + 1, cout]}.
     */
    @MlxOp("mlx_conv_transpose1d")
    public static LibraryTaskDescriptor convTranspose1d(BFloat16Array x, BFloat16Array w, BFloat16Array out, int n, int len, int cin, int cout, int k, int stride,
            int padding, int dilation, int outputPadding, int groups) {
        return Mlx.task("conv_transpose1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 2D transposed convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]};
     * each output axis as in {@link #convTranspose1d}.
     */
    @MlxOp("mlx_conv_transpose2d")
    public static LibraryTaskDescriptor convTranspose2d(FloatArray x, FloatArray weight, FloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padding, int dilation, int outputPadding, int groups) {
        return Mlx.task("conv_transpose2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 2D transposed convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]};
     * each output axis as in {@link #convTranspose1d}.
     */
    @MlxOp("mlx_conv_transpose2d")
    public static LibraryTaskDescriptor convTranspose2d(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padding, int dilation, int outputPadding, int groups) {
        return Mlx.task("conv_transpose2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 2D transposed convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]};
     * each output axis as in {@link #convTranspose1d}.
     */
    @MlxOp("mlx_conv_transpose2d")
    public static LibraryTaskDescriptor convTranspose2d(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padding, int dilation, int outputPadding, int groups) {
        return Mlx.task("conv_transpose2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * General 2D convolution: {@code x[n, h, w, cin]} dilated by {@code inputDilation} and padded by
     * {@code padLo} / {@code padHi}, {@code weight[cout, kh, kw, cin / groups]} dilated by {@code
     * kernelDilation} (and flipped, a true convolution, if {@code flip}).
     */
    @MlxOp("mlx_conv_general")
    public static LibraryTaskDescriptor convGeneral2d(FloatArray x, FloatArray weight, FloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padLo, int padHi, int kernelDilation, int inputDilation, int groups, boolean flip) {
        return Mlx.task("conv_general", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip);
    }

    /**
     * General 2D convolution: {@code x[n, h, w, cin]} dilated by {@code inputDilation} and padded by
     * {@code padLo} / {@code padHi}, {@code weight[cout, kh, kw, cin / groups]} dilated by {@code
     * kernelDilation} (and flipped, a true convolution, if {@code flip}).
     */
    @MlxOp("mlx_conv_general")
    public static LibraryTaskDescriptor convGeneral2d(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padLo, int padHi, int kernelDilation, int inputDilation, int groups, boolean flip) {
        return Mlx.task("conv_general", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip);
    }

    /**
     * General 2D convolution: {@code x[n, h, w, cin]} dilated by {@code inputDilation} and padded by
     * {@code padLo} / {@code padHi}, {@code weight[cout, kh, kw, cin / groups]} dilated by {@code
     * kernelDilation} (and flipped, a true convolution, if {@code flip}).
     */
    @MlxOp("mlx_conv_general")
    public static LibraryTaskDescriptor convGeneral2d(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padLo, int padHi, int kernelDilation, int inputDilation, int groups, boolean flip) {
        return Mlx.task("conv_general", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip);
    }
}
