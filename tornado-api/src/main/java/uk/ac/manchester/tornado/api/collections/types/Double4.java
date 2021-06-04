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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.types;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

@Vector
public final class Double4 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<Double4> TYPE = Double4.class;

    /**
     * backing array
     */
    @Payload
    final protected double[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 4;

    public Double4(double[] storage) {
        this.storage = storage;
    }

    public Double4() {
        this(new double[numElements]);
    }

    public double get(int index) {
        return storage[index];
    }

    public void set(int index, double value) {
        storage[index] = value;
    }

    public Double4(double x, double y, double z, double w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
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

    public double getY() {
        return get(1);
    }

    public double getZ() {
        return get(2);
    }

    public double getW() {
        return get(3);
    }

    public void setX(double value) {
        set(0, value);
    }

    public void setY(double value) {
        set(1, value);
    }

    public void setZ(double value) {
        set(2, value);
    }

    public void setW(double value) {
        set(3, value);
    }

    /**
     * Duplicates this vector
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
     * Cast vector into a Double2
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

    protected static Double4 loadFromArray(final double[] array, int index) {
        final Double4 result = new Double4();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        result.setW(array[index + 3]);
        return result;
    }

    protected final void storeToArray(final double[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
        array[index + 3] = getW();
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
        return numElements;
    }

    public void fill(double value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }

    }

    /**
     * * Operations on Double4 vectors
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
     * Returns the vector length e.g. the sqrt of all elements squared
     *
     * @return {@link double}
     */
    public static double length(Double4 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(Double4 a, Double4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
