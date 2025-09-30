/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

import java.nio.ShortBuffer;

@Vector
public final class Half16 implements TornadoVectorsInterface<ShortBuffer> {

    public static final Class<Half16> TYPE = Half16.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 16;
    /**
     * backing array.
     */
    @Payload
    short[] storage = new short[NUM_ELEMENTS];

    private Half16(HalfFloat[] storage) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            this.storage[i] = storage[i].getHalfFloatValue();
        }
    }

    public Half16() {
        this.storage = new short[NUM_ELEMENTS];
    }

    public Half16(HalfFloat s0, HalfFloat s1, HalfFloat s2, HalfFloat s3, HalfFloat s4, HalfFloat s5, HalfFloat s6, HalfFloat s7, HalfFloat s8, HalfFloat s9, HalfFloat s10, HalfFloat s11,
            HalfFloat s12, HalfFloat s13, HalfFloat s14, HalfFloat s15) {
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
     * * Operations on {@code Half16} vectors.
     */
    public static Half16 add(Half16 a, Half16 b) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.add(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half16 add(Half16 a, HalfFloat b) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.add(a.get(i), b));
        }
        return result;
    }

    public static Half16 sub(Half16 a, Half16 b) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.sub(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half16 sub(Half16 a, HalfFloat b) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.sub(a.get(i), b));
        }
        return result;
    }

    public static Half16 div(Half16 a, Half16 b) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.div(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half16 div(Half16 a, HalfFloat value) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.div(a.get(i), value));
        }
        return result;
    }

    public static Half16 mult(Half16 a, Half16 b) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.mult(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half16 mult(Half16 a, HalfFloat value) {
        final Half16 result = new Half16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.mult(a.get(i), value));
        }
        return result;
    }

    public static HalfFloat dot(Half16 a, Half16 b) {
        final Half16 m = mult(a, b);
        return HalfFloat.add(HalfFloat.add(HalfFloat.add(HalfFloat.add(m.getS0(), m.getS1()), HalfFloat.add(m.getS2(), m.getS3())), HalfFloat.add(HalfFloat.add(m.getS4(), m.getS5()), HalfFloat.add(m
                .getS6(), m.getS7()))), HalfFloat.add(HalfFloat.add(HalfFloat.add(m.getS8(), m.getS9()), HalfFloat.add(m.getS10(), m.getS11())), HalfFloat.add(HalfFloat.add(m.getS12(), m.getS13()),
                        HalfFloat.add(m.getS14(), m.getS15()))));
    }

    public HalfFloat get(int index) {
        return new HalfFloat(storage[index]);
    }

    public void set(int index, HalfFloat value) {
        storage[index] = value.getHalfFloatValue();
    }

    public HalfFloat getS0() {
        return get(0);
    }

    public void setS0(HalfFloat value) {
        set(0, value);
    }

    public HalfFloat getS1() {
        return get(1);
    }

    public void setS1(HalfFloat value) {
        set(1, value);
    }

    public HalfFloat getS2() {
        return get(2);
    }

    public void setS2(HalfFloat value) {
        set(2, value);
    }

    public HalfFloat getS3() {
        return get(3);
    }

    public void setS3(HalfFloat value) {
        set(3, value);
    }

    public HalfFloat getS4() {
        return get(4);
    }

    public void setS4(HalfFloat value) {
        set(4, value);
    }

    public HalfFloat getS5() {
        return get(5);
    }

    public void setS5(HalfFloat value) {
        set(5, value);
    }

    public HalfFloat getS6() {
        return get(6);
    }

    public void setS6(HalfFloat value) {
        set(6, value);
    }

    public HalfFloat getS7() {
        return get(7);
    }

    public void setS7(HalfFloat value) {
        set(7, value);
    }

    public HalfFloat getS8() {
        return get(8);
    }

    public void setS8(HalfFloat value) {
        set(8, value);
    }

    public HalfFloat getS9() {
        return get(9);
    }

    public void setS9(HalfFloat value) {
        set(9, value);
    }

    public HalfFloat getS10() {
        return get(10);
    }

    public void setS10(HalfFloat value) {
        set(10, value);
    }

    public HalfFloat getS11() {
        return get(11);
    }

    public void setS11(HalfFloat value) {
        set(11, value);
    }

    public HalfFloat getS12() {
        return get(12);
    }

    public void setS12(HalfFloat value) {
        set(12, value);
    }

    public HalfFloat getS13() {
        return get(13);
    }

    public void setS13(HalfFloat value) {
        set(13, value);
    }

    public HalfFloat getS14() {
        return get(14);
    }

    public void setS14(HalfFloat value) {
        set(14, value);
    }

    public HalfFloat getS15() {
        return get(15);
    }

    public void setS15(HalfFloat value) {
        set(15, value);
    }

    public void set(Half16 value) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            set(i, value.get(i));
        }
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0().getFloat32(), getS1().getFloat32(), getS2().getFloat32(), getS3().getFloat32(), getS4().getFloat32(), getS5().getFloat32(), getS6().getFloat32(), getS7()
                .getFloat32(), getS8().getFloat32(), getS9().getFloat32(), getS10().getFloat32(), getS11().getFloat32(), getS12().getFloat32(), getS13().getFloat32(), getS13().getFloat32(), getS14()
                        .getFloat32(), getS15().getFloat32());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_16);
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    public ShortBuffer asBuffer() {
        return ShortBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public short[] toArray() {
        return storage;
    }

    public short[] getArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 2;
    }

}
