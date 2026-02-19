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

    public static int dp4a(Int8Array a, long offsetA, Int8Array b, long offsetB, int c) {
        if (a.getSize() < offsetA + 4 || b.getSize() < offsetB + 4) {
            throw new IllegalArgumentException("Array slice is out of bounds for the given offset.");
        }

        int dotProduct = 0;
        for (int i = 0; i < 4; i++) {
            dotProduct += a.get((int) offsetA + i) * b.get((int) offsetB + i);
        }
        return c + dotProduct;
    }

    public static int dp4a(Int8Array aGlobal, long offsetA, byte[] bLocal, long offsetB, int c) {
        if (aGlobal.getSize() < offsetA + 4 || bLocal.length < offsetB + 4) {
            throw new IllegalArgumentException("Array slice is out of bounds for the given offset.");
        }

        int dotProduct = 0;
        for (int i = 0; i < 4; i++) {
            dotProduct += aGlobal.get((int) offsetA + i) * bLocal[(int) offsetB + i];
        }
        return c + dotProduct;
    }

    public static float dequantizeFusedResult(int dotProductResult, float wScale, float xScale) {
        return (float) dotProductResult * wScale * xScale;
    }

    public static int dp4a_packed(int a, int b, int c) {
        // Extract individual bytes and compute dot product
        int sum = c;
        for (int i = 0; i < 4; i++) {
            byte aByte = (byte) ((a >> (i * 8)) & 0xFF);
            byte bByte = (byte) ((b >> (i * 8)) & 0xFF);
            sum += aByte * bByte;
        }
        return sum;
    }

}
