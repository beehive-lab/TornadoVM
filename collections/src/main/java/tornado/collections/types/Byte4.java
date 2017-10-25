/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.collections.types;

import java.nio.ByteBuffer;
import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

import static java.lang.String.format;
import static java.nio.ByteBuffer.wrap;

/**
 * Class that represents a vector of 3x bytes e.g. <byte,byte,byte>
 *
 * @author jamesclarkson
 */
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
     * @return
     */
    public Byte4 duplicate() {
        Byte4 vector = new Byte4();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static final Byte4 loadFromArray(final byte[] array, int index) {
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
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Byte4 add(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()),
                (byte) (a.getZ() + b.getZ()), (byte) (a.getW() + b.getW()));
    }

    public static Byte4 sub(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()),
                (byte) (a.getZ() - b.getZ()), (byte) (a.getW() - b.getW()));
    }

    public static Byte4 div(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()),
                (byte) (a.getZ() / b.getZ()), (byte) (a.getW() / b.getW()));
    }

    public static Byte4 mult(Byte4 a, Byte4 b) {
        return new Byte4((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()),
                (byte) (a.getZ() * b.getZ()), (byte) (a.getW() * b.getW()));
    }

    public static Byte4 min(Byte4 a, Byte4 b) {
        return new Byte4(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()),
                TornadoMath.min(a.getZ(), b.getZ()), TornadoMath.min(a.getW(), b.getW()));
    }

    public static Byte4 max(Byte4 a, Byte4 b) {
        return new Byte4(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()),
                TornadoMath.max(a.getZ(), b.getZ()), TornadoMath.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Byte4 add(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b),
                (byte) (a.getW() + b));
    }

    public static Byte4 sub(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b),
                (byte) (a.getW() - b));
    }

    public static Byte4 mult(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b),
                (byte) (a.getW() * b));
    }

    public static Byte4 div(Byte4 a, byte b) {
        return new Byte4((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b),
                (byte) (a.getW() / b));
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
        return new Byte4(
                TornadoMath.clamp(x.getX(), min, max),
                TornadoMath.clamp(x.getY(), min, max),
                TornadoMath.clamp(x.getZ(), min, max),
                TornadoMath.clamp(x.getW(), min, max));
    }

    /*
     * vector wide operations
     */
    public static byte min(Byte4 value) {
        return TornadoMath.min(TornadoMath.min(value.getX(), value.getY()),
                TornadoMath.min(value.getZ(), value.getW()));
    }

    public static byte max(Byte4 value) {
        return TornadoMath.max(TornadoMath.max(value.getX(), value.getY()),
                TornadoMath.max(value.getZ(), value.getW()));
    }

    public static boolean isEqual(Byte4 a, Byte4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
