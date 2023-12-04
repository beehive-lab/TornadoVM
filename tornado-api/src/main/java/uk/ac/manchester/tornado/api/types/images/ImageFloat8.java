/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.images;

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
    protected final FloatArray storage;
    /**
     * Number of rows.
     */
    protected final int Y;
    /**
     * Number of columns.
     */
    protected final int X;
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
}
