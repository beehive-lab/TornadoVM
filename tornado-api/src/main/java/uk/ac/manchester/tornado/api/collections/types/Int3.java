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
public final class Int3 implements PrimitiveStorage<IntBuffer> {

    public static final Class<Int3> TYPE = Int3.class;

    private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d }";

    /**
     * backing array
     */
    @Payload
    final protected int[] storage;

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

    public int[] getArray() {
        return storage;
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
     * @return {@link Int3}
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
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static Int3 loadFromArray(final int[] array, int index) {
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
        return IntBuffer.wrap(storage);
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
