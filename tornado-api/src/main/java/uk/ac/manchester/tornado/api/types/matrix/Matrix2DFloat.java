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

import static java.lang.String.format;
import static uk.ac.manchester.tornado.api.types.utils.FloatOps.FMT;
import static uk.ac.manchester.tornado.api.types.utils.StorageFormats.toRowMajor;

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;

public final class Matrix2DFloat extends Matrix2DType implements TornadoMatrixInterface<FloatBuffer> {

    /**
     * backing array.
     */
    private final FloatArray storage;

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
    public Matrix2DFloat(int rows, int columns, FloatArray array) {
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
    public Matrix2DFloat(int rows, int columns) {
        this(rows, columns, new FloatArray(rows * columns));
    }

    public Matrix2DFloat(float[][] matrix) {
        this(matrix.length, matrix[0].length, toRowMajor(matrix));
    }

    /**
     * Transposes the matrix in-place.
     *
     * @param matrix
     *     matrix to transpose
     */
    public static void transpose(Matrix2DFloat matrix) {
        if (matrix.COLUMNS == matrix.ROWS) {
            // transpose square matrix
            for (int i = 0; i < matrix.ROWS; i++) {
                for (int j = 0; j < i; j++) {
                    final float tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        }
    }

    public static void scale(Matrix2DFloat matrix, float value) {
        for (int i = 0; i < matrix.storage.getSize(); i++) {
            matrix.storage.set(i, matrix.storage.get(i) * value);
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public float get(int i, int j) {
        return storage.get(toRowMajor(i, j, COLUMNS));
    }

    public void set(int i, int j, float value) {
        storage.set(toRowMajor(i, j, COLUMNS), value);
    }

    public VectorFloat row(int row) {
        int baseIndex = toRowMajor(row, 0, COLUMNS);
        int to = getFinalIndexOfRange(baseIndex);
        int size = to - baseIndex;
        FloatArray f = new FloatArray(size);
        int j = 0;
        for (int i = baseIndex; i < to; i++) {
            f.set(j, storage.get(i));
            j++;
        }
        return new VectorFloat(COLUMNS, f);
    }

    public VectorFloat column(int col) {
        int index = toRowMajor(0, col, COLUMNS);
        final VectorFloat vector = new VectorFloat(ROWS);
        for (int i = 0; i < ROWS; i++) {
            vector.set(i, storage.get(index + (i * COLUMNS)));
        }
        return vector;
    }

    public VectorFloat diag() {
        final VectorFloat v = new VectorFloat(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage.get(i * (COLUMNS + 1)));
        }
        return v;
    }

    public void fill(float value) {
        storage.init(value);
    }

    public void multiply(Matrix2DFloat a, Matrix2DFloat b) {
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                float sum = 0f;
                for (int k = 0; k < b.getNumRows(); k++) {
                    sum += a.get(row, k) * b.get(k, col);
                }
                set(row, col, sum);
            }
        }
    }

    public Matrix2DFloat duplicate() {
        Matrix2DFloat matrix = new Matrix2DFloat(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DFloat m) {
        for (int i = 0; i < m.storage.getSize(); i++) {
            this.storage.set(i, m.storage.get(i));
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLUMNS; j++) {
                str.append(format(fmt, get(i, j)) + " ");
            }
            str.append("\n");
        }
        return str.toString().trim();
    }

    @Override
    public String toString() {
        String result = format("MatrixFloat <%d x %d>", ROWS, COLUMNS);
        if (ROWS < 16 && COLUMNS < 16) {
            result += "\n" + toString(FMT);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage.toHeapArray());
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
