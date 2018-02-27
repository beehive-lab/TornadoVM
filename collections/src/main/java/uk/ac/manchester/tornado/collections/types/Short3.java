/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
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
package uk.ac.manchester.tornado.collections.types;

import java.nio.ShortBuffer;
import tornado.api.Payload;
import tornado.api.Vector;
import uk.ac.manchester.tornado.collections.math.TornadoMath;

import static java.lang.String.format;
import static java.nio.ShortBuffer.wrap;

/**
 * Class that represents a vector of 3x shorts e.g. <short,short,short>
 *
 * @author jamesclarkson
 *
 */
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

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Short3 duplicate() {
        Short3 vector = new Short3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static final Short3 loadFromArray(final short[] array, int index) {
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
        return wrap(storage);
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
        return new Short3(
                TornadoMath.clamp(x.getX(), min, max),
                TornadoMath.clamp(x.getY(), min, max),
                TornadoMath.clamp(x.getZ(), min, max));
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
