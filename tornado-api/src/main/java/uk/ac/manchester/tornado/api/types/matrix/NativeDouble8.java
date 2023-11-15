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
import uk.ac.manchester.tornado.api.types.vectors.Double4;
import uk.ac.manchester.tornado.api.types.vectors.Double8;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;

@Vector
public class NativeDouble8 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<NativeDouble8> TYPE = NativeDouble8.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorDouble nativeVectorDouble;

    private NativeDouble8(NativeVectorDouble nativeVectorDouble) {
        this.nativeVectorDouble = nativeVectorDouble;
    }

    public NativeDouble8() {
        this(new NativeVectorDouble(NUM_ELEMENTS));
    }

    public NativeDouble8(double s0, double s1, double s2, double s3, double s4, double s5, double s6, double s7) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
        setS6(s6);
        setS7(s7);
    }

    /**
     * * Operations on Double8 vectors.
     */
    public static NativeDouble8 add(NativeDouble8 a, NativeDouble8 b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static NativeDouble8 add(NativeDouble8 a, double b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static NativeDouble8 sub(NativeDouble8 a, NativeDouble8 b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static NativeDouble8 sub(NativeDouble8 a, double b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static NativeDouble8 div(NativeDouble8 a, NativeDouble8 b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static NativeDouble8 div(NativeDouble8 a, double value) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static NativeDouble8 mult(NativeDouble8 a, NativeDouble8 b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static NativeDouble8 mult(NativeDouble8 a, double value) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static NativeDouble8 min(NativeDouble8 a, NativeDouble8 b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double min(NativeDouble8 value) {
        double result = Double.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static NativeDouble8 max(NativeDouble8 a, NativeDouble8 b) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double max(NativeDouble8 value) {
        double result = Double.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static NativeDouble8 sqrt(NativeDouble8 a) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static boolean isEqual(NativeDouble8 a, NativeDouble8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static double findULPDistance(NativeDouble8 value, NativeDouble8 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public double get(int index) {
        return nativeVectorDouble.get(index);
    }

    public void set(int index, double value) {
        nativeVectorDouble.set(index, value);
    }

    public void set(NativeDouble8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
    }

    public double getS0() {
        return get(0);
    }

    public void setS0(double value) {
        set(0, value);
    }

    public double getS1() {
        return get(1);
    }

    public void setS1(double value) {
        set(1, value);
    }

    public double getS2() {
        return get(2);
    }

    public void setS2(double value) {
        set(2, value);
    }

    public double getS3() {
        return get(3);
    }

    public void setS3(double value) {
        set(3, value);
    }

    public double getS4() {
        return get(4);
    }

    public void setS4(double value) {
        set(4, value);
    }

    public double getS5() {
        return get(5);
    }

    public void setS5(double value) {
        set(5, value);
    }

    public double getS6() {
        return get(6);
    }

    public void setS6(double value) {
        set(6, value);
    }

    public double getS7() {
        return get(7);
    }

    public void setS7(double value) {
        set(7, value);
    }

    public Double4 getHigh() {
        return new Double4(getS4(), getS5(), getS6(), getS7());
    }

    public Double4 getLow() {
        return new Double4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Double8}
     */
    public NativeDouble8 duplicate() {
        NativeDouble8 vector = new NativeDouble8();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7());
    }

    @Override
    public String toString() {
        return toString(DoubleOps.FMT_8);
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return nativeVectorDouble.getSegment().asByteBuffer().asDoubleBuffer();
    }

    public void fill(double value) {
        for (int i = 0; i < nativeVectorDouble.getSize(); i++) {
            nativeVectorDouble.set(i, value);
        }
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    static NativeDouble8 loadFromArray(final DoubleArray array, int index) {
        final NativeDouble8 result = new NativeDouble8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, array.get(index + i));
        }
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
