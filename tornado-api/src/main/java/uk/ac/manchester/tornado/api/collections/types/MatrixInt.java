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

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.System.out;
import static java.nio.IntBuffer.wrap;
import static java.util.Arrays.copyOfRange;
import static uk.ac.manchester.tornado.api.collections.types.IntOps.fmt;
import static uk.ac.manchester.tornado.api.collections.types.StorageFormats.toRowMajor;

import java.nio.IntBuffer;

public class MatrixInt implements PrimitiveStorage<IntBuffer> {
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
     * @param height
     *            number of columns
     * @param width
     *            number of rows
     * @param data
     *            array reference which contains data
     */
    public MatrixInt(int width, int height, int[] array) {
        storage = array;
        N = width;
        M = height;
        numElements = width * height;
    }

    /**
     * Storage format for matrix
     * 
     * @param height
     *            number of columns
     * @param width
     *            number of rows
     */
    public MatrixInt(int width, int height) {
        this(width, height, new int[width * height]);
    }

    public MatrixInt(int[][] matrix) {
        this(matrix.length, matrix[0].length, toRowMajor(matrix));
    }

    public int get(int i, int j) {
        return storage[toRowMajor(i, j, N)];
    }

    public void set(int i, int j, int value) {
        storage[toRowMajor(i, j, N)] = value;
    }

    public int M() {
        return M;
    }

    public int N() {
        return N;
    }

    public VectorInt row(int row) {
        int index = toRowMajor(row, 0, N);
        return new VectorInt(N, copyOfRange(storage, index, N));
    }

    public VectorInt column(int col) {
        int index = toRowMajor(0, col, N);
        final VectorInt v = new VectorInt(M);
        for (int i = 0; i < M; i++)
            v.set(i, storage[index + (i * N)]);
        return v;
    }

    public VectorInt diag() {
        final VectorInt v = new VectorInt(min(M, N));
        for (int i = 0; i < M; i++)
            v.set(i, storage[i * (N + 1)]);
        return v;
    }

    public void fill(int value) {
        for (int i = 0; i < storage.length; i++)
            storage[i] = value;
    }

    public void multiply(MatrixInt a, MatrixInt b) {
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

    public void tmultiply(MatrixInt a, MatrixInt b) {
        out.printf("tmult: M=%d (expect %d)\n", M(), a.M());
        out.printf("tmult: N=%d (expect %d)\n", N(), b.M());
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
     * @param m
     *            matrix to transpose
     */
    public static void transpose(MatrixInt matrix) {

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

    public MatrixInt duplicate() {
        MatrixInt matrix = new MatrixInt(N, M);
        matrix.set(this);
        return matrix;
    }

    public void set(MatrixInt m) {
        for (int i = 0; i < m.storage.length; i++)
            storage[i] = m.storage[i];
    }

    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                str += format(fmt, get(i, j)) + " ";
            }
            str += "\n";
        }

        return str.trim();
    }

    @Override
    public String toString() {
        String result = format("MatrixInt <%d x %d>", M, N);
        if (M < 16 && N < 16)
            result += "\n" + toString(fmt);
        return result;
    }

    public static void scale(MatrixInt matrix, int value) {
        for (int i = 0; i < matrix.storage.length; i++)
            matrix.storage[i] *= value;
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

}
