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
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * MLX linear algebra as TornadoVM library tasks: matrix multiplication (plain, transposed, batched,
 * gathered and segmented), addmm, einsum (as a batched matmul), inner, outer and Kronecker products,
 * tensordot, cross products, and vector and matrix norms.
 */
public final class MlxLinearAlgebra {

    private MlxLinearAlgebra() {
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    public static LibraryTaskDescriptor matmul(FloatArray a, FloatArray b, FloatArray c, int m, int k, int n) {
        return Mlx.task("mlx_matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    public static LibraryTaskDescriptor matmul(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c, int m, int k, int n) {
        return Mlx.task("mlx_matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    public static LibraryTaskDescriptor matmul(BFloat16Array a, BFloat16Array b, BFloat16Array c, int m, int k, int n) {
        return Mlx.task("mlx_matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    public static LibraryTaskDescriptor matmulTransposed(FloatArray a, FloatArray w, FloatArray c, int m, int k, int n) {
        return Mlx.task("mlx_matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    public static LibraryTaskDescriptor matmulTransposed(HalfFloatArray a, HalfFloatArray w, HalfFloatArray c, int m, int k, int n) {
        return Mlx.task("mlx_matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    public static LibraryTaskDescriptor matmulTransposed(BFloat16Array a, BFloat16Array w, BFloat16Array c, int m, int k, int n) {
        return Mlx.task("mlx_matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    public static LibraryTaskDescriptor addmm(FloatArray cIn, FloatArray a, FloatArray b, FloatArray out, int m, int k, int n, float alpha, float beta) {
        return Mlx.task("mlx_addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    public static LibraryTaskDescriptor addmm(HalfFloatArray cIn, HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k, int n, float alpha, float beta) {
        return Mlx.task("mlx_addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    public static LibraryTaskDescriptor addmm(BFloat16Array cIn, BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k, int n, float alpha, float beta) {
        return Mlx.task("mlx_addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    public static LibraryTaskDescriptor gatherMm(FloatArray a, FloatArray b, IntArray lhs, IntArray rhs, FloatArray out, int batchesA, int batchesB, int m, int k, int n) {
        return Mlx.task("mlx_gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    public static LibraryTaskDescriptor gatherMm(HalfFloatArray a, HalfFloatArray b, IntArray lhs, IntArray rhs, HalfFloatArray out, int batchesA, int batchesB, int m, int k, int n) {
        return Mlx.task("mlx_gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    public static LibraryTaskDescriptor gatherMm(BFloat16Array a, BFloat16Array b, IntArray lhs, IntArray rhs, BFloat16Array out, int batchesA, int batchesB, int m, int k, int n) {
        return Mlx.task("mlx_gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    public static LibraryTaskDescriptor cross(FloatArray a, FloatArray b, FloatArray out, int count) {
        return Mlx.task("mlx_linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    public static LibraryTaskDescriptor norm(FloatArray x, FloatArray out, int rows, int cols, float ord) {
        return Mlx.task("mlx_linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor l2Norm(FloatArray x, FloatArray out, int rows, int cols) {
        return Mlx.task("mlx_linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    public static LibraryTaskDescriptor frobeniusNorm(FloatArray x, FloatArray out, int batch, int rows, int cols) {
        return Mlx.task("mlx_linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    public static LibraryTaskDescriptor cross(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int count) {
        return Mlx.task("mlx_linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    public static LibraryTaskDescriptor norm(HalfFloatArray x, HalfFloatArray out, int rows, int cols, float ord) {
        return Mlx.task("mlx_linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor l2Norm(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return Mlx.task("mlx_linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    public static LibraryTaskDescriptor frobeniusNorm(HalfFloatArray x, HalfFloatArray out, int batch, int rows, int cols) {
        return Mlx.task("mlx_linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    public static LibraryTaskDescriptor cross(BFloat16Array a, BFloat16Array b, BFloat16Array out, int count) {
        return Mlx.task("mlx_linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    public static LibraryTaskDescriptor norm(BFloat16Array x, BFloat16Array out, int rows, int cols, float ord) {
        return Mlx.task("mlx_linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor l2Norm(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return Mlx.task("mlx_linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    public static LibraryTaskDescriptor frobeniusNorm(BFloat16Array x, BFloat16Array out, int batch, int rows, int cols) {
        return Mlx.task("mlx_linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    public static LibraryTaskDescriptor einsumBatchedMatmul(FloatArray a, FloatArray b, FloatArray out, int batch, int m, int k, int n) {
        return Mlx.task("mlx_einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    public static LibraryTaskDescriptor einsumBatchedMatmul(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int batch, int m, int k, int n) {
        return Mlx.task("mlx_einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    public static LibraryTaskDescriptor einsumBatchedMatmul(BFloat16Array a, BFloat16Array b, BFloat16Array out, int batch, int m, int k, int n) {
        return Mlx.task("mlx_einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    public static LibraryTaskDescriptor inner(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("mlx_inner", 2, a, b, out);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    public static LibraryTaskDescriptor inner(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("mlx_inner", 2, a, b, out);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    public static LibraryTaskDescriptor inner(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("mlx_inner", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    public static LibraryTaskDescriptor outer(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("mlx_outer", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    public static LibraryTaskDescriptor outer(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("mlx_outer", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    public static LibraryTaskDescriptor outer(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("mlx_outer", 2, a, b, out);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    public static LibraryTaskDescriptor kron(FloatArray a, FloatArray b, FloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        return Mlx.task("mlx_kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    public static LibraryTaskDescriptor kron(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        return Mlx.task("mlx_kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    public static LibraryTaskDescriptor kron(BFloat16Array a, BFloat16Array b, BFloat16Array out, int rowsA, int colsA, int rowsB, int colsB) {
        return Mlx.task("mlx_kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    public static LibraryTaskDescriptor tensordot(FloatArray a, FloatArray b, FloatArray out, int m, int k1, int k2, int n) {
        return Mlx.task("mlx_tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    public static LibraryTaskDescriptor tensordot(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k1, int k2, int n) {
        return Mlx.task("mlx_tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    public static LibraryTaskDescriptor tensordot(BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k1, int k2, int n) {
        return Mlx.task("mlx_tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    public static LibraryTaskDescriptor tensordotAxis(FloatArray a, FloatArray b, FloatArray out, int m, int k, int n) {
        return Mlx.task("mlx_tensordot_axis", 2, a, b, out, m, k, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    public static LibraryTaskDescriptor tensordotAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k, int n) {
        return Mlx.task("mlx_tensordot_axis", 2, a, b, out, m, k, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    public static LibraryTaskDescriptor tensordotAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k, int n) {
        return Mlx.task("mlx_tensordot_axis", 2, a, b, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    public static LibraryTaskDescriptor segmentedMm(FloatArray a, FloatArray b, IntArray segments, FloatArray out, int m, int k, int n) {
        return Mlx.task("mlx_segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    public static LibraryTaskDescriptor segmentedMm(HalfFloatArray a, HalfFloatArray b, IntArray segments, HalfFloatArray out, int m, int k, int n) {
        return Mlx.task("mlx_segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    public static LibraryTaskDescriptor segmentedMm(BFloat16Array a, BFloat16Array b, IntArray segments, BFloat16Array out, int m, int k, int n) {
        return Mlx.task("mlx_segmented_mm", 3, a, b, segments, out, m, k, n);
    }
}
