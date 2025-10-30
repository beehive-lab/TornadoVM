package uk.ac.manchester.tornado.api.utils;

import uk.ac.manchester.tornado.api.types.arrays.Int8Array;

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
