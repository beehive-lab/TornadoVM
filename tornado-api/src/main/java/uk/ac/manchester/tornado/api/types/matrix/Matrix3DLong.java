/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.utils.LongOps;
import uk.ac.manchester.tornado.api.types.utils.StorageFormats;

import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;

public final class Matrix3DLong extends Matrix3DType implements TornadoMatrixInterface<LongBuffer> {

    /**
     * backing array.
     */
    private final LongArray storage;

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
    public Matrix3DLong(int rows, int columns, int depth, LongArray array) {
        super(rows, columns, depth);
        storage = array;
        numElements = rows * columns * depth;
    }

    /**
     * Storage format for matrix.
     *
     * @param rows
     *     number of columns
     * @param columns
     *     number of columns
     */
    public Matrix3DLong(int rows, int columns, int depth) {
        this(rows, columns, depth, new LongArray(rows * columns * depth));
    }

    public Matrix3DLong(long[][][] matrix) {
        this(matrix.length, matrix[0].length, matrix[0][0].length, StorageFormats.toRowMajor3D(matrix));
    }

    public static void scale(Matrix3DLong matrix, long value) {
        for (int i = 0; i < matrix.storage.getSize(); i++) {
            matrix.storage.set(i, matrix.storage.get(i) * value);
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public long get(int i, int j, int k) {
        return storage.get(StorageFormats.toRowMajor3D(i, j, k, DEPTH, COLUMNS));
    }

    public void set(int i, int j, int k, long value) {
        storage.set(StorageFormats.toRowMajor3D(i, j, k, DEPTH, COLUMNS), value);
    }

    public void fill(long value) {
        storage.init(value);
    }

    public Matrix3DLong duplicate() {
        Matrix3DLong matrix = new Matrix3DLong(ROWS, COLUMNS, DEPTH);
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix3DLong m) {
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
        String result = String.format("Matrix3DLong <%d x %d x %d>", ROWS, COLUMNS, DEPTH);
        if (ROWS < 16 && COLUMNS < 16 && DEPTH < 16) {
            result += "\n" + toString(LongOps.FMT);
        }
        return result;
    }

    @Override
    public void loadFromBuffer(LongBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public LongBuffer asBuffer() {
        return LongBuffer.wrap(storage.toHeapArray());
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
