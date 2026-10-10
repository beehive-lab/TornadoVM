/*
 * Copyright (c) 2013-2025, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.HalfFloat;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * This class represents an array of bytes stored in native memory. The byte data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory. The class also
 * encapsulates methods for setting and getting byte values, for initializing the byte array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 1)
public final class ByteArray extends TornadoNativeArray {
    private static final int BYTE_BYTES = 1;
    private TornadoMemorySegment segment;
    private long numberOfElements;
    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link ByteArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *         The number of elements in the array.
     */
    public ByteArray(int numberOfElements) {
        this((long) numberOfElements);
    }

    /**
     * Constructs a new instance of the {@link ByteArray} that will store a user-specified number of elements. The number of elements can exceed
     * {@link Integer#MAX_VALUE}; access such arrays with the {@code long}-index accessors.
     *
     * @param numberOfElements
     *         The number of elements in the array.
     */
    public ByteArray(long numberOfElements) {
        this.numberOfElements = checkNumElements(numberOfElements);
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / BYTE_BYTES;
        segmentByteSize = (long) numberOfElements * BYTE_BYTES + arrayHeaderSize;
        segment = new TornadoMemorySegment(segmentByteSize, numberOfElements);
    }

    /**
     * Constructs a new instance of the {@link ByteArray} by wrapping an existing {@link MemorySegment} without copying its contents.
     *
     * @param existingSegment
     *         The {@link MemorySegment} containing *both* the off-heap byte *header* and *data*.
     */
    private ByteArray(MemorySegment existingSegment) {
        this.arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        this.baseIndex = arrayHeaderSize / BYTE_BYTES;

        // Calculate number of elements from segment size
        long dataSize = existingSegment.byteSize() - arrayHeaderSize;
        this.numberOfElements = toNumElements(dataSize, BYTE_BYTES);

        // Set up the segment and initialize header
        this.segmentByteSize = existingSegment.byteSize();
        this.segment = new TornadoMemorySegment(existingSegment);
        this.segment.getSegment().setAtIndex(JAVA_LONG, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link ByteArray} instance by concatenating the contents of the given array of {@link ByteArray} instances.
     *
     * @param arrays
     *         An array of {@link ByteArray} instances to be concatenated into the new instance.
     */
    public ByteArray(ByteArray... arrays) {
        concat(arrays);
    }

    /**
     * Internal method used to create a new instance of the {@link ByteArray} from on-heap data.
     *
     * @param values
     *         The on-heap byte array to create the instance from.
     * @return A new {@link ByteArray} instance, initialized with values of the on-heap byte array.
     */
    private static ByteArray createSegment(byte[] values) {
        ByteArray array = new ByteArray(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link ByteArray} class from an on-heap byte array.
     *
     * @param values
     *         The on-heap byte array to create the instance from.
     * @return A new {@link ByteArray} instance, initialized with values of the on-heap byte array.
     */
    public static ByteArray fromArray(byte[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link ByteArray} class from a set of byte values.
     *
     * @param values
     *         The byte values to initialize the array with.
     * @return A new {@link ByteArray} instance, initialized with the given values.
     */
    public static ByteArray fromElements(byte... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link ByteArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *         The {@link MemorySegment} containing the off-heap byte data.
     * @return A new {@link ByteArray} instance, initialized with the segment data.
     */
    public static ByteArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        long numElements = toNumElements(byteSize, BYTE_BYTES);
        ByteArray byteArray = new ByteArray(numElements);
        MemorySegment.copy(segment, 0, byteArray.segment.getSegment(), (long) byteArray.baseIndex * BYTE_BYTES, byteSize);
        return byteArray;
    }

    /**
     * Creates a new instance of the {@link ByteArray} class by wrapping an existing {@link MemorySegment} without copying its contents.
     *
     * @param segment
     *         The {@link MemorySegment} containing *both* the off-heap byte *header* and *data*.
     * @return A new {@link ByteArray} instance that wraps the given segment.
     */
    public static ByteArray fromSegmentShallow(MemorySegment segment) {
        return new ByteArray(segment);
    }

    /**
     * Creates a new instance of the {@link ByteArray} class from a {@link ByteBuffer}.
     *
     * @param buffer
     *         The {@link ByteBuffer} containing the float data.
     * @return A new {@link ByteArray} instance, initialized with the buffer data.
     */
    public static ByteArray fromByteBuffer(ByteBuffer buffer) {
        int numElements = buffer.remaining();
        ByteArray byteArray = new ByteArray(numElements);
        byteArray.getSegment().copyFrom(MemorySegment.ofBuffer(buffer));
        return byteArray;
    }

    /**
     * Factory method to initialize a {@link ByteArray}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *         Input Array.
     * @param value
     *         The float value to initialize the {@code ByteArray} instance with.
     */
    public static void initialize(ByteArray array, byte value) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, value);
        }
    }

    /**
     * Concatenates multiple {@link ByteArray} instances into a single {@link ByteArray}.
     *
     * @param arrays
     *         Variable number of {@link ByteArray} objects to be concatenated.
     * @return A new {@link ByteArray} instance containing all the elements of the input arrays, concatenated in the order they were provided.
     */
    public static ByteArray concat(ByteArray... arrays) {
        long newSize = checkNumElements(Arrays.stream(arrays).mapToLong(ByteArray::getSizeLong).sum());
        ByteArray concatArray = new ByteArray(newSize);
        long currentPositionBytes = 0;
        for (ByteArray array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Converts the byte data from off-heap to on-heap, by copying the values of a {@link ByteArray} instance into a new on-heap array.
     *
     * @return A new on-heap byte array, initialized with the values stored in the {@link ByteArray} instance.
     */
    public byte[] toHeapArray() {
        byte[] outputArray = new byte[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the byte value at a specified index of the {@link ByteArray} instance.
     *
     * @param index
     *         The index at which to set the byte value.
     * @param value
     *         The byte value to store at the specified index.
     */
    public void set(int index, byte value) {
        segment.setAtIndex(index, value, baseIndex);
    }

    /**
     * Sets the byte value at a specified index of the {@link ByteArray} instance.
     *
     * @param index
     *         The index at which to set the byte value.
     * @param value
     *         The byte value to store at the specified index.
     */
    public void set(long index, byte value) {
        segment.setAtIndex(index, value, baseIndex);
    }

    /**
     * Sets the half-float value at the specified byte index within the {@link ByteArray} instance.
     *
     * The specified {@code byteIndex} must be aligned to a 2-byte boundary; if it is not, an {@link IllegalArgumentException} will be thrown. The method internally calculates the appropriate short
     * index for storage and updates the underlying memory segment.
     *
     * @param byteIndex
     *         The byte index at which to set the half-float value. Must be aligned to a 2-byte boundary.
     * @param value
     *         The {@link HalfFloat} value to be stored at the specified index.
     * @throws IllegalArgumentException
     *         If the {@code byteIndex} is not aligned to a 2-byte boundary.
     */
    public void setHalfFloat(int byteIndex, HalfFloat value) {
        if (byteIndex % 2 != 0) {
            throw new IllegalArgumentException("Half-float must be aligned to 2-byte boundary");
        }
        // Convert byte index to short index for the segment
        // arrayHeaderSize (8 bytes) + byteIndex, then divide by 2 for short indexing
        int shortIndex = (arrayHeaderSize + byteIndex) / 2;
        segment.setAtIndex(shortIndex, value.getHalfFloatValue(), 0);
    }

    /**
     * Sets the half-float value at a {@code long} byte index. See {@link #setHalfFloat(int, HalfFloat)}.
     */
    public void setHalfFloat(long byteIndex, HalfFloat value) {
        if (byteIndex % 2 != 0) {
            throw new IllegalArgumentException("Half-float must be aligned to 2-byte boundary");
        }
        long shortIndex = (arrayHeaderSize + byteIndex) / 2;
        segment.setAtIndex(shortIndex, value.getHalfFloatValue(), 0);
    }

    /**
     * Gets the byte value stored at the specified index of the {@link ByteArray} instance.
     *
     * @param index
     *         The index of which to retrieve the byte value.
     * @return en element byte of the off-heap array
     */
    public byte get(int index) {
        return segment.getByteAtIndex(index, baseIndex);
    }

    /**
     * Gets the byte value stored at the specified index of the {@link ByteArray} instance.
     *
     * @param index
     *         The index of which to retrieve the byte value.
     * @return en element byte of the off-heap array
     */
    public byte get(long index) {
        return segment.getByteAtIndex(index, baseIndex);
    }

    /**
     * Gets the half-float value stored at the specified byte index within the {@link ByteArray} instance.
     *
     * The specified {@code byteIndex} must be aligned to a 2-byte boundary; if it is not, an {@link IllegalArgumentException} will be thrown. The method internally calculates the appropriate short
     * index for storage and retrieves the value from the underlying memory segment.
     *
     * @param byteIndex
     *         The byte index from which to retrieve the half-float value. Must be aligned to a 2-byte boundary.
     * @return A {@link HalfFloat} instance containing the value stored at the specified index.
     * @throws IllegalArgumentException
     *         If the {@code byteIndex} is not aligned to a 2-byte boundary.
     */
    public HalfFloat getHalfFloat(int byteIndex) {
        if (byteIndex % 2 != 0) {
            throw new IllegalArgumentException("Half-float must be aligned to 2-byte boundary");
        }
        // Convert byte index to short index for the segment
        // arrayHeaderSize (8 bytes) + byteIndex, then divide by 2 for short indexing
        int shortIndex = (arrayHeaderSize + byteIndex) / 2;
        short halfFloatValue = segment.getShortAtIndex(shortIndex, 0); //Todo: might have issues
        return new HalfFloat(halfFloatValue);
    }

    /**
     * Gets the half-float value at a {@code long} byte index. See {@link #getHalfFloat(int)}.
     */
    public HalfFloat getHalfFloat(long byteIndex) {
        if (byteIndex % 2 != 0) {
            throw new IllegalArgumentException("Half-float must be aligned to 2-byte boundary");
        }
        // Convert byte index to short index for the segment
        // arrayHeaderSize (8 bytes) + byteIndex, then divide by 2 for short indexing
        long shortIndex = (arrayHeaderSize + byteIndex) / 2;
        short halfFloatValue = segment.getShortAtIndex(shortIndex, 0); //Todo: might have issues
        return new HalfFloat(halfFloatValue);
    }

    /**
     * Gets the 32-bit little-endian integer stored at the specified byte index within the {@link ByteArray} instance.
     * On a GPU backend this is one 32-bit load rather than four byte loads.
     *
     * @param byteIndex
     *         The byte index of the integer's first byte. Must be aligned to a 4-byte boundary.
     * @return The integer stored at {@code byteIndex}.
     * @throws IllegalArgumentException
     *         If the {@code byteIndex} is not aligned to a 4-byte boundary.
     */
    public int getInt(int byteIndex) {
        if (byteIndex % Integer.BYTES != 0) {
            throw new IllegalArgumentException("Int must be aligned to a 4-byte boundary");
        }
        return segment.getSegment().get(ValueLayout.JAVA_INT_UNALIGNED, arrayHeaderSize + (long) byteIndex);
    }

    /**
     * Sets the 32-bit little-endian integer at the specified byte index within the {@link ByteArray} instance.
     *
     * @param byteIndex
     *         The byte index of the integer's first byte. Must be aligned to a 4-byte boundary.
     * @param value
     *         The integer to store.
     * @throws IllegalArgumentException
     *         If the {@code byteIndex} is not aligned to a 4-byte boundary.
     */
    public void setInt(int byteIndex, int value) {
        if (byteIndex % Integer.BYTES != 0) {
            throw new IllegalArgumentException("Int must be aligned to a 4-byte boundary");
        }
        segment.getSegment().set(ValueLayout.JAVA_INT_UNALIGNED, arrayHeaderSize + (long) byteIndex, value);
    }

    /**
     * Gets the 64-bit little-endian long stored at the specified byte index within the {@link ByteArray} instance.
     * On a GPU backend this is one 64-bit load rather than eight byte loads.
     *
     * @param byteIndex
     *         The byte index of the long's first byte. Must be aligned to an 8-byte boundary.
     * @return The long stored at {@code byteIndex}.
     * @throws IllegalArgumentException
     *         If the {@code byteIndex} is not aligned to an 8-byte boundary.
     */
    public long getLong(int byteIndex) {
        if (byteIndex % Long.BYTES != 0) {
            throw new IllegalArgumentException("Long must be aligned to an 8-byte boundary");
        }
        return segment.getSegment().get(ValueLayout.JAVA_LONG_UNALIGNED, arrayHeaderSize + (long) byteIndex);
    }

    /**
     * Sets the 64-bit little-endian long at the specified byte index within the {@link ByteArray} instance.
     *
     * @param byteIndex
     *         The byte index of the long's first byte. Must be aligned to an 8-byte boundary.
     * @param value
     *         The long to store.
     * @throws IllegalArgumentException
     *         If the {@code byteIndex} is not aligned to an 8-byte boundary.
     */
    public void setLong(int byteIndex, long value) {
        if (byteIndex % Long.BYTES != 0) {
            throw new IllegalArgumentException("Long must be aligned to an 8-byte boundary");
        }
        segment.getSegment().set(ValueLayout.JAVA_LONG_UNALIGNED, arrayHeaderSize + (long) byteIndex, value);
    }

    /**
     * Gets the 32-bit integer at a {@code long} byte index. See {@link #getInt(int)}.
     */
    public int getInt(long byteIndex) {
        if (byteIndex % Integer.BYTES != 0) {
            throw new IllegalArgumentException("Int must be aligned to a 4-byte boundary");
        }
        return segment.getSegment().get(ValueLayout.JAVA_INT_UNALIGNED, arrayHeaderSize + byteIndex);
    }

    /**
     * Sets the 32-bit integer at a {@code long} byte index. See {@link #setInt(int, int)}.
     */
    public void setInt(long byteIndex, int value) {
        if (byteIndex % Integer.BYTES != 0) {
            throw new IllegalArgumentException("Int must be aligned to a 4-byte boundary");
        }
        segment.getSegment().set(ValueLayout.JAVA_INT_UNALIGNED, arrayHeaderSize + byteIndex, value);
    }

    /**
     * Gets the 64-bit long at a {@code long} byte index. See {@link #getLong(int)}.
     */
    public long getLong(long byteIndex) {
        if (byteIndex % Long.BYTES != 0) {
            throw new IllegalArgumentException("Long must be aligned to an 8-byte boundary");
        }
        return segment.getSegment().get(ValueLayout.JAVA_LONG_UNALIGNED, arrayHeaderSize + byteIndex);
    }

    /**
     * Sets the 64-bit long at a {@code long} byte index. See {@link #setLong(int, long)}.
     */
    public void setLong(long byteIndex, long value) {
        if (byteIndex % Long.BYTES != 0) {
            throw new IllegalArgumentException("Long must be aligned to an 8-byte boundary");
        }
        segment.getSegment().set(ValueLayout.JAVA_LONG_UNALIGNED, arrayHeaderSize + byteIndex, value);
    }

    /**
     * Sets all the values of the {@link ByteArray} instance to zero.
     */
    @Override
    public void clear() {
        init((byte) 0);
    }

    @Override
    public int getElementSize() {
        return BYTE_BYTES;
    }

    /**
     * Initializes all the elements of the {@link ByteArray} instance with a specified value.
     *
     * @param value
     *         The byte value to initialize the {@link ByteArray} instance with.
     */
    public void init(byte value) {
        for (long i = 0; i < numberOfElements; i++) {
            segment.setAtIndex(i, value, baseIndex);
        }
    }

    /**
     * @return Returns the number of byte elements stored in the {@link ByteArray} instance.
     *
     */
    @Override
    public int getSize() {
        return toIntSize(numberOfElements);
    }

    @Override
    public long getSizeLong() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link ByteArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link ByteArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.getSegment().asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link ByteArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link ByteArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment.getSegment();
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link ByteArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link ByteArray} instance, excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Extracts a slice of elements from a given {@link ByteArray}, creating a new {@link ByteArray} instance.
     *
     * @param offset
     *         The starting index from which to begin the slice, inclusive.
     * @param length
     *         The number of elements to include in the slice.
     * @return A new {@link ByteArray} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *         if the specified slice is out of the bounds of the original array.
     */
    public ByteArray slice(int offset, int length) {
        return slice((long) offset, (long) length);
    }

    /**
     * Extracts a slice of elements using {@code long} bounds. See {@link #slice(int, int)}.
     */
    public ByteArray slice(long offset, long length) {
        if (offset < 0 || length < 0 || offset > numberOfElements - length) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * BYTE_BYTES;
        long sliceByteLength = (long) length * BYTE_BYTES;
        MemorySegment sliceSegment = segment.getSegment().asSlice(sliceOffsetInBytes, sliceByteLength);
        ByteArray slice = fromSegment(sliceSegment);
        return slice;
    }
}
