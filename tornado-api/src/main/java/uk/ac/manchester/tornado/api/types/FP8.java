/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types;

/**
 * 8-bit floating-point (FP8) conversions in the two OCP / hardware formats used by modern GPUs
 * for low-precision weight storage: <b>E4M3</b> (1 sign, 4 exponent, 3 mantissa; bias 7; no
 * infinity; max normal {@code +/-448}) and <b>E5M2</b> (1 sign, 5 exponent, 2 mantissa; bias 15;
 * IEEE-like, with infinity and NaN).
 *
 * <p>FP8 is a storage type: a value lives in one byte and is expanded to {@code float} on use.
 * The decoders here are written with <em>only</em> integer bit-ops and float arithmetic (no
 * {@code Float.intBitsToFloat}), so the exact same method compiles and runs inside a TornadoVM
 * kernel on the CUDA backend - a {@code ByteArray} / {@link uk.ac.manchester.tornado.api.types.arrays.FP8Array}
 * of FP8 weights is dequantized on the device with no special code generation, mirroring the Q8
 * dequant path.</p>
 */
public final class FP8 {

    private FP8() {
    }

    /** Largest finite E4M3 magnitude (S.1111.110). */
    public static final float E4M3_MAX = 448.0f;
    /** Largest finite E5M2 magnitude (S.11110.11). */
    public static final float E5M2_MAX = 57344.0f;

    // - E4M3 (bias 7, no inf; all-ones exp + all-ones mantissa = NaN) -

    /**
     * Decode an E4M3 byte to float. Kernel-safe: the bit fields are extracted with integer
     * division/modulo on a non-negative byte value rather than shifts/masks, because the
     * TornadoVM CUDA codegen treats {@code >>>}/{@code &} operands as unsigned and mis-extracts
     * the sign and exponent otherwise.
     */
    public static float e4m3ToFloat(byte bits) {
        int b = toUnsigned(bits);
        int sign = b / 128;
        int rem = b - sign * 128;
        int exp = rem / 8;
        int mant = rem - exp * 8;
        // Arithmetic sign (sign is 0 or 1 -> +1 or -1); avoids a ternary/phi.
        float sig = 1.0f - 2.0f * sign;
        // Single return through a result variable - early returns inside a helper inlined into a
        // kernel loop mis-lower on the CUDA backend (the guard captures only the first statement).
        float result;
        if (exp == 0) {
            // Subnormal: (mant / 8) * 2^(1-7).
            result = sig * (mant * (1.0f / 8.0f)) * pow2(-6);
        } else if (exp == 15 && mant == 7) {
            result = Float.NaN; // the single E4M3 NaN encoding
        } else {
            // Normal: (1 + mant/8) * 2^(exp-7).
            result = sig * (1.0f + mant * (1.0f / 8.0f)) * pow2(exp - 7);
        }
        return result;
    }

    /**
     * Encode a float to the nearest E4M3 byte (round to nearest, saturating to +/-448). Kernel-safe:
     * single return, no {@code Math.*} intrinsics, so float activations can be quantized to FP8
     * inside a CUDA kernel (the encode complement to {@link #e4m3ToFloat}).
     */
    public static byte e4m3FromFloat(float value) {
        int out;
        if (value != value) {                 // NaN (kernel-safe, no Float.isNaN)
            out = 0x7F;
        } else {
            int sign = 0;
            float a = value;
            if (a < 0.0f) {
                sign = 1;
                a = -a;
            }
            if (a >= E4M3_MAX) {              // saturate (also catches +Inf)
                out = (sign << 7) | 0x7E;
            } else {
                // E4M3: exponent field runs 0..15; the top exponent's only reserved code is
                // mantissa all-ones (NaN), so the largest finite mantissa there is 6.
                out = sign << 7 | encodeField(a, 3, 7, 15, 6);
            }
        }
        return (byte) out;
    }

    // - E5M2 (bias 15, IEEE-like: exp all-ones -> inf/NaN) -

    /** Decode an E5M2 byte to float. Kernel-safe (division/modulo extraction; see {@link #e4m3ToFloat}). */
    public static float e5m2ToFloat(byte bits) {
        int b = toUnsigned(bits);
        int sign = b / 128;
        int rem = b - sign * 128;
        int exp = rem / 4;
        int mant = rem - exp * 4;
        float sig = 1.0f - 2.0f * sign;
        float result;
        if (exp == 0) {
            result = sig * (mant * (1.0f / 4.0f)) * pow2(-14);
        } else if (exp == 31 && mant == 0) {
            result = sig * Float.POSITIVE_INFINITY;
        } else if (exp == 31) {
            result = Float.NaN;
        } else {
            result = sig * (1.0f + mant * (1.0f / 4.0f)) * pow2(exp - 15);
        }
        return result;
    }

    /** Byte -> 0..255, kernel-safe (no {@code & 0xFF}, which the CUDA codegen treats as unsigned). */
    private static int toUnsigned(byte bits) {
        int b = bits;
        if (b < 0) {
            b = b + 256;
        }
        return b;
    }

    /** Encode a float to the nearest E5M2 byte (round to nearest; overflow -> infinity). Kernel-safe. */
    public static byte e5m2FromFloat(float value) {
        int out;
        if (value != value) {
            out = 0x7F;
        } else {
            int sign = 0;
            float a = value;
            if (a < 0.0f) {
                sign = 1;
                a = -a;
            }
            // The last finite E5M2 value is 57344 = (exp 30, mant 3); the round-to-nearest boundary
            // to infinity is one half-step above it (57344 + 4096 = 61440).
            if (a >= 61440.0f) {
                out = (sign << 7) | 0x7C;    // +/-Inf
            } else {
                out = sign << 7 | encodeField(a, 2, 15, 30, 3);
            }
        }
        return (byte) out;
    }

    // - Shared encoder -

    /**
     * Round a non-negative magnitude {@code a} (below the format's overflow point) to the
     * {@code exponent|mantissa} bit field. Kernel-safe: the exponent is found by a bounded
     * normalisation loop (not {@code Math.getExponent}), rounding is {@code (int)(x + 0.5)} (not
     * {@code Math.round}), and there is a single return - so this compiles and runs inside a CUDA
     * kernel, letting activations be quantized to FP8 on the device.
     */
    private static int encodeField(float a, int mantBits, int bias, int maxFiniteExp, int maxMantAtTop) {
        int field;
        if (a == 0.0f) {
            field = 0;
        } else {
            int mantScale = 1 << mantBits;
            int unbiasedMin = 1 - bias;
            // Normalise: find e with 2^e <= a < 2^(e+1), giving t = a / 2^e in [1,2). Both loops are
            // fixed-count (kernel-safe, no data-dependent bound) and act on one accumulator.
            int e = 0;
            float t = a;
            for (int i = 0; i < 80; i++) {
                if (t < 1.0f) {
                    t = t * 2.0f;
                    e = e - 1;
                }
            }
            for (int i = 0; i < 80; i++) {
                if (t >= 2.0f) {
                    t = t * 0.5f;
                    e = e + 1;
                }
            }
            int mant;
            int storedExp;
            if (e < unbiasedMin) {
                // Subnormal: value = mant/2^mantBits * 2^(1-bias).
                float scaled = a / pow2(unbiasedMin) * mantScale;
                mant = (int) (scaled + 0.5f);
                storedExp = 0;
                if (mant == mantScale) {   // rounded up into the smallest normal
                    mant = 0;
                    storedExp = 1;
                }
            } else {
                float scaled = (t - 1.0f) * mantScale;
                mant = (int) (scaled + 0.5f);
                storedExp = e + bias;
                if (mant == mantScale) {   // mantissa overflow carries into the exponent
                    mant = 0;
                    storedExp = storedExp + 1;
                }
            }
            // Clamp to the largest finite field (E4M3 reserves its top-exponent all-ones mantissa).
            if (storedExp > maxFiniteExp) {
                storedExp = maxFiniteExp;
                mant = maxMantAtTop;
            } else if (storedExp == maxFiniteExp && mant > maxMantAtTop) {
                mant = maxMantAtTop;
            }
            field = (storedExp << mantBits) | (mant & (mantScale - 1));
        }
        return field;
    }

    /**
     * {@code 2^n} for small integer {@code n}, kernel-safe. The two directions use <em>sequential</em>
     * loops (each bounded at 0 when unused) rather than an {@code if/else} pair, because the CUDA
     * backend's phi lowering merges the results of two loops that sit in separate branches
     * incorrectly. Keeping both loops on the straight-line path, mutating one accumulator, avoids
     * that merge.
     */
    private static float pow2(int n) {
        int up = 0;
        int down = 0;
        if (n >= 0) {
            up = n;
        } else {
            down = -n;
        }
        float r = 1.0f;
        for (int i = 0; i < up; i++) {
            r = r * 2.0f;
        }
        for (int i = 0; i < down; i++) {
            r = r * 0.5f;
        }
        return r;
    }
}
