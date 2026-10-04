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
 * MLX indexing as TornadoVM library tasks: slicing, slice updates and the gathered batched
 * matmul. Indices are int32. Operations that modify an array write the modified copy to
 * {@code out} and leave {@code x} unchanged.
 */
public final class MlxIndex {

    private MlxIndex() {
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    @MlxOp("mlx_slice")
    public static LibraryTaskDescriptor slice(FloatArray x, FloatArray out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return Mlx.task("slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    @MlxOp("mlx_slice")
    public static LibraryTaskDescriptor slice(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return Mlx.task("slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    @MlxOp("mlx_slice")
    public static LibraryTaskDescriptor slice(BFloat16Array x, BFloat16Array out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return Mlx.task("slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    @MlxOp("mlx_slice")
    public static LibraryTaskDescriptor slice(IntArray x, IntArray out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return Mlx.task("slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update")
    public static LibraryTaskDescriptor sliceUpdate(FloatArray x, FloatArray update, FloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update")
    public static LibraryTaskDescriptor sliceUpdate(HalfFloatArray x, HalfFloatArray update, HalfFloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update")
    public static LibraryTaskDescriptor sliceUpdate(BFloat16Array x, BFloat16Array update, BFloat16Array out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update")
    public static LibraryTaskDescriptor sliceUpdate(IntArray x, IntArray update, IntArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_add")
    public static LibraryTaskDescriptor sliceUpdateAdd(FloatArray x, FloatArray update, FloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_add")
    public static LibraryTaskDescriptor sliceUpdateAdd(HalfFloatArray x, HalfFloatArray update, HalfFloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_add")
    public static LibraryTaskDescriptor sliceUpdateAdd(BFloat16Array x, BFloat16Array update, BFloat16Array out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_add")
    public static LibraryTaskDescriptor sliceUpdateAdd(IntArray x, IntArray update, IntArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_prod")
    public static LibraryTaskDescriptor sliceUpdateProd(FloatArray x, FloatArray update, FloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_prod")
    public static LibraryTaskDescriptor sliceUpdateProd(HalfFloatArray x, HalfFloatArray update, HalfFloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_prod")
    public static LibraryTaskDescriptor sliceUpdateProd(BFloat16Array x, BFloat16Array update, BFloat16Array out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    @MlxOp("mlx_slice_update_prod")
    public static LibraryTaskDescriptor sliceUpdateProd(IntArray x, IntArray update, IntArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return Mlx.task("slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    @MlxOp("mlx_gather_mm")
    public static LibraryTaskDescriptor gatherMm(FloatArray a, FloatArray b, IntArray lhs, IntArray rhs, FloatArray out, int batchesA, int batchesB, int m, int k, int n) {
        return Mlx.task("gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    @MlxOp("mlx_gather_mm")
    public static LibraryTaskDescriptor gatherMm(HalfFloatArray a, HalfFloatArray b, IntArray lhs, IntArray rhs, HalfFloatArray out, int batchesA, int batchesB, int m, int k, int n) {
        return Mlx.task("gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    @MlxOp("mlx_gather_mm")
    public static LibraryTaskDescriptor gatherMm(BFloat16Array a, BFloat16Array b, IntArray lhs, IntArray rhs, BFloat16Array out, int batchesA, int batchesB, int m, int k, int n) {
        return Mlx.task("gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }
}
