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
 * MLX scans (cumsum, cumprod, cummax, cummin, logcumsumexp) as TornadoVM library tasks. A scan keeps
 * the input's shape and runs along the middle axis of the input viewed as {@code [outer, len, inner]}.
 */
public final class MlxScans {

    private MlxScans() {
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative log(sum(exp(x))) along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor logcumsumexp(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_logcumsumexp", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative log(sum(exp(x))) along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor logcumsumexp(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_logcumsumexp", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative log(sum(exp(x))) along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor logcumsumexp(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return Mlx.task("mlx_logcumsumexp", 1, x, out, outer, len, inner, reverse, inclusive);
    }
}
