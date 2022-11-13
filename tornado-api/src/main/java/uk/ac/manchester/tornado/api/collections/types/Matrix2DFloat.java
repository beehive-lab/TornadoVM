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

import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.FMT;
import static uk.ac.manchester.tornado.api.collections.types.StorageFormats.toRowMajor;

import java.nio.FloatBuffer;
import java.util.Arrays;

public class Matrix2DFloat extends Matrix2DType implements PrimitiveStorage<FloatBuffer> {

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
    public Matrix2DFloat(int rows, int columns, float[] array) {
        super(rows, columns);
        storage = array;
        numElements = columns * rows;
    }

    /**
     * Storage format for matrix
     *
     * @param rows
     *            number of rows
     * @param columns
     *            number of columns
     *
     */
    public Matrix2DFloat(int rows, int columns) {
        this(rows, columns, new float[rows * columns]);
    }

    public Matrix2DFloat(float[][] matrix) {
        this(matrix.length, matrix[0].length, toRowMajor(matrix));
    }

    public float get(int i, int j) {
        return storage[toRowMajor(i, j, COLUMNS)];
    }

    public void set(int i, int j, float value) {
        storage[toRowMajor(i, j, COLUMNS)] = value;
    }

    public VectorFloat row(int row) {
        int index = toRowMajor(row, 0, COLUMNS);
        return new VectorFloat(COLUMNS, Arrays.copyOfRange(storage, index, getFinalIndexOfRange(index)));
    }

    public VectorFloat column(int col) {
        int index = toRowMajor(0, col, COLUMNS);
        final VectorFloat vector = new VectorFloat(ROWS);
        for (int i = 0; i < ROWS; i++) {
            vector.set(i, storage[index + (i * COLUMNS)]);
        }
        return vector;
    }

    public VectorFloat diag() {
        final VectorFloat v = new VectorFloat(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage[i * (COLUMNS + 1)]);
        }
        return v;
    }

    public void fill(float value) {
        for (int i = 0; i < this.storage.length; i++) {
            this.storage[i] = value;
        }
    }

    public void multiply(Matrix2DFloat a, Matrix2DFloat b) {
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                float sum = 0f;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                set(row, col, sum);
            }
        }
    }

    /**
     * Transposes the matrix in-place
     *
     * @param matrix
     *            matrix to transpose
     */
    public static void transpose(Matrix2DFloat matrix) {
        if (matrix.COLUMNS == matrix.ROWS) {
            // transpose square matrix
            for (int i = 0; i < matrix.ROWS; i++) {
                for (int j = 0; j < i; j++) {
                    final float tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        }
    }

    public Matrix2DFloat duplicate() {
        Matrix2DFloat matrix = new Matrix2DFloat(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DFloat m) {
        for (int i = 0; i < m.storage.length; i++) {
            this.storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLUMNS; j++) {
                str.append(format(fmt, get(i, j)) + " ");
            }
            str.append("\n");
        }
        return str.toString().trim();
    }

    @Override
    public String toString() {
        String result = format("MatrixFloat <%d x %d>", ROWS, COLUMNS);
        if (ROWS < 16 && COLUMNS < 16) {
            result += "\n" + toString(FMT);
        }
        return result;
    }

    public static void scale(Matrix2DFloat matrix, float value) {
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
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }
}
