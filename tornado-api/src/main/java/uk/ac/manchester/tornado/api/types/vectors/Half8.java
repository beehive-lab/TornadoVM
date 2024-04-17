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
public final class Half8 implements TornadoVectorsInterface<ShortBuffer> {

    public static final Class<Half8> TYPE = Half8.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;
    /**
     * backing array.
     */
    @Payload
    short[] storage = new short[8];

    private Half8(HalfFloat[] storage) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            this.storage[i] = storage[i].getHalfFloatValue();
        }
    }

    public Half8() {
        this.storage = new short[NUM_ELEMENTS];
    }

    public Half8(HalfFloat s0, HalfFloat s1, HalfFloat s2, HalfFloat s3, HalfFloat s4, HalfFloat s5, HalfFloat s6, HalfFloat s7) {
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
     * * Operations on Half8 vectors.
     */
    public static Half8 add(Half8 a, Half8 b) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.add(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half8 add(Half8 a, HalfFloat b) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.add(a.get(i), b));
        }
        return result;
    }

    public static Half8 sub(Half8 a, Half8 b) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.sub(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half8 sub(Half8 a, HalfFloat b) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.sub(a.get(i), b));
        }
        return result;
    }

    public static Half8 div(Half8 a, Half8 b) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.div(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half8 div(Half8 a, HalfFloat value) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.div(a.get(i), value));
        }
        return result;
    }

    public static Half8 mult(Half8 a, Half8 b) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.mult(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Half8 mult(Half8 a, HalfFloat value) {
        final Half8 result = new Half8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, HalfFloat.mult(a.get(i), value));
        }
        return result;
    }

    public static HalfFloat dot(Half8 a, Half8 b) {
        final Half8 m = mult(a, b);
        return HalfFloat.add(HalfFloat.add(HalfFloat.add(m.getS0(), m.getS1()), HalfFloat.add(m.getS2(), m.getS3())), HalfFloat.add(HalfFloat.add(m.getS4(), m.getS5()), HalfFloat.add(m.getS6(), m
                .getS7())));
    }

    public short[] getArray() {
        return storage;
    }

    public HalfFloat get(int index) {
        return new HalfFloat(storage[index]);
    }

    public void set(int index, HalfFloat value) {
        storage[index] = value.getHalfFloatValue();
    }

    public void set(Half8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
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

    public Half4 getHigh() {
        return new Half4(getS4(), getS5(), getS6(), getS7());
    }

    public Half4 getLow() {
        return new Half4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Float8}
     */
    public Half8 duplicate() {
        Half8 vector = new Half8();
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

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 2;
    }

}
