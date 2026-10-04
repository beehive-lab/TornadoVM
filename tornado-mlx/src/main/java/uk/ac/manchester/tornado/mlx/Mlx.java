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

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Factory methods for Apple MLX library tasks on the Metal backend. Each method builds a
 * {@link LibraryTaskDescriptor} consumed by {@code TaskGraph#libraryTask(String, ...)}:
 *
 * <pre>
 * taskGraph.libraryTask("add", Mlx::add, a, b, c);
 * </pre>
 *
 * <p>
 * Each task runs MLX's own Metal kernels in place on TornadoVM's buffers, with no MLX array and no
 * copy. A task whose arguments those kernels do not cover (a shape or layout outside what a
 * factory's documentation states) fails rather than falling back. Every factory is annotated with
 * the MLX operation it binds ({@link MlxOp}); the build checks each one has a test.
 * </p>
 */
public final class Mlx {

    public static final String LIBRARY_NAME = "apple/mlx";

    private Mlx() {
    }

    /** All arguments are READ_ONLY except the output at {@code outputIndex}, which is WRITE_ONLY. */
    private static Access[] readOnlyExcept(int numArgs, int outputIndex) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        accesses[outputIndex] = Access.WRITE_ONLY;
        return accesses;
    }

    /** A task of this library whose arguments are all read-only except {@code outputIndex}. */
    static LibraryTaskDescriptor task(String function, int outputIndex, Object... parameters) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(parameters) //
                .withAccess(readOnlyExcept(parameters.length, outputIndex));
    }

    /** A task of this library whose arguments are all read-only except those in {@code outputs}. */
    static LibraryTaskDescriptor task(String function, int[] outputs, Object... parameters) {
        Access[] accesses = readOnlyExcept(parameters.length, outputs[0]);
        for (int output : outputs) {
            accesses[output] = Access.WRITE_ONLY;
        }
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(parameters) //
                .withAccess(accesses);
    }

    // ---------------------------------------------------------------- element-wise binary: (a, b, c), same length

    /** Element-wise {@code c = a + b}. */
    @MlxOp("mlx_add")
    public static LibraryTaskDescriptor add(FloatArray a, FloatArray b, FloatArray c) {
        return task("add", 2, a, b, c);
    }

    /** Element-wise {@code c = a + b}. */
    @MlxOp("mlx_add")
    public static LibraryTaskDescriptor add(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("add", 2, a, b, c);
    }

    /** Element-wise {@code c = a + b}. */
    @MlxOp("mlx_add")
    public static LibraryTaskDescriptor add(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("add", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    @MlxOp("mlx_subtract")
    public static LibraryTaskDescriptor subtract(FloatArray a, FloatArray b, FloatArray c) {
        return task("subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    @MlxOp("mlx_subtract")
    public static LibraryTaskDescriptor subtract(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    @MlxOp("mlx_subtract")
    public static LibraryTaskDescriptor subtract(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    @MlxOp("mlx_multiply")
    public static LibraryTaskDescriptor multiply(FloatArray a, FloatArray b, FloatArray c) {
        return task("multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    @MlxOp("mlx_multiply")
    public static LibraryTaskDescriptor multiply(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    @MlxOp("mlx_multiply")
    public static LibraryTaskDescriptor multiply(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    @MlxOp("mlx_divide")
    public static LibraryTaskDescriptor divide(FloatArray a, FloatArray b, FloatArray c) {
        return task("divide", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    @MlxOp("mlx_divide")
    public static LibraryTaskDescriptor divide(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("divide", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    @MlxOp("mlx_divide")
    public static LibraryTaskDescriptor divide(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("divide", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    @MlxOp("mlx_maximum")
    public static LibraryTaskDescriptor maximum(FloatArray a, FloatArray b, FloatArray c) {
        return task("maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    @MlxOp("mlx_maximum")
    public static LibraryTaskDescriptor maximum(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    @MlxOp("mlx_maximum")
    public static LibraryTaskDescriptor maximum(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    @MlxOp("mlx_minimum")
    public static LibraryTaskDescriptor minimum(FloatArray a, FloatArray b, FloatArray c) {
        return task("minimum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    @MlxOp("mlx_minimum")
    public static LibraryTaskDescriptor minimum(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("minimum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    @MlxOp("mlx_minimum")
    public static LibraryTaskDescriptor minimum(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("minimum", 2, a, b, c);
    }

    // ---------------------------------------------------------------- element-wise unary: (a, out), same length

    /** Element-wise {@code out = -a}. */
    @MlxOp("mlx_negative")
    public static LibraryTaskDescriptor negative(FloatArray a, FloatArray out) {
        return task("negative", 1, a, out);
    }

    /** Element-wise {@code out = -a}. */
    @MlxOp("mlx_negative")
    public static LibraryTaskDescriptor negative(HalfFloatArray a, HalfFloatArray out) {
        return task("negative", 1, a, out);
    }

    /** Element-wise {@code out = -a}. */
    @MlxOp("mlx_negative")
    public static LibraryTaskDescriptor negative(BFloat16Array a, BFloat16Array out) {
        return task("negative", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    @MlxOp("mlx_exp")
    public static LibraryTaskDescriptor exp(FloatArray a, FloatArray out) {
        return task("exp", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    @MlxOp("mlx_exp")
    public static LibraryTaskDescriptor exp(HalfFloatArray a, HalfFloatArray out) {
        return task("exp", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    @MlxOp("mlx_exp")
    public static LibraryTaskDescriptor exp(BFloat16Array a, BFloat16Array out) {
        return task("exp", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    @MlxOp("mlx_tanh")
    public static LibraryTaskDescriptor tanh(FloatArray a, FloatArray out) {
        return task("tanh", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    @MlxOp("mlx_tanh")
    public static LibraryTaskDescriptor tanh(HalfFloatArray a, HalfFloatArray out) {
        return task("tanh", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    @MlxOp("mlx_tanh")
    public static LibraryTaskDescriptor tanh(BFloat16Array a, BFloat16Array out) {
        return task("tanh", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    @MlxOp("mlx_erf")
    public static LibraryTaskDescriptor erf(FloatArray a, FloatArray out) {
        return task("erf", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    @MlxOp("mlx_erf")
    public static LibraryTaskDescriptor erf(HalfFloatArray a, HalfFloatArray out) {
        return task("erf", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    @MlxOp("mlx_erf")
    public static LibraryTaskDescriptor erf(BFloat16Array a, BFloat16Array out) {
        return task("erf", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    @MlxOp("mlx_sigmoid")
    public static LibraryTaskDescriptor sigmoid(FloatArray a, FloatArray out) {
        return task("sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    @MlxOp("mlx_sigmoid")
    public static LibraryTaskDescriptor sigmoid(HalfFloatArray a, HalfFloatArray out) {
        return task("sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    @MlxOp("mlx_sigmoid")
    public static LibraryTaskDescriptor sigmoid(BFloat16Array a, BFloat16Array out) {
        return task("sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    @MlxOp("mlx_sqrt")
    public static LibraryTaskDescriptor sqrt(FloatArray a, FloatArray out) {
        return task("sqrt", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    @MlxOp("mlx_sqrt")
    public static LibraryTaskDescriptor sqrt(HalfFloatArray a, HalfFloatArray out) {
        return task("sqrt", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    @MlxOp("mlx_sqrt")
    public static LibraryTaskDescriptor sqrt(BFloat16Array a, BFloat16Array out) {
        return task("sqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    @MlxOp("mlx_rsqrt")
    public static LibraryTaskDescriptor rsqrt(FloatArray a, FloatArray out) {
        return task("rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    @MlxOp("mlx_rsqrt")
    public static LibraryTaskDescriptor rsqrt(HalfFloatArray a, HalfFloatArray out) {
        return task("rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    @MlxOp("mlx_rsqrt")
    public static LibraryTaskDescriptor rsqrt(BFloat16Array a, BFloat16Array out) {
        return task("rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    @MlxOp("mlx_square")
    public static LibraryTaskDescriptor square(FloatArray a, FloatArray out) {
        return task("square", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    @MlxOp("mlx_square")
    public static LibraryTaskDescriptor square(HalfFloatArray a, HalfFloatArray out) {
        return task("square", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    @MlxOp("mlx_square")
    public static LibraryTaskDescriptor square(BFloat16Array a, BFloat16Array out) {
        return task("square", 1, a, out);
    }

    // ---------------------------------------------------------------- linear algebra

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    @MlxOp("mlx_matmul")
    public static LibraryTaskDescriptor matmul(FloatArray a, FloatArray b, FloatArray c, int m, int k, int n) {
        return task("matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    @MlxOp("mlx_matmul")
    public static LibraryTaskDescriptor matmul(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c, int m, int k, int n) {
        return task("matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    @MlxOp("mlx_matmul")
    public static LibraryTaskDescriptor matmul(BFloat16Array a, BFloat16Array b, BFloat16Array c, int m, int k, int n) {
        return task("matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    @MlxOp({ "mlx_matmul", "mlx_transpose" })
    public static LibraryTaskDescriptor matmulTransposed(FloatArray a, FloatArray w, FloatArray c, int m, int k, int n) {
        return task("matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    @MlxOp({ "mlx_matmul", "mlx_transpose" })
    public static LibraryTaskDescriptor matmulTransposed(HalfFloatArray a, HalfFloatArray w, HalfFloatArray c, int m, int k, int n) {
        return task("matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    @MlxOp({ "mlx_matmul", "mlx_transpose" })
    public static LibraryTaskDescriptor matmulTransposed(BFloat16Array a, BFloat16Array w, BFloat16Array c, int m, int k, int n) {
        return task("matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    @MlxOp("mlx_addmm")
    public static LibraryTaskDescriptor addmm(FloatArray cIn, FloatArray a, FloatArray b, FloatArray out, int m, int k, int n, float alpha, float beta) {
        return task("addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    @MlxOp("mlx_addmm")
    public static LibraryTaskDescriptor addmm(HalfFloatArray cIn, HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k, int n, float alpha, float beta) {
        return task("addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    @MlxOp("mlx_addmm")
    public static LibraryTaskDescriptor addmm(BFloat16Array cIn, BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k, int n, float alpha, float beta) {
        return task("addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    // ---------------------------------------------------------------- affine group quantization
    //
    // MLX's affine format: weights w[rows, cols] are split along cols into groups of groupSize
    // (32, 64 or 128); each group has a scale and a bias, w = scale * q + bias, with q stored in
    // `bits` bits (2-8) packed into uint32 words, low bits first. Packed weights are an IntArray of
    // rows * cols * bits / 32 words; scales and biases have rows * cols / groupSize elements in the
    // activations' float type.

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    @MlxOp("mlx_quantized_matmul")
    public static LibraryTaskDescriptor quantizedMatmul(FloatArray x, IntArray wq, FloatArray scales, FloatArray biases, FloatArray y, int m, int k, int n, int groupSize,
            int bits) {
        return task("quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    @MlxOp("mlx_quantized_matmul")
    public static LibraryTaskDescriptor quantizedMatmul(HalfFloatArray x, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, HalfFloatArray y, int m, int k, int n,
            int groupSize, int bits) {
        return task("quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    @MlxOp("mlx_quantized_matmul")
    public static LibraryTaskDescriptor quantizedMatmul(BFloat16Array x, IntArray wq, BFloat16Array scales, BFloat16Array biases, BFloat16Array y, int m, int k, int n,
            int groupSize, int bits) {
        return task("quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    @MlxOp("mlx_gather_qmm")
    public static LibraryTaskDescriptor gatherQmm(FloatArray x, IntArray wq, FloatArray scales, FloatArray biases, IntArray lhsIndices, IntArray rhsIndices, FloatArray y,
            int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return task("gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    @MlxOp("mlx_gather_qmm")
    public static LibraryTaskDescriptor gatherQmm(HalfFloatArray x, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, IntArray lhsIndices, IntArray rhsIndices,
            HalfFloatArray y, int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return task("gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    @MlxOp("mlx_gather_qmm")
    public static LibraryTaskDescriptor gatherQmm(BFloat16Array x, IntArray wq, BFloat16Array scales, BFloat16Array biases, IntArray lhsIndices, IntArray rhsIndices,
            BFloat16Array y, int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return task("gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    @MlxOp("mlx_quantize")
    public static LibraryTaskDescriptor quantize(FloatArray w, IntArray wq, FloatArray scales, FloatArray biases, int rows, int cols, int groupSize, int bits) {
        Access[] accesses = readOnlyExcept(8, 1);
        accesses[2] = Access.WRITE_ONLY;
        accesses[3] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("quantize") //
                .withParameters(new Object[] { w, wq, scales, biases, rows, cols, groupSize, bits }) //
                .withAccess(accesses);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    @MlxOp("mlx_quantize")
    public static LibraryTaskDescriptor quantize(HalfFloatArray w, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, int rows, int cols, int groupSize, int bits) {
        Access[] accesses = readOnlyExcept(8, 1);
        accesses[2] = Access.WRITE_ONLY;
        accesses[3] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("quantize") //
                .withParameters(new Object[] { w, wq, scales, biases, rows, cols, groupSize, bits }) //
                .withAccess(accesses);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    @MlxOp("mlx_quantize")
    public static LibraryTaskDescriptor quantize(BFloat16Array w, IntArray wq, BFloat16Array scales, BFloat16Array biases, int rows, int cols, int groupSize, int bits) {
        Access[] accesses = readOnlyExcept(8, 1);
        accesses[2] = Access.WRITE_ONLY;
        accesses[3] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("quantize") //
                .withParameters(new Object[] { w, wq, scales, biases, rows, cols, groupSize, bits }) //
                .withAccess(accesses);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    @MlxOp("mlx_dequantize")
    public static LibraryTaskDescriptor dequantize(IntArray wq, FloatArray scales, FloatArray biases, FloatArray w, int rows, int cols, int groupSize, int bits) {
        return task("dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    @MlxOp("mlx_dequantize")
    public static LibraryTaskDescriptor dequantize(IntArray wq, HalfFloatArray scales, HalfFloatArray biases, HalfFloatArray w, int rows, int cols, int groupSize, int bits) {
        return task("dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    @MlxOp("mlx_dequantize")
    public static LibraryTaskDescriptor dequantize(IntArray wq, BFloat16Array scales, BFloat16Array biases, BFloat16Array w, int rows, int cols, int groupSize, int bits) {
        return task("dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    // ---------------------------------------------------------------- mlx.fast

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    @MlxOp("mlx_fast_rms_norm")
    public static LibraryTaskDescriptor rmsNorm(FloatArray x, FloatArray weight, FloatArray out, int rows, int dim, float eps) {
        return task("fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    @MlxOp("mlx_fast_rms_norm")
    public static LibraryTaskDescriptor rmsNorm(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int rows, int dim, float eps) {
        return task("fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    @MlxOp("mlx_fast_rms_norm")
    public static LibraryTaskDescriptor rmsNorm(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int rows, int dim, float eps) {
        return task("fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    @MlxOp("mlx_fast_layer_norm")
    public static LibraryTaskDescriptor layerNorm(FloatArray x, FloatArray weight, FloatArray bias, FloatArray out, int rows, int dim, float eps) {
        return task("fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    @MlxOp("mlx_fast_layer_norm")
    public static LibraryTaskDescriptor layerNorm(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray bias, HalfFloatArray out, int rows, int dim, float eps) {
        return task("fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    @MlxOp("mlx_fast_layer_norm")
    public static LibraryTaskDescriptor layerNorm(BFloat16Array x, BFloat16Array weight, BFloat16Array bias, BFloat16Array out, int rows, int dim, float eps) {
        return task("fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    @MlxOp("mlx_fast_rope")
    public static LibraryTaskDescriptor rope(FloatArray x, FloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base, float scale,
            int offset) {
        return task("fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    @MlxOp("mlx_fast_rope")
    public static LibraryTaskDescriptor rope(HalfFloatArray x, HalfFloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base,
            float scale, int offset) {
        return task("fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    @MlxOp("mlx_fast_rope")
    public static LibraryTaskDescriptor rope(BFloat16Array x, BFloat16Array out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base,
            float scale, int offset) {
        return task("fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    @MlxOp("mlx_fast_rope_dynamic")
    public static LibraryTaskDescriptor ropeDynamic(FloatArray x, IntArray offset, FloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional,
            float base, float scale) {
        return task("fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    @MlxOp("mlx_fast_rope_dynamic")
    public static LibraryTaskDescriptor ropeDynamic(HalfFloatArray x, IntArray offset, HalfFloatArray out, int batch, int heads, int seqLen, int headDim, int dims,
            boolean traditional, float base, float scale) {
        return task("fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    @MlxOp("mlx_fast_rope_dynamic")
    public static LibraryTaskDescriptor ropeDynamic(BFloat16Array x, IntArray offset, BFloat16Array out, int batch, int heads, int seqLen, int headDim, int dims,
            boolean traditional, float base, float scale) {
        return task("fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    @MlxOp("mlx_fast_scaled_dot_product_attention")
    public static LibraryTaskDescriptor scaledDotProductAttention(FloatArray q, FloatArray k, FloatArray v, FloatArray out, int batch, int qHeads, int kvHeads, int qLen,
            int kvLen, int headDim, float scale, boolean causal) {
        return task("fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    @MlxOp("mlx_fast_scaled_dot_product_attention")
    public static LibraryTaskDescriptor scaledDotProductAttention(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, HalfFloatArray out, int batch, int qHeads, int kvHeads,
            int qLen, int kvLen, int headDim, float scale, boolean causal) {
        return task("fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    @MlxOp("mlx_fast_scaled_dot_product_attention")
    public static LibraryTaskDescriptor scaledDotProductAttention(BFloat16Array q, BFloat16Array k, BFloat16Array v, BFloat16Array out, int batch, int qHeads, int kvHeads,
            int qLen, int kvLen, int headDim, float scale, boolean causal) {
        return task("fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    // ---------------------------------------------------------------- softmax, argmax, top-k

    /** Softmax over the whole array. */
    @MlxOp("mlx_softmax")
    public static LibraryTaskDescriptor softmax(FloatArray x, FloatArray out) {
        return task("softmax", 1, x, out);
    }

    /** Softmax over the whole array. */
    @MlxOp("mlx_softmax")
    public static LibraryTaskDescriptor softmax(HalfFloatArray x, HalfFloatArray out) {
        return task("softmax", 1, x, out);
    }

    /** Softmax over the whole array. */
    @MlxOp("mlx_softmax")
    public static LibraryTaskDescriptor softmax(BFloat16Array x, BFloat16Array out) {
        return task("softmax", 1, x, out);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    @MlxOp("mlx_softmax_axis")
    public static LibraryTaskDescriptor softmaxRows(FloatArray x, FloatArray out, int rows, int cols) {
        return task("softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    @MlxOp("mlx_softmax_axis")
    public static LibraryTaskDescriptor softmaxRows(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return task("softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    @MlxOp("mlx_softmax_axis")
    public static LibraryTaskDescriptor softmaxRows(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return task("softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    @MlxOp("mlx_softmax_axes")
    public static LibraryTaskDescriptor softmaxLastTwoAxes(FloatArray x, FloatArray out, int d0, int d1, int d2) {
        return task("softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    @MlxOp("mlx_softmax_axes")
    public static LibraryTaskDescriptor softmaxLastTwoAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2) {
        return task("softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    @MlxOp("mlx_softmax_axes")
    public static LibraryTaskDescriptor softmaxLastTwoAxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2) {
        return task("softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    @MlxOp("mlx_argmax")
    public static LibraryTaskDescriptor argmax(FloatArray x, IntArray out) {
        return task("argmax", 1, x, out);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    @MlxOp("mlx_argmax")
    public static LibraryTaskDescriptor argmax(HalfFloatArray x, IntArray out) {
        return task("argmax", 1, x, out);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    @MlxOp("mlx_argmax")
    public static LibraryTaskDescriptor argmax(BFloat16Array x, IntArray out) {
        return task("argmax", 1, x, out);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    @MlxOp("mlx_argmax_axis")
    public static LibraryTaskDescriptor argmaxRows(FloatArray x, IntArray out, int rows, int cols) {
        return task("argmax_axis", 1, x, out, rows, cols);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    @MlxOp("mlx_argmax_axis")
    public static LibraryTaskDescriptor argmaxRows(HalfFloatArray x, IntArray out, int rows, int cols) {
        return task("argmax_axis", 1, x, out, rows, cols);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    @MlxOp("mlx_argmax_axis")
    public static LibraryTaskDescriptor argmaxRows(BFloat16Array x, IntArray out, int rows, int cols) {
        return task("argmax_axis", 1, x, out, rows, cols);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    @MlxOp("mlx_topk")
    public static LibraryTaskDescriptor topk(FloatArray x, FloatArray out, int k) {
        return task("topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    @MlxOp("mlx_topk")
    public static LibraryTaskDescriptor topk(HalfFloatArray x, HalfFloatArray out, int k) {
        return task("topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    @MlxOp("mlx_topk")
    public static LibraryTaskDescriptor topk(BFloat16Array x, BFloat16Array out, int k) {
        return task("topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    @MlxOp("mlx_topk_axis")
    public static LibraryTaskDescriptor topkRows(FloatArray x, FloatArray out, int rows, int cols, int k) {
        return task("topk_axis", 1, x, out, rows, cols, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    @MlxOp("mlx_topk_axis")
    public static LibraryTaskDescriptor topkRows(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int k) {
        return task("topk_axis", 1, x, out, rows, cols, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    @MlxOp("mlx_topk_axis")
    public static LibraryTaskDescriptor topkRows(BFloat16Array x, BFloat16Array out, int rows, int cols, int k) {
        return task("topk_axis", 1, x, out, rows, cols, k);
    }
}
