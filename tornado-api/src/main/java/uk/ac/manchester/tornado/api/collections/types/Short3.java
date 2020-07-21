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

import java.nio.ShortBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

@Vector
public final class Short3 implements PrimitiveStorage<ShortBuffer> {

    public static final Class<Short3> TYPE = Short3.class;

    private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d }";

    /**
     * backing array
     */
    @Payload
    final protected short[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 3;

    public Short3(short[] storage) {
        this.storage = storage;
    }

    public Short3() {
        this(new short[numElements]);
    }

    public Short3(short x, short y, short z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    public void set(Short3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    public short[] getArray() {
        return storage;
    }

    public short get(int index) {
        return storage[index];
    }

    public void set(int index, short value) {
        storage[index] = value;
    }

    public short getX() {
        return get(0);
    }

    public short getY() {
        return get(1);
    }

    public short getZ() {
        return get(2);
    }

    public void setX(short value) {
        set(0, value);
    }

    public void setY(short value) {
        set(1, value);
    }

    public void setZ(short value) {
        set(2, value);
    }

    public Short3 duplicate() {
        Short3 vector = new Short3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static Short3 loadFromArray(final short[] array, int index) {
        final Short3 result = new Short3();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        return result;
    }

    protected final void storeToArray(final short[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return ShortBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Short3 add(Short3 a, Short3 b) {
        return new Short3((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()), (short) (a.getZ() + b.getZ()));
    }

    public static Short3 sub(Short3 a, Short3 b) {
        return new Short3((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()), (short) (a.getZ() - b.getZ()));
    }

    public static Short3 div(Short3 a, Short3 b) {
        return new Short3((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()), (short) (a.getZ() / b.getZ()));
    }

    public static Short3 mult(Short3 a, Short3 b) {
        return new Short3((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()), (short) (a.getZ() * b.getZ()));
    }

    public static Short3 min(Short3 a, Short3 b) {
        return new Short3(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()));
    }

    public static Short3 max(Short3 a, Short3 b) {
        return new Short3(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Short3 add(Short3 a, short b) {
        return new Short3((short) (a.getX() + b), (short) (a.getY() + b), (short) (a.getZ() + b));
    }

    public static Short3 sub(Short3 a, short b) {
        return new Short3((short) (a.getX() - b), (short) (a.getY() - b), (short) (a.getZ() - b));
    }

    public static Short3 mult(Short3 a, short b) {
        return new Short3((short) (a.getX() * b), (short) (a.getY() * b), (short) (a.getZ() * b));
    }

    public static Short3 div(Short3 a, short b) {
        return new Short3((short) (a.getX() / b), (short) (a.getY() / b), (short) (a.getZ() / b));
    }

    public static Short3 inc(Short3 a, short value) {
        return add(a, value);
    }

    public static Short3 dec(Short3 a, short value) {
        return sub(a, value);
    }

    public static Short3 scale(Short3 a, short value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static Short3 clamp(Short3 x, short min, short max) {
        return new Short3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /*
     * vector wide operations
     */
    public static short min(Short3 value) {
        return TornadoMath.min(value.getX(), TornadoMath.min(value.getY(), value.getZ()));
    }

    public static short max(Short3 value) {
        return TornadoMath.max(value.getX(), TornadoMath.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(Short3 a, Short3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
