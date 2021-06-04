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
public final class Float4 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<Float4> TYPE = Float4.class;

    /**
     * backing array
     */
    @Payload
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 4;

    public Float4(float[] storage) {
        this.storage = storage;
    }

    public Float4() {
        this(new float[numElements]);
    }

    public float get(int index) {
        return storage[index];
    }

    public void set(int index, float value) {
        storage[index] = value;
    }

    public Float4(float x, float y, float z, float w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    public float[] getArray() {
        return storage;
    }

    public void set(Float4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
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

    public float getW() {
        return get(3);
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

    public void setW(float value) {
        set(3, value);
    }

    /**
     * Duplicates this vector
     *
     * @return {@link Float4}
     */
    public Float4 duplicate() {
        final Float4 vector = new Float4();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_4);
    }

    /**
     * Cast vector into a Float2
     *
     * @return {@link Float2}
     */
    public Float2 asFloat2() {
        return new Float2(getX(), getY());
    }

    public Float3 asFloat3() {
        return new Float3(getX(), getY(), getZ());
    }

    public Float2 getLow() {
        return asFloat2();
    }

    public Float2 getHigh() {
        return new Float2(getZ(), getW());
    }

    protected static Float4 loadFromArray(final float[] array, int index) {
        final Float4 result = new Float4();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        result.setW(array[index + 3]);
        return result;
    }

    protected final void storeToArray(final float[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
        array[index + 3] = getW();
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

    public void fill(float value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }

    }

    // ===================================
    // Operations on Float4 vectors
    // vector = op( vector, vector )
    // ===================================

    public static Float4 add(Float4 a, Float4 b) {
        return new Float4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Float4 sub(Float4 a, Float4 b) {
        return new Float4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Float4 div(Float4 a, Float4 b) {
        return new Float4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Float4 mult(Float4 a, Float4 b) {
        return new Float4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Float4 min(Float4 a, Float4 b) {
        return new Float4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Float4 max(Float4 a, Float4 b) {
        return new Float4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Float4 add(Float4 a, float b) {
        return new Float4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static Float4 sub(Float4 a, float b) {
        return new Float4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static Float4 mult(Float4 a, float b) {
        return new Float4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static Float4 div(Float4 a, float b) {
        return new Float4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static Float4 inc(Float4 a, float value) {
        return add(a, value);
    }

    public static Float4 dec(Float4 a, float value) {
        return sub(a, value);
    }

    public static Float4 scaleByInverse(Float4 a, float value) {
        return mult(a, 1f / value);
    }

    public static Float4 scale(Float4 a, float value) {
        return mult(a, value);
    }

    public static float sum(Float4 a) {
        return a.getX() + a.getY() + a.getZ() + a.getW();
    }

    /*
     * vector = op(vector)
     */
    public static Float4 sqrt(Float4 a) {
        return new Float4(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()), TornadoMath.sqrt(a.getW()));
    }

    public static Float4 floor(Float4 a) {
        return new Float4(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()), TornadoMath.floor(a.getW()));
    }

    public static Float4 fract(Float4 a) {
        return new Float4(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()), TornadoMath.fract(a.getW()));
    }

    /*
     * misc inplace vector ops
     */
    public static Float4 clamp(Float4 x, float min, float max) {
        return new Float4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    public static Float4 normalise(Float4 value) {
        return scaleByInverse(value, length(value));
    }

    /*
     * vector wide operations
     */
    public static float min(Float4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static float max(Float4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static float dot(Float4 a, Float4 b) {
        final Float4 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ() + m.getW();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return float
     */
    public static float length(Float4 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Float4 a, Float4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static float findULPDistance(Float4 a, Float4 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    }

}
