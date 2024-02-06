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

import java.nio.DoubleBuffer;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;

@Vector
public final class Double4 implements TornadoVectorsInterface<DoubleBuffer> {

    public static final Class<Double4> TYPE = Double4.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    final double[] storage;

    private Double4(double[] storage) {
        this.storage = storage;
    }

    public Double4() {
        this(new double[NUM_ELEMENTS]);
    }

    public Double4(double x, double y, double z, double w) {
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
    public static Double4 add(Double4 a, Double4 b) {
        return new Double4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Double4 sub(Double4 a, Double4 b) {
        return new Double4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Double4 div(Double4 a, Double4 b) {
        return new Double4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Double4 mult(Double4 a, Double4 b) {
        return new Double4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Double4 min(Double4 a, Double4 b) {
        return new Double4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Double4 max(Double4 a, Double4 b) {
        return new Double4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Double4 add(Double4 a, double b) {
        return new Double4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static Double4 sub(Double4 a, double b) {
        return new Double4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static Double4 mult(Double4 a, double b) {
        return new Double4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static Double4 div(Double4 a, double b) {
        return new Double4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static Double4 inc(Double4 a, double value) {
        return add(a, value);
    }

    public static Double4 dec(Double4 a, double value) {
        return sub(a, value);
    }

    public static Double4 scaleByInverse(Double4 a, double value) {
        return mult(a, 1f / value);
    }

    public static Double4 scale(Double4 a, double value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static Double4 sqrt(Double4 a) {
        return new Double4(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()), TornadoMath.sqrt(a.getW()));
    }

    public static Double4 floor(Double4 a) {
        return new Double4(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()), TornadoMath.floor(a.getW()));
    }

    public static Double4 fract(Double4 a) {
        return new Double4(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()), TornadoMath.fract(a.getW()));
    }

    /*
     * misc inplace vector ops
     */
    public static Double4 clamp(Double4 x, double min, double max) {
        return new Double4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    public static void normalise(Double4 value) {
        final double len = length(value);
        scaleByInverse(value, len);
    }

    /*
     * vector wide operations
     */
    public static double min(Double4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static double max(Double4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static double dot(Double4 a, Double4 b) {
        final Double4 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ() + m.getW();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return {@link double}
     */
    public static double length(Double4 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Double4 a, Double4 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public double get(int index) {
        return storage[index];
    }

    public void set(int index, double value) {
        storage[index] = value;
    }

    public double[] getArray() {
        return storage;
    }

    public void set(Double4 value) {
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
    public Double4 duplicate() {
        final Double4 vector = new Double4();
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
        return DoubleBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public void fill(double value) {
        Arrays.fill(storage, value);
    }

    public double[] toArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 8;
    }
}
