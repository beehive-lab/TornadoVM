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
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class Float16 implements TornadoVectorsInterface<FloatBuffer> {

    public static final Class<Float16> TYPE = Float16.class;
    private static final int NUM_ELEMENTS = 16;

    @Payload
    final float[] storage;

    private Float16(float[] storage) {
        this.storage = storage;
    }

    public Float16() {
        this(new float[NUM_ELEMENTS]);
    }

    public Float16(float s0, float s1, float s2, float s3, float s4, float s5, float s6, float s7, float s8, float s9, float s10, float s11, float s12, float s13, float s14, float s15) {
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

    public static Float16 add(Float16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float16 add(Int16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float16 add(Float16 a, Int16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float16 add(Float16 a, float b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Float16 sub(Float16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float16 sub(Int16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float16 sub(Float16 a, Int16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float16 sub(Float16 a, float b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Float16 div(Float16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float16 div(Int16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float16 div(Float16 a, Int16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float16 div(Float16 a, float value) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Float16 mult(Float16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float16 mult(Int16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float16 mult(Float16 a, Int16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float16 mult(Float16 a, float value) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Float16 min(Float16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float16 min(Int16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float16 min(Float16 a, Int16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float min(Float16 value) {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Float16 max(Float16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float16 max(Int16 a, Float16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float16 max(Float16 a, Int16 b) {
        final Float16 result = new Float16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float max(Float16 value) {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static float dot(Float16 a, Float16 b) {
        final Float16 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() //
                + m.getS4() + m.getS5() + m.getS6() + m.getS7() //
                + m.getS8() + m.getS9() + m.getS10() + m.getS11() //
                + m.getS12() + m.getS13() + m.getS14() + m.getS15();
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

    public float getS8() {
        return get(8);
    }

    public void setS8(float value) {
        set(8, value);
    }

    public float getS9() {
        return get(9);
    }

    public void setS9(float value) {
        set(9, value);
    }

    public float getS10() {
        return get(10);
    }

    public void setS10(float value) {
        set(10, value);
    }

    public float getS11() {
        return get(11);
    }

    public void setS11(float value) {
        set(11, value);
    }

    public float getS12() {
        return get(12);
    }

    public void setS12(float value) {
        set(12, value);
    }

    public float getS13() {
        return get(13);
    }

    private void setS13(float value) {
        set(13, value);
    }

    public float getS14() {
        return get(14);
    }

    public void setS14(float value) {
        set(14, value);
    }

    public float getS15() {
        return get(15);
    }

    public void setS15(float value) {
        set(15, value);
    }

    public float get(int index) {
        return storage[index];
    }

    public void set(int index, float value) {
        storage[index] = value;
    }

    public void set(Float16 value) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            set(i, value.get(i));
        }
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7(), getS8(), getS9(), getS10(), getS11(), getS12(), getS13(), getS13(), getS14(), getS15());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_16);
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

    public float[] getArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 4;
    }
}
