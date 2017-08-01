/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.collections.types;

import java.nio.ByteBuffer;

import static java.lang.String.format;
import static java.lang.System.out;
import static java.nio.ByteBuffer.wrap;
import static tornado.collections.types.Byte4.add;
import static tornado.collections.types.Byte4.loadFromArray;
import static tornado.collections.types.ByteOps.fmt4;

public class VectorByte8 implements PrimitiveStorage<ByteBuffer> {

    /**
     * backing array
     */
    final protected byte[] storage;

    /**
     * number of elements in the storage
     */
    final private int numElements;
    final private static int elementSize = 4;

    /**
     * Creates a vector using the provided backing array
     *
     * @param numElements
     * @param offset
     * @param step
     * @param elementSize
     * @param array
     */
    protected VectorByte8(int numElements, byte[] array) {
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
    public VectorByte8(byte[] array) {
        this(array.length / elementSize, array);
    }

    /**
     * Creates an empty vector with
     *
     * @param numElements
     */
    public VectorByte8(int numElements) {
        this(numElements, new byte[numElements * elementSize]);
    }

    private final int toIndex(int index) {
        return (index * elementSize);
    }

    /**
     * Returns the byte at the given index of this vector
     *
     * @param index
     *
     * @return value
     */
    public Byte4 get(int index) {
        return loadFromArray(storage, toIndex(index));
    }

    /**
     * Sets the byte at the given index of this vector
     *
     * @param index
     * @param value
     */
    public void set(int index, Byte4 value) {
        value.storeToArray(storage, toIndex(index));
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     *
     * @param values
     */
    public void set(VectorByte8 values) {
        for (int i = 0; i < numElements; i++) {
            set(i, values.get(i));
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     *
     * @param values
     */
    public void set(byte[] values) {
        VectorByte8 vector = new VectorByte8(values);
        for (int i = 0; i < numElements; i++) {
            set(i, vector.get(i));
        }
    }

    public void fill(byte value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public VectorByte8 duplicate() {
        VectorByte8 vector = new VectorByte8(numElements);
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
        out.printf("has %d elements\n", numElements);
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
            return format("VectorByte4 <%d>", numElements);
        } else {
            return toString(fmt4);
        }
    }

    public Byte4 sum() {
        Byte4 result = new Byte4();
        for (int i = 0; i < numElements; i++) {
            result = add(result, get(i));
        }
        return result;
    }

    public Byte4 min() {
        Byte4 result = new Byte4();
        for (int i = 0; i < numElements; i++) {
            result = Byte4.min(result, get(i));
        }
        return result;
    }

    public Byte4 max() {
        Byte4 result = new Byte4();
        for (int i = 0; i < numElements; i++) {
            result = Byte4.max(result, get(i));
        }
        return result;
    }

    @Override
    public void loadFromBuffer(ByteBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ByteBuffer asBuffer() {
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
