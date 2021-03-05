/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import static java.lang.String.format;
import static uk.ac.manchester.tornado.api.collections.types.Double3.add;
import static uk.ac.manchester.tornado.api.collections.types.Double3.loadFromArray;

import java.nio.DoubleBuffer;

public class VectorDouble3 implements PrimitiveStorage<DoubleBuffer> {

    /**
     * backing array
     */
    final protected double[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 3;

    /**
     * Creates a vector using the provided backing array
     *
     * @param numElements
     * @param array
     */
    protected VectorDouble3(int numElements, double[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array
     */
    public VectorDouble3(double[] array) {
        this(array.length / elementSize, array);
    }

    /**
     * Creates an empty vector with
     *
     * @param numElements
     */
    public VectorDouble3(int numElements) {
        this(numElements, new double[numElements * elementSize]);
    }

    private int toIndex(int index) {
        return (index * elementSize);
    }

    /**
     * Returns the float at the given index of this vector
     *
     * @param index
     * @return value
     */
    public Double3 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    /**
     * Sets the float at the given index of this vector
     *
     * @param index
     * @param value
     */
    public void set(int index, Double3 value) {
        value.storeToArray(storage, toIndex(index));
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     *
     * @param values
     */
    public void set(VectorDouble3 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     *
     * @param values
     */
    public void set(double[] values) {
        VectorDouble3 vector = new VectorDouble3(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public VectorDouble3 duplicate() {
        VectorDouble3 vector = new VectorDouble3(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > elementSize) {
            return String.format("VectorDouble3 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Double3 sum() {
        Double3 result = new Double3();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Double3 min() {
        Double3 result = new Double3();
        for (int i = 0; i < numElements; i++) {
            result = Double3.min(result, get(i));
        }
        return result;
    }

    public Double3 max() {
        Double3 result = new Double3();
        for (int i = 0; i < numElements; i++) {
            result = Double3.max(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return null;
    }

    public DoubleBuffer asBuffer(DoubleBuffer buffer) {
        return asBuffer().put(buffer);
    }

    @Override
    public int size() {
        return storage.length;
    }

    public int getLength() {
        return numElements;
    }

    public double[] getArray() {
        return storage;
    }

}
