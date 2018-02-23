/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.collections.types;

import java.nio.FloatBuffer;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static tornado.collections.types.Float8.findULPDistance;
import static tornado.collections.types.Float8.sqrt;
import static tornado.collections.types.FloatOps.fmt8;
import static tornado.collections.types.StorageFormats.toRowMajor;

public class ImageFloat8 implements PrimitiveStorage<FloatBuffer>, Container<Float8> {

    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 8;

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
     * @param width  number of rows
     * @param data   array reference which contains data
     */
    public ImageFloat8(int width, int height, float[] array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * elementSize;
    }

    /**
     * Storage format for matrix
     *
     * @param height number of columns
     * @param width  number of rows
     */
    public ImageFloat8(int width, int height) {
        this(width, height, new float[width * height * elementSize]);
    }

    public ImageFloat8(float[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, toRowMajor(matrix));
    }

    private final int toIndex(int x, int y) {
        return (x * elementSize) + (y * elementSize * X);
    }

    public int numElements() {
        return numElements;
    }

    public Float8 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Float8 value) {
        final int offset = toIndex(x, 0);
        value.storeToArray(storage, offset);
    }

    public Float8 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Float8.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Float8 value) {
        final int offset = toIndex(x, y);
        value.storeToArray(storage, offset);
    }

    public int X() {
        return X;
    }

    public int Y() {
        return Y;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public ImageFloat8 duplicate() {
        ImageFloat8 image = new ImageFloat8(X, Y);
        image.set(this);
        return image;
    }

    public void set(ImageFloat8 m) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str += get(j, i).toString(fmt) + "\n";
            }
        }

        return str;
    }

    @Override
    public String toString() {
        String result = format("ImageFloat8 <%d x %d>", X, Y);
        if (X <= 4 && Y <= 4) {
            result += "\n" + toString(fmt8);
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
        Float8 result = new Float8(MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE, MAX_VALUE);

        for (int row = 0; row < Y; row++) {
            for (int col = 0; col < X; col++) {
                result = Float8.min(result, get(col, row));
            }
        }

        return result;
    }

    public Float8 max() {
        Float8 result = new Float8(MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE, MIN_VALUE);

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
        return sqrt(varience);
    }

    public String summerise() {
        return format("ImageFloat8<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    public FloatingPointError calculateULP(ImageFloat8 ref) {
        float maxULP = MIN_VALUE;
        float minULP = MAX_VALUE;
        float averageULP = 0f;

        /*
         * check to make sure dimensions match
         */
        if (ref.X != X && ref.Y != Y) {
            return new FloatingPointError(-1f, 0f, 0f, 0f);
        }

        for (int j = 0; j < Y; j++) {
            for (int i = 0; i < X; i++) {
                final Float8 v = get(i, j);
                final Float8 r = ref.get(i, j);

                final float ulpFactor = findULPDistance(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);

            }
        }

        averageULP /= (float) X * Y;

        return new FloatingPointError(averageULP, minULP, maxULP, -1f);
    }
}
