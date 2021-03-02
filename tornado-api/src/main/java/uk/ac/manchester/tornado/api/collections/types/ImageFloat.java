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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

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
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     * @param array
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
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     */
    public ImageFloat(int width, int height) {
        this(width, height, new float[width * height]);
    }

    public ImageFloat(float[][] matrix) {
        this(matrix.length, matrix[0].length, StorageFormats.toRowMajor(matrix));
    }

    public float[] getArray() {
        return storage;
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
     * @return float
     */
    public float get(int i, int j) {
        return storage[StorageFormats.toRowMajor(j, i, X)];
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
        storage[StorageFormats.toRowMajor(j, i, X)] = value;
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
        System.arraycopy(storage, 0, m.storage, 0, storage.length);
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

    public static void scale(ImageFloat image, float alpha) {
        for (int i = 0; i < image.storage.length; i++) {
            image.storage[i] *= alpha;
        }
    }

    public float mean() {
        float result = 0f;
        for (float v : storage) {
            result += v;
        }
        return result / (float) (X * Y);
    }

    public float min() {
        float result = Float.MAX_VALUE;
        for (float v : storage) {
            result = Math.min(result, v);
        }
        return result;
    }

    public float max() {
        float result = Float.MIN_VALUE;
        for (float v : storage) {
            result = Math.max(result, v);
        }
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
        return FloatBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    public FloatingPointError calculateULP(ImageFloat ref) {
        float maxULP = Float.MIN_VALUE;
        float minULP = Float.MAX_VALUE;
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
                final float ulpFactor = FloatOps.findMaxULP(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);
            }
        }
        averageULP /= (float) X * Y;
        return new FloatingPointError(averageULP, minULP, maxULP, -1f);
    }
}
