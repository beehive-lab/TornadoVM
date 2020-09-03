/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

@Vector
public final class Int8 implements PrimitiveStorage<IntBuffer> {

    public static final Class<Int8> TYPE = Int8.class;

    /**
     * backing array
     */
    @Payload
    final protected int[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 8;

    public Int8(int[] storage) {
        this.storage = storage;
    }

    public Int8() {
        this(new int[numElements]);
    }

    public Int8(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int s7) {
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

    public int[] getArray() {
        return storage;
    }

    public int get(int index) {
        return storage[index];
    }

    public void set(int index, int value) {
        storage[index] = value;
    }

    public void set(Int8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
    }

    public int getS0() {
        return get(0);
    }

    public int getS1() {
        return get(1);
    }

    public int getS2() {
        return get(2);
    }

    public int getS3() {
        return get(3);
    }

    public int getS4() {
        return get(4);
    }

    public int getS5() {
        return get(5);
    }

    public int getS6() {
        return get(6);
    }

    public int getS7() {
        return get(7);
    }

    public void setS0(int value) {
        set(0, value);
    }

    public void setS1(int value) {
        set(1, value);
    }

    public void setS2(int value) {
        set(2, value);
    }

    public void setS3(int value) {
        set(3, value);
    }

    public void setS4(int value) {
        set(4, value);
    }

    public void setS5(int value) {
        set(5, value);
    }

    public void setS6(int value) {
        set(6, value);
    }

    public void setS7(int value) {
        set(7, value);
    }

    public Int4 getHigh() {
        return new Int4(getS4(), getS5(), getS6(), getS7());
    }

    public Int4 getLow() {
        return new Int4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector
     *
     * @return {@link Int8}
     */
    public Int8 duplicate() {
        Int8 vector = new Int8();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7());
    }

    @Override
    public String toString() {
        return toString(IntOps.fmt8);
    }

    protected static Int8 loadFromArray(final int[] array, int index) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, array[index + i]);
        }
        return result;
    }

    protected final void storeToArray(final int[] array, int index) {
        for (int i = 0; i < numElements; i++) {
            array[index + i] = get(i);
        }
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {

    }

    @Override
    public IntBuffer asBuffer() {
        return null;
    }

    @Override
    public int size() {
        return numElements;
    }

    /**
     * * Operations on int8 vectors
     */
    public static Int8 add(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Int8 add(Int8 a, int b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Int8 sub(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Int8 sub(Int8 a, int b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Int8 div(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Int8 div(Int8 a, int value) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Int8 mult(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Int8 mult(Int8 a, int value) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Int8 min(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int min(Int8 value) {
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Int8 max(Int8 a, Int8 b) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static int max(Int8 value) {
        int result = Integer.MIN_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Int8 sqrt(Int8 a) {
        final Int8 result = new Int8();
        for (int i = 0; i < numElements; i++) {
            a.set(i, (int) TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static int dot(Int8 a, Int8 b) {
        final Int8 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() + m.getS4() + m.getS5() + m.getS6() + m.getS7();
    }

    public static boolean isEqual(Int8 a, Int8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
