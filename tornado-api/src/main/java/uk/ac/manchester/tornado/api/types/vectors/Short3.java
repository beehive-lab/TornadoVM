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

import java.nio.ShortBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;

@Vector
public final class Short3 implements TornadoVectorsInterface<ShortBuffer> {

    public static final Class<Short3> TYPE = Short3.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    private final short[] storage;

    private Short3(short[] nativeVectorShort) {
        this.storage = nativeVectorShort;
    }

    public Short3() {
        this(new short[NUM_ELEMENTS]);
    }

    public Short3(short x, short y, short z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
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
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public void set(Short3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
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
        return toString(NUMBER_FORMAT);
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
        return NUM_ELEMENTS;
    }

    public short[] toArray() {
        return storage;
    }

    public short[] getArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 2;
    }

}
