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

import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;

/**
 * This class represents an array of characters stored in native memory.
 * The char data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory.
 * The class also encapsulates methods for setting and getting char values,
 * for initializing the char array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 2)
public final class CharArray extends TornadoNativeArray {
    private static final int CHAR_BYTES = 2;
    private MemorySegment segment;
    private int numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link CharArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *     The number of elements in the array.
     */
    public CharArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / CHAR_BYTES;
        segmentByteSize = numberOfElements * CHAR_BYTES + arrayHeaderSize;

        segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        segment.setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Internal method used to create a new instance of the {@link CharArray} from on-heap data.
     *
     * @param values
     *     The on-heap char array to create the instance from.
     * @return A new {@link CharArray} instance, initialized with values of the on-heap char array.
     */
    private static CharArray createSegment(char[] values) {
        CharArray array = new CharArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link CharArray} class from an on-heap char array.
     *
     * @param values
     *     The on-heap char array to create the instance from.
     * @return A new {@link CharArray} instance, initialized with values of the on-heap char array.
     */
    public static CharArray fromArray(char[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link CharArray} class from a set of char values.
     *
     * @param values
     *     The char values to initialize the array with.
     * @return A new {@link CharArray} instance, initialized with the given values.
     */
    public static CharArray fromElements(char... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link CharArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *     The {@link MemorySegment} containing the off-heap char data.
     * @return A new {@link CharArray} instance, initialized with the segment data.
     */
    public static CharArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / CHAR_BYTES);
        CharArray charArray = new CharArray(numElements);
        MemorySegment.copy(segment, 0, charArray.segment, charArray.baseIndex * CHAR_BYTES, byteSize);
        return charArray;
    }

    /**
     * Sets all the values of the {@link CharArray} instance to \u0000, the default char value.
     */
    @Override
    public void clear() {
        init('\u0000');
    }

    @Override
    public int getElementSize() {
        return CHAR_BYTES;
    }

    /**
     * Converts the char data from off-heap to on-heap, by copying the values of a {@link CharArray}
     * instance into a new on-heap array.
     *
     * @return A new on-heap char array, initialized with the values stored in the {@link CharArray} instance.
     */
    public char[] toHeapArray() {
        char[] outputArray = new char[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the char value at a specified index of the {@link CharArray} instance.
     *
     * @param index
     *     The index at which to set the char value.
     * @param value
     *     The char value to store at the specified index.
     */
    public void set(int index, char value) {
        segment.setAtIndex(JAVA_CHAR, baseIndex + index, value);
    }

    /**
     * Gets the char value stored at the specified index of the {@link CharArray} instance.
     *
     * @param index
     *     The index of which to retrieve the char value.
     * @return
     */
    public char get(int index) {
        return segment.getAtIndex(JAVA_CHAR, baseIndex + index);
    }

    /**
     * Initializes all the elements of the {@link CharArray} instance with a specified value.
     *
     * @param value
     *     The char value to initialize the {@link ByteArray} instance with.
     */
    public void init(char value) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(JAVA_CHAR, baseIndex + i, value);
        }
    }

    /**
     * Returns the number of char elements stored in the {@link CharArray} instance.
     *
     * @return
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link CharArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link CharArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link CharArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link CharArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment;
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link CharArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link CharArray} instance,
     * excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }
}
