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
package uk.ac.manchester.tornado.api.types.arrays.natives;

import java.nio.IntBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.vectors.Int2;

@Vector
public final class NativeInt3 implements TornadoNativeCollectionsInterface<IntBuffer> {
    public static final Class<NativeInt3> TYPE = NativeInt3.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d }";

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;

    /**
     * backing array.
     */
    @Payload
    private final NativeVectorInt nativeVectorInt;

    public NativeInt3(NativeVectorInt storage) {
        this.nativeVectorInt = storage;
    }

    public NativeInt3() {
        this(new NativeVectorInt(NUM_ELEMENTS));
    }

    public NativeInt3(int x, int y, int z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /*
     * vector = op( vector, vector )
     */
    public static NativeInt3 add(NativeInt3 a, NativeInt3 b) {
        return new NativeInt3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    public static NativeInt3 sub(NativeInt3 a, NativeInt3 b) {
        return new NativeInt3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    public static NativeInt3 div(NativeInt3 a, NativeInt3 b) {
        return new NativeInt3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    public static NativeInt3 mult(NativeInt3 a, NativeInt3 b) {
        return new NativeInt3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    public static NativeInt3 min(NativeInt3 a, NativeInt3 b) {
        return new NativeInt3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static NativeInt3 max(NativeInt3 a, NativeInt3 b) {
        return new NativeInt3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeInt3 add(NativeInt3 a, int b) {
        return new NativeInt3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    public static NativeInt3 sub(NativeInt3 a, int b) {
        return new NativeInt3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    public static NativeInt3 mult(NativeInt3 a, int b) {
        return new NativeInt3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    public static NativeInt3 div(NativeInt3 a, int b) {
        return new NativeInt3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    public static NativeInt3 inc(NativeInt3 a, int value) {
        return add(a, value);
    }

    public static NativeInt3 dec(NativeInt3 a, int value) {
        return sub(a, value);
    }

    public static NativeInt3 scale(NativeInt3 a, int value) {
        return mult(a, value);
    }

    public static NativeInt3 clamp(NativeInt3 x, int min, int max) {
        return new NativeInt3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(NativeInt3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    public static int max(NativeInt3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(NativeInt3 a, NativeInt3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    static NativeInt3 loadFromArray(final IntArray array, int index) {
        final NativeInt3 result = new NativeInt3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    public int get(int index) {
        return nativeVectorInt.get(index);
    }

    public void set(int index, int value) {
        nativeVectorInt.set(index, value);
    }

    public void set(NativeInt3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    public int getX() {
        return get(0);
    }

    public void setX(int value) {
        set(0, value);
    }

    public int getY() {
        return get(1);
    }

    public void setY(int value) {
        set(1, value);
    }

    public int getZ() {
        return get(2);
    }

    public void setZ(int value) {
        set(2, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeInt3}
     */
    public NativeInt3 duplicate() {
        NativeInt3 vector = new NativeInt3();
        vector.set(this);
        return vector;
    }

    public Int2 asInt2() {
        return new Int2(getX(), getY());
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(NUMBER_FORMAT);
    }

    @Override
    public void loadFromBuffer(IntBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public IntBuffer asBuffer() {
        return nativeVectorInt.getSegment().asByteBuffer().asIntBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    void storeToArray(final IntArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
        array.set(index + 2, getZ());
    }

    public void clear() {
        nativeVectorInt.clear();
    }
}
