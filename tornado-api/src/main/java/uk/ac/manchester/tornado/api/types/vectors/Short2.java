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
import uk.ac.manchester.tornado.api.types.utils.ShortOps;

@Vector
public final class Short2 implements TornadoVectorsInterface<ShortBuffer> {

    public static final Class<Short2> TYPE = Short2.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    private final short[] storage;

    private Short2(short[] nativeVectorShort) {
        this.storage = nativeVectorShort;
    }

    public Short2() {
        this(new short[NUM_ELEMENTS]);
    }

    public Short2(short x, short y) {
        this();
        setX(x);
        setY(y);
    }

    /*
     * vector = op( vector, vector )
     */
    public static Short2 add(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()));
    }

    public static Short2 sub(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()));
    }

    public static Short2 div(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()));
    }

    public static Short2 mult(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()));
    }

    public static Short2 min(Short2 a, Short2 b) {
        return new Short2(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()));
    }

    public static Short2 max(Short2 a, Short2 b) {
        return new Short2(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Short2 add(Short2 a, short b) {
        return new Short2((short) (a.getX() + b), (short) (a.getY() + b));
    }

    public static Short2 sub(Short2 a, short b) {
        return new Short2((short) (a.getX() - b), (short) (a.getY() - b));
    }

    public static Short2 mult(Short2 a, short b) {
        return new Short2((short) (a.getX() * b), (short) (a.getY() * b));
    }

    public static Short2 div(Short2 a, short b) {
        return new Short2((short) (a.getX() / b), (short) (a.getY() / b));
    }

    public static Short2 inc(Short2 a, short value) {
        return add(a, value);
    }

    public static Short2 dec(Short2 a, short value) {
        return sub(a, value);
    }

    public static Short2 scale(Short2 a, short value) {
        return mult(a, value);
    }

    public static Short2 clamp(Short2 x, short min, short max) {
        return new Short2(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static short min(Short2 value) {
        return TornadoMath.min(value.getX(), value.getY());
    }

    public static short max(Short2 value) {
        return TornadoMath.max(value.getX(), value.getY());
    }

    public static boolean isEqual(Short2 a, Short2 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public short get(int index) {
        return storage[index];
    }

    public void set(int index, short value) {
        storage[index] = value;
    }

    public void set(Short2 value) {
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
     * @return {@link Short2}
     */
    public Short2 duplicate() {
        Short2 vector = new Short2();
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
