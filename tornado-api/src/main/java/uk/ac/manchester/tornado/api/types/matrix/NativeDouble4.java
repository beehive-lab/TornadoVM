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
import uk.ac.manchester.tornado.api.types.vectors.Double3;
import uk.ac.manchester.tornado.api.types.vectors.Double4;

@Vector
public final class NativeDouble4 implements TornadoMatrixInterface<DoubleBuffer> {

    public static final Class<NativeDouble4> TYPE = NativeDouble4.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorDouble nativeVectorDouble;

    public NativeDouble4(NativeVectorDouble nativeVectorDouble) {
        this.nativeVectorDouble = nativeVectorDouble;
    }

    public NativeDouble4() {
        this(new NativeVectorDouble(NUM_ELEMENTS));
    }

    public NativeDouble4(double x, double y, double z, double w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    /**
     * * Operations on Double4 vectors.
     */

    /*
     * vector = op( vector, vector )
     */
    public static NativeDouble4 add(NativeDouble4 a, NativeDouble4 b) {
        return new NativeDouble4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static NativeDouble4 sub(NativeDouble4 a, NativeDouble4 b) {
        return new NativeDouble4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static NativeDouble4 div(NativeDouble4 a, NativeDouble4 b) {
        return new NativeDouble4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static NativeDouble4 mult(NativeDouble4 a, NativeDouble4 b) {
        return new NativeDouble4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static NativeDouble4 min(NativeDouble4 a, NativeDouble4 b) {
        return new NativeDouble4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static NativeDouble4 max(NativeDouble4 a, NativeDouble4 b) {
        return new NativeDouble4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeDouble4 add(NativeDouble4 a, double b) {
        return new NativeDouble4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static NativeDouble4 sub(NativeDouble4 a, double b) {
        return new NativeDouble4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static NativeDouble4 mult(NativeDouble4 a, double b) {
        return new NativeDouble4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static NativeDouble4 div(NativeDouble4 a, double b) {
        return new NativeDouble4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static NativeDouble4 inc(NativeDouble4 a, double value) {
        return add(a, value);
    }

    public static NativeDouble4 dec(NativeDouble4 a, double value) {
        return sub(a, value);
    }

    public static NativeDouble4 scaleByInverse(NativeDouble4 a, double value) {
        return mult(a, 1f / value);
    }

    public static NativeDouble4 scale(NativeDouble4 a, double value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static NativeDouble4 sqrt(NativeDouble4 a) {
        return new NativeDouble4(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()), TornadoMath.sqrt(a.getW()));
    }

    public static NativeDouble4 floor(NativeDouble4 a) {
        return new NativeDouble4(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()), TornadoMath.floor(a.getW()));
    }

    public static NativeDouble4 fract(NativeDouble4 a) {
        return new NativeDouble4(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()), TornadoMath.fract(a.getW()));
    }

    /*
     * misc inplace vector ops
     */
    public static NativeDouble4 clamp(NativeDouble4 x, double min, double max) {
        return new NativeDouble4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    public static void normalise(NativeDouble4 value) {
        final double len = length(value);
        scaleByInverse(value, len);
    }

    /*
     * vector wide operations
     */
    public static double min(NativeDouble4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static double max(NativeDouble4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static double dot(NativeDouble4 a, NativeDouble4 b) {
        final NativeDouble4 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ() + m.getW();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return {@link double}
     */
    public static double length(NativeDouble4 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(NativeDouble4 a, NativeDouble4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public double get(int index) {
        return nativeVectorDouble.get(index);
    }

    public void set(int index, double value) {
        nativeVectorDouble.set(index, value);
    }

    public void set(NativeDouble4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
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

    public double getZ() {
        return get(2);
    }

    public void setZ(double value) {
        set(2, value);
    }

    public double getW() {
        return get(3);
    }

    public void setW(double value) {
        set(3, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Double4}
     */
    public NativeDouble4 duplicate() {
        final NativeDouble4 vector = new NativeDouble4();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(DoubleOps.FMT_4);
    }

    /**
     * Cast vector into a Double2.
     *
     * @return {@link Double2}
     */
    public Double2 asDouble2() {
        return new Double2(getX(), getY());
    }

    public Double3 asDouble3() {
        return new Double3(getX(), getY(), getZ());
    }

    public Double2 getLow() {
        return asDouble2();
    }

    public Double2 getHigh() {
        return new Double2(getZ(), getW());
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

    public double[] toArray() {
        return nativeVectorDouble.getSegment().toArray(ValueLayout.JAVA_DOUBLE);
    }

    static NativeDouble4 loadFromArray(final DoubleArray array, int index) {
        final NativeDouble4 result = new NativeDouble4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    void storeToArray(final DoubleArray array, int index) {
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            array.set(index + i, get(i));
        }
    }

    public void clear() {
        nativeVectorDouble.clear();
    }
}
