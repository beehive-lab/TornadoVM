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
 * MLX's fused neural-network kernels ({@code mlx.fast}) as TornadoVM library tasks: RMS and layer
 * normalization, rotary position embeddings and scaled dot-product attention.
 */
public final class MlxNeuralNetwork {

    private MlxNeuralNetwork() {
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor rmsNorm(FloatArray x, FloatArray weight, FloatArray out, int rows, int dim, float eps) {
        return Mlx.task("mlx_fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor rmsNorm(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int rows, int dim, float eps) {
        return Mlx.task("mlx_fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor rmsNorm(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int rows, int dim, float eps) {
        return Mlx.task("mlx_fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor layerNorm(FloatArray x, FloatArray weight, FloatArray bias, FloatArray out, int rows, int dim, float eps) {
        return Mlx.task("mlx_fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor layerNorm(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray bias, HalfFloatArray out, int rows, int dim, float eps) {
        return Mlx.task("mlx_fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor layerNorm(BFloat16Array x, BFloat16Array weight, BFloat16Array bias, BFloat16Array out, int rows, int dim, float eps) {
        return Mlx.task("mlx_fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    public static LibraryTaskDescriptor rope(FloatArray x, FloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base, float scale,
            int offset) {
        return Mlx.task("mlx_fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    public static LibraryTaskDescriptor rope(HalfFloatArray x, HalfFloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base,
            float scale, int offset) {
        return Mlx.task("mlx_fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    public static LibraryTaskDescriptor rope(BFloat16Array x, BFloat16Array out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base,
            float scale, int offset) {
        return Mlx.task("mlx_fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    public static LibraryTaskDescriptor ropeDynamic(FloatArray x, IntArray offset, FloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional,
            float base, float scale) {
        return Mlx.task("mlx_fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    public static LibraryTaskDescriptor ropeDynamic(HalfFloatArray x, IntArray offset, HalfFloatArray out, int batch, int heads, int seqLen, int headDim, int dims,
            boolean traditional, float base, float scale) {
        return Mlx.task("mlx_fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    public static LibraryTaskDescriptor ropeDynamic(BFloat16Array x, IntArray offset, BFloat16Array out, int batch, int heads, int seqLen, int headDim, int dims,
            boolean traditional, float base, float scale) {
        return Mlx.task("mlx_fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    public static LibraryTaskDescriptor scaledDotProductAttention(FloatArray q, FloatArray k, FloatArray v, FloatArray out, int batch, int qHeads, int kvHeads, int qLen,
            int kvLen, int headDim, float scale, boolean causal) {
        return Mlx.task("mlx_fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    public static LibraryTaskDescriptor scaledDotProductAttention(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, HalfFloatArray out, int batch, int qHeads, int kvHeads,
            int qLen, int kvLen, int headDim, float scale, boolean causal) {
        return Mlx.task("mlx_fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    public static LibraryTaskDescriptor scaledDotProductAttention(BFloat16Array q, BFloat16Array k, BFloat16Array v, BFloat16Array out, int batch, int qHeads, int kvHeads,
            int qLen, int kvLen, int headDim, float scale, boolean causal) {
        return Mlx.task("mlx_fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }
}
