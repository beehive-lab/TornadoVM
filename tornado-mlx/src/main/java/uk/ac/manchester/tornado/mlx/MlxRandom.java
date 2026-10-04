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
 * MLX random sampling (Tier 3) as TornadoVM library tasks. Each call draws from an MLX key made
 * from {@code seed}, so a given seed always gives the same values; the size of {@code out} sets
 * the number of samples.
 */
public final class MlxRandom {

    private MlxRandom() {
    }

    /** Random 32-bit words. */
    @MlxOp("mlx_random_bits")
    public static LibraryTaskDescriptor bits(IntArray out, int seed) {
        return Mlx.task("random_bits", 0, out, seed);
    }

    /** Values uniform in {@code [low, high)}. */
    @MlxOp("mlx_random_uniform")
    public static LibraryTaskDescriptor uniform(FloatArray out, float low, float high, int seed) {
        return Mlx.task("random_uniform", 0, out, low, high, seed);
    }

    /** Values uniform in {@code [low, high)}. */
    @MlxOp("mlx_random_uniform")
    public static LibraryTaskDescriptor uniform(HalfFloatArray out, float low, float high, int seed) {
        return Mlx.task("random_uniform", 0, out, low, high, seed);
    }

    /** Values uniform in {@code [low, high)}. */
    @MlxOp("mlx_random_uniform")
    public static LibraryTaskDescriptor uniform(BFloat16Array out, float low, float high, int seed) {
        return Mlx.task("random_uniform", 0, out, low, high, seed);
    }

    /** Normal values with mean {@code loc} and standard deviation {@code scale}. */
    @MlxOp("mlx_random_normal")
    public static LibraryTaskDescriptor normal(FloatArray out, float loc, float scale, int seed) {
        return Mlx.task("random_normal", 0, out, loc, scale, seed);
    }

    /** Normal values with mean {@code loc} and standard deviation {@code scale}. */
    @MlxOp("mlx_random_normal")
    public static LibraryTaskDescriptor normal(HalfFloatArray out, float loc, float scale, int seed) {
        return Mlx.task("random_normal", 0, out, loc, scale, seed);
    }

    /** Normal values with mean {@code loc} and standard deviation {@code scale}. */
    @MlxOp("mlx_random_normal")
    public static LibraryTaskDescriptor normal(BFloat16Array out, float loc, float scale, int seed) {
        return Mlx.task("random_normal", 0, out, loc, scale, seed);
    }

    /** Normal values with a mean and standard deviation per element. */
    @MlxOp("mlx_random_normal_broadcast")
    public static LibraryTaskDescriptor normalBroadcast(FloatArray loc, FloatArray scale, FloatArray out, int seed) {
        return Mlx.task("random_normal_broadcast", 2, loc, scale, out, seed);
    }

    /** Normal values with a mean and standard deviation per element. */
    @MlxOp("mlx_random_normal_broadcast")
    public static LibraryTaskDescriptor normalBroadcast(HalfFloatArray loc, HalfFloatArray scale, HalfFloatArray out, int seed) {
        return Mlx.task("random_normal_broadcast", 2, loc, scale, out, seed);
    }

    /** Normal values with a mean and standard deviation per element. */
    @MlxOp("mlx_random_normal_broadcast")
    public static LibraryTaskDescriptor normalBroadcast(BFloat16Array loc, BFloat16Array scale, BFloat16Array out, int seed) {
        return Mlx.task("random_normal_broadcast", 2, loc, scale, out, seed);
    }

    /** {@code out[i]} = 1 with probability {@code p[i]}, else 0. */
    @MlxOp("mlx_random_bernoulli")
    public static LibraryTaskDescriptor bernoulli(FloatArray p, ByteArray out, int seed) {
        return Mlx.task("random_bernoulli", 1, p, out, seed);
    }

    /** {@code out[i]} = 1 with probability {@code p[i]}, else 0. */
    @MlxOp("mlx_random_bernoulli")
    public static LibraryTaskDescriptor bernoulli(HalfFloatArray p, ByteArray out, int seed) {
        return Mlx.task("random_bernoulli", 1, p, out, seed);
    }

    /** {@code out[i]} = 1 with probability {@code p[i]}, else 0. */
    @MlxOp("mlx_random_bernoulli")
    public static LibraryTaskDescriptor bernoulli(BFloat16Array p, ByteArray out, int seed) {
        return Mlx.task("random_bernoulli", 1, p, out, seed);
    }

    /** Integers uniform in {@code [low, high)}. */
    @MlxOp("mlx_random_randint")
    public static LibraryTaskDescriptor randint(IntArray out, int low, int high, int seed) {
        return Mlx.task("random_randint", 0, out, low, high, seed);
    }

    /** Standard normal values restricted to {@code [lower, upper]}. */
    @MlxOp("mlx_random_truncated_normal")
    public static LibraryTaskDescriptor truncatedNormal(FloatArray out, float lower, float upper, int seed) {
        return Mlx.task("random_truncated_normal", 0, out, lower, upper, seed);
    }

    /** Standard normal values restricted to {@code [lower, upper]}. */
    @MlxOp("mlx_random_truncated_normal")
    public static LibraryTaskDescriptor truncatedNormal(HalfFloatArray out, float lower, float upper, int seed) {
        return Mlx.task("random_truncated_normal", 0, out, lower, upper, seed);
    }

    /** Standard normal values restricted to {@code [lower, upper]}. */
    @MlxOp("mlx_random_truncated_normal")
    public static LibraryTaskDescriptor truncatedNormal(BFloat16Array out, float lower, float upper, int seed) {
        return Mlx.task("random_truncated_normal", 0, out, lower, upper, seed);
    }

    /** Values from the standard Gumbel distribution. */
    @MlxOp("mlx_random_gumbel")
    public static LibraryTaskDescriptor gumbel(FloatArray out, int seed) {
        return Mlx.task("random_gumbel", 0, out, seed);
    }

    /** Values from the standard Gumbel distribution. */
    @MlxOp("mlx_random_gumbel")
    public static LibraryTaskDescriptor gumbel(HalfFloatArray out, int seed) {
        return Mlx.task("random_gumbel", 0, out, seed);
    }

    /** Values from the standard Gumbel distribution. */
    @MlxOp("mlx_random_gumbel")
    public static LibraryTaskDescriptor gumbel(BFloat16Array out, int seed) {
        return Mlx.task("random_gumbel", 0, out, seed);
    }

    /** Values from the Laplace distribution with location {@code loc} and scale {@code scale}. */
    @MlxOp("mlx_random_laplace")
    public static LibraryTaskDescriptor laplace(FloatArray out, float loc, float scale, int seed) {
        return Mlx.task("random_laplace", 0, out, loc, scale, seed);
    }

    /** Values from the Laplace distribution with location {@code loc} and scale {@code scale}. */
    @MlxOp("mlx_random_laplace")
    public static LibraryTaskDescriptor laplace(HalfFloatArray out, float loc, float scale, int seed) {
        return Mlx.task("random_laplace", 0, out, loc, scale, seed);
    }

    /** Values from the Laplace distribution with location {@code loc} and scale {@code scale}. */
    @MlxOp("mlx_random_laplace")
    public static LibraryTaskDescriptor laplace(BFloat16Array out, float loc, float scale, int seed) {
        return Mlx.task("random_laplace", 0, out, loc, scale, seed);
    }

    /** One class index per row of {@code logits[rows, classes]}, drawn from the row's softmax. */
    @MlxOp("mlx_random_categorical")
    public static LibraryTaskDescriptor categorical(FloatArray logits, IntArray out, int rows, int classes, int seed) {
        return Mlx.task("random_categorical", 1, logits, out, rows, classes, seed);
    }

    /** One class index per row of {@code logits[rows, classes]}, drawn from the row's softmax. */
    @MlxOp("mlx_random_categorical")
    public static LibraryTaskDescriptor categorical(HalfFloatArray logits, IntArray out, int rows, int classes, int seed) {
        return Mlx.task("random_categorical", 1, logits, out, rows, classes, seed);
    }

    /** One class index per row of {@code logits[rows, classes]}, drawn from the row's softmax. */
    @MlxOp("mlx_random_categorical")
    public static LibraryTaskDescriptor categorical(BFloat16Array logits, IntArray out, int rows, int classes, int seed) {
        return Mlx.task("random_categorical", 1, logits, out, rows, classes, seed);
    }

    /** {@code samples} class indices per row of {@code logits[rows, classes]}: {@code out[rows, samples]}. */
    @MlxOp("mlx_random_categorical_num_samples")
    public static LibraryTaskDescriptor categoricalSamples(FloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return Mlx.task("random_categorical_num_samples", 1, logits, out, rows, classes, samples, seed);
    }

    /** {@code samples} class indices per row of {@code logits[rows, classes]}: {@code out[rows, samples]}. */
    @MlxOp("mlx_random_categorical_num_samples")
    public static LibraryTaskDescriptor categoricalSamples(HalfFloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return Mlx.task("random_categorical_num_samples", 1, logits, out, rows, classes, samples, seed);
    }

    /** {@code samples} class indices per row of {@code logits[rows, classes]}: {@code out[rows, samples]}. */
    @MlxOp("mlx_random_categorical_num_samples")
    public static LibraryTaskDescriptor categoricalSamples(BFloat16Array logits, IntArray out, int rows, int classes, int samples, int seed) {
        return Mlx.task("random_categorical_num_samples", 1, logits, out, rows, classes, samples, seed);
    }

    /** Class indices drawn for an output of shape {@code [samples, rows]} from each row of {@code logits[rows, classes]}. */
    @MlxOp("mlx_random_categorical_shape")
    public static LibraryTaskDescriptor categoricalShape(FloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return Mlx.task("random_categorical_shape", 1, logits, out, rows, classes, samples, seed);
    }

    /** Class indices drawn for an output of shape {@code [samples, rows]} from each row of {@code logits[rows, classes]}. */
    @MlxOp("mlx_random_categorical_shape")
    public static LibraryTaskDescriptor categoricalShape(HalfFloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return Mlx.task("random_categorical_shape", 1, logits, out, rows, classes, samples, seed);
    }

    /** Class indices drawn for an output of shape {@code [samples, rows]} from each row of {@code logits[rows, classes]}. */
    @MlxOp("mlx_random_categorical_shape")
    public static LibraryTaskDescriptor categoricalShape(BFloat16Array logits, IntArray out, int rows, int classes, int samples, int seed) {
        return Mlx.task("random_categorical_shape", 1, logits, out, rows, classes, samples, seed);
    }

    /** A random permutation of {@code 0 .. out.length - 1}. */
    @MlxOp("mlx_random_permutation_arange")
    public static LibraryTaskDescriptor permutationArange(IntArray out, int seed) {
        return Mlx.task("random_permutation_arange", 0, out, seed);
    }
}
