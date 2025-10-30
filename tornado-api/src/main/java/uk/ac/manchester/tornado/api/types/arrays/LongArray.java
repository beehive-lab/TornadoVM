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
package uk.ac.manchester.tornado.api.types.arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

/**
 * This class represents an array of longs stored in native memory.
 * The long data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory.
 * The class also encapsulates methods for setting and getting long values,
 * for initializing the long array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 8)
public final class LongArray extends TornadoNativeArray {
    private static final int LONG_BYTES = 8;
    private MemorySegment segment;
    private int numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link LongArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public LongArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / LONG_BYTES;

        segmentByteSize = (long) numberOfElements * LONG_BYTES + arrayHeaderSize;
        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link LongArray} instance by concatenating the contents of the given array of {@link LongArray} instances.
     *
     * @param arrays
     *     An array of {@link LongArray} instances to be concatenated into the new instance.
     */
    public LongArray(LongArray... arrays) {
        concat(arrays);
    }

    /**
     * Internal method used to create a new instance of the {@link LongArray} from on-heap data.
     *
     * @param values
     *     The on-heap long array to create the instance from.
     * @return A new {@link LongArray} instance, initialized with values of the on-heap long array.
     */
    private static LongArray createSegment(long[] values) {
        LongArray array = new LongArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link LongArray} class from an on-heap long array.
     *
     * @param values
     *     The on-heap long array to create the instance from.
     * @return A new {@link LongArray} instance, initialized with values of the on-heap long array.
     */
    public static LongArray fromArray(long[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link LongArray} class from a set of long values.
     *
     * @param values
     *     The long values to initialize the array with.
     * @return A new {@link LongArray} instance, initialized with the given values.
     */
    public static LongArray fromElements(long... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link LongArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap long data.
     * @return A new {@link LongArray} instance, initialized with the segment data.
     */
    public static LongArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / LONG_BYTES);
        ensureMultipleOfElementSize(byteSize, LONG_BYTES);
        LongArray longArray = new LongArray(numElements);
        MemorySegment.copy(segment, 0, longArray.segment, (long) longArray.baseIndex * LONG_BYTES, byteSize);
        return longArray;
    }

    /**
     * Creates a new instance of the {@link LongArray} class from a {@link LongBuffer}.
     *
     * @param buffer
     *     The {@link LongBuffer} containing the long data.
     * @return A new {@link LongArray} instance, initialized with the buffer data.
     */
    public static LongArray fromLongBuffer(LongBuffer buffer) {
        int numElements = buffer.remaining();
        LongArray longArray = new LongArray(numElements);
        longArray.getSegment().copyFrom(MemorySegment.ofBuffer(buffer));
        return longArray;
    }

    /**
     * Converts the long data from off-heap to on-heap, by copying the values of a {@link LongArray}
     * instance into a new on-heap array.
     *
     * @return A new on-heap long array, initialized with the values stored in the {@link LongArray} instance.
     */
    public long[] toHeapArray() {
        long[] outputArray = new long[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the long value at a specified index of the {@link LongArray} instance.
     *
     * @param index
     *     The index at which to set the long value.
     * @param value
     *     The long value to store at the specified index.
     */
    public void set(int index, long value) {
        segment.setAtIndex(JAVA_LONG, baseIndex + index, value);
    }

    /**
     * Gets the long value stored at the specified index of the {@link LongArray} instance.
     *
     * @param index
     *     The index of which to retrieve the long value.
     * @return
     */
    public long get(int index) {
        return segment.getAtIndex(JAVA_LONG, baseIndex + index);
    }

    /**
     * Sets all the values of the {@link LongArray} instance to zero.
     */
    @Override
    public void clear() {
        init(0);
    }

    @Override
    public int getElementSize() {
        return LONG_BYTES;
    }

    /**
     * Initializes all the elements of the {@link LongArray} instance with a specified value.
     *
     * @param value
     *     The long value to initialize the {@link LongArray} instance with.
     */
    public void init(long value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_LONG, baseIndex + i, value);
        }
    }

    /**
     * Returns the number of long elements stored in the {@link LongArray} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link LongArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link LongArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link LongArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link LongArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link LongArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link LongArray} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Factory method to initialize a {@link LongArray}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *     Input Array.
     * @param value
     *     The float value to initialize the {@code LongArray} instance with.
     */
    public static void initialize(LongArray array, long value) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link LongArray} instances into a single {@link LongArray}.
     *
     * @param arrays
     *     Variable number of {@link LongArray} objects to be concatenated.
     * @return A new {@link LongArray} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static LongArray concat(LongArray... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(LongArray::getSize).sum();
        LongArray concatArray = new LongArray(newSize);
        long currentPositionBytes = 0;
        for (LongArray array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Extracts a slice of elements from a given {@link LongArray}, creating a new {@link LongArray} instance.
     *
     *
     * @param offset
     *     The starting index from which to begin the slice, inclusive.
     * @param length
     *     The number of elements to include in the slice.
     * @return A new {@link LongArray} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *     if the specified slice is out of the bounds of the original array.
     */
    public LongArray slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > getSize()) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * LONG_BYTES;
        long sliceByteLength = (long) length * LONG_BYTES;
        MemorySegment sliceSegment = segment.asSlice(sliceOffsetInBytes, sliceByteLength);
        LongArray slice = fromSegment(sliceSegment);
        return slice;
    }
}
