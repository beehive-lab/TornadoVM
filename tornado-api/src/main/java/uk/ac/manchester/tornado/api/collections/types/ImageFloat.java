/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.String.format;
import static java.lang.System.arraycopy;
import static java.nio.FloatBuffer.wrap;
import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.sqrt;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.findMaxULP;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.fmt;
import static uk.ac.manchester.tornado.api.collections.types.StorageFormats.toRowMajor;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.annotations.Parallel;

public class ImageFloat implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;

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
     * @param height
     *            number of columns
     * @param width
     *            number of rows
     * @param data
     *            array reference which contains data
     */
    public ImageFloat(int width, int height, float[] array) {
        storage = array;
        X = width;
        Y = height;
        numElements = X * Y;
    }

    /**
     * Storage format for matrix
     * 
     * @param height
     *            number of columns
     * @param width
     *            number of rows
     */
    public ImageFloat(int width, int height) {
        this(width, height, new float[width * height]);
    }

    public ImageFloat(float[][] matrix) {
        this(matrix.length, matrix[0].length, toRowMajor(matrix));
    }

    public float get(int i) {
        return storage[i];
    }

    public void set(int i, float value) {
        storage[i] = value;
    }

    /***
     * returns the ith column of the jth row
     * 
     * @param i
     *            row index
     * @param j
     *            column index
     * @return
     */
    public float get(int i, int j) {
        return storage[toRowMajor(j, i, X)];
    }

    /***
     * sets the ith column of the jth row to value
     * 
     * @param i
     *            row index
     * @param j
     *            column index
     * @param value
     *            new value
     */
    public void set(int i, int j, float value) {
        storage[toRowMajor(j, i, X)] = value;
    }

    public void put(float[] array) {
        arraycopy(array, 0, storage, 0, array.length);
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
        for (int i = 0; i < storage.length; i++)
            storage[i] = m.storage[i];
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < Y; i++) {
            for (int j = 0; j < X; j++) {
                str += format(fmt, get(j, i)) + " ";
            }
            str += "\n";
        }

        return str;
    }

    public String toString() {
        String result = format("ImageFloat <%d x %d>", X, Y);
        if (Y < 16 && X < 16)
            result += "\n" + toString(fmt);
        return result;
    }

    public static void scale(ImageFloat image, float alpha) {
        for (int i = 0; i < image.storage.length; i++)
            image.storage[i] *= alpha;
    }

    public float mean() {
        float result = 0f;
        for (int i = 0; i < storage.length; i++)
            result += storage[i];
        return result / (float) (X * Y);
    }

    public float min() {
        float result = MAX_VALUE;
        for (int i = 0; i < storage.length; i++)
            result = Math.min(result, storage[i]);
        return result;
    }

    public float max() {
        float result = MIN_VALUE;
        for (int i = 0; i < storage.length; i++)
            result = Math.max(result, storage[i]);
        return result;
    }

    public float stdDev() {
        final float mean = mean();
        float varience = 0f;
        for (int i = 0; i < storage.length; i++) {
            float v = storage[i];
            v -= mean;
            v *= v;
            varience = v / (float) X;
        }
        return sqrt(varience);
    }

    public String summerise() {
        return format("ImageFloat<%dx%d>: min=%e, max=%e, mean=%e, sd=%e", X, Y, min(), max(), mean(), stdDev());
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

    public FloatingPointError calculateULP(ImageFloat ref) {
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
                final float v = get(i, j);
                final float r = ref.get(i, j);
                final float ulpFactor = findMaxULP(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);
            }
        }
        averageULP /= (float) X * Y;
        return new FloatingPointError(averageULP, minULP, maxULP, -1f);
    }
}
