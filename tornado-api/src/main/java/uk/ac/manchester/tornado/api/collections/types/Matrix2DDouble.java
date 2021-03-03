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

import java.nio.DoubleBuffer;
import java.util.Arrays;

public class Matrix2DDouble implements PrimitiveStorage<DoubleBuffer> {
    /**
     * backing array
     */
    final protected double[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;

    /**
     * Number of rows
     */
    final protected int M;

    /**
     * Number of columns
     */
    final protected int N;

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
    public Matrix2DDouble(int width, int height, double[] array) {
        storage = array;
        N = width;
        M = height;
        numElements = width * height;
    }

    /**
     * Storage format for matrix
     * 
     * @param width
     *            number of rows
     * @param height
     *            number of columns
     * 
     */
    public Matrix2DDouble(int width, int height) {
        this(width, height, new double[width * height]);
    }

    public Matrix2DDouble(double[][] matrix) {
        this(matrix.length, matrix[0].length, StorageFormats.toRowMajor(matrix));
    }

    public double[] getFlattenedArray() {
        return storage;
    }

    public double get(int i, int j) {
        return storage[StorageFormats.toRowMajor(i, j, M)];
    }

    public void set(int i, int j, double value) {
        storage[StorageFormats.toRowMajor(i, j, M)] = value;
    }

    public int M() {
        return M;
    }

    public int N() {
        return N;
    }

    public VectorDouble row(int row) {
        int index = StorageFormats.toRowMajor(row, 0, N);
        return new VectorDouble(N, Arrays.copyOfRange(storage, index, N));
    }

    public VectorDouble column(int col) {
        int index = StorageFormats.toRowMajor(0, col, N);
        final VectorDouble v = new VectorDouble(M);
        for (int i = 0; i < M; i++) {
            v.set(i, storage[index + (i * N)]);
        }
        return v;
    }

    public VectorDouble diag() {
        final VectorDouble v = new VectorDouble(Math.min(M, N));
        for (int i = 0; i < M; i++) {
            v.set(i, storage[i * (N + 1)]);
        }
        return v;
    }

    public void fill(double value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public void multiply(Matrix2DDouble a, Matrix2DDouble b) {
        for (int row = 0; row < M(); row++) {
            for (int col = 0; col < N(); col++) {
                double sum = 0f;
                for (int k = 0; k < b.M(); k++) {
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
    public static void transpose(Matrix2DDouble matrix) {
        if (matrix.N == matrix.M) {
            // transpose square matrix
            for (int i = 0; i < matrix.M; i++) {
                for (int j = 0; j < i; j++) {
                    final double tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        }
    }

    public Matrix2DDouble duplicate() {
        Matrix2DDouble matrix = new Matrix2DDouble(N, M);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DDouble m) {
        for (int i = 0; i < m.storage.length; i++) {
            storage[i] = m.storage[i];
        }
    }

    public String toString(String fmt) {
        String str = "";
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                str += String.format(fmt, get(i, j)) + " ";
            }
            str += "\n";
        }
        return str.trim();
    }

    @Override
    public String toString() {
        String result = String.format("MatrixDouble <%d x %d>", M, N);
        if (M < 16 && N < 16) {
            result += "\n" + toString(DoubleOps.FMT);
        }
        return result;
    }

    public static void scale(Matrix2DDouble matrix, double value) {
        for (int i = 0; i < matrix.storage.length; i++) {
            matrix.storage[i] *= value;
        }
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return DoubleBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }
}
