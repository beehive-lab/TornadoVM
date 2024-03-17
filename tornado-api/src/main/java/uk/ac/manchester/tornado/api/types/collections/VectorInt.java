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
package uk.ac.manchester.tornado.api.types.collections;

import java.lang.foreign.MemorySegment;
import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.utils.IntOps;

public final class VectorInt implements TornadoCollectionInterface<IntBuffer> {

    private static final int ELEMENT_SIZE = 1;
    private final int numElements;
    private final IntArray storage;

    /**
     * Creates a vector using the provided backing array.
     *
     * @param numElements
     *     number of elements
     * @param array
     *     reference to the input array
     */
    public VectorInt(int numElements, IntArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     *     number of elements
     */
    public VectorInt(int numElements) {
        this(numElements, new IntArray(numElements));
    }

    /**
     * Creates an new vector from the provided storage.
     *
     * @param storage
     *     vector int array
     */
    public VectorInt(IntArray storage) {
        this(storage.getSize() / ELEMENT_SIZE, storage);
    }

    public static int min(VectorInt v) {
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < v.storage.getSize(); i++) {
            result = Math.min(v.storage.get(i), result);
        }
        return result;
    }

    public static int max(VectorInt v) {
        int result = Integer.MIN_VALUE;
        for (int i = 0; i < v.storage.getSize(); i++) {
            result = Math.max(v.storage.get(i), result);
        }
        return result;
    }

    /**
     * Perform dot-product.
     *
     * @return int value
     */
    public static int dot(VectorInt a, VectorInt b) {
        int sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += a.get(i) * b.get(i);
        }
        return sum;
    }

    public IntArray getArray() {
        return storage;
    }

    /**
     * Returns the int at the given index of this vector.
     *
     * @param index
     *     index value
     * @return int
     */
    public int get(int index) {
        return storage.get(index);
    }

    /**
     * Sets the int at the given index of this vector.
     *
     * @param index
     *     index value
     * @param value
     *     value to be set in position index
     */
    public void set(int index, int value) {
        storage.set(index, value);
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     *     assign an input vector int to the internal array
     */
    public void set(VectorInt values) {
        for (int i = 0; i < values.storage.getSize(); i++) {
            storage.set(i, values.storage.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     *     assign an input vector int to the internal array
     */
    public void set(int[] values) {
        for (int i = 0; i < values.length; i++) {
            storage.set(i, values[i]);
        }
    }

    /**
     * Sets all elements to value.
     *
     * @param value
     *     Fill input vector with value
     */
    public void fill(int value) {
        for (int i = 0; i < storage.getSize(); i++) {
            storage.set(i, value);
        }
    }

    /**
     * Returns slice of this vector.
     *
     * @param start
     *     starting index
     * @param length
     *     number of elements
     * @return {@link VectorInt}
     */
    public VectorInt subVector(int start, int length) {
        final VectorInt v = new VectorInt(length);
        for (int i = 0; i < length; i++) {
            v.storage.set(i, storage.get(i + start));
        }

        return v;
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link VectorInt}
     */
    public VectorInt duplicate() {
        IntArray cp = new IntArray(storage.getSize());
        for (int i = 0; i < cp.getSize(); i++) {
            cp.set(i, storage.get(i));
        }
        return new VectorInt(cp);
    }

    /**
     * Vector equality test.
     *
     * @param vector
     *     Input vector to compare
     * @return true if vectors match
     */
    public boolean isEqual(VectorInt vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * Prints the vector using the specified format string.
     *
     * @param fmt
     *     String Format
     * @return String
     */
    public String toString(String fmt) {
        StringBuilder sb = new StringBuilder("[");
        sb.append("[ ");
        for (int i = 0; i < numElements; i++) {
            sb.append(String.format(fmt, get(i)) + " ");
        }
        sb.append("]");
        return sb.toString();
    }

    public String toString() {
        String str = String.format("VectorInt <%d>", numElements);
        if (numElements < 32) {
            str += toString(IntOps.FMT);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage.toHeapArray());
    }

    @Override
    public int size() {
        return numElements;
    }

    public int getLength() {
        return numElements;
    }

    public void clear() {
        storage.clear();
    }

    @Override
    public long getNumBytes() {
        return storage.getNumBytesOfSegment();
    }

    @Override
    public long getNumBytesWithHeader() {
        return storage.getNumBytesOfSegmentWithHeader();
    }

    @Override
    public MemorySegment getSegment() {
        return getArray().getSegment();
    }

    @Override
    public MemorySegment getSegmentWithHeader() {
        return getArray().getSegmentWithHeader();
    }
}
