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

import java.lang.foreign.ValueLayout;
import java.nio.ShortBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;

@Vector
public final class NativeShort3 implements PrimitiveStorage<ShortBuffer> {

    public static final Class<NativeShort3> TYPE = NativeShort3.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    private final NativeVectorShort nativeVectorShort;

    public NativeShort3(NativeVectorShort nativeVectorShort) {
        this.nativeVectorShort = nativeVectorShort;
    }

    public NativeShort3() {
        this(new NativeVectorShort(NUM_ELEMENTS));
    }

    public NativeShort3(short x, short y, short z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /*
     * vector = op( vector, vector )
     */
    public static NativeShort3 add(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()), (short) (a.getZ() + b.getZ()));
    }

    public static NativeShort3 sub(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()), (short) (a.getZ() - b.getZ()));
    }

    public static NativeShort3 div(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()), (short) (a.getZ() / b.getZ()));
    }

    public static NativeShort3 mult(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()), (short) (a.getZ() * b.getZ()));
    }

    public static NativeShort3 min(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()));
    }

    public static NativeShort3 max(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeShort3 add(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() + b), (short) (a.getY() + b), (short) (a.getZ() + b));
    }

    public static NativeShort3 sub(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() - b), (short) (a.getY() - b), (short) (a.getZ() - b));
    }

    public static NativeShort3 mult(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() * b), (short) (a.getY() * b), (short) (a.getZ() * b));
    }

    public static NativeShort3 div(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() / b), (short) (a.getY() / b), (short) (a.getZ() / b));
    }

    public static NativeShort3 inc(NativeShort3 a, short value) {
        return add(a, value);
    }

    public static NativeShort3 dec(NativeShort3 a, short value) {
        return sub(a, value);
    }

    public static NativeShort3 scale(NativeShort3 a, short value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static NativeShort3 clamp(NativeShort3 x, short min, short max) {
        return new NativeShort3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /*
     * vector wide operations
     */
    public static short min(NativeShort3 value) {
        return TornadoMath.min(value.getX(), TornadoMath.min(value.getY(), value.getZ()));
    }

    public static short max(NativeShort3 value) {
        return TornadoMath.max(value.getX(), TornadoMath.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(NativeShort3 a, NativeShort3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public void set(NativeShort3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    public short get(int index) {
        return nativeVectorShort.get(index);
    }

    public void set(int index, short value) {
        nativeVectorShort.set(index, value);
    }

    public short getX() {
        return get(0);
    }

    public void setX(short value) {
        set(0, value);
    }

    public short getY() {
        return get(1);
    }

    public void setY(short value) {
        set(1, value);
    }

    public short getZ() {
        return get(2);
    }

    public void setZ(short value) {
        set(2, value);
    }

    public NativeShort3 duplicate() {
        NativeShort3 vector = new NativeShort3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(NUMBER_FORMAT);
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return nativeVectorShort.getSegment().asByteBuffer().asShortBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public short[] toArray() {
        return nativeVectorShort.getSegment().toArray(ValueLayout.JAVA_SHORT);
    }

    private static NativeShort3 loadFromArray(final short[] array, int index) {
        final NativeShort3 result = new NativeShort3();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        return result;
    }

    private void storeToArray(final short[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
    }

}
