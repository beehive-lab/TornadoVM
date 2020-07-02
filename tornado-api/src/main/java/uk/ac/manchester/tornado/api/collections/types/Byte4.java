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

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.type.annotations.Payload;
import uk.ac.manchester.tornado.api.type.annotations.Vector;

@Vector
public final class Byte4 implements PrimitiveStorage<ByteBuffer> {

    private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";

    public static final Class<Byte4> TYPE = Byte4.class;

    /**
     * backing array
     */
    @Payload
    final protected byte[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 4;

    public Byte4(byte[] storage) {
        this.storage = storage;
    }

    public Byte4() {
        this(new byte[numElements]);
    }

    public Byte4(byte x, byte y, byte z, byte w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    public byte[] getArray() {
        return storage;
    }

    public void set(Byte4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
    }

    public byte get(int index) {
        return storage[index];
    }

    public void set(int index, byte value) {
        storage[index] = value;
    }

    public byte getX() {
        return get(0);
    }

    public byte getY() {
        return get(1);
    }

    public byte getZ() {
        return get(2);
    }

    public byte getW() {
        return get(3);
    }

    public void setX(byte value) {
        set(0, value);
    }

    public void setY(byte value) {
        set(1, value);
    }

    public void setZ(byte value) {
        set(2, value);
    }

    public void setW(byte value) {
        set(3, value);
    }

    /**
     * Duplicates this vector
     *
     * @return {@link Byte4}
     */
    public Byte4 duplicate() {
        Byte4 vector = new Byte4();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static Byte4 loadFromArray(final byte[] array, int index) {
        final Byte4 result = new Byte4();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        result.setW(array[index + 3]);
        return result;
    }

    protected final void storeToArray(final byte[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
        array[index + 3] = getW();
    }

    @Override
    public void loadFromBuffer(ByteBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ByteBuffer asBuffer() {
        return ByteBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Byte4 add(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()), (byte) (a.getZ() + b.getZ()), (byte) (a.getW() + b.getW()));
    }

    public static Byte4 sub(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()), (byte) (a.getZ() - b.getZ()), (byte) (a.getW() - b.getW()));
    }

    public static Byte4 div(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()), (byte) (a.getZ() / b.getZ()), (byte) (a.getW() / b.getW()));
    }

    public static Byte4 mult(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()), (byte) (a.getZ() * b.getZ()), (byte) (a.getW() * b.getW()));
    }

    public static Byte4 min(Byte4 a, Byte4 b) {
        return new Byte4(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()), TornadoMath.min(a.getW(), b.getW()));
    }

    public static Byte4 max(Byte4 a, Byte4 b) {
        return new Byte4(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()), TornadoMath.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Byte4 add(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b), (byte) (a.getW() + b));
    }

    public static Byte4 sub(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b), (byte) (a.getW() - b));
    }

    public static Byte4 mult(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b), (byte) (a.getW() * b));
    }

    public static Byte4 div(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b), (byte) (a.getW() / b));
    }

    public static Byte4 inc(Byte4 a, byte value) {
        return add(a, value);
    }

    public static Byte4 dec(Byte4 a, byte value) {
        return sub(a, value);
    }

    public static Byte4 scale(Byte4 a, byte value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static Byte4 clamp(Byte4 x, byte min, byte max) {
        return new Byte4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    /*
     * vector wide operations
     */
    public static byte min(Byte4 value) {
        return TornadoMath.min(TornadoMath.min(value.getX(), value.getY()), TornadoMath.min(value.getZ(), value.getW()));
    }

    public static byte max(Byte4 value) {
        return TornadoMath.max(TornadoMath.max(value.getX(), value.getY()), TornadoMath.max(value.getZ(), value.getW()));
    }

    public static boolean isEqual(Byte4 a, Byte4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
