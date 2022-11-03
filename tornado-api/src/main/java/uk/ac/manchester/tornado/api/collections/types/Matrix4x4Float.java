/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

import java.nio.FloatBuffer;

public class Matrix4x4Float implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    protected final float[] storage;

    /**
     * number of elements in the storage
     */
    private static final int NUM_ELEMENTS = 16;

    /**
     * Number of rows
     */
    protected static final int ROWS = 4;

    /**
     * Number of columns
     */
    protected static final int COLUMNS = 4;

    public Matrix4x4Float() {
        this(new float[NUM_ELEMENTS]);
    }

    public Matrix4x4Float(float[] array) {
        storage = array;
    }

    private int toIndex(int i, int j) {
        return j + (i * COLUMNS);
    }

    private float get(int index) {
        return storage[index];
    }

    private void set(int index, float value) {
        storage[index] = value;
    }

    /**
     * Returns the value
     *
     * @param i
     *            row index
     * @param j
     *            col index
     * @return float
     */
    public float get(int i, int j) {
        return storage[toIndex(i, j)];
    }

    /**
     * Sets the value
     *
     * @param i
     *            row index
     * @param j
     *            col index
     * @return float
     */
    public void set(int i, int j, float value) {
        storage[toIndex(i, j)] = value;
    }

    /**
     * Returns the number of rows in this matrix
     *
     * @return int
     */
    public int getNumRows() {
        return ROWS;
    }

    /**
     * Returns the number of columns in the matrix
     *
     * @return int
     */
    public int getNumColumns() {
        return COLUMNS;
    }

    public Float4 row(int row) {
        int offset = ROWS * row;
        return Float4.loadFromArray(storage, offset);
    }

    public Float4 column(int col) {
        return new Float4(get(col), get(col + ROWS), get(col + (2 * ROWS)), get(col + (3 * ROWS)));
    }

    public Float4 diag() {
        return new Float4(get(0), get(1 + ROWS), get(2 + (2 * ROWS)), get(3 + (3 * ROWS)));
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    public Matrix4x4Float duplicate() {
        Matrix4x4Float matrix = new Matrix4x4Float();
        matrix.set(this);
        return matrix;
    }

    public void set(Matrix4x4Float m) {
        for (int i = 0; i < ROWS; i++) {
            int offset = ROWS * i;
            m.row(i).storeToArray(storage, offset);
        }
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
     * Turns this matrix into an identity matrix
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
        return FloatBuffer.wrap(storage);
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

}
