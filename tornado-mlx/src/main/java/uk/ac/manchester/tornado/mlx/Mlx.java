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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
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
 * factory's documentation states) fails rather than falling back. The function name of each task is
 * the mlx-c name of the operation it runs, e.g. {@code mlx_add}. The factories are grouped by
 * operation category, in the order of the coverage manifest.
 * </p>
 */
public final class Mlx {

    public static final String LIBRARY_NAME = "apple/mlx";

    /** Normalisation: the inverse transform is scaled by 1/n (the default). */
    public static final int FFT_BACKWARD = 0;

    /** Normalisation: both directions are scaled by 1/sqrt(n). */
    public static final int FFT_ORTHO = 1;

    /** Normalisation: the forward transform is scaled by 1/n. */
    public static final int FFT_FORWARD = 2;

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

    // ---------------------------------------------------------------- Arithmetic
    // MLX element-wise arithmetic and math: binary operators, unary functions (exponentials,
    // logarithms, roots, trigonometric and hyperbolic functions), rounding, integer-style division,
    // clipping, selection, NaN replacement and complex parts. All operands of one call have the same
    // length; complex arrays are float arrays of (real, imaginary) pairs.

    /** Element-wise {@code c = a + b}. */
    public static LibraryTaskDescriptor add(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_add", 2, a, b, c);
    }

    /** Element-wise {@code c = a + b}. */
    public static LibraryTaskDescriptor add(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_add", 2, a, b, c);
    }

    /** Element-wise {@code c = a + b}. */
    public static LibraryTaskDescriptor add(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_add", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    public static LibraryTaskDescriptor subtract(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    public static LibraryTaskDescriptor subtract(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    public static LibraryTaskDescriptor subtract(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    public static LibraryTaskDescriptor multiply(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    public static LibraryTaskDescriptor multiply(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    public static LibraryTaskDescriptor multiply(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    public static LibraryTaskDescriptor divide(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    public static LibraryTaskDescriptor divide(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    public static LibraryTaskDescriptor divide(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    public static LibraryTaskDescriptor maximum(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    public static LibraryTaskDescriptor maximum(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    public static LibraryTaskDescriptor maximum(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    public static LibraryTaskDescriptor minimum(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_minimum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    public static LibraryTaskDescriptor minimum(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_minimum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    public static LibraryTaskDescriptor minimum(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_minimum", 2, a, b, c);
    }

    /** Element-wise {@code out = -a}. */
    public static LibraryTaskDescriptor negative(FloatArray a, FloatArray out) {
        return task("mlx_negative", 1, a, out);
    }

    /** Element-wise {@code out = -a}. */
    public static LibraryTaskDescriptor negative(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_negative", 1, a, out);
    }

    /** Element-wise {@code out = -a}. */
    public static LibraryTaskDescriptor negative(BFloat16Array a, BFloat16Array out) {
        return task("mlx_negative", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    public static LibraryTaskDescriptor exp(FloatArray a, FloatArray out) {
        return task("mlx_exp", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    public static LibraryTaskDescriptor exp(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_exp", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    public static LibraryTaskDescriptor exp(BFloat16Array a, BFloat16Array out) {
        return task("mlx_exp", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    public static LibraryTaskDescriptor tanh(FloatArray a, FloatArray out) {
        return task("mlx_tanh", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    public static LibraryTaskDescriptor tanh(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_tanh", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    public static LibraryTaskDescriptor tanh(BFloat16Array a, BFloat16Array out) {
        return task("mlx_tanh", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    public static LibraryTaskDescriptor erf(FloatArray a, FloatArray out) {
        return task("mlx_erf", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    public static LibraryTaskDescriptor erf(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_erf", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    public static LibraryTaskDescriptor erf(BFloat16Array a, BFloat16Array out) {
        return task("mlx_erf", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    public static LibraryTaskDescriptor sigmoid(FloatArray a, FloatArray out) {
        return task("mlx_sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    public static LibraryTaskDescriptor sigmoid(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    public static LibraryTaskDescriptor sigmoid(BFloat16Array a, BFloat16Array out) {
        return task("mlx_sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    public static LibraryTaskDescriptor sqrt(FloatArray a, FloatArray out) {
        return task("mlx_sqrt", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    public static LibraryTaskDescriptor sqrt(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_sqrt", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    public static LibraryTaskDescriptor sqrt(BFloat16Array a, BFloat16Array out) {
        return task("mlx_sqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    public static LibraryTaskDescriptor rsqrt(FloatArray a, FloatArray out) {
        return task("mlx_rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    public static LibraryTaskDescriptor rsqrt(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    public static LibraryTaskDescriptor rsqrt(BFloat16Array a, BFloat16Array out) {
        return task("mlx_rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    public static LibraryTaskDescriptor square(FloatArray a, FloatArray out) {
        return task("mlx_square", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    public static LibraryTaskDescriptor square(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_square", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    public static LibraryTaskDescriptor square(BFloat16Array a, BFloat16Array out) {
        return task("mlx_square", 1, a, out);
    }

    /** {@code out} = {@code a} with NaN replaced by {@code nan}, +infinity by {@code posinf} and -infinity by {@code neginf}. */
    public static LibraryTaskDescriptor nanToNum(FloatArray a, FloatArray out, float nan, float posinf, float neginf) {
        return task("mlx_nan_to_num", 1, a, out, nan, posinf, neginf);
    }

    /** {@code out} = {@code a} with NaN replaced by {@code nan}, +infinity by {@code posinf} and -infinity by {@code neginf}. */
    public static LibraryTaskDescriptor nanToNum(HalfFloatArray a, HalfFloatArray out, float nan, float posinf, float neginf) {
        return task("mlx_nan_to_num", 1, a, out, nan, posinf, neginf);
    }

    /** {@code out} = {@code a} with NaN replaced by {@code nan}, +infinity by {@code posinf} and -infinity by {@code neginf}. */
    public static LibraryTaskDescriptor nanToNum(BFloat16Array a, BFloat16Array out, float nan, float posinf, float neginf) {
        return task("mlx_nan_to_num", 1, a, out, nan, posinf, neginf);
    }

    /** {@code out[i]} = the real part of complex {@code z[i]} ({@code z} holds (real, imaginary) pairs). */
    public static LibraryTaskDescriptor real(FloatArray z, FloatArray out) {
        return task("mlx_real", 1, z, out);
    }

    /** {@code out[i]} = the imaginary part of complex {@code z[i]}. */
    public static LibraryTaskDescriptor imag(FloatArray z, FloatArray out) {
        return task("mlx_imag", 1, z, out);
    }

    /** {@code out[i]} = the complex conjugate of {@code z[i]} (both as (real, imaginary) pairs). */
    public static LibraryTaskDescriptor conjugate(FloatArray z, FloatArray out) {
        return task("mlx_conjugate", 1, z, out);
    }

    /** Element-wise {@code out = |a|}. */
    public static LibraryTaskDescriptor abs(FloatArray a, FloatArray out) {
        return task("mlx_abs", 1, a, out);
    }

    /** Element-wise {@code out = |a|}. */
    public static LibraryTaskDescriptor abs(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_abs", 1, a, out);
    }

    /** Element-wise {@code out = |a|}. */
    public static LibraryTaskDescriptor abs(BFloat16Array a, BFloat16Array out) {
        return task("mlx_abs", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    public static LibraryTaskDescriptor arccos(FloatArray a, FloatArray out) {
        return task("mlx_arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    public static LibraryTaskDescriptor arccos(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    public static LibraryTaskDescriptor arccos(BFloat16Array a, BFloat16Array out) {
        return task("mlx_arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    public static LibraryTaskDescriptor arccosh(FloatArray a, FloatArray out) {
        return task("mlx_arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    public static LibraryTaskDescriptor arccosh(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    public static LibraryTaskDescriptor arccosh(BFloat16Array a, BFloat16Array out) {
        return task("mlx_arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    public static LibraryTaskDescriptor arcsin(FloatArray a, FloatArray out) {
        return task("mlx_arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    public static LibraryTaskDescriptor arcsin(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    public static LibraryTaskDescriptor arcsin(BFloat16Array a, BFloat16Array out) {
        return task("mlx_arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    public static LibraryTaskDescriptor arcsinh(FloatArray a, FloatArray out) {
        return task("mlx_arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    public static LibraryTaskDescriptor arcsinh(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    public static LibraryTaskDescriptor arcsinh(BFloat16Array a, BFloat16Array out) {
        return task("mlx_arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    public static LibraryTaskDescriptor arctan(FloatArray a, FloatArray out) {
        return task("mlx_arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    public static LibraryTaskDescriptor arctan(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    public static LibraryTaskDescriptor arctan(BFloat16Array a, BFloat16Array out) {
        return task("mlx_arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    public static LibraryTaskDescriptor arctanh(FloatArray a, FloatArray out) {
        return task("mlx_arctanh", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    public static LibraryTaskDescriptor arctanh(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_arctanh", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    public static LibraryTaskDescriptor arctanh(BFloat16Array a, BFloat16Array out) {
        return task("mlx_arctanh", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    public static LibraryTaskDescriptor ceil(FloatArray a, FloatArray out) {
        return task("mlx_ceil", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    public static LibraryTaskDescriptor ceil(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_ceil", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    public static LibraryTaskDescriptor ceil(BFloat16Array a, BFloat16Array out) {
        return task("mlx_ceil", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    public static LibraryTaskDescriptor cos(FloatArray a, FloatArray out) {
        return task("mlx_cos", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    public static LibraryTaskDescriptor cos(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_cos", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    public static LibraryTaskDescriptor cos(BFloat16Array a, BFloat16Array out) {
        return task("mlx_cos", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    public static LibraryTaskDescriptor cosh(FloatArray a, FloatArray out) {
        return task("mlx_cosh", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    public static LibraryTaskDescriptor cosh(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_cosh", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    public static LibraryTaskDescriptor cosh(BFloat16Array a, BFloat16Array out) {
        return task("mlx_cosh", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    public static LibraryTaskDescriptor degrees(FloatArray a, FloatArray out) {
        return task("mlx_degrees", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    public static LibraryTaskDescriptor degrees(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_degrees", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    public static LibraryTaskDescriptor degrees(BFloat16Array a, BFloat16Array out) {
        return task("mlx_degrees", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    public static LibraryTaskDescriptor erfinv(FloatArray a, FloatArray out) {
        return task("mlx_erfinv", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    public static LibraryTaskDescriptor erfinv(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_erfinv", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    public static LibraryTaskDescriptor erfinv(BFloat16Array a, BFloat16Array out) {
        return task("mlx_erfinv", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    public static LibraryTaskDescriptor expm1(FloatArray a, FloatArray out) {
        return task("mlx_expm1", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    public static LibraryTaskDescriptor expm1(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_expm1", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    public static LibraryTaskDescriptor expm1(BFloat16Array a, BFloat16Array out) {
        return task("mlx_expm1", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    public static LibraryTaskDescriptor floor(FloatArray a, FloatArray out) {
        return task("mlx_floor", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    public static LibraryTaskDescriptor floor(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_floor", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    public static LibraryTaskDescriptor floor(BFloat16Array a, BFloat16Array out) {
        return task("mlx_floor", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    public static LibraryTaskDescriptor log(FloatArray a, FloatArray out) {
        return task("mlx_log", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    public static LibraryTaskDescriptor log(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_log", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    public static LibraryTaskDescriptor log(BFloat16Array a, BFloat16Array out) {
        return task("mlx_log", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    public static LibraryTaskDescriptor log10(FloatArray a, FloatArray out) {
        return task("mlx_log10", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    public static LibraryTaskDescriptor log10(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_log10", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    public static LibraryTaskDescriptor log10(BFloat16Array a, BFloat16Array out) {
        return task("mlx_log10", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    public static LibraryTaskDescriptor log1p(FloatArray a, FloatArray out) {
        return task("mlx_log1p", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    public static LibraryTaskDescriptor log1p(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_log1p", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    public static LibraryTaskDescriptor log1p(BFloat16Array a, BFloat16Array out) {
        return task("mlx_log1p", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    public static LibraryTaskDescriptor log2(FloatArray a, FloatArray out) {
        return task("mlx_log2", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    public static LibraryTaskDescriptor log2(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_log2", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    public static LibraryTaskDescriptor log2(BFloat16Array a, BFloat16Array out) {
        return task("mlx_log2", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    public static LibraryTaskDescriptor radians(FloatArray a, FloatArray out) {
        return task("mlx_radians", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    public static LibraryTaskDescriptor radians(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_radians", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    public static LibraryTaskDescriptor radians(BFloat16Array a, BFloat16Array out) {
        return task("mlx_radians", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    public static LibraryTaskDescriptor reciprocal(FloatArray a, FloatArray out) {
        return task("mlx_reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    public static LibraryTaskDescriptor reciprocal(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    public static LibraryTaskDescriptor reciprocal(BFloat16Array a, BFloat16Array out) {
        return task("mlx_reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    public static LibraryTaskDescriptor sign(FloatArray a, FloatArray out) {
        return task("mlx_sign", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    public static LibraryTaskDescriptor sign(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_sign", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    public static LibraryTaskDescriptor sign(BFloat16Array a, BFloat16Array out) {
        return task("mlx_sign", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    public static LibraryTaskDescriptor sin(FloatArray a, FloatArray out) {
        return task("mlx_sin", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    public static LibraryTaskDescriptor sin(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_sin", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    public static LibraryTaskDescriptor sin(BFloat16Array a, BFloat16Array out) {
        return task("mlx_sin", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    public static LibraryTaskDescriptor sinh(FloatArray a, FloatArray out) {
        return task("mlx_sinh", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    public static LibraryTaskDescriptor sinh(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_sinh", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    public static LibraryTaskDescriptor sinh(BFloat16Array a, BFloat16Array out) {
        return task("mlx_sinh", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    public static LibraryTaskDescriptor tan(FloatArray a, FloatArray out) {
        return task("mlx_tan", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    public static LibraryTaskDescriptor tan(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_tan", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    public static LibraryTaskDescriptor tan(BFloat16Array a, BFloat16Array out) {
        return task("mlx_tan", 1, a, out);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    public static LibraryTaskDescriptor round(FloatArray a, FloatArray out, int decimals) {
        return task("mlx_round", 1, a, out, decimals);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    public static LibraryTaskDescriptor round(HalfFloatArray a, HalfFloatArray out, int decimals) {
        return task("mlx_round", 1, a, out, decimals);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    public static LibraryTaskDescriptor round(BFloat16Array a, BFloat16Array out, int decimals) {
        return task("mlx_round", 1, a, out, decimals);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    public static LibraryTaskDescriptor arctan2(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    public static LibraryTaskDescriptor arctan2(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    public static LibraryTaskDescriptor arctan2(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(IntArray a, IntArray b, IntArray c) {
        return task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    public static LibraryTaskDescriptor logaddexp(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    public static LibraryTaskDescriptor logaddexp(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    public static LibraryTaskDescriptor logaddexp(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    public static LibraryTaskDescriptor power(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_power", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    public static LibraryTaskDescriptor power(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_power", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    public static LibraryTaskDescriptor power(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_power", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    public static LibraryTaskDescriptor remainder(FloatArray a, FloatArray b, FloatArray c) {
        return task("mlx_remainder", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    public static LibraryTaskDescriptor remainder(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return task("mlx_remainder", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    public static LibraryTaskDescriptor remainder(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return task("mlx_remainder", 2, a, b, c);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(FloatArray a, FloatArray b, FloatArray quotient, FloatArray remainder) {
        return task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(HalfFloatArray a, HalfFloatArray b, HalfFloatArray quotient, HalfFloatArray remainder) {
        return task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(BFloat16Array a, BFloat16Array b, BFloat16Array quotient, BFloat16Array remainder) {
        return task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(IntArray a, IntArray b, IntArray quotient, IntArray remainder) {
        return task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(FloatArray a, FloatArray out, float lo, float hi) {
        return task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(HalfFloatArray a, HalfFloatArray out, float lo, float hi) {
        return task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(BFloat16Array a, BFloat16Array out, float lo, float hi) {
        return task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(IntArray a, IntArray out, int lo, int hi) {
        return task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, FloatArray x, FloatArray y, FloatArray out) {
        return task("mlx_where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, HalfFloatArray x, HalfFloatArray y, HalfFloatArray out) {
        return task("mlx_where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, BFloat16Array x, BFloat16Array y, BFloat16Array out) {
        return task("mlx_where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, IntArray x, IntArray y, IntArray out) {
        return task("mlx_where", 3, condition, x, y, out);
    }

    // ---------------------------------------------------------------- Logic
    // MLX comparisons, floating-point classification, and logical and bitwise operations. Boolean
    // results are written as 0 or 1 into byte arrays.

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(FloatArray a, FloatArray b, ByteArray out) {
        return task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(IntArray a, IntArray b, ByteArray out) {
        return task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(FloatArray a, FloatArray b, ByteArray out) {
        return task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(IntArray a, IntArray b, ByteArray out) {
        return task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(FloatArray a, FloatArray b, ByteArray out) {
        return task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(IntArray a, IntArray b, ByteArray out) {
        return task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(FloatArray a, FloatArray b, ByteArray out) {
        return task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(IntArray a, IntArray b, ByteArray out) {
        return task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(FloatArray a, FloatArray b, ByteArray out) {
        return task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(IntArray a, IntArray b, ByteArray out) {
        return task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(FloatArray a, FloatArray b, ByteArray out) {
        return task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(IntArray a, IntArray b, ByteArray out) {
        return task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = |a - b| <= atol + rtol * |b|} as 0 or 1; NaNs compare equal if {@code equalNan}. */
    public static LibraryTaskDescriptor isclose(FloatArray a, FloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return task("mlx_isclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if every element of {@code a} and {@code b} is close (as in {@link #isclose}), else 0. */
    public static LibraryTaskDescriptor allclose(FloatArray a, FloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return task("mlx_allclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[i] = |a - b| <= atol + rtol * |b|} as 0 or 1; NaNs compare equal if {@code equalNan}. */
    public static LibraryTaskDescriptor isclose(HalfFloatArray a, HalfFloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return task("mlx_isclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if every element of {@code a} and {@code b} is close (as in {@link #isclose}), else 0. */
    public static LibraryTaskDescriptor allclose(HalfFloatArray a, HalfFloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return task("mlx_allclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[i] = |a - b| <= atol + rtol * |b|} as 0 or 1; NaNs compare equal if {@code equalNan}. */
    public static LibraryTaskDescriptor isclose(BFloat16Array a, BFloat16Array b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return task("mlx_isclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if every element of {@code a} and {@code b} is close (as in {@link #isclose}), else 0. */
    public static LibraryTaskDescriptor allclose(BFloat16Array a, BFloat16Array b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return task("mlx_allclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(FloatArray a, FloatArray b, ByteArray out, boolean equalNan) {
        return task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out, boolean equalNan) {
        return task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(BFloat16Array a, BFloat16Array b, ByteArray out, boolean equalNan) {
        return task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(IntArray a, IntArray b, ByteArray out, boolean equalNan) {
        return task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is finite, else 0. */
    public static LibraryTaskDescriptor isfinite(FloatArray a, ByteArray out) {
        return task("mlx_isfinite", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is finite, else 0. */
    public static LibraryTaskDescriptor isfinite(HalfFloatArray a, ByteArray out) {
        return task("mlx_isfinite", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is finite, else 0. */
    public static LibraryTaskDescriptor isfinite(BFloat16Array a, ByteArray out) {
        return task("mlx_isfinite", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is infinite, else 0. */
    public static LibraryTaskDescriptor isinf(FloatArray a, ByteArray out) {
        return task("mlx_isinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is infinite, else 0. */
    public static LibraryTaskDescriptor isinf(HalfFloatArray a, ByteArray out) {
        return task("mlx_isinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is infinite, else 0. */
    public static LibraryTaskDescriptor isinf(BFloat16Array a, ByteArray out) {
        return task("mlx_isinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is NaN, else 0. */
    public static LibraryTaskDescriptor isnan(FloatArray a, ByteArray out) {
        return task("mlx_isnan", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is NaN, else 0. */
    public static LibraryTaskDescriptor isnan(HalfFloatArray a, ByteArray out) {
        return task("mlx_isnan", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is NaN, else 0. */
    public static LibraryTaskDescriptor isnan(BFloat16Array a, ByteArray out) {
        return task("mlx_isnan", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is -infinity, else 0. */
    public static LibraryTaskDescriptor isneginf(FloatArray a, ByteArray out) {
        return task("mlx_isneginf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is -infinity, else 0. */
    public static LibraryTaskDescriptor isneginf(HalfFloatArray a, ByteArray out) {
        return task("mlx_isneginf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is -infinity, else 0. */
    public static LibraryTaskDescriptor isneginf(BFloat16Array a, ByteArray out) {
        return task("mlx_isneginf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is +infinity, else 0. */
    public static LibraryTaskDescriptor isposinf(FloatArray a, ByteArray out) {
        return task("mlx_isposinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is +infinity, else 0. */
    public static LibraryTaskDescriptor isposinf(HalfFloatArray a, ByteArray out) {
        return task("mlx_isposinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is +infinity, else 0. */
    public static LibraryTaskDescriptor isposinf(BFloat16Array a, ByteArray out) {
        return task("mlx_isposinf", 1, a, out);
    }

    /** {@code out[i] = a[i] != 0 && b[i] != 0} as 0 or 1. */
    public static LibraryTaskDescriptor logicalAnd(ByteArray a, ByteArray b, ByteArray out) {
        return task("mlx_logical_and", 2, a, b, out);
    }

    /** {@code out[i] = a[i] != 0 || b[i] != 0} as 0 or 1. */
    public static LibraryTaskDescriptor logicalOr(ByteArray a, ByteArray b, ByteArray out) {
        return task("mlx_logical_or", 2, a, b, out);
    }

    /** {@code out[i] = a[i] == 0} as 0 or 1. */
    public static LibraryTaskDescriptor logicalNot(ByteArray a, ByteArray out) {
        return task("mlx_logical_not", 1, a, out);
    }

    /** {@code out[i] = a & b}. */
    public static LibraryTaskDescriptor bitwiseAnd(IntArray a, IntArray b, IntArray out) {
        return task("mlx_bitwise_and", 2, a, b, out);
    }

    /** {@code out[i] = a | b}. */
    public static LibraryTaskDescriptor bitwiseOr(IntArray a, IntArray b, IntArray out) {
        return task("mlx_bitwise_or", 2, a, b, out);
    }

    /** {@code out[i] = a ^ b}. */
    public static LibraryTaskDescriptor bitwiseXor(IntArray a, IntArray b, IntArray out) {
        return task("mlx_bitwise_xor", 2, a, b, out);
    }

    /** {@code out[i] = a << b}. */
    public static LibraryTaskDescriptor leftShift(IntArray a, IntArray b, IntArray out) {
        return task("mlx_left_shift", 2, a, b, out);
    }

    /** {@code out[i] = a >> b (arithmetic)}. */
    public static LibraryTaskDescriptor rightShift(IntArray a, IntArray b, IntArray out) {
        return task("mlx_right_shift", 2, a, b, out);
    }

    /** {@code out[i] = ~a[i]}. */
    public static LibraryTaskDescriptor bitwiseInvert(IntArray a, IntArray out) {
        return task("mlx_bitwise_invert", 1, a, out);
    }

    // ---------------------------------------------------------------- Reductions
    // MLX reductions. Each comes in three forms: over the whole array (sum), over one axis (sumAxis,
    // with the input viewed as [outer, len, inner] and the middle axis reduced), and over two adjacent
    // axes (sumAxes, input viewed as [outer, len1, len2, inner]). The axis forms of the reductions need
    // inner == 1 (the reduced axes trailing); argmin also takes inner > 1. Outputs hold outer * inner
    // elements (one for a whole-array reduction). all and any write 0 or 1 into a byte array; argmin
    // and argmax write int32 indices. Softmax keeps the input's shape.

    /** Softmax over the whole array. */
    public static LibraryTaskDescriptor softmax(FloatArray x, FloatArray out) {
        return task("mlx_softmax", 1, x, out);
    }

    /** Softmax over the whole array. */
    public static LibraryTaskDescriptor softmax(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_softmax", 1, x, out);
    }

    /** Softmax over the whole array. */
    public static LibraryTaskDescriptor softmax(BFloat16Array x, BFloat16Array out) {
        return task("mlx_softmax", 1, x, out);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor softmaxRows(FloatArray x, FloatArray out, int rows, int cols) {
        return task("mlx_softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor softmaxRows(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return task("mlx_softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor softmaxRows(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return task("mlx_softmax_axis", 1, x, out, rows, cols);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    public static LibraryTaskDescriptor softmaxLastTwoAxes(FloatArray x, FloatArray out, int d0, int d1, int d2) {
        return task("mlx_softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    public static LibraryTaskDescriptor softmaxLastTwoAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2) {
        return task("mlx_softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Softmax over the last two axes of {@code x[d0, d1, d2]}. */
    public static LibraryTaskDescriptor softmaxLastTwoAxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2) {
        return task("mlx_softmax_axes", 1, x, out, d0, d1, d2);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    public static LibraryTaskDescriptor argmax(FloatArray x, IntArray out) {
        return task("mlx_argmax", 1, x, out);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    public static LibraryTaskDescriptor argmax(HalfFloatArray x, IntArray out) {
        return task("mlx_argmax", 1, x, out);
    }

    /** Index of the largest element of the whole array, into a one-element {@code IntArray}. */
    public static LibraryTaskDescriptor argmax(BFloat16Array x, IntArray out) {
        return task("mlx_argmax", 1, x, out);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor argmaxRows(FloatArray x, IntArray out, int rows, int cols) {
        return task("mlx_argmax_axis", 1, x, out, rows, cols);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor argmaxRows(HalfFloatArray x, IntArray out, int rows, int cols) {
        return task("mlx_argmax_axis", 1, x, out, rows, cols);
    }

    /** Index of the largest element of each row of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor argmaxRows(BFloat16Array x, IntArray out, int rows, int cols) {
        return task("mlx_argmax_axis", 1, x, out, rows, cols);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(FloatArray x, FloatArray out) {
        return task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(BFloat16Array x, BFloat16Array out) {
        return task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the sum of all of {@code x}. */
    public static LibraryTaskDescriptor sum(IntArray x, IntArray out) {
        return task("mlx_sum", 1, x, out);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor sumAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_sum_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the sum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor sumAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_sum_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(FloatArray x, FloatArray out) {
        return task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(BFloat16Array x, BFloat16Array out) {
        return task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the product of all of {@code x}. */
    public static LibraryTaskDescriptor prod(IntArray x, IntArray out) {
        return task("mlx_prod", 1, x, out);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor prodAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_prod_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the product of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor prodAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_prod_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(FloatArray x, FloatArray out) {
        return task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(BFloat16Array x, BFloat16Array out) {
        return task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the maximum of all of {@code x}. */
    public static LibraryTaskDescriptor max(IntArray x, IntArray out) {
        return task("mlx_max", 1, x, out);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor maxAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_max_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the maximum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor maxAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_max_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(FloatArray x, FloatArray out) {
        return task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(BFloat16Array x, BFloat16Array out) {
        return task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the minimum of all of {@code x}. */
    public static LibraryTaskDescriptor min(IntArray x, IntArray out) {
        return task("mlx_min", 1, x, out);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor minAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_min_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the minimum of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor minAxes(IntArray x, IntArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_min_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the mean of all of {@code x}. */
    public static LibraryTaskDescriptor mean(FloatArray x, FloatArray out) {
        return task("mlx_mean", 1, x, out);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor meanAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_mean_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor meanAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_mean_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the mean of all of {@code x}. */
    public static LibraryTaskDescriptor mean(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_mean", 1, x, out);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor meanAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_mean_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor meanAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_mean_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the mean of all of {@code x}. */
    public static LibraryTaskDescriptor mean(BFloat16Array x, BFloat16Array out) {
        return task("mlx_mean", 1, x, out);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor meanAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_mean_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = the mean of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor meanAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return task("mlx_mean_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = log(sum(exp(x))) of all of {@code x}. */
    public static LibraryTaskDescriptor logsumexp(FloatArray x, FloatArray out) {
        return task("mlx_logsumexp", 1, x, out);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_logsumexp_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_logsumexp_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = log(sum(exp(x))) of all of {@code x}. */
    public static LibraryTaskDescriptor logsumexp(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_logsumexp", 1, x, out);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_logsumexp_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_logsumexp_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = log(sum(exp(x))) of all of {@code x}. */
    public static LibraryTaskDescriptor logsumexp(BFloat16Array x, BFloat16Array out) {
        return task("mlx_logsumexp", 1, x, out);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_logsumexp_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = log(sum(exp(x))) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor logsumexpAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner) {
        return task("mlx_logsumexp_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the variance (divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor var(FloatArray x, FloatArray out, int ddof) {
        return task("mlx_var", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor varAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof) {
        return task("mlx_var_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor varAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return task("mlx_var_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the variance (divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor var(HalfFloatArray x, HalfFloatArray out, int ddof) {
        return task("mlx_var", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor varAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, int ddof) {
        return task("mlx_var_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor varAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return task("mlx_var_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the variance (divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor var(BFloat16Array x, BFloat16Array out, int ddof) {
        return task("mlx_var", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor varAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, int ddof) {
        return task("mlx_var_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the variance (divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor varAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner, int ddof) {
        return task("mlx_var_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the standard deviation (from the variance divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor std(FloatArray x, FloatArray out, int ddof) {
        return task("mlx_std", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor stdAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int ddof) {
        return task("mlx_std_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor stdAxes(FloatArray x, FloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return task("mlx_std_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the standard deviation (from the variance divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor std(HalfFloatArray x, HalfFloatArray out, int ddof) {
        return task("mlx_std", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor stdAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, int ddof) {
        return task("mlx_std_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor stdAxes(HalfFloatArray x, HalfFloatArray out, int outer, int len1, int len2, int inner, int ddof) {
        return task("mlx_std_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = the standard deviation (from the variance divided by {@code n - ddof}) of all of {@code x}. */
    public static LibraryTaskDescriptor std(BFloat16Array x, BFloat16Array out, int ddof) {
        return task("mlx_std", 1, x, out, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor stdAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, int ddof) {
        return task("mlx_std_axis", 1, x, out, outer, len, inner, ddof);
    }

    /** {@code out[o, j]} = the standard deviation (from the variance divided by {@code n - ddof}) of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor stdAxes(BFloat16Array x, BFloat16Array out, int outer, int len1, int len2, int inner, int ddof) {
        return task("mlx_std_axes", 1, x, out, outer, len1, len2, inner, ddof);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(FloatArray x, ByteArray out) {
        return task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(FloatArray x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(FloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(HalfFloatArray x, ByteArray out) {
        return task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(HalfFloatArray x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(HalfFloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(BFloat16Array x, ByteArray out) {
        return task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(BFloat16Array x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(BFloat16Array x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if every element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor all(IntArray x, ByteArray out) {
        return task("mlx_all", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor allAxis(IntArray x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_all_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if every element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor allAxes(IntArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_all_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(FloatArray x, ByteArray out) {
        return task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(FloatArray x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(FloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(HalfFloatArray x, ByteArray out) {
        return task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(HalfFloatArray x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(HalfFloatArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(BFloat16Array x, ByteArray out) {
        return task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(BFloat16Array x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(BFloat16Array x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = 1 if any element is non-zero, else 0 of all of {@code x}. */
    public static LibraryTaskDescriptor any(IntArray x, ByteArray out) {
        return task("mlx_any", 1, x, out);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor anyAxis(IntArray x, ByteArray out, int outer, int len, int inner) {
        return task("mlx_any_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[o, j]} = 1 if any element is non-zero, else 0 of {@code x[o, :, :, j]}, with {@code x} viewed as {@code [outer, len1, len2, inner]}. */
    public static LibraryTaskDescriptor anyAxes(IntArray x, ByteArray out, int outer, int len1, int len2, int inner) {
        return task("mlx_any_axes", 1, x, out, outer, len1, len2, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(FloatArray x, IntArray out) {
        return task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(FloatArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(HalfFloatArray x, IntArray out) {
        return task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(HalfFloatArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(BFloat16Array x, IntArray out) {
        return task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(BFloat16Array x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out[0]} = the index of the smallest element of {@code x} (the first on ties). */
    public static LibraryTaskDescriptor argmin(IntArray x, IntArray out) {
        return task("mlx_argmin", 1, x, out);
    }

    /** {@code out[o, j]} = the index of the smallest of {@code x[o, :, j]}, with {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor argminAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argmin_axis", 1, x, out, outer, len, inner);
    }

    // ---------------------------------------------------------------- Scans
    // MLX scans (cumsum, cumprod, cummax, cummin, logcumsumexp). A scan keeps the input's shape and
    // runs along the middle axis of the input viewed as [outer, len, inner].

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative sum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumsum(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumsum", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative product along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cumprod(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cumprod", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative maximum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummax(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummax", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative minimum along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor cummin(IntArray x, IntArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_cummin", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative log(sum(exp(x))) along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor logcumsumexp(FloatArray x, FloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_logcumsumexp", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative log(sum(exp(x))) along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor logcumsumexp(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_logcumsumexp", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    /**
     * Cumulative log(sum(exp(x))) along the middle axis of {@code x} viewed as {@code [outer, len, inner]}, into
     * {@code out} (same shape). {@code reverse} scans from the end; an exclusive scan
     * ({@code inclusive == false}) shifts the result by one.
     */
    public static LibraryTaskDescriptor logcumsumexp(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, boolean reverse, boolean inclusive) {
        return task("mlx_logcumsumexp", 1, x, out, outer, len, inner, reverse, inclusive);
    }

    // ---------------------------------------------------------------- Sorting
    // MLX sorting, partitioning and top-k, over a whole array or along the middle axis of an input
    // viewed as [outer, len, inner]. The arg- forms write int32 indices.

    /** The {@code k} largest elements of the whole array, in no particular order. */
    public static LibraryTaskDescriptor topk(FloatArray x, FloatArray out, int k) {
        return task("mlx_topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    public static LibraryTaskDescriptor topk(HalfFloatArray x, HalfFloatArray out, int k) {
        return task("mlx_topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of the whole array, in no particular order. */
    public static LibraryTaskDescriptor topk(BFloat16Array x, BFloat16Array out, int k) {
        return task("mlx_topk", 1, x, out, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    public static LibraryTaskDescriptor topkRows(FloatArray x, FloatArray out, int rows, int cols, int k) {
        return task("mlx_topk_axis", 1, x, out, rows, cols, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    public static LibraryTaskDescriptor topkRows(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int k) {
        return task("mlx_topk_axis", 1, x, out, rows, cols, k);
    }

    /** The {@code k} largest elements of each row of {@code x[rows, cols]}, in no particular order; {@code out[rows, k]}. */
    public static LibraryTaskDescriptor topkRows(BFloat16Array x, BFloat16Array out, int rows, int cols, int k) {
        return task("mlx_topk_axis", 1, x, out, rows, cols, k);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(FloatArray x, FloatArray out) {
        return task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(FloatArray x, FloatArray out, int outer, int len, int inner) {
        return task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(FloatArray x, IntArray out) {
        return task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(FloatArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(FloatArray x, FloatArray out, int kth) {
        return task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(FloatArray x, FloatArray out, int outer, int len, int inner, int kth) {
        return task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(FloatArray x, IntArray out, int kth) {
        return task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(FloatArray x, IntArray out, int outer, int len, int inner, int kth) {
        return task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner) {
        return task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(HalfFloatArray x, IntArray out) {
        return task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(HalfFloatArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(HalfFloatArray x, HalfFloatArray out, int kth) {
        return task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(HalfFloatArray x, HalfFloatArray out, int outer, int len, int inner, int kth) {
        return task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(HalfFloatArray x, IntArray out, int kth) {
        return task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(HalfFloatArray x, IntArray out, int outer, int len, int inner, int kth) {
        return task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(BFloat16Array x, BFloat16Array out) {
        return task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner) {
        return task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(BFloat16Array x, IntArray out) {
        return task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(BFloat16Array x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(BFloat16Array x, BFloat16Array out, int kth) {
        return task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(BFloat16Array x, BFloat16Array out, int outer, int len, int inner, int kth) {
        return task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(BFloat16Array x, IntArray out, int kth) {
        return task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(BFloat16Array x, IntArray out, int outer, int len, int inner, int kth) {
        return task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** {@code out} = the elements of {@code x} in ascending order. */
    public static LibraryTaskDescriptor sort(IntArray x, IntArray out) {
        return task("mlx_sort", 1, x, out);
    }

    /** Sorts each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]} ascending. */
    public static LibraryTaskDescriptor sortAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_sort_axis", 1, x, out, outer, len, inner);
    }

    /** {@code out} = the indices that sort {@code x} ascending. */
    public static LibraryTaskDescriptor argsort(IntArray x, IntArray out) {
        return task("mlx_argsort", 1, x, out);
    }

    /** The indices (within each slice) that sort each slice {@code x[o, :, j]} ascending. */
    public static LibraryTaskDescriptor argsortAxis(IntArray x, IntArray out, int outer, int len, int inner) {
        return task("mlx_argsort_axis", 1, x, out, outer, len, inner);
    }

    /**
     * {@code out} = {@code x} reordered so that {@code out[kth]} is the element a sort would put
     * there, with no larger element before it and no smaller one after it (otherwise unordered).
     */
    public static LibraryTaskDescriptor partition(IntArray x, IntArray out, int kth) {
        return task("mlx_partition", 1, x, out, kth);
    }

    /** {@link #partition} of each slice {@code x[o, :, j]} of {@code x} viewed as {@code [outer, len, inner]}. */
    public static LibraryTaskDescriptor partitionAxis(IntArray x, IntArray out, int outer, int len, int inner, int kth) {
        return task("mlx_partition_axis", 1, x, out, outer, len, inner, kth);
    }

    /** The indices of {@code x} in the order {@link #partition} would put the elements. */
    public static LibraryTaskDescriptor argpartition(IntArray x, IntArray out, int kth) {
        return task("mlx_argpartition", 1, x, out, kth);
    }

    /** {@link #argpartition} of each slice {@code x[o, :, j]} (indices within the slice). */
    public static LibraryTaskDescriptor argpartitionAxis(IntArray x, IntArray out, int outer, int len, int inner, int kth) {
        return task("mlx_argpartition_axis", 1, x, out, outer, len, inner, kth);
    }

    // ---------------------------------------------------------------- Indexing
    // MLX indexing: slicing and slice updates. Indices are int32. Operations that modify an array write
    // the modified copy to out and leave x unchanged.

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    public static LibraryTaskDescriptor slice(FloatArray x, FloatArray out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return task("mlx_slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    public static LibraryTaskDescriptor slice(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return task("mlx_slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    public static LibraryTaskDescriptor slice(BFloat16Array x, BFloat16Array out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return task("mlx_slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /** {@code out = x[r0:r1:rowStep, c0:c1:colStep]} for {@code x} viewed as {@code [rows, cols]}. */
    public static LibraryTaskDescriptor slice(IntArray x, IntArray out, int rows, int cols, int r0, int r1, int rowStep, int c0, int c1, int colStep) {
        return task("mlx_slice", 1, x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdate(FloatArray x, FloatArray update, FloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdate(HalfFloatArray x, HalfFloatArray update, HalfFloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdate(BFloat16Array x, BFloat16Array update, BFloat16Array out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} replaced by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdate(IntArray x, IntArray update, IntArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateAdd(FloatArray x, FloatArray update, FloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateAdd(HalfFloatArray x, HalfFloatArray update, HalfFloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateAdd(BFloat16Array x, BFloat16Array update, BFloat16Array out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} incremented by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateAdd(IntArray x, IntArray update, IntArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_add", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateProd(FloatArray x, FloatArray update, FloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateProd(HalfFloatArray x, HalfFloatArray update, HalfFloatArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateProd(BFloat16Array x, BFloat16Array update, BFloat16Array out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    /**
     * {@code out} = {@code x} (viewed as {@code [rows, cols]}) with the block {@code out[r0 : r0 +
     * update.rows, c0 : c0 + update.cols]} multiplied by {@code update} (viewed as {@code [updateRows,
     * updateCols]}).
     */
    public static LibraryTaskDescriptor sliceUpdateProd(IntArray x, IntArray update, IntArray out, int rows, int cols, int r0, int c0, int updateRows, int updateCols) {
        return task("mlx_slice_update_prod", 2, x, update, out, rows, cols, r0, c0, updateRows, updateCols);
    }

    // ---------------------------------------------------------------- LinearAlgebra
    // MLX linear algebra: matrix multiplication (plain, transposed, batched, gathered and segmented),
    // addmm, einsum (as a batched matmul), inner, outer and Kronecker products, tensordot, cross
    // products, and vector and matrix norms.

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    public static LibraryTaskDescriptor matmul(FloatArray a, FloatArray b, FloatArray c, int m, int k, int n) {
        return task("mlx_matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    public static LibraryTaskDescriptor matmul(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c, int m, int k, int n) {
        return task("mlx_matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ b[k, n]}. */
    public static LibraryTaskDescriptor matmul(BFloat16Array a, BFloat16Array b, BFloat16Array c, int m, int k, int n) {
        return task("mlx_matmul", 2, a, b, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    public static LibraryTaskDescriptor matmulTransposed(FloatArray a, FloatArray w, FloatArray c, int m, int k, int n) {
        return task("mlx_matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    public static LibraryTaskDescriptor matmulTransposed(HalfFloatArray a, HalfFloatArray w, HalfFloatArray c, int m, int k, int n) {
        return task("mlx_matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code c[m, n] = a[m, k] @ w[n, k]^T}: weights stored one row per output, as in LLM checkpoints. */
    public static LibraryTaskDescriptor matmulTransposed(BFloat16Array a, BFloat16Array w, BFloat16Array c, int m, int k, int n) {
        return task("mlx_matmul_transposed", 2, a, w, c, m, k, n);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    public static LibraryTaskDescriptor addmm(FloatArray cIn, FloatArray a, FloatArray b, FloatArray out, int m, int k, int n, float alpha, float beta) {
        return task("mlx_addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    public static LibraryTaskDescriptor addmm(HalfFloatArray cIn, HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k, int n, float alpha, float beta) {
        return task("mlx_addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /** {@code out[m, n] = alpha * a[m, k] @ b[k, n] + beta * cIn[m, n]}. */
    public static LibraryTaskDescriptor addmm(BFloat16Array cIn, BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k, int n, float alpha, float beta) {
        return task("mlx_addmm", 3, cIn, a, b, out, m, k, n, alpha, beta);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    public static LibraryTaskDescriptor gatherMm(FloatArray a, FloatArray b, IntArray lhs, IntArray rhs, FloatArray out, int batchesA, int batchesB, int m, int k, int n) {
        return task("mlx_gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    public static LibraryTaskDescriptor gatherMm(HalfFloatArray a, HalfFloatArray b, IntArray lhs, IntArray rhs, HalfFloatArray out, int batchesA, int batchesB, int m, int k, int n) {
        return task("mlx_gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /**
     * Gathered batched matmul: {@code out[i] = a[lhs[i]] @ b[rhs[i]]}, with {@code a[batchesA, m, k]},
     * {@code b[batchesB, k, n]} and {@code out[count, m, n]} (the mixture-of-experts building block).
     */
    public static LibraryTaskDescriptor gatherMm(BFloat16Array a, BFloat16Array b, IntArray lhs, IntArray rhs, BFloat16Array out, int batchesA, int batchesB, int m, int k, int n) {
        return task("mlx_gather_mm", 4, a, b, lhs, rhs, out, batchesA, batchesB, m, k, n);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    public static LibraryTaskDescriptor cross(FloatArray a, FloatArray b, FloatArray out, int count) {
        return task("mlx_linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    public static LibraryTaskDescriptor norm(FloatArray x, FloatArray out, int rows, int cols, float ord) {
        return task("mlx_linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor l2Norm(FloatArray x, FloatArray out, int rows, int cols) {
        return task("mlx_linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    public static LibraryTaskDescriptor frobeniusNorm(FloatArray x, FloatArray out, int batch, int rows, int cols) {
        return task("mlx_linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    public static LibraryTaskDescriptor cross(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int count) {
        return task("mlx_linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    public static LibraryTaskDescriptor norm(HalfFloatArray x, HalfFloatArray out, int rows, int cols, float ord) {
        return task("mlx_linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor l2Norm(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return task("mlx_linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    public static LibraryTaskDescriptor frobeniusNorm(HalfFloatArray x, HalfFloatArray out, int batch, int rows, int cols) {
        return task("mlx_linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** {@code out[i] = a[i] x b[i]} for {@code count} 3-vectors stored as {@code [count, 3]}. */
    public static LibraryTaskDescriptor cross(BFloat16Array a, BFloat16Array b, BFloat16Array out, int count) {
        return task("mlx_linalg_cross", 2, a, b, out, count);
    }

    /**
     * {@code out[r]} = the {@code ord}-norm of row {@code r} of {@code x[rows, cols]}:
     * {@code (sum |x|^ord)^(1/ord)}; infinity and -infinity give the largest and smallest
     * magnitude, 0 the number of non-zeros.
     */
    public static LibraryTaskDescriptor norm(BFloat16Array x, BFloat16Array out, int rows, int cols, float ord) {
        return task("mlx_linalg_norm", 1, x, out, rows, cols, ord);
    }

    /** {@code out[r]} = the Euclidean norm of row {@code r} of {@code x[rows, cols]}. */
    public static LibraryTaskDescriptor l2Norm(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return task("mlx_linalg_norm_l2", 1, x, out, rows, cols);
    }

    /** {@code out[b]} = the Frobenius norm of matrix {@code b} of {@code x[batch, rows, cols]}. */
    public static LibraryTaskDescriptor frobeniusNorm(BFloat16Array x, BFloat16Array out, int batch, int rows, int cols) {
        return task("mlx_linalg_norm_matrix", 1, x, out, batch, rows, cols);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    public static LibraryTaskDescriptor einsumBatchedMatmul(FloatArray a, FloatArray b, FloatArray out, int batch, int m, int k, int n) {
        return task("mlx_einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    public static LibraryTaskDescriptor einsumBatchedMatmul(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int batch, int m, int k, int n) {
        return task("mlx_einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** Einstein summation {@code "bij,bjk->bik"}: a batched matmul of {@code a[batch, m, k]} and {@code b[batch, k, n]}. */
    public static LibraryTaskDescriptor einsumBatchedMatmul(BFloat16Array a, BFloat16Array b, BFloat16Array out, int batch, int m, int k, int n) {
        return task("mlx_einsum_bmm", 2, a, b, out, batch, m, k, n);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    public static LibraryTaskDescriptor inner(FloatArray a, FloatArray b, FloatArray out) {
        return task("mlx_inner", 2, a, b, out);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    public static LibraryTaskDescriptor inner(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return task("mlx_inner", 2, a, b, out);
    }

    /** {@code out[0]} = the inner (dot) product of vectors {@code a} and {@code b}. */
    public static LibraryTaskDescriptor inner(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return task("mlx_inner", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    public static LibraryTaskDescriptor outer(FloatArray a, FloatArray b, FloatArray out) {
        return task("mlx_outer", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    public static LibraryTaskDescriptor outer(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return task("mlx_outer", 2, a, b, out);
    }

    /** {@code out[i, j] = a[i] * b[j]}. */
    public static LibraryTaskDescriptor outer(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return task("mlx_outer", 2, a, b, out);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    public static LibraryTaskDescriptor kron(FloatArray a, FloatArray b, FloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        return task("mlx_kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    public static LibraryTaskDescriptor kron(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int rowsA, int colsA, int rowsB, int colsB) {
        return task("mlx_kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** The Kronecker product of {@code a[rowsA, colsA]} and {@code b[rowsB, colsB]}: {@code out[rowsA * rowsB, colsA * colsB]}. */
    public static LibraryTaskDescriptor kron(BFloat16Array a, BFloat16Array b, BFloat16Array out, int rowsA, int colsA, int rowsB, int colsB) {
        return task("mlx_kron", 2, a, b, out, rowsA, colsA, rowsB, colsB);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    public static LibraryTaskDescriptor tensordot(FloatArray a, FloatArray b, FloatArray out, int m, int k1, int k2, int n) {
        return task("mlx_tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    public static LibraryTaskDescriptor tensordot(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k1, int k2, int n) {
        return task("mlx_tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k1, k2]} and {@code b[k1, k2, n]} contracted over the k axes: {@code out[m, n]}. */
    public static LibraryTaskDescriptor tensordot(BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k1, int k2, int n) {
        return task("mlx_tensordot", 2, a, b, out, m, k1, k2, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    public static LibraryTaskDescriptor tensordotAxis(FloatArray a, FloatArray b, FloatArray out, int m, int k, int n) {
        return task("mlx_tensordot_axis", 2, a, b, out, m, k, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    public static LibraryTaskDescriptor tensordotAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int m, int k, int n) {
        return task("mlx_tensordot_axis", 2, a, b, out, m, k, n);
    }

    /** {@code a[m, k]} and {@code b[k, n]} contracted over one axis (a matrix product). */
    public static LibraryTaskDescriptor tensordotAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out, int m, int k, int n) {
        return task("mlx_tensordot_axis", 2, a, b, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    public static LibraryTaskDescriptor segmentedMm(FloatArray a, FloatArray b, IntArray segments, FloatArray out, int m, int k, int n) {
        return task("mlx_segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    public static LibraryTaskDescriptor segmentedMm(HalfFloatArray a, HalfFloatArray b, IntArray segments, HalfFloatArray out, int m, int k, int n) {
        return task("mlx_segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    /**
     * {@code out[s] = a[:, k0 : k1] @ b[k0 : k1, :]} for each segment {@code s}, with
     * {@code segments[s] = (k0, k1)}: {@code a[m, k]}, {@code b[k, n]}, {@code out[segments, m, n]}.
     */
    public static LibraryTaskDescriptor segmentedMm(BFloat16Array a, BFloat16Array b, IntArray segments, BFloat16Array out, int m, int k, int n) {
        return task("mlx_segmented_mm", 3, a, b, segments, out, m, k, n);
    }

    // ---------------------------------------------------------------- Quantization
    // MLX quantization: affine group quantization and dequantization, the quantized matmul, and the
    // mxfp8 quantize and quantized-quantized matmul.

    // MLX's affine format: weights w[rows, cols] are split along cols into groups of groupSize
    // (32, 64 or 128); each group has a scale and a bias, w = scale * q + bias, with q stored in
    // `bits` bits (2-8) packed into uint32 words, low bits first. Packed weights are an IntArray of
    // rows * cols * bits / 32 words; scales and biases have rows * cols / groupSize elements in the
    // activations' float type.

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    public static LibraryTaskDescriptor quantizedMatmul(FloatArray x, IntArray wq, FloatArray scales, FloatArray biases, FloatArray y, int m, int k, int n, int groupSize,
            int bits) {
        return task("mlx_quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    public static LibraryTaskDescriptor quantizedMatmul(HalfFloatArray x, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, HalfFloatArray y, int m, int k, int n,
            int groupSize, int bits) {
        return task("mlx_quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /** {@code y[m, n] = x[m, k] @ dequantize(w)[n, k]^T} with {@code w} quantized as above. */
    public static LibraryTaskDescriptor quantizedMatmul(BFloat16Array x, IntArray wq, BFloat16Array scales, BFloat16Array biases, BFloat16Array y, int m, int k, int n,
            int groupSize, int bits) {
        return task("mlx_quantized_matmul", 4, x, wq, scales, biases, y, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    public static LibraryTaskDescriptor gatherQmm(FloatArray x, IntArray wq, FloatArray scales, FloatArray biases, IntArray lhsIndices, IntArray rhsIndices, FloatArray y,
            int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return task("mlx_gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    public static LibraryTaskDescriptor gatherQmm(HalfFloatArray x, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, IntArray lhsIndices, IntArray rhsIndices,
            HalfFloatArray y, int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return task("mlx_gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /**
     * Mixture-of-experts quantized matmul: {@code y[b] = x[lhsIndices[b]] @ dequantize(w[rhsIndices[b]])^T}, with {@code x[batches, m, k]}, {@code
     * w[experts, n, k]}, {@code y[batches, m, n]}.
     */
    public static LibraryTaskDescriptor gatherQmm(BFloat16Array x, IntArray wq, BFloat16Array scales, BFloat16Array biases, IntArray lhsIndices, IntArray rhsIndices,
            BFloat16Array y, int batches, int experts, int m, int k, int n, int groupSize, int bits) {
        return task("mlx_gather_qmm", 6, x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    public static LibraryTaskDescriptor quantize(FloatArray w, IntArray wq, FloatArray scales, FloatArray biases, int rows, int cols, int groupSize, int bits) {
        return task("mlx_quantize", new int[] { 1, 2, 3 }, w, wq, scales, biases, rows, cols, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    public static LibraryTaskDescriptor quantize(HalfFloatArray w, IntArray wq, HalfFloatArray scales, HalfFloatArray biases, int rows, int cols, int groupSize, int bits) {
        return task("mlx_quantize", new int[] { 1, 2, 3 }, w, wq, scales, biases, rows, cols, groupSize, bits);
    }

    /** Quantizes {@code w[rows, cols]} into packed {@code wq}, {@code scales} and {@code biases}. */
    public static LibraryTaskDescriptor quantize(BFloat16Array w, IntArray wq, BFloat16Array scales, BFloat16Array biases, int rows, int cols, int groupSize, int bits) {
        return task("mlx_quantize", new int[] { 1, 2, 3 }, w, wq, scales, biases, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    public static LibraryTaskDescriptor dequantize(IntArray wq, FloatArray scales, FloatArray biases, FloatArray w, int rows, int cols, int groupSize, int bits) {
        return task("mlx_dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    public static LibraryTaskDescriptor dequantize(IntArray wq, HalfFloatArray scales, HalfFloatArray biases, HalfFloatArray w, int rows, int cols, int groupSize, int bits) {
        return task("mlx_dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /** Dequantizes packed {@code wq}, {@code scales} and {@code biases} into {@code w[rows, cols]}. */
    public static LibraryTaskDescriptor dequantize(IntArray wq, BFloat16Array scales, BFloat16Array biases, BFloat16Array w, int rows, int cols, int groupSize, int bits) {
        return task("mlx_dequantize", 3, wq, scales, biases, w, rows, cols, groupSize, bits);
    }

    /**
     * Quantized-quantized matmul {@code out[m, n] = x[m, k] @ w[n, k]^T} with both operands quantized
     * in {@code mode} (0: mxfp8, 1: nvfp4, 2: mxfp4); {@code w} and {@code wScales} as produced by an
     * MLX quantization in that mode.
     */
    public static LibraryTaskDescriptor qqmm(FloatArray x, IntArray w, ByteArray wScales, FloatArray out, int m, int k, int n, int mode) {
        return task("mlx_qqmm", 3, x, w, wScales, out, m, k, n, mode);
    }

    /**
     * Quantizes {@code w[rows, cols]} in an MX mode (0: mxfp8, 2: mxfp4): {@code wq} packs the codes
     * into 32-bit words and {@code scales} holds one E8M0 exponent per group of 32, the layout
     * {@link #qqmm} consumes.
     */
    public static LibraryTaskDescriptor quantizeMx(FloatArray w, IntArray wq, ByteArray scales, int rows, int cols, int mode) {
        return task("mlx_quantize_mx", new int[] { 1, 2 }, w, wq, scales, rows, cols, mode);
    }

    // ---------------------------------------------------------------- NeuralNetwork
    // MLX's fused neural-network kernels (mlx.fast): RMS and layer normalization, rotary position
    // embeddings and scaled dot-product attention.

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor rmsNorm(FloatArray x, FloatArray weight, FloatArray out, int rows, int dim, float eps) {
        return task("mlx_fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor rmsNorm(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int rows, int dim, float eps) {
        return task("mlx_fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** RMS normalisation of each row: {@code out = x / sqrt(mean(x^2) + eps) * weight}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor rmsNorm(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int rows, int dim, float eps) {
        return task("mlx_fast_rms_norm", 2, x, weight, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor layerNorm(FloatArray x, FloatArray weight, FloatArray bias, FloatArray out, int rows, int dim, float eps) {
        return task("mlx_fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor layerNorm(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray bias, HalfFloatArray out, int rows, int dim, float eps) {
        return task("mlx_fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /** Layer normalisation of each row: {@code out = (x - mean) / sqrt(var + eps) * weight + bias}, {@code x[rows, dim]}. */
    public static LibraryTaskDescriptor layerNorm(BFloat16Array x, BFloat16Array weight, BFloat16Array bias, BFloat16Array out, int rows, int dim, float eps) {
        return task("mlx_fast_layer_norm", 3, x, weight, bias, out, rows, dim, eps);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    public static LibraryTaskDescriptor rope(FloatArray x, FloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base, float scale,
            int offset) {
        return task("mlx_fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    public static LibraryTaskDescriptor rope(HalfFloatArray x, HalfFloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base,
            float scale, int offset) {
        return task("mlx_fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /**
     * Rotary position embedding of {@code x[batch, heads, seqLen, headDim]} at positions {@code offset .. offset + seqLen - 1}; rotates the first {@code
     * dims} features, pairing {@code (i, i + dims/2)} unless {@code traditional} (adjacent pairs).
     */
    public static LibraryTaskDescriptor rope(BFloat16Array x, BFloat16Array out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base,
            float scale, int offset) {
        return task("mlx_fast_rope", 1, x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    public static LibraryTaskDescriptor ropeDynamic(FloatArray x, IntArray offset, FloatArray out, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional,
            float base, float scale) {
        return task("mlx_fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    public static LibraryTaskDescriptor ropeDynamic(HalfFloatArray x, IntArray offset, HalfFloatArray out, int batch, int heads, int seqLen, int headDim, int dims,
            boolean traditional, float base, float scale) {
        return task("mlx_fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /** {@link #rope} with the position offset read from a one-element {@code IntArray} on the device, so it can change between executions of the same graph. */
    public static LibraryTaskDescriptor ropeDynamic(BFloat16Array x, IntArray offset, BFloat16Array out, int batch, int heads, int seqLen, int headDim, int dims,
            boolean traditional, float base, float scale) {
        return task("mlx_fast_rope_dynamic", 2, x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    public static LibraryTaskDescriptor scaledDotProductAttention(FloatArray q, FloatArray k, FloatArray v, FloatArray out, int batch, int qHeads, int kvHeads, int qLen,
            int kvLen, int headDim, float scale, boolean causal) {
        return task("mlx_fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    public static LibraryTaskDescriptor scaledDotProductAttention(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, HalfFloatArray out, int batch, int qHeads, int kvHeads,
            int qLen, int kvLen, int headDim, float scale, boolean causal) {
        return task("mlx_fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    /**
     * Scaled dot-product attention {@code softmax(q k^T * scale) v}, with {@code q[batch, qHeads, qLen, headDim]} and {@code k, v[batch, kvHeads, kvLen,
     * headDim]} ({@code qHeads} a multiple of {@code kvHeads} for grouped-query attention); {@code causal} masks future positions.
     */
    public static LibraryTaskDescriptor scaledDotProductAttention(BFloat16Array q, BFloat16Array k, BFloat16Array v, BFloat16Array out, int batch, int qHeads, int kvHeads,
            int qLen, int kvLen, int headDim, float scale, boolean causal) {
        return task("mlx_fast_scaled_dot_product_attention", 3, q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal);
    }

    // ---------------------------------------------------------------- Fft
    // MLX FFTs. Complex arrays are float arrays of interleaved (real, imaginary) pairs, so a complex
    // [rows, len] array holds 2 * rows * len floats. 2D and 3D transforms run over the last two or
    // three axes; norm is FFT_BACKWARD, FFT_ORTHO or FFT_FORWARD.

    /** FFT of each row of complex {@code x[rows, len]}, zero-padded or cropped to {@code n}: complex {@code out[rows, n]}. */
    public static LibraryTaskDescriptor fft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return task("mlx_fft_fft", 1, x, out, rows, len, n, norm);
    }

    /** Inverse FFT of each row of complex {@code x[rows, len]}: complex {@code out[rows, n]}. */
    public static LibraryTaskDescriptor ifft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return task("mlx_fft_ifft", 1, x, out, rows, len, n, norm);
    }

    /** FFT of each row of real {@code x[rows, len]} (as length {@code n}): the non-negative frequencies, complex {@code out[rows, n / 2 + 1]}. */
    public static LibraryTaskDescriptor rfft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return task("mlx_fft_rfft", 1, x, out, rows, len, n, norm);
    }

    /** Inverse of {@link #rfft}: complex {@code x[rows, len]} (non-negative frequencies) to real {@code out[rows, n]}. */
    public static LibraryTaskDescriptor irfft(FloatArray x, FloatArray out, int rows, int len, int n, int norm) {
        return task("mlx_fft_irfft", 1, x, out, rows, len, n, norm);
    }

    /** 2D FFT of each complex {@code x[b]} of {@code [batch, h, w]}. */
    public static LibraryTaskDescriptor fft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return task("mlx_fft_fft2", 1, x, out, batch, h, w, norm);
    }

    /** 2D inverse FFT of each complex {@code x[b]} of {@code [batch, h, w]}. */
    public static LibraryTaskDescriptor ifft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return task("mlx_fft_ifft2", 1, x, out, batch, h, w, norm);
    }

    /** 2D FFT of each real {@code x[b]} of {@code [batch, h, w]}: complex {@code out[batch, h, w / 2 + 1]}. */
    public static LibraryTaskDescriptor rfft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return task("mlx_fft_rfft2", 1, x, out, batch, h, w, norm);
    }

    /** Inverse of {@link #rfft2}: complex {@code x[batch, h, w / 2 + 1]} to real {@code out[batch, h, w]}. */
    public static LibraryTaskDescriptor irfft2(FloatArray x, FloatArray out, int batch, int h, int w, int norm) {
        return task("mlx_fft_irfft2", 1, x, out, batch, h, w, norm);
    }

    /** 3D FFT of each complex {@code x[b]} of {@code [batch, d, h, w]}. */
    public static LibraryTaskDescriptor fftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return task("mlx_fft_fftn", 1, x, out, batch, d, h, w, norm);
    }

    /** 3D inverse FFT of each complex {@code x[b]} of {@code [batch, d, h, w]}. */
    public static LibraryTaskDescriptor ifftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return task("mlx_fft_ifftn", 1, x, out, batch, d, h, w, norm);
    }

    /** 3D FFT of each real {@code x[b]} of {@code [batch, d, h, w]}: complex {@code out[batch, d, h, w / 2 + 1]}. */
    public static LibraryTaskDescriptor rfftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return task("mlx_fft_rfftn", 1, x, out, batch, d, h, w, norm);
    }

    /** Inverse of {@link #rfftn}: complex {@code x[batch, d, h, w / 2 + 1]} to real {@code out[batch, d, h, w]}. */
    public static LibraryTaskDescriptor irfftn(FloatArray x, FloatArray out, int batch, int d, int h, int w, int norm) {
        return task("mlx_fft_irfftn", 1, x, out, batch, d, h, w, norm);
    }

    /** {@code out[i] = } the i-th sample frequency of an n-point FFT with sample spacing {@code d} ({@code out} has n elements). */
    public static LibraryTaskDescriptor fftfreq(FloatArray out, int n, float d) {
        return task("mlx_fft_fftfreq", 0, out, n, d);
    }

    /** The non-negative sample frequencies of an n-point real FFT ({@code out} has n / 2 + 1 elements). */
    public static LibraryTaskDescriptor rfftfreq(FloatArray out, int n, float d) {
        return task("mlx_fft_rfftfreq", 0, out, n, d);
    }

    /** Moves the zero-frequency term of each row of {@code x[rows, len]} to the centre (a roll by {@code len / 2}). */
    public static LibraryTaskDescriptor fftshift(FloatArray x, FloatArray out, int rows, int len) {
        return task("mlx_fft_fftshift", 1, x, out, rows, len);
    }

    /** Inverse of {@link #fftshift} (a roll by {@code -(len / 2)}). */
    public static LibraryTaskDescriptor ifftshift(FloatArray x, FloatArray out, int rows, int len) {
        return task("mlx_fft_ifftshift", 1, x, out, rows, len);
    }

    /** Moves the zero-frequency term of each row of {@code x[rows, len]} to the centre (a roll by {@code len / 2}). */
    public static LibraryTaskDescriptor fftshift(HalfFloatArray x, HalfFloatArray out, int rows, int len) {
        return task("mlx_fft_fftshift", 1, x, out, rows, len);
    }

    /** Inverse of {@link #fftshift} (a roll by {@code -(len / 2)}). */
    public static LibraryTaskDescriptor ifftshift(HalfFloatArray x, HalfFloatArray out, int rows, int len) {
        return task("mlx_fft_ifftshift", 1, x, out, rows, len);
    }

    /** Moves the zero-frequency term of each row of {@code x[rows, len]} to the centre (a roll by {@code len / 2}). */
    public static LibraryTaskDescriptor fftshift(BFloat16Array x, BFloat16Array out, int rows, int len) {
        return task("mlx_fft_fftshift", 1, x, out, rows, len);
    }

    /** Inverse of {@link #fftshift} (a roll by {@code -(len / 2)}). */
    public static LibraryTaskDescriptor ifftshift(BFloat16Array x, BFloat16Array out, int rows, int len) {
        return task("mlx_fft_ifftshift", 1, x, out, rows, len);
    }

    // ---------------------------------------------------------------- Convolution
    // MLX convolutions, channels-last as in MLX. Stride, padding and dilation apply equally to every
    // spatial axis (MLX accepts them per axis; one value keeps the argument lists within the
    // library-task arity).

    /**
     * 1D convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code out[n, outLen,
     * cout]}.
     */
    public static LibraryTaskDescriptor conv1d(FloatArray x, FloatArray w, FloatArray out, int n, int len, int cin, int cout, int k, int stride, int padding, int dilation,
            int groups) {
        return task("mlx_conv1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups);
    }

    /**
     * 1D convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code out[n, outLen,
     * cout]}.
     */
    public static LibraryTaskDescriptor conv1d(HalfFloatArray x, HalfFloatArray w, HalfFloatArray out, int n, int len, int cin, int cout, int k, int stride, int padding,
            int dilation, int groups) {
        return task("mlx_conv1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups);
    }

    /**
     * 1D convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code out[n, outLen,
     * cout]}.
     */
    public static LibraryTaskDescriptor conv1d(BFloat16Array x, BFloat16Array w, BFloat16Array out, int n, int len, int cin, int cout, int k, int stride, int padding,
            int dilation, int groups) {
        return task("mlx_conv1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups);
    }

    /**
     * 2D convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]}, {@code
     * out[n, outH, outW, cout]}.
     */
    public static LibraryTaskDescriptor conv2d(FloatArray x, FloatArray weight, FloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padding, int dilation, int groups) {
        return task("mlx_conv2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups);
    }

    /**
     * 2D convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]}, {@code
     * out[n, outH, outW, cout]}.
     */
    public static LibraryTaskDescriptor conv2d(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padding, int dilation, int groups) {
        return task("mlx_conv2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups);
    }

    /**
     * 2D convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]}, {@code
     * out[n, outH, outW, cout]}.
     */
    public static LibraryTaskDescriptor conv2d(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padding, int dilation, int groups) {
        return task("mlx_conv2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups);
    }

    /**
     * 1D transposed convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code
     * out[n, (len - 1) * stride - 2 * padding + dilation * (k - 1) + outputPadding + 1, cout]}.
     */
    public static LibraryTaskDescriptor convTranspose1d(FloatArray x, FloatArray w, FloatArray out, int n, int len, int cin, int cout, int k, int stride, int padding,
            int dilation, int outputPadding, int groups) {
        return task("mlx_conv_transpose1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 1D transposed convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code
     * out[n, (len - 1) * stride - 2 * padding + dilation * (k - 1) + outputPadding + 1, cout]}.
     */
    public static LibraryTaskDescriptor convTranspose1d(HalfFloatArray x, HalfFloatArray w, HalfFloatArray out, int n, int len, int cin, int cout, int k, int stride,
            int padding, int dilation, int outputPadding, int groups) {
        return task("mlx_conv_transpose1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 1D transposed convolution: {@code x[n, len, cin]}, {@code w[cout, k, cin / groups]}, {@code
     * out[n, (len - 1) * stride - 2 * padding + dilation * (k - 1) + outputPadding + 1, cout]}.
     */
    public static LibraryTaskDescriptor convTranspose1d(BFloat16Array x, BFloat16Array w, BFloat16Array out, int n, int len, int cin, int cout, int k, int stride,
            int padding, int dilation, int outputPadding, int groups) {
        return task("mlx_conv_transpose1d", 2, x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 2D transposed convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]};
     * each output axis as in {@link #convTranspose1d}.
     */
    public static LibraryTaskDescriptor convTranspose2d(FloatArray x, FloatArray weight, FloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padding, int dilation, int outputPadding, int groups) {
        return task("mlx_conv_transpose2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 2D transposed convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]};
     * each output axis as in {@link #convTranspose1d}.
     */
    public static LibraryTaskDescriptor convTranspose2d(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padding, int dilation, int outputPadding, int groups) {
        return task("mlx_conv_transpose2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * 2D transposed convolution: {@code x[n, h, w, cin]}, {@code weight[cout, kh, kw, cin / groups]};
     * each output axis as in {@link #convTranspose1d}.
     */
    public static LibraryTaskDescriptor convTranspose2d(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padding, int dilation, int outputPadding, int groups) {
        return task("mlx_conv_transpose2d", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups);
    }

    /**
     * General 2D convolution: {@code x[n, h, w, cin]} dilated by {@code inputDilation} and padded by
     * {@code padLo} / {@code padHi}, {@code weight[cout, kh, kw, cin / groups]} dilated by {@code
     * kernelDilation} (and flipped, a true convolution, if {@code flip}).
     */
    public static LibraryTaskDescriptor convGeneral2d(FloatArray x, FloatArray weight, FloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw, int stride,
            int padLo, int padHi, int kernelDilation, int inputDilation, int groups, boolean flip) {
        return task("mlx_conv_general", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip);
    }

    /**
     * General 2D convolution: {@code x[n, h, w, cin]} dilated by {@code inputDilation} and padded by
     * {@code padLo} / {@code padHi}, {@code weight[cout, kh, kw, cin / groups]} dilated by {@code
     * kernelDilation} (and flipped, a true convolution, if {@code flip}).
     */
    public static LibraryTaskDescriptor convGeneral2d(HalfFloatArray x, HalfFloatArray weight, HalfFloatArray out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padLo, int padHi, int kernelDilation, int inputDilation, int groups, boolean flip) {
        return task("mlx_conv_general", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip);
    }

    /**
     * General 2D convolution: {@code x[n, h, w, cin]} dilated by {@code inputDilation} and padded by
     * {@code padLo} / {@code padHi}, {@code weight[cout, kh, kw, cin / groups]} dilated by {@code
     * kernelDilation} (and flipped, a true convolution, if {@code flip}).
     */
    public static LibraryTaskDescriptor convGeneral2d(BFloat16Array x, BFloat16Array weight, BFloat16Array out, int n, int h, int w, int cin, int cout, int kh, int kw,
            int stride, int padLo, int padHi, int kernelDilation, int inputDilation, int groups, boolean flip) {
        return task("mlx_conv_general", 2, x, weight, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip);
    }

    // ---------------------------------------------------------------- Creation
    // MLX array construction (ranges, constants, identity and triangular matrices, windows, grids) and
    // matrix-structure operations (diag, diagonal, trace, tril, triu). Constructors write into out,
    // whose type sets the dtype and whose length sets the size where no size is given.

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(FloatArray out, float start, float stop, float step) {
        return task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(HalfFloatArray out, float start, float stop, float step) {
        return task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(BFloat16Array out, float start, float stop, float step) {
        return task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out[i] = start + i * step}; {@code out} must hold exactly the number of values MLX generates from start to stop (exclusive). */
    public static LibraryTaskDescriptor arange(IntArray out, float start, float stop, float step) {
        return task("mlx_arange", 0, out, start, stop, step);
    }

    /** {@code out.length} evenly spaced values from start to stop (inclusive). */
    public static LibraryTaskDescriptor linspace(FloatArray out, float start, float stop) {
        return task("mlx_linspace", 0, out, start, stop);
    }

    /** {@code out.length} evenly spaced values from start to stop (inclusive). */
    public static LibraryTaskDescriptor linspace(HalfFloatArray out, float start, float stop) {
        return task("mlx_linspace", 0, out, start, stop);
    }

    /** {@code out.length} evenly spaced values from start to stop (inclusive). */
    public static LibraryTaskDescriptor linspace(BFloat16Array out, float start, float stop) {
        return task("mlx_linspace", 0, out, start, stop);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(FloatArray out, int n, int m, int k) {
        return task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(FloatArray out, int n) {
        return task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(FloatArray out, int n, int m, int k) {
        return task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(FloatArray out, float value) {
        return task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(FloatArray a, FloatArray out, float value) {
        return task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(FloatArray out) {
        return task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(FloatArray a, FloatArray out) {
        return task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(FloatArray out) {
        return task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(FloatArray a, FloatArray out) {
        return task("mlx_ones_like", 1, a, out);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(HalfFloatArray out, int n, int m, int k) {
        return task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(HalfFloatArray out, int n) {
        return task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(HalfFloatArray out, int n, int m, int k) {
        return task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(HalfFloatArray out, float value) {
        return task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(HalfFloatArray a, HalfFloatArray out, float value) {
        return task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(HalfFloatArray out) {
        return task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(HalfFloatArray out) {
        return task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(HalfFloatArray a, HalfFloatArray out) {
        return task("mlx_ones_like", 1, a, out);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(BFloat16Array out, int n, int m, int k) {
        return task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(BFloat16Array out, int n) {
        return task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(BFloat16Array out, int n, int m, int k) {
        return task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(BFloat16Array out, float value) {
        return task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(BFloat16Array a, BFloat16Array out, float value) {
        return task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(BFloat16Array out) {
        return task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(BFloat16Array a, BFloat16Array out) {
        return task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(BFloat16Array out) {
        return task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(BFloat16Array a, BFloat16Array out) {
        return task("mlx_ones_like", 1, a, out);
    }

    /** An n x m matrix with ones on diagonal k (0 is the main diagonal, positive above it). */
    public static LibraryTaskDescriptor eye(IntArray out, int n, int m, int k) {
        return task("mlx_eye", 0, out, n, m, k);
    }

    /** The n x n identity matrix. */
    public static LibraryTaskDescriptor identity(IntArray out, int n) {
        return task("mlx_identity", 0, out, n);
    }

    /** An n x m matrix with ones on and below diagonal k. */
    public static LibraryTaskDescriptor tri(IntArray out, int n, int m, int k) {
        return task("mlx_tri", 0, out, n, m, k);
    }

    /** Every element of {@code out} set to {@code value}. */
    public static LibraryTaskDescriptor full(IntArray out, float value) {
        return task("mlx_full", 0, out, value);
    }

    /** Every element of {@code out} set to {@code value}, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor fullLike(IntArray a, IntArray out, float value) {
        return task("mlx_full_like", 1, a, out, value);
    }

    /** Every element of {@code out} set to 0. */
    public static LibraryTaskDescriptor zeros(IntArray out) {
        return task("mlx_zeros", 0, out);
    }

    /** Every element of {@code out} set to 0, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor zerosLike(IntArray a, IntArray out) {
        return task("mlx_zeros_like", 1, a, out);
    }

    /** Every element of {@code out} set to 1. */
    public static LibraryTaskDescriptor ones(IntArray out) {
        return task("mlx_ones", 0, out);
    }

    /** Every element of {@code out} set to 1, with {@code out} shaped and typed like {@code a}. */
    public static LibraryTaskDescriptor onesLike(IntArray a, IntArray out) {
        return task("mlx_ones_like", 1, a, out);
    }

    /** The Bartlett window of length {@code out.length}. */
    public static LibraryTaskDescriptor bartlett(FloatArray out) {
        return task("mlx_bartlett", 0, out);
    }

    /** The Blackman window of length {@code out.length}. */
    public static LibraryTaskDescriptor blackman(FloatArray out) {
        return task("mlx_blackman", 0, out);
    }

    /** The Hamming window of length {@code out.length}. */
    public static LibraryTaskDescriptor hamming(FloatArray out) {
        return task("mlx_hamming", 0, out);
    }

    /** The Hann window of length {@code out.length}. */
    public static LibraryTaskDescriptor hanning(FloatArray out) {
        return task("mlx_hanning", 0, out);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(FloatArray x, FloatArray y, FloatArray outX, FloatArray outY, boolean ij) {
        return task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(FloatArray v, FloatArray out, int k) {
        return task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(FloatArray a, FloatArray out, int rows, int cols, int offset) {
        return task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(FloatArray a, FloatArray out, int rows, int cols, int offset) {
        return task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(FloatArray a, FloatArray out, int rows, int cols, int k) {
        return task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(FloatArray a, FloatArray out, int rows, int cols, int k) {
        return task("mlx_triu", 1, a, out, rows, cols, k);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(HalfFloatArray x, HalfFloatArray y, HalfFloatArray outX, HalfFloatArray outY, boolean ij) {
        return task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(HalfFloatArray v, HalfFloatArray out, int k) {
        return task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int offset) {
        return task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int offset) {
        return task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int k) {
        return task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(HalfFloatArray a, HalfFloatArray out, int rows, int cols, int k) {
        return task("mlx_triu", 1, a, out, rows, cols, k);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(BFloat16Array x, BFloat16Array y, BFloat16Array outX, BFloat16Array outY, boolean ij) {
        return task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(BFloat16Array v, BFloat16Array out, int k) {
        return task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(BFloat16Array a, BFloat16Array out, int rows, int cols, int offset) {
        return task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(BFloat16Array a, BFloat16Array out, int rows, int cols, int offset) {
        return task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(BFloat16Array a, BFloat16Array out, int rows, int cols, int k) {
        return task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(BFloat16Array a, BFloat16Array out, int rows, int cols, int k) {
        return task("mlx_triu", 1, a, out, rows, cols, k);
    }

    /**
     * Coordinate grids of x (nx values) and y (ny values): Cartesian indexing gives {@code [ny, nx]}
     * grids with {@code outX[r, c] = x[c]} and {@code outY[r, c] = y[r]}; matrix indexing
     * ({@code ij}) gives {@code [nx, ny]} grids with {@code outX[r, c] = x[r]}.
     */
    public static LibraryTaskDescriptor meshgrid(IntArray x, IntArray y, IntArray outX, IntArray outY, boolean ij) {
        return task("mlx_meshgrid", new int[] { 2, 3 }, x, y, outX, outY, ij);
    }

    /** The square matrix with {@code v} on diagonal k and zeros elsewhere ({@code out} is {@code (v.length + |k|)} squared). */
    public static LibraryTaskDescriptor diag(IntArray v, IntArray out, int k) {
        return task("mlx_diag", 1, v, out, k);
    }

    /** Diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor diagonal(IntArray a, IntArray out, int rows, int cols, int offset) {
        return task("mlx_diagonal", 1, a, out, rows, cols, offset);
    }

    /** {@code out[0]} = the sum of diagonal {@code offset} of {@code a[rows, cols]}. */
    public static LibraryTaskDescriptor trace(IntArray a, IntArray out, int rows, int cols, int offset) {
        return task("mlx_trace", 1, a, out, rows, cols, offset);
    }

    /** {@code a[rows, cols]} with the elements above diagonal k set to 0. */
    public static LibraryTaskDescriptor tril(IntArray a, IntArray out, int rows, int cols, int k) {
        return task("mlx_tril", 1, a, out, rows, cols, k);
    }

    /** {@code a[rows, cols]} with the elements below diagonal k set to 0. */
    public static LibraryTaskDescriptor triu(IntArray a, IntArray out, int rows, int cols, int k) {
        return task("mlx_triu", 1, a, out, rows, cols, k);
    }

    // ---------------------------------------------------------------- Shape
    // MLX shape and layout operations: reshaping, squeezing and expanding, axis permutations,
    // broadcasting, strided views, type conversion and reinterpretation, joining and splitting,
    // repetition, rolling and padding. Results are written row-major into out.

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor reshape(FloatArray x, FloatArray out, int rows, int cols) {
        return task("mlx_reshape", 1, x, out, rows, cols);
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor reshape(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return task("mlx_reshape", 1, x, out, rows, cols);
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor reshape(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return task("mlx_reshape", 1, x, out, rows, cols);
    }

    /** {@code x} ({@code rows * cols} elements) reshaped to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor reshape(IntArray x, IntArray out, int rows, int cols) {
        return task("mlx_reshape", 1, x, out, rows, cols);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    public static LibraryTaskDescriptor flatten(FloatArray x, FloatArray out, int d0, int d1, int d2) {
        return task("mlx_flatten", 1, x, out, d0, d1, d2);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    public static LibraryTaskDescriptor flatten(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2) {
        return task("mlx_flatten", 1, x, out, d0, d1, d2);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    public static LibraryTaskDescriptor flatten(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2) {
        return task("mlx_flatten", 1, x, out, d0, d1, d2);
    }

    /** {@code x [d0, d1, d2]} with axes 1 and 2 flattened into one. */
    public static LibraryTaskDescriptor flatten(IntArray x, IntArray out, int d0, int d1, int d2) {
        return task("mlx_flatten", 1, x, out, d0, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    public static LibraryTaskDescriptor unflatten(FloatArray x, FloatArray out, int d1, int d2) {
        return task("mlx_unflatten", 1, x, out, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    public static LibraryTaskDescriptor unflatten(HalfFloatArray x, HalfFloatArray out, int d1, int d2) {
        return task("mlx_unflatten", 1, x, out, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    public static LibraryTaskDescriptor unflatten(BFloat16Array x, BFloat16Array out, int d1, int d2) {
        return task("mlx_unflatten", 1, x, out, d1, d2);
    }

    /** Flat {@code x} unflattened into {@code [d1, d2]}. */
    public static LibraryTaskDescriptor unflatten(IntArray x, IntArray out, int d1, int d2) {
        return task("mlx_unflatten", 1, x, out, d1, d2);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    public static LibraryTaskDescriptor squeeze(FloatArray x, FloatArray out, int d0, int d1) {
        return task("mlx_squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    public static LibraryTaskDescriptor squeeze(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return task("mlx_squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    public static LibraryTaskDescriptor squeeze(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return task("mlx_squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with every size-1 axis removed. */
    public static LibraryTaskDescriptor squeeze(IntArray x, IntArray out, int d0, int d1) {
        return task("mlx_squeeze", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    public static LibraryTaskDescriptor squeezeAxis(FloatArray x, FloatArray out, int d0, int d1) {
        return task("mlx_squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    public static LibraryTaskDescriptor squeezeAxis(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return task("mlx_squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    public static LibraryTaskDescriptor squeezeAxis(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return task("mlx_squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [d0, 1, d1]} with axis 1 removed. */
    public static LibraryTaskDescriptor squeezeAxis(IntArray x, IntArray out, int d0, int d1) {
        return task("mlx_squeeze_axis", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    public static LibraryTaskDescriptor squeezeAxes(FloatArray x, FloatArray out, int d0, int d1) {
        return task("mlx_squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    public static LibraryTaskDescriptor squeezeAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return task("mlx_squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    public static LibraryTaskDescriptor squeezeAxes(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return task("mlx_squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [1, d0, 1, d1]} with axes 0 and 2 removed. */
    public static LibraryTaskDescriptor squeezeAxes(IntArray x, IntArray out, int d0, int d1) {
        return task("mlx_squeeze_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    public static LibraryTaskDescriptor expandDims(FloatArray x, FloatArray out, int d0, int d1) {
        return task("mlx_expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    public static LibraryTaskDescriptor expandDims(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return task("mlx_expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    public static LibraryTaskDescriptor expandDims(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return task("mlx_expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with a size-1 axis inserted at position 1. */
    public static LibraryTaskDescriptor expandDims(IntArray x, IntArray out, int d0, int d1) {
        return task("mlx_expand_dims", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    public static LibraryTaskDescriptor expandDimsAxes(FloatArray x, FloatArray out, int d0, int d1) {
        return task("mlx_expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    public static LibraryTaskDescriptor expandDimsAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1) {
        return task("mlx_expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    public static LibraryTaskDescriptor expandDimsAxes(BFloat16Array x, BFloat16Array out, int d0, int d1) {
        return task("mlx_expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x [d0, d1]} with size-1 axes inserted at positions 0 and 2. */
    public static LibraryTaskDescriptor expandDimsAxes(IntArray x, IntArray out, int d0, int d1) {
        return task("mlx_expand_dims_axes", 1, x, out, d0, d1);
    }

    /** {@code x} as an array of at least 1 dimension. */
    public static LibraryTaskDescriptor atleast1d(FloatArray x, FloatArray out) {
        return task("mlx_atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 1 dimension. */
    public static LibraryTaskDescriptor atleast1d(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 1 dimension. */
    public static LibraryTaskDescriptor atleast1d(BFloat16Array x, BFloat16Array out) {
        return task("mlx_atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 1 dimension. */
    public static LibraryTaskDescriptor atleast1d(IntArray x, IntArray out) {
        return task("mlx_atleast_1d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    public static LibraryTaskDescriptor atleast2d(FloatArray x, FloatArray out) {
        return task("mlx_atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    public static LibraryTaskDescriptor atleast2d(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    public static LibraryTaskDescriptor atleast2d(BFloat16Array x, BFloat16Array out) {
        return task("mlx_atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 2 dimensions. */
    public static LibraryTaskDescriptor atleast2d(IntArray x, IntArray out) {
        return task("mlx_atleast_2d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    public static LibraryTaskDescriptor atleast3d(FloatArray x, FloatArray out) {
        return task("mlx_atleast_3d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    public static LibraryTaskDescriptor atleast3d(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_atleast_3d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    public static LibraryTaskDescriptor atleast3d(BFloat16Array x, BFloat16Array out) {
        return task("mlx_atleast_3d", 1, x, out);
    }

    /** {@code x} as an array of at least 3 dimensions. */
    public static LibraryTaskDescriptor atleast3d(IntArray x, IntArray out) {
        return task("mlx_atleast_3d", 1, x, out);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    public static LibraryTaskDescriptor transposeAxes(FloatArray x, FloatArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return task("mlx_transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    public static LibraryTaskDescriptor transposeAxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return task("mlx_transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    public static LibraryTaskDescriptor transposeAxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return task("mlx_transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with its axes permuted: output axis k is input axis {@code pk}. */
    public static LibraryTaskDescriptor transposeAxes(IntArray x, IntArray out, int d0, int d1, int d2, int p0, int p1, int p2) {
        return task("mlx_transpose_axes", 1, x, out, d0, d1, d2, p0, p1, p2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    public static LibraryTaskDescriptor swapaxes(FloatArray x, FloatArray out, int d0, int d1, int d2, int axis1, int axis2) {
        return task("mlx_swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    public static LibraryTaskDescriptor swapaxes(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2, int axis1, int axis2) {
        return task("mlx_swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    public static LibraryTaskDescriptor swapaxes(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2, int axis1, int axis2) {
        return task("mlx_swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with two axes swapped. */
    public static LibraryTaskDescriptor swapaxes(IntArray x, IntArray out, int d0, int d1, int d2, int axis1, int axis2) {
        return task("mlx_swapaxes", 1, x, out, d0, d1, d2, axis1, axis2);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    public static LibraryTaskDescriptor moveaxis(FloatArray x, FloatArray out, int d0, int d1, int d2, int source, int destination) {
        return task("mlx_moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    public static LibraryTaskDescriptor moveaxis(HalfFloatArray x, HalfFloatArray out, int d0, int d1, int d2, int source, int destination) {
        return task("mlx_moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    public static LibraryTaskDescriptor moveaxis(BFloat16Array x, BFloat16Array out, int d0, int d1, int d2, int source, int destination) {
        return task("mlx_moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [d0, d1, d2]} with axis {@code source} moved to position {@code destination}. */
    public static LibraryTaskDescriptor moveaxis(IntArray x, IntArray out, int d0, int d1, int d2, int source, int destination) {
        return task("mlx_moveaxis", 1, x, out, d0, d1, d2, source, destination);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastTo(FloatArray x, FloatArray out, int rows, int cols) {
        return task("mlx_broadcast_to", 1, x, out, rows, cols);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastTo(HalfFloatArray x, HalfFloatArray out, int rows, int cols) {
        return task("mlx_broadcast_to", 1, x, out, rows, cols);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastTo(BFloat16Array x, BFloat16Array out, int rows, int cols) {
        return task("mlx_broadcast_to", 1, x, out, rows, cols);
    }

    /** {@code x [cols]} broadcast to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastTo(IntArray x, IntArray out, int rows, int cols) {
        return task("mlx_broadcast_to", 1, x, out, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastArrays(FloatArray a, FloatArray b, FloatArray outA, FloatArray outB, int rows, int cols) {
        return task("mlx_broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastArrays(HalfFloatArray a, HalfFloatArray b, HalfFloatArray outA, HalfFloatArray outB, int rows, int cols) {
        return task("mlx_broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastArrays(BFloat16Array a, BFloat16Array b, BFloat16Array outA, BFloat16Array outB, int rows, int cols) {
        return task("mlx_broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** A row {@code a [1, cols]} and a column {@code b [rows, 1]} broadcast together to {@code [rows, cols]}. */
    public static LibraryTaskDescriptor broadcastArrays(IntArray a, IntArray b, IntArray outA, IntArray outB, int rows, int cols) {
        return task("mlx_broadcast_arrays", new int[] { 2, 3 }, a, b, outA, outB, rows, cols);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    public static LibraryTaskDescriptor asStrided(FloatArray x, FloatArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        return task("mlx_as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    public static LibraryTaskDescriptor asStrided(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        return task("mlx_as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    public static LibraryTaskDescriptor asStrided(BFloat16Array x, BFloat16Array out, int rows, int cols, int rowStride, int colStride, int offset) {
        return task("mlx_as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** {@code out[r, c] = x[offset + r * rowStride + c * colStride]} (strides and offset in elements). */
    public static LibraryTaskDescriptor asStrided(IntArray x, IntArray out, int rows, int cols, int rowStride, int colStride, int offset) {
        return task("mlx_as_strided", 1, x, out, rows, cols, rowStride, colStride, offset);
    }

    /** A row-major copy of {@code x}. */
    public static LibraryTaskDescriptor contiguous(FloatArray x, FloatArray out) {
        return task("mlx_contiguous", 1, x, out);
    }

    /** A row-major copy of {@code x}. */
    public static LibraryTaskDescriptor contiguous(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_contiguous", 1, x, out);
    }

    /** A row-major copy of {@code x}. */
    public static LibraryTaskDescriptor contiguous(BFloat16Array x, BFloat16Array out) {
        return task("mlx_contiguous", 1, x, out);
    }

    /** A row-major copy of {@code x}. */
    public static LibraryTaskDescriptor contiguous(IntArray x, IntArray out) {
        return task("mlx_contiguous", 1, x, out);
    }

    /** A copy of {@code x}. */
    public static LibraryTaskDescriptor copy(FloatArray x, FloatArray out) {
        return task("mlx_copy", 1, x, out);
    }

    /** A copy of {@code x}. */
    public static LibraryTaskDescriptor copy(HalfFloatArray x, HalfFloatArray out) {
        return task("mlx_copy", 1, x, out);
    }

    /** A copy of {@code x}. */
    public static LibraryTaskDescriptor copy(BFloat16Array x, BFloat16Array out) {
        return task("mlx_copy", 1, x, out);
    }

    /** A copy of {@code x}. */
    public static LibraryTaskDescriptor copy(IntArray x, IntArray out) {
        return task("mlx_copy", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    public static LibraryTaskDescriptor astype(FloatArray x, HalfFloatArray out) {
        return task("mlx_astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    public static LibraryTaskDescriptor astype(FloatArray x, BFloat16Array out) {
        return task("mlx_astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    public static LibraryTaskDescriptor astype(FloatArray x, IntArray out) {
        return task("mlx_astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    public static LibraryTaskDescriptor astype(HalfFloatArray x, FloatArray out) {
        return task("mlx_astype", 1, x, out);
    }

    /** {@code x} converted element by element to the type of {@code out}. */
    public static LibraryTaskDescriptor astype(IntArray x, FloatArray out) {
        return task("mlx_astype", 1, x, out);
    }

    /** The bits of {@code x} reinterpreted as the (same-size) type of {@code out}. */
    public static LibraryTaskDescriptor view(FloatArray x, IntArray out) {
        return task("mlx_view", 1, x, out);
    }

    /** The bits of {@code x} reinterpreted as the (same-size) type of {@code out}. */
    public static LibraryTaskDescriptor view(IntArray x, FloatArray out) {
        return task("mlx_view", 1, x, out);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    public static LibraryTaskDescriptor numberOfElements(FloatArray x, IntArray out, int d0, int d1, int d2) {
        return task("mlx_number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    public static LibraryTaskDescriptor numberOfElements(HalfFloatArray x, IntArray out, int d0, int d1, int d2) {
        return task("mlx_number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    public static LibraryTaskDescriptor numberOfElements(BFloat16Array x, IntArray out, int d0, int d1, int d2) {
        return task("mlx_number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out[0]} = the number of elements along axes 1 and 2 of {@code x [d0, d1, d2]}. */
    public static LibraryTaskDescriptor numberOfElements(IntArray x, IntArray out, int d0, int d1, int d2) {
        return task("mlx_number_of_elements", 1, x, out, d0, d1, d2);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    public static LibraryTaskDescriptor concatenate(FloatArray a, FloatArray b, FloatArray out) {
        return task("mlx_concatenate", 2, a, b, out);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    public static LibraryTaskDescriptor concatenate(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return task("mlx_concatenate", 2, a, b, out);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    public static LibraryTaskDescriptor concatenate(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return task("mlx_concatenate", 2, a, b, out);
    }

    /** {@code out} = {@code a} followed by {@code b}. */
    public static LibraryTaskDescriptor concatenate(IntArray a, IntArray b, IntArray out) {
        return task("mlx_concatenate", 2, a, b, out);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    public static LibraryTaskDescriptor concatenateAxis(FloatArray a, FloatArray b, FloatArray out, int rows, int colsA, int colsB) {
        return task("mlx_concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    public static LibraryTaskDescriptor concatenateAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out, int rows, int colsA, int colsB) {
        return task("mlx_concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    public static LibraryTaskDescriptor concatenateAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out, int rows, int colsA, int colsB) {
        return task("mlx_concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a [rows, colsA]} and {@code b [rows, colsB]} joined along axis 1. */
    public static LibraryTaskDescriptor concatenateAxis(IntArray a, IntArray b, IntArray out, int rows, int colsA, int colsB) {
        return task("mlx_concatenate_axis", 2, a, b, out, rows, colsA, colsB);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    public static LibraryTaskDescriptor stack(FloatArray a, FloatArray b, FloatArray out) {
        return task("mlx_stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    public static LibraryTaskDescriptor stack(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return task("mlx_stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    public static LibraryTaskDescriptor stack(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return task("mlx_stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new first axis ({@code out [2, n]}). */
    public static LibraryTaskDescriptor stack(IntArray a, IntArray b, IntArray out) {
        return task("mlx_stack", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    public static LibraryTaskDescriptor stackAxis(FloatArray a, FloatArray b, FloatArray out) {
        return task("mlx_stack_axis", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    public static LibraryTaskDescriptor stackAxis(HalfFloatArray a, HalfFloatArray b, HalfFloatArray out) {
        return task("mlx_stack_axis", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    public static LibraryTaskDescriptor stackAxis(BFloat16Array a, BFloat16Array b, BFloat16Array out) {
        return task("mlx_stack_axis", 2, a, b, out);
    }

    /** {@code a} and {@code b} stacked along a new last axis ({@code out [n, 2]}). */
    public static LibraryTaskDescriptor stackAxis(IntArray a, IntArray b, IntArray out) {
        return task("mlx_stack_axis", 2, a, b, out);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    public static LibraryTaskDescriptor split(FloatArray x, FloatArray first, FloatArray second, int rows, int cols) {
        return task("mlx_split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    public static LibraryTaskDescriptor split(HalfFloatArray x, HalfFloatArray first, HalfFloatArray second, int rows, int cols) {
        return task("mlx_split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    public static LibraryTaskDescriptor split(BFloat16Array x, BFloat16Array first, BFloat16Array second, int rows, int cols) {
        return task("mlx_split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split in two equal halves along axis 1. */
    public static LibraryTaskDescriptor split(IntArray x, IntArray first, IntArray second, int rows, int cols) {
        return task("mlx_split", new int[] { 1, 2 }, x, first, second, rows, cols);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    public static LibraryTaskDescriptor splitSections(FloatArray x, FloatArray first, FloatArray second, int rows, int cols, int index) {
        return task("mlx_split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    public static LibraryTaskDescriptor splitSections(HalfFloatArray x, HalfFloatArray first, HalfFloatArray second, int rows, int cols, int index) {
        return task("mlx_split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    public static LibraryTaskDescriptor splitSections(BFloat16Array x, BFloat16Array first, BFloat16Array second, int rows, int cols, int index) {
        return task("mlx_split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** {@code x [rows, cols]} split along axis 1 at column {@code index}. */
    public static LibraryTaskDescriptor splitSections(IntArray x, IntArray first, IntArray second, int rows, int cols, int index) {
        return task("mlx_split_sections", new int[] { 1, 2 }, x, first, second, rows, cols, index);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeat(FloatArray x, FloatArray out, int times) {
        return task("mlx_repeat", 1, x, out, times);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeat(HalfFloatArray x, HalfFloatArray out, int times) {
        return task("mlx_repeat", 1, x, out, times);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeat(BFloat16Array x, BFloat16Array out, int times) {
        return task("mlx_repeat", 1, x, out, times);
    }

    /** Each element of flat {@code x} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeat(IntArray x, IntArray out, int times) {
        return task("mlx_repeat", 1, x, out, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeatAxis(FloatArray x, FloatArray out, int rows, int cols, int times) {
        return task("mlx_repeat_axis", 1, x, out, rows, cols, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeatAxis(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int times) {
        return task("mlx_repeat_axis", 1, x, out, rows, cols, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeatAxis(BFloat16Array x, BFloat16Array out, int rows, int cols, int times) {
        return task("mlx_repeat_axis", 1, x, out, rows, cols, times);
    }

    /** Each row of {@code x [rows, cols]} repeated {@code times} times. */
    public static LibraryTaskDescriptor repeatAxis(IntArray x, IntArray out, int rows, int cols, int times) {
        return task("mlx_repeat_axis", 1, x, out, rows, cols, times);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    public static LibraryTaskDescriptor tile(FloatArray x, FloatArray out, int rows, int cols, int repsRows, int repsCols) {
        return task("mlx_tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    public static LibraryTaskDescriptor tile(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int repsRows, int repsCols) {
        return task("mlx_tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    public static LibraryTaskDescriptor tile(BFloat16Array x, BFloat16Array out, int rows, int cols, int repsRows, int repsCols) {
        return task("mlx_tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** {@code x [rows, cols]} tiled {@code repsRows} by {@code repsCols} times. */
    public static LibraryTaskDescriptor tile(IntArray x, IntArray out, int rows, int cols, int repsRows, int repsCols) {
        return task("mlx_tile", 1, x, out, rows, cols, repsRows, repsCols);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    public static LibraryTaskDescriptor roll(FloatArray x, FloatArray out, int shift) {
        return task("mlx_roll", 1, x, out, shift);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    public static LibraryTaskDescriptor roll(HalfFloatArray x, HalfFloatArray out, int shift) {
        return task("mlx_roll", 1, x, out, shift);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    public static LibraryTaskDescriptor roll(BFloat16Array x, BFloat16Array out, int shift) {
        return task("mlx_roll", 1, x, out, shift);
    }

    /** Flat {@code x} rolled by {@code shift} (elements move to higher indices, wrapping around). */
    public static LibraryTaskDescriptor roll(IntArray x, IntArray out, int shift) {
        return task("mlx_roll", 1, x, out, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    public static LibraryTaskDescriptor rollAxis(FloatArray x, FloatArray out, int rows, int cols, int shift) {
        return task("mlx_roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    public static LibraryTaskDescriptor rollAxis(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int shift) {
        return task("mlx_roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    public static LibraryTaskDescriptor rollAxis(BFloat16Array x, BFloat16Array out, int rows, int cols, int shift) {
        return task("mlx_roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shift} along axis 1. */
    public static LibraryTaskDescriptor rollAxis(IntArray x, IntArray out, int rows, int cols, int shift) {
        return task("mlx_roll_axis", 1, x, out, rows, cols, shift);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    public static LibraryTaskDescriptor rollAxes(FloatArray x, FloatArray out, int rows, int cols, int shiftRows, int shiftCols) {
        return task("mlx_roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    public static LibraryTaskDescriptor rollAxes(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int shiftRows, int shiftCols) {
        return task("mlx_roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    public static LibraryTaskDescriptor rollAxes(BFloat16Array x, BFloat16Array out, int rows, int cols, int shiftRows, int shiftCols) {
        return task("mlx_roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} rolled by {@code shiftRows} along axis 0 and {@code shiftCols} along axis 1. */
    public static LibraryTaskDescriptor rollAxes(IntArray x, IntArray out, int rows, int cols, int shiftRows, int shiftCols) {
        return task("mlx_roll_axes", 1, x, out, rows, cols, shiftRows, shiftCols);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    public static LibraryTaskDescriptor pad(FloatArray x, FloatArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return task("mlx_pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    public static LibraryTaskDescriptor pad(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return task("mlx_pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    public static LibraryTaskDescriptor pad(BFloat16Array x, BFloat16Array out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return task("mlx_pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value}: {@code top}/{@code bottom} rows and {@code left}/{@code right} columns. */
    public static LibraryTaskDescriptor pad(IntArray x, IntArray out, int rows, int cols, int top, int bottom, int left, int right, float value) {
        return task("mlx_pad", 1, x, out, rows, cols, top, bottom, left, right, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    public static LibraryTaskDescriptor padSymmetric(FloatArray x, FloatArray out, int rows, int cols, int width, float value) {
        return task("mlx_pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    public static LibraryTaskDescriptor padSymmetric(HalfFloatArray x, HalfFloatArray out, int rows, int cols, int width, float value) {
        return task("mlx_pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    public static LibraryTaskDescriptor padSymmetric(BFloat16Array x, BFloat16Array out, int rows, int cols, int width, float value) {
        return task("mlx_pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    /** {@code x [rows, cols]} padded with {@code value} by {@code width} on every side. */
    public static LibraryTaskDescriptor padSymmetric(IntArray x, IntArray out, int rows, int cols, int width, float value) {
        return task("mlx_pad_symmetric", 1, x, out, rows, cols, width, value);
    }

    // ---------------------------------------------------------------- Random
    // MLX random sampling. Each call draws from an MLX key made from seed, so a given seed always gives
    // the same values; the size of out sets the number of samples.

    /** Random 32-bit words. */
    public static LibraryTaskDescriptor bits(IntArray out, int seed) {
        return task("mlx_random_bits", 0, out, seed);
    }

    /** Values uniform in {@code [low, high)}. */
    public static LibraryTaskDescriptor uniform(FloatArray out, float low, float high, int seed) {
        return task("mlx_random_uniform", 0, out, low, high, seed);
    }

    /** Values uniform in {@code [low, high)}. */
    public static LibraryTaskDescriptor uniform(HalfFloatArray out, float low, float high, int seed) {
        return task("mlx_random_uniform", 0, out, low, high, seed);
    }

    /** Values uniform in {@code [low, high)}. */
    public static LibraryTaskDescriptor uniform(BFloat16Array out, float low, float high, int seed) {
        return task("mlx_random_uniform", 0, out, low, high, seed);
    }

    /** Normal values with mean {@code loc} and standard deviation {@code scale}. */
    public static LibraryTaskDescriptor normal(FloatArray out, float loc, float scale, int seed) {
        return task("mlx_random_normal", 0, out, loc, scale, seed);
    }

    /** Normal values with mean {@code loc} and standard deviation {@code scale}. */
    public static LibraryTaskDescriptor normal(HalfFloatArray out, float loc, float scale, int seed) {
        return task("mlx_random_normal", 0, out, loc, scale, seed);
    }

    /** Normal values with mean {@code loc} and standard deviation {@code scale}. */
    public static LibraryTaskDescriptor normal(BFloat16Array out, float loc, float scale, int seed) {
        return task("mlx_random_normal", 0, out, loc, scale, seed);
    }

    /** Normal values with a mean and standard deviation per element. */
    public static LibraryTaskDescriptor normalBroadcast(FloatArray loc, FloatArray scale, FloatArray out, int seed) {
        return task("mlx_random_normal_broadcast", 2, loc, scale, out, seed);
    }

    /** Normal values with a mean and standard deviation per element. */
    public static LibraryTaskDescriptor normalBroadcast(HalfFloatArray loc, HalfFloatArray scale, HalfFloatArray out, int seed) {
        return task("mlx_random_normal_broadcast", 2, loc, scale, out, seed);
    }

    /** Normal values with a mean and standard deviation per element. */
    public static LibraryTaskDescriptor normalBroadcast(BFloat16Array loc, BFloat16Array scale, BFloat16Array out, int seed) {
        return task("mlx_random_normal_broadcast", 2, loc, scale, out, seed);
    }

    /** {@code out[i]} = 1 with probability {@code p[i]}, else 0. */
    public static LibraryTaskDescriptor bernoulli(FloatArray p, ByteArray out, int seed) {
        return task("mlx_random_bernoulli", 1, p, out, seed);
    }

    /** {@code out[i]} = 1 with probability {@code p[i]}, else 0. */
    public static LibraryTaskDescriptor bernoulli(HalfFloatArray p, ByteArray out, int seed) {
        return task("mlx_random_bernoulli", 1, p, out, seed);
    }

    /** {@code out[i]} = 1 with probability {@code p[i]}, else 0. */
    public static LibraryTaskDescriptor bernoulli(BFloat16Array p, ByteArray out, int seed) {
        return task("mlx_random_bernoulli", 1, p, out, seed);
    }

    /** Integers uniform in {@code [low, high)}. */
    public static LibraryTaskDescriptor randint(IntArray out, int low, int high, int seed) {
        return task("mlx_random_randint", 0, out, low, high, seed);
    }

    /** Standard normal values restricted to {@code [lower, upper]}. */
    public static LibraryTaskDescriptor truncatedNormal(FloatArray out, float lower, float upper, int seed) {
        return task("mlx_random_truncated_normal", 0, out, lower, upper, seed);
    }

    /** Standard normal values restricted to {@code [lower, upper]}. */
    public static LibraryTaskDescriptor truncatedNormal(HalfFloatArray out, float lower, float upper, int seed) {
        return task("mlx_random_truncated_normal", 0, out, lower, upper, seed);
    }

    /** Standard normal values restricted to {@code [lower, upper]}. */
    public static LibraryTaskDescriptor truncatedNormal(BFloat16Array out, float lower, float upper, int seed) {
        return task("mlx_random_truncated_normal", 0, out, lower, upper, seed);
    }

    /** Values from the standard Gumbel distribution. */
    public static LibraryTaskDescriptor gumbel(FloatArray out, int seed) {
        return task("mlx_random_gumbel", 0, out, seed);
    }

    /** Values from the standard Gumbel distribution. */
    public static LibraryTaskDescriptor gumbel(HalfFloatArray out, int seed) {
        return task("mlx_random_gumbel", 0, out, seed);
    }

    /** Values from the standard Gumbel distribution. */
    public static LibraryTaskDescriptor gumbel(BFloat16Array out, int seed) {
        return task("mlx_random_gumbel", 0, out, seed);
    }

    /** Values from the Laplace distribution with location {@code loc} and scale {@code scale}. */
    public static LibraryTaskDescriptor laplace(FloatArray out, float loc, float scale, int seed) {
        return task("mlx_random_laplace", 0, out, loc, scale, seed);
    }

    /** Values from the Laplace distribution with location {@code loc} and scale {@code scale}. */
    public static LibraryTaskDescriptor laplace(HalfFloatArray out, float loc, float scale, int seed) {
        return task("mlx_random_laplace", 0, out, loc, scale, seed);
    }

    /** Values from the Laplace distribution with location {@code loc} and scale {@code scale}. */
    public static LibraryTaskDescriptor laplace(BFloat16Array out, float loc, float scale, int seed) {
        return task("mlx_random_laplace", 0, out, loc, scale, seed);
    }

    /** One class index per row of {@code logits[rows, classes]}, drawn from the row's softmax. */
    public static LibraryTaskDescriptor categorical(FloatArray logits, IntArray out, int rows, int classes, int seed) {
        return task("mlx_random_categorical", 1, logits, out, rows, classes, seed);
    }

    /** One class index per row of {@code logits[rows, classes]}, drawn from the row's softmax. */
    public static LibraryTaskDescriptor categorical(HalfFloatArray logits, IntArray out, int rows, int classes, int seed) {
        return task("mlx_random_categorical", 1, logits, out, rows, classes, seed);
    }

    /** One class index per row of {@code logits[rows, classes]}, drawn from the row's softmax. */
    public static LibraryTaskDescriptor categorical(BFloat16Array logits, IntArray out, int rows, int classes, int seed) {
        return task("mlx_random_categorical", 1, logits, out, rows, classes, seed);
    }

    /** {@code samples} class indices per row of {@code logits[rows, classes]}: {@code out[rows, samples]}. */
    public static LibraryTaskDescriptor categoricalSamples(FloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return task("mlx_random_categorical_num_samples", 1, logits, out, rows, classes, samples, seed);
    }

    /** {@code samples} class indices per row of {@code logits[rows, classes]}: {@code out[rows, samples]}. */
    public static LibraryTaskDescriptor categoricalSamples(HalfFloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return task("mlx_random_categorical_num_samples", 1, logits, out, rows, classes, samples, seed);
    }

    /** {@code samples} class indices per row of {@code logits[rows, classes]}: {@code out[rows, samples]}. */
    public static LibraryTaskDescriptor categoricalSamples(BFloat16Array logits, IntArray out, int rows, int classes, int samples, int seed) {
        return task("mlx_random_categorical_num_samples", 1, logits, out, rows, classes, samples, seed);
    }

    /** Class indices drawn for an output of shape {@code [samples, rows]} from each row of {@code logits[rows, classes]}. */
    public static LibraryTaskDescriptor categoricalShape(FloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return task("mlx_random_categorical_shape", 1, logits, out, rows, classes, samples, seed);
    }

    /** Class indices drawn for an output of shape {@code [samples, rows]} from each row of {@code logits[rows, classes]}. */
    public static LibraryTaskDescriptor categoricalShape(HalfFloatArray logits, IntArray out, int rows, int classes, int samples, int seed) {
        return task("mlx_random_categorical_shape", 1, logits, out, rows, classes, samples, seed);
    }

    /** Class indices drawn for an output of shape {@code [samples, rows]} from each row of {@code logits[rows, classes]}. */
    public static LibraryTaskDescriptor categoricalShape(BFloat16Array logits, IntArray out, int rows, int classes, int samples, int seed) {
        return task("mlx_random_categorical_shape", 1, logits, out, rows, classes, samples, seed);
    }

    /** A random permutation of {@code 0 .. out.length - 1}. */
    public static LibraryTaskDescriptor permutationArange(IntArray out, int seed) {
        return task("mlx_random_permutation_arange", 0, out, seed);
    }
}
