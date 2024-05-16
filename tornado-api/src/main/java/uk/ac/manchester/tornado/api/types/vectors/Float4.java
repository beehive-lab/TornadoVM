/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import java.nio.FloatBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class Float4 implements TornadoVectorsInterface<FloatBuffer> {

    public static final Class<Float4> TYPE = Float4.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    final float[] storage;

    private Float4(float[] storage) {
        this.storage = storage;
    }

    public Float4() {
        this(new float[NUM_ELEMENTS]);
    }

    public Float4(float x, float y, float z, float w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    public static Float4 add(Float4 a, Float4 b) {
        return new Float4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Float4 add(Int4 a, Float4 b) {
        return new Float4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Float4 add(Float4 a, Int4 b) {
        return new Float4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Float4 sub(Float4 a, Float4 b) {
        return new Float4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Float4 sub(Int4 a, Float4 b) {
        return new Float4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Float4 sub(Float4 a, Int4 b) {
        return new Float4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Float4 div(Float4 a, Float4 b) {
        return new Float4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Float4 div(Int4 a, Float4 b) {
        return new Float4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Float4 div(Float4 a, Int4 b) {
        return new Float4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Float4 mult(Float4 a, Float4 b) {
        return new Float4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Float4 mult(Int4 a, Float4 b) {
        return new Float4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Float4 mult(Float4 a, Int4 b) {
        return new Float4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Float4 min(Float4 a, Float4 b) {
        return new Float4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Float4 min(Int4 a, Float4 b) {
        return new Float4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Float4 min(Float4 a, Int4 b) {
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
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    public static float length(Float4 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    // ===================================
    // Operations on Float4 vectors
    // vector = op( vector, vector )
    // ===================================

    public static boolean isEqual(Float4 a, Float4 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public static float findULPDistance(Float4 a, Float4 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    }

    public float get(int index) {
        return storage[index];
    }

    public void set(int index, float value) {
        storage[index] = value;
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
        return FloatBuffer.wrap(getArray());
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public void fill(float value) {
        Arrays.fill(storage, value);
    }

    public float[] toArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 4;
    }

}
