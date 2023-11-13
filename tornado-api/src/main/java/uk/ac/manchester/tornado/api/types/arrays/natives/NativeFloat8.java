/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.arrays.natives;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class NativeFloat8 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<NativeFloat8> TYPE = NativeFloat8.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorFloat nativeVectorFloat;

    public NativeFloat8(NativeVectorFloat storage) {
        this.nativeVectorFloat = storage;
    }

    public NativeFloat8() {
        this(new NativeVectorFloat(NUM_ELEMENTS));
    }

    public NativeFloat8(float s0, float s1, float s2, float s3, float s4, float s5, float s6, float s7) {
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
     * * Operations on Float8 vectors.
     */
    public static NativeFloat8 add(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static NativeFloat8 add(NativeFloat8 a, float b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static NativeFloat8 sub(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static NativeFloat8 sub(NativeFloat8 a, float b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static NativeFloat8 div(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static NativeFloat8 div(NativeFloat8 a, float value) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static NativeFloat8 mult(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static NativeFloat8 mult(NativeFloat8 a, float value) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static NativeFloat8 min(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float min(NativeFloat8 value) {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static NativeFloat8 max(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float max(NativeFloat8 value) {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static NativeFloat8 sqrt(NativeFloat8 a) {
        final NativeFloat8 result = new NativeFloat8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static float dot(NativeFloat8 a, NativeFloat8 b) {
        final NativeFloat8 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() + m.getS4() + m.getS5() + m.getS6() + m.getS7();
    }

    public static boolean isEqual(NativeFloat8 a, NativeFloat8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static float findULPDistance(NativeFloat8 value, NativeFloat8 expected) {
        return TornadoMath.findULPDistance(value.asBuffer().array(), expected.asBuffer().array());
    }

    public NativeVectorFloat getArray() {
        return nativeVectorFloat;
    }

    public float get(int index) {
        return nativeVectorFloat.get(index);
    }

    public void set(int index, float value) {
        nativeVectorFloat.set(index, value);
    }

    public void set(NativeFloat8 value) {
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
     * @return {@link NativeFloat8}
     */
    public NativeFloat8 duplicate() {
        NativeFloat8 vector = new NativeFloat8();
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
        return nativeVectorFloat.getSegment().asByteBuffer().asFloatBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public float[] toArray() {
        return nativeVectorFloat.getSegment().toArray(JAVA_FLOAT);
    }

    static NativeFloat8 loadFromArray(final FloatArray array, int index) {
        final NativeFloat8 result = new NativeFloat8();
        result.setS0(array.get(index));
        result.setS1(array.get(index + 1));
        result.setS2(array.get(index + 2));
        result.setS3(array.get(index + 3));
        result.setS4(array.get(index + 4));
        result.setS5(array.get(index + 5));
        result.setS6(array.get(index + 6));
        result.setS7(array.get(index + 7));
        return result;
    }

    void storeToArray(final FloatArray array, int index) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            array.set(index + i, get(i));
        }
    }
}
