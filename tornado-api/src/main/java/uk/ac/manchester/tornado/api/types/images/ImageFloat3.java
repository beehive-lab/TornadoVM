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
import uk.ac.manchester.tornado.api.types.utils.FloatingPointError;
import uk.ac.manchester.tornado.api.types.vectors.Float3;

public final class ImageFloat3 implements TornadoImagesInterface<FloatBuffer> {

    public static final Class<ImageFloat3> TYPE = ImageFloat3.class;

    private static final int ELEMENT_SIZE = 3;
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
    public ImageFloat3(int width, int height, FloatArray array) {
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
     *     number of columns
     */
    public ImageFloat3(int width, int height) {
        this(width, height, new FloatArray(width * height * ELEMENT_SIZE));
    }

    public ImageFloat3(float[][] matrix) {
        this(matrix.length / ELEMENT_SIZE, matrix[0].length / ELEMENT_SIZE);
    }

    public FloatArray getArray() {
        return storage;
    }

    private int toIndex(int x, int y) {
        return ELEMENT_SIZE * (x + (y * X));
    }

    public Float3 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Float3 value) {
        set(x, 0, value);
    }

    public Float3 get(int x, int y) {
        final int offset = toIndex(x, y);
        return loadFromArray(storage, offset);
    }

    private Float3 loadFromArray(final FloatArray array, int index) {
        final Float3 result = new Float3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    public void set(int x, int y, Float3 value) {
        final int offset = toIndex(x, y);
        storeToArray(value, storage, offset);
    }

    private void storeToArray(Float3 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    public ImageFloat3 duplicate() {
        ImageFloat3 matrix = new ImageFloat3(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageFloat3 m) {
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
        String result = String.format("ImageFloat3 <%d x %d>", X, Y);
        if (X <= 8 && Y <= 8) {
            result += "\n" + toString(FloatOps.FMT_3);
        }
        return result;
    }

    public Float3 mean() {
        Float3 result = new Float3();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float3.add(result, get(col, row));
            }
        }

        return Float3.div(result, (X * Y));
    }

    public Float3 min() {
        Float3 result = new Float3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float3.min(result, get(col, row));
            }
        }

        return result;
    }

    public Float3 max() {
        Float3 result = new Float3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float3.max(result, get(col, row));
            }
        }

        return result;
    }

    public Float3 stdDev() {
        final Float3 mean = mean();
        Float3 varience = new Float3();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                Float3 v = Float3.sub(mean, get(col, row));
                v = Float3.mult(v, v);
                v = Float3.div(v, X);
                varience = Float3.add(v, varience);
            }
        }

        return Float3.sqrt(varience);
    }

    public String summarise() {
        return String.format("ImageFloat3<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
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

    public FloatingPointError calculateULP(ImageFloat3 ref) {
        float maxULP = Float.MIN_VALUE;
        float minULP = Float.MAX_VALUE;
        float averageULP = 0f;

        /*
         * check to make sure dimensions match
         */
        if (ref.X != X && ref.Y != Y) {
            return new FloatingPointError(-1f, 0f, 0f, 0f);
        }

        int errors = 0;
        for (int j = 0; j < Y; j++) {
            for (int i = 0; i < X; i++) {
                final Float3 v = get(i, j);
                final Float3 r = ref.get(i, j);

                final float ulpFactor = Float3.findULPDistance(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);

                if (ulpFactor > 5f) {
                    errors++;
                }
            }
        }
        averageULP /= (float) X * Y;
        return new FloatingPointError(averageULP, minULP, maxULP, -1f, errors);
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
