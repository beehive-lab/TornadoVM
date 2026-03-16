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

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public final class Matrix2DFloat4 extends Matrix2DType implements TornadoMatrixInterface<FloatBuffer> {

    public static final Class<Matrix2DFloat4> TYPE = Matrix2DFloat4.class;

    /**
     * Vector-width each position in the matrix.
     */
    private static final int VECTOR_ELEMENTS = 4;
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
    public Matrix2DFloat4(int rows, int columns, FloatArray array) {
        super(rows, columns);
        storage = array;
        numElements = columns * rows * VECTOR_ELEMENTS;
    }

    /**
     * Storage format for matrix.
     *
     * @param rows
     *     number of rows
     * @param columns
     *     number of columns
     */
    public Matrix2DFloat4(int rows, int columns) {
        this(rows, columns, new FloatArray(rows * columns * VECTOR_ELEMENTS));
    }

    /**
     * Transposes the matrix in-place.
     *
     * @param matrix
     *     matrix to transpose
     */
    public static void transpose(Matrix2DFloat4 matrix) {
        if (matrix.COLUMNS == matrix.ROWS) {
            for (int i = 0; i < matrix.ROWS; i++) {
                for (int j = 0; j < i; j++) {
                    final Float4 tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        } else {
            throw new TornadoRuntimeException("Square matrix expected");
        }
    }

    public static void scale(Matrix2DFloat4 matrix, float value) {
        for (int i = 0; i < matrix.storage.getSize(); i++) {
            matrix.storage.set(i, matrix.storage.get(i) * value);
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public Float4 get(int i, int j) {
        int baseIndex = StorageFormats.toRowMajorVector(i, j, COLUMNS, VECTOR_ELEMENTS);
        return loadFromArray(storage, baseIndex);
    }

    private Float4 loadFromArray(final FloatArray array, int index) {
        final Float4 result = new Float4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    public void set(int i, int j, Float4 value) {
        int baseIndex = StorageFormats.toRowMajorVector(i, j, COLUMNS, VECTOR_ELEMENTS);
        storeToArray(value, storage, baseIndex);
    }

    private void storeToArray(Float4 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
    }

    public VectorFloat row(int row) {
        int index = StorageFormats.toRowMajor(row, 0, COLUMNS);
        int to = getFinalIndexOfRange(index);
        int size = to - index;
        FloatArray f = new FloatArray(size);
        int j = 0;
        for (int i = index; i < to; i++) {
            f.set(j, storage.get(i));
            j++;
        }
        return new VectorFloat(COLUMNS, f);
    }

    public VectorFloat column(int col) {
        int index = StorageFormats.toRowMajor(0, col, COLUMNS);
        final VectorFloat v = new VectorFloat(ROWS);
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage.get(index + (i * COLUMNS)));
        }
        return v;
    }

    public VectorFloat diag() {
        final VectorFloat v = new VectorFloat(Math.min(ROWS, COLUMNS));
        for (int i = 0; i < ROWS; i++) {
            v.set(i, storage.get(i * (COLUMNS + 1)));
        }
        return v;
    }

    public void fill(float value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    public void multiply(Matrix2DFloat4 a, Matrix2DFloat4 b) {
        for (int row = 0; row < getNumRows(); row++) {
            for (int col = 0; col < getNumColumns(); col++) {
                Float4 sum = new Float4();
                for (int k = 0; k < b.getNumRows(); k++) {
                    Float4 fa = a.get(row, k);
                    Float4 fb = b.get(k, col);
                    Float4 fc = Float4.mult(fa, fb);
                    sum = Float4.add(fc, sum);
                }
                set(row, col, sum);
            }
        }
    }

    public Matrix2DFloat4 duplicate() {
        Matrix2DFloat4 matrix = new Matrix2DFloat4(ROWS, COLUMNS);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix2DFloat4 m) {
        for (int i = 0; i < m.storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
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
        String result = String.format("MatrixFloat <%d x %d>", ROWS, COLUMNS);
        if (ROWS < 16 && COLUMNS < 16) {
            result += "\n" + toString(FloatOps.FMT);
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

    public FloatArray getArray() {
        return storage;
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
