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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.collections;

import static uk.ac.manchester.tornado.api.types.vectors.Double4.add;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.vectors.Double4;

public class VectorDouble4 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<VectorDouble4> TYPE = VectorDouble4.class;

    private static final int ELEMENT_SIZE = 4;
    /**
     * backing array.
     */
    protected final DoubleArray storage;
    /**
     * number of elements in the storage.
     */
    private final int numElements;

    /**
     * Creates a vector using the provided backing array.
     *
     * @param numElements
     * @param array
     */
    protected VectorDouble4(int numElements, DoubleArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array.
     */
    public VectorDouble4(DoubleArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     */
    public VectorDouble4(int numElements) {
        this(numElements, new DoubleArray(numElements * ELEMENT_SIZE));
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
    }

    private int toIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the float at the given index of this vector.
     *
     * @param index
     * @return value
     */
    public Double4 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Double4 loadFromArray(final DoubleArray array, int index) {
        final Double4 result = new Double4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     * @param value
     */
    public void set(int index, Double4 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Double4 value, DoubleArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorDouble4 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     */
    public void set(DoubleArray values) {
        VectorDouble4 vector = new VectorDouble4(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(float value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    /**
     * Duplicates this vector.
     *
     * @return
     */
    public VectorDouble4 duplicate() {
        VectorDouble4 vector = new VectorDouble4(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorDouble4 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Double4 sum() {
        Double4 result = new Double4();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Double4 min() {
        Double4 result = new Double4();
        for (int i = 0; i < numElements; i++) {
            result = Double4.min(result, get(i));
        }
        return result;
    }

    public Double4 max() {
        Double4 result = new Double4();
        for (int i = 0; i < numElements; i++) {
            result = Double4.max(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return DoubleBuffer.wrap(storage.toHeapArray());
    }

    public DoubleBuffer asBuffer(DoubleBuffer buffer) {
        return asBuffer().put(buffer);
    }

    @Override
    public int size() {
        return storage.getSize();
    }

    public int getLength() {
        return numElements;
    }

    public DoubleArray getArray() {
        return storage;
    }

    public void clear() {
        storage.clear();
    }

}
