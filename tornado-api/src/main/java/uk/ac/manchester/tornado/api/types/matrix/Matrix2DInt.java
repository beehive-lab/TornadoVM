/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.matrix;

import static uk.ac.manchester.tornado.api.types.utils.StorageFormats.toRowMajor;

import java.lang.foreign.MemorySegment;
import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;
import uk.ac.manchester.tornado.api.types.utils.IntOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;

public final class Matrix2DInt extends Matrix2DType implements TornadoMatrixInterface<IntBuffer> {

    /**
     * backing array.
     */
    private final IntArray storage;

    /**
     * number of elements in the storage.
     */
    private final int numElements;

    /**
     * Storage format for matrix.
     *
     * @param rows
     *     number of rows
     * @param columns
     *     number of columns
     * @param array
     *     array reference which contains data
     */
    public Matrix2DInt(int rows, int columns, IntArray array) {
        super(rows, columns);
        storage = array;
        numElements = columns * rows;
    }

    /**
     * Storage format for matrix.
     *
     * @param rows
     *     number of rows
     * @param columns
     *     number of columns
     */
    public Matrix2DInt(int rows, int columns) {
        this(rows, columns, new IntArray(rows * columns));
    }

    public Matrix2DInt(int[][] matrix) {
        this(matrix.length, matrix[0].length, StorageFormats.toRowMajor(matrix));
    }

    /**
     * Transposes the matrix in-place.
     *
     * @param matrix
     *     matrix to transpose
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

    public static void scale(Matrix2DInt matrix, int value) {
        for (int i = 0; i < matrix.storage.getSize(); i++) {
            matrix.storage.set(i, matrix.storage.get(i) * value);
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public int get(int i, int j) {
        return storage.get(StorageFormats.toRowMajor(i, j, COLUMNS));
    }

    public void set(int i, int j, int value) {
        storage.set(StorageFormats.toRowMajor(i, j, COLUMNS), value);
    }

    public VectorInt row(int row) {
        int baseIndex = toRowMajor(row, 0, COLUMNS);
        int to = getFinalIndexOfRange(baseIndex);
        int size = to - baseIndex;
        IntArray f = new IntArray(size);
        int j = 0;
        for (int i = baseIndex; i < to; i++) {
            f.set(j, storage.get(i));
            j++;
        }
        return new VectorInt(COLUMNS, f);
    }

    public VectorInt column(int col) {
        int index = StorageFormats.toRowMajor(0, col, COLUMNS);
        final VectorInt v = new VectorInt(ROWS);
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage.get(index + (i * COLUMNS)));
        }
        return v;
    }

    public VectorInt diag() {
        final VectorInt v = new VectorInt(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            // v.set(i, storage[i * (COLUMNS + 1)]);
        }
        return v;
    }

    public void fill(int value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
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

    public Matrix2DInt duplicate() {
        Matrix2DInt matrix = new Matrix2DInt(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DInt m) {
        for (int i = 0; i < m.storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
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

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return numElements;
    }

    @Override
    public long getNumBytes() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public long getNumBytesWithHeader() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public MemorySegment getSegment() {
        return storage.getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return storage.getSegmentWithHeader();
    }

}
