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
package uk.ac.manchester.tornado.api.types.arrays.natives;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;

@Vector
public final class NativeByte4 implements PrimitiveStorage<ByteBuffer> {

    public static final Class<NativeByte4> TYPE = NativeByte4.class;
    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    final NativeVectorByte nativeVectorByte;

    public NativeByte4(NativeVectorByte nativeVectorByte) {
        this.nativeVectorByte = nativeVectorByte;
    }

    public NativeByte4() {
        this(new NativeVectorByte(NUM_ELEMENTS));
    }

    public NativeByte4(byte x, byte y, byte z, byte w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    /*
     * vector = op( vector, vector )
     */
    public static NativeByte4 add(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()), (byte) (a.getZ() + b.getZ()), (byte) (a.getW() + b.getW()));
    }

    public static NativeByte4 sub(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()), (byte) (a.getZ() - b.getZ()), (byte) (a.getW() - b.getW()));
    }

    public static NativeByte4 div(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()), (byte) (a.getZ() / b.getZ()), (byte) (a.getW() / b.getW()));
    }

    public static NativeByte4 mult(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()), (byte) (a.getZ() * b.getZ()), (byte) (a.getW() * b.getW()));
    }

    public static NativeByte4 min(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()), TornadoMath.min(a.getW(), b.getW()));
    }

    public static NativeByte4 max(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()), TornadoMath.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeByte4 add(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b), (byte) (a.getW() + b));
    }

    public static NativeByte4 sub(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b), (byte) (a.getW() - b));
    }

    public static NativeByte4 mult(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b), (byte) (a.getW() * b));
    }

    public static NativeByte4 div(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b), (byte) (a.getW() / b));
    }

    public static NativeByte4 inc(NativeByte4 a, byte value) {
        return add(a, value);
    }

    public static NativeByte4 dec(NativeByte4 a, byte value) {
        return sub(a, value);
    }

    public static NativeByte4 scale(NativeByte4 a, byte value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static NativeByte4 clamp(NativeByte4 x, byte min, byte max) {
        return new NativeByte4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    /*
     * vector wide operations
     */
    public static byte min(NativeByte4 value) {
        return TornadoMath.min(TornadoMath.min(value.getX(), value.getY()), TornadoMath.min(value.getZ(), value.getW()));
    }

    public static byte max(NativeByte4 value) {
        return TornadoMath.max(TornadoMath.max(value.getX(), value.getY()), TornadoMath.max(value.getZ(), value.getW()));
    }

    public static boolean isEqual(NativeByte4 a, NativeByte4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    static NativeByte4 loadFromArray(final ByteArray array, int index) {
        final NativeByte4 result = new NativeByte4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    public void set(NativeByte4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
    }

    public byte get(int index) {
        return nativeVectorByte.get(index);
    }

    public void set(int index, byte value) {
        nativeVectorByte.set(index, value);
    }

    public byte getX() {
        return get(0);
    }

    public void setX(byte value) {
        set(0, value);
    }

    public byte getY() {
        return get(1);
    }

    public void setY(byte value) {
        set(1, value);
    }

    public byte getZ() {
        return get(2);
    }

    public void setZ(byte value) {
        set(2, value);
    }

    public byte getW() {
        return get(3);
    }

    public void setW(byte value) {
        set(3, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeByte4}
     */
    public NativeByte4 duplicate() {
        NativeByte4 vector = new NativeByte4();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(NUMBER_FORMAT);
    }

    @Override
    public void loadFromBuffer(ByteBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ByteBuffer asBuffer() {
        return nativeVectorByte.getSegment().asByteBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    void storeToArray(ByteArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
        array.set(index + 2, getZ());
        array.set(index + 3, getW());
    }

}
