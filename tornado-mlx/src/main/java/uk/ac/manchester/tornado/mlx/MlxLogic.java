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
 * MLX comparisons, floating-point classification, and logical and bitwise operations as TornadoVM
 * library tasks. Boolean results are written as 0 or 1 into byte arrays.
 */
public final class MlxLogic {

    private MlxLogic() {
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(FloatArray a, FloatArray b, ByteArray out) {
        return Mlx.task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return Mlx.task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return Mlx.task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a == b} as 0 or 1. */
    public static LibraryTaskDescriptor equal(IntArray a, IntArray b, ByteArray out) {
        return Mlx.task("mlx_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(FloatArray a, FloatArray b, ByteArray out) {
        return Mlx.task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return Mlx.task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return Mlx.task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a != b} as 0 or 1. */
    public static LibraryTaskDescriptor notEqual(IntArray a, IntArray b, ByteArray out) {
        return Mlx.task("mlx_not_equal", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(FloatArray a, FloatArray b, ByteArray out) {
        return Mlx.task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return Mlx.task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return Mlx.task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a > b} as 0 or 1. */
    public static LibraryTaskDescriptor greater(IntArray a, IntArray b, ByteArray out) {
        return Mlx.task("mlx_greater", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(FloatArray a, FloatArray b, ByteArray out) {
        return Mlx.task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return Mlx.task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return Mlx.task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a >= b} as 0 or 1. */
    public static LibraryTaskDescriptor greaterEqual(IntArray a, IntArray b, ByteArray out) {
        return Mlx.task("mlx_greater_equal", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(FloatArray a, FloatArray b, ByteArray out) {
        return Mlx.task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return Mlx.task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return Mlx.task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a < b} as 0 or 1. */
    public static LibraryTaskDescriptor less(IntArray a, IntArray b, ByteArray out) {
        return Mlx.task("mlx_less", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(FloatArray a, FloatArray b, ByteArray out) {
        return Mlx.task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out) {
        return Mlx.task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(BFloat16Array a, BFloat16Array b, ByteArray out) {
        return Mlx.task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = a <= b} as 0 or 1. */
    public static LibraryTaskDescriptor lessEqual(IntArray a, IntArray b, ByteArray out) {
        return Mlx.task("mlx_less_equal", 2, a, b, out);
    }

    /** {@code out[i] = |a - b| <= atol + rtol * |b|} as 0 or 1; NaNs compare equal if {@code equalNan}. */
    public static LibraryTaskDescriptor isclose(FloatArray a, FloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return Mlx.task("mlx_isclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if every element of {@code a} and {@code b} is close (as in {@link #isclose}), else 0. */
    public static LibraryTaskDescriptor allclose(FloatArray a, FloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return Mlx.task("mlx_allclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[i] = |a - b| <= atol + rtol * |b|} as 0 or 1; NaNs compare equal if {@code equalNan}. */
    public static LibraryTaskDescriptor isclose(HalfFloatArray a, HalfFloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return Mlx.task("mlx_isclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if every element of {@code a} and {@code b} is close (as in {@link #isclose}), else 0. */
    public static LibraryTaskDescriptor allclose(HalfFloatArray a, HalfFloatArray b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return Mlx.task("mlx_allclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[i] = |a - b| <= atol + rtol * |b|} as 0 or 1; NaNs compare equal if {@code equalNan}. */
    public static LibraryTaskDescriptor isclose(BFloat16Array a, BFloat16Array b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return Mlx.task("mlx_isclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if every element of {@code a} and {@code b} is close (as in {@link #isclose}), else 0. */
    public static LibraryTaskDescriptor allclose(BFloat16Array a, BFloat16Array b, ByteArray out, float rtol, float atol, boolean equalNan) {
        return Mlx.task("mlx_allclose", 2, a, b, out, rtol, atol, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(FloatArray a, FloatArray b, ByteArray out, boolean equalNan) {
        return Mlx.task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(HalfFloatArray a, HalfFloatArray b, ByteArray out, boolean equalNan) {
        return Mlx.task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(BFloat16Array a, BFloat16Array b, ByteArray out, boolean equalNan) {
        return Mlx.task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[0]} = 1 if {@code a} and {@code b} are equal element by element, else 0. */
    public static LibraryTaskDescriptor arrayEqual(IntArray a, IntArray b, ByteArray out, boolean equalNan) {
        return Mlx.task("mlx_array_equal", 2, a, b, out, equalNan);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is finite, else 0. */
    public static LibraryTaskDescriptor isfinite(FloatArray a, ByteArray out) {
        return Mlx.task("mlx_isfinite", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is finite, else 0. */
    public static LibraryTaskDescriptor isfinite(HalfFloatArray a, ByteArray out) {
        return Mlx.task("mlx_isfinite", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is finite, else 0. */
    public static LibraryTaskDescriptor isfinite(BFloat16Array a, ByteArray out) {
        return Mlx.task("mlx_isfinite", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is infinite, else 0. */
    public static LibraryTaskDescriptor isinf(FloatArray a, ByteArray out) {
        return Mlx.task("mlx_isinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is infinite, else 0. */
    public static LibraryTaskDescriptor isinf(HalfFloatArray a, ByteArray out) {
        return Mlx.task("mlx_isinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is infinite, else 0. */
    public static LibraryTaskDescriptor isinf(BFloat16Array a, ByteArray out) {
        return Mlx.task("mlx_isinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is NaN, else 0. */
    public static LibraryTaskDescriptor isnan(FloatArray a, ByteArray out) {
        return Mlx.task("mlx_isnan", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is NaN, else 0. */
    public static LibraryTaskDescriptor isnan(HalfFloatArray a, ByteArray out) {
        return Mlx.task("mlx_isnan", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is NaN, else 0. */
    public static LibraryTaskDescriptor isnan(BFloat16Array a, ByteArray out) {
        return Mlx.task("mlx_isnan", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is -infinity, else 0. */
    public static LibraryTaskDescriptor isneginf(FloatArray a, ByteArray out) {
        return Mlx.task("mlx_isneginf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is -infinity, else 0. */
    public static LibraryTaskDescriptor isneginf(HalfFloatArray a, ByteArray out) {
        return Mlx.task("mlx_isneginf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is -infinity, else 0. */
    public static LibraryTaskDescriptor isneginf(BFloat16Array a, ByteArray out) {
        return Mlx.task("mlx_isneginf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is +infinity, else 0. */
    public static LibraryTaskDescriptor isposinf(FloatArray a, ByteArray out) {
        return Mlx.task("mlx_isposinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is +infinity, else 0. */
    public static LibraryTaskDescriptor isposinf(HalfFloatArray a, ByteArray out) {
        return Mlx.task("mlx_isposinf", 1, a, out);
    }

    /** {@code out[i]} = 1 if {@code a[i]} is +infinity, else 0. */
    public static LibraryTaskDescriptor isposinf(BFloat16Array a, ByteArray out) {
        return Mlx.task("mlx_isposinf", 1, a, out);
    }

    /** {@code out[i] = a[i] != 0 && b[i] != 0} as 0 or 1. */
    public static LibraryTaskDescriptor logicalAnd(ByteArray a, ByteArray b, ByteArray out) {
        return Mlx.task("mlx_logical_and", 2, a, b, out);
    }

    /** {@code out[i] = a[i] != 0 || b[i] != 0} as 0 or 1. */
    public static LibraryTaskDescriptor logicalOr(ByteArray a, ByteArray b, ByteArray out) {
        return Mlx.task("mlx_logical_or", 2, a, b, out);
    }

    /** {@code out[i] = a[i] == 0} as 0 or 1. */
    public static LibraryTaskDescriptor logicalNot(ByteArray a, ByteArray out) {
        return Mlx.task("mlx_logical_not", 1, a, out);
    }

    /** {@code out[i] = a & b}. */
    public static LibraryTaskDescriptor bitwiseAnd(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("mlx_bitwise_and", 2, a, b, out);
    }

    /** {@code out[i] = a | b}. */
    public static LibraryTaskDescriptor bitwiseOr(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("mlx_bitwise_or", 2, a, b, out);
    }

    /** {@code out[i] = a ^ b}. */
    public static LibraryTaskDescriptor bitwiseXor(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("mlx_bitwise_xor", 2, a, b, out);
    }

    /** {@code out[i] = a << b}. */
    public static LibraryTaskDescriptor leftShift(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("mlx_left_shift", 2, a, b, out);
    }

    /** {@code out[i] = a >> b (arithmetic)}. */
    public static LibraryTaskDescriptor rightShift(IntArray a, IntArray b, IntArray out) {
        return Mlx.task("mlx_right_shift", 2, a, b, out);
    }

    /** {@code out[i] = ~a[i]}. */
    public static LibraryTaskDescriptor bitwiseInvert(IntArray a, IntArray out) {
        return Mlx.task("mlx_bitwise_invert", 1, a, out);
    }
}
