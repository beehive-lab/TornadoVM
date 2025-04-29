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
import uk.ac.manchester.tornado.api.types.utils.FloatingPointError;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public final class Matrix4x4Float implements TornadoMatrixInterface<FloatBuffer> {

    public static final Class<Matrix4x4Float> TYPE = Matrix4x4Float.class;

    /**
     * Number of rows.
     */
    private static final int ROWS = 4;
    /**
     * Number of columns.
     */
    private static final int COLUMNS = 4;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 16;
    /**
     * backing array.
     */
    private final FloatArray storage;

    public Matrix4x4Float() {
        this(new FloatArray(NUM_ELEMENTS));
    }

    public Matrix4x4Float(FloatArray array) {
        storage = array;
    }

    private int toIndex(int i, int j) {
        return j + (i * COLUMNS);
    }

    public float get(int index) {
        return storage.get(index);
    }

    public void set(int index, float value) {
        storage.set(index, value);
    }

    /**
     * Returns the value.
     *
     * @param i
     *     row index
     * @param j
     *     col index
     * @return float
     */
    public float get(int i, int j) {
        return storage.get(toIndex(i, j));
    }

    /**
     * Sets the value.
     *
     * @param i
     *     row index
     * @param j
     *     col index
     * @return float
     */
    public void set(int i, int j, float value) {
        storage.set(toIndex(i, j), value);
    }

    /**
     * Returns the number of rows in this matrix.
     *
     * @return int
     */
    public int getNumRows() {
        return ROWS;
    }

    /**
     * Returns the number of columns in the matrix.
     *
     * @return int
     */
    public int getNumColumns() {
        return COLUMNS;
    }

    public Float4 row(int row) {
        int offset = ROWS * row;
        return loadFromArray(storage, offset);
    }

    private Float4 loadFromArray(final FloatArray array, int index) {
        final Float4 result = new Float4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    public Float4 column(int col) {
        return new Float4(get(col), get(col + ROWS), get(col + (2 * ROWS)), get(col + (3 * ROWS)));
    }

    public Float4 diag() {
        return new Float4(get(0), get(1 + ROWS), get(2 + (2 * ROWS)), get(3 + (3 * ROWS)));
    }

    public void fill(float value) {
        storage.init(value);
    }

    public Matrix4x4Float duplicate() {
        Matrix4x4Float matrix = new Matrix4x4Float();
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix4x4Float m) {
        for (int i = 0; i < ROWS; i++) {
            int offset = ROWS * i;
            storeToArray(m.row(i), storage, offset);
        }
    }

    private void storeToArray(Float4 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
    }

    public String toString(String fmt) {
        StringBuilder str = new StringBuilder("");

        for (int i = 0; i < ROWS; i++) {
            str.append(row(i).toString(fmt) + "\n");
        }
        return str.toString().trim();
    }

    public String toString() {
        String result = String.format("MatrixFloat <%d x %d>", ROWS, COLUMNS);
        result += "\n" + toString(FloatOps.FMT_4_M);
        return result;
    }

    /**
     * Turns this matrix into an identity matrix.
     */
    public void identity() {
        fill(0f);
        set(0, 1f);
        set(1 + ROWS, 1f);
        set(2 + (2 * ROWS), 1f);
        set(3 + (3 * ROWS), 1f);
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
        return NUM_ELEMENTS;
    }

    public FloatingPointError calculateULP(Matrix4x4Float ref) {
        float maxULP = Float.MIN_VALUE;
        float minULP = Float.MAX_VALUE;
        float averageULP = 0f;

        /*
         * check to make sure dimensions match
         */
        if (ref.ROWS != ROWS && ref.COLUMNS != COLUMNS) {
            return new FloatingPointError(-1f, 0f, 0f, 0f);
        }

        for (int j = 0; j < ROWS; j++) {
            for (int i = 0; i < COLUMNS; i++) {
                final float v = get(i, j);
                final float r = ref.get(i, j);

                final float ulpFactor = FloatOps.findMaxULP(v, r);
                averageULP += ulpFactor;
                minULP = Math.min(ulpFactor, minULP);
                maxULP = Math.max(ulpFactor, maxULP);

            }
        }

        averageULP /= (float) ROWS * COLUMNS;

        return new FloatingPointError(averageULP, minULP, maxULP, -1f);
    }

    public void clear() {
        storage.clear();
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
