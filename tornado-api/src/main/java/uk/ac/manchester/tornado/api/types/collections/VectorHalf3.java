/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Half3;

import java.lang.foreign.MemorySegment;
import java.nio.ShortBuffer;

import static uk.ac.manchester.tornado.api.types.vectors.Half3.add;

public final class VectorHalf3 implements TornadoCollectionInterface<ShortBuffer> {

    public static final Class<VectorHalf3> TYPE = VectorHalf3.class;

    private static final int ELEMENT_SIZE = 3;
    private final int numElements;
    private final HalfFloatArray storage;

    protected VectorHalf3(int numElements, HalfFloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector.
     *
     * @param numElements
     */
    public VectorHalf3(int numElements) {
        this(numElements, new HalfFloatArray(numElements * ELEMENT_SIZE));
    }

    /**
     * Creates a vector using the provided backing array.
     */
    private VectorHalf3(HalfFloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
    }

    private int toIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the {@link Half3} at the given index of this vector.
     *
     * @param index
     * @return value
     */
    public Half3 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Half3 loadFromArray(final HalfFloatArray array, int index) {
        final Half3 result = new Half3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    /**
     * Sets the {@link Half3} at the given index of this vector.
     *
     * @param index
     * @param value
     */
    public void set(int index, Half3 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Half3 value, HalfFloatArray array, int index) {
        array.set(index, value.getX());
        array.set(index + 1, value.getY());
        array.set(index + 2, value.getZ());
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorHalf3 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     */
    public void set(HalfFloatArray values) {
        VectorHalf3 vector = new VectorHalf3(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(HalfFloat value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    /**
     * Duplicates this vector.
     *
     * @return vector
     */
    public VectorHalf3 duplicate() {
        VectorHalf3 vector = new VectorHalf3(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorHalf3 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Half3 sum() {
        Half3 result = new Half3();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return ShortBuffer.wrap(storage.toShortArray());
    }

    @Override
    public int size() {
        return storage.getSize();
    }

    public int getLength() {
        return numElements;
    }

    public HalfFloatArray getArray() {
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
