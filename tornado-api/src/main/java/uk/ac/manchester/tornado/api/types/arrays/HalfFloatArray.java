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
import java.util.Arrays;

import uk.ac.manchester.tornado.api.annotations.Parallel;
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
        segmentByteSize = (long) numberOfElements * HALF_FLOAT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link HalfFloatArray} instance by concatenating the contents of the given array of {@link HalfFloatArray} instances.
     *
     * @param arrays
     *     An array of {@link HalfFloatArray} instances to be concatenated into the new instance.
     */
    public HalfFloatArray(HalfFloatArray... arrays) {
        concat(arrays);
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
     * @return A new {@linkHalfFloatArray} instance, initialized with the given values.
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
        ensureMultipleOfElementSize(byteSize, HALF_FLOAT_BYTES);
        HalfFloatArray halfFloatArray = new HalfFloatArray(numElements);
        MemorySegment.copy(segment, 0, halfFloatArray.segment, (long) halfFloatArray.baseIndex * HALF_FLOAT_BYTES, byteSize);
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
     * Converts the {@link HalfFloat} data from off-heap to an on-heap short representation,
     * by getting the values of a {@link HalfFloatArray} instance as short and coping them
     * into a new on-heap short array.
     *
     * @return A new on-heap short array, initialized with the values stored in the {@link HalfFloatArray} instance.
     */
    public short[] toShortArray() {
        short[] outputArray = new short[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i).getHalfFloatValue();
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

    /**
     * Factory method to initialize a {@link HalfFloatArray}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *     Input Array.
     * @param value
     *     The float value to initialize the {@code HalfFloatArray} instance with.
     */
    public static void initialize(HalfFloatArray array, HalfFloat value) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link HalfFloatArray} instances into a single {@link HalfFloatArray}.
     *
     * @param arrays
     *     Variable number of {@link HalfFloatArray} objects to be concatenated.
     * @return A new {@link HalfFloatArray} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static HalfFloatArray concat(HalfFloatArray... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(HalfFloatArray::getSize).sum();
        HalfFloatArray concatArray = new HalfFloatArray(newSize);
        long currentPositionBytes = 0;
        for (HalfFloatArray array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Extracts a slice of elements from a given {@linkHalfFloatArray}, creating a new {@linkHalfFloatArray} instance.
     *
     *
     * @param offset
     *     The starting index from which to begin the slice, inclusive.
     * @param length
     *     The number of elements to include in the slice.
     * @return A new {@linkHalfFloatArray} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *     if the specified slice is out of the bounds of the original array.
     */
    public HalfFloatArray slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > getSize()) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * HALF_FLOAT_BYTES;
        long sliceByteLength = (long) length * HALF_FLOAT_BYTES;
        MemorySegment sliceSegment = segment.asSlice(sliceOffsetInBytes, sliceByteLength);
        HalfFloatArray slice = fromSegment(sliceSegment);
        return slice;
    }

}
