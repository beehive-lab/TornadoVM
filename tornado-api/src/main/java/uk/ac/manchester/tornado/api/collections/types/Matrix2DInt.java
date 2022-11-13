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

import java.nio.IntBuffer;
import java.util.Arrays;

public class Matrix2DInt extends Matrix2DType implements PrimitiveStorage<IntBuffer> {
    /**
     * backing array
     */
    protected final int[] storage;

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
    public Matrix2DInt(int rows, int columns, int[] array) {
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
     */
    public Matrix2DInt(int rows, int columns) {
        this(rows, columns, new int[rows * columns]);
    }

    public Matrix2DInt(int[][] matrix) {
        this(matrix.length, matrix[0].length, StorageFormats.toRowMajor(matrix));
    }

    public int get(int i, int j) {
        return storage[StorageFormats.toRowMajor(i, j, COLUMNS)];
    }

    public void set(int i, int j, int value) {
        storage[StorageFormats.toRowMajor(i, j, COLUMNS)] = value;
    }

    public VectorInt row(int row) {
        int index = StorageFormats.toRowMajor(row, 0, COLUMNS);
        return new VectorInt(COLUMNS, Arrays.copyOfRange(storage, index, getFinalIndexOfRange(index)));
    }

    public VectorInt column(int col) {
        int index = StorageFormats.toRowMajor(0, col, COLUMNS);
        final VectorInt v = new VectorInt(ROWS);
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage[index + (i * COLUMNS)]);
        }
        return v;
    }

    public VectorInt diag() {
        final VectorInt v = new VectorInt(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage[i * (COLUMNS + 1)]);
        }
        return v;
    }

    public void fill(int value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public void multiply(Matrix2DInt a, Matrix2DInt b) {
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                int sum = 0;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                set(row, col, sum);
            }
        }
    }

    public void tmultiply(Matrix2DInt a, Matrix2DInt b) {
        System.out.printf("tmult: M=%d (expect %d)\n", getNumRows(), a.getNumRows());
        System.out.printf("tmult: N=%d (expect %d)\n", getNumColumns(), b.getNumRows());
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < b.getNumRows(); col++) {
                int sum = 0;
                for (int k = 0; k < b.getNumColumns(); k++) {
                    sum += a.get(row, k) * b.get(col, k);
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
    public static void transpose(Matrix2DInt matrix) {

        if (matrix.COLUMNS == matrix.ROWS) {
            // transpose square matrix
            for (int i = 0; i < matrix.ROWS; i++) {
                for (int j = 0; j < i; j++) {
                    final int tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        }
    }

    public Matrix2DInt duplicate() {
        Matrix2DInt matrix = new Matrix2DInt(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DInt m) {
        for (int i = 0; i < m.storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLUMNS; j++) {
                str += String.format(fmt, get(i, j)) + " ";
            }
            str += "\n";
        }
        return str.trim();
    }

    @Override
    public String toString() {
        String result = String.format("MatrixInt <%d x %d>", ROWS, COLUMNS);
        if (ROWS < 16 && COLUMNS < 16) {
            result += "\n" + toString(IntOps.FMT);
        }
        return result;
    }

    public static void scale(Matrix2DInt matrix, int value) {
        for (int i = 0; i < matrix.storage.length; i++) {
            matrix.storage[i] *= value;
        }
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

}
