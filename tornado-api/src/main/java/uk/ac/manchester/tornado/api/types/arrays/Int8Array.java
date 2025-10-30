/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@SegmentElementSize(size = 1)
public final class Int8Array extends TornadoNativeArray {
    private static final int INT8_BYTES = 1;
    private MemorySegment segment;

    private int numberOfElements;

    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link Int8Array} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public Int8Array(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / INT8_BYTES;
        segmentByteSize = numberOfElements * INT8_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link Int8Array} instance by concatenating the contents of the given array of {@link Int8Array} instances.
     *
     * @param arrays
     *     An array of {@link Int8Array} instances to be concatenated into the new instance.
     */
    public Int8Array(Int8Array... arrays) {
        concat(arrays);
    }

    /**
     * Internal method used to create a new instance of the {@link Int8Array} from on-heap data.
     *
     * @param values
     *     The on-heap byte array to create the instance from.
     * @return A new {@link Int8Array} instance, initialized with values of the on-heap byte array.
     */
    private static Int8Array createSegment(byte[] values) {
        Int8Array array = new Int8Array(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link Int8Array} class from an on-heap byte.
     *
     * @param values
     *     The on-heap byte array to create the instance from.
     * @return A new {@link Int8Array} instance, initialized with values of the on-heap byte array.
     */
    public static Int8Array fromArray(byte[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link Int8Array} class from a set of byte values.
     *
     * @param values
     *     The byte values to initialize the array with.
     * @return A new {@link Int8Array} instance, initialized with the given values.
     */
    public static Int8Array fromElements(byte... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link Int8Array} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap byte int8 data.
     * @return A new {@link Int8Array} instance, initialized with the segment data.
     */
    public static Int8Array fromSegment(MemorySegment segment) {
        int numElements = (int) segment.byteSize();
        Int8Array int8Array = new Int8Array(numElements);
        MemorySegment.copy(segment, 0, int8Array.segment, (long) int8Array.baseIndex, numElements);
        return int8Array;
    }

    /**
     * Converts the byte array data from off-heap to on-heap, by copying the values of a {@link Int8Array}
     * instance into a new on-heap byte array.
     *
     * @return A new on-heap byte array, initialized with the values stored in the {@link Int8Array} instance.
     */
    public byte[] toHeapArray() {
        byte[] outputArray = new byte[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the byte value at a specified index of the {@link Int8Array} instance.
     *
     * @param index
     *     The index at which to set the byte value.
     * @param value
     *     The byte value to store at the specified index.
     */
    public void set(int index, byte value) {
        segment.setAtIndex(JAVA_BYTE, baseIndex + index, value);
    }

    /**
     * Gets the byte value stored at the specified index of the {@link Int8Array} instance.
     *
     * @param index
     *     The index of which to retrieve the byte value.
     * @return
     */
    public byte get(int index) {
        return segment.getAtIndex(JAVA_BYTE, baseIndex + index);
    }

    /**
     * Sets all the values of the {@link Int8Array} instance to zero.
     */
    @Override
    public void clear() {
        init((byte) 0);
    }

    @Override
    public int getElementSize() {
        return INT8_BYTES;
    }

    /**
     * Initializes all the elements of the {@link Int8Array} instance with a specified value.
     *
     * @param value
     *     The byte value to initialize the {@link Int8Array} instance with.
     */
    public void init(byte value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_BYTE, baseIndex + i, value);
        }
    }

    /**
     * Returns the number of byte (int 8) elements stored in the {@link Int8Array} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link Int8Array} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link Int8Array} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link Int8Array} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link Int8Array} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link Int8Array} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link Int8Array} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Factory method to initialize a {@link Int8Array}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *     Input Array.
     * @param value
     *     The byte int8 value to initialize the {@code Int8Array} instance with.
     */
    public static void initialize(Int8Array array, byte value) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link Int8Array} instances into a single {@link Int8Array}.
     *
     * @param arrays
     *     Variable number of {@link Int8Array} objects to be concatenated.
     * @return A new {@link Int8Array} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static Int8Array concat(Int8Array... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(Int8Array::getSize).sum();
        Int8Array concatArray = new Int8Array(newSize);
        long currentPositionBytes = 0;
        for (Int8Array array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Extracts a slice of elements from a given {@link Int8Array}, creating a new {@link Int8Array} instance.
     *
     *
     * @param offset
     *     The starting index from which to begin the slice, inclusive.
     * @param length
     *     The number of elements to include in the slice.
     * @return A new {@link Int8Array} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *     if the specified slice is out of the bounds of the original array.
     */
    public Int8Array slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > getSize()) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + offset;
        long sliceByteLength = length;
        MemorySegment sliceSegment = segment.asSlice(sliceOffsetInBytes, sliceByteLength);
        Int8Array slice = fromSegment(sliceSegment);
        return slice;
    }
}
