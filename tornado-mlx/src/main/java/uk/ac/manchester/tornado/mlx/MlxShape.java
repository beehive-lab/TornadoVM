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
 * MLX shape and layout operations as TornadoVM library tasks: reshaping, squeezing and
 * expanding, axis permutations, broadcasting, strided views, type conversion and reinterpretation,
 * joining and splitting, repetition, rolling and padding. Results are written row-major into
 * {@code out}.
 */
public final class MlxShape {

    private MlxShape() {
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    @MlxOp("mlx_reshape")
    public static LibraryTaskDescriptor reshape(FloatArray x, FloatArray out, int rows, int cols) {
        return Mlx.task("reshape", 1, x, out, rows, cols);
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    @MlxOp("mlx_reshape")
    public static LibraryTaskDescriptor reshape(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return Mlx.task("reshape", 1, x, out, rows, cols);
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    @MlxOp("mlx_reshape")
    public static LibraryTaskDescriptor reshape(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return Mlx.task("reshape", 1, x, out, rows, cols);
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    @MlxOp("mlx_reshape")
    public static LibraryTaskDescriptor reshape(IntArray x, IntArray out, int rows, int cols) {
        return Mlx.task("reshape", 1, x, out, rows, cols);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    @MlxOp("mlx_flatten")
    public static LibraryTaskDescriptor flatten(FloatArray x, FloatArray out, int d0, int d1, int d2) {
        return Mlx.task("flatten", 1, x, out, d0, d1, d2);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    @MlxOp("mlx_flatten")
    public static LibraryTaskDescriptor flatten(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2) {
        return Mlx.task("flatten", 1, x, out, d0, d1, d2);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    @MlxOp("mlx_flatten")
    public static LibraryTaskDescriptor flatten(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2) {
        return Mlx.task("flatten", 1, x, out, d0, d1, d2);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    @MlxOp("mlx_flatten")
    public static LibraryTaskDescriptor flatten(IntArray x, IntArray out, int d0, int d1, int d2) {
        return Mlx.task("flatten", 1, x, out, d0, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    @MlxOp("mlx_unflatten")
    public static LibraryTaskDescriptor unflatten(FloatArray x, FloatArray out, int d1, int d2) {
        return Mlx.task("unflatten", 1, x, out, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    @MlxOp("mlx_unflatten")
    public static LibraryTaskDescriptor unflatten(HalfFloatArray x, HalfFloatArray out, int d1, int d2) {
        return Mlx.task("unflatten", 1, x, out, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    @MlxOp("mlx_unflatten")
    public static LibraryTaskDescriptor unflatten(BFloat16Array x, BFloat16Array out, int d1, int d2) {
        return Mlx.task("unflatten", 1, x, out, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    @MlxOp("mlx_unflatten")
    public static LibraryTaskDescriptor unflatten(IntArray x, IntArray out, int d1, int d2) {
        return Mlx.task("unflatten", 1, x, out, d1, d2);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    @MlxOp("mlx_squeeze")
    public static LibraryTaskDescriptor squeeze(FloatArray x, FloatArray out, int d0, int d1) {
        return Mlx.task("squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    @MlxOp("mlx_squeeze")
    public static LibraryTaskDescriptor squeeze(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return Mlx.task("squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    @MlxOp("mlx_squeeze")
    public static LibraryTaskDescriptor squeeze(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return Mlx.task("squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    @MlxOp("mlx_squeeze")
    public static LibraryTaskDescriptor squeeze(IntArray x, IntArray out, int d0, int d1) {
        return Mlx.task("squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    @MlxOp("mlx_squeeze_axis")
    public static LibraryTaskDescriptor squeezeAxis(FloatArray x, FloatArray out, int d0, int d1) {
        return Mlx.task("squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    @MlxOp("mlx_squeeze_axis")
    public static LibraryTaskDescriptor squeezeAxis(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return Mlx.task("squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    @MlxOp("mlx_squeeze_axis")
    public static LibraryTaskDescriptor squeezeAxis(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return Mlx.task("squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    @MlxOp("mlx_squeeze_axis")
    public static LibraryTaskDescriptor squeezeAxis(IntArray x, IntArray out, int d0, int d1) {
        return Mlx.task("squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    @MlxOp("mlx_squeeze_axes")
    public static LibraryTaskDescriptor squeezeAxes(FloatArray x, FloatArray out, int d0, int d1) {
        return Mlx.task("squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    @MlxOp("mlx_squeeze_axes")
    public static LibraryTaskDescriptor squeezeAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return Mlx.task("squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    @MlxOp("mlx_squeeze_axes")
    public static LibraryTaskDescriptor squeezeAxes(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return Mlx.task("squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    @MlxOp("mlx_squeeze_axes")
    public static LibraryTaskDescriptor squeezeAxes(IntArray x, IntArray out, int d0, int d1) {
        return Mlx.task("squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    @MlxOp("mlx_expand_dims")
    public static LibraryTaskDescriptor expandDims(FloatArray x, FloatArray out, int d0, int d1) {
        return Mlx.task("expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    @MlxOp("mlx_expand_dims")
    public static LibraryTaskDescriptor expandDims(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return Mlx.task("expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    @MlxOp("mlx_expand_dims")
    public static LibraryTaskDescriptor expandDims(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return Mlx.task("expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    @MlxOp("mlx_expand_dims")
    public static LibraryTaskDescriptor expandDims(IntArray x, IntArray out, int d0, int d1) {
        return Mlx.task("expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    @MlxOp("mlx_expand_dims_axes")
    public static LibraryTaskDescriptor expandDimsAxes(FloatArray x, FloatArray out, int d0, int d1) {
        return Mlx.task("expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    @MlxOp("mlx_expand_dims_axes")
    public static LibraryTaskDescriptor expandDimsAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return Mlx.task("expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    @MlxOp("mlx_expand_dims_axes")
    public static LibraryTaskDescriptor expandDimsAxes(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return Mlx.task("expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    @MlxOp("mlx_expand_dims_axes")
    public static LibraryTaskDescriptor expandDimsAxes(IntArray x, IntArray out, int d0, int d1) {
        return Mlx.task("expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x} as an array of at least 1 dimension. */
    @MlxOp("mlx_atleast_1d")
    public static LibraryTaskDescriptor atleast1d(FloatArray x, FloatArray out) {
        return Mlx.task("atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 1 dimension. */
    @MlxOp("mlx_atleast_1d")
    public static LibraryTaskDescriptor atleast1d(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 1 dimension. */
    @MlxOp("mlx_atleast_1d")
    public static LibraryTaskDescriptor atleast1d(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 1 dimension. */
    @MlxOp("mlx_atleast_1d")
    public static LibraryTaskDescriptor atleast1d(IntArray x, IntArray out) {
        return Mlx.task("atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    @MlxOp("mlx_atleast_2d")
    public static LibraryTaskDescriptor atleast2d(FloatArray x, FloatArray out) {
        return Mlx.task("atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    @MlxOp("mlx_atleast_2d")
    public static LibraryTaskDescriptor atleast2d(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    @MlxOp("mlx_atleast_2d")
    public static LibraryTaskDescriptor atleast2d(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    @MlxOp("mlx_atleast_2d")
    public static LibraryTaskDescriptor atleast2d(IntArray x, IntArray out) {
        return Mlx.task("atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    @MlxOp("mlx_atleast_3d")
    public static LibraryTaskDescriptor atleast3d(FloatArray x, FloatArray out) {
        return Mlx.task("atleast_3d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    @MlxOp("mlx_atleast_3d")
    public static LibraryTaskDescriptor atleast3d(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("atleast_3d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    @MlxOp("mlx_atleast_3d")
    public static LibraryTaskDescriptor atleast3d(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("atleast_3d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    @MlxOp("mlx_atleast_3d")
    public static LibraryTaskDescriptor atleast3d(IntArray x, IntArray out) {
        return Mlx.task("atleast_3d", 1, x, out);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    @MlxOp("mlx_transpose_axes")
    public static LibraryTaskDescriptor transposeAxes(FloatArray x, FloatArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return Mlx.task("transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    @MlxOp("mlx_transpose_axes")
    public static LibraryTaskDescriptor transposeAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return Mlx.task("transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    @MlxOp("mlx_transpose_axes")
    public static LibraryTaskDescriptor transposeAxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return Mlx.task("transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    @MlxOp("mlx_transpose_axes")
    public static LibraryTaskDescriptor transposeAxes(IntArray x, IntArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return Mlx.task("transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    @MlxOp("mlx_swapaxes")
    public static LibraryTaskDescriptor swapaxes(FloatArray x, FloatArray out, int d0, int d1, int d2, int axis1, int axis2) {
        return Mlx.task("swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    @MlxOp("mlx_swapaxes")
    public static LibraryTaskDescriptor swapaxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2, int axis1, int axis2) {
        return Mlx.task("swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    @MlxOp("mlx_swapaxes")
    public static LibraryTaskDescriptor swapaxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2, int axis1, int axis2) {
        return Mlx.task("swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    @MlxOp("mlx_swapaxes")
    public static LibraryTaskDescriptor swapaxes(IntArray x, IntArray out, int d0, int d1, int d2, int axis1, int axis2) {
        return Mlx.task("swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    @MlxOp("mlx_moveaxis")
    public static LibraryTaskDescriptor moveaxis(FloatArray x, FloatArray out, int d0, int d1, int d2, int source, int destination) {
        return Mlx.task("moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    @MlxOp("mlx_moveaxis")
    public static LibraryTaskDescriptor moveaxis(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2, int source, int destination) {
        return Mlx.task("moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    @MlxOp("mlx_moveaxis")
    public static LibraryTaskDescriptor moveaxis(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2, int source, int destination) {
        return Mlx.task("moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    @MlxOp("mlx_moveaxis")
    public static LibraryTaskDescriptor moveaxis(IntArray x, IntArray out, int d0, int d1, int d2, int source, int destination) {
        return Mlx.task("moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_to")
    public static LibraryTaskDescriptor broadcastTo(FloatArray x, FloatArray out, int rows, int cols) {
        return Mlx.task("broadcast_to", 1, x, out, rows, cols);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_to")
    public static LibraryTaskDescriptor broadcastTo(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return Mlx.task("broadcast_to", 1, x, out, rows, cols);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_to")
    public static LibraryTaskDescriptor broadcastTo(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return Mlx.task("broadcast_to", 1, x, out, rows, cols);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_to")
    public static LibraryTaskDescriptor broadcastTo(IntArray x, IntArray out, int rows, int cols) {
        return Mlx.task("broadcast_to", 1, x, out, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_arrays")
    public static LibraryTaskDescriptor broadcastArrays(FloatArray a, FloatArray b, FloatArray outA, FloatArray outB, int rows, int cols) {
        return Mlx.task("broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_arrays")
    public static LibraryTaskDescriptor broadcastArrays(HalfFloatArray a, HalfFloatArray b, HalfFloatArray outA, HalfFloatArray outB, int rows, int cols) {
        return Mlx.task("broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_arrays")
    public static LibraryTaskDescriptor broadcastArrays(BFloat16Array a, BFloat16Array b, BFloat16Array outA, BFloat16Array outB, int rows, int cols) {
        return Mlx.task("broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    @MlxOp("mlx_broadcast_arrays")
    public static LibraryTaskDescriptor broadcastArrays(IntArray a, IntArray b, IntArray outA, IntArray outB, int rows, int cols) {
        return Mlx.task("broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    @MlxOp("mlx_as_strided")
    public static LibraryTaskDescriptor asStrided(FloatArray x, FloatArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        return Mlx.task("as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    @MlxOp("mlx_as_strided")
    public static LibraryTaskDescriptor asStrided(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        return Mlx.task("as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    @MlxOp("mlx_as_strided")
    public static LibraryTaskDescriptor asStrided(BFloat16Array x, BFloat16Array out, int rows, int cols, int rowStride, int colStride, int offset) {
        return Mlx.task("as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    @MlxOp("mlx_as_strided")
    public static LibraryTaskDescriptor asStrided(IntArray x, IntArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        return Mlx.task("as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** A row-major copy of {@code x}. */
    @MlxOp("mlx_contiguous")
    public static LibraryTaskDescriptor contiguous(FloatArray x, FloatArray out) {
        return Mlx.task("contiguous", 1, x, out);
    }

    /** A row-major copy of {@code x}. */
    @MlxOp("mlx_contiguous")
    public static LibraryTaskDescriptor contiguous(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("contiguous", 1, x, out);
    }

    /** A row-major copy of {@code x}. */
    @MlxOp("mlx_contiguous")
    public static LibraryTaskDescriptor contiguous(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("contiguous", 1, x, out);
    }

    /** A row-major copy of {@code x}. */
    @MlxOp("mlx_contiguous")
    public static LibraryTaskDescriptor contiguous(IntArray x, IntArray out) {
        return Mlx.task("contiguous", 1, x, out);
    }

    /** A copy of {@code x}. */
    @MlxOp("mlx_copy")
    public static LibraryTaskDescriptor copy(FloatArray x, FloatArray out) {
        return Mlx.task("copy", 1, x, out);
    }

    /** A copy of {@code x}. */
    @MlxOp("mlx_copy")
    public static LibraryTaskDescriptor copy(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("copy", 1, x, out);
    }

    /** A copy of {@code x}. */
    @MlxOp("mlx_copy")
    public static LibraryTaskDescriptor copy(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("copy", 1, x, out);
    }

    /** A copy of {@code x}. */
    @MlxOp("mlx_copy")
    public static LibraryTaskDescriptor copy(IntArray x, IntArray out) {
        return Mlx.task("copy", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    @MlxOp("mlx_astype")
    public static LibraryTaskDescriptor astype(FloatArray x, HalfFloatArray out) {
        return Mlx.task("astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    @MlxOp("mlx_astype")
    public static LibraryTaskDescriptor astype(FloatArray x, BFloat16Array out) {
        return Mlx.task("astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    @MlxOp("mlx_astype")
    public static LibraryTaskDescriptor astype(FloatArray x, IntArray out) {
        return Mlx.task("astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    @MlxOp("mlx_astype")
    public static LibraryTaskDescriptor astype(HalfFloatArray x, FloatArray out) {
        return Mlx.task("astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    @MlxOp("mlx_astype")
    public static LibraryTaskDescriptor astype(IntArray x, FloatArray out) {
        return Mlx.task("astype", 1, x, out);
    }

    /** The bits of {@code x} reinterpreted as the (same-size) type of {@code out}. */
    @MlxOp("mlx_view")
    public static LibraryTaskDescriptor view(FloatArray x, IntArray out) {
        return Mlx.task("view", 1, x, out);
    }

    /** The bits of {@code x} reinterpreted as the (same-size) type of {@code out}. */
    @MlxOp("mlx_view")
    public static LibraryTaskDescriptor view(IntArray x, FloatArray out) {
        return Mlx.task("view", 1, x, out);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    @MlxOp("mlx_number_of_elements")
    public static LibraryTaskDescriptor numberOfElements(FloatArray x, IntArray out, int d0, int d1, int d2) {
        return Mlx.task("number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    @MlxOp("mlx_number_of_elements")
    public static LibraryTaskDescriptor numberOfElements(HalfFloatArray x, IntArray out, int d0, int d1, int d2) {
        return Mlx.task("number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    @MlxOp("mlx_number_of_elements")
    public static LibraryTaskDescriptor numberOfElements(BFloat16Array x, IntArray out, int d0, int d1, int d2) {
        return Mlx.task("number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    @MlxOp("mlx_number_of_elements")
    public static LibraryTaskDescriptor numberOfElements(IntArray x, IntArray out, int d0, int d1, int d2) {
        return Mlx.task("number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    @MlxOp("mlx_concatenate")
    public static LibraryTaskDescriptor concatenate(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("concatenate", 2, a, b, out);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    @MlxOp("mlx_concatenate")
    public static LibraryTaskDescriptor concatenate(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("concatenate", 2, a, b, out);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    @MlxOp("mlx_concatenate")
    public static LibraryTaskDescriptor concatenate(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("concatenate", 2, a, b, out);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    @MlxOp("mlx_concatenate")
    public static LibraryTaskDescriptor concatenate(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("concatenate", 2, a, b, out);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    @MlxOp("mlx_concatenate_axis")
    public static LibraryTaskDescriptor concatenateAxis(FloatArray a, FloatArray b, FloatArray out, int rows, int colsA, int colsB) {
        return Mlx.task("concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    @MlxOp("mlx_concatenate_axis")
    public static LibraryTaskDescriptor concatenateAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int rows, int colsA, int colsB) {
        return Mlx.task("concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    @MlxOp("mlx_concatenate_axis")
    public static LibraryTaskDescriptor concatenateAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out, int rows, int colsA, int colsB) {
        return Mlx.task("concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    @MlxOp("mlx_concatenate_axis")
    public static LibraryTaskDescriptor concatenateAxis(IntArray a, IntArray b, IntArray out, int rows, int colsA, int colsB) {
        return Mlx.task("concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    @MlxOp("mlx_stack")
    public static LibraryTaskDescriptor stack(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    @MlxOp("mlx_stack")
    public static LibraryTaskDescriptor stack(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    @MlxOp("mlx_stack")
    public static LibraryTaskDescriptor stack(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    @MlxOp("mlx_stack")
    public static LibraryTaskDescriptor stack(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    @MlxOp("mlx_stack_axis")
    public static LibraryTaskDescriptor stackAxis(FloatArray a, FloatArray b, FloatArray out) {
        return Mlx.task("stack_axis", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    @MlxOp("mlx_stack_axis")
    public static LibraryTaskDescriptor stackAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return Mlx.task("stack_axis", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    @MlxOp("mlx_stack_axis")
    public static LibraryTaskDescriptor stackAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return Mlx.task("stack_axis", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    @MlxOp("mlx_stack_axis")
    public static LibraryTaskDescriptor stackAxis(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("stack_axis", 2, a, b, out);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    @MlxOp("mlx_split")
    public static LibraryTaskDescriptor split(FloatArray x, FloatArray first, FloatArray second, int rows, int cols) {
        return Mlx.task("split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    @MlxOp("mlx_split")
    public static LibraryTaskDescriptor split(HalfFloatArray x, HalfFloatArray first, HalfFloatArray second, int rows, int cols) {
        return Mlx.task("split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    @MlxOp("mlx_split")
    public static LibraryTaskDescriptor split(BFloat16Array x, BFloat16Array first, BFloat16Array second, int rows, int cols) {
        return Mlx.task("split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    @MlxOp("mlx_split")
    public static LibraryTaskDescriptor split(IntArray x, IntArray first, IntArray second, int rows, int cols) {
        return Mlx.task("split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    @MlxOp("mlx_split_sections")
    public static LibraryTaskDescriptor splitSections(FloatArray x, FloatArray first, FloatArray second, int rows, int cols, int index) {
        return Mlx.task("split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    @MlxOp("mlx_split_sections")
    public static LibraryTaskDescriptor splitSections(HalfFloatArray x, HalfFloatArray first, HalfFloatArray second, int rows, int cols, int index) {
        return Mlx.task("split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    @MlxOp("mlx_split_sections")
    public static LibraryTaskDescriptor splitSections(BFloat16Array x, BFloat16Array first, BFloat16Array second, int rows, int cols, int index) {
        return Mlx.task("split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    @MlxOp("mlx_split_sections")
    public static LibraryTaskDescriptor splitSections(IntArray x, IntArray first, IntArray second, int rows, int cols, int index) {
        return Mlx.task("split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    @MlxOp("mlx_repeat")
    public static LibraryTaskDescriptor repeat(FloatArray x, FloatArray out, int times) {
        return Mlx.task("repeat", 1, x, out, times);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    @MlxOp("mlx_repeat")
    public static LibraryTaskDescriptor repeat(HalfFloatArray x, HalfFloatArray out, int times) {
        return Mlx.task("repeat", 1, x, out, times);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    @MlxOp("mlx_repeat")
    public static LibraryTaskDescriptor repeat(BFloat16Array x, BFloat16Array out, int times) {
        return Mlx.task("repeat", 1, x, out, times);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    @MlxOp("mlx_repeat")
    public static LibraryTaskDescriptor repeat(IntArray x, IntArray out, int times) {
        return Mlx.task("repeat", 1, x, out, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    @MlxOp("mlx_repeat_axis")
    public static LibraryTaskDescriptor repeatAxis(FloatArray x, FloatArray out, int rows, int cols, int times) {
        return Mlx.task("repeat_axis", 1, x, out, rows, cols, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    @MlxOp("mlx_repeat_axis")
    public static LibraryTaskDescriptor repeatAxis(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int times) {
        return Mlx.task("repeat_axis", 1, x, out, rows, cols, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    @MlxOp("mlx_repeat_axis")
    public static LibraryTaskDescriptor repeatAxis(BFloat16Array x, BFloat16Array out, int rows, int cols, int times) {
        return Mlx.task("repeat_axis", 1, x, out, rows, cols, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    @MlxOp("mlx_repeat_axis")
    public static LibraryTaskDescriptor repeatAxis(IntArray x, IntArray out, int rows, int cols, int times) {
        return Mlx.task("repeat_axis", 1, x, out, rows, cols, times);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    @MlxOp("mlx_tile")
    public static LibraryTaskDescriptor tile(FloatArray x, FloatArray out, int rows, int cols, int repsRows, int repsCols) {
        return Mlx.task("tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    @MlxOp("mlx_tile")
    public static LibraryTaskDescriptor tile(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int repsRows, int repsCols) {
        return Mlx.task("tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    @MlxOp("mlx_tile")
    public static LibraryTaskDescriptor tile(BFloat16Array x, BFloat16Array out, int rows, int cols, int repsRows, int repsCols) {
        return Mlx.task("tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    @MlxOp("mlx_tile")
    public static LibraryTaskDescriptor tile(IntArray x, IntArray out, int rows, int cols, int repsRows, int repsCols) {
        return Mlx.task("tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    @MlxOp("mlx_roll")
    public static LibraryTaskDescriptor roll(FloatArray x, FloatArray out, int shift) {
        return Mlx.task("roll", 1, x, out, shift);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    @MlxOp("mlx_roll")
    public static LibraryTaskDescriptor roll(HalfFloatArray x, HalfFloatArray out, int shift) {
        return Mlx.task("roll", 1, x, out, shift);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    @MlxOp("mlx_roll")
    public static LibraryTaskDescriptor roll(BFloat16Array x, BFloat16Array out, int shift) {
        return Mlx.task("roll", 1, x, out, shift);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    @MlxOp("mlx_roll")
    public static LibraryTaskDescriptor roll(IntArray x, IntArray out, int shift) {
        return Mlx.task("roll", 1, x, out, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    @MlxOp("mlx_roll_axis")
    public static LibraryTaskDescriptor rollAxis(FloatArray x, FloatArray out, int rows, int cols, int shift) {
        return Mlx.task("roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    @MlxOp("mlx_roll_axis")
    public static LibraryTaskDescriptor rollAxis(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int shift) {
        return Mlx.task("roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    @MlxOp("mlx_roll_axis")
    public static LibraryTaskDescriptor rollAxis(BFloat16Array x, BFloat16Array out, int rows, int cols, int shift) {
        return Mlx.task("roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    @MlxOp("mlx_roll_axis")
    public static LibraryTaskDescriptor rollAxis(IntArray x, IntArray out, int rows, int cols, int shift) {
        return Mlx.task("roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    @MlxOp("mlx_roll_axes")
    public static LibraryTaskDescriptor rollAxes(FloatArray x, FloatArray out, int rows, int cols, int shiftRows, int shiftCols) {
        return Mlx.task("roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    @MlxOp("mlx_roll_axes")
    public static LibraryTaskDescriptor rollAxes(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int shiftRows, int shiftCols) {
        return Mlx.task("roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    @MlxOp("mlx_roll_axes")
    public static LibraryTaskDescriptor rollAxes(BFloat16Array x, BFloat16Array out, int rows, int cols, int shiftRows, int shiftCols) {
        return Mlx.task("roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    @MlxOp("mlx_roll_axes")
    public static LibraryTaskDescriptor rollAxes(IntArray x, IntArray out, int rows, int cols, int shiftRows, int shiftCols) {
        return Mlx.task("roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    @MlxOp("mlx_pad")
    public static LibraryTaskDescriptor pad(FloatArray x, FloatArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return Mlx.task("pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    @MlxOp("mlx_pad")
    public static LibraryTaskDescriptor pad(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return Mlx.task("pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    @MlxOp("mlx_pad")
    public static LibraryTaskDescriptor pad(BFloat16Array x, BFloat16Array out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return Mlx.task("pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    @MlxOp("mlx_pad")
    public static LibraryTaskDescriptor pad(IntArray x, IntArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return Mlx.task("pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    @MlxOp("mlx_pad_symmetric")
    public static LibraryTaskDescriptor padSymmetric(FloatArray x, FloatArray out, int rows, int cols, int width, float value) {
        return Mlx.task("pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    @MlxOp("mlx_pad_symmetric")
    public static LibraryTaskDescriptor padSymmetric(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int width, float value) {
        return Mlx.task("pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    @MlxOp("mlx_pad_symmetric")
    public static LibraryTaskDescriptor padSymmetric(BFloat16Array x, BFloat16Array out, int rows, int cols, int width, float value) {
        return Mlx.task("pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    @MlxOp("mlx_pad_symmetric")
    public static LibraryTaskDescriptor padSymmetric(IntArray x, IntArray out, int rows, int cols, int width, float value) {
        return Mlx.task("pad_symmetric", 1, x, out, rows, cols, width, value);
    }
}
