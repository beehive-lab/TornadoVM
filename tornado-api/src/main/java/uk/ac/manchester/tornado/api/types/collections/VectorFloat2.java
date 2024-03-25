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

import static uk.ac.manchester.tornado.api.types.vectors.Float2.add;

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Float2;

public final class VectorFloat2 implements TornadoCollectionInterface<FloatBuffer> {

    public static final Class<VectorFloat2> TYPE = VectorFloat2.class;

    private static final int ELEMENT_SIZE = 2;
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
     * @param array
     */
    VectorFloat2(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector.
     *
     * @param numElements
     */
    public VectorFloat2(int numElements) {
        this(numElements, new FloatArray(numElements * ELEMENT_SIZE));
    }

    /**
     * Creates a vector using the provided backing array.
     */
    private VectorFloat2(FloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
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
    public Float2 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Float2 loadFromArray(final FloatArray array, int index) {
        final Float2 result = new Float2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     * @param value
     */
    public void set(int index, Float2 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Float2 value, FloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorFloat2 values) {
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
        VectorFloat2 vector = new VectorFloat2(values);
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
     * @return vector
     */
    public VectorFloat2 duplicate() {
        VectorFloat2 vector = new VectorFloat2(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorFloat2 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Float2 sum() {
        Float2 result = new Float2();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Float2 min() {
        Float2 result = new Float2();
        for (int i = 0; i < numElements; i++) {
            result = Float2.min(result, get(i));
        }
        return result;
    }

    public Float2 max() {
        Float2 result = new Float2();
        for (int i = 0; i < numElements; i++) {
            result = Float2.max(result, get(i));
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

    public FloatArray getArray() {
        return storage;
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
