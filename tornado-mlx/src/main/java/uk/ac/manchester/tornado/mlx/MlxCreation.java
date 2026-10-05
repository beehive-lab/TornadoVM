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
 * MLX array construction (ranges, constants, identity and triangular matrices, windows, grids) and
 * matrix-structure operations (diag, diagonal, trace, tril, triu) as TornadoVM library
 * tasks. Constructors write into {@code out}, whose type sets the dtype and whose length sets the
 * size where no size is given.
 */
public final class MlxCreation {

    private MlxCreation() {
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(FloatArray out, float start, float stop, float step) {
        return Mlx.task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(HalfFloatArray out, float start, float stop, float step) {
        return Mlx.task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(BFloat16Array out, float start, float stop, float step) {
        return Mlx.task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(IntArray out, float start, float stop, float step) {
        return Mlx.task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out.length} evenly spaced values from start to stop (inclusive). */
    public static LibraryTaskDescriptor linspace(FloatArray out, float start, float stop) {
        return Mlx.task("mlx_linspace", 0, out, start, stop);
    }

    /** {@code out.length} evenly spaced values from start to stop (inclusive). */
    public static LibraryTaskDescriptor linspace(HalfFloatArray out, float start, float stop) {
        return Mlx.task("mlx_linspace", 0, out, start, stop);
    }

    /** {@code out.length} evenly spaced values from start to stop (inclusive). */
    public static LibraryTaskDescriptor linspace(BFloat16Array out, float start, float stop) {
        return Mlx.task("mlx_linspace", 0, out, start, stop);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(FloatArray out, int n, int m, int k) {
        return Mlx.task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(FloatArray out, int n) {
        return Mlx.task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(FloatArray out, int n, int m, int k) {
        return Mlx.task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(FloatArray out, float value) {
        return Mlx.task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(FloatArray a, FloatArray out, float value) {
        return Mlx.task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(FloatArray out) {
        return Mlx.task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(FloatArray out) {
        return Mlx.task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_ones_like", 1, a, out);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(HalfFloatArray out, int n, int m, int k) {
        return Mlx.task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(HalfFloatArray out, int n) {
        return Mlx.task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(HalfFloatArray out, int n, int m, int k) {
        return Mlx.task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(HalfFloatArray out, float value) {
        return Mlx.task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(HalfFloatArray a, HalfFloatArray out, float value) {
        return Mlx.task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(HalfFloatArray out) {
        return Mlx.task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(HalfFloatArray out) {
        return Mlx.task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_ones_like", 1, a, out);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(BFloat16Array out, int n, int m, int k) {
        return Mlx.task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(BFloat16Array out, int n) {
        return Mlx.task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(BFloat16Array out, int n, int m, int k) {
        return Mlx.task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(BFloat16Array out, float value) {
        return Mlx.task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(BFloat16Array a, BFloat16Array out, float value) {
        return Mlx.task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(BFloat16Array out) {
        return Mlx.task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(BFloat16Array out) {
        return Mlx.task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_ones_like", 1, a, out);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(IntArray out, int n, int m, int k) {
        return Mlx.task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(IntArray out, int n) {
        return Mlx.task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(IntArray out, int n, int m, int k) {
        return Mlx.task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(IntArray out, float value) {
        return Mlx.task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(IntArray a, IntArray out, float value) {
        return Mlx.task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(IntArray out) {
        return Mlx.task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(IntArray a, IntArray out) {
        return Mlx.task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(IntArray out) {
        return Mlx.task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(IntArray a, IntArray out) {
        return Mlx.task("mlx_ones_like", 1, a, out);
    }

    /** The Bartlett window of length {@code out.length}. */
    public static LibraryTaskDescriptor bartlett(FloatArray out) {
        return Mlx.task("mlx_bartlett", 0, out);
    }

    /** The Blackman window of length {@code out.length}. */
    public static LibraryTaskDescriptor blackman(FloatArray out) {
        return Mlx.task("mlx_blackman", 0, out);
    }

    /** The Hamming window of length {@code out.length}. */
    public static LibraryTaskDescriptor hamming(FloatArray out) {
        return Mlx.task("mlx_hamming", 0, out);
    }

    /** The Hann window of length {@code out.length}. */
    public static LibraryTaskDescriptor hanning(FloatArray out) {
        return Mlx.task("mlx_hanning", 0, out);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(FloatArray x, FloatArray y, FloatArray outX, FloatArray outY, boolean ij) {
        return Mlx.task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(FloatArray v, FloatArray out, int k) {
        return Mlx.task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(FloatArray a, FloatArray out, int rows, int cols, int offset) {
        return Mlx.task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(FloatArray a, FloatArray out, int rows, int cols, int offset) {
        return Mlx.task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(FloatArray a, FloatArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(FloatArray a, FloatArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_triu", 1, a, out, rows, cols, k);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(HalfFloatArray x, HalfFloatArray y, HalfFloatArray outX, HalfFloatArray outY, boolean ij) {
        return Mlx.task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(HalfFloatArray v, HalfFloatArray out, int k) {
        return Mlx.task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int offset) {
        return Mlx.task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int offset) {
        return Mlx.task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_triu", 1, a, out, rows, cols, k);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(BFloat16Array x, BFloat16Array y, BFloat16Array outX, BFloat16Array outY, boolean ij) {
        return Mlx.task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(BFloat16Array v, BFloat16Array out, int k) {
        return Mlx.task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(BFloat16Array a, BFloat16Array out, int rows, int cols, int offset) {
        return Mlx.task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(BFloat16Array a, BFloat16Array out, int rows, int cols, int offset) {
        return Mlx.task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(BFloat16Array a, BFloat16Array out, int rows, int cols, int k) {
        return Mlx.task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(BFloat16Array a, BFloat16Array out, int rows, int cols, int k) {
        return Mlx.task("mlx_triu", 1, a, out, rows, cols, k);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(IntArray x, IntArray y, IntArray outX, IntArray outY, boolean ij) {
        return Mlx.task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(IntArray v, IntArray out, int k) {
        return Mlx.task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(IntArray a, IntArray out, int rows, int cols, int offset) {
        return Mlx.task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(IntArray a, IntArray out, int rows, int cols, int offset) {
        return Mlx.task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(IntArray a, IntArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(IntArray a, IntArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_triu", 1, a, out, rows, cols, k);
    }
}
