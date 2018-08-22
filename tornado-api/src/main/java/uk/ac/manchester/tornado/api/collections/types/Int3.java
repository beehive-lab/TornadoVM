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
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.String.format;
import static java.nio.IntBuffer.wrap;

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

/**
 * Class that represents a vector of 3x ints e.g. <int,int,int>
 *
 * @author jamesclarkson
 *
 */
@Vector
public final class Int3 implements PrimitiveStorage<IntBuffer> {

    public static final Class<Int3> TYPE = Int3.class;

    private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d }";

    /**
     * backing array
     */
    @Payload final protected int[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 3;

    public Int3(int[] storage) {
        this.storage = storage;
    }

    public Int3() {
        this(new int[numElements]);
    }

    public Int3(int x, int y, int z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    public int get(int index) {
        return storage[index];
    }

    public void set(int index, int value) {
        storage[index] = value;
    }

    public void set(Int3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
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

    public void setX(int value) {
        set(0, value);
    }

    public void setY(int value) {
        set(1, value);
    }

    public void setZ(int value) {
        set(2, value);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Int3 duplicate() {
        Int3 vector = new Int3();
        vector.set(this);
        return vector;
    }

    public Int2 asInt2() {
        return new Int2(getX(), getY());
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static final Int3 loadFromArray(final int[] array, int index) {
        final Int3 result = new Int3();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        return result;
    }

    protected final void storeToArray(final int[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Int3 add(Int3 a, Int3 b) {
        return new Int3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static Int3 sub(Int3 a, Int3 b) {
        return new Int3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static Int3 div(Int3 a, Int3 b) {
        return new Int3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static Int3 mult(Int3 a, Int3 b) {
        return new Int3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static Int3 min(Int3 a, Int3 b) {
        return new Int3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static Int3 max(Int3 a, Int3 b) {
        return new Int3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int3 add(Int3 a, int b) {
        return new Int3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static Int3 sub(Int3 a, int b) {
        return new Int3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static Int3 mult(Int3 a, int b) {
        return new Int3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static Int3 div(Int3 a, int b) {
        return new Int3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static Int3 inc(Int3 a, int value) {
        return add(a, value);
    }

    public static Int3 dec(Int3 a, int value) {
        return sub(a, value);
    }

    public static Int3 scale(Int3 a, int value) {
        return mult(a, value);
    }

    public static Int3 clamp(Int3 x, int min, int max) {
        return new Int3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static int max(Int3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(Int3 a, Int3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
