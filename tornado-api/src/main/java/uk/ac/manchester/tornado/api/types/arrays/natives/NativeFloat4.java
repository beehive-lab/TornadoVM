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
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class NativeFloat4 implements PrimitiveStorage<FloatBuffer> {

    public static final Class<NativeFloat4> TYPE = NativeFloat4.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorFloat nativeVectorFloat;

    public NativeFloat4(NativeVectorFloat storage) {
        this.nativeVectorFloat = storage;
    }

    public NativeFloat4() {
        this(new NativeVectorFloat(NUM_ELEMENTS));
    }

    public NativeFloat4(float x, float y, float z, float w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    public static NativeFloat4 add(NativeFloat4 a, NativeFloat4 b) {
        return new NativeFloat4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static NativeFloat4 sub(NativeFloat4 a, NativeFloat4 b) {
        return new NativeFloat4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static NativeFloat4 div(NativeFloat4 a, NativeFloat4 b) {
        return new NativeFloat4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static NativeFloat4 mult(NativeFloat4 a, NativeFloat4 b) {
        return new NativeFloat4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static NativeFloat4 min(NativeFloat4 a, NativeFloat4 b) {
        return new NativeFloat4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static NativeFloat4 max(NativeFloat4 a, NativeFloat4 b) {
        return new NativeFloat4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeFloat4 add(NativeFloat4 a, float b) {
        return new NativeFloat4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static NativeFloat4 sub(NativeFloat4 a, float b) {
        return new NativeFloat4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static NativeFloat4 mult(NativeFloat4 a, float b) {
        return new NativeFloat4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static NativeFloat4 div(NativeFloat4 a, float b) {
        return new NativeFloat4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static NativeFloat4 inc(NativeFloat4 a, float value) {
        return add(a, value);
    }

    public static NativeFloat4 dec(NativeFloat4 a, float value) {
        return sub(a, value);
    }

    public static NativeFloat4 scaleByInverse(NativeFloat4 a, float value) {
        return mult(a, 1f / value);
    }

    public static NativeFloat4 scale(NativeFloat4 a, float value) {
        return mult(a, value);
    }

    public static float sum(NativeFloat4 a) {
        return a.getX() + a.getY() + a.getZ() + a.getW();
    }

    /*
     * vector = op(vector)
     */
    public static NativeFloat4 sqrt(NativeFloat4 a) {
        return new NativeFloat4(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()), TornadoMath.sqrt(a.getW()));
    }

    public static NativeFloat4 floor(NativeFloat4 a) {
        return new NativeFloat4(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()), TornadoMath.floor(a.getW()));
    }

    public static NativeFloat4 fract(NativeFloat4 a) {
        return new NativeFloat4(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()), TornadoMath.fract(a.getW()));
    }

    /*
     * misc inplace vector ops
     */
    public static NativeFloat4 clamp(NativeFloat4 x, float min, float max) {
        return new NativeFloat4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    public static NativeFloat4 normalise(NativeFloat4 value) {
        return scaleByInverse(value, length(value));
    }

    /*
     * vector wide operations
     */
    public static float min(NativeFloat4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static float max(NativeFloat4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static float dot(NativeFloat4 a, NativeFloat4 b) {
        final NativeFloat4 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ() + m.getW();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    public static float length(NativeFloat4 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    // ===================================
    // Operations on Float4 vectors
    // vector = op( vector, vector )
    // ===================================

    public static boolean isEqual(NativeFloat4 a, NativeFloat4 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public static float findULPDistance(NativeFloat4 a, NativeFloat4 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    }

    public float get(int index) {
        return nativeVectorFloat.get(index);
    }

    public void set(int index, float value) {
        nativeVectorFloat.set(index, value);
    }

    public NativeVectorFloat getArray() {
        return nativeVectorFloat;
    }

    public void set(NativeFloat4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
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

    public float getW() {
        return get(3);
    }

    public void setW(float value) {
        set(3, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeFloat4}
     */
    public NativeFloat4 duplicate() {
        final NativeFloat4 vector = new NativeFloat4();
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
     * Cast vector into a Float2.
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

    public void fill(float value) {
        nativeVectorFloat.init(value);
    }

    public float[] toArray() {
        return nativeVectorFloat.getSegment().toArray(JAVA_FLOAT);
    }

    static NativeFloat4 loadFromArray(final FloatArray array, int index) {
        final NativeFloat4 result = new NativeFloat4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    void storeToArray(final FloatArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
        array.set(index + 2, getZ());
        array.set(index + 3, getW());
    }

}
