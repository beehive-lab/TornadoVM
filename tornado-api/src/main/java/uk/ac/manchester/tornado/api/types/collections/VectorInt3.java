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

import static uk.ac.manchester.tornado.api.types.vectors.Int3.add;

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.vectors.Int3;

public class VectorInt3 implements PrimitiveStorage<IntBuffer> {

    public static final Class<VectorInt3> TYPE = VectorInt3.class;

    private static final int ELEMENT_SIZE = 3;
    /**
     * backing array.
     */
    protected final IntArray storage;
    /**
     * number of elements in the storage.
     */
    private final int numElements;

    /**
     * Creates a vector using the provided backing arrayR R.
     *
     * @param numElements
     * @param array
     */
    protected VectorInt3(int numElements, IntArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array.
     */
    public VectorInt3(IntArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     */
    public VectorInt3(int numElements) {
        this(numElements, new IntArray(numElements * ELEMENT_SIZE));
    }

    private int toIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the floatr at the given index of this vector.
     *
     * @param index
     *
     * @return value
     */
    public Int3 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Int3 loadFromArray(final IntArray array, int index) {
        final Int3 result = new Int3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     * @param value
     */
    public void set(int index, Int3 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Int3 value, IntArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorInt3 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     */
    public void set(IntArray values) {
        VectorInt3 vector = new VectorInt3(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(int value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    /**
     * Duplicates this vector.
     *
     * @return
     */
    public VectorInt3 duplicate() {
        VectorInt3 vector = new VectorInt3(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorInt3 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Int3 sum() {
        Int3 result = new Int3();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Int3 min() {
        Int3 result = new Int3();
        for (int i = 0; i < numElements; i++) {
            result = Int3.min(result, get(i));
        }
        return result;
    }

    public Int3 max() {
        Int3 result = new Int3();
        for (int i = 0; i < numElements; i++) {
            result = Int3.max(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return storage.getSize();
    }

    public int getLength() {
        return numElements;
    }

    public IntArray getArray() {
        return storage;
    }

    public void clear() {
        storage.clear();
    }

}
