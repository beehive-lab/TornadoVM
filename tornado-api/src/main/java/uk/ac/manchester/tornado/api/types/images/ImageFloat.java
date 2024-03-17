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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.utils.FloatingPointError;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;

public final class ImageFloat implements TornadoImagesInterface<FloatBuffer> {

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
    float maxULP = Float.MIN_VALUE;
    float minULP = Float.MAX_VALUE;
    float averageULP = 0f;

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
    public ImageFloat(int width, int height, FloatArray array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y;
    }

    /**
     * Storage format for matrix.
     *
     * @param width
     *     number of rows
     * @param height
     *     number of columns
     */
    public ImageFloat(int width, int height) {
        this(width, height, new FloatArray(width * height));
    }

    public ImageFloat(float[][] matrix) {
        this(matrix.length, matrix[0].length);
    }

    public static void scale(ImageFloat image, float alpha) {
        for (int i = 0; i < image.storage.getSize(); i++) {
            image.storage.set(i, image.storage.get(i) * alpha);
        }
    }

    public FloatArray getArray() {
        return storage;
    }

    public float get(int i) {
        return storage.get(i);
    }

    public void set(int i, float value) {
        storage.set(i, value);
    }

    /***
     * returns the ith column of the jth row.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @return float
     */
    public float get(int i, int j) {
        return storage.get(StorageFormats.toRowMajor(j, i, X));
    }

    /***
     * sets the ith column of the jth row to value.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @param value
     *     new value
     */
    public void set(int i, int j, float value) {
        storage.set(StorageFormats.toRowMajor(j, i, X), value);
    }

    public void put(float[] array) {
        System.arraycopy(array, 0, storage, 0, array.length);
    }

    public int Y() {
        return Y;
    }

    public int X() {
        return X;
    }

    public void fill(float value) {
        for (@Parallel int i = 0; i < Y; i++) {
            for (@Parallel int j = 0; j < X; j++) {
                set(j, i, value);
            }
        }
    }

    public ImageFloat duplicate() {
        final ImageFloat matrix = new ImageFloat(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageFloat m) {
        System.arraycopy(storage, 0, m.storage, 0, storage.getSize());
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str.append(String.format(fmt, get(j, i))).append(" ");
            }
            str.append("\n");
        }
        return str.toString();
    }

    public String toString() {
        String result = String.format("ImageFloat <%d x %d>", X, Y);
        if (Y < 16 && X < 16) {
            result += "\n" + toString(FloatOps.FMT);
        }
        return result;
    }

    public float mean() {
        float result = 0f;
        for (int i = 0; i < storage.getSize(); i++) {
            result += storage.get(i);
        }
        return result / (X * Y);
    }

    public float min() {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < storage.getSize(); i++) {
            result = Math.min(result, storage.get(i));
        }
        return result;
    }

    public float max() {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < storage.getSize(); i++) {
            result = Math.max(result, storage.get(i));
        }
        return result;
    }

    public float stdDev() {
        final float mean = mean();
        float varience = 0f;
        for (int i = 0; i < storage.getSize(); i++) {
            float v = storage.get(i);
            v -= mean;
            v *= v;
            varience = v / X;
        }
        return TornadoMath.sqrt(varience);
    }

    public String summerise() {
        return String.format("ImageFloat<%dx%d>: min=%e, max=%e, mean=%e, sd=%e", X, Y, min(), max(), mean(), stdDev());
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

    /*
     * check to make sure dimensions match
     */
    public FloatingPointError calculateULP(ImageFloat ref) {
        if (ref.X != X && ref.Y != Y) {
            return new FloatingPointError(-1f, 0f, 0f, 0f);
        }

        for (int j = 0; j < Y; j++) {
            for (int i = 0; i < X; i++) {
                final float v = get(i, j);
                final float r = ref.get(i, j);
                final float ulpFactor = FloatOps.findMaxULP(v, r);
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
