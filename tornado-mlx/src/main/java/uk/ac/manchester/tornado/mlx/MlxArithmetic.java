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
 * MLX element-wise arithmetic and math as TornadoVM library tasks: binary operators, unary functions
 * (exponentials, logarithms, roots, trigonometric and hyperbolic functions), rounding, integer-style
 * division, clipping, selection, NaN replacement and complex parts. All operands of one call have the
 * same length; complex arrays are float arrays of (real, imaginary) pairs.
 */
public final class MlxArithmetic {

    private MlxArithmetic() {
    }

    /** Element-wise {@code c = a + b}. */
    public static LibraryTaskDescriptor add(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_add", 2, a, b, c);
    }

    /** Element-wise {@code c = a + b}. */
    public static LibraryTaskDescriptor add(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_add", 2, a, b, c);
    }

    /** Element-wise {@code c = a + b}. */
    public static LibraryTaskDescriptor add(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_add", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    public static LibraryTaskDescriptor subtract(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    public static LibraryTaskDescriptor subtract(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b}. */
    public static LibraryTaskDescriptor subtract(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_subtract", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    public static LibraryTaskDescriptor multiply(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    public static LibraryTaskDescriptor multiply(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a * b}. */
    public static LibraryTaskDescriptor multiply(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_multiply", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    public static LibraryTaskDescriptor divide(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    public static LibraryTaskDescriptor divide(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = a / b}. */
    public static LibraryTaskDescriptor divide(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    public static LibraryTaskDescriptor maximum(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    public static LibraryTaskDescriptor maximum(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = max(a, b)}. */
    public static LibraryTaskDescriptor maximum(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_maximum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    public static LibraryTaskDescriptor minimum(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_minimum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    public static LibraryTaskDescriptor minimum(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_minimum", 2, a, b, c);
    }

    /** Element-wise {@code c = min(a, b)}. */
    public static LibraryTaskDescriptor minimum(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_minimum", 2, a, b, c);
    }

    /** Element-wise {@code out = -a}. */
    public static LibraryTaskDescriptor negative(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_negative", 1, a, out);
    }

    /** Element-wise {@code out = -a}. */
    public static LibraryTaskDescriptor negative(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_negative", 1, a, out);
    }

    /** Element-wise {@code out = -a}. */
    public static LibraryTaskDescriptor negative(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_negative", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    public static LibraryTaskDescriptor exp(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_exp", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    public static LibraryTaskDescriptor exp(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_exp", 1, a, out);
    }

    /** Element-wise {@code out = exp(a)}. */
    public static LibraryTaskDescriptor exp(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_exp", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    public static LibraryTaskDescriptor tanh(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_tanh", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    public static LibraryTaskDescriptor tanh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_tanh", 1, a, out);
    }

    /** Element-wise {@code out = tanh(a)}. */
    public static LibraryTaskDescriptor tanh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_tanh", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    public static LibraryTaskDescriptor erf(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_erf", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    public static LibraryTaskDescriptor erf(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_erf", 1, a, out);
    }

    /** Element-wise {@code out = erf(a)}. */
    public static LibraryTaskDescriptor erf(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_erf", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    public static LibraryTaskDescriptor sigmoid(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    public static LibraryTaskDescriptor sigmoid(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = 1 / (1 + exp(-a))}. */
    public static LibraryTaskDescriptor sigmoid(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_sigmoid", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    public static LibraryTaskDescriptor sqrt(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_sqrt", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    public static LibraryTaskDescriptor sqrt(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_sqrt", 1, a, out);
    }

    /** Element-wise {@code out = sqrt(a)}. */
    public static LibraryTaskDescriptor sqrt(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_sqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    public static LibraryTaskDescriptor rsqrt(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    public static LibraryTaskDescriptor rsqrt(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = 1 / sqrt(a)}. */
    public static LibraryTaskDescriptor rsqrt(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_rsqrt", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    public static LibraryTaskDescriptor square(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_square", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    public static LibraryTaskDescriptor square(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_square", 1, a, out);
    }

    /** Element-wise {@code out = a * a}. */
    public static LibraryTaskDescriptor square(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_square", 1, a, out);
    }

    /** {@code out} = {@code a} with NaN replaced by {@code nan}, +infinity by {@code posinf} and -infinity by {@code neginf}. */
    public static LibraryTaskDescriptor nanToNum(FloatArray a, FloatArray out, float nan, float posinf, float neginf) {
        return Mlx.task("mlx_nan_to_num", 1, a, out, nan, posinf, neginf);
    }

    /** {@code out} = {@code a} with NaN replaced by {@code nan}, +infinity by {@code posinf} and -infinity by {@code neginf}. */
    public static LibraryTaskDescriptor nanToNum(HalfFloatArray a, HalfFloatArray out, float nan, float posinf, float neginf) {
        return Mlx.task("mlx_nan_to_num", 1, a, out, nan, posinf, neginf);
    }

    /** {@code out} = {@code a} with NaN replaced by {@code nan}, +infinity by {@code posinf} and -infinity by {@code neginf}. */
    public static LibraryTaskDescriptor nanToNum(BFloat16Array a, BFloat16Array out, float nan, float posinf, float neginf) {
        return Mlx.task("mlx_nan_to_num", 1, a, out, nan, posinf, neginf);
    }

    /** {@code out[i]} = the real part of complex {@code z[i]} ({@code z} holds (real, imaginary) pairs). */
    public static LibraryTaskDescriptor real(FloatArray z, FloatArray out) {
        return Mlx.task("mlx_real", 1, z, out);
    }

    /** {@code out[i]} = the imaginary part of complex {@code z[i]}. */
    public static LibraryTaskDescriptor imag(FloatArray z, FloatArray out) {
        return Mlx.task("mlx_imag", 1, z, out);
    }

    /** {@code out[i]} = the complex conjugate of {@code z[i]} (both as (real, imaginary) pairs). */
    public static LibraryTaskDescriptor conjugate(FloatArray z, FloatArray out) {
        return Mlx.task("mlx_conjugate", 1, z, out);
    }

    /** Element-wise {@code out = |a|}. */
    public static LibraryTaskDescriptor abs(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_abs", 1, a, out);
    }

    /** Element-wise {@code out = |a|}. */
    public static LibraryTaskDescriptor abs(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_abs", 1, a, out);
    }

    /** Element-wise {@code out = |a|}. */
    public static LibraryTaskDescriptor abs(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_abs", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    public static LibraryTaskDescriptor arccos(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    public static LibraryTaskDescriptor arccos(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    public static LibraryTaskDescriptor arccos(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    public static LibraryTaskDescriptor arccosh(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    public static LibraryTaskDescriptor arccosh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    public static LibraryTaskDescriptor arccosh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    public static LibraryTaskDescriptor arcsin(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    public static LibraryTaskDescriptor arcsin(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    public static LibraryTaskDescriptor arcsin(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    public static LibraryTaskDescriptor arcsinh(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    public static LibraryTaskDescriptor arcsinh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    public static LibraryTaskDescriptor arcsinh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    public static LibraryTaskDescriptor arctan(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    public static LibraryTaskDescriptor arctan(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    public static LibraryTaskDescriptor arctan(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    public static LibraryTaskDescriptor arctanh(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_arctanh", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    public static LibraryTaskDescriptor arctanh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_arctanh", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    public static LibraryTaskDescriptor arctanh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_arctanh", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    public static LibraryTaskDescriptor ceil(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_ceil", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    public static LibraryTaskDescriptor ceil(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_ceil", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    public static LibraryTaskDescriptor ceil(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_ceil", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    public static LibraryTaskDescriptor cos(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_cos", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    public static LibraryTaskDescriptor cos(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_cos", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    public static LibraryTaskDescriptor cos(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_cos", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    public static LibraryTaskDescriptor cosh(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_cosh", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    public static LibraryTaskDescriptor cosh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_cosh", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    public static LibraryTaskDescriptor cosh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_cosh", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    public static LibraryTaskDescriptor degrees(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_degrees", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    public static LibraryTaskDescriptor degrees(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_degrees", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    public static LibraryTaskDescriptor degrees(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_degrees", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    public static LibraryTaskDescriptor erfinv(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_erfinv", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    public static LibraryTaskDescriptor erfinv(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_erfinv", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    public static LibraryTaskDescriptor erfinv(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_erfinv", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    public static LibraryTaskDescriptor expm1(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_expm1", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    public static LibraryTaskDescriptor expm1(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_expm1", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    public static LibraryTaskDescriptor expm1(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_expm1", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    public static LibraryTaskDescriptor floor(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_floor", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    public static LibraryTaskDescriptor floor(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_floor", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    public static LibraryTaskDescriptor floor(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_floor", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    public static LibraryTaskDescriptor log(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_log", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    public static LibraryTaskDescriptor log(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_log", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    public static LibraryTaskDescriptor log(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_log", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    public static LibraryTaskDescriptor log10(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_log10", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    public static LibraryTaskDescriptor log10(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_log10", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    public static LibraryTaskDescriptor log10(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_log10", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    public static LibraryTaskDescriptor log1p(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_log1p", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    public static LibraryTaskDescriptor log1p(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_log1p", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    public static LibraryTaskDescriptor log1p(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_log1p", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    public static LibraryTaskDescriptor log2(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_log2", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    public static LibraryTaskDescriptor log2(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_log2", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    public static LibraryTaskDescriptor log2(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_log2", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    public static LibraryTaskDescriptor radians(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_radians", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    public static LibraryTaskDescriptor radians(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_radians", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    public static LibraryTaskDescriptor radians(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_radians", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    public static LibraryTaskDescriptor reciprocal(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    public static LibraryTaskDescriptor reciprocal(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    public static LibraryTaskDescriptor reciprocal(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    public static LibraryTaskDescriptor sign(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_sign", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    public static LibraryTaskDescriptor sign(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_sign", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    public static LibraryTaskDescriptor sign(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_sign", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    public static LibraryTaskDescriptor sin(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_sin", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    public static LibraryTaskDescriptor sin(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_sin", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    public static LibraryTaskDescriptor sin(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_sin", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    public static LibraryTaskDescriptor sinh(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_sinh", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    public static LibraryTaskDescriptor sinh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_sinh", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    public static LibraryTaskDescriptor sinh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_sinh", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    public static LibraryTaskDescriptor tan(FloatArray a, FloatArray out) {
        return Mlx.task("mlx_tan", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    public static LibraryTaskDescriptor tan(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("mlx_tan", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    public static LibraryTaskDescriptor tan(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("mlx_tan", 1, a, out);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    public static LibraryTaskDescriptor round(FloatArray a, FloatArray out, int decimals) {
        return Mlx.task("mlx_round", 1, a, out, decimals);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    public static LibraryTaskDescriptor round(HalfFloatArray a, HalfFloatArray out, int decimals) {
        return Mlx.task("mlx_round", 1, a, out, decimals);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    public static LibraryTaskDescriptor round(BFloat16Array a, BFloat16Array out, int decimals) {
        return Mlx.task("mlx_round", 1, a, out, decimals);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    public static LibraryTaskDescriptor arctan2(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    public static LibraryTaskDescriptor arctan2(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    public static LibraryTaskDescriptor arctan2(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    public static LibraryTaskDescriptor floorDivide(IntArray a, IntArray b, IntArray c) {
        return Mlx.task("mlx_floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    public static LibraryTaskDescriptor logaddexp(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    public static LibraryTaskDescriptor logaddexp(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    public static LibraryTaskDescriptor logaddexp(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    public static LibraryTaskDescriptor power(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_power", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    public static LibraryTaskDescriptor power(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_power", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    public static LibraryTaskDescriptor power(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_power", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    public static LibraryTaskDescriptor remainder(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("mlx_remainder", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    public static LibraryTaskDescriptor remainder(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("mlx_remainder", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    public static LibraryTaskDescriptor remainder(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("mlx_remainder", 2, a, b, c);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(FloatArray a, FloatArray b, FloatArray quotient, FloatArray remainder) {
        return Mlx.task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(HalfFloatArray a, HalfFloatArray b, HalfFloatArray quotient, HalfFloatArray remainder) {
        return Mlx.task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(BFloat16Array a, BFloat16Array b, BFloat16Array quotient, BFloat16Array remainder) {
        return Mlx.task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    public static LibraryTaskDescriptor divmod(IntArray a, IntArray b, IntArray quotient, IntArray remainder) {
        return Mlx.task("mlx_divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(FloatArray a, FloatArray out, float lo, float hi) {
        return Mlx.task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(HalfFloatArray a, HalfFloatArray out, float lo, float hi) {
        return Mlx.task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(BFloat16Array a, BFloat16Array out, float lo, float hi) {
        return Mlx.task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    public static LibraryTaskDescriptor clip(IntArray a, IntArray out, int lo, int hi) {
        return Mlx.task("mlx_clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, FloatArray x, FloatArray y, FloatArray out) {
        return Mlx.task("mlx_where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, HalfFloatArray x, HalfFloatArray y, HalfFloatArray out) {
        return Mlx.task("mlx_where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, BFloat16Array x, BFloat16Array y, BFloat16Array out) {
        return Mlx.task("mlx_where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    public static LibraryTaskDescriptor where(ByteArray condition, IntArray x, IntArray y, IntArray out) {
        return Mlx.task("mlx_where", 3, condition, x, y, out);
    }
}
