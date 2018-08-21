/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.fmt2;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.annotations.Payload;
import uk.ac.manchester.tornado.api.annotations.Vector;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

/**
 * Class that represents a vector of 2x floats e.g. <float,float>
 *
 * @author jamesclarkson
 *
 */
@Vector
public final class Float2 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<Float2> TYPE = Float2.class;

    /**
     * backing array
     */
    @Payload
    final protected float[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 2;

    protected Float2(float[] storage) {
        this.storage = storage;
    }

    public Float2() {
        this(new float[numElements]);
    }

    public Float2(float x, float y) {
        this();
        setX(x);
        setY(y);
    }

    public float get(int index) {
        return storage[index];
    }

    public void set(int index, float value) {
        storage[index] = value;
    }

    public void set(Float2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    public float getX() {
        return get(0);
    }

    public float getY() {
        return get(1);
    }

    public void setX(float value) {
        set(0, value);
    }

    public void setY(float value) {
        set(1, value);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Float2 duplicate() {
        Float2 vector = new Float2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(fmt2);
    }

    protected static final Float2 loadFromArray(final float[] array, int index) {
        final Float2 result = new Float2();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        return result;
    }

    protected final void storeToArray(final float[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /**
     * *
     * Operations on Float2 vectors
     */
    /*
     * vector = op( vector, vector )
     */
    public static Float2 add(Float2 a, Float2 b) {
        return new Float2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Float2 sub(Float2 a, Float2 b) {
        return new Float2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Float2 div(Float2 a, Float2 b) {
        return new Float2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Float2 mult(Float2 a, Float2 b) {
        return new Float2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Float2 min(Float2 a, Float2 b) {
        return new Float2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Float2 max(Float2 a, Float2 b) {
        return new Float2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Float2 add(Float2 a, float b) {
        return new Float2(a.getX() + b, a.getY() + b);
    }

    public static Float2 sub(Float2 a, float b) {
        return new Float2(a.getX() - b, a.getY() - b);
    }

    public static Float2 mult(Float2 a, float b) {
        return new Float2(a.getX() * b, a.getY() * b);
    }

    public static Float2 div(Float2 a, float b) {
        return new Float2(a.getX() / b, a.getY() / b);
    }

    /*
     * vector = op (vector, vector)
     */
    public static void add(Float2 a, Float2 b, Float2 c) {
        c.setX(a.getX() + b.getX());
        c.setY(a.getY() + b.getY());
    }

    public static void sub(Float2 a, Float2 b, Float2 c) {
        c.setX(a.getX() - b.getX());
        c.setY(a.getY() - b.getY());
    }

    public static void mult(Float2 a, Float2 b, Float2 c) {
        c.setX(a.getX() * b.getX());
        c.setY(a.getY() * b.getY());
    }

    public static void div(Float2 a, Float2 b, Float2 c) {
        c.setX(a.getX() / b.getX());
        c.setY(a.getY() / b.getY());
    }

    public static void min(Float2 a, Float2 b, Float2 c) {
        c.setX(Math.min(a.getX(), b.getX()));
        c.setY(Math.min(a.getY(), b.getY()));
    }

    public static void max(Float2 a, Float2 b, Float2 c) {
        c.setX(Math.max(a.getX(), b.getX()));
        c.setY(Math.max(a.getY(), b.getY()));
    }

    public static Float2 inc(Float2 a, float value) {
        return add(a, value);
    }

    public static Float2 dec(Float2 a, float value) {
        return sub(a, value);
    }

    public static Float2 scaleByInverse(Float2 a, float value) {
        return mult(a, 1f / value);
    }

    public static Float2 scale(Float2 a, float value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static Float2 sqrt(Float2 a) {
        return new Float2(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()));
    }

    public static Float2 floor(Float2 a) {
        return new Float2(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()));
    }

    public static Float2 fract(Float2 a) {
        return new Float2(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()));
    }

    /*
     * misc inplace vector ops
     */
    public static void clamp(Float2 x, float min, float max) {
        x.setX(TornadoMath.clamp(x.getX(), min, max));
        x.setY(TornadoMath.clamp(x.getY(), min, max));
    }

    public static void normalise(Float2 value) {
        final float len = length(value);
        scaleByInverse(value, len);
    }

    /*
     * vector wide operations
     */
    public static float min(Float2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static float max(Float2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static float dot(Float2 a, Float2 b) {
        final Float2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return
     */
    public static float length(Float2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Float2 a, Float2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
