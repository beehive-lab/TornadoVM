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

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public final class Matrix3DFloat4 extends Matrix3DType implements TornadoMatrixInterface<FloatBuffer> {

    public static final Class<Matrix3DFloat4> TYPE = Matrix3DFloat4.class;
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
     * @param depth
     *     number of elements in depth
     * @param array
     *     array reference which contains data
     */
    public Matrix3DFloat4(int rows, int columns, int depth, FloatArray array) {
        super(rows, columns, depth);
        storage = array;
        numElements = rows * columns * depth * VECTOR_ELEMENTS;
    }

    /**
     * Storage format for matrix.
     *
     * @param rows
     *     number of rows
     * @param columns
     *     number of columns
     * @param depth
     *     depth-rows
     */
    public Matrix3DFloat4(int rows, int columns, int depth) {
        this(rows, columns, depth, new FloatArray(rows * columns * depth * VECTOR_ELEMENTS));
    }

    public static void scale(Matrix3DFloat4 matrix, float value) {
        for (int i = 0; i < matrix.storage.getSize(); i++) {
            matrix.storage.set(i, matrix.storage.get(i) * value);
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public Float4 get(int i, int j, int k) {
        int baseIndex = StorageFormats.toRowMajor3DVector(i, j, k, DEPTH, COLUMNS, VECTOR_ELEMENTS);
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

    public void set(int i, int j, int k, Float4 value) {
        int baseIndex = StorageFormats.toRowMajor3DVector(i, j, k, DEPTH, COLUMNS, VECTOR_ELEMENTS);
        storeToArray(value, storage, baseIndex);
    }

    private void storeToArray(Float4 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
    }

    public void fill(float value) {
        storage.init(value);
    }

    public Matrix3DFloat4 duplicate() {
        Matrix3DFloat4 matrix = new Matrix3DFloat4(ROWS, COLUMNS, DEPTH);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix3DFloat4 m) {
        for (int i = 0; i < m.storage.getSize(); i++) {
            storage.set(i, m.storage.get(i));
        }
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLUMNS; j++) {
                for (int k = 0; k < DEPTH; k++) {
                    str.append(String.format(fmt, get(i, j, k)) + " ");
                }
            }
            str.append("\n");
        }
        return str.toString().trim();
    }

    @Override
    public String toString() {
        String result = String.format("MatrixFloat <%d x %d x %d>", ROWS, COLUMNS, DEPTH);
        if (ROWS < 16 && COLUMNS < 16 && DEPTH < 16) {
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
