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

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

@Vector
public final class Int4 implements PrimitiveStorage<IntBuffer> {

    public static final Class<Int4> TYPE = Int4.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";

    /**
     * backing array
     */
    @Payload
    protected final int[] storage;

    /**
     * number of elements in the storage
     */
    static final private int NUM_ELEMENTS = 4;

    public Int4(int[] storage) {
        this.storage = storage;
    }

    public Int4() {
        this(new int[NUM_ELEMENTS]);
    }

    public Int4(int x, int y, int z, int w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
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

    public void set(Int4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
    }

    public int getX() {
        return get(0);
    }

    public int getY() {
        return get(1);
    }

    public int getZ() {
        return get(2);
    }

    public int getW() {
        return get(3);
    }

    public void setX(int value) {
        set(0, value);
    }

    public void setY(int value) {
        set(1, value);
    }

    public void setZ(int value) {
        set(2, value);
    }

    public void setW(int value) {
        set(3, value);
    }

    /**
     * Duplicates this vector
     *
     * @return {@link Int4}
     */
    public Int4 duplicate() {
        Int4 vector = new Int4();
        vector.set(this);
        return vector;
    }

    public Int2 asInt2() {
        return new Int2(getX(), getY());
    }

    public Int3 asInt3() {
        return new Int3(getX(), getY(), getZ());
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(NUMBER_FORMAT);
    }

    protected static Int4 loadFromArray(final int[] array, int index) {
        final Int4 result = new Int4();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        result.setW(array[index + 3]);
        return result;
    }

    protected final void storeToArray(final int[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
        array[index + 3] = getW();
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return IntBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Int4 add(Int4 a, Int4 b) {
        return new Int4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Int4 sub(Int4 a, Int4 b) {
        return new Int4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Int4 div(Int4 a, Int4 b) {
        return new Int4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Int4 mult(Int4 a, Int4 b) {
        return new Int4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Int4 min(Int4 a, Int4 b) {
        return new Int4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Int4 max(Int4 a, Int4 b) {
        return new Int4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int4 add(Int4 a, int b) {
        return new Int4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static Int4 sub(Int4 a, int b) {
        return new Int4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static Int4 mult(Int4 a, int b) {
        return new Int4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static Int4 div(Int4 a, int b) {
        return new Int4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static Int4 inc(Int4 a, int value) {
        return add(a, value);
    }

    public static Int4 dec(Int4 a, int value) {
        return sub(a, value);
    }

    public static Int4 scale(Int4 a, int value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static Int4 clamp(Int4 x, int min, int max) {
        return new Int4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static int max(Int4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static boolean isEqual(Int4 a, Int4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
