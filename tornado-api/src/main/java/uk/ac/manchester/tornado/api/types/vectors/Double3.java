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

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;

@Vector
public final class Double3 implements TornadoVectorsInterface<DoubleBuffer> {

    public static final Class<Double3> TYPE = Double3.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    final double[] storage;

    private Double3(double[] storage) {
        this.storage = storage;
    }

    public Double3() {
        this(new double[NUM_ELEMENTS]);
    }

    public Double3(double x, double y, double z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /**
     * * Operations on Double3 vectors.
     */
    public static Double3 add(Double3 a, Double3 b) {
        return new Double3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static Double3 sub(Double3 a, Double3 b) {
        return new Double3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static Double3 div(Double3 a, Double3 b) {
        return new Double3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static Double3 mult(Double3 a, Double3 b) {
        return new Double3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static Double3 min(Double3 a, Double3 b) {
        return new Double3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static Double3 max(Double3 a, Double3 b) {
        return new Double3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static Double3 cross(Double3 a, Double3 b) {
        return new Double3(a.getY() * b.getZ() - a.getZ() * b.getY(), a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Double3 add(Double3 a, double b) {
        return new Double3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static Double3 sub(Double3 a, double b) {
        return new Double3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static Double3 mult(Double3 a, double b) {
        return new Double3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static Double3 div(Double3 a, double b) {
        return new Double3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static Double3 inc(Double3 a, double value) {
        return new Double3(a.getX() + value, a.getY() + value, a.getZ() + value);
    }

    public static Double3 dec(Double3 a, double value) {
        return new Double3(a.getX() - value, a.getY() - value, a.getZ() - value);
    }

    public static Double3 scaleByInverse(Double3 a, double value) {
        return mult(a, 1f / value);
    }

    public static Double3 scale(Double3 a, double value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static Double3 sqrt(Double3 a) {
        return new Double3(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()));
    }

    public static Double3 floor(Double3 a) {
        return new Double3(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()));
    }

    public static Double3 fract(Double3 a) {
        return new Double3(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()));
    }

    /*
     * misc inplace vector ops
     */
    public static Double3 clamp(Double3 x, double min, double max) {
        return new Double3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    public static Double3 normalise(Double3 value) {
        final double len = 1f / length(value);
        return mult(value, len);
    }

    /*
     * vector wide operations
     */
    public static double min(Double3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static double max(Double3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static double dot(Double3 a, Double3 b) {
        final Double3 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return {@link double}
     */
    public static double length(Double3 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Double3 a, Double3 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public static boolean isEqualULP(Double3 a, Double3 b, double numULP) {
        return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
    }

    public static double findULPDistance(Double3 a, Double3 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    }

    public double[] getArray() {
        return storage;
    }

    public double get(int index) {
        return storage[index];
    }

    public void set(int index, double value) {
        storage[index] = value;
    }

    public void set(Double3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
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

    public void setS0(double value) {
        set(0, value);
    }

    public void setS1(double value) {
        set(1, value);
    }

    public void setS2(double value) {
        set(2, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Double3}
     */
    public Double3 duplicate() {
        final Double3 vector = new Double3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(DoubleOps.FMT_3);
    }

    /**
     * Cast vector into a Double2.
     *
     * @return {@link Double2}
     */
    public Double2 asDouble2() {
        return new Double2(getX(), getY());
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

    public double[] toArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 8;
    }

}
