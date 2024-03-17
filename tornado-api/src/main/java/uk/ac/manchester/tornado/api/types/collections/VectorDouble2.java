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

import static uk.ac.manchester.tornado.api.types.vectors.Double2.add;

import java.lang.foreign.MemorySegment;
import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.vectors.Double2;

public final class VectorDouble2 implements TornadoCollectionInterface<DoubleBuffer> {

    public static final Class<VectorDouble2> TYPE = VectorDouble2.class;
    private static final int ELEMENT_SIZE = 2;

    /**
     * backing array.
     */
    private final DoubleArray storage;

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
    VectorDouble2(int numElements, DoubleArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array.
     */
    public VectorDouble2(DoubleArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     */
    public VectorDouble2(int numElements) {
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
    public Double2 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Double2 loadFromArray(final DoubleArray array, int index) {
        final Double2 result = new Double2();
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
    public void set(int index, Double2 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Double2 value, DoubleArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorDouble2 values) {
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
        VectorDouble2 vector = new VectorDouble2(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(double value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    /**
     * Duplicates this vector.
     *
     * @return
     */
    public VectorDouble2 duplicate() {
        VectorDouble2 vector = new VectorDouble2(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorDouble2 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Double2 sum() {
        Double2 result = new Double2();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Double2 min() {
        Double2 result = new Double2();
        for (int i = 0; i < numElements; i++) {
            result = Double2.min(result, get(i));
        }
        return result;
    }

    public Double2 max() {
        Double2 result = new Double2();
        for (int i = 0; i < numElements; i++) {
            result = Double2.max(result, get(i));
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
