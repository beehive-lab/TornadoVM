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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class NativeFloat2 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<NativeFloat2> TYPE = NativeFloat2.class;

    public static final Class<NativeVectorFloat> FIELD_CLASS = NativeVectorFloat.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorFloat nativeVector;

    public NativeFloat2(NativeVectorFloat nativeVector) {
        this.nativeVector = nativeVector;
    }

    public NativeFloat2() {
        this(new NativeVectorFloat(2));
    }

    public NativeFloat2(float x, float y) {
        this();
        setX(x);
        setY(y);
    }

    public static NativeFloat2 add(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static NativeFloat2 sub(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static NativeFloat2 div(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static NativeFloat2 mult(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static NativeFloat2 min(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static NativeFloat2 max(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeFloat2 add(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() + b, a.getY() + b);
    }

    public static NativeFloat2 sub(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() - b, a.getY() - b);
    }

    public static NativeFloat2 mult(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() * b, a.getY() * b);
    }

    public static NativeFloat2 div(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() / b, a.getY() / b);
    }

    /*
     * vector = op (vector, vector)
     */
    public static void add(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() + b.getX());
        c.setY(a.getY() + b.getY());
    }

    public static void sub(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() - b.getX());
        c.setY(a.getY() - b.getY());
    }

    public static void mult(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() * b.getX());
        c.setY(a.getY() * b.getY());
    }

    public static void div(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() / b.getX());
        c.setY(a.getY() / b.getY());
    }

    public static void min(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(Math.min(a.getX(), b.getX()));
        c.setY(Math.min(a.getY(), b.getY()));
    }

    public static void max(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(Math.max(a.getX(), b.getX()));
        c.setY(Math.max(a.getY(), b.getY()));
    }

    public static NativeFloat2 inc(NativeFloat2 a, float value) {
        return add(a, value);
    }

    public static NativeFloat2 dec(NativeFloat2 a, float value) {
        return sub(a, value);
    }

    public static NativeFloat2 scaleByInverse(NativeFloat2 a, float value) {
        return mult(a, 1f / value);
    }

    public static NativeFloat2 scale(NativeFloat2 a, float value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static NativeFloat2 sqrt(NativeFloat2 a) {
        return new NativeFloat2(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()));
    }

    public static NativeFloat2 floor(NativeFloat2 a) {
        return new NativeFloat2(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()));
    }

    public static NativeFloat2 fract(NativeFloat2 a) {
        return new NativeFloat2(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()));
    }

    /*
     * misc inplace vector ops
     */
    public static void clamp(NativeFloat2 x, float min, float max) {
        x.setX(TornadoMath.clamp(x.getX(), min, max));
        x.setY(TornadoMath.clamp(x.getY(), min, max));
    }

    public static NativeFloat2 normalise(NativeFloat2 value) {
        return scaleByInverse(value, length(value));
    }

    /*
     * vector wide operations
     */
    public static float min(NativeFloat2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static float max(NativeFloat2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static float dot(NativeFloat2 a, NativeFloat2 b) {
        final NativeFloat2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    public static float length(NativeFloat2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(NativeFloat2 a, NativeFloat2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    static NativeFloat2 loadFromArray(final FloatArray array, int index) {
        final NativeFloat2 result = new NativeFloat2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    public float get(int index) {
        return nativeVector.get(index);
    }

    public void set(int index, float value) {
        nativeVector.set(index, value);
    }

    public void set(NativeFloat2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    public float getX() {
        return get(0);
    }

    public void setX(float value) {
        set(0, value);
    }

    public float getY() {
        return get(1);
    }

    public void setY(float value) {
        set(1, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeFloat2}
     */
    public NativeFloat2 duplicate() {
        NativeFloat2 vector = new NativeFloat2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_2);
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        return nativeVector.getSegment().asByteBuffer().asFloatBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public float[] toArray() {
        return nativeVector.getSegment().toArray(JAVA_FLOAT);
    }

    void storeToArray(final FloatArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
    }

}
