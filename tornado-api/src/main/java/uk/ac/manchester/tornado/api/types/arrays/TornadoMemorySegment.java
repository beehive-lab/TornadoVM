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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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
