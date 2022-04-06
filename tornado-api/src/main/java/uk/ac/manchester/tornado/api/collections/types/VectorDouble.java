/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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

import java.nio.DoubleBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

public class VectorDouble implements PrimitiveStorage<DoubleBuffer> {

    private final int numElements;
    private final double[] storage;
    private static final int ELEMENT_SIZE = 1;

    protected VectorDouble(int numElements, double[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with
     *
     * @param numElements
     *            Number of elements
     */
    public VectorDouble(int numElements) {
        this(numElements, new double[numElements]);
    }

    /**
     * Creates an new vector from the provided storage
     *
     * @param storage
     *            vector to be stored
     */
    public VectorDouble(double[] storage) {
        this(storage.length / ELEMENT_SIZE, storage);
    }

    public double[] getArray() {
        return storage;
    }

    /**
     * Returns the double at the given index of this vector
     *
     * @param index
     *            Position
     * @return value
     */
    public double get(int index) {
        return storage[index];
    }

    /**
     * Sets the double at the given index of this vector
     *
     * @param index
     *            Position
     * @param value
     *            value to be stored
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
     *            input vector to be stored
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
     *            input vector to be stored
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
     * @param length
     *            number of elements
     *
     * @return vector with elements updated
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
     * @return a new Vector of Doubles
     */
    public VectorDouble duplicate() {
        return new VectorDouble(Arrays.copyOf(storage, storage.length));
    }

    public static double min(VectorDouble v) {
        double result = Double.MAX_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.min(v.storage[i], result);
        }

        return result;
    }

    public static double max(VectorDouble v) {
        double result = Double.MIN_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.max(v.storage[i], result);
        }

        return result;
    }

    /**
     * Vector equality test
     *
     * @param vector
     *            input Vector
     *
     * @return true if vectors match
     */
    public boolean isEqual(VectorDouble vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * Perform dot product
     *
     * @return dot-product value
     */
    public static double dot(VectorDouble a, VectorDouble b) {
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
     *            String Format
     * @return String
     */
    public String toString(String fmt) {
        StringBuilder sb = new StringBuilder("[");
        sb.append("[ ");
        for (int i = 0; i < numElements; i++) {
            sb.append(String.format(fmt, get(i)) + " ");
        }
        sb.append("]");
        return sb.toString();
    }

    public String toString() {
        String str = String.format("VectorDouble <%d>", numElements);
        if (numElements < 32) {
            str += toString(DoubleOps.FMT);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);

    }

    @Override
    public DoubleBuffer asBuffer() {
        return DoubleBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    public int getLength() {
        return numElements;
    }
}
