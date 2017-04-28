/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.collections.types;

import java.nio.ByteBuffer;

public class ImageByte4 implements PrimitiveStorage<ByteBuffer> {

    /**
     * backing array
     */
    final protected byte[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 4;

    /**
     * Number of rows
     */
    final protected int Y;

    /**
     * Number of columns
     */
    final protected int X;

    /**
     * Storage format for matrix
     *
     * @param height number of columns
     * @param width number of rows
     * @param array array reference which contains data
     */
    public ImageByte4(int width, int height, byte[] array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * elementSize;
    }

    /**
     * Storage format for matrix
     *
     * @param height number of columns
     * @param width number of rows
     */
    public ImageByte4(int width, int height) {
        this(width, height, new byte[width * height * elementSize]);
    }

    public ImageByte4(byte[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, StorageFormats.toRowMajor(matrix));
    }

    private final int toIndex(int x, int y) {
        return (x * elementSize) + (y * elementSize * X);
    }

    public Byte4 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Byte4 value) {
        set(x, 0, value);
    }

    public Byte4 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Byte4.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Byte4 value) {
        final int offset = toIndex(x, y);
        value.storeToArray(storage, offset);
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    public void fill(byte value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public ImageByte4 duplicate() {
        final ImageByte4 matrix = new ImageByte4(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageByte4 m) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public Float4 mean() {
        Float4 result = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());
                result = Float4.add(cast, result);

            }
        }
        return Float4.div(result, (float) (X * Y));
    }

    public Float4 min() {
        Float4 result = new Float4(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());
                result = Float4.min(cast, result);
            }
        }

        return result;
    }

    public Float4 max() {
        Float4 result = new Float4(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());
                result = Float4.max(cast, result);
            }
        }

        return result;
    }

    public Float4 stdDev() {
        final Float4 mean = mean();
        Float4 varience = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                final Byte4 value = get(col, row);
                final Float4 cast = new Float4(value.getX(), value.getY(), value.getZ(), value.getW());

                Float4 v = Float4.sub(mean, cast);
                v = Float4.mult(v, v);
                v = Float4.div(v, (float) (X * Y));
                varience = Float4.add(v, varience);

            }
        }
        return Float4.sqrt(varience);
    }

    public String summerise() {
        return String.format("ImageFloat3<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str += get(j, i).toString(fmt) + " ";
            }
            str += "\n";
        }

        return str;
    }

    public String toString(String fmt, int width, int height) {
        String str = "";

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                str += get(j, i).toString(fmt) + " ";
            }
            str += "\n";
        }

        return str;
    }

    public String toString() {
        String result = String.format("ImageByte4 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(ByteOps.fmt4);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(ByteBuffer src) {
        asBuffer().put(src);
    }

    @Override
    public ByteBuffer asBuffer() {
        return ByteBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

}
