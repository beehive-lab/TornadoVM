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

import java.nio.IntBuffer;
import tornado.api.Payload;
import tornado.api.Vector;
import uk.ac.manchester.tornado.collections.math.TornadoMath;

import static java.lang.String.format;
import static java.nio.IntBuffer.wrap;

/**
 * Class that represents a vector of 4x ints e.g. <int,int,int,int>
 *
 * @author jamesclarkson
 *
 */
@Vector
public final class Int4 implements PrimitiveStorage<IntBuffer> {

    public static final Class<Int4> TYPE = Int4.class;

    private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";

    /**
     * backing array
     */
    @Payload
    final protected int[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 4;

    public Int4(int[] storage) {
        this.storage = storage;
    }

    public Int4() {
        this(new int[numElements]);
    }

    public Int4(int x, int y, int z, int w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    public int get(int index) {
        return storage[index];
    }

    public void set(int index, int value) {
        storage[index] = value;
    }

    public void set(Int4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
    }

    public int getX() {
        return get(0);
    }

    public int getY() {
        return get(1);
    }

    public int getZ() {
        return get(2);
    }

    public int getW() {
        return get(3);
    }

    public void setX(int value) {
        set(0, value);
    }

    public void setY(int value) {
        set(1, value);
    }

    public void setZ(int value) {
        set(2, value);
    }

    public void setW(int value) {
        set(3, value);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Int4 duplicate() {
        Int4 vector = new Int4();
        vector.set(this);
        return vector;
    }

    public Int2 asInt2() {
        return new Int2(getX(), getY());
    }

    public Int3 asInt3() {
        return new Int3(getX(), getY(), getZ());
    }

    public String toString(String fmt) {
        return format(fmt, getX(), getY(), getZ(), getW());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static final Int4 loadFromArray(final int[] array, int index) {
        final Int4 result = new Int4();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        result.setW(array[index + 3]);
        return result;
    }

    protected final void storeToArray(final int[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
        array[index + 3] = getW();
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Int4 add(Int4 a, Int4 b) {
        return new Int4(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ(), a.getW() + b.getW());
    }

    public static Int4 sub(Int4 a, Int4 b) {
        return new Int4(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ(), a.getW() - b.getW());
    }

    public static Int4 div(Int4 a, Int4 b) {
        return new Int4(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ(), a.getW() / b.getW());
    }

    public static Int4 mult(Int4 a, Int4 b) {
        return new Int4(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ(), a.getW() * b.getW());
    }

    public static Int4 min(Int4 a, Int4 b) {
        return new Int4(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.min(a.getW(), b.getW()));
    }

    public static Int4 max(Int4 a, Int4 b) {
        return new Int4(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), Math.max(a.getW(), b.getW()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Int4 add(Int4 a, int b) {
        return new Int4(a.getX() + b, a.getY() + b, a.getZ() + b, a.getW() + b);
    }

    public static Int4 sub(Int4 a, int b) {
        return new Int4(a.getX() - b, a.getY() - b, a.getZ() - b, a.getW() - b);
    }

    public static Int4 mult(Int4 a, int b) {
        return new Int4(a.getX() * b, a.getY() * b, a.getZ() * b, a.getW() * b);
    }

    public static Int4 div(Int4 a, int b) {
        return new Int4(a.getX() / b, a.getY() / b, a.getZ() / b, a.getW() / b);
    }

    public static Int4 inc(Int4 a, int value) {
        return add(a, value);
    }

    public static Int4 dec(Int4 a, int value) {
        return sub(a, value);
    }

    public static Int4 scale(Int4 a, int value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static Int4 clamp(Int4 x, int min, int max) {
        return new Int4(
                TornadoMath.clamp(x.getX(), min, max),
                TornadoMath.clamp(x.getY(), min, max),
                TornadoMath.clamp(x.getZ(), min, max),
                TornadoMath.clamp(x.getW(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(Int4 value) {
        return Math.min(value.getX(), Math.min(value.getY(), Math.min(value.getZ(), value.getW())));
    }

    public static int max(Int4 value) {
        return Math.max(value.getX(), Math.max(value.getY(), Math.max(value.getZ(), value.getW())));
    }

    public static boolean isEqual(Int4 a, Int4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
