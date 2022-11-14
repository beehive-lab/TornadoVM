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
import java.util.Arrays;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

public class Matrix2DFloat4 extends Matrix2DType implements PrimitiveStorage<FloatBuffer> {
    /**
     * backing array
     */
    protected final float[] storage;

    /**
     * number of elements in the storage
     */
    private final int numElements;

    /**
     * Vector-width each position in the matrix
     */
    private static final int VECTOR_ELEMENTS = 4;

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
    public Matrix2DFloat4(int rows, int columns, float[] array) {
        super(rows, columns);
        storage = array;
        numElements = columns * rows * VECTOR_ELEMENTS;
    }

    /**
     * Storage format for matrix
     *
     * @param rows
     *            number of rows
     * @param columns
     *            number of columns
     */
    public Matrix2DFloat4(int rows, int columns) {
        this(rows, columns, new float[rows * columns * VECTOR_ELEMENTS]);
    }

    public Float4 get(int i, int j) {
        int baseIndex = StorageFormats.toRowMajorVector(i, j, COLUMNS, VECTOR_ELEMENTS);
        return Float4.loadFromArray(storage, baseIndex);
    }

    public void set(int i, int j, Float4 value) {
        int baseIndex = StorageFormats.toRowMajorVector(i, j, COLUMNS, VECTOR_ELEMENTS);
        value.storeToArray(storage, baseIndex);
    }

    public VectorFloat row(int row) {
        int index = StorageFormats.toRowMajor(row, 0, COLUMNS);
        return new VectorFloat(COLUMNS, Arrays.copyOfRange(storage, index, getFinalIndexOfRange(index)));
    }

    public VectorFloat column(int col) {
        int index = StorageFormats.toRowMajor(0, col, COLUMNS);
        final VectorFloat v = new VectorFloat(ROWS);
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage[index + (i * COLUMNS)]);
        }
        return v;
    }

    public VectorFloat diag() {
        final VectorFloat v = new VectorFloat(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage[i * (COLUMNS + 1)]);
        }
        return v;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public void multiply(Matrix2DFloat4 a, Matrix2DFloat4 b) {
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                Float4 sum = new Float4();
                for (int k = 0; k < b.getNumRows(); k++) {
                    Float4 fa = a.get(row, k);
                    Float4 fb = b.get(k, col);
                    Float4 fc = Float4.mult(fa, fb);
                    sum = Float4.add(fc, sum);
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
    public static void transpose(Matrix2DFloat4 matrix) {
        if (matrix.COLUMNS == matrix.ROWS) {
            for (int i = 0; i < matrix.ROWS; i++) {
                for (int j = 0; j < i; j++) {
                    final Float4 tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        } else {
            throw new TornadoRuntimeException("Square matrix expected");
        }
    }

    public Matrix2DFloat4 duplicate() {
        Matrix2DFloat4 matrix = new Matrix2DFloat4(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DFloat4 m) {
        for (int i = 0; i < m.storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");

        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLUMNS; j++) {
                str.append(String.format(fmt, get(i, j)) + " ");
            }
            str.append("\n");
        }
        return str.toString().trim();
    }

    public static void scale(Matrix2DFloat4 matrix, float value) {
        for (int i = 0; i < matrix.storage.length; i++) {
            matrix.storage[i] *= value;
        }
    }

    @Override
    public String toString() {
        String result = String.format("MatrixFloat <%d x %d>", ROWS, COLUMNS);
        if (ROWS < 16 && COLUMNS < 16) {
            result += "\n" + toString(FloatOps.FMT);
        }
        return result;
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
