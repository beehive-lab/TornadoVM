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
import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;

public final class Matrix2DDouble extends Matrix2DType implements TornadoMatrixInterface<DoubleBuffer> {
    /**
     * backing array.
     */
    private final DoubleArray storage;

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
    public Matrix2DDouble(int rows, int columns, DoubleArray array) {
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
     *
     */
    public Matrix2DDouble(int rows, int columns) {
        this(rows, columns, new DoubleArray(rows * columns));
    }

    public Matrix2DDouble(double[][] matrix) {
        this(matrix.length, matrix[0].length, StorageFormats.toRowMajor(matrix));
    }

    /**
     * Transposes the matrix in-place.
     *
     * @param matrix
     *     matrix to transpose
     */
    public static void transpose(Matrix2DDouble matrix) {
        if (matrix.COLUMNS == matrix.ROWS) {
            // transpose square matrix
            for (int i = 0; i < matrix.ROWS; i++) {
                for (int j = 0; j < i; j++) {
                    final double tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public double get(int i, int j) {
        return storage.get(toRowMajor(i, j, COLUMNS));
    }

    public void set(int i, int j, double value) {
        storage.set(StorageFormats.toRowMajor(i, j, COLUMNS), value);
    }

    public VectorDouble row(int row) {
        int baseIndex = toRowMajor(row, 0, COLUMNS);
        int to = getFinalIndexOfRange(baseIndex);
        int size = to - baseIndex;
        DoubleArray f = new DoubleArray(size);
        int j = 0;
        for (int i = baseIndex; i < to; i++, j++) {
            f.set(j, storage.get(i));
        }
        return new VectorDouble(COLUMNS, f);
    }

    public VectorDouble column(int col) {
        int baseIndex = StorageFormats.toRowMajor(0, col, COLUMNS);
        final VectorDouble v = new VectorDouble(ROWS);
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage.get(baseIndex + (i * COLUMNS)));
        }
        return v;
    }

    public VectorDouble diag() {
        final VectorDouble v = new VectorDouble(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage.get(i * (COLUMNS + 1)));
        }
        return v;
    }

    public void fill(double value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    public void multiply(Matrix2DDouble a, Matrix2DDouble b) {
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                double sum = 0f;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                set(row, col, sum);
            }
        }
    }

    public Matrix2DDouble duplicate() {
        Matrix2DDouble matrix = new Matrix2DDouble(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DDouble m) {
        for (int i = 0; i < m.storage.getSize(); i++) {
            this.storage.set(i, m.storage.get(i));
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

    @Override
    public String toString() {
        String result = String.format("MatrixDouble <%d x %d>", ROWS, COLUMNS);
        if (ROWS < 16 && COLUMNS < 16) {
            result += "\n" + toString(DoubleOps.FMT);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return DoubleBuffer.wrap(storage.toHeapArray());
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
        return storage.getNumBytesOfSegmentWithHeader();
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
