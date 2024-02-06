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
public final class Double2 implements TornadoVectorsInterface<DoubleBuffer> {

    public static final Class<Double2> TYPE = Double2.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    final double[] storage;

    private Double2(double[] storage) {
        this.storage = storage;
    }

    public Double2() {
        this(new double[NUM_ELEMENTS]);
    }

    public Double2(double x, double y) {
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
    public static Double2 add(Double2 a, Double2 b) {
        return new Double2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Double2 sub(Double2 a, Double2 b) {
        return new Double2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Double2 div(Double2 a, Double2 b) {
        return new Double2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Double2 mult(Double2 a, Double2 b) {
        return new Double2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Double2 min(Double2 a, Double2 b) {
        return new Double2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Double2 max(Double2 a, Double2 b) {
        return new Double2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Double2 add(Double2 a, double b) {
        return new Double2(a.getX() + b, a.getY() + b);
    }

    public static Double2 sub(Double2 a, double b) {
        return new Double2(a.getX() - b, a.getY() - b);
    }

    public static Double2 mult(Double2 a, double b) {
        return new Double2(a.getX() * b, a.getY() * b);
    }

    public static Double2 div(Double2 a, double b) {
        return new Double2(a.getX() / b, a.getY() / b);
    }

    /*
     * vector = op (vector, vector)
     */
    public static void add(Double2 a, Double2 b, Double2 c) {
        c.setX(a.getX() + b.getX());
        c.setY(a.getY() + b.getY());
    }

    public static void sub(Double2 a, Double2 b, Double2 c) {
        c.setX(a.getX() - b.getX());
        c.setY(a.getY() - b.getY());
    }

    public static void mult(Double2 a, Double2 b, Double2 c) {
        c.setX(a.getX() * b.getX());
        c.setY(a.getY() * b.getY());
    }

    public static void div(Double2 a, Double2 b, Double2 c) {
        c.setX(a.getX() / b.getX());
        c.setY(a.getY() / b.getY());
    }

    public static void min(Double2 a, Double2 b, Double2 c) {
        c.setX(Math.min(a.getX(), b.getX()));
        c.setY(Math.min(a.getY(), b.getY()));
    }

    public static void max(Double2 a, Double2 b, Double2 c) {
        c.setX(Math.max(a.getX(), b.getX()));
        c.setY(Math.max(a.getY(), b.getY()));
    }

    public static Double2 inc(Double2 a, double value) {
        return add(a, value);
    }

    public static Double2 dec(Double2 a, double value) {
        return sub(a, value);
    }

    public static Double2 scaleByInverse(Double2 a, double value) {
        return mult(a, 1f / value);
    }

    public static Double2 scale(Double2 a, double value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static Double2 sqrt(Double2 a) {
        return new Double2(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()));
    }

    public static Double2 floor(Double2 a) {
        return new Double2(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()));
    }

    public static Double2 fract(Double2 a) {
        return new Double2(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()));
    }

    /*
     * misc inplace vector ops
     */
    public static void clamp(Double2 x, double min, double max) {
        x.setX(TornadoMath.clamp(x.getX(), min, max));
        x.setY(TornadoMath.clamp(x.getY(), min, max));
    }

    public static void normalise(Double2 value) {
        final double len = length(value);
        scaleByInverse(value, len);
    }

    /*
     * vector wide operations
     */
    public static double min(Double2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static double max(Double2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static double dot(Double2 a, Double2 b) {
        final Double2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return {@link double}
     */
    public static double length(Double2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Double2 a, Double2 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
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

    public void set(Double2 value) {
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
    public Double2 duplicate() {
        Double2 vector = new Double2();
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
