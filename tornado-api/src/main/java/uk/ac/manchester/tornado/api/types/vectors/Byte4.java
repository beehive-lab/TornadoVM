/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.types.vectors;

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;

@Vector
public final class Byte4 implements TornadoVectorsInterface<ByteBuffer> {

    public static final Class<Byte4> TYPE = Byte4.class;
    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    final byte[] storage;

    private Byte4(byte[] nativeVectorByte) {
        this.storage = nativeVectorByte;
    }

    public Byte4() {
        this(new byte[NUM_ELEMENTS]);
    }

    public Byte4(byte x, byte y, byte z, byte w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
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
        return TornadoMath.isEqual(a.toArray(), b.toArray());
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
        return toString(NUMBER_FORMAT);
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
        return NUM_ELEMENTS;
    }

    public byte[] toArray() {
        return storage;
    }

    public byte[] getArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 4;
    }

}
