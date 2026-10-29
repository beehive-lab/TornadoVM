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

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.internal.annotations.SegmentElementSize;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

import uk.ac.manchester.tornado.api.memory.DeviceViewBinding;

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * This class represents an array of floats stored in native memory. The float data is stored in a {@link MemorySegment}, which represents a contiguous region of off-heap memory. The class also
 * encapsulates methods for setting and getting float values, for initializing the float array, and for converting the array to and from different representations.
 */
@SegmentElementSize(size = 4)
public final class FloatArray extends TornadoNativeArray {
    private static final int FLOAT_BYTES = 4;
    private TornadoMemorySegment segment;

    private long numberOfElements;

    private int arrayHeaderSize;

    private int baseIndex;

    private long segmentByteSize;

    /**
     * Constructs a new instance of the {@link FloatArray} that will store a user-specified number of elements.
     *
     * @param numberOfElements
     *         The number of elements in the array.
     */
    public FloatArray(int numberOfElements) {
        this((long) numberOfElements);
    }

    /**
     * Constructs a new instance of the {@link FloatArray} that will store a user-specified number of elements. The number of elements can exceed
     * {@link Integer#MAX_VALUE}; access such arrays with the {@code long}-index accessors.
     *
     * @param numberOfElements
     *         The number of elements in the array.
     */
    public FloatArray(long numberOfElements) {
        this.numberOfElements = checkNumElements(numberOfElements);
        arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        baseIndex = arrayHeaderSize / FLOAT_BYTES;
        segmentByteSize = (long) numberOfElements * FLOAT_BYTES + arrayHeaderSize;
        segment = new TornadoMemorySegment(segmentByteSize, numberOfElements);
    }

    /**
     * Constructs a new instance of the {@link FloatArray} by wrapping an existing {@link MemorySegment} without copying its contents.
     *
     * @param existingSegment
     *         The {@link MemorySegment} containing *both* the off-heap float *header* and *data*.
     */
    private FloatArray(MemorySegment existingSegment) {
        this.arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        this.baseIndex = arrayHeaderSize / FLOAT_BYTES;

        // Calculate number of elements from segment size
        long dataSize = existingSegment.byteSize() - arrayHeaderSize;
        this.numberOfElements = toNumElements(dataSize, FLOAT_BYTES);

        // Set up the segment and initialize header
        this.segmentByteSize = existingSegment.byteSize();
        this.segment = new TornadoMemorySegment(existingSegment);
        this.segment.getSegment().setAtIndex(JAVA_LONG, 0, numberOfElements);
    }

    /**
     * Constructs a new {@link FloatArray} instance by concatenating the contents of the given array of {@link FloatArray} instances.
     *
     * @param arrays
     *         An array of {@link FloatArray} instances to be concatenated into the new instance.
     */
    public FloatArray(FloatArray... arrays) {
        concat(arrays);
    }

    /**
     * Internal method used to create a new instance of the {@link FloatArray} from on-heap data.
     *
     * @param values
     *         The on-heap float array to create the instance from.
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
     *         The on-heap float array to create the instance from.
     * @return A new {@link FloatArray} instance, initialized with values of the on-heap float array.
     */
    public static FloatArray fromArray(float[] values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from a set of float values.
     *
     * @param values
     *         The float values to initialize the array with.
     * @return A new {@link FloatArray} instance, initialized with the given values.
     */
    public static FloatArray fromElements(float... values) {
        return createSegment(values);
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from a {@link MemorySegment}.
     *
     * @param segment
     *         The {@link MemorySegment} containing the off-heap float data.
     * @return A new {@link FloatArray} instance, initialized with the segment data.
     */
    public static FloatArray fromSegment(MemorySegment segment) {
        long byteSize = segment.byteSize();
        long numElements = toNumElements(byteSize, FLOAT_BYTES);
        FloatArray floatArray = new FloatArray(numElements);
        MemorySegment.copy(segment, 0, floatArray.segment.getSegment(), (long) floatArray.baseIndex * FLOAT_BYTES, byteSize);
        return floatArray;
    }

    /**
     * Creates a new instance of the {@link FloatArray} class by wrapping an existing {@link MemorySegment} without copying its contents.
     *
     * @param segment
     *         The {@link MemorySegment} containing *both* the off-heap float *header* and *data*.
     * @return A new {@link FloatArray} instance that wraps the given segment.
     */
    public static FloatArray fromSegmentShallow(MemorySegment segment) {
        return new FloatArray(segment);
    }

    /**
     * Creates a new instance of the {@link FloatArray} class from a {@link FloatBuffer}.
     *
     * @param buffer
     *         The {@link FloatBuffer} containing the float data.
     * @return A new {@link FloatArray} instance, initialized with the buffer data.
     */
    public static FloatArray fromFloatBuffer(FloatBuffer buffer) {
        int numElements = buffer.remaining();
        FloatArray floatArray = new FloatArray(numElements);
        floatArray.getSegment().copyFrom(MemorySegment.ofBuffer(buffer));
        return floatArray;
    }

    /**
     * Factory method to initialize a {@link FloatArray}. This method can be invoked from a Task-Graph.
     *
     * @param array
     *         Input Array.
     * @param value
     *         The float value to initialize the {@code FloatArray} instance with.
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
     *         Variable number of {@link FloatArray} objects to be concatenated.
     * @return A new {@link FloatArray} instance containing all the elements of the input arrays, concatenated in the order they were provided.
     */
    public static FloatArray concat(FloatArray... arrays) {
        long newSize = checkNumElements(Arrays.stream(arrays).mapToLong(FloatArray::getSizeLong).sum());
        FloatArray concatArray = new FloatArray(newSize);
        long currentPositionBytes = 0;
        for (FloatArray array : arrays) {
            MemorySegment.copy(array.getSegment(), 0, concatArray.getSegment(), currentPositionBytes, array.getNumBytesOfSegment());
            currentPositionBytes += array.getNumBytesOfSegment();
        }
        return concatArray;
    }

    /**
     * Converts the float data from off-heap to on-heap, by copying the values of a {@link FloatArray} instance into a new on-heap array.
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
     *         The index at which to set the float value.
     * @param value
     *         The float value to store at the specified index.
     */
    public void set(int index, float value) {
        segment.setAtIndex(index, value, baseIndex);
    }

    /**
     * Sets the float value at a specified index of the {@link FloatArray} instance.
     *
     * @param index
     *         The index at which to set the float value.
     * @param value
     *         The float value to store at the specified index.
     */
    public void set(long index, float value) {
        segment.setAtIndex(index, value, baseIndex);
    }

    /**
     * Gets the float value stored at the specified index of the {@link FloatArray} instance.
     *
     * @param index
     *         The index of which to retrieve the float value.
     * @return
     */
    public float get(int index) {
        return segment.getFloatAtIndex(index, baseIndex);
    }

    /**
     * Gets four consecutive float values starting at the specified element index as a {@link Float4}. On backends
     * with vector loads this maps to a single 16-byte load, so {@code index} must be a multiple of 4.
     *
     * @param index
     *         The element index of the first lane; must be a multiple of 4.
     * @return A {@link Float4} holding elements {@code index} to {@code index + 3}.
     */
    public Float4 getFloat4(int index) {
        // Unchecked, like get(int): device kernels cannot throw. Callers guarantee bounds and alignment.
        return new Float4(get(index), get(index + 1), get(index + 2), get(index + 3));
    }

    /**
     * Gets the float value stored at the specified index of the {@link FloatArray} instance.
     *
     * @param index
     *         The index of which to retrieve the float value.
     * @return
     */
    public float get(long index) {
        return segment.getFloatAtIndex(index, baseIndex);
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
     *         The float value to initialize the {@link FloatArray} instance with.
     */
    public void init(float value) {
        for (long i = 0; i < numberOfElements; i++) {
            segment.setAtIndex(i, value, baseIndex);
        }
    }

    /**
     * Returns the number of float elements stored in the {@link FloatArray} instance.
     *
     * @return
     */
    /**
     * Constructs a {@link FloatArray} that is a device view: no host memory is allocated, and the
     * array stands for a device allocation somebody else owns, bound later with
     * {@code bindExternalBuffer}.
     */
    private FloatArray(DeviceViewBinding binding) {
        this.numberOfElements = binding.capacityElements();
        this.arrayHeaderSize = (int) TornadoNativeArray.ARRAY_HEADER;
        this.baseIndex = arrayHeaderSize / FLOAT_BYTES;
        this.segmentByteSize = 0;
        this.segment = null;
        markAsDeviceView(binding);
    }

    /**
     * Creates a device-only view that can address up to {@code capacityElements} elements of
     * foreign device memory.
     *
     * <p>
     * Unlike {@code new FloatArray(n)}, this allocates nothing: no host {@link MemorySegment} and no
     * TornadoVM device buffer. The view carries no native-array header, so the kernel reads element
     * {@code i} at {@code address + i * 4} and a foreign library's buffer can be used exactly as
     * that library laid it out. Bind an address to it with
     * {@link uk.ac.manchester.tornado.api.TornadoExecutionPlan#bindExternalBuffer} before executing a
     * plan that uses it; the address stays the caller's to free.
     * </p>
     *
     * <p>
     * Host {@code get}/{@code set}, {@code toHeapArray}, {@code slice} and the segment accessors are
     * not available on a view: there is no host memory behind it. {@code getSize()} reports the
     * binding's logical length, which starts at the capacity and follows each rebind.
     * </p>
     *
     * @param capacityElements
     *         the largest number of elements any binding of this view may hold.
     * @return A detached {@link FloatArray} device view.
     */
    public static FloatArray deviceView(long capacityElements) {
        return new FloatArray(new DeviceViewBinding(checkNumElements(capacityElements), FLOAT_BYTES));
    }

    /**
     * Creates a device-only view. See {@link #deviceView(long)}.
     */
    public static FloatArray deviceView(int capacityElements) {
        return deviceView((long) capacityElements);
    }

    @Override
    public int getSize() {
        return toIntSize(getSizeLong());
    }

    @Override
    public long getSizeLong() {
        return isDeviceView() ? deviceViewBinding().logicalLength() : numberOfElements;
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link FloatArray} instance.
     *
     * @return The {@link MemorySegment} associated with the {@link FloatArray} instance.
     */
    @Override
    public MemorySegment getSegment() {
        checkNotDeviceView("getSegment()");
        return segment.getSegment().asSlice(TornadoNativeArray.ARRAY_HEADER);
    }

    /**
     * Returns the underlying {@link MemorySegment} of the {@link FloatArray} instance, including the header.
     *
     * @return The {@link MemorySegment} associated with the {@link FloatArray} instance.
     */
    @Override
    public MemorySegment getSegmentWithHeader() {
        checkNotDeviceView("getSegmentWithHeader()");
        return segment.getSegment();
    }

    /**
     * Returns the total number of bytes that the {@link MemorySegment}, associated with the {@link FloatArray} instance, occupies.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegmentWithHeader() {
        // A device view has no header, so both byte counts are the same: the payload it spans.
        return isDeviceView() ? getNumBytesOfSegment() : segmentByteSize;
    }

    /**
     * Returns the number of bytes of the {@link MemorySegment} that is associated with the {@link FloatArray} instance, excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    @Override
    public long getNumBytesOfSegment() {
        return isDeviceView() ? getSizeLong() * FLOAT_BYTES : segmentByteSize - TornadoNativeArray.ARRAY_HEADER;
    }

    /**
     * Extracts a slice of elements from a given {@link FloatArray}, creating a new {@link FloatArray} instance.
     *
     * @param offset
     *         The starting index from which to begin the slice, inclusive.
     * @param length
     *         The number of elements to include in the slice.
     * @return A new {@link FloatArray} instance representing the specified slice of the original array.
     * @throws IllegalArgumentException
     *         if the specified slice is out of the bounds of the original array.
     */
    public FloatArray slice(int offset, int length) {
        return slice((long) offset, (long) length);
    }

    /**
     * Extracts a slice of elements using {@code long} bounds. See {@link #slice(int, int)}.
     */
    public FloatArray slice(long offset, long length) {
        if (offset < 0 || length < 0 || offset > numberOfElements - length) {
            throw new IllegalArgumentException("Slice out of bounds");
        }

        long sliceOffsetInBytes = TornadoNativeArray.ARRAY_HEADER + (long) offset * FLOAT_BYTES;
        long sliceByteLength = (long) length * FLOAT_BYTES;
        MemorySegment sliceSegment = segment.getSegment().asSlice(sliceOffsetInBytes, sliceByteLength);
        FloatArray slice = fromSegment(sliceSegment);
        return slice;
    }
}
