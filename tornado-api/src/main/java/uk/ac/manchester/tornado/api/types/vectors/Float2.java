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

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

@Vector
public final class Float2 implements TornadoVectorsInterface<FloatBuffer> {

    public static final Class<Float2> TYPE = Float2.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    final float[] storage;

    private Float2(float[] storage) {
        this.storage = storage;
    }

    public Float2() {
        this(new float[NUM_ELEMENTS]);
    }

    public Float2(float x, float y) {
        this();
        setX(x);
        setY(y);
    }

    public static Float2 add(Float2 a, Float2 b) {
        return new Float2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Float2 add(Float2 a, Int2 b) {
        return new Float2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Float2 add(Int2 a, Float2 b) {
        return new Float2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Float2 sub(Float2 a, Float2 b) {
        return new Float2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Float2 sub(Int2 a, Float2 b) {
        return new Float2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Float2 sub(Float2 a, Int2 b) {
        return new Float2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Float2 div(Float2 a, Float2 b) {
        return new Float2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Float2 div(Int2 a, Float2 b) {
        return new Float2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Float2 div(Float2 a, Int2 b) {
        return new Float2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Float2 mult(Float2 a, Float2 b) {
        return new Float2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Float2 mult(Int2 a, Float2 b) {
        return new Float2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Float2 mult(Float2 a, Int2 b) {
        return new Float2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Float2 min(Float2 a, Float2 b) {
        return new Float2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Float2 min(Int2 a, Float2 b) {
        return new Float2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Float2 min(Float2 a, Int2 b) {
        return new Float2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Float2 max(Float2 a, Float2 b) {
        return new Float2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    public static Float2 max(Int2 a, Float2 b) {
        return new Float2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    public static Float2 max(Float2 a, Int2 b) {
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

    public static Float2 normalise(Float2 value) {
        return scaleByInverse(value, length(value));
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
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    public static float length(Float2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Float2 a, Float2 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
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
     * @return {@link Float2}
     */
    public Float2 duplicate() {
        Float2 vector = new Float2();
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
        return FloatBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public float[] toArray() {
        return storage;
    }

    public float[] getArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 4;
    }
}
