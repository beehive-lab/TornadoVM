/*
 * Copyright (c) 2013-2024 APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.vectors.Float8;

public final class ImageFloat8 implements TornadoImagesInterface<FloatBuffer> {

    public static final Class<ImageFloat8> TYPE = ImageFloat8.class;

    private static final int ELEMENT_SIZE = 8;
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
    public ImageFloat8(int width, int height, FloatArray array) {
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
    public ImageFloat8(int width, int height) {
        this(width, height, new FloatArray(width * height * ELEMENT_SIZE));
    }

    public FloatArray getArray() {
        return storage;
    }

    private int getIndex(int x, int y) {
        return (x * ELEMENT_SIZE) + (y * ELEMENT_SIZE * X);
    }

    public int numElements() {
        return numElements;
    }

    public Float8 get(int x) {
        return get(x, 0);
    }

    private void storeToArray(Float8 value, FloatArray array, int index) {
        for (int i = 0; i < ELEMENT_SIZE; i++) {
            array.set(index + i, value.get(i));
        }
    }

    public void set(int x, Float8 value) {
        final int offset = getIndex(x, 0);
        storeToArray(value, storage, offset);
    }

    public Float8 get(int x, int y) {
        final int offset = getIndex(x, y);
        return loadFromArray(storage, offset);
    }

    private Float8 loadFromArray(final FloatArray array, int index) {
        final Float8 result = new Float8();
        result.setS0(array.get(index));
        result.setS1(array.get(index + 1));
        result.setS2(array.get(index + 2));
        result.setS3(array.get(index + 3));
        result.setS4(array.get(index + 4));
        result.setS5(array.get(index + 5));
        result.setS6(array.get(index + 6));
        result.setS7(array.get(index + 7));
        return result;
    }

    public void set(int x, int y, Float8 value) {
        final int offset = getIndex(x, y);
        storeToArray(value, storage, offset);
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

    public ImageFloat8 duplicate() {
        ImageFloat8 image = new ImageFloat8(X, Y);
        image.set(this);
        return image;
    }

    public void set(ImageFloat8 m) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str.append(get(j, i).toString(fmt) + "\n");
            }
        }

        return str.toString();
    }

    @Override
    public String toString() {
        String result = String.format("ImageFloat8 <%d x %d>", X, Y);
        if (X <= 4 && Y <= 4) {
            result += "\n" + toString(FloatOps.FMT_8);
        }
        return result;
    }

    public Float8 mean() {
        Float8 result = new Float8();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float8.add(result, get(col, row));
            }
        }
        result = Float8.div(result, (X * Y));
        return result;
    }

    public Float8 min() {
        Float8 result = new Float8(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float8.min(result, get(col, row));
            }
        }

        return result;
    }

    public Float8 max() {
        Float8 result = new Float8(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float8.max(result, get(col, row));
            }
        }

        return result;
    }

    public Float8 stdDev() {
        final Float8 mean = mean();
        Float8 varience = new Float8();
        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                Float8 v = Float8.sub(mean, get(col, row));
                v = Float8.mult(v, v);
                v = Float8.div(v, X);
                varience = Float8.add(v, varience);
            }
        }
        return Float8.sqrt(varience);
    }

    public String summarise() {
        return String.format("ImageFloat8<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
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

    public FloatingPointError calculateULP(ImageFloat8 ref) {
        float maxULP = Float.MIN_VALUE;
        float minULP = Float.MAX_VALUE;
        float averageULP = 0f;

        if (ref.X != X && ref.Y != Y) {
            return new FloatingPointError(-1f, 0f, 0f, 0f);
        }

        for (int j = 0; j < Y; j++) {
            for (int i = 0; i < X; i++) {
                final Float8 v = get(i, j);
                final Float8 r = ref.get(i, j);

                final float ulpFactor = Float8.findULPDistance(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);
            }
        }
        averageULP /= (float) X * Y;
        return new FloatingPointError(averageULP, minULP, maxULP, -1f);
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
