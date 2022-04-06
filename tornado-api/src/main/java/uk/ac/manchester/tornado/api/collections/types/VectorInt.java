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

import java.nio.IntBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

public class VectorInt implements PrimitiveStorage<IntBuffer> {

    private final int numElements;
    private final int[] storage;
    private static final int ELEMENT_SIZE = 1;

    /**
     * Creates a vector using the provided backing array
     * 
     * @param numElements
     *            number of elements
     * @param array
     *            reference to the input array
     */
    protected VectorInt(int numElements, int[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with
     * 
     * @param numElements
     *            number of elements
     */
    public VectorInt(int numElements) {
        this(numElements, new int[numElements]);
    }

    /**
     * Creates an new vector from the provided storage
     * 
     * @param storage
     *            vector int array
     */
    public VectorInt(int[] storage) {
        this(storage.length / ELEMENT_SIZE, storage);
    }

    public int[] getArray() {
        return storage;
    }

    /**
     * Returns the int at the given index of this vector
     * 
     * @param index
     *            index value
     * @return int
     */
    public int get(int index) {
        return storage[index];
    }

    /**
     * Sets the int at the given index of this vector
     * 
     * @param index
     *            index value
     * @param value
     *            value to be set in position index
     */
    public void set(int index, int value) {
        storage[index] = value;
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     * 
     * @param values
     *            assign an input vector int to the internal array
     */
    public void set(VectorInt values) {
        for (int i = 0; i < values.storage.length; i++) {
            storage[i] = values.storage[i];
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     * 
     * @param values
     *            assign an input vector int to the internal array
     */
    public void set(int[] values) {
        for (int i = 0; i < values.length; i++) {
            storage[i] = values[i];
        }
    }

    /**
     * Sets all elements to value
     * 
     * @param value
     *            Fill input vector with value
     */
    public void fill(int value) {
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
     * @return {@link VectorInt}
     */
    public VectorInt subVector(int start, int length) {
        final VectorInt v = new VectorInt(length);
        for (int i = 0; i < length; i++) {
            v.storage[i] = storage[i + start];
        }

        return v;
    }

    /**
     * Duplicates this vector
     * 
     * @return {@link VectorInt}
     */
    public VectorInt duplicate() {
        return new VectorInt(Arrays.copyOf(storage, storage.length));
    }

    public static int min(VectorInt v) {
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.min(v.storage[i], result);
        }
        return result;
    }

    public static int max(VectorInt v) {
        int result = Integer.MIN_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.max(v.storage[i], result);
        }
        return result;
    }

    /**
     * Vector equality test
     * 
     * @param vector
     *            Input vector to compare
     * @return true if vectors match
     */
    public boolean isEqual(VectorInt vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * Perform dot-product
     * 
     * @return int value
     */
    public static int dot(VectorInt a, VectorInt b) {
        int sum = 0;
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
        String str = String.format("VectorInt <%d>", numElements);
        if (numElements < 32) {
            str += toString(IntOps.FMT);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    public int getLength() {
        return numElements;
    }
}
