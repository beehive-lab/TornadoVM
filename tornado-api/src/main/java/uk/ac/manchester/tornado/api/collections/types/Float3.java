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
public final class Float3 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<Float3> TYPE = Float3.class;

    /**
     * backing array
     */
    @Payload
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 3;

    public Float3(float[] storage) {
        this.storage = storage;
    }

    public Float3() {
        this(new float[numElements]);
    }

    public Float3(float x, float y, float z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
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

    public void set(Float3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    public float getX() {
        return get(0);
    }

    public float getY() {
        return get(1);
    }

    public float getZ() {
        return get(2);
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

    public void setX(float value) {
        set(0, value);
    }

    public void setY(float value) {
        set(1, value);
    }

    public void setZ(float value) {
        set(2, value);
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

    /**
     * Duplicates this vector
     *
     * @return {@link Float3}
     */
    public Float3 duplicate() {
        final Float3 vector = new Float3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_3);
    }

    /**
     * Cast vector From Float3 into a Float2
     *
     * @return {@link Float2}
     */
    public Float2 asFloat2() {
        return new Float2(getX(), getY());
    }

    protected static Float3 loadFromArray(final float[] array, int index) {
        final Float3 result = new Float3();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        return result;
    }

    protected final void storeToArray(final float[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
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
     * * Operations on Float3 vectors
     */
    /*
     * vector = op( vector, vector )
     */
    public static Float3 add(Float3 a, Float3 b) {
        return new Float3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static Float3 sub(Float3 a, Float3 b) {
        return new Float3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static Float3 div(Float3 a, Float3 b) {
        return new Float3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static Float3 mult(Float3 a, Float3 b) {
        return new Float3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static Float3 min(Float3 a, Float3 b) {
        return new Float3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static Float3 max(Float3 a, Float3 b) {
        return new Float3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static Float3 cross(Float3 a, Float3 b) {
        return new Float3(a.getY() * b.getZ() - a.getZ() * b.getY(), a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Float3 add(Float3 a, float b) {
        return new Float3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static Float3 sub(Float3 a, float b) {
        return new Float3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static Float3 mult(Float3 a, float b) {
        return new Float3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static Float3 div(Float3 a, float b) {
        return new Float3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static Float3 inc(Float3 a, float value) {
        return new Float3(a.getX() + value, a.getY() + value, a.getZ() + value);
    }

    public static Float3 dec(Float3 a, float value) {
        return new Float3(a.getX() - value, a.getY() - value, a.getZ() - value);
    }

    public static Float3 scaleByInverse(Float3 a, float value) {
        return mult(a, 1f / value);
    }

    public static Float3 scale(Float3 a, float value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static Float3 sqrt(Float3 a) {
        return new Float3(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()));
    }

    public static Float3 floor(Float3 a) {
        return new Float3(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()));
    }

    public static Float3 fract(Float3 a) {
        return new Float3(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()));
    }

    /*
     * misc inplace vector ops
     */
    public static Float3 clamp(Float3 x, float min, float max) {
        return new Float3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    public static Float3 normalise(Float3 value) {
        final float len = 1f / length(value);
        return mult(value, len);
    }

    /*
     * vector wide operations
     */
    public static float min(Float3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static float max(Float3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static float dot(Float3 a, Float3 b) {
        final Float3 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return float
     */
    public static float length(Float3 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Float3 a, Float3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static boolean isEqualULP(Float3 a, Float3 b, float numULP) {
        return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
    }

    public static float findULPDistance(Float3 a, Float3 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    }
}
