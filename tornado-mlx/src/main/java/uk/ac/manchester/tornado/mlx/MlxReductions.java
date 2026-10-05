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
 * MLX reductions as TornadoVM library tasks. Each comes in three forms: over the whole
 * array ({@code sum}), over one axis ({@code sumAxis}, with the input viewed as
 * {@code [outer, len, inner]} and the middle axis reduced), and over two adjacent axes
 * ({@code sumAxes}, input viewed as {@code [outer, len1, len2, inner]}). The axis forms of the
 * reductions need {@code inner == 1} (the reduced axes trailing); argmin also takes
 * {@code inner > 1}. Outputs hold {@code outer * inner} elements (one for a whole-array reduction).
 * all and any write 0 or 1 into a byte array; argmin and argmax write int32 indices. Softmax keeps
 * the input's shape.
 */
public final class MlxReductions {

    private MlxReductions() {
    }

    /** Softmax over the whole array. */
    public static LibraryTaskDescriptor softmax(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_softmax", 1, x, out);
    }

    /** Softmax over the whole array. */
    public static LibraryTaskDescriptor softmax(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_softmax", 1, x, out);
    }

    /** Softmax over the whole array. */
    public static LibraryTaskDescriptor softmax(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_softmax", 1, x, out);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor softmaxRows(FloatArray x, FloatArray out, int rows, int cols) {
        return Mlx.task("mlx_softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor softmaxRows(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return Mlx.task("mlx_softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor softmaxRows(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return Mlx.task("mlx_softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    public static LibraryTaskDescriptor softmaxLastTwoAxes(FloatArray x, FloatArray out, int d0, int d1, int d2) {
        return Mlx.task("mlx_softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    public static LibraryTaskDescriptor softmaxLastTwoAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2) {
        return Mlx.task("mlx_softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    public static LibraryTaskDescriptor softmaxLastTwoAxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2) {
        return Mlx.task("mlx_softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    public static LibraryTaskDescriptor argmax(FloatArray x, IntArray out) {
        return Mlx.task("mlx_argmax", 1, x, out);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    public static LibraryTaskDescriptor argmax(HalfFloatArray x, IntArray out) {
        return Mlx.task("mlx_argmax", 1, x, out);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    public static LibraryTaskDescriptor argmax(BFloat16Array x, IntArray out) {
        return Mlx.task("mlx_argmax", 1, x, out);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor argmaxRows(FloatArray x, IntArray out, int rows, int cols) {
        return Mlx.task("mlx_argmax_axis", 1, x, out, rows, cols);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor argmaxRows(HalfFloatArray x, IntArray out, int rows, int cols) {
        return Mlx.task("mlx_argmax_axis", 1, x, out, rows, cols);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor argmaxRows(BFloat16Array x, IntArray out, int rows, int cols) {
        return Mlx.task("mlx_argmax_axis", 1, x, out, rows, cols);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(IntArray x, IntArray out) {
        return Mlx.task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(IntArray x, IntArray out) {
        return Mlx.task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(IntArray x, IntArray out) {
        return Mlx.task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(IntArray x, IntArray out) {
        return Mlx.task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the mean of all of {@code x}. */
    public static LibraryTaskDescriptor mean(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_mean", 1, x, out);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor meanAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_mean_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor meanAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_mean_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the mean of all of {@code x}. */
    public static LibraryTaskDescriptor mean(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_mean", 1, x, out);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor meanAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_mean_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor meanAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_mean_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the mean of all of {@code x}. */
    public static LibraryTaskDescriptor mean(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_mean", 1, x, out);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor meanAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_mean_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor meanAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_mean_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = log(sum(exp(x))) of all of {@code x}. */
    public static LibraryTaskDescriptor logsumexp(FloatArray x, FloatArray out) {
        return Mlx.task("mlx_logsumexp", 1, x, out);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_logsumexp_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_logsumexp_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = log(sum(exp(x))) of all of {@code x}. */
    public static LibraryTaskDescriptor logsumexp(HalfFloatArray x, HalfFloatArray out) {
        return Mlx.task("mlx_logsumexp", 1, x, out);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_logsumexp_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_logsumexp_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = log(sum(exp(x))) of all of {@code x}. */
    public static LibraryTaskDescriptor logsumexp(BFloat16Array x, BFloat16Array out) {
        return Mlx.task("mlx_logsumexp", 1, x, out);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return Mlx.task("mlx_logsumexp_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_logsumexp_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the variance (divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor var(FloatArray x, FloatArray out, int ddof) {
        return Mlx.task("mlx_var", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor varAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof) {
        return Mlx.task("mlx_var_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor varAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return Mlx.task("mlx_var_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the variance (divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor var(HalfFloatArray x, HalfFloatArray out, int ddof) {
        return Mlx.task("mlx_var", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor varAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, int ddof) {
        return Mlx.task("mlx_var_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor varAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return Mlx.task("mlx_var_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the variance (divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor var(BFloat16Array x, BFloat16Array out, int ddof) {
        return Mlx.task("mlx_var", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor varAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, int ddof) {
        return Mlx.task("mlx_var_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor varAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner, int ddof) {
        return Mlx.task("mlx_var_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the standard deviation (from the variance divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor std(FloatArray x, FloatArray out, int ddof) {
        return Mlx.task("mlx_std", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor stdAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof) {
        return Mlx.task("mlx_std_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor stdAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return Mlx.task("mlx_std_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the standard deviation (from the variance divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor std(HalfFloatArray x, HalfFloatArray out, int ddof) {
        return Mlx.task("mlx_std", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor stdAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, int ddof) {
        return Mlx.task("mlx_std_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor stdAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return Mlx.task("mlx_std_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the standard deviation (from the variance divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor std(BFloat16Array x, BFloat16Array out, int ddof) {
        return Mlx.task("mlx_std", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor stdAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, int ddof) {
        return Mlx.task("mlx_std_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor stdAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner, int ddof) {
        return Mlx.task("mlx_std_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(FloatArray x, ByteArray out) {
        return Mlx.task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(FloatArray x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(FloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(HalfFloatArray x, ByteArray out) {
        return Mlx.task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(HalfFloatArray x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(HalfFloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(BFloat16Array x, ByteArray out) {
        return Mlx.task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(BFloat16Array x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(BFloat16Array x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(IntArray x, ByteArray out) {
        return Mlx.task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(IntArray x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(IntArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(FloatArray x, ByteArray out) {
        return Mlx.task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(FloatArray x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(FloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(HalfFloatArray x, ByteArray out) {
        return Mlx.task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(HalfFloatArray x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(HalfFloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(BFloat16Array x, ByteArray out) {
        return Mlx.task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(BFloat16Array x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(BFloat16Array x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(IntArray x, ByteArray out) {
        return Mlx.task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(IntArray x, ByteArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(IntArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return Mlx.task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(FloatArray x, IntArray out) {
        return Mlx.task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(FloatArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(HalfFloatArray x, IntArray out) {
        return Mlx.task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(HalfFloatArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(BFloat16Array x, IntArray out) {
        return Mlx.task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(BFloat16Array x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(IntArray x, IntArray out) {
        return Mlx.task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return Mlx.task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }
}
