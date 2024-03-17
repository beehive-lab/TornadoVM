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
package uk.ac.manchester.tornado.api.types.collections;

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Float3;

public final class VectorFloat3 implements TornadoCollectionInterface<FloatBuffer> {

    public static final Class<VectorFloat3> TYPE = VectorFloat3.class;

    private static final int ELEMENT_SIZE = 3;

    /**
     * backing array.
     */
    private final FloatArray storage;

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
     *     array to be copied
     */
    VectorFloat3(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array.
     */
    public VectorFloat3(FloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     *     Number of elements
     */
    public VectorFloat3(int numElements) {
        this(numElements, new FloatArray(numElements * ELEMENT_SIZE));
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
    }

    public FloatArray getArray() {
        return storage;
    }

    private int getIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @return {@link Float3}
     */
    public Float3 get(int index) {
        return loadFromArray(storage, getIndex(index));
    }

    private Float3 loadFromArray(final FloatArray array, int index) {
        final Float3 result = new Float3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @param value
     *     Value to be set
     */
    public void set(int index, Float3 value) {
        storeToArray(value, storage, getIndex(index));
    }

    private void storeToArray(Float3 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     *     set an input array into the internal array
     */
    public void set(VectorFloat3 values) {
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
        VectorFloat3 vector = new VectorFloat3(values);
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
     * @return A new vector
     */
    public VectorFloat3 duplicate() {
        VectorFloat3 vector = new VectorFloat3(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorFloat3 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Float3 sum() {
        Float3 result = new Float3();
        for (int i = 0; i < numElements; i++) {
            result = Float3.add(result, get(i));
        }
        return result;
    }

    public Float3 min() {
        Float3 result = new Float3();
        for (int i = 0; i < numElements; i++) {
            result = Float3.min(result, get(i));
        }
        return result;
    }

    public Float3 max() {
        Float3 result = new Float3();
        for (int i = 0; i < numElements; i++) {
            result = Float3.max(result, get(i));
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

    @Override
    public long getNumBytes() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public long getNumBytesWithHeader() {
        return storage.getNumBytesOfSegmentWithHeader();
    }

    @Override
    public MemorySegment getSegment() {
        return getArray().getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return getArray().getSegmentWithHeader();
    }
}
