/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.vectors;

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;

@Vector
public final class Int3 implements TornadoVectorsInterface<IntBuffer> {

    public static final Class<Int3> TYPE = Int3.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d }";

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;

    /**
     * backing array.
     */
    @Payload
    private final int[] storage;

    private Int3(int[] storage) {
        this.storage = storage;
    }

    public Int3() {
        this(new int[NUM_ELEMENTS]);
    }

    public Int3(int x, int y, int z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /*
     * vector = op( vector, vector )
     */
    public static Int3 add(Int3 a, Int3 b) {
        return new Int3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static Int3 sub(Int3 a, Int3 b) {
        return new Int3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static Int3 div(Int3 a, Int3 b) {
        return new Int3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static Int3 mult(Int3 a, Int3 b) {
        return new Int3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static Int3 min(Int3 a, Int3 b) {
        return new Int3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static Int3 max(Int3 a, Int3 b) {
        return new Int3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int3 add(Int3 a, int b) {
        return new Int3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static Int3 sub(Int3 a, int b) {
        return new Int3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static Int3 mult(Int3 a, int b) {
        return new Int3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static Int3 div(Int3 a, int b) {
        return new Int3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static Int3 inc(Int3 a, int value) {
        return add(a, value);
    }

    public static Int3 dec(Int3 a, int value) {
        return sub(a, value);
    }

    public static Int3 scale(Int3 a, int value) {
        return mult(a, value);
    }

    public static Int3 clamp(Int3 x, int min, int max) {
        return new Int3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static int max(Int3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(Int3 a, Int3 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public int[] toArray() {
        return storage;
    }

    public int[] getArray() {
        return storage;
    }

    public int get(int index) {
        return storage[index];
    }

    public void set(int index, int value) {
        storage[index] = value;
    }

    public void set(Int3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    public int getX() {
        return get(0);
    }

    public void setX(int value) {
        set(0, value);
    }

    public int getY() {
        return get(1);
    }

    public void setY(int value) {
        set(1, value);
    }

    public int getZ() {
        return get(2);
    }

    public void setZ(int value) {
        set(2, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Int3}
     */
    public Int3 duplicate() {
        Int3 vector = new Int3();
        vector.set(this);
        return vector;
    }

    public Int2 asInt2() {
        return new Int2(getX(), getY());
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(NUMBER_FORMAT);
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 4;
    }

}
