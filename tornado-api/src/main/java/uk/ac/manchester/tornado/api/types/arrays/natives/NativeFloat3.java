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
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class NativeFloat3 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<NativeFloat3> TYPE = NativeFloat3.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorFloat nativeVectorFloat;

    public NativeFloat3(NativeVectorFloat nativeVectorFloat) {
        this.nativeVectorFloat = nativeVectorFloat;
    }

    public NativeFloat3() {
        this(new NativeVectorFloat(NUM_ELEMENTS));
    }

    public NativeFloat3(float x, float y, float z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /**
     * * Operations on Float3 vectors.
     */
    /*
     * vector = op( vector, vector )
     */
    public static NativeFloat3 add(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static NativeFloat3 sub(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static NativeFloat3 div(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static NativeFloat3 mult(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static NativeFloat3 min(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static NativeFloat3 max(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static NativeFloat3 cross(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getY() * b.getZ() - a.getZ() * b.getY(), a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeFloat3 add(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static NativeFloat3 sub(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static NativeFloat3 mult(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static NativeFloat3 div(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static NativeFloat3 inc(NativeFloat3 a, float value) {
        return new NativeFloat3(a.getX() + value, a.getY() + value, a.getZ() + value);
    }

    public static NativeFloat3 dec(NativeFloat3 a, float value) {
        return new NativeFloat3(a.getX() - value, a.getY() - value, a.getZ() - value);
    }

    public static NativeFloat3 scaleByInverse(NativeFloat3 a, float value) {
        return mult(a, 1f / value);
    }

    public static NativeFloat3 scale(NativeFloat3 a, float value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static NativeFloat3 sqrt(NativeFloat3 a) {
        return new NativeFloat3(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()));
    }

    public static NativeFloat3 floor(NativeFloat3 a) {
        return new NativeFloat3(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()));
    }

    public static NativeFloat3 fract(NativeFloat3 a) {
        return new NativeFloat3(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()));
    }

    /*
     * misc inplace vector ops
     */
    public static NativeFloat3 clamp(NativeFloat3 x, float min, float max) {
        return new NativeFloat3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    public static NativeFloat3 normalise(NativeFloat3 value) {
        final float len = 1f / length(value);
        return mult(value, len);
    }

    /*
     * vector wide operations
     */
    public static float min(NativeFloat3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static float max(NativeFloat3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static float dot(NativeFloat3 a, NativeFloat3 b) {
        final NativeFloat3 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    public static float length(NativeFloat3 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(NativeFloat3 a, NativeFloat3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static boolean isEqualULP(NativeFloat3 a, NativeFloat3 b, float numULP) {
        return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
    }

    public static float findULPDistance(NativeFloat3 a, NativeFloat3 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
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

    public void set(NativeFloat3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
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

    public float getZ() {
        return get(2);
    }

    public void setZ(float value) {
        set(2, value);
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

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeFloat3}
     */
    public NativeFloat3 duplicate() {
        final NativeFloat3 vector = new NativeFloat3();
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
     * Cast vector From Float3 into a Float2.
     *
     * @return {@link Float2}
     */
    public Float2 asFloat2() {
        return new Float2(getX(), getY());
    }

    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public FloatBuffer asBuffer() {
        //TODO: This needs to be removed
        return nativeVectorFloat.getSegment().asByteBuffer().asFloatBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public float[] toArray() {
        return nativeVectorFloat.getSegment().toArray(JAVA_FLOAT);
    }

    static NativeFloat3 loadFromArray(final FloatArray array, int index) {
        final NativeFloat3 result = new NativeFloat3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    void storeToArray(final FloatArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
        array.set(index + 2, getZ());
    }

}
