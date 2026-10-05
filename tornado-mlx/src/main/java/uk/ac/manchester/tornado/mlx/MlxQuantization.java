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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * MLX quantization as TornadoVM library tasks: affine group quantization and dequantization, the
 * quantized matmul, and the mxfp8 quantize and quantized-quantized matmul.
 */
public final class MlxQuantization {

    private MlxQuantization() {
    }

    // MLX's affine format: weights w[rows, cols] are split along cols into groups of groupSize
    // (32, 64 or 128); each group has a scale and a bias, w = scale * q + bias, with q stored in
    // `bits` bits (2-8) packed into uint32 words, low bits first. Packed weights are an IntArray of
    // rows * cols * bits / 32 words; scales and biases have rows * cols / groupSize elements in the
    // activations' float type.

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    public static LibraryTaskDescriptor quantizedMatmul(FloatArray x, IntArray wq, FloatArray scales, FloatArray biases, FloatArray y, int m, int k, int n, int groupSize,
            int bits) {
        return Mlx.task("mlx_quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    public static LibraryTaskDescriptor quantizedMatmul(HalfFloatArray x, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, HalfFloatArray y, int m, int k, int n,
            int groupSize, int bits) {
        return Mlx.task("mlx_quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    public static LibraryTaskDescriptor quantizedMatmul(BFloat16Array x, IntArray wq, BFloat16Array scales, BFloat16Array biases, BFloat16Array y, int m, int k, int n,
            int groupSize, int bits) {
        return Mlx.task("mlx_quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    public static LibraryTaskDescriptor gatherQmm(FloatArray x, IntArray wq, FloatArray scales, FloatArray biases, IntArray lhsIndices, IntArray rhsIndices, FloatArray y,
            int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return Mlx.task("mlx_gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    public static LibraryTaskDescriptor gatherQmm(HalfFloatArray x, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, IntArray lhsIndices, IntArray rhsIndices,
            HalfFloatArray y, int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return Mlx.task("mlx_gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    public static LibraryTaskDescriptor gatherQmm(BFloat16Array x, IntArray wq, BFloat16Array scales, BFloat16Array biases, IntArray lhsIndices, IntArray rhsIndices,
            BFloat16Array y, int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return Mlx.task("mlx_gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    public static LibraryTaskDescriptor quantize(FloatArray w, IntArray wq, FloatArray scales, FloatArray biases, int rows, int cols, int groupSize, int bits) {
        return Mlx.task("mlx_quantize", new int[] { 1, 2, 3 }, w, wq, scales, biases, rows, cols, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    public static LibraryTaskDescriptor quantize(HalfFloatArray w, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, int rows, int cols, int groupSize, int bits) {
        return Mlx.task("mlx_quantize", new int[] { 1, 2, 3 }, w, wq, scales, biases, rows, cols, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    public static LibraryTaskDescriptor quantize(BFloat16Array w, IntArray wq, BFloat16Array scales, BFloat16Array biases, int rows, int cols, int groupSize, int bits) {
        return Mlx.task("mlx_quantize", new int[] { 1, 2, 3 }, w, wq, scales, biases, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    public static LibraryTaskDescriptor dequantize(IntArray wq, FloatArray scales, FloatArray biases, FloatArray w, int rows, int cols, int groupSize, int bits) {
        return Mlx.task("mlx_dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    public static LibraryTaskDescriptor dequantize(IntArray wq, HalfFloatArray scales, HalfFloatArray biases, HalfFloatArray w, int rows, int cols, int groupSize, int bits) {
        return Mlx.task("mlx_dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    public static LibraryTaskDescriptor dequantize(IntArray wq, BFloat16Array scales, BFloat16Array biases, BFloat16Array w, int rows, int cols, int groupSize, int bits) {
        return Mlx.task("mlx_dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /**
     * Quantized-quantized matmul {@code out[m, n] = x[m, k] @ w[n, k]^T} with both operands quantized
     * in {@code mode} (0: mxfp8, 1: nvfp4, 2: mxfp4); {@code w} and {@code wScales} as produced by an
     * MLX quantization in that mode.
     */
    public static LibraryTaskDescriptor qqmm(FloatArray x, IntArray w, ByteArray wScales, FloatArray out, int m, int k, int n, int mode) {
        return Mlx.task("mlx_qqmm", 3, x, w, wScales, out, m, k, n, mode);
    }

    /**
     * Quantizes {@code w[rows, cols]} in an MX mode (0: mxfp8, 2: mxfp4): {@code wq} packs the codes
     * into 32-bit words and {@code scales} holds one E8M0 exponent per group of 32, the layout
     * {@link #qqmm} consumes.
     */
    public static LibraryTaskDescriptor quantizeMx(FloatArray w, IntArray wq, ByteArray scales, int rows, int cols, int mode) {
        return Mlx.task("mlx_quantize_mx", new int[] { 1, 2 }, w, wq, scales, rows, cols, mode);
    }
}
