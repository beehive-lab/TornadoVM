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
 * MLX element-wise math as TornadoVM library tasks: trigonometric and hyperbolic
 * functions, logarithms, rounding, integer-style division, clipping and selection. All operands of
 * one call have the same length; the arguments and access rules follow {@link Mlx}.
 */
public final class MlxMath {

    private MlxMath() {
    }

    /** Element-wise {@code out = |a|}. */
    @MlxOp("mlx_abs")
    public static LibraryTaskDescriptor abs(FloatArray a, FloatArray out) {
        return Mlx.task("abs", 1, a, out);
    }

    /** Element-wise {@code out = |a|}. */
    @MlxOp("mlx_abs")
    public static LibraryTaskDescriptor abs(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("abs", 1, a, out);
    }

    /** Element-wise {@code out = |a|}. */
    @MlxOp("mlx_abs")
    public static LibraryTaskDescriptor abs(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("abs", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    @MlxOp("mlx_arccos")
    public static LibraryTaskDescriptor arccos(FloatArray a, FloatArray out) {
        return Mlx.task("arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    @MlxOp("mlx_arccos")
    public static LibraryTaskDescriptor arccos(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccos(a)}. */
    @MlxOp("mlx_arccos")
    public static LibraryTaskDescriptor arccos(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("arccos", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    @MlxOp("mlx_arccosh")
    public static LibraryTaskDescriptor arccosh(FloatArray a, FloatArray out) {
        return Mlx.task("arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    @MlxOp("mlx_arccosh")
    public static LibraryTaskDescriptor arccosh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arccosh(a)}. */
    @MlxOp("mlx_arccosh")
    public static LibraryTaskDescriptor arccosh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("arccosh", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    @MlxOp("mlx_arcsin")
    public static LibraryTaskDescriptor arcsin(FloatArray a, FloatArray out) {
        return Mlx.task("arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    @MlxOp("mlx_arcsin")
    public static LibraryTaskDescriptor arcsin(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsin(a)}. */
    @MlxOp("mlx_arcsin")
    public static LibraryTaskDescriptor arcsin(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("arcsin", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    @MlxOp("mlx_arcsinh")
    public static LibraryTaskDescriptor arcsinh(FloatArray a, FloatArray out) {
        return Mlx.task("arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    @MlxOp("mlx_arcsinh")
    public static LibraryTaskDescriptor arcsinh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arcsinh(a)}. */
    @MlxOp("mlx_arcsinh")
    public static LibraryTaskDescriptor arcsinh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("arcsinh", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    @MlxOp("mlx_arctan")
    public static LibraryTaskDescriptor arctan(FloatArray a, FloatArray out) {
        return Mlx.task("arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    @MlxOp("mlx_arctan")
    public static LibraryTaskDescriptor arctan(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctan(a)}. */
    @MlxOp("mlx_arctan")
    public static LibraryTaskDescriptor arctan(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("arctan", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    @MlxOp("mlx_arctanh")
    public static LibraryTaskDescriptor arctanh(FloatArray a, FloatArray out) {
        return Mlx.task("arctanh", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    @MlxOp("mlx_arctanh")
    public static LibraryTaskDescriptor arctanh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("arctanh", 1, a, out);
    }

    /** Element-wise {@code out = arctanh(a)}. */
    @MlxOp("mlx_arctanh")
    public static LibraryTaskDescriptor arctanh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("arctanh", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    @MlxOp("mlx_ceil")
    public static LibraryTaskDescriptor ceil(FloatArray a, FloatArray out) {
        return Mlx.task("ceil", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    @MlxOp("mlx_ceil")
    public static LibraryTaskDescriptor ceil(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("ceil", 1, a, out);
    }

    /** Element-wise {@code out = ceil(a)}. */
    @MlxOp("mlx_ceil")
    public static LibraryTaskDescriptor ceil(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("ceil", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    @MlxOp("mlx_cos")
    public static LibraryTaskDescriptor cos(FloatArray a, FloatArray out) {
        return Mlx.task("cos", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    @MlxOp("mlx_cos")
    public static LibraryTaskDescriptor cos(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("cos", 1, a, out);
    }

    /** Element-wise {@code out = cos(a)}. */
    @MlxOp("mlx_cos")
    public static LibraryTaskDescriptor cos(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("cos", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    @MlxOp("mlx_cosh")
    public static LibraryTaskDescriptor cosh(FloatArray a, FloatArray out) {
        return Mlx.task("cosh", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    @MlxOp("mlx_cosh")
    public static LibraryTaskDescriptor cosh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("cosh", 1, a, out);
    }

    /** Element-wise {@code out = cosh(a)}. */
    @MlxOp("mlx_cosh")
    public static LibraryTaskDescriptor cosh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("cosh", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    @MlxOp("mlx_degrees")
    public static LibraryTaskDescriptor degrees(FloatArray a, FloatArray out) {
        return Mlx.task("degrees", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    @MlxOp("mlx_degrees")
    public static LibraryTaskDescriptor degrees(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("degrees", 1, a, out);
    }

    /** Element-wise {@code out = a * 180 / pi}. */
    @MlxOp("mlx_degrees")
    public static LibraryTaskDescriptor degrees(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("degrees", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    @MlxOp("mlx_erfinv")
    public static LibraryTaskDescriptor erfinv(FloatArray a, FloatArray out) {
        return Mlx.task("erfinv", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    @MlxOp("mlx_erfinv")
    public static LibraryTaskDescriptor erfinv(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("erfinv", 1, a, out);
    }

    /** Element-wise {@code out = erf^-1(a)}. */
    @MlxOp("mlx_erfinv")
    public static LibraryTaskDescriptor erfinv(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("erfinv", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    @MlxOp("mlx_expm1")
    public static LibraryTaskDescriptor expm1(FloatArray a, FloatArray out) {
        return Mlx.task("expm1", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    @MlxOp("mlx_expm1")
    public static LibraryTaskDescriptor expm1(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("expm1", 1, a, out);
    }

    /** Element-wise {@code out = exp(a) - 1}. */
    @MlxOp("mlx_expm1")
    public static LibraryTaskDescriptor expm1(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("expm1", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    @MlxOp("mlx_floor")
    public static LibraryTaskDescriptor floor(FloatArray a, FloatArray out) {
        return Mlx.task("floor", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    @MlxOp("mlx_floor")
    public static LibraryTaskDescriptor floor(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("floor", 1, a, out);
    }

    /** Element-wise {@code out = floor(a)}. */
    @MlxOp("mlx_floor")
    public static LibraryTaskDescriptor floor(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("floor", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    @MlxOp("mlx_log")
    public static LibraryTaskDescriptor log(FloatArray a, FloatArray out) {
        return Mlx.task("log", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    @MlxOp("mlx_log")
    public static LibraryTaskDescriptor log(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("log", 1, a, out);
    }

    /** Element-wise {@code out = ln(a)}. */
    @MlxOp("mlx_log")
    public static LibraryTaskDescriptor log(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("log", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    @MlxOp("mlx_log10")
    public static LibraryTaskDescriptor log10(FloatArray a, FloatArray out) {
        return Mlx.task("log10", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    @MlxOp("mlx_log10")
    public static LibraryTaskDescriptor log10(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("log10", 1, a, out);
    }

    /** Element-wise {@code out = log10(a)}. */
    @MlxOp("mlx_log10")
    public static LibraryTaskDescriptor log10(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("log10", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    @MlxOp("mlx_log1p")
    public static LibraryTaskDescriptor log1p(FloatArray a, FloatArray out) {
        return Mlx.task("log1p", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    @MlxOp("mlx_log1p")
    public static LibraryTaskDescriptor log1p(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("log1p", 1, a, out);
    }

    /** Element-wise {@code out = ln(1 + a)}. */
    @MlxOp("mlx_log1p")
    public static LibraryTaskDescriptor log1p(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("log1p", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    @MlxOp("mlx_log2")
    public static LibraryTaskDescriptor log2(FloatArray a, FloatArray out) {
        return Mlx.task("log2", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    @MlxOp("mlx_log2")
    public static LibraryTaskDescriptor log2(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("log2", 1, a, out);
    }

    /** Element-wise {@code out = log2(a)}. */
    @MlxOp("mlx_log2")
    public static LibraryTaskDescriptor log2(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("log2", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    @MlxOp("mlx_radians")
    public static LibraryTaskDescriptor radians(FloatArray a, FloatArray out) {
        return Mlx.task("radians", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    @MlxOp("mlx_radians")
    public static LibraryTaskDescriptor radians(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("radians", 1, a, out);
    }

    /** Element-wise {@code out = a * pi / 180}. */
    @MlxOp("mlx_radians")
    public static LibraryTaskDescriptor radians(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("radians", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    @MlxOp("mlx_reciprocal")
    public static LibraryTaskDescriptor reciprocal(FloatArray a, FloatArray out) {
        return Mlx.task("reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    @MlxOp("mlx_reciprocal")
    public static LibraryTaskDescriptor reciprocal(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = 1 / a}. */
    @MlxOp("mlx_reciprocal")
    public static LibraryTaskDescriptor reciprocal(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("reciprocal", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    @MlxOp("mlx_sign")
    public static LibraryTaskDescriptor sign(FloatArray a, FloatArray out) {
        return Mlx.task("sign", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    @MlxOp("mlx_sign")
    public static LibraryTaskDescriptor sign(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("sign", 1, a, out);
    }

    /** Element-wise {@code out = sign(a)}. */
    @MlxOp("mlx_sign")
    public static LibraryTaskDescriptor sign(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("sign", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    @MlxOp("mlx_sin")
    public static LibraryTaskDescriptor sin(FloatArray a, FloatArray out) {
        return Mlx.task("sin", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    @MlxOp("mlx_sin")
    public static LibraryTaskDescriptor sin(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("sin", 1, a, out);
    }

    /** Element-wise {@code out = sin(a)}. */
    @MlxOp("mlx_sin")
    public static LibraryTaskDescriptor sin(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("sin", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    @MlxOp("mlx_sinh")
    public static LibraryTaskDescriptor sinh(FloatArray a, FloatArray out) {
        return Mlx.task("sinh", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    @MlxOp("mlx_sinh")
    public static LibraryTaskDescriptor sinh(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("sinh", 1, a, out);
    }

    /** Element-wise {@code out = sinh(a)}. */
    @MlxOp("mlx_sinh")
    public static LibraryTaskDescriptor sinh(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("sinh", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    @MlxOp("mlx_tan")
    public static LibraryTaskDescriptor tan(FloatArray a, FloatArray out) {
        return Mlx.task("tan", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    @MlxOp("mlx_tan")
    public static LibraryTaskDescriptor tan(HalfFloatArray a, HalfFloatArray out) {
        return Mlx.task("tan", 1, a, out);
    }

    /** Element-wise {@code out = tan(a)}. */
    @MlxOp("mlx_tan")
    public static LibraryTaskDescriptor tan(BFloat16Array a, BFloat16Array out) {
        return Mlx.task("tan", 1, a, out);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    @MlxOp("mlx_round")
    public static LibraryTaskDescriptor round(FloatArray a, FloatArray out, int decimals) {
        return Mlx.task("round", 1, a, out, decimals);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    @MlxOp("mlx_round")
    public static LibraryTaskDescriptor round(HalfFloatArray a, HalfFloatArray out, int decimals) {
        return Mlx.task("round", 1, a, out, decimals);
    }

    /** Element-wise {@code out = round(a, decimals)}, ties to even. */
    @MlxOp("mlx_round")
    public static LibraryTaskDescriptor round(BFloat16Array a, BFloat16Array out, int decimals) {
        return Mlx.task("round", 1, a, out, decimals);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    @MlxOp("mlx_arctan2")
    public static LibraryTaskDescriptor arctan2(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    @MlxOp("mlx_arctan2")
    public static LibraryTaskDescriptor arctan2(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = arctan2(a, b)}. */
    @MlxOp("mlx_arctan2")
    public static LibraryTaskDescriptor arctan2(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("arctan2", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    @MlxOp("mlx_floor_divide")
    public static LibraryTaskDescriptor floorDivide(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    @MlxOp("mlx_floor_divide")
    public static LibraryTaskDescriptor floorDivide(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    @MlxOp("mlx_floor_divide")
    public static LibraryTaskDescriptor floorDivide(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = floor(a / b)} (for int32, {@code a / b} rounded toward zero, as MLX divides integers). */
    @MlxOp("mlx_floor_divide")
    public static LibraryTaskDescriptor floorDivide(IntArray a, IntArray b, IntArray c) {
        return Mlx.task("floor_divide", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    @MlxOp("mlx_logaddexp")
    public static LibraryTaskDescriptor logaddexp(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    @MlxOp("mlx_logaddexp")
    public static LibraryTaskDescriptor logaddexp(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = ln(exp(a) + exp(b))}. */
    @MlxOp("mlx_logaddexp")
    public static LibraryTaskDescriptor logaddexp(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("logaddexp", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    @MlxOp("mlx_power")
    public static LibraryTaskDescriptor power(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("power", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    @MlxOp("mlx_power")
    public static LibraryTaskDescriptor power(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("power", 2, a, b, c);
    }

    /** Element-wise {@code c = a ^ b}. */
    @MlxOp("mlx_power")
    public static LibraryTaskDescriptor power(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("power", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    @MlxOp("mlx_remainder")
    public static LibraryTaskDescriptor remainder(FloatArray a, FloatArray b, FloatArray c) {
        return Mlx.task("remainder", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    @MlxOp("mlx_remainder")
    public static LibraryTaskDescriptor remainder(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        return Mlx.task("remainder", 2, a, b, c);
    }

    /** Element-wise {@code c = a - b * floor(a / b) (sign of b)}. */
    @MlxOp("mlx_remainder")
    public static LibraryTaskDescriptor remainder(BFloat16Array a, BFloat16Array b, BFloat16Array c) {
        return Mlx.task("remainder", 2, a, b, c);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    @MlxOp("mlx_divmod")
    public static LibraryTaskDescriptor divmod(FloatArray a, FloatArray b, FloatArray quotient, FloatArray remainder) {
        return Mlx.task("divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    @MlxOp("mlx_divmod")
    public static LibraryTaskDescriptor divmod(HalfFloatArray a, HalfFloatArray b, HalfFloatArray quotient, HalfFloatArray remainder) {
        return Mlx.task("divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    @MlxOp("mlx_divmod")
    public static LibraryTaskDescriptor divmod(BFloat16Array a, BFloat16Array b, BFloat16Array quotient, BFloat16Array remainder) {
        return Mlx.task("divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /**
     * Element-wise quotient and remainder as MLX computes them: {@code quotient = trunc(a / b)}
     * (rounded toward zero) and {@code remainder} as {@link #remainder} (sign of {@code b}). The
     * two therefore disagree with {@code a = b * quotient + remainder} when {@code a / b < 0}.
     */
    @MlxOp("mlx_divmod")
    public static LibraryTaskDescriptor divmod(IntArray a, IntArray b, IntArray quotient, IntArray remainder) {
        return Mlx.task("divmod", new int[] { 2, 3 }, a, b, quotient, remainder);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    @MlxOp("mlx_clip")
    public static LibraryTaskDescriptor clip(FloatArray a, FloatArray out, float lo, float hi) {
        return Mlx.task("clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    @MlxOp("mlx_clip")
    public static LibraryTaskDescriptor clip(HalfFloatArray a, HalfFloatArray out, float lo, float hi) {
        return Mlx.task("clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    @MlxOp("mlx_clip")
    public static LibraryTaskDescriptor clip(BFloat16Array a, BFloat16Array out, float lo, float hi) {
        return Mlx.task("clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = min(max(a, lo), hi)}. */
    @MlxOp("mlx_clip")
    public static LibraryTaskDescriptor clip(IntArray a, IntArray out, int lo, int hi) {
        return Mlx.task("clip", 1, a, out, lo, hi);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    @MlxOp("mlx_where")
    public static LibraryTaskDescriptor where(ByteArray condition, FloatArray x, FloatArray y, FloatArray out) {
        return Mlx.task("where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    @MlxOp("mlx_where")
    public static LibraryTaskDescriptor where(ByteArray condition, HalfFloatArray x, HalfFloatArray y, HalfFloatArray out) {
        return Mlx.task("where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    @MlxOp("mlx_where")
    public static LibraryTaskDescriptor where(ByteArray condition, BFloat16Array x, BFloat16Array y, BFloat16Array out) {
        return Mlx.task("where", 3, condition, x, y, out);
    }

    /** Element-wise {@code out = condition != 0 ? x : y}. */
    @MlxOp("mlx_where")
    public static LibraryTaskDescriptor where(ByteArray condition, IntArray x, IntArray y, IntArray out) {
        return Mlx.task("where", 3, condition, x, y, out);
    }
}
