/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.types;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.Payload;
import uk.ac.manchester.tornado.collections.math.TornadoMath;

import static java.lang.Double.MAX_VALUE;
import static java.lang.Double.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.DoubleBuffer.wrap;
import static uk.ac.manchester.tornado.collections.types.DoubleOps.fmt6;

/**
 * Class that represents a vector of 3x doubles e.g. <double,double,double>
 *
 * @author jamesclarkson
 *
 */
public final class Double6 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<Double6> TYPE = Double6.class;

    /**
     * backing array
     */
    @Payload
    final protected double[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 6;

    public Double6(double[] storage) {
        this.storage = storage;
    }

    public Double6() {
        this(new double[numElements]);
    }

    public Double6(double s0, double s1, double s2, double s3, double s4, double s5) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
    }

    public void set(Double6 value) {
        setS0(value.getS0());
        setS1(value.getS1());
        setS2(value.getS2());
        setS3(value.getS3());
        setS4(value.getS4());
        setS5(value.getS5());
    }

    public double get(int index) {
        return storage[index];
    }

    public void set(int index, double value) {
        storage[index] = value;
    }

    public double getS0() {
        return get(0);
    }

    public double getS1() {
        return get(1);
    }

    public double getS2() {
        return get(2);
    }

    public double getS3() {
        return get(3);
    }

    public double getS4() {
        return get(4);
    }

    public double getS5() {
        return get(5);
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

    public void setS3(double value) {
        set(3, value);
    }

    public void setS4(double value) {
        set(4, value);
    }

    public void setS5(double value) {
        set(5, value);
    }

    public Double3 getHi() {
        return Double3.loadFromArray(storage, 0);
    }

    public Double3 getLo() {
        return Double3.loadFromArray(storage, 3);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Double6 duplicate() {
        final Double6 vector = new Double6();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5());
    }

    @Override
    public String toString() {
        return toString(fmt6);
    }

    public static final Double6 loadFromArray(final double[] array, int index) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, array[index + i]);
        }
        return result;
    }

    public final void storeToArray(final double[] array, int index) {
        for (int i = 0; i < numElements; i++) {
            array[index + i] = get(i);
        }
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /**
     * *
     * Operations on Double6 vectors
     */
    /*
     * vector = op( vector, vector )
     */
    public static Double6 add(Double6 a, Double6 b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Double6 sub(Double6 a, Double6 b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Double6 div(Double6 a, Double6 b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Double6 mult(Double6 a, Double6 b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Double6 min(Double6 a, Double6 b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Double6 max(Double6 a, Double6 b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Double6 add(Double6 a, double b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Double6 sub(Double6 a, double b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Double6 mult(Double6 a, double b) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b);
        }
        return result;
    }

    public static Double6 inc(Double6 a, double value) {
        return add(a, value);
    }

    public static Double6 dec(Double6 a, double value) {
        return sub(a, value);
    }

    public static Double6 scale(Double6 a, double value) {
        return mult(a, value);
    }

    public static Double6 scaleByInverse(Double6 a, double value) {
        return mult(a, 1f / value);
    }

    /*
     * vector = op(vector)
     */
    public static Double6 sqrt(Double6 a) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static Double6 floor(Double6 a) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.floor(a.get(i)));
        }
        return result;
    }

    public static Double6 fract(Double6 a) {
        final Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.fract(a.get(i)));
        }
        return result;
    }

    public static Double6 clamp(Double6 a, double min, double max) {
        Double6 result = new Double6();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.clamp(a.get(i), min, max));
        }
        return result;
    }

    /*
     * vector wide operations
     */
    public static double min(Double6 value) {
        double result = MAX_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static double max(Double6 value) {
        double result = MIN_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static double dot(Double6 a, Double6 b) {
        double result = 0f;
        final Double6 m = mult(a, b);
        for (int i = 0; i < numElements; i++) {
            result += m.get(i);
        }
        return result;
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return
     */
    public static double length(Double6 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Double6 a, Double6 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
