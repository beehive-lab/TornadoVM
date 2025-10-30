/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.utils;

import uk.ac.manchester.tornado.api.types.arrays.Int8Array;

/**
 * Utility class providing operators for quantized data types. Currently, the functions
 * implemented are compatible with 8-bit quantized models (e.g., Q8_0), but it will be extended
 * as support is added for additional quantization formats and optimization strategies.
 */
public class QuantizationUtils {

    public static int dp4a(Int8Array a, long offset_a, Int8Array b, long offset_b, int c) {
        if (a.getSize() < offset_a + 4 || b.getSize() < offset_b + 4) {
            throw new IllegalArgumentException("Array slice is out of bounds for the given offset.");
        }

        int dotProduct = 0;
        for (int i = 0; i < 4; i++) {
            dotProduct += a.get((int) offset_a + i) * b.get((int) offset_b + i);
        }
        return c + dotProduct;
    }

    public static int dp4a(Int8Array a_global, long offset_a, byte[] b_local, long offset_b, int c) {
        if (a_global.getSize() < offset_a + 4 || b_local.length < offset_b + 4) {
            throw new IllegalArgumentException("Array slice is out of bounds for the given offset.");
        }

        int dotProduct = 0;
        for (int i = 0; i < 4; i++) {
            dotProduct += a_global.get((int) offset_a + i) * b_local[(int) offset_b + i];
        }
        return c + dotProduct;
    }

    public static float dequantizeFusedResult(int dotProductResult, float w_scale, float x_scale) {
        return (float) dotProductResult * w_scale * x_scale;
    }

    public static int dp4a_packed(int a, int b, int c) {
        // Extract individual bytes and compute dot product
        int sum = c;
        for (int i = 0; i < 4; i++) {
            byte a_byte = (byte) ((a >> (i * 8)) & 0xFF);
            byte b_byte = (byte) ((b >> (i * 8)) & 0xFF);
            sum += a_byte * b_byte;
        }
        return sum;
    }

}
