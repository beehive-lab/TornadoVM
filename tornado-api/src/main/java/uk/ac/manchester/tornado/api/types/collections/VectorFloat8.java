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

import static uk.ac.manchester.tornado.api.types.vectors.Float8.add;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.vectors.Float8;

public class VectorFloat8 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<VectorFloat8> TYPE = VectorFloat8.class;

    private static final int ELEMENT_SIZE = 8;
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
     * @param array
     */
    protected VectorFloat8(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array.
     */
    public VectorFloat8(FloatArray array) {
        this((array.getSize() / ELEMENT_SIZE), array);
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     */
    public VectorFloat8(int numElements) {
        this(numElements, new FloatArray(numElements * ELEMENT_SIZE));
    }

    private int toIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the float at the given index of this vector.
     *
     * @param index
     *     int
     *
     * @return value
     */
    public Float8 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Float8 loadFromArray(final FloatArray array, int index) {
        final Float8 result = new Float8();
        result.setS0(array.get(index));
        result.setS1(array.get(index + 1));
        result.setS2(array.get(index + 2));
        result.setS3(array.get(index + 3));
        result.setS4(array.get(index + 4));
        result.setS5(array.get(index + 5));
        result.setS6(array.get(index + 6));
        result.setS7(array.get(index + 7));
        return result;
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     * @param value
     */
    public void set(int index, Float8 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Float8 value, FloatArray array, int index) {
        for (int i = 0; i < ELEMENT_SIZE; i++) {
            array.set(index + i, value.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorFloat8 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     */
    public void set(FloatArray values) {
        VectorFloat8 vector = new VectorFloat8(values);
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
    public VectorFloat8 duplicate() {
        VectorFloat8 vector = new VectorFloat8(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorFloat8 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Float8 sum() {
        Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Float8 min() {
        Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result = Float8.min(result, get(i));
        }
        return result;
    }

    public Float8 max() {
        Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result = Float8.max(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return storage.getSegment().asByteBuffer().asFloatBuffer();
    }

    @Override
    public int size() {
        return storage.getSize();
    }

    public int getLength() {
        return numElements;
    }

    public FloatArray getArray() {
        return storage;
    }

    public void clear() {
        storage.clear();
    }
}
