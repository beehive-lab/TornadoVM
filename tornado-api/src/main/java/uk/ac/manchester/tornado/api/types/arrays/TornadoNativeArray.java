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

import uk.ac.manchester.tornado.api.types.tensors.Tensor;

import static java.lang.String.format;

/**
 * This abstract sealed class represents the common functionality of the TornadoVM custom native arrays,
 * (e.g., {@link ByteArray}, {@link IntArray}, etc.)
 *
 * <p>
 * The class provides methods for retrieving the number of elements stored in the native arrays,
 * for obtaining the underlying memory segment, for clearing the data, for calculating the total number of
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
        LongArray, ShortArray, Int8Array, Tensor {

    /**
     * The size of the header in bytes. It sets the default value either to 16 or 24, depending on whether the uncompressed flags
     * are passed by the user. It can also be configurable through the "tornado.panama.objectHeader" system property.
     */
    public static final long ARRAY_HEADER = Long.parseLong(System.getProperty("tornado.panama.objectHeader", getDefaultHeaderSize()));

    private static String getDefaultHeaderSize() {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean isUncompressed = jvmArgs.contains("-XX:-UseCompressedOops") ||
                jvmArgs.contains("-XX:-UseCompressedClassPointers");

        return isUncompressed ? "24" : "16";
    }

    /**
     * Returns the number of elements stored in the native array.
     *
     * @return The number of elements of the native data array.
     */
    public abstract int getSize();

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
    protected abstract void clear();

    public abstract int getElementSize();

    /**
     * Checks that the byte size is a multiple of the element size.
     */
    static void ensureMultipleOfElementSize(long byteSize, long elementSize) {
        if (byteSize % elementSize != 0) {
            throw new IllegalArgumentException(format("The byte size (%d) is not a multiple of the element size (%d)", byteSize, elementSize));
        }
    }

}
