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

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class Float8 implements TornadoVectorsInterface<FloatBuffer> {

    public static final Class<Float8> TYPE = Float8.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;
    /**
     * backing array.
     */
    @Payload
    final float[] storage;

    private Float8(float[] storage) {
        this.storage = storage;
    }

    public Float8() {
        this(new float[NUM_ELEMENTS]);
    }

    public Float8(float s0, float s1, float s2, float s3, float s4, float s5, float s6, float s7) {
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

    public static Float8 add(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float8 add(Int8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float8 add(Float8 a, Int8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float8 add(Float8 a, float b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Float8 sub(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float8 sub(Int8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float8 sub(Float8 a, Int8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float8 sub(Float8 a, float b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Float8 div(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float8 div(Int8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float8 div(Float8 a, Int8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float8 div(Float8 a, float value) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Float8 mult(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float8 mult(Int8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float8 mult(Float8 a, Int8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float8 mult(Float8 a, float value) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Float8 min(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float8 min(Int8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float8 min(Float8 a, Int8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float min(Float8 value) {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Float8 max(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float8 max(Int8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float8 max(Float8 a, Int8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float max(Float8 value) {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Float8 sqrt(Float8 a) {
        final Float8 result = new Float8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static float dot(Float8 a, Float8 b) {
        final Float8 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() + m.getS4() + m.getS5() + m.getS6() + m.getS7();
    }

    public static boolean isEqual(Float8 a, Float8 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public static float findULPDistance(Float8 value, Float8 expected) {
        return TornadoMath.findULPDistance(value.asBuffer().array(), expected.asBuffer().array());
    }

    public float[] getArray() {
        return storage;
    }

    public float get(int index) {
        return storage[index];
    }

    public void set(int index, float value) {
        storage[index] = value;
    }

    public void set(Float8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
    }

    public float getS0() {
        return get(0);
    }

    public void setS0(float value) {
        set(0, value);
    }

    public float getS1() {
        return get(1);
    }

    public void setS1(float value) {
        set(1, value);
    }

    public float getS2() {
        return get(2);
    }

    public void setS2(float value) {
        set(2, value);
    }

    public float getS3() {
        return get(3);
    }

    public void setS3(float value) {
        set(3, value);
    }

    public float getS4() {
        return get(4);
    }

    public void setS4(float value) {
        set(4, value);
    }

    public float getS5() {
        return get(5);
    }

    public void setS5(float value) {
        set(5, value);
    }

    public float getS6() {
        return get(6);
    }

    public void setS6(float value) {
        set(6, value);
    }

    public float getS7() {
        return get(7);
    }

    public void setS7(float value) {
        set(7, value);
    }

    public Float4 getHigh() {
        return new Float4(getS4(), getS5(), getS6(), getS7());
    }

    public Float4 getLow() {
        return new Float4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Float8}
     */
    public Float8 duplicate() {
        Float8 vector = new Float8();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_8);
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return FloatBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public float[] toArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 4;
    }

}
