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
import uk.ac.manchester.tornado.api.types.vectors.Float16;

public final class VectorFloat16 implements TornadoCollectionInterface<FloatBuffer> {

    public static final Class<VectorFloat16> TYPE = VectorFloat16.class;

    private static final int ELEMENT_SIZE = 16;
    private final FloatArray storage;
    private final int numElements;

    VectorFloat16(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    public VectorFloat16(FloatArray array) {
        this(array.getSize() / ELEMENT_SIZE, array);
    }

    public VectorFloat16(int numElements) {
        this(numElements, new FloatArray(numElements * ELEMENT_SIZE));
    }

    private int toIndex(int index) {
        return index * ELEMENT_SIZE;
    }

    public Float16 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    private Float16 loadFromArray(FloatArray array, int index) {
        Float16 result = new Float16();
        for (int i = 0; i < ELEMENT_SIZE; ++i) {
            result.set(i, array.get(index + i));
        }
        return result;
    }

    public void set(int index, Float16 value) {
        storeToArray(value, storage, toIndex(index));
    }

    private void storeToArray(Float16 value, FloatArray array, int index) {
        for (int i = 0; i < ELEMENT_SIZE; ++i) {
            array.set(index + i, value.get(i));
        }
    }

    public void set(VectorFloat16 values) {
        for (int i = 0; i < numElements; ++i) {
            set(i, values.get(i));
        }
    }

    public void set(FloatArray values) {
        VectorFloat16 vector = new VectorFloat16(values);
        for (int i = 0; i < numElements; ++i) {
            set(i, vector.get(i));
        }
    }

    public void fill(float value) {
        for (int i = 0; i < storage.getSize(); ++i) {
            storage.set(i, value);
        }
    }

    public VectorFloat16 duplicate() {
        VectorFloat16 vector = new VectorFloat16(numElements);
        vector.set(this);
        return vector;
    }

    public String toString() {
        if (numElements > ELEMENT_SIZE) {
            return String.format("VectorFloat16 <%d>", numElements);
        }
        StringBuilder tempString = new StringBuilder();
        for (int i = 0; i < numElements; ++i) {
            tempString.append(" ").append(get(i).toString());
        }
        return tempString.toString();
    }

    public Float16 sum() {
        Float16 result = new Float16();
        for (int i = 0; i < numElements; ++i) {
            result = Float16.add(result, get(i));
        }
        return result;
    }

    public Float16 min() {
        Float16 result = new Float16();
        for (int i = 0; i < numElements; ++i) {
            result = Float16.min(result, get(i));
        }
        return result;
    }

    public Float16 max() {
        Float16 result = new Float16();
        for (int i = 0; i < numElements; ++i) {
            result = Float16.max(result, get(i));
        }
        return result;
    }

    public int vectorWidth() {
        return ELEMENT_SIZE;
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
