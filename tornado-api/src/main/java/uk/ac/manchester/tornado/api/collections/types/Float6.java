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

public final class Float6 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<Float6> TYPE = Float6.class;

    /**
     * backing array
     */
    @Payload
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 6;

    public Float6(float[] storage) {
        this.storage = storage;
    }

    public Float6() {
        this(new float[numElements]);
    }

    public Float6(float s0, float s1, float s2, float s3, float s4, float s5) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
    }

    public float[] getArray() {
        return storage;
    }

    public void set(Float6 value) {
        setS0(value.getS0());
        setS1(value.getS1());
        setS2(value.getS2());
        setS3(value.getS3());
        setS4(value.getS4());
        setS5(value.getS5());
    }

    public float get(int index) {
        return storage[index];
    }

    public void set(int index, float value) {
        storage[index] = value;
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

    public Float3 getHigh() {
        return Float3.loadFromArray(storage, 0);
    }

    public Float3 getLow() {
        return Float3.loadFromArray(storage, 3);
    }

    /**
     * Duplicates this vector
     *
     * @return {@link Float6}
     */
    public Float6 duplicate() {
        final Float6 vector = new Float6();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_6);
    }

    public static Float6 loadFromArray(final float[] array, int index) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, array[index + i]);
        }
        return result;
    }

    public final void storeToArray(final float[] array, int index) {
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
     * * Operations on Float6 vectors
     */
    /*
     * vector = op( vector, vector )
     */
    public static Float6 add(Float6 a, Float6 b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Float6 sub(Float6 a, Float6 b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Float6 div(Float6 a, Float6 b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Float6 mult(Float6 a, Float6 b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Float6 min(Float6 a, Float6 b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Float6 max(Float6 a, Float6 b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Float6 add(Float6 a, float b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Float6 sub(Float6 a, float b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Float6 mult(Float6 a, float b) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b);
        }
        return result;
    }

    public static Float6 inc(Float6 a, float value) {
        return add(a, value);
    }

    public static Float6 dec(Float6 a, float value) {
        return sub(a, value);
    }

    public static Float6 scale(Float6 a, float value) {
        return mult(a, value);
    }

    public static Float6 scaleByInverse(Float6 a, float value) {
        return mult(a, 1f / value);
    }

    /*
     * vector = op(vector)
     */
    public static Float6 sqrt(Float6 a) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static Float6 floor(Float6 a) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.floor(a.get(i)));
        }
        return result;
    }

    public static Float6 fract(Float6 a) {
        final Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.fract(a.get(i)));
        }
        return result;
    }

    public static Float6 clamp(Float6 a, float min, float max) {
        Float6 result = new Float6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.clamp(a.get(i), min, max));
        }
        return result;
    }

    /*
     * vector wide operations
     */
    public static float min(Float6 value) {
        float result = Float.MAX_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static float max(Float6 value) {
        float result = Float.MIN_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static float dot(Float6 a, Float6 b) {
        float result = 0f;
        final Float6 m = mult(a, b);
        for (int i = 0; i < numElements; i++) {
            result += m.get(i);
        }
        return result;
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return float
     */
    public static float length(Float6 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Float6 a, Float6 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
