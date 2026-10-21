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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * The {@code TornadoMemorySegment} class provides a high-level interface for managing a {@link MemorySegment} with support for different data types.
 * <p>
 * This class allows the allocation of a memory segment with a specific size, and provides methods to set and get values of various types at specific indices relative to a base index.
 * </p>
 */
public class TornadoMemorySegment {
    private MemorySegment segment;

    /**
     * Constructs a {@code TornadoMemorySegment} with a specified byte size and base index.
     * <p>
     * This constructor allocates a new memory segment of the specified byte size and initializes it with a given number of elements.
     * </p>
     *
     * @param segmentByteSize
     *         the size of the memory segment in bytes
     * @param numElements
     *         the number of elements to initialize in the segment
     */
    public TornadoMemorySegment(long segmentByteSize, long numElements) {
        this.segment = Arena.ofAuto().allocate(segmentByteSize, 1);
        this.segment.setAtIndex(ValueLayout.JAVA_LONG, 0, numElements);
    }

    /**
     * The C library's {@code malloc} and {@code free}, for segments whose contents are written in
     * full right after allocation; {@code null} when they cannot be bound (no native access granted
     * to this module), in which case those segments are allocated zero-filled as usual.
     */
    private static final MethodHandle MALLOC;
    private static final MethodHandle FREE;

    static {
        MethodHandle malloc = null;
        MethodHandle free = null;
        try {
            if (!nativeAccessEnabled()) {
                throw new IllegalCallerException("no native access for " + TornadoMemorySegment.class.getModule());
            }
            Linker linker = Linker.nativeLinker();
            SymbolLookup libc = linker.defaultLookup();
            Optional<MemorySegment> mallocSymbol = libc.find("malloc");
            Optional<MemorySegment> freeSymbol = libc.find("free");
            if (mallocSymbol.isPresent() && freeSymbol.isPresent()) {
                malloc = linker.downcallHandle(mallocSymbol.get(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
                free = linker.downcallHandle(freeSymbol.get(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            }
        } catch (IllegalCallerException | UnsupportedOperationException e) {
            // no native access for this module: fall back to zero-filled allocation
            malloc = null;
            free = null;
        }
        MALLOC = malloc;
        FREE = free;
    }

    /**
     * Whether this module may call restricted methods, asked up front so that a JVM without
     * {@code --enable-native-access} for it falls back quietly instead of printing a warning.
     * {@code Module.isNativeAccessEnabled} exists from JDK 22; on JDK 21 the binding is tried.
     */
    private static boolean nativeAccessEnabled() {
        try {
            return (boolean) Module.class.getMethod("isNativeAccessEnabled").invoke(TornadoMemorySegment.class.getModule());
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    /**
     * Allocates a segment of {@code segmentByteSize} bytes whose header ({@code headerBytes}) is zeroed
     * and holds {@code numElements}, and whose element bytes are left as they come from the allocator:
     * for arrays that are filled in full straight away ({@code fromArray}), where zero-filling first
     * would write every byte twice. The memory is freed when the segment is no longer reachable, as for
     * {@link #TornadoMemorySegment(long, long)}, which is used when {@code malloc} is not available.
     */
    static TornadoMemorySegment uninitialized(long segmentByteSize, long numElements, long headerBytes) {
        if (MALLOC != null) {
            try {
                MemorySegment raw = (MemorySegment) MALLOC.invokeExact(segmentByteSize);
                long address = raw.address();
                if (address != 0) {
                    MemorySegment segment = raw.reinterpret(segmentByteSize, Arena.ofAuto(), s -> free(address));
                    segment.asSlice(0, headerBytes).fill((byte) 0);
                    segment.setAtIndex(ValueLayout.JAVA_LONG, 0, numElements);
                    return new TornadoMemorySegment(segment);
                }
            } catch (Throwable t) {
                throw new IllegalStateException("native allocation of " + segmentByteSize + " bytes failed", t);
            }
        }
        return new TornadoMemorySegment(segmentByteSize, numElements);
    }

    private static void free(long address) {
        try {
            FREE.invokeExact(MemorySegment.ofAddress(address));
        } catch (Throwable t) {
            throw new IllegalStateException("free failed", t);
        }
    }

    public TornadoMemorySegment(MemorySegment memorySegment) {
        this.segment = memorySegment;
    }

    /**
     * Returns the underlying {@link MemorySegment}.
     *
     * @return the memory segment
     */
    public MemorySegment getSegment() {
        return segment;
    }

    public void setSegment(MemorySegment segment) {
        this.segment = segment;
    }

    /**
     * Sets a {@code float} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code float} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, float value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_FLOAT, (long) baseIndex + index, value);
    }

    /**
     * Sets a {@code float} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code float} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, float value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_FLOAT, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code float} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code float} value at the specified index
     */
    public float getFloatAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_FLOAT, (long) baseIndex + index);
    }

    /**
     * Returns the {@code float} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code float} value at the specified index
     */
    public float getFloatAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_FLOAT, (long) baseIndex + index);
    }

    /**
     * Sets a {@code double} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code double} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, double value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_DOUBLE, (long) baseIndex + index, value);
    }

    /**
     * Sets a {@code double} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code double} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, double value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_DOUBLE, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code double} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code double} value at the specified index
     */
    public double getDoubleAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_DOUBLE, (long) baseIndex + index);
    }

    /**
     * Returns the {@code double} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code double} value at the specified index
     */
    public double getDoubleAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_DOUBLE, (long) baseIndex + index);
    }

    /**
     * Sets a {@code byte} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code byte} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, byte value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_BYTE, (long) baseIndex + index, value);
    }

    /**
     * Sets a {@code byte} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code byte} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, byte value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_BYTE, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code byte} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code byte} value at the specified index
     */
    public byte getByteAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_BYTE, (long) baseIndex + index);
    }

    /**
     * Returns the {@code byte} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code byte} value at the specified index
     */
    public byte getByteAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_BYTE, (long) baseIndex + index);
    }

    /**
     * Sets a {@code char} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code char} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, char value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_CHAR, (long) baseIndex + index, value);
    }

    /**
     * Sets a {@code char} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code char} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, char value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_CHAR, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code char} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code char} value at the specified index
     */
    public char getCharAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_CHAR, (long) baseIndex + index);
    }

    /**
     * Returns the {@code char} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code char} value at the specified index
     */
    public char getCharAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_CHAR, (long) baseIndex + index);
    }

    /**
     * Sets an {@code int} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code int} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, int value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_INT, (long) baseIndex + index, value);
    }

    /**
     * Sets an {@code int} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code int} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, int value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_INT, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code int} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code int} value at the specified index
     */
    public int getIntAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_INT, (long) baseIndex + index);
    }

    /**
     * Returns the {@code int} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code int} value at the specified index
     */
    public int getIntAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_INT, (long) baseIndex + index);
    }

    /**
     * Sets a {@code long} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code long} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, long value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_LONG, (long) baseIndex + index, value);
    }

    /**
     * Sets a {@code long} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code long} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, long value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_LONG, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code long} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code long} value at the specified index
     */
    public long getLongAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_LONG, (long) baseIndex + index);
    }

    /**
     * Returns the {@code long} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code long} value at the specified index
     */
    public long getLongAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_LONG, (long) baseIndex + index);
    }

    /**
     * Sets a {@code short} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code short} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(int index, short value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_SHORT, (long) baseIndex + index, value);
    }

    /**
     * Sets a {@code short} value at the specified index.
     *
     * @param index
     *         the index where the value will be set
     * @param value
     *         the {@code short} value to set
     * @param baseIndex
     *         the base index used for calculating the actual index
     */
    public void setAtIndex(long index, short value, int baseIndex) {
        segment.setAtIndex(ValueLayout.JAVA_SHORT, (long) baseIndex + index, value);
    }

    /**
     * Returns the {@code short} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code short} value at the specified index
     */
    public short getShortAtIndex(int index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_SHORT, (long) baseIndex + index);
    }

    /**
     * Returns the {@code short} value at the specified index.
     *
     * @param index
     *         the index from which the value will be retrieved
     * @param baseIndex
     *         the base index used for calculating the actual index
     * @return the {@code short} value at the specified index
     */
    public short getShortAtIndex(long index, int baseIndex) {
        return segment.getAtIndex(ValueLayout.JAVA_SHORT, (long) baseIndex + index);
    }
}
