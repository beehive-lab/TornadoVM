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
import uk.ac.manchester.tornado.api.types.vectors.Half16;

import java.lang.foreign.MemorySegment;
import java.nio.ShortBuffer;

import static uk.ac.manchester.tornado.api.types.vectors.Half16.add;

public final class VectorHalf16 implements TornadoCollectionInterface<ShortBuffer> {

    public static final Class<VectorHalf16> TYPE = VectorHalf16.class;

    private static final int ELEMENT_SIZE = 16;
    private final int numElements;
    private final HalfFloatArray storage;

    protected VectorHalf16(int numElements, HalfFloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector.
     *
     * @param numElements
     */
    public VectorHalf16(int numElements) {
        this(numElements, new HalfFloatArray(numElements * ELEMENT_SIZE));
    }

    /**
     * Creates a vector using the provided backing array.
     */
    private VectorHalf16(HalfFloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
    }

    private int toIndex(int index) {
        return (index * ELEMENT_SIZE);
    }

    /**
     * Returns the {@link Half16} at the given index of this vector.
     *
     * @param index
     * @return value
     */
    public Half16 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Half16 loadFromArray(final HalfFloatArray array, int index) {
        final Half16 result = new Half16();
        for (int i = 0; i < ELEMENT_SIZE; i++) {
            result.set(i, array.get(index + i));
        }
        return result;
    }

    /**
     * Sets the {@code Half16} at the given index of this vector.
     *
     * @param index
     * @param value
     */
    public void set(int index, Half16 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Half16 value, HalfFloatArray array, int index) {
        for (int i = 0; i < ELEMENT_SIZE; i++) {
            array.set(index + i, value.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     */
    public void set(VectorHalf16 values) {
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
        VectorHalf16 vector = new VectorHalf16(values);
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
    public VectorHalf16 duplicate() {
        VectorHalf16 vector = new VectorHalf16(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (this.numElements > ELEMENT_SIZE) {
            return String.format("VectorHalf16 <%d>", this.numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; i++) {
            tempString.append(" ").append(this.get(i).toString());
        }
        return tempString.toString();
    }

    public Half16 sum() {
        Half16 result = new Half16();
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
