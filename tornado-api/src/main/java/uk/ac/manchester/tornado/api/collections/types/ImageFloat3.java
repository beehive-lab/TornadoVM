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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import java.nio.FloatBuffer;
import java.util.Arrays;

public class ImageFloat3 implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 3;

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
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     * @param array
     *            array reference which contains data
     */
    public ImageFloat3(int width, int height, float[] array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y * elementSize;
    }

    /**
     * Storage format for matrix
     * 
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     */
    public ImageFloat3(int width, int height) {
        this(width, height, new float[width * height * elementSize]);
    }

    public ImageFloat3(float[][] matrix) {
        this(matrix.length / elementSize, matrix[0].length / elementSize, StorageFormats.toRowMajor(matrix));
    }

    public float[] getArray() {
        return storage;
    }

    private int toIndex(int x, int y) {
        return elementSize * (x + (y * X));
    }

    public Float3 get(int x) {
        return get(x, 0);
    }

    public void set(int x, Float3 value) {
        set(x, 0, value);
    }

    public Float3 get(int x, int y) {
        final int offset = toIndex(x, y);
        return Float3.loadFromArray(storage, offset);
    }

    public void set(int x, int y, Float3 value) {
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
        Arrays.fill(storage, value);
    }

    public ImageFloat3 duplicate() {
        ImageFloat3 matrix = new ImageFloat3(X, Y);
        matrix.set(this);
        return matrix;
    }

    public void set(ImageFloat3 m) {
        System.arraycopy(storage, 0, m.storage, 0, storage.length);
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

    public String summerise() {
        return String.format("ImageFloat3<%dx%d>: min=%s, max=%s, mean=%s, sd=%s", X, Y, min(), max(), mean(), stdDev());
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage);
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

}
