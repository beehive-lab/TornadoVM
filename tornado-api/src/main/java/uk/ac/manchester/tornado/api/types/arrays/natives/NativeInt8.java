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
package uk.ac.manchester.tornado.api.types.arrays.natives;

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.utils.IntOps;
import uk.ac.manchester.tornado.api.types.vectors.Int4;

@Vector
public final class NativeInt8 implements TornadoNativeCollectionsInterface<IntBuffer> {
    public static final Class<NativeInt8> TYPE = NativeInt8.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;

    /**
     * backing array.
     */
    @Payload
    private final NativeVectorInt nativeVectorInt;

    public NativeInt8(NativeVectorInt storage) {
        this.nativeVectorInt = storage;
    }

    public NativeInt8() {
        this(new NativeVectorInt(NUM_ELEMENTS));

    }

    public NativeInt8(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7) {
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
     * * Operations on NativeInt8 vectors.
     */
    public static NativeInt8 add(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static NativeInt8 add(NativeInt8 a, int b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static NativeInt8 sub(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static NativeInt8 sub(NativeInt8 a, int b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static NativeInt8 div(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static NativeInt8 div(NativeInt8 a, int value) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static NativeInt8 mult(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static NativeInt8 mult(NativeInt8 a, int value) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static NativeInt8 min(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int min(NativeInt8 value) {
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static NativeInt8 max(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int max(NativeInt8 value) {
        int result = Integer.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static NativeInt8 sqrt(NativeInt8 a) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, (int) TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static int dot(NativeInt8 a, NativeInt8 b) {
        final NativeInt8 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() + m.getS4() + m.getS5() + m.getS6() + m.getS7();
    }

    public static boolean isEqual(NativeInt8 a, NativeInt8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    static NativeInt8 loadFromArray(final IntArray array, int index) {
        final NativeInt8 result = new NativeInt8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, array.get(index + i));
        }
        return result;
    }

    public int get(int index) {
        return nativeVectorInt.get(index);
    }

    public void set(int index, int value) {
        nativeVectorInt.set(index, value);
    }

    public void set(NativeInt8 value) {
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
     * @return {@link NativeInt8}
     */
    public NativeInt8 duplicate() {
        NativeInt8 vector = new NativeInt8();
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
        return nativeVectorInt.getSegment().asByteBuffer().asIntBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    void storeToArray(final IntArray array, int index) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            array.set(index + i, get(i));
        }
    }

    public void clear() {
        nativeVectorInt.clear();
    }
}
