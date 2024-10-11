/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.types.utils;

import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;

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
    @Deprecated
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
     * @param numColumns
     *     length of a row
     * @return int
     */
    public static int toRowMajor(int i, int j, int numColumns) {
        return (i * numColumns) + j;
    }

    public static int toRowMajorVector(int i, int j, int numColumns, int vectorElements) {
        return ((i * numColumns) + j) * vectorElements;
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

    public static IntArray toRowMajor3D(int[][][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;
        final int dimZ = matrix[0][0].length;
        IntArray flattenMatrix = new IntArray(dimX * dimY * dimZ);

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

    public static DoubleArray toRowMajor3D(double[][][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;
        final int dimZ = matrix[0][0].length;
        DoubleArray flattenMatrix = new DoubleArray(dimX * dimY * dimZ);

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

    public static LongArray toRowMajor3D(long[][][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;
        final int dimZ = matrix[0][0].length;
        LongArray flattenMatrix = new LongArray(dimX * dimY * dimZ);

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

    public static ShortArray toRowMajor3D(short[][][] matrix) {
        final int dimX = matrix.length;
        final int dimY = matrix[0].length;
        final int dimZ = matrix[0][0].length;
        ShortArray flattenMatrix = new ShortArray(dimX * dimY * dimZ);

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
