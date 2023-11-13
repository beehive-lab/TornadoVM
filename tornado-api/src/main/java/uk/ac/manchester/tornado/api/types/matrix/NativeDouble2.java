/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;

@Vector
public class NativeDouble2 implements PrimitiveStorage<DoubleBuffer> {

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
