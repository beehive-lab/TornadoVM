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
package uk.ac.manchester.tornado.api.types.collections;

import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

import java.lang.foreign.MemorySegment;
import java.nio.ShortBuffer;

public final class VectorHalf implements TornadoCollectionInterface<ShortBuffer> {

    private static final int ELEMENT_SIZE = 1;
    private final int numElements;
    private final HalfFloatArray storage;

    public VectorHalf(int numElements, HalfFloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     *     Number of elements
     */
    public VectorHalf(int numElements) {
        this(numElements, new HalfFloatArray(numElements));
    }

    /**
     * Creates a new vector from the provided storage.
     *
     * @param storage
     *     Array to be stored
     */
    public VectorHalf(HalfFloatArray storage) {
        this(storage.getSize() / ELEMENT_SIZE, storage);
    }

    /**
     * Performs Dot-product.
     *
     * @return dot-product value
     */
    public static HalfFloat dot(VectorHalf a, VectorHalf b) {
        HalfFloat sum = new HalfFloat(0);
        for (int i = 0; i < a.size(); i++) {
            sum = HalfFloat.add(sum, HalfFloat.mult(a.get(i), b.get(i)));
        }
        return sum;
    }

    public HalfFloatArray getArray() {
        return storage;
    }

    /**
     * Returns the half float at the given index of this vector.
     *
     * @param index
     *     Position
     * @return value
     */
    public HalfFloat get(int index) {
        return storage.get(index);
    }

    /**
     * Sets the half float at the given index of this vector.
     *
     * @param index
     *     Position
     * @param value
     *     Half float value to be stored
     */
    public void set(int index, HalfFloat value) {
        storage.set(index, value);
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     *     {@code VectorHalf}
     */
    public void set(VectorHalf values) {
        for (int i = 0; i < values.storage.getSize(); i++) {
            storage.set(i, values.storage.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array.
     *
     * @param values
     *     Set input array as internal stored
     */
    public void set(HalfFloat[] values) {
        System.arraycopy(values, 0, storage, 0, values.length);
    }

    /**
     * Sets all elements to value.
     *
     * @param value
     *     Fill input array with value
     */
    public void fill(HalfFloat value) {
        storage.init(value);
    }

    /**
     * Returns slice of this vector.
     *
     * @param start
     *     starting index
     * @param length
     *     number of elements
     */
    public VectorHalf subVector(int start, int length) {
        final VectorHalf v = new VectorHalf(length);
        for (int i = 0; i < length; i++) {
            v.storage.set(i, storage.get(i + start));
        }
        return v;
    }

    /**
     * Duplicates this vector.
     *
     */
    public VectorHalf duplicate() {
        HalfFloatArray cp = new HalfFloatArray(storage.getSize());
        for (int i = 0; i < cp.getSize(); i++) {
            cp.set(i, storage.get(i));
        }
        return new VectorHalf(cp);
    }

    /**
     * Vector equality test.
     *
     * @param vector
     *     input vector
     * @return true if vectors match
     */
    public boolean isEqual(VectorHalf vector) {
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
        String str = String.format("VectorHalf <%d>", numElements);
        if (numElements < 32) {
            str += toString(FloatOps.FMT);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return ShortBuffer.wrap(storage.toShortArray());
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
