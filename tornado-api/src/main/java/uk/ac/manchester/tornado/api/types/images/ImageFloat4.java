/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.images;

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public final class ImageFloat4 implements TornadoImagesInterface<FloatBuffer> {

    public static final Class<ImageFloat4> TYPE = ImageFloat4.class;

    private static final int ELEMENT_SIZE = 4;
    /**
     * backing array.
     */
    private final FloatArray storage;
    /**
     * Number of rows.
     */
    private final int Y;
    /**
     * Number of columns.
     */
    private final int X;
    /**
     * number of elements in the storage.
     */
    private final int numElements;

    /**
     * Storage format for matrix.
     *
     * @param width
     *     number of rows
     * @param height
     *     number of columns
     * @param array
     *     array reference which contains data
     */
    public ImageFloat4(int width, int height, FloatArray array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * ELEMENT_SIZE;
    }

    /**
     * Storage format for matrix.
     *
     * @param width
     *     number of rows
     * @param height
     *     number of column
     */
    public ImageFloat4(int width, int height) {
        this(width, height, new FloatArray(width * height * ELEMENT_SIZE));
    }

    public ImageFloat4(float[][] matrix) {
        this(matrix.length / ELEMENT_SIZE, matrix[0].length / ELEMENT_SIZE, StorageFormats.toRowMajor(matrix));
    }

    public FloatArray getArray() {
        return storage;
    }

    private int toIndex(int x, int y) {
        return ELEMENT_SIZE * (x + (y * X));
    }

    public Float4 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Float4 value) {
        set(x, 0, value);
    }

    public Float4 get(int x, int y) {
        final int offset = toIndex(x, y);
        return loadFromArray(storage, offset);
    }

    private Float4 loadFromArray(final FloatArray array, int index) {
        final Float4 result = new Float4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    public void set(int x, int y, Float4 value) {
        final int offset = toIndex(x, y);
        storeToArray(value, storage, offset);
    }

    private void storeToArray(Float4 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    public void fill(float value) {
        storage.init(value);
    }

    public ImageFloat4 duplicate() {
        ImageFloat4 matrix = new ImageFloat4(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageFloat4 m) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str.append(get(j, i).toString(fmt)).append("\n");
            }
        }

        return str.toString();
    }

    @Override
    public String toString() {
        String result = String.format("ImageFloat4 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(FloatOps.FMT_4);
        }
        return result;
    }

    public Float4 mean() {
        Float4 result = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float4.add(result, get(col, row));
            }
        }

        return Float4.div(result, (X * Y));
    }

    public Float4 min() {
        Float4 result = new Float4(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float4.min(result, get(col, row));
            }
        }
        return result;
    }

    public Float4 max() {
        Float4 result = new Float4(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float4.max(result, get(col, row));
            }
        }
        return result;
    }

    public Float4 stdDev() {
        final Float4 mean = mean();
        Float4 varience = new Float4();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                Float4 v = Float4.sub(mean, get(col, row));
                v = Float4.mult(v, v);
                v = Float4.div(v, X);
                varience = Float4.add(v, varience);
            }
        }

        return Float4.sqrt(varience);
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return numElements;
    }

    public void clear() {
        storage.clear();
    }

    @Override
    public long getNumBytes() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public long getNumBytesWithHeader() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public MemorySegment getSegment() {
        return getArray().getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return getArray().getSegmentWithHeader();
    }

}
