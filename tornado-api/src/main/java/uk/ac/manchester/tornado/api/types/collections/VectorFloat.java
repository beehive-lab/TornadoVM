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
import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

public final class VectorFloat implements TornadoCollectionInterface<FloatBuffer> {

    private static final int ELEMENT_SIZE = 1;
    private final int numElements;
    private final FloatArray storage;

    public VectorFloat(int numElements, FloatArray array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with.
     *
     * @param numElements
     *     Number of elements
     */
    public VectorFloat(int numElements) {
        this(numElements, new FloatArray(numElements));
    }

    /**
     * Creates a new vector from the provided storage.
     *
     * @param storage
     *     Array to be stored
     */
    public VectorFloat(FloatArray storage) {
        this(storage.getSize() / ELEMENT_SIZE, storage);
    }

    public static float min(VectorFloat v) {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < v.storage.getSize(); i++) {
            result = Math.min(v.storage.get(i), result);
        }
        return result;
    }

    public static float max(VectorFloat v) {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < v.storage.getSize(); i++) {
            result = Math.max(v.storage.get(i), result);
        }
        return result;
    }

    /**
     * Performs Dot-product.
     *
     * @return dot-product value
     */
    public static float dot(VectorFloat a, VectorFloat b) {
        float sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += a.get(i) * b.get(i);
        }
        return sum;
    }

    public FloatArray getArray() {
        return storage;
    }

    /**
     * Returns the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @return value
     */
    public float get(int index) {
        return storage.get(index);
    }

    /**
     * Sets the float at the given index of this vector.
     *
     * @param index
     *     Position
     * @param value
     *     Float value to be stored
     */
    public void set(int index, float value) {
        storage.set(index, value);
    }

    /**
     * Sets the elements of this vector to that of the provided vector.
     *
     * @param values
     *     VectorFloat4
     */
    public void set(VectorFloat values) {
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
    public void set(float[] values) {
        System.arraycopy(values, 0, storage, 0, values.length);
    }

    /**
     * Sets all elements to value.
     *
     * @param value
     *     Fill input array with value
     */
    public void fill(float value) {
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
    public VectorFloat subVector(int start, int length) {
        final VectorFloat v = new VectorFloat(length);
        for (int i = 0; i < length; i++) {
            v.storage.set(i, storage.get(i + start));
        }
        return v;
    }

    /**
     * Duplicates this vector.
     *
     */
    public VectorFloat duplicate() {
        FloatArray cp = new FloatArray(storage.getSize());
        for (int i = 0; i < cp.getSize(); i++) {
            cp.set(i, storage.get(i));
        }
        return new VectorFloat(cp);
    }

    /**
     * Vector equality test.
     *
     * @param vector
     *     input vector
     * @return true if vectors match
     */
    public boolean isEqual(VectorFloat vector) {
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
        String str = String.format("VectorFloat <%d>", numElements);
        if (numElements < 32) {
            str += toString(FloatOps.FMT);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage.toHeapArray());
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
