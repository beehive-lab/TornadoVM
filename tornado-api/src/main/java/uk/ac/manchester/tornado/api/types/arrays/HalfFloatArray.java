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
package uk.ac.manchester.tornado.api.types.arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.HalfFloat;

/**
 * This class represents an array of half floats (float16 types) stored in native memory.
 * The half float data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory.
 * The class also encapsulates methods for setting and getting half float values,
 * for initializing the half float array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 2)
public final class HalfFloatArray extends TornadoNativeArray {

    private static final int HALF_FLOAT_BYTES = 2;
    private MemorySegment segment;

    private int numberOfElements;

    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link HalfFloatArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public HalfFloatArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / HALF_FLOAT_BYTES;
        segmentByteSize = numberOfElements * HALF_FLOAT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Internal method used to create a new instance of the {@link HalfFloatArray} from on-heap data.
     *
     * @param values
     *     The on-heap {@link HalfFloat} to create the instance from.
     * @return A new {@link HalfFloatArray} instance, initialized with values of the on-heap {@link HalfFloat} array.
     */
    private static HalfFloatArray createSegment(HalfFloat[] values) {
        HalfFloatArray array = new HalfFloatArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link HalfFloatArray} class from an on-heap {@link HalfFloat}.
     *
     * @param values
     *     The on-heap {@link HalfFloat} array to create the instance from.
     * @return A new {@link HalfFloatArray} instance, initialized with values of the on-heap {@link HalfFloat} array.
     */
    public static HalfFloatArray fromArray(HalfFloat[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link HalfFloatArray} class from a set of {@link HalfFloat} values.
     *
     * @param values
     *     The {@link HalfFloat} values to initialize the array with.
     * @return A new {@link FloatArray} instance, initialized with the given values.
     */
    public static HalfFloatArray fromElements(HalfFloat... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link HalfFloatArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap half float data.
     * @return A new {@link HalfFloatArray} instance, initialized with the segment data.
     */
    public static HalfFloatArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / HALF_FLOAT_BYTES);
        HalfFloatArray halfFloatArray = new HalfFloatArray(numElements);
        MemorySegment.copy(segment, 0, halfFloatArray.segment, halfFloatArray.baseIndex * HALF_FLOAT_BYTES, byteSize);
        return halfFloatArray;
    }

    /**
     * Converts the {@link HalfFloat} data from off-heap to on-heap, by copying the values of a {@link HalfFloatArray}
     * instance into a new on-heap {@link HalfFloat}.
     *
     * @return A new on-heap {@link HalfFloat} array, initialized with the values stored in the {@link HalfFloatArray} instance.
     */
    public HalfFloat[] toHeapArray() {
        HalfFloat[] outputArray = new HalfFloat[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the {@link HalfFloat} value at a specified index of the {@link HalfFloatArray} instance.
     *
     * @param index
     *     The index at which to set the {@link HalfFloat} value.
     * @param value
     *     The {@link HalfFloat} value to store at the specified index.
     */
    public void set(int index, HalfFloat value) {
        segment.setAtIndex(JAVA_SHORT, baseIndex + index, value.getHalfFloatValue());
    }

    /**
     * Gets the {@link HalfFloat} value stored at the specified index of the {@link HalfFloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the {@link HalfFloat} value.
     * @return
     */
    public HalfFloat get(int index) {
        short halfFloatValue = segment.getAtIndex(JAVA_SHORT, baseIndex + index);
        return new HalfFloat(halfFloatValue);
    }

    /**
     * Sets all the values of the {@link HalfFloatArray} instance to zero.
     */
    @Override
    public void clear() {
        init(new HalfFloat(0.0f));
    }

    @Override
    public int getElementSize() {
        return HALF_FLOAT_BYTES;
    }

    /**
     * Initializes all the elements of the {@link HalfFloatArray} instance with a specified value.
     *
     * @param value
     *     The {@link HalfFloat} value to initialize the {@link HalfFloatArray} instance with.
     */
    public void init(HalfFloat value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_SHORT, baseIndex + i, value.getHalfFloatValue());
        }
    }

    /**
     * Returns the number of half float elements stored in the {@link HalfFloatArray} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link HalfFloatArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link HalfFloatArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link HalfFloatArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link HalfFloatArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link HalfFloatArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link HalfFloatArray} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

}
