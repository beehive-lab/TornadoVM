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

public final class StorageFormats {

    private StorageFormats() {
    }

    /**
     * Converts a given i,j index to a column-major index
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @param ld
     *            length of a column
     * @return int
     */
    public static int toColumnMajor(int i, int j, int ld) {
        return (j * ld) + i;
    }

    /**
     * Converts a given i,j index to a row-major index
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @param yMax
     *            length of a row
     * @return int
     */
    public static int toRowMajor(int i, int j, int yMax) {
        return (i * yMax) + j;
    }

    public static int toRowMajorVector(int i, int j, int numColumns, int vectorElements) {
        return (i * numColumns * vectorElements) + j;
    }

    public static int toRowMajor3D(int i, int j, int k, int zMax, int yMax) {
        return (i * zMax * yMax) + (j * zMax) + k;
    }

    public static int toRowMajor3DVector(int i, int j, int k, int zSize, int ySize, int vectorWidth) {
        return (i * zSize * ySize * vectorWidth) + (j * zSize) + k;
    }

    /**
     * Converts a given i,j index to row-major index
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @param ld
     *            length of a row
     * @param el
     *            length of each element in a row
     * @return int
     */
    public static int toRowMajor(int i, int j, int ld, int el) {
        return (i * ld) + (j * el);
    }

    /**
     * Converts a given i,j,k index to row-major index
     *
     * @param i
     *            index in 1st dimension
     * @param j
     *            index in 2nd dimension
     * @param k
     *            index in 3rd dimension
     * @param ld1
     *            leading edge length 1st dimension
     * @param ld2
     *            leading edge length 2dn dimension
     * @param el
     *            basic element length
     * @return int
     */
    public static int toRowMajor(int i, int j, int k, int ld1, int ld2, int el) {
        return toRowMajor(i, j, ld1, el) + (k * ld2);
    }

    /**
     * Converts a given i,j index to a row-major index
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @param incm
     *            row step
     * @param incn
     *            col step
     * @param ld
     *            length of a row
     * @return int
     */
    public static int toRowMajor(int i, int j, int incm, int incn, int ld) {
        return (i * ld * incn) + (j * incm);
    }

    /**
     * Converts a given i,j index to Fortran index
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @param ld
     *            length of a column
     * @return int
     */
    public static int toFortran(int i, int j, int ld) {
        return ((j - 1) * ld) + (i - 1);
    }

    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     *
     * @param matrix
     *            input matrix
     * @return double[]
     */
    public static double[] toRowMajor(double[][] matrix) {
        final int cols = matrix[0].length;
        final int rows = matrix.length;
        double[] flattenMatrix = new double[rows * cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                flattenMatrix[toRowMajor(i, j, cols)] = matrix[i][j];
            }
        }
        return flattenMatrix;
    }

    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     *
     * @param matrix
     *            input matrix
     * @return float[]
     */
    public static float[] toRowMajor(float[][] matrix) {
        final int M = matrix.length;
        final int N = matrix[0].length;

        float[] flattenMatrix = new float[M * N];

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                flattenMatrix[toRowMajor(i, j, N)] = matrix[i][j];
            }
        }

        return flattenMatrix;
    }

    public static float[] toRowMajor3D(float[][][] matrix) {
        final int X = matrix.length;
        final int Y = matrix[0].length;
        final int Z = matrix[0][0].length;
        float[] flattenMatrix = new float[X * Y * Z];

        for (int i = 0; i < X; i++) {
            for (int j = 0; j < Y; j++) {
                for (int k = 0; k < Z; k++) {
                    int index = toRowMajor3D(i, j, k, Z, Y);
                    flattenMatrix[index] = matrix[i][j][k];
                }
            }
        }
        return flattenMatrix;
    }

    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     *
     * @param matrix
     *            input matrix
     * @return int[]
     */
    public static int[] toRowMajor(int[][] matrix) {
        final int M = matrix.length;
        final int N = matrix[0].length;

        int[] matrixRM = new int[M * N];
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                matrixRM[toRowMajor(i, j, N)] = matrix[i][j];
            }
        }
        return matrixRM;
    }

    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     *
     * @param matrix
     *            input matrix
     * @return byte[]
     */
    public static byte[] toRowMajor(byte[][] matrix) {
        final int m = matrix[0].length;
        final int n = matrix.length;
        byte[] matrixRM = new byte[m * n];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                matrixRM[toRowMajor(i, j, m)] = matrix[i][j];
            }
        }
        return matrixRM;
    }
}
