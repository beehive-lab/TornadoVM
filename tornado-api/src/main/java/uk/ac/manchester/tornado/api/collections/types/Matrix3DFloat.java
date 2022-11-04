/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

public class Matrix3DFloat extends Matrix3DType implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    protected final float[] storage;

    /**
     * number of elements in the storage
     */
    private final int numElements;

    /**
     * Storage format for matrix
     *
     * @param rows
     *            number of rows
     * @param columns
     *            number of columns
     * @param array
     *            array reference which contains data
     */
    public Matrix3DFloat(int rows, int columns, int depth, float[] array) {
        super(rows, columns, depth);
        storage = array;
        numElements = rows * columns * depth;
    }

    /**
     * Storage format for matrix
     *
     * @param rows
     *            number of columns
     * @param columns
     *            number of columns
     */
    public Matrix3DFloat(int rows, int columns, int depth) {
        this(rows, columns, depth, new float[rows * columns * depth]);
    }

    public Matrix3DFloat(float[][][] matrix) {
        this(matrix.length, matrix[0].length, matrix[0][0].length, StorageFormats.toRowMajor3D(matrix));
    }

    public float get(int i, int j, int k) {
        return storage[StorageFormats.toRowMajor3D(i, j, k, DEPTH, COLUMNS)];
    }

    public void set(int i, int j, int k, float value) {
        storage[StorageFormats.toRowMajor3D(i, j, k, DEPTH, COLUMNS)] = value;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public Matrix3DFloat duplicate() {
        Matrix3DFloat matrix = new Matrix3DFloat(ROWS, COLUMNS, DEPTH);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix3DFloat m) {
        for (int i = 0; i < m.storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLUMNS; j++) {
                for (int k = 0; k < DEPTH; k++) {
                    str.append(String.format(fmt, get(i, j, k)) + " ");
                }
            }
            str.append("\n");
        }
        return str.toString().trim();
    }

    @Override
    public String toString() {
        String result = String.format("Matrix3DFloat <%d x %d x %d>", ROWS, COLUMNS, DEPTH);
        if (ROWS < 16 && COLUMNS < 16 && DEPTH < 16) {
            result += "\n" + toString(FloatOps.FMT);
        }
        return result;
    }

    public static void scale(Matrix3DFloat matrix, float value) {
        for (int i = 0; i < matrix.storage.length; i++) {
            matrix.storage[i] *= value;
        }
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
}
