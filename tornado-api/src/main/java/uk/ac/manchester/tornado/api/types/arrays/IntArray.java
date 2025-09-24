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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.IntBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

/**
 * This class represents an array of ints stored in native memory.
 * The int data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory.
 * The class also encapsulates methods for setting and getting int values,
 * for initializing the int array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 4)
public final class IntArray extends TornadoNativeArray {
    private static final int INT_BYTES = 4;
    private int numberOfElements;
    private MemorySegment segment;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link IntArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public IntArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / INT_BYTES;
        segmentByteSize = (long) numberOfElements * INT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link IntArray} instance by concatenating the contents of the given array of {@link IntArray} instances.
     *
     * @param arrays
     *     An array of {@link IntArray} instances to be concatenated into the new instance.
     */
    public IntArray(IntArray... arrays) {
        concat(arrays);
    }

    /**
     * Internal method used to create a new instance of the {@link IntArray} from on-heap data.
     *
     * @param values
     *     The on-heap int array to create the instance from.
     * @return A new {@link IntArray} instance, initialized with values of the on-heap int array.
     */
    private static IntArray createSegment(int[] values) {
        IntArray array = new IntArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link IntArray} class from an on-heap int array.
     *
     * @param values
     *     The on-heap int array to create the instance from.
     * @return A new {@link IntArray} instance, initialized with values of the on-heap int array.
     */
    public static IntArray fromArray(int[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link IntArray} class from a set of int values.
     *
     * @param values
     *     The int values to initialize the array with.
     * @return A new {@link IntArray} instance, initialized with the given values.
     */
    public static IntArray fromElements(int... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link IntArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap int data.
     * @return A new {@link IntArray} instance, initialized with the segment data.
     */
    public static IntArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / INT_BYTES);
        ensureMultipleOfElementSize(byteSize, INT_BYTES);
        IntArray intArray = new IntArray(numElements);
        MemorySegment.copy(segment, 0, intArray.segment, (long) intArray.baseIndex * INT_BYTES, byteSize);
        return intArray;
    }

    /**
     * Creates a new instance of the {@link IntArray} class from a {@link IntBuffer}.
     *
     * @param buffer
     *     The {@link IntBuffer} containing the int data.
     * @return A new {@link IntArray} instance, initialized with the buffer data.
     */
    public static IntArray fromIntBuffer(IntBuffer buffer) {
        int numElements = buffer.remaining();
        IntArray intArray = new IntArray(numElements);
        intArray.getSegment().copyFrom(MemorySegment.ofBuffer(buffer));
        return intArray;
    }

    /**
     * Converts the int data from off-heap to on-heap, by copying the values of a {@link IntArray}
     * instance into a new on-heap array.
     *
     * @return A new on-heap int array, initialized with the values stored in the {@link IntArray} instance.
     */
    public int[] toHeapArray() {
        int[] outputArray = new int[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the int value at a specified index of the {@link IntArray} instance.
     *
     * @param index
     *     The index at which to set the int value.
     * @param value
     *     The int value to store at the specified index.
     */
    public void set(int index, int value) {
        segment.setAtIndex(JAVA_INT, baseIndex + index, value);
    }

    /**
     * Gets the int value stored at the specified index of the {@link IntArray} instance.
     *
     * @param index
     *     The index of which to retrieve the int value.
     * @return
     */
    public int get(int index) {
        return segment.getAtIndex(JAVA_INT, baseIndex + index);
    }

    /**
     * Sets all the values of the {@link IntArray} instance to zero.
     */
    @Override
    public void clear() {
        init(0);
    }

    @Override
    public int getElementSize() {
        return INT_BYTES;
    }

    /**
     * Initializes all the elements of the {@link IntArray} instance with a specified value.
     *
     * @param value
     *     The int value to initialize the {@link IntArray} instance with.
     */
    public void init(int value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_INT, baseIndex + i, value);
        }
    }

    /**
     * Returns the number of int elements stored in the {@link IntArray} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link IntArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link IntArray} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link IntArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link IntArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link IntArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link IntArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    /**
     * Factory method to initialize a {@link IntArray}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *     Input Array.
     * @param value
     *     The float value to initialize the {@code IntArray} instance with.
     */
    public static void initialize(IntArray array, int value) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link IntArray} instances into a single {@link IntArray}.
     *
     * @param arrays
     *     Variable number of {@link IntArray} objects to be concatenated.
     * @return A new {@link IntArray} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static IntArray concat(IntArray... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(IntArray::getSize).sum();
        IntArray concatArray = new IntArray(newSize);
        long currentPositionBytes = 0;
        for (IntArray array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Extracts a slice of elements from a given {@linkIntArray}, creating a new {@linkIntArray} instance.
     *
     *
     * @param offset
     *     The starting index from which to begin the slice, inclusive.
     * @param length
     *     The number of elements to include in the slice.
     * @return A new {@linkIntArray} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *     if the specified slice is out of the bounds of the original array.
     */
    public IntArray slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > getSize()) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * INT_BYTES;
        long sliceByteLength = (long) length * INT_BYTES;
        MemorySegment sliceSegment = segment.asSlice(sliceOffsetInBytes, sliceByteLength);
        IntArray slice = fromSegment(sliceSegment);
        return slice;
    }
}
