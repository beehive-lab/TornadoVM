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
 * MLX sorting, partitioning and top-k as TornadoVM library tasks, over a whole array or along
 * the middle axis of an input viewed as {@code [outer, len, inner]}. The arg- forms write int32
 * indices.
 */
public final class MlxSorting {

    private MlxSorting() {
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    public static LibraryTaskDescriptor topk(FloatArray x, FloatArray out, int k) {
        return Mlx.task("mlx_topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    public static LibraryTaskDescriptor topk(HalfFloatArray x, HalfFloatArray out, int k) {
        return Mlx.task("mlx_topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    public static LibraryTaskDescriptor topk(BFloat16Array x, BFloat16Array out, int k) {
        return Mlx.task("mlx_topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    public static LibraryTaskDescriptor topkRows(FloatArray x, FloatArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_topk_axis", 1, x, out, rows, cols, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    public static LibraryTaskDescriptor topkRows(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int k) {
        return Mlx.task("mlx_topk_axis", 1, x, out, rows, cols, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    public static LibraryTaskDescriptor topkRows(BFloat16Array x, BFloat16Array out, int rows, int cols, int k) {
        return Mlx.task("mlx_topk_axis", 1, x, out, rows, cols, k);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(FloatArray x, IntArray out) {
        return Mlx.task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(FloatArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(FloatArray x, FloatArray out, int kth) {
        return Mlx.task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(FloatArray x, IntArray out, int kth) {
        return Mlx.task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(FloatArray x, IntArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(HalfFloatArray x, IntArray out) {
        return Mlx.task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(HalfFloatArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(HalfFloatArray x, HalfFloatArray out, int kth) {
        return Mlx.task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(HalfFloatArray x, IntArray out, int kth) {
        return Mlx.task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(HalfFloatArray x, IntArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(BFloat16Array x, IntArray out) {
        return Mlx.task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(BFloat16Array x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(BFloat16Array x, BFloat16Array out, int kth) {
        return Mlx.task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(BFloat16Array x, IntArray out, int kth) {
        return Mlx.task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(BFloat16Array x, IntArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(IntArray x, IntArray out) {
        return Mlx.task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(IntArray x, IntArray out) {
        return Mlx.task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(IntArray x, IntArray out, int kth) {
        return Mlx.task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(IntArray x, IntArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(IntArray x, IntArray out, int kth) {
        return Mlx.task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(IntArray x, IntArray out, int outer, int len, int inner, int kth) {
        return Mlx.task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }
}
