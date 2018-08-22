/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.Double.MAX_VALUE;
import static java.lang.Double.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.DoubleBuffer.wrap;
import static java.util.Arrays.copyOf;
import static uk.ac.manchester.tornado.api.collections.types.DoubleOps.fmt;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

public class VectorDouble implements PrimitiveStorage<DoubleBuffer> {

    private final int numElements;
    private final double[] storage;
    private static final int elementSize = 1;

    /**
     * Creates a vector using the provided backing array
     *
     * @param numElements
     * @param offset
     * @param step
     * @param elementSize
     * @param array
     */
    protected VectorDouble(int numElements, double[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with
     *
     * @param numElements
     */
    public VectorDouble(int numElements) {
        this(numElements, new double[numElements]);
    }

    /**
     * Creates an new vector from the provided storage
     *
     * @param storage
     */
    public VectorDouble(double[] storage) {
        this(storage.length / elementSize, storage);
    }

    /**
     * Returns the double at the given index of this vector
     *
     * @param index
     *
     * @return value
     */
    public double get(int index) {
        return storage[index];
    }

    /**
     * Sets the double at the given index of this vector
     *
     * @param index
     * @param value
     */
    public void set(int index, double value) {
        storage[index] = value;
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     *
     * @param values
     */
    public void set(VectorDouble values) {
        for (int i = 0; i < values.storage.length; i++) {
            storage[i] = values.storage[i];
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     *
     * @param values
     */
    public void set(double[] values) {
        for (int i = 0; i < values.length; i++) {
            storage[i] = values[i];
        }
    }

    /**
     * Sets all elements to value
     *
     * @param value
     */
    public void fill(double value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Returns slice of this vector
     *
     * @param start
     *            starting index
     * @param numElements
     *            number of elements
     *
     * @return
     */
    public VectorDouble subVector(int start, int length) {
        final VectorDouble v = new VectorDouble(length);
        for (int i = 0; i < length; i++) {
            v.storage[i] = storage[i + start];
        }

        return v;
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public VectorDouble duplicate() {
        return new VectorDouble(copyOf(storage, storage.length));
    }

    public static double min(VectorDouble v) {
        double result = MAX_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.min(v.storage[i], result);
        }

        return result;
    }

    public static double max(VectorDouble v) {
        double result = MIN_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.max(v.storage[i], result);
        }

        return result;
    }

    /**
     * Vector equality test
     *
     * @param vector
     *
     * @return true if vectors match
     */
    public boolean isEqual(VectorDouble vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * dot product (this . this)
     *
     * @return
     */
    public static final double dot(VectorDouble a, VectorDouble b) {
        double sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += a.get(i) * b.get(i);
        }
        return sum;
    }

    /**
     * Prints the vector using the specified format string
     *
     * @param fmt
     *
     * @return
     */
    public String toString(String fmt) {
        String str = "[ ";

        for (int i = 0; i < numElements; i++) {
            str += format(fmt, get(i)) + " ";
        }

        str += "]";

        return str;
    }

    public String toString() {
        String str = format("VectorDouble <%d>", numElements);
        if (numElements < 32) {
            str += toString(fmt);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);

    }

    @Override
    public DoubleBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }
}
