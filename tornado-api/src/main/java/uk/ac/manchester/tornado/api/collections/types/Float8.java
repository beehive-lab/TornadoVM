/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

@Vector
public final class Float8 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<Float8> TYPE = Float8.class;

    /**
     * backing array
     */
    @Payload
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 8;

    public Float8(float[] storage) {
        this.storage = storage;
    }

    public Float8() {
        this(new float[numElements]);
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

    public float getS1() {
        return get(1);
    }

    public float getS2() {
        return get(2);
    }

    public float getS3() {
        return get(3);
    }

    public float getS4() {
        return get(4);
    }

    public float getS5() {
        return get(5);
    }

    public float getS6() {
        return get(6);
    }

    public float getS7() {
        return get(7);
    }

    public void setS0(float value) {
        set(0, value);
    }

    public void setS1(float value) {
        set(1, value);
    }

    public void setS2(float value) {
        set(2, value);
    }

    public void setS3(float value) {
        set(3, value);
    }

    public void setS4(float value) {
        set(4, value);
    }

    public void setS5(float value) {
        set(5, value);
    }

    public void setS6(float value) {
        set(6, value);
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
     * Duplicates this vector
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

    protected static Float8 loadFromArray(final float[] array, int index) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, array[index + i]);
        }
        return result;
    }

    protected final void storeToArray(final float[] array, int index) {
        for (int i = 0; i < numElements; i++) {
            array[index + i] = get(i);
        }
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
        return numElements;
    }

    /**
     * * Operations on Float8 vectors
     */
    public static Float8 add(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float8 add(Float8 a, float b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Float8 sub(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float8 sub(Float8 a, float b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Float8 div(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float8 div(Float8 a, float value) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Float8 mult(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float8 mult(Float8 a, float value) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Float8 min(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float min(Float8 value) {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Float8 max(Float8 a, Float8 b) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static float max(Float8 value) {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Float8 sqrt(Float8 a) {
        final Float8 result = new Float8();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static float dot(Float8 a, Float8 b) {
        final Float8 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() + m.getS4() + m.getS5() + m.getS6() + m.getS7();
    }

    public static boolean isEqual(Float8 a, Float8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static float findULPDistance(Float8 value, Float8 expected) {
        return TornadoMath.findULPDistance(value.asBuffer().array(), expected.asBuffer().array());
    }

}
