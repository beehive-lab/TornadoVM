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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.types.matrix;

import java.lang.foreign.ValueLayout;
import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.types.arrays.natives.NativeVectorDouble;
import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.vectors.Double2;
import uk.ac.manchester.tornado.api.types.vectors.Double3;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;

@Vector
public class NativeDouble3 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<NativeDouble3> TYPE = NativeDouble3.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorDouble nativeVectorDouble;

    public NativeDouble3(NativeVectorDouble nativeVectorDouble) {
        this.nativeVectorDouble = nativeVectorDouble;
    }

    public NativeDouble3() {
        this(new NativeVectorDouble(NUM_ELEMENTS));
    }

    public NativeDouble3(double x, double y, double z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /**
     * * Operations on Double3 vectors.
     */
    public static NativeDouble3 add(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static NativeDouble3 sub(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static NativeDouble3 div(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static NativeDouble3 mult(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static NativeDouble3 min(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static NativeDouble3 max(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static NativeDouble3 cross(NativeDouble3 a, NativeDouble3 b) {
        return new NativeDouble3(a.getY() * b.getZ() - a.getZ() * b.getY(), a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeDouble3 add(NativeDouble3 a, double b) {
        return new NativeDouble3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static NativeDouble3 sub(NativeDouble3 a, double b) {
        return new NativeDouble3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static NativeDouble3 mult(NativeDouble3 a, double b) {
        return new NativeDouble3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static NativeDouble3 div(NativeDouble3 a, double b) {
        return new NativeDouble3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static NativeDouble3 inc(NativeDouble3 a, double value) {
        return new NativeDouble3(a.getX() + value, a.getY() + value, a.getZ() + value);
    }

    public static NativeDouble3 dec(NativeDouble3 a, double value) {
        return new NativeDouble3(a.getX() - value, a.getY() - value, a.getZ() - value);
    }

    public static NativeDouble3 scaleByInverse(NativeDouble3 a, double value) {
        return mult(a, 1f / value);
    }

    public static NativeDouble3 scale(NativeDouble3 a, double value) {
        return mult(a, value);
    }

    /*
     * vector = op(vector)
     */
    public static NativeDouble3 sqrt(NativeDouble3 a) {
        return new NativeDouble3(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()));
    }

    public static NativeDouble3 floor(NativeDouble3 a) {
        return new NativeDouble3(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()));
    }

    public static NativeDouble3 fract(NativeDouble3 a) {
        return new NativeDouble3(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()));
    }

    /*
     * misc inplace vector ops
     */
    public static NativeDouble3 clamp(NativeDouble3 x, double min, double max) {
        return new NativeDouble3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    public static NativeDouble3 normalise(NativeDouble3 value) {
        final double len = 1f / length(value);
        return mult(value, len);
    }

    /*
     * vector wide operations
     */
    public static double min(NativeDouble3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static double max(NativeDouble3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static double dot(NativeDouble3 a, NativeDouble3 b) {
        final NativeDouble3 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ();
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return {@link double}
     */
    public static double length(NativeDouble3 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    public static boolean isEqual(NativeDouble3 a, NativeDouble3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static boolean isEqualULP(NativeDouble3 a, NativeDouble3 b, double numULP) {
        return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
    }

    public static double findULPDistance(NativeDouble3 a, NativeDouble3 b) {
        return TornadoMath.findULPDistance(a.toArray(), b.toArray());
    }

    public double get(int index) {
        return nativeVectorDouble.get(index);
    }

    public void set(int index, double value) {
        nativeVectorDouble.set(index, value);
    }

    public void set(NativeDouble3 value) {
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
    public NativeDouble3 duplicate() {
        final NativeDouble3 vector = new NativeDouble3();
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

    static Double3 loadFromArray(final DoubleArray array, int index) {
        final Double3 result = new Double3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
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
