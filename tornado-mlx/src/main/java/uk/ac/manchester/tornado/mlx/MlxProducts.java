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
 * MLX tensor products and special matrix products as TornadoVM library tasks: einsum (as
 * a batched matmul), inner, outer and Kronecker products, tensordot, the segmented matmul and the
 * quantized-quantized matmul.
 */
public final class MlxProducts {

    private MlxProducts() {
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    @MlxOp("mlx_einsum")
    public static LibraryTaskDescriptor einsumBatchedMatmul(FloatArray a, FloatArray b, FloatArray out, int batch, int m, int k, int n) {
        return Mlx.task("einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    @MlxOp("mlx_einsum")
    public static LibraryTaskDescriptor einsumBatchedMatmul(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int batch, int m, int k, int n) {
        return Mlx.task("einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    @MlxOp("mlx_einsum")
    public static LibraryTaskDescriptor einsumBatchedMatmul(BFloat16Array a, BFloat16Array b, BFloat16Array out, int batch, int m, int k, int n) {
        return Mlx.task("einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    @MlxOp("mlx_inner")
    public static LibraryTaskDescriptor inner(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("inner", 2, a, b, out);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    @MlxOp("mlx_inner")
    public static LibraryTaskDescriptor inner(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("inner", 2, a, b, out);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    @MlxOp("mlx_inner")
    public static LibraryTaskDescriptor inner(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("inner", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    @MlxOp("mlx_outer")
    public static LibraryTaskDescriptor outer(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("outer", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    @MlxOp("mlx_outer")
    public static LibraryTaskDescriptor outer(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("outer", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    @MlxOp("mlx_outer")
    public static LibraryTaskDescriptor outer(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("outer", 2, a, b, out);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    @MlxOp("mlx_kron")
    public static LibraryTaskDescriptor kron(FloatArray a, FloatArray b, FloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        return Mlx.task("kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    @MlxOp("mlx_kron")
    public static LibraryTaskDescriptor kron(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        return Mlx.task("kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    @MlxOp("mlx_kron")
    public static LibraryTaskDescriptor kron(BFloat16Array a, BFloat16Array b, BFloat16Array out, int rowsA, int colsA, int rowsB, int colsB) {
        return Mlx.task("kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    @MlxOp("mlx_tensordot")
    public static LibraryTaskDescriptor tensordot(FloatArray a, FloatArray b, FloatArray out, int m, int k1, int k2, int n) {
        return Mlx.task("tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    @MlxOp("mlx_tensordot")
    public static LibraryTaskDescriptor tensordot(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k1, int k2, int n) {
        return Mlx.task("tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    @MlxOp("mlx_tensordot")
    public static LibraryTaskDescriptor tensordot(BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k1, int k2, int n) {
        return Mlx.task("tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    @MlxOp("mlx_tensordot_axis")
    public static LibraryTaskDescriptor tensordotAxis(FloatArray a, FloatArray b, FloatArray out, int m, int k, int n) {
        return Mlx.task("tensordot_axis", 2, a, b, out, m, k, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    @MlxOp("mlx_tensordot_axis")
    public static LibraryTaskDescriptor tensordotAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k, int n) {
        return Mlx.task("tensordot_axis", 2, a, b, out, m, k, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    @MlxOp("mlx_tensordot_axis")
    public static LibraryTaskDescriptor tensordotAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k, int n) {
        return Mlx.task("tensordot_axis", 2, a, b, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    @MlxOp("mlx_segmented_mm")
    public static LibraryTaskDescriptor segmentedMm(FloatArray a, FloatArray b, IntArray segments, FloatArray out, int m, int k, int n) {
        return Mlx.task("segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    @MlxOp("mlx_segmented_mm")
    public static LibraryTaskDescriptor segmentedMm(HalfFloatArray a, HalfFloatArray b, IntArray segments, HalfFloatArray out, int m, int k, int n) {
        return Mlx.task("segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    @MlxOp("mlx_segmented_mm")
    public static LibraryTaskDescriptor segmentedMm(BFloat16Array a, BFloat16Array b, IntArray segments, BFloat16Array out, int m, int k, int n) {
        return Mlx.task("segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * Quantized-quantized matmul {@code out[m, n] = x[m, k] @ w[n, k]^T} with both operands quantized
     * in {@code mode} (0: mxfp8, 1: nvfp4, 2: mxfp4); {@code w} and {@code wScales} as produced by an
     * MLX quantization in that mode.
     */
    @MlxOp("mlx_qqmm")
    public static LibraryTaskDescriptor qqmm(FloatArray x, IntArray w, ByteArray wScales, FloatArray out, int m, int k, int n, int mode) {
        return Mlx.task("qqmm", 3, x, w, wScales, out, m, k, n, mode);
    }

    /**
     * Quantizes {@code w[rows, cols]} in an MX mode (0: mxfp8, 2: mxfp4): {@code wq} packs the codes
     * into 32-bit words and {@code scales} holds one E8M0 exponent per group of 32, the layout
     * {@link #qqmm} consumes.
     */
    @MlxOp("mlx_quantize")
    public static LibraryTaskDescriptor quantizeMx(FloatArray w, IntArray wq, ByteArray scales, int rows, int cols, int mode) {
        return Mlx.task("quantize_mx", new int[] { 1, 2 }, w, wq, scales, rows, cols, mode);
    }
}
