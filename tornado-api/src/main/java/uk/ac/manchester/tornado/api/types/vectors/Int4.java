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
public final class Int4 implements TornadoVectorsInterface<IntBuffer> {

    public static final Class<Int4> TYPE = Int4.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    private final int[] storage;

    private Int4(int[] storage) {
        this.storage = storage;
    }

    public Int4() {
        this(new int[NUM_ELEMENTS]);
    }

    public Int4(int x, int y, int z, int w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    /*
     * vector = op( vector, vector )
     */
    public static Int4 add(Int4 a, Int4 b) {
        return new Int4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Int4 sub(Int4 a, Int4 b) {
        return new Int4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Int4 div(Int4 a, Int4 b) {
        return new Int4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Int4 mult(Int4 a, Int4 b) {
        return new Int4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Int4 min(Int4 a, Int4 b) {
        return new Int4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Int4 max(Int4 a, Int4 b) {
        return new Int4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int4 add(Int4 a, int b) {
        return new Int4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static Int4 sub(Int4 a, int b) {
        return new Int4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static Int4 mult(Int4 a, int b) {
        return new Int4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static Int4 div(Int4 a, int b) {
        return new Int4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static Int4 inc(Int4 a, int value) {
        return add(a, value);
    }

    public static Int4 dec(Int4 a, int value) {
        return sub(a, value);
    }

    public static Int4 scale(Int4 a, int value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static Int4 clamp(Int4 x, int min, int max) {
        return new Int4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static int max(Int4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static boolean isEqual(Int4 a, Int4 b) {
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

    public void set(Int4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
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

    public int getW() {
        return get(3);
    }

    public void setW(int value) {
        set(3, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Int4}
     */
    public Int4 duplicate() {
        Int4 vector = new Int4();
        vector.set(this);
        return vector;
    }

    public Int2 asInt2() {
        return new Int2(getX(), getY());
    }

    public Int3 asInt3() {
        return new Int3(getX(), getY(), getZ());
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
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
