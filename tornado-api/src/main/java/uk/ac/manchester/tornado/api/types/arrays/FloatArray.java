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

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

/**
 * This class represents an array of floats stored in native memory.
 * The float data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory.
 * The class also encapsulates methods for setting and getting float values,
 * for initializing the float array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 4)
public final class FloatArray extends TornadoNativeArray {
    private static final int FLOAT_BYTES = 4;
    private MemorySegment segment;

    private int numberOfElements;

    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link FloatArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public FloatArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / FLOAT_BYTES;
        segmentByteSize = (long) numberOfElements * FLOAT_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link FloatArray} instance by concatenating the contents of the given array of {@link FloatArray} instances.
     *
     * @param arrays
     *     An array of {@link FloatArray} instances to be concatenated into the new instance.
     */
    public FloatArray(FloatArray... arrays) {
        concat(arrays);
    }

    /**
     * Internal method used to create a new instance of the {@link FloatArray} from on-heap data.
     *
     * @param values
     *     The on-heap float array to create the instance from.
     * @return A new {@link FloatArray} instance, initialized with values of the on-heap float array.
     */
    private static FloatArray createSegment(float[] values) {
        FloatArray array = new FloatArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from an on-heap float array.
     *
     * @param values
     *     The on-heap float array to create the instance from.
     * @return A new {@link FloatArray} instance, initialized with values of the on-heap float array.
     */
    public static FloatArray fromArray(float[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from a set of float values.
     *
     * @param values
     *     The float values to initialize the array with.
     * @return A new {@link FloatArray} instance, initialized with the given values.
     */
    public static FloatArray fromElements(float... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap float data.
     * @return A new {@link FloatArray} instance, initialized with the segment data.
     */
    public static FloatArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / FLOAT_BYTES);
        ensureMultipleOfElementSize(byteSize, FLOAT_BYTES);
        FloatArray floatArray = new FloatArray(numElements);
        MemorySegment.copy(segment, 0, floatArray.segment, (long) floatArray.baseIndex * FLOAT_BYTES, byteSize);
        return floatArray;
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from a {@link FloatBuffer}.
     *
     * @param buffer
     *     The {@link FloatBuffer} containing the float data.
     * @return A new {@link FloatArray} instance, initialized with the buffer data.
     */
    public static FloatArray fromFloatBuffer(FloatBuffer buffer) {
        int numElements = buffer.remaining();
        FloatArray floatArray = new FloatArray(numElements);
        floatArray.getSegment().copyFrom(MemorySegment.ofBuffer(buffer));
        return floatArray;
    }

    /**
     * Converts the float data from off-heap to on-heap, by copying the values of a {@link FloatArray}
     * instance into a new on-heap array.
     *
     * @return A new on-heap float array, initialized with the values stored in the {@link FloatArray} instance.
     */
    public float[] toHeapArray() {
        float[] outputArray = new float[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the float value at a specified index of the {@link FloatArray} instance.
     *
     * @param index
     *     The index at which to set the float value.
     * @param value
     *     The float value to store at the specified index.
     */
    public void set(int index, float value) {
        segment.setAtIndex(JAVA_FLOAT, baseIndex + index, value);
    }

    /**
     * Gets the float value stored at the specified index of the {@link FloatArray} instance.
     *
     * @param index
     *     The index of which to retrieve the float value.
     * @return
     */
    public float get(int index) {
        return segment.getAtIndex(JAVA_FLOAT, baseIndex + index);
    }

    /**
     * Sets all the values of the {@link FloatArray} instance to zero.
     */
    @Override
    public void clear() {
        init(0.0f);
    }

    @Override
    public int getElementSize() {
        return FLOAT_BYTES;
    }

    /**
     * Initializes all the elements of the {@link FloatArray} instance with a specified value.
     *
     * @param value
     *     The float value to initialize the {@link FloatArray} instance with.
     */
    public void init(float value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_FLOAT, baseIndex + i, value);
        }
    }

    /**
     * Returns the number of float elements stored in the {@link FloatArray} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link FloatArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link FloatArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link FloatArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link FloatArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link FloatArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link FloatArray} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Factory method to initialize a {@link FloatArray}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *     Input Array.
     * @param value
     *     The float value to initialize the {@code FloatArray} instance with.
     */
    public static void initialize(FloatArray array, float value) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link FloatArray} instances into a single {@link FloatArray}.
     *
     * @param arrays
     *     Variable number of {@link FloatArray} objects to be concatenated.
     * @return A new {@link FloatArray} instance containing all the elements of the input arrays,
     *     concatenated in the order they were provided.
     */
    public static FloatArray concat(FloatArray... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(FloatArray::getSize).sum();
        FloatArray concatArray = new FloatArray(newSize);
        long currentPositionBytes = 0;
        for (FloatArray array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Extracts a slice of elements from a given {@link FloatArray}, creating a new {@link FloatArray} instance.
     *
     *
     * @param offset
     *     The starting index from which to begin the slice, inclusive.
     * @param length
     *     The number of elements to include in the slice.
     * @return A new {@link FloatArray} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *     if the specified slice is out of the bounds of the original array.
     */
    public FloatArray slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > getSize()) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * FLOAT_BYTES;
        long sliceByteLength = (long) length * FLOAT_BYTES;
        MemorySegment sliceSegment = segment.asSlice(sliceOffsetInBytes, sliceByteLength);
        FloatArray slice = fromSegment(sliceSegment);
        return slice;
    }
}
