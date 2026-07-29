/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.BFloat16;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * A native array of bfloat16 values (see {@link BFloat16}) stored in off-heap memory. Each element is the two-byte raw bfloat16 bit pattern held in a {@link MemorySegment}; this is a type-safe
 * container for what would otherwise be a {@link ShortArray} of bf16 bits.
 *
 * <p>bfloat16 is a <em>storage</em> type: {@link #get(int)}/{@link #set(int, short)} move the raw
 * {@code short} bit pattern (so a device kernel can decode it with the hardware-accelerated
 * {@link BFloat16#bf16ToFloat(short)}), while {@link #getFloat(int)}/{@link #setFloat(int, float)}
 * are host-side conveniences that decode/encode through the software codec. The element size is two
 * bytes, mirroring {@link HalfFloatArray}; the two differ only in the float conversion.</p>
 */
@SegmentElementSize(size = 2)
public final class BFloat16Array extends TornadoNativeArray {

    private static final int BF16_BYTES = 2;
    private TornadoMemorySegment segment;

    private int numberOfElements;

    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link BFloat16Array} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *         The number of elements in the array.
     */
    public BFloat16Array(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / BF16_BYTES;
        segmentByteSize = (long) numberOfElements * BF16_BYTES + arrayHeaderSize;
        segment = new TornadoMemorySegment(segmentByteSize, numberOfElements);
    }

    /**
     * Constructs a new instance of the {@link BFloat16Array} by wrapping an existing {@link MemorySegment} without copying its contents.
     *
     * @param existingSegment
     *         The {@link MemorySegment} containing *both* the off-heap bfloat16 *header* and *data*.
     */
    private BFloat16Array(MemorySegment existingSegment) {
        this.arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        this.baseIndex = arrayHeaderSize / BF16_BYTES;

        // Calculate number of elements from segment size
        long dataSize = existingSegment.byteSize() - arrayHeaderSize;
        ensureMultipleOfElementSize(dataSize, BF16_BYTES);
        this.numberOfElements = (int) (dataSize / BF16_BYTES);

        // Set up the segment and initialize header
        this.segmentByteSize = existingSegment.byteSize();
        this.segment = new TornadoMemorySegment(existingSegment);
        this.segment.getSegment().setAtIndex(JAVA_INT, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link BFloat16Array} instance by concatenating the contents of the given array of {@link BFloat16Array} instances.
     *
     * @param arrays
     *         An array of {@link BFloat16Array} instances to be concatenated into the new instance.
     */
    public BFloat16Array(BFloat16Array... arrays) {
        concat(arrays);
    }

    /**
     * Creates a new {@link BFloat16Array} from an array of raw bfloat16 bit patterns.
     *
     * @param bits
     *         The raw bfloat16 {@code short} values.
     * @return A new {@link BFloat16Array} initialized with the given bits.
     */
    public static BFloat16Array fromShorts(short... bits) {
        BFloat16Array array = new BFloat16Array(bits.length);
        for (int i = 0; i < bits.length; i++) {
            array.set(i, bits[i]);
        }
        return array;
    }

    /**
     * Creates a new {@link BFloat16Array} from an array of float values, each encoded to its nearest bfloat16 bit pattern.
     *
     * @param values
     *         The float values to encode.
     * @return A new {@link BFloat16Array} initialized with the encoded values.
     */
    public static BFloat16Array fromFloats(float... values) {
        BFloat16Array array = new BFloat16Array(values.length);
        for (int i = 0; i < values.length; i++) {
            array.setFloat(i, values[i]);
        }
        return array;
    }

    /**
     * Creates a new instance of the {@link BFloat16Array} class from a {@link MemorySegment}.
     *
     * @param segment
     *         The {@link MemorySegment} containing the off-heap bfloat16 data.
     * @return A new {@link BFloat16Array} instance, initialized with the segment data.
     */
    public static BFloat16Array fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        int numElements = (int) (byteSize / BF16_BYTES);
        ensureMultipleOfElementSize(byteSize, BF16_BYTES);
        BFloat16Array bfloat16Array = new BFloat16Array(numElements);
        MemorySegment.copy(segment, 0, bfloat16Array.segment.getSegment(), (long) bfloat16Array.baseIndex * BF16_BYTES, byteSize);
        return bfloat16Array;
    }

    /**
     * Creates a new instance of the {@link BFloat16Array} class by wrapping an existing {@link MemorySegment} without copying its contents.
     *
     * @param segment
     *         The {@link MemorySegment} containing *both* the off-heap bfloat16 *header* and *data*.
     * @return A new {@link BFloat16Array} instance that wraps the given segment.
     */
    public static BFloat16Array fromSegmentShallow(MemorySegment segment) {
        return new BFloat16Array(segment);
    }

    /**
     * Concatenates multiple {@link BFloat16Array} instances into a single {@link BFloat16Array}.
     *
     * @param arrays
     *         Variable number of {@link BFloat16Array} objects to be concatenated.
     * @return A new {@link BFloat16Array} instance containing all the elements of the input arrays, concatenated in the order they were provided.
     */
    public static BFloat16Array concat(BFloat16Array... arrays) {
        int newSize = Arrays.stream(arrays).mapToInt(BFloat16Array::getSize).sum();
        BFloat16Array concatArray = new BFloat16Array(newSize);
        long currentPositionBytes = 0;
        for (BFloat16Array array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Initializes all elements of a {@link BFloat16Array} with a raw bfloat16 bit pattern. This method can be invoked from a Task-Graph.
     *
     * @param array
     *         Input array.
     * @param bits
     *         The raw bfloat16 bit pattern to broadcast.
     */
    public static void initialize(BFloat16Array array, short bits) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, bits);
        }
    }

    /**
     * Copies the raw bfloat16 bit patterns of this array into a new on-heap {@code short} array.
     *
     * @return A new {@code short} array with the raw bfloat16 bits.
     */
    public short[] toShortArray() {
        short[] outputArray = new short[getSize()];
        for (int i = 0; i < getSize(); i++) {
            outputArray[i] = get(i);
        }
        return outputArray;
    }

    /**
     * Sets the raw bfloat16 bit pattern at a specified index.
     *
     * @param index
     *         The index at which to set the value.
     * @param bits
     *         The raw bfloat16 {@code short} bit pattern.
     */
    public void set(int index, short bits) {
        segment.setAtIndex(index, bits, baseIndex);
    }

    /**
     * Gets the raw bfloat16 bit pattern stored at the specified index. Inside a device kernel this is
     * decoded to float with the hardware-accelerated {@link BFloat16#bf16ToFloat(short)}.
     *
     * @param index
     *         The index to retrieve.
     * @return The raw bfloat16 {@code short} bit pattern at the given index.
     */
    public short get(int index) {
        return segment.getShortAtIndex(index, baseIndex);
    }

    /**
     * Host-side convenience: encodes a float to bfloat16 and stores it at the given index.
     *
     * @param index
     *         The index at which to set the value.
     * @param value
     *         The float value to encode and store.
     */
    public void setFloat(int index, float value) {
        segment.setAtIndex(index, BFloat16.bf16FromFloat(value), baseIndex);
    }

    /**
     * Host-side convenience: reads the raw bfloat16 bits at the given index and decodes them to float.
     *
     * @param index
     *         The index to retrieve.
     * @return The decoded float value.
     */
    public float getFloat(int index) {
        return BFloat16.bf16ToFloat(get(index));
    }

    /**
     * Sets all the values of the {@link BFloat16Array} instance to zero.
     */
    @Override
    public void clear() {
        init((short) 0);
    }

    @Override
    public int getElementSize() {
        return BF16_BYTES;
    }

    /**
     * Initializes all the elements of the {@link BFloat16Array} instance with a raw bfloat16 bit pattern.
     *
     * @param bits
     *         The raw bfloat16 bit pattern.
     */
    public void init(short bits) {
        for (int i = 0; i < getSize(); i++) {
            segment.setAtIndex(i, bits, baseIndex);
        }
    }

    /**
     * Returns the number of bfloat16 elements stored in the {@link BFloat16Array} instance.
     *
     * @return The number of elements.
     */
    @Override
    public int getSize() {
        return numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link BFloat16Array} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link BFloat16Array} instance.
     */
    @Override
    public MemorySegment getSegment() {
        return segment.getSegment().asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link BFloat16Array} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link BFloat16Array} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        return segment.getSegment();
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link BFloat16Array} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        return segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link BFloat16Array} instance, excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Extracts a slice of elements from a given {@link BFloat16Array}, creating a new {@link BFloat16Array} instance.
     *
     * @param offset
     *         The starting index from which to begin the slice, inclusive.
     * @param length
     *         The number of elements to include in the slice.
     * @return A new {@link BFloat16Array} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *         if the specified slice is out of the bounds of the original array.
     */
    public BFloat16Array slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > getSize()) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * BF16_BYTES;
        long sliceByteLength = (long) length * BF16_BYTES;
        MemorySegment sliceSegment = segment.getSegment().asSlice(sliceOffsetInBytes, sliceByteLength);
        BFloat16Array slice = fromSegment(sliceSegment);
        return slice;
    }

}
