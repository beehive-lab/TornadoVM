/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public final class VectorFloat4 implements TornadoCollectionInterface<FloatBuffer> {

    public static final Class<VectorFloat4> TYPE = VectorFloat4.class;

    private static final int ELEMENT_SIZE = 4;
    /**
     * backing array.
     */
    protected final FloatArray storage;
    /**
     * number of elements in the storage.
     */
    private final int numElements;

    /**
     * Creates a vector using the provided backing array.
     *
     * @param numElements
     *     Number of elements
     * @param array
     *     Array to be stored
     */
    protected VectorFloat4(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array.
     */
    public VectorFloat4(FloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     *     Number of elements
     */
    public VectorFloat4(int numElements) {
        this(numElements, new FloatArray(numElements * ELEMENT_SIZE));
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
    }

    public FloatArray getArray() {
        return storage;
    }

    private int toIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @return value
     */
    public Float4 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Float4 loadFromArray(final FloatArray array, int index) {
        final Float4 result = new Float4();
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
     *     position
     * @param value
     *     value to be stored
     */
    public void set(int index, Float4 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Float4 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
        array.set(index + 3, value.getW());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     *     set a {@link VectorFloat4} into the internal array
     */
    public void set(VectorFloat4 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     *     set an input array into the internal array
     */
    public void set(FloatArray values) {
        VectorFloat4 vector = new VectorFloat4(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(float value) {
        storage.init(value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link VectorFloat4}
     */
    public VectorFloat4 duplicate() {
        VectorFloat4 vector = new VectorFloat4(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorFloat4 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Float4 sum() {
        Float4 result = new Float4();
        for (int i = 0; i < numElements; i++) {
            result = Float4.add(result, get(i));
        }
        return result;
    }

    public Float4 min() {
        Float4 result = new Float4();
        for (int i = 0; i < numElements; i++) {
            result = Float4.min(result, get(i));
        }
        return result;
    }

    public Float4 max() {
        Float4 result = new Float4();
        for (int i = 0; i < numElements; i++) {
            result = Float4.max(result, get(i));
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
        return storage.getSize();
    }

    public int getLength() {
        return numElements;
    }

    public void clear() {
        storage.clear();
    }

}
