/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.types;

import java.nio.FloatBuffer;

import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static uk.ac.manchester.tornado.collections.types.Float3.add;
import static uk.ac.manchester.tornado.collections.types.Float3.loadFromArray;
import static uk.ac.manchester.tornado.collections.types.FloatOps.fmt3;

public class VectorFloat3 implements PrimitiveStorage<FloatBuffer> {

    /**
     * backing array
     */
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 3;

    /**
     * Creates a vector using the provided backing array
     *
     * @param numElements
     * @param offset
     * @param step
     * @param elementSize
     * @param array
     */
    protected VectorFloat3(int numElements, float[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates a vector using the provided backing array
     *
     * @param numElements
     * @param offset
     * @param step
     * @param storage
     */
    public VectorFloat3(float[] array) {
        this(array.length / elementSize, array);
    }

    /**
     * Creates an empty vector with
     *
     * @param numElements
     */
    public VectorFloat3(int numElements) {
        this(numElements, new float[numElements * elementSize]);
    }

    private final int toIndex(int index) {
        return (index * elementSize);
    }

    /**
     * Returns the float at the given index of this vector
     *
     * @param index
     *
     * @return value
     */
    public Float3 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    /**
     * Sets the float at the given index of this vector
     *
     * @param index
     * @param value
     */
    public void set(int index, Float3 value) {
        value.storeToArray(storage, toIndex(index));
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     *
     * @param values
     */
    public void set(VectorFloat3 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     *
     * @param values
     */
    public void set(float[] values) {
        VectorFloat3 vector = new VectorFloat3(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public VectorFloat3 duplicate() {
        VectorFloat3 vector = new VectorFloat3(numElements);
        vector.set(this);
        return vector;
    }

    /**
     * Prints the vector using the specified format string
     *
     * @param fmt
     *
     * @return
     */
    public String toString(String fmt) {
        String str = "";

        for (int i = 0; i < numElements; i++) {
            str += get(i).toString() + " ";
        }

        return str;
    }

    /**
     *
     */
    public String toString() {
        if (numElements > 4) {
            return format("VectorFloat3 <%d>", numElements);
        } else {
            return toString(fmt3);
        }
    }

    public Float3 sum() {
        Float3 result = new Float3();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Float3 min() {
        Float3 result = new Float3();
        for (int i = 0; i < numElements; i++) {
            result = Float3.min(result, get(i));
        }
        return result;
    }

    public Float3 max() {
        Float3 result = new Float3();
        for (int i = 0; i < numElements; i++) {
            result = Float3.max(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return storage.length;
    }

    public int getLength() {
        return numElements;
    }

}
