/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.types;

import static java.lang.String.format;
import static java.nio.DoubleBuffer.wrap;
import static uk.ac.manchester.tornado.collections.types.DoubleOps.fmt2;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.annotations.Payload;
import uk.ac.manchester.tornado.api.annotations.Vector;
import uk.ac.manchester.tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 2x doubles e.g. <double,double>
 *
 * @author jamesclarkson
 *
 */
@Vector
public final class Double2 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<Double2> TYPE = Double2.class;

    /**
     * backing array
     */
    @Payload
    final protected double[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 2;

    protected Double2(double[] storage) {
        this.storage = storage;
    }

    public Double2() {
        this(new double[numElements]);
    }

    public Double2(double x, double y) {
        this();
        setX(x);
        setY(y);
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

    public double getY() {
        return get(1);
    }

    public void setX(double value) {
        set(0, value);
    }

    public void setY(double value) {
        set(1, value);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Double2 duplicate() {
        Double2 vector = new Double2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(fmt2);
    }

    protected static final Double2 loadFromArray(final double[] array, int index) {
        final Double2 result = new Double2();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        return result;
    }

    protected final void storeToArray(final double[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
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
     * Operations on Double2 vectors
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
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return
     */
    public static double length(Double2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Double2 a, Double2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
