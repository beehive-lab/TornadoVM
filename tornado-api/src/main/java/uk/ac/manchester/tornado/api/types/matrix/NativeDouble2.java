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
package uk.ac.manchester.tornado.api.types.matrix;

import java.lang.foreign.ValueLayout;
import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorDouble;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;
import uk.ac.manchester.tornado.api.types.vectors.Double2;

@Vector
public final class NativeDouble2 implements TornadoMatrixInterface<DoubleBuffer> {

    public static final Class<NativeDouble2> TYPE = NativeDouble2.class;
    public static final Class<NativeVectorDouble> FIELD_CLASS = NativeVectorDouble.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;

    /**
     * backing array.
     */
    @Payload
    final NativeVectorDouble nativeVectorDouble;

    public NativeDouble2(NativeVectorDouble nativeVectorDouble) {
        this.nativeVectorDouble = nativeVectorDouble;
    }

    public NativeDouble2() {
        this(new NativeVectorDouble(NUM_ELEMENTS));
    }

    public NativeDouble2(double x, double y) {
        this();
        setX(x);
        setY(y);
    }

    /**
     * * Operations on Double2 vectors.
     */
    /*
     * vector = op( vector, vector )
     */
    public static NativeDouble2 add(NativeDouble2 a, NativeDouble2 b) {
        return new NativeDouble2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static NativeDouble2 sub(NativeDouble2 a, NativeDouble2 b) {
        return new NativeDouble2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static NativeDouble2 div(NativeDouble2 a, NativeDouble2 b) {
        return new NativeDouble2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static NativeDouble2 mult(NativeDouble2 a, NativeDouble2 b) {
        return new NativeDouble2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static NativeDouble2 min(NativeDouble2 a, NativeDouble2 b) {
        return new NativeDouble2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static NativeDouble2 max(NativeDouble2 a, NativeDouble2 b) {
        return new NativeDouble2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeDouble2 add(NativeDouble2 a, double b) {
        return new NativeDouble2(a.getX() + b, a.getY() + b);
    }

    public static NativeDouble2 sub(NativeDouble2 a, double b) {
        return new NativeDouble2(a.getX() - b, a.getY() - b);
    }

    public static NativeDouble2 mult(NativeDouble2 a, double b) {
        return new NativeDouble2(a.getX() * b, a.getY() * b);
    }

    public static NativeDouble2 div(Double2 a, double b) {
        return new NativeDouble2(a.getX() / b, a.getY() / b);
    }

    /*
     * vector = op (vector, vector)
     */
    public static void add(NativeDouble2 a, NativeDouble2 b, NativeDouble2 c) {
        c.setX(a.getX() + b.getX());
        c.setY(a.getY() + b.getY());
    }

    public static void sub(NativeDouble2 a, NativeDouble2 b, NativeDouble2 c) {
        c.setX(a.getX() - b.getX());
        c.setY(a.getY() - b.getY());
    }

    public static void mult(NativeDouble2 a, NativeDouble2 b, NativeDouble2 c) {
        c.setX(a.getX() * b.getX());
        c.setY(a.getY() * b.getY());
    }

    public static void div(NativeDouble2 a, NativeDouble2 b, NativeDouble2 c) {
        c.setX(a.getX() / b.getX());
        c.setY(a.getY() / b.getY());
    }

    public static void min(NativeDouble2 a, NativeDouble2 b, NativeDouble2 c) {
        c.setX(Math.min(a.getX(), b.getX()));
        c.setY(Math.min(a.getY(), b.getY()));
    }

    public static void max(NativeDouble2 a, NativeDouble2 b, NativeDouble2 c) {
        c.setX(Math.max(a.getX(), b.getX()));
        c.setY(Math.max(a.getY(), b.getY()));
    }

    public static NativeDouble2 inc(NativeDouble2 a, double value) {
        return add(a, value);
    }

    public static NativeDouble2 dec(NativeDouble2 a, double value) {
        return sub(a, value);
    }

    public static NativeDouble2 scaleByInverse(NativeDouble2 a, double value) {
        return mult(a, 1f / value);
    }

    public static NativeDouble2 scale(NativeDouble2 a, double value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static NativeDouble2 sqrt(NativeDouble2 a) {
        return new NativeDouble2(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()));
    }

    public static NativeDouble2 floor(NativeDouble2 a) {
        return new NativeDouble2(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()));
    }

    public static NativeDouble2 fract(NativeDouble2 a) {
        return new NativeDouble2(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()));
    }

    /*
     * misc inplace vector ops
     */
    public static void clamp(NativeDouble2 x, double min, double max) {
        x.setX(TornadoMath.clamp(x.getX(), min, max));
        x.setY(TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static double min(NativeDouble2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static double max(NativeDouble2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static double dot(NativeDouble2 a, NativeDouble2 b) {
        final NativeDouble2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return {@link double}
     */
    public static double length(NativeDouble2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(NativeDouble2 a, NativeDouble2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public double get(int index) {
        return nativeVectorDouble.get(index);
    }

    public void set(int index, double value) {
        nativeVectorDouble.set(index, value);
    }

    public void set(NativeDouble2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    public double getX() {
        return get(0);
    }

    public void setX(double value) {
        set(0, value);
    }

    public double getY() {
        return get(1);
    }

    public void setY(double value) {
        set(1, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@Double 2}
     */
    public NativeDouble2 duplicate() {
        NativeDouble2 vector = new NativeDouble2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(DoubleOps.FMT_2);
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return nativeVectorDouble.getSegment().asByteBuffer().asDoubleBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public void fill(double value) {
        for (int i = 0; i < nativeVectorDouble.getSize(); i++) {
            nativeVectorDouble.set(i, value);
        }
    }

    static NativeDouble2 loadFromArray(final DoubleArray array, int index) {
        final NativeDouble2 result = new NativeDouble2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    void storeToArray(final DoubleArray array, int index) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            array.set(index + i, get(i));
        }
    }

    public double[] toArray() {
        return nativeVectorDouble.getSegment().toArray(ValueLayout.JAVA_DOUBLE);
    }

    public void clear() {
        nativeVectorDouble.clear();
    }

}
