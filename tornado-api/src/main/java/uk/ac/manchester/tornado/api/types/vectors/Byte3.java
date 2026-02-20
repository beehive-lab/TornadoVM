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
public final class Byte3 implements TornadoVectorsInterface<ByteBuffer> {

    public static final Class<Byte3> TYPE = Byte3.class;
    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d }";

    private static final int NUM_ELEMENTS = 3;

    @Payload
    private final byte[] storage;

    private Byte3(byte[] nativeVectorByte) {
        this.storage = nativeVectorByte;
    }

    public Byte3() {
        this(new byte[NUM_ELEMENTS]);
    }

    public Byte3(byte x, byte y, byte z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /*
     * vector = op( vector, vector )
     */
    public static Byte3 add(Byte3 a, Byte3 b) {
        return new Byte3((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()), (byte) (a.getZ() + b.getZ()));
    }

    public static Byte3 sub(Byte3 a, Byte3 b) {
        return new Byte3((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()), (byte) (a.getZ() - b.getZ()));
    }

    public static Byte3 div(Byte3 a, Byte3 b) {
        return new Byte3((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()), (byte) (a.getZ() / b.getZ()));
    }

    public static Byte3 mult(Byte3 a, Byte3 b) {
        return new Byte3((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()), (byte) (a.getZ() * b.getZ()));
    }

    public static Byte3 min(Byte3 a, Byte3 b) {
        return new Byte3(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()));
    }

    public static Byte3 max(Byte3 a, Byte3 b) {
        return new Byte3(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Byte3 add(Byte3 a, byte b) {
        return new Byte3((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b));
    }

    public static Byte3 sub(Byte3 a, byte b) {
        return new Byte3((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b));
    }

    public static Byte3 mult(Byte3 a, byte b) {
        return new Byte3((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b));
    }

    public static Byte3 div(Byte3 a, byte b) {
        return new Byte3((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b));
    }

    public static Byte3 inc(Byte3 a, byte value) {
        return add(a, value);
    }

    public static Byte3 dec(Byte3 a, byte value) {
        return sub(a, value);
    }

    public static Byte3 scale(Byte3 a, byte value) {
        return mult(a, value);
    }

    public static Byte3 clamp(Byte3 x, byte min, byte max) {
        return new Byte3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    public static byte min(Byte3 value) {
        return TornadoMath.min(value.getX(), TornadoMath.min(value.getY(), value.getZ()));
    }

    public static byte max(Byte3 value) {
        return TornadoMath.max(value.getX(), TornadoMath.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(Byte3 a, Byte3 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public void set(Byte3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
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

    /**
     * Duplicates this vector.
     *
     * @return {@Byte3}
     */
    public Byte3 duplicate() {
        final Byte3 vector = new Byte3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

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
        return NUM_ELEMENTS;
    }

}
