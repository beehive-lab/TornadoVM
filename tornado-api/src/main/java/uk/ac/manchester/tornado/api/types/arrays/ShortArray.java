/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

/**
 * This class represents an array of shorts stored in native memory.
 * The short data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory.
 * The class also encapsulates methods for setting and getting short values,
 * for initializing the short array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 2)
public final class ShortArray extends TornadoNativeArray {
    private static final int SHORT_BYTES = 2;
    private MemorySegment segment;
    private int numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@code ShortArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public ShortArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        assert arrayHeaderSize >= 4;
        baseIndex = arrayHeaderSize / SHORT_BYTES;
        segmentByteSize = numberOfElements * SHORT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Internal method used to create a new instance of the {@code ShortArray} from on-heap data.
     *
     * @param values
     *     The on-heap short array to create the instance from.
     * @return A new {@code ShortArray} instance, initialized with values of the on-heap short array.
     */
    private static ShortArray createSegment(short[] values) {
        ShortArray array = new ShortArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@code ShortArray} class from an on-heap short array.
     *
     * @param values
     *     The on-heap short array to create the instance from.
     * @return A new {@code ShortArray} instance, initialized with values of the on-heap short array.
     */
    public static ShortArray fromArray(short[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@code ShortArray} class from a set of short values.
     *
     * @param values
     *     The short values to initialize the array with.
     * @return A new {@code ShortArray} instance, initialized with the given values.
     */
    public static ShortArray fromElements(short... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@code ShortArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap short data.
     * @return A new {@code ShortArray} instance, initialized with the segment data.
     */
    public static ShortArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / SHORT_BYTES);
        ShortArray shortArray = new ShortArray(numElements);
        MemorySegment.copy(segment, 0, shortArray.segment, shortArray.baseIndex * SHORT_BYTES, byteSize);
        return shortArray;
    }

    /**
     * Converts the short data from off-heap to on-heap, by copying the values of a {@code ShortArray}
     * instance into a new on-heap array.
     *
     * @return A new on-heap short array, initialized with the values stored in the {@code ShortArray} instance.
     */
    public short[] toHeapArray() {
        short[] outputArray = new short[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the short value at a specified index of the {@code ShortArray} instance.
     *
     * @param index
     *     The index at which to set the short value.
     * @param value
     *     The short value to store at the specified index.
     */
    public void set(int index, short value) {
        segment.setAtIndex(JAVA_SHORT, baseIndex + index, value);
    }

    /**
     * Gets the short value stored at the specified index of the {@code ShortArray} instance.
     *
     * @param index
     *     The index of which to retrieve the short value.
     * @return
     */
    public short get(int index) {
        return segment.getAtIndex(JAVA_SHORT, baseIndex + index);
    }

    /**
     * Sets all the values of the {@code ShortArray} instance to zero.
     */
    @Override
    public void clear() {
        init((short) 0);
    }

    @Override
    public int getElementSize() {
        return SHORT_BYTES;
    }

    /**
     * Initializes all the elements of the {@code ShortArray} instance with a specified value.
     *
     * @param value
     *     The short value to initialize the {@code ShortArray} instance with.
     */
    public void init(short value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_SHORT, baseIndex + i, value);
        }
    }

    /**
     * Returns the number of short elements stored in the {@code ShortArray} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@code ShortArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@code ShortArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@code ShortArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@code ShortArray} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesWithoutHeader() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }
}
