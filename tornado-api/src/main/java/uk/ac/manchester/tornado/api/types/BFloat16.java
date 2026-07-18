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
package uk.ac.manchester.tornado.api.types;

/**
 * bfloat16 (brain float, 1 sign / 8 exponent / 7 mantissa; bias 127) conversions. BF16 keeps
 * float32's exponent range with a truncated mantissa, which is why ML frameworks prefer it over
 * fp16 for training/serving robustness: no overflow surprises at fp16's +-65504 boundary.
 *
 * <p>BF16 is a storage type: a value lives in a {@code short} (the raw bit pattern) and expands
 * to {@code float} on use. Like {@link FP8}, the decoder here uses <em>only</em> integer
 * arithmetic and float multiplies (no {@code Float.intBitsToFloat}), with a single return and
 * sequential (not branched) power-of-two loops, so the same method compiles and runs inside a
 * TornadoVM kernel on any backend - and the CUDA backend swaps it for a single
 * {@code __int_as_float(bits << 16)} bit reinterpretation at code-generation time.</p>
 */
public final class BFloat16 {

    private BFloat16() {
    }

    /** Largest finite BF16 magnitude (0x7F7F). */
    public static final float BF16_MAX = 3.3895314e38f;

    /**
     * Decode a BF16 bit pattern to float. Kernel-safe: fields are extracted with integer
     * division/modulo on a non-negative value (the CUDA codegen treats {@code >>>}/{@code &}
     * operands as unsigned and mis-extracts sign/exponent otherwise; see {@link FP8}).
     */
    public static float bf16ToFloat(short bits) {
        int b = toUnsigned(bits);
        int sign = b / 32768;
        int rem = b - sign * 32768;
        int exp = rem / 128;
        int mant = rem - exp * 128;
        float sig = 1.0f - 2.0f * sign;
        // Single return through a result variable - early returns inside a helper inlined into a
        // kernel loop mis-lower on the CUDA backend (issue #931).
        float result;
        if (exp == 0) {
            // Subnormal: (mant / 128) * 2^(1-127).
            result = sig * (mant * (1.0f / 128.0f)) * pow2(-126);
        } else if (exp == 255 && mant == 0) {
            result = sig * Float.POSITIVE_INFINITY;
        } else if (exp == 255) {
            result = Float.NaN;
        } else {
            // Normal: (1 + mant/128) * 2^(exp-127).
            result = sig * (1.0f + mant * (1.0f / 128.0f)) * pow2(exp - 127);
        }
        return result;
    }

    /**
     * Encode a float to the nearest BF16 bit pattern (round to nearest, overflow to infinity).
     * Kernel-safe: single return, bounded normalisation loops, no {@code Math.*} intrinsics.
     *
     * <p>Ties round half away from zero, matching the {@link FP8} encoders; the CUDA hardware
     * encoder ({@code __float2bfloat16}) rounds ties to even, so the two can differ by one ULP
     * on exact ties - which is why only the decoder is hardware-accelerated on CUDA.</p>
     */
    public static short bf16FromFloat(float value) {
        int out;
        if (value != value) {                 // NaN (kernel-safe, no Float.isNaN)
            out = 0x7FC0;
        } else {
            int sign = 0;
            float a = value;
            if (a < 0.0f) {
                sign = 1;
                a = -a;
            }
            // The round-to-nearest boundary into infinity is half an ULP above the max finite
            // value: 2^127 * (1 + 127.5/128).
            if (a >= 3.3961775e38f) {
                out = (sign << 15) | 0x7F80;  // +/-Inf (also catches float +Inf)
            } else {
                out = (sign << 15) | encodeField(a);
            }
        }
        return (short) out;
    }

    /** Short -> 0..65535, kernel-safe (no {@code & 0xFFFF}; see {@link FP8#e4m3ToFloat}). */
    private static int toUnsigned(short bits) {
        int b = bits;
        if (b < 0) {
            b = b + 65536;
        }
        return b;
    }

    /**
     * Round a non-negative finite magnitude below the overflow boundary to the BF16
     * {@code exponent|mantissa} field. Mirrors {@link FP8}'s encoder: bounded normalisation
     * loops, {@code (int)(x + 0.5)} rounding, single return.
     */
    private static int encodeField(float a) {
        int field;
        if (a == 0.0f) {
            field = 0;
        } else {
            // Normalise: find e with 2^e <= a < 2^(e+1). Fixed-count loops (kernel-safe);
            // 160 iterations cover the full float exponent range including subnormal inputs.
            int e = 0;
            float t = a;
            for (int i = 0; i < 160; i++) {
                if (t < 1.0f) {
                    t = t * 2.0f;
                    e = e - 1;
                }
            }
            for (int i = 0; i < 160; i++) {
                if (t >= 2.0f) {
                    t = t * 0.5f;
                    e = e + 1;
                }
            }
            int mant;
            int storedExp;
            if (e < -126) {
                // Subnormal: value = mant/128 * 2^-126.
                float scaled = a / pow2(-126) * 128.0f;
                mant = (int) (scaled + 0.5f);
                storedExp = 0;
                if (mant == 128) {   // rounded up into the smallest normal
                    mant = 0;
                    storedExp = 1;
                }
            } else {
                float scaled = (t - 1.0f) * 128.0f;
                mant = (int) (scaled + 0.5f);
                storedExp = e + 127;
                if (mant == 128) {   // mantissa overflow carries into the exponent
                    mant = 0;
                    storedExp = storedExp + 1;
                }
            }
            field = (storedExp << 7) | (mant % 128);
        }
        return field;
    }

    /**
     * {@code 2^n} for integer {@code n} in the float range, kernel-safe. Sequential loops on
     * one accumulator instead of an if/else pair, because the CUDA backend's phi lowering
     * merges two loops in separate branches incorrectly (issue #930).
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
