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
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.common.PrimitiveStorage;
import uk.ac.manchester.tornado.api.types.utils.ShortOps;

@Vector
public final class NativeShort2 implements PrimitiveStorage<ShortBuffer> {

    public static final Class<NativeShort2> TYPE = NativeShort2.class;

    public static final Class<NativeVectorShort> FIELD_CLASS = NativeVectorShort.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    private final NativeVectorShort nativeVectorShort;

    public NativeShort2(NativeVectorShort nativeVectorShort) {
        this.nativeVectorShort = nativeVectorShort;
    }

    public NativeShort2() {
        this(new NativeVectorShort(NUM_ELEMENTS));
    }

    public NativeShort2(short x, short y) {
        this();
        setX(x);
        setY(y);
    }

    /*
     * vector = op( vector, vector )
     */
    public static NativeShort2 add(NativeShort2 a, NativeShort2 b) {
        return new NativeShort2((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()));
    }

    public static NativeShort2 sub(NativeShort2 a, NativeShort2 b) {
        return new NativeShort2((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()));
    }

    public static NativeShort2 div(NativeShort2 a, NativeShort2 b) {
        return new NativeShort2((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()));
    }

    public static NativeShort2 mult(NativeShort2 a, NativeShort2 b) {
        return new NativeShort2((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()));
    }

    public static NativeShort2 min(NativeShort2 a, NativeShort2 b) {
        return new NativeShort2(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()));
    }

    public static NativeShort2 max(NativeShort2 a, NativeShort2 b) {
        return new NativeShort2(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeShort2 add(NativeShort2 a, short b) {
        return new NativeShort2((short) (a.getX() + b), (short) (a.getY() + b));
    }

    public static NativeShort2 sub(NativeShort2 a, short b) {
        return new NativeShort2((short) (a.getX() - b), (short) (a.getY() - b));
    }

    public static NativeShort2 mult(NativeShort2 a, short b) {
        return new NativeShort2((short) (a.getX() * b), (short) (a.getY() * b));
    }

    public static NativeShort2 div(NativeShort2 a, short b) {
        return new NativeShort2((short) (a.getX() / b), (short) (a.getY() / b));
    }

    public static NativeShort2 inc(NativeShort2 a, short value) {
        return add(a, value);
    }

    public static NativeShort2 dec(NativeShort2 a, short value) {
        return sub(a, value);
    }

    public static NativeShort2 scale(NativeShort2 a, short value) {
        return mult(a, value);
    }

    public static NativeShort2 clamp(NativeShort2 x, short min, short max) {
        return new NativeShort2(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static short min(NativeShort2 value) {
        return TornadoMath.min(value.getX(), value.getY());
    }

    public static short max(NativeShort2 value) {
        return TornadoMath.max(value.getX(), value.getY());
    }

    public static boolean isEqual(NativeShort2 a, NativeShort2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public short[] getArray() {
        return nativeVectorShort.getSegment().toArray(ValueLayout.JAVA_SHORT);
    }

    public short get(int index) {
        return nativeVectorShort.get(index);
    }

    public void set(int index, short value) {
        nativeVectorShort.set(index, value);
    }

    public void set(NativeShort2 value) {
        setX(value.getX());
        setY(value.getY());
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

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeShort2}
     */
    public NativeShort2 duplicate() {
        NativeShort2 vector = new NativeShort2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(ShortOps.FMT_2);
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

    static NativeShort2 loadFromArray(final ShortArray array, int index) {
        final NativeShort2 result = new NativeShort2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    void storeToArray(final ShortArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
    }
}
