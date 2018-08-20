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
import static java.nio.IntBuffer.wrap;

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.annotations.Payload;
import uk.ac.manchester.tornado.api.annotations.Vector;
import uk.ac.manchester.tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 2x ints e.g. <int,int>
 *
 * @author jamesclarkson
 *
 */
@Vector
public final class Int2 implements PrimitiveStorage<IntBuffer> {

    public static final Class<Int2> TYPE = Int2.class;

    private static final String numberFormat = "{ x=%-7d, y=%-7d }";

    /**
     * backing array
     */
    @Payload
    final protected int[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 2;

    public Int2(int[] storage) {
        this.storage = storage;
    }

    public Int2() {
        this(new int[numElements]);
    }

    public Int2(int x, int y) {
        this();
        setX(x);
        setY(y);
    }

    public int get(int index) {
        return storage[index];
    }

    public void set(int index, int value) {
        storage[index] = value;
    }

    public void set(Int2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    public int getX() {
        return get(0);
    }

    public int getY() {
        return get(1);
    }

    public int getS0() {
        return get(0);
    }

    public int getS1() {
        return get(1);
    }

    public void setX(int value) {
        set(0, value);
    }

    public void setY(int value) {
        set(1, value);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Int2 duplicate() {
        Int2 vector = new Int2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static final Int2 loadFromArray(final int[] array, int index) {
        final Int2 result = new Int2();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        return result;
    }

    protected final void storeToArray(final int[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
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

    /**
     * *
     * Operations on Int2 vectors
     */
    /*
     * vector = op( vector, vector )
     */
    public static Int2 add(Int2 a, Int2 b) {
        return new Int2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static Int2 sub(Int2 a, Int2 b) {
        return new Int2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static Int2 div(Int2 a, Int2 b) {
        return new Int2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static Int2 mult(Int2 a, Int2 b) {
        return new Int2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static Int2 min(Int2 a, Int2 b) {
        return new Int2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static Int2 max(Int2 a, Int2 b) {
        return new Int2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int2 add(Int2 a, int b) {
        return new Int2(a.getX() + b, a.getY() + b);
    }

    public static Int2 sub(Int2 a, int b) {
        return new Int2(a.getX() - b, a.getY() - b);
    }

    public static Int2 mult(Int2 a, int b) {
        return new Int2(a.getX() * b, a.getY() * b);
    }

    public static Int2 div(Int2 a, int b) {
        return new Int2(a.getX() / b, a.getY() / b);
    }

    public static Int2 inc(Int2 a, int value) {
        return add(a, value);
    }

    public static Int2 dec(Int2 a, int value) {
        return sub(a, value);
    }

    public static Int2 scaleByInverse(Int2 a, int value) {
        return mult(a, 1 / value);
    }

    public static Int2 scale(Int2 a, int value) {
        return mult(a, value);
    }

    public static Int2 clamp(Int2 x, int min, int max) {
        return new Int2(
                TornadoMath.clamp(x.getX(), min, max),
                TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static int max(Int2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static int dot(Int2 a, Int2 b) {
        final Int2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    public static boolean isEqual(Int2 a, Int2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
