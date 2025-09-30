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
public final class Int2 implements TornadoVectorsInterface<IntBuffer> {

    public static final Class<Int2> TYPE = Int2.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    private final int[] storage;

    private Int2(int[] storage) {
        this.storage = storage;
    }

    public Int2() {
        this(new int[NUM_ELEMENTS]);
    }

    public Int2(int x, int y) {
        this();
        setX(x);
        setY(y);
    }

    /**
     * * Operations on Int2 vectors.
     */
    /*
     * vector = op( vector, vector )
     */
    public static Int2 add(Int2 a, Int2 b) {
        return new Int2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Int2 sub(Int2 a, Int2 b) {
        return new Int2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Int2 div(Int2 a, Int2 b) {
        return new Int2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Int2 mult(Int2 a, Int2 b) {
        return new Int2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Int2 min(Int2 a, Int2 b) {
        return new Int2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Int2 max(Int2 a, Int2 b) {
        return new Int2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int2 add(Int2 a, int b) {
        return new Int2(a.getX() + b, a.getY() + b);
    }

    public static Int2 sub(Int2 a, int b) {
        return new Int2(a.getX() - b, a.getY() - b);
    }

    public static Int2 mult(Int2 a, int b) {
        return new Int2(a.getX() * b, a.getY() * b);
    }

    public static Int2 div(Int2 a, int b) {
        return new Int2(a.getX() / b, a.getY() / b);
    }

    public static Int2 inc(Int2 a, int value) {
        return add(a, value);
    }

    public static Int2 dec(Int2 a, int value) {
        return sub(a, value);
    }

    public static Int2 scaleByInverse(Int2 a, int value) {
        return mult(a, 1 / value);
    }

    public static Int2 scale(Int2 a, int value) {
        return mult(a, value);
    }

    public static Int2 clamp(Int2 x, int min, int max) {
        return new Int2(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static int max(Int2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static int dot(Int2 a, Int2 b) {
        final Int2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    public static boolean isEqual(Int2 a, Int2 b) {
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

    public void set(Int2 value) {
        setX(value.getX());
        setY(value.getY());
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

    public int getS0() {
        return get(0);
    }

    public int getS1() {
        return get(1);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Int2}
     */
    public Int2 duplicate() {
        Int2 vector = new Int2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
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
