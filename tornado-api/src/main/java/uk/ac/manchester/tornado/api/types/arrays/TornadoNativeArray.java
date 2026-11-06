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

import java.lang.foreign.MemorySegment;
import java.lang.management.ManagementFactory;
import java.util.List;

import uk.ac.manchester.tornado.api.memory.CUDADeviceViewBinding;
import uk.ac.manchester.tornado.api.memory.DeviceViewBinding;

import static java.lang.String.format;

/**
 * This abstract sealed class represents the common functionality of the TornadoVM custom native arrays, (e.g., {@link ByteArray}, {@link IntArray}, etc.)
 *
 * <p>
 * The class provides methods for retrieving the number of elements stored in the native arrays, for obtaining the underlying memory segment, for clearing the data, for calculating the total number of
 * bytes occupied by the memory segment, and for getting the number of bytes, excluding the array header size.
 * </p>
 *
 * <p>
 * The constant {@link ARRAY_HEADER} represents the size of the header in bytes.
 * </p>
 */
public abstract sealed class TornadoNativeArray //
        permits ByteArray, CharArray, DoubleArray, //
        FloatArray, HalfFloatArray, IntArray, //
        LongArray, ShortArray, Int8Array, FP8Array, BFloat16Array {

    /**
     * The size of the header in bytes. It sets the default value either to 16 or 24, depending on whether the uncompressed flags are passed by the user. It can also be configurable through the
     * "tornado.panama.objectHeader" system property.
     */
    public static final long ARRAY_HEADER = Long.parseLong(System.getProperty("tornado.panama.objectHeader", getDefaultHeaderSize()));

    private static String getDefaultHeaderSize() {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean isUncompressed = jvmArgs.contains("-XX:-UseCompressedOops") || jvmArgs.contains("-XX:-UseCompressedClassPointers");

        return isUncompressed ? "24" : "16";
    }

    /**
     * Checks that the byte size is a multiple of the element size.
     */
    static void ensureMultipleOfElementSize(long byteSize, long elementSize) {
        if (byteSize % elementSize != 0) {
            throw new IllegalArgumentException(format("The byte size (%d) is not a multiple of the element size (%d)", byteSize, elementSize));
        }
    }

    /**
     * Checks that a native array can hold the given number of elements.
     *
     * @return The number of elements.
     */
    static long checkNumElements(long numElements) {
        if (numElements < 0) {
            throw new IllegalArgumentException(format("Invalid number of elements (%d): the number of elements cannot be negative", numElements));
        }
        return numElements;
    }

    /**
     * Computes the number of elements stored in a region of {@code byteSize} bytes, checking that the size is a multiple of the element size.
     *
     * @return The number of elements.
     */
    static long toNumElements(long byteSize, long elementSize) {
        ensureMultipleOfElementSize(byteSize, elementSize);
        return checkNumElements(byteSize / elementSize);
    }

    /**
     * Narrows an element count to an {@code int} for the {@code int}-sized API, failing instead of truncating when the count does not fit.
     */
    static int toIntSize(long numElements) {
        if (numElements > Integer.MAX_VALUE) {
            throw new IllegalStateException(format("The array has %d elements, more than an int can hold: use getSizeLong()", numElements));
        }
        return (int) numElements;
    }

    /**
     * Returns the number of elements stored in the native array.
     *
     * @return The number of elements of the native data array.
     * @throws IllegalStateException
     *         if the array has more than {@link Integer#MAX_VALUE} elements; use {@link #getSizeLong()} for those.
     */
    public abstract int getSize();

    /**
     * Returns the number of elements stored in the native array as a {@code long}. Unlike {@link #getSize()}, this works for arrays with more than
     * {@link Integer#MAX_VALUE} elements.
     *
     * @return The number of elements of the native data array.
     */
    public abstract long getSizeLong();

    /**
     * Returns the underlying {@link MemorySegment} of the native array, without the Tornado Array header.
     *
     * @return The {@link MemorySegment} associated with the native array instance.
     */
    public abstract MemorySegment getSegment();

    /**
     * Returns the underlying {@link MemorySegment} of the native array, including the header.
     *
     * @return The {@link MemorySegment} associated with the native array instance.
     */
    public abstract MemorySegment getSegmentWithHeader();

    /**
     * Returns the total number of bytes that the {@link MemorySegment} occupies, including the header bytes.
     *
     * @return The total number of bytes of the {@link MemorySegment}.
     */
    public abstract long getNumBytesOfSegmentWithHeader();

    /**
     * Returns the number of bytes of the {@link MemorySegment}, excluding the header bytes.
     *
     * @return The number of bytes of the raw data in the {@link MemorySegment}.
     */
    public abstract long getNumBytesOfSegment();

    /**
     * Clears the contents of the native array.
     */
    protected void clear() {

    }

    public abstract int getElementSize();

    /**
     * The device-only state of this array when it was created by a {@code deviceView} factory, and
     * {@code null} for an ordinary array. Set once, by the factory.
     */
    private DeviceViewBinding deviceView;

    /**
     * Marks this instance as a device view. Called by the {@code deviceView} factories only; an
     * array that already allocated host storage must never become one.
     */
    protected final void markAsDeviceView(DeviceViewBinding binding) {
        if (this.deviceView != null) {
            throw new IllegalStateException("This array is already a device view");
        }
        this.deviceView = binding;
    }

    /**
     * Whether this array is a device view: it has no host {@link MemorySegment}, its storage is a
     * device allocation somebody else owns, and the device copy has no native-array header. The
     * default for every array built with a constructor or a {@code fromX} factory is {@code false},
     * and nothing about those arrays changes.
     */
    public final boolean isDeviceView() {
        return deviceView != null;
    }

    /**
     * The binding of this device view, for code that reads or changes the device buffer and the
     * logical length.
     *
     * @throws IllegalStateException
     *         if this array is not a device view.
     */
    public final DeviceViewBinding deviceViewBinding() {
        if (deviceView == null) {
            throw new IllegalStateException("This array is not a device view: it was allocated on the host and has a header on the device");
        }
        return deviceView;
    }

    /**
     * Whether this array is a device view created by a {@code cudaDeviceView} factory, the
     * compatibility form of {@link #isDeviceView()} for code written against the CUDA-only API.
     */
    public final boolean isCUDADeviceView() {
        return deviceView instanceof CUDADeviceViewBinding;
    }

    /**
     * The binding of a view created by a {@code cudaDeviceView} factory, the compatibility form of
     * {@link #deviceViewBinding()} for code written against the CUDA-only API.
     *
     * @throws IllegalStateException
     *         if this array is not a device view, or if a {@code deviceView} factory created it.
     */
    public final CUDADeviceViewBinding cudaDeviceViewBinding() {
        if (deviceView instanceof CUDADeviceViewBinding binding) {
            return binding;
        }
        if (deviceView != null) {
            throw new IllegalStateException("This device view was created by a deviceView factory: use deviceViewBinding()");
        }
        throw new IllegalStateException("This array is not a device view: it was allocated on the host and has a header on the device");
    }

    /** Fails when an operation that needs host storage is attempted on a device view. */
    protected final void checkNotDeviceView(String operation) {
        if (deviceView != null) {
            throw new UnsupportedOperationException(operation + " is not available on a device view: the data lives in memory that TornadoVM does not own and cannot read from the host. "
                    + "Copy it out with the owning library instead. View: " + deviceView);
        }
    }
}
