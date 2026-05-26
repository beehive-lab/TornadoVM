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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.IntOps;

@Vector
public final class Int8 implements TornadoVectorsInterface<IntBuffer> {

    public static final Class<Int8> TYPE = Int8.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;

    /**
     * backing array.
     */
    @Payload
    private final int[] storage;

    private Int8(int[] storage) {
        this.storage = storage;
    }

    public Int8() {
        this(new int[NUM_ELEMENTS]);
    }

    public Int8(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
        setS6(s6);
        setS7(s7);
    }

    /**
     * * Operations on int8 vectors.
     */
    public static Int8 add(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Int8 add(Int8 a, int b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Int8 sub(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Int8 sub(Int8 a, int b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Int8 div(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Int8 div(Int8 a, int value) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Int8 mult(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Int8 mult(Int8 a, int value) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Int8 min(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int min(Int8 value) {
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Int8 max(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int max(Int8 value) {
        int result = Integer.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Int8 sqrt(Int8 a) {
        final Int8 result = new Int8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, (int) TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static int dot(Int8 a, Int8 b) {
        final Int8 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() + m.getS4() + m.getS5() + m.getS6() + m.getS7();
    }

    public static boolean isEqual(Int8 a, Int8 b) {
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

    public void set(Int8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
    }

    public int getS0() {
        return get(0);
    }

    public void setS0(int value) {
        set(0, value);
    }

    public int getS1() {
        return get(1);
    }

    public void setS1(int value) {
        set(1, value);
    }

    public int getS2() {
        return get(2);
    }

    public void setS2(int value) {
        set(2, value);
    }

    public int getS3() {
        return get(3);
    }

    public void setS3(int value) {
        set(3, value);
    }

    public int getS4() {
        return get(4);
    }

    public void setS4(int value) {
        set(4, value);
    }

    public int getS5() {
        return get(5);
    }

    public void setS5(int value) {
        set(5, value);
    }

    public int getS6() {
        return get(6);
    }

    public void setS6(int value) {
        set(6, value);
    }

    public int getS7() {
        return get(7);
    }

    public void setS7(int value) {
        set(7, value);
    }

    public Int4 getHigh() {
        return new Int4(getS4(), getS5(), getS6(), getS7());
    }

    public Int4 getLow() {
        return new Int4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Int8}
     */
    public Int8 duplicate() {
        Int8 vector = new Int8();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7());
    }

    @Override
    public String toString() {
        return toString(IntOps.FMT_8);
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        // TODO document why this method is empty
        throw new TornadoRuntimeException("Not implemented");
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
