package uk.ac.manchester.tornado.api.utils;

import uk.ac.manchester.tornado.api.types.arrays.Int8Array;

public class QuantizationUtils {

    public static int dp4a(Int8Array a, Int8Array b, int c, int offset) {
        if (a.getSize() < offset + 4 || b.getSize() < offset + 4) {
            throw new IllegalArgumentException("Array slice is out of bounds for the given offset.");
        }

        int dotProduct = 0;
        for (int i = 0; i < 4; i++) {
            dotProduct += a.get(offset + i) * b.get(offset + i);
        }
        return c + dotProduct;
    }

    public static float dequantizeFusedResult(int dotProductResult, float w_scale, float x_scale) {
        return (float) dotProductResult * w_scale * x_scale;
    }

}
