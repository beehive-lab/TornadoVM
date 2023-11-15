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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.utils;

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

public final class StorageFormats {

    private StorageFormats() {
    }

    /**
     * Converts a given i,j index to a column-major index.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @param ld
     *     length of a column
     * @return int
     */
    public static int toColumnMajor(int i, int j, int ld) {
        return (j * ld) + i;
    }

    /**
     * Converts a given i,j index to a row-major index.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @param yMax
     *     length of a row
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
     * Converts a given i,j index to row-major index.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @param ld
     *     length of a row
     * @param el
     *     length of each element in a row
     * @return int
     */
    public static int toRowMajor(int i, int j, int ld, int el) {
        return (i * ld) + (j * el);
    }

    /**
     * Converts a given i,j,k index to row-major index.
     *
     * @param i
     *     index in 1st dimension
     * @param j
     *     index in 2nd dimension
     * @param k
     *     index in 3rd dimension
     * @param ld1
     *     leading edge length 1st dimension
     * @param ld2
     *     leading edge length 2dn dimension
     * @param el
     *     basic element length
     * @return int
     */
    public static int toRowMajor(int i, int j, int k, int ld1, int ld2, int el) {
        return toRowMajor(i, j, ld1, el) + (k * ld2);
    }

    /**
     * Converts a given i,j index to a row-major index.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @param incm
     *     row step
     * @param incn
     *     col step
     * @param ld
     *     length of a row
     * @return int
     */
    public static int toRowMajor(int i, int j, int incm, int incn, int ld) {
        return (i * ld * incn) + (j * incm);
    }

    /**
     * Converts a given i,j index to Fortran index.
     *
     * @param i
     *     row index
     * @param j
     *     column index
     * @param ld
     *     length of a column
     * @return int
     */
    public static int toFortran(int i, int j, int ld) {
        return ((j - 1) * ld) + (i - 1);
    }

    /**
     * Converts a matrix stored in multidimensional arrays into Row-Major format.
     *
     * @param matrix
     *     input matrix
     * @return double[]
     */
    public static DoubleArray toRowMajor(double[][] matrix) {
        final int cols = matrix[0].length;
        final int rows = matrix.length;
        DoubleArray flattenMatrix = new DoubleArray(rows * cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                flattenMatrix.set(toRowMajor(i, j, cols), matrix[i][j]);
            }
        }
        return flattenMatrix;
    }

    /**
     * Converts a matrix stored in multidimensional arrays into Row-Major format.
     *
     * @param matrix
     *     input matrix
     * @return float[]
     */
    public static FloatArray toRowMajor(float[][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;

        FloatArray flattenMatrix = new FloatArray(dimX * dimY);

        for (int i = 0; i < dimX; i++) {
            for (int j = 0; j < dimY; j++) {
                flattenMatrix.set(toRowMajor(i, j, dimY), matrix[i][j]);
            }
        }

        return flattenMatrix;
    }

    public static FloatArray toRowMajor3D(float[][][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;
        final int dimZ = matrix[0][0].length;
        FloatArray flattenMatrix = new FloatArray(dimX * dimY * dimZ);

        for (int i = 0; i < dimX; i++) {
            for (int j = 0; j < dimY; j++) {
                for (int k = 0; k < dimZ; k++) {
                    int index = toRowMajor3D(i, j, k, dimZ, dimY);
                    flattenMatrix.set(index, matrix[i][j][k]);
                }
            }
        }
        return flattenMatrix;
    }

    public static IntArray toRowMajor(int[][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;

        IntArray matrixRM = new IntArray(dimX * dimY);
        for (int i = 0; i < dimX; i++) {
            for (int j = 0; j < dimY; j++) {
                matrixRM.set(toRowMajor(i, j, dimY), matrix[i][j]);
            }
        }
        return matrixRM;
    }

    /**
     * Converts a matrix stored in multidimensional arrays into Row-Major format.
     *
     * @param matrix
     *     input matrix
     * @return byte[]
     */
    public static ByteArray toRowMajor(byte[][] matrix) {
        final int m = matrix[0].length;
        final int n = matrix.length;
        ByteArray matrixRM = new ByteArray(m * n);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                matrixRM.set(toRowMajor(i, j, m), matrix[i][j]);
            }
        }
        return matrixRM;
    }
}
