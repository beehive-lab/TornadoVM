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
public final class Int16 implements TornadoVectorsInterface<IntBuffer> {

    public static final Class<Int16> TYPE = Int16.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 16;

    /**
     * backing array.
     */
    @Payload
    private final int[] storage;

    private Int16(int[] storage) {
        this.storage = storage;
    }

    public Int16() {
        this(new int[NUM_ELEMENTS]);
    }

    public Int16(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7, int s8, int s9, int s10, int s11, int s12, int s13, int s14, int s15) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
        setS6(s6);
        setS7(s7);
        setS8(s8);
        setS9(s9);
        setS10(s10);
        setS11(s11);
        setS12(s12);
        setS13(s13);
        setS14(s14);
        setS15(s15);
    }

    /**
     * * Operations on Int16 vectors.
     */
    public static Int16 add(Int16 a, Int16 b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Int16 add(Int16 a, int b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Int16 sub(Int16 a, Int16 b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Int16 sub(Int16 a, int b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Int16 div(Int16 a, Int16 b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Int16 div(Int16 a, int value) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Int16 mult(Int16 a, Int16 b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Int16 mult(Int16 a, int value) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Int16 min(Int16 a, Int16 b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int min(Int16 value) {
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Int16 max(Int16 a, Int16 b) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int max(Int16 value) {
        int result = Integer.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Int16 sqrt(Int16 a) {
        final Int16 result = new Int16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, (int) TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static int dot(Int16 a, Int16 b) {
        final Int16 m = Int16.mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() //
                + m.getS4() + m.getS5() + m.getS6() + m.getS7() //
                + m.getS8() + m.getS9() + m.getS10() + m.getS11() //
                + m.getS12() + m.getS13() + m.getS14() + m.getS15();
    }

    public static boolean isEqual(Int16 a, Int16 b) {
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

    public void set(Int16 value) {
        for (int i = 0; i < 16; i++) {
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

    public int getS8() {
        return get(8);
    }

    public void setS8(int value) {
        set(8, value);
    }

    public int getS9() {
        return get(9);
    }

    public void setS9(int value) {
        set(9, value);
    }

    public int getS10() {
        return get(10);
    }

    public void setS10(int value) {
        set(10, value);
    }

    public int getS11() {
        return get(11);
    }

    public void setS11(int value) {
        set(11, value);
    }

    public int getS12() {
        return get(12);
    }

    public void setS12(int value) {
        set(12, value);
    }

    public int getS13() {
        return get(13);
    }

    private void setS13(int value) {
        set(13, value);
    }

    public int getS14() {
        return get(14);
    }

    public void setS14(int value) {
        set(14, value);
    }

    public int getS15() {
        return get(15);
    }

    public void setS15(int value) {
        set(15, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Int16}
     */
    public Int16 duplicate() {
        Int16 vector = new Int16();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7(), getS8(), getS9(), getS10(), getS11(), getS12(), getS13(), getS13(), getS14(), getS15());
    }

    @Override
    public String toString() {
        return toString(IntOps.FMT_16);
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
