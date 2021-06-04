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
public final class Double8 implements PrimitiveStorage<DoubleBuffer> {

    public static final Class<Double8> TYPE = Double8.class;

    /**
     * backing array
     */
    @Payload
    final protected double[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 8;

    protected Double8(double[] storage) {
        this.storage = storage;
    }

    public Double8() {
        this(new double[numElements]);
    }

    public Double8(double s0, double s1, double s2, double s3, double s4, double s5, double s6, double s7) {
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

    public double[] getArray() {
        return storage;
    }

    public double get(int index) {
        return storage[index];
    }

    public void set(int index, double value) {
        storage[index] = value;
    }

    public void set(Double8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
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

    public double getS6() {
        return get(6);
    }

    public double getS7() {
        return get(7);
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

    public void setS6(double value) {
        set(6, value);
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
     * Duplicates this vector
     *
     * @return {@link Double8}
     */
    public Double8 duplicate() {
        Double8 vector = new Double8();
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

    protected static Double8 loadFromArray(final double[] array, int index) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, array[index + i]);
        }
        return result;
    }

    protected final void storeToArray(final double[] array, int index) {
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
        return DoubleBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /**
     * * Operations on Double8 vectors
     */
    public static Double8 add(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Double8 add(Double8 a, double b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Double8 sub(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Double8 sub(Double8 a, double b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Double8 div(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Double8 div(Double8 a, double value) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Double8 mult(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Double8 mult(Double8 a, double value) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Double8 min(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double min(Double8 value) {
        double result = Double.MAX_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Double8 max(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double max(Double8 value) {
        double result = Double.MIN_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Double8 sqrt(Double8 a) {
        final Double8 result = new Double8();
        for (int i = 0; i < numElements; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static boolean isEqual(Double8 a, Double8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public static double findULPDistance(Double8 value, Double8 expected) {
        return TornadoMath.findULPDistance(value.asBuffer().array(), expected.asBuffer().array());
    }
}
