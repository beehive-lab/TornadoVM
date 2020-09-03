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

import java.nio.IntBuffer;
import java.util.Arrays;

public class Matrix2DInt implements PrimitiveStorage<IntBuffer> {
    /**
     * backing array
     */
    final protected int[] storage;

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
     *            number of columns
     * @param height
     *            number of rows
     * @param array
     *            array reference which contains data
     */
    public Matrix2DInt(int width, int height, int[] array) {
        storage = array;
        N = width;
        M = height;
        numElements = width * height;
    }

    /**
     * Storage format for matrix
     * 
     * @param width
     *            number of columns
     * @param height
     *            number of rows
     */
    public Matrix2DInt(int width, int height) {
        this(width, height, new int[width * height]);
    }

    public Matrix2DInt(int[][] matrix) {
        this(matrix.length, matrix[0].length, StorageFormats.toRowMajor(matrix));
    }

    public int[] getFlattenedArray() {
        return storage;
    }

    public int get(int i, int j) {
        return storage[StorageFormats.toRowMajor(i, j, M)];
    }

    public void set(int i, int j, int value) {
        storage[StorageFormats.toRowMajor(i, j, M)] = value;
    }

    public int M() {
        return M;
    }

    public int N() {
        return N;
    }

    public VectorInt row(int row) {
        int index = StorageFormats.toRowMajor(row, 0, N);
        return new VectorInt(N, Arrays.copyOfRange(storage, index, N));
    }

    public VectorInt column(int col) {
        int index = StorageFormats.toRowMajor(0, col, N);
        final VectorInt v = new VectorInt(M);
        for (int i = 0; i < M; i++) {
            v.set(i, storage[index + (i * N)]);
        }
        return v;
    }

    public VectorInt diag() {
        final VectorInt v = new VectorInt(Math.min(M, N));
        for (int i = 0; i < M; i++) {
            v.set(i, storage[i * (N + 1)]);
        }
        return v;
    }

    public void fill(int value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public void multiply(Matrix2DInt a, Matrix2DInt b) {
        for (int row = 0; row < M(); row++) {
            for (int col = 0; col < N(); col++) {
                int sum = 0;
                for (int k = 0; k < b.M(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                set(row, col, sum);
            }
        }
    }

    public void tmultiply(Matrix2DInt a, Matrix2DInt b) {
        System.out.printf("tmult: M=%d (expect %d)\n", M(), a.M());
        System.out.printf("tmult: N=%d (expect %d)\n", N(), b.M());
        for (int row = 0; row < M(); row++) {
            for (int col = 0; col < b.M(); col++) {
                int sum = 0;
                for (int k = 0; k < b.N(); k++) {
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

        if (matrix.N == matrix.M) {
            // transpose square matrix
            for (int i = 0; i < matrix.M; i++) {
                for (int j = 0; j < i; j++) {
                    final int tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        }
    }

    public Matrix2DInt duplicate() {
        Matrix2DInt matrix = new Matrix2DInt(N, M);
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
        String result = String.format("MatrixInt <%d x %d>", M, N);
        if (M < 16 && N < 16) {
            result += "\n" + toString(IntOps.fmt);
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
