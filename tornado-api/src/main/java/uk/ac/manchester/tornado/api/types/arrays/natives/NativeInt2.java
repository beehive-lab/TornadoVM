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

@Vector
public final class NativeInt2 implements TornadoNativeCollectionsInterface<IntBuffer> {

    public static final Class<NativeInt2> TYPE = NativeInt2.class;

    public static final Class<NativeVectorInt> FIELD_CLASS = NativeVectorInt.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    private final NativeVectorInt nativeVectorInt;

    public NativeInt2(NativeVectorInt storage) {
        this.nativeVectorInt = storage;
    }

    public NativeInt2() {
        this(new NativeVectorInt(NUM_ELEMENTS));
    }

    public NativeInt2(int x, int y) {
        this();
        setX(x);
        setY(y);
    }

    /**
     * * Operations on NativeInt2 vectors.
     */
    /*
     * vector = op( vector, vector )
     */
    public static NativeInt2 add(NativeInt2 a, NativeInt2 b) {
        return new NativeInt2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    public static NativeInt2 sub(NativeInt2 a, NativeInt2 b) {
        return new NativeInt2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    public static NativeInt2 div(NativeInt2 a, NativeInt2 b) {
        return new NativeInt2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    public static NativeInt2 mult(NativeInt2 a, NativeInt2 b) {
        return new NativeInt2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    public static NativeInt2 min(NativeInt2 a, NativeInt2 b) {
        return new NativeInt2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    public static NativeInt2 max(NativeInt2 a, NativeInt2 b) {
        return new NativeInt2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeInt2 add(NativeInt2 a, int b) {
        return new NativeInt2(a.getX() + b, a.getY() + b);
    }

    public static NativeInt2 sub(NativeInt2 a, int b) {
        return new NativeInt2(a.getX() - b, a.getY() - b);
    }

    public static NativeInt2 mult(NativeInt2 a, int b) {
        return new NativeInt2(a.getX() * b, a.getY() * b);
    }

    public static NativeInt2 div(NativeInt2 a, int b) {
        return new NativeInt2(a.getX() / b, a.getY() / b);
    }

    public static NativeInt2 inc(NativeInt2 a, int value) {
        return add(a, value);
    }

    public static NativeInt2 dec(NativeInt2 a, int value) {
        return sub(a, value);
    }

    public static NativeInt2 scaleByInverse(NativeInt2 a, int value) {
        return mult(a, 1 / value);
    }

    public static NativeInt2 scale(NativeInt2 a, int value) {
        return mult(a, value);
    }

    public static NativeInt2 clamp(NativeInt2 x, int min, int max) {
        return new NativeInt2(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static int min(NativeInt2 value) {
        return Math.min(value.getX(), value.getY());
    }

    public static int max(NativeInt2 value) {
        return Math.max(value.getX(), value.getY());
    }

    public static int dot(NativeInt2 a, NativeInt2 b) {
        final NativeInt2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    public static boolean isEqual(NativeInt2 a, NativeInt2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    static NativeInt2 loadFromArray(final IntArray array, int index) {
        final NativeInt2 result = new NativeInt2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    public int get(int index) {
        return nativeVectorInt.get(index);
    }

    public void set(int index, int value) {
        nativeVectorInt.set(index, value);
    }

    public void set(NativeInt2 value) {
        setX(value.getX());
        setY(value.getY());
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

    public int getS0() {
        return get(0);
    }

    public int getS1() {
        return get(1);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link NativeInt2}
     */
    public NativeInt2 duplicate() {
        NativeInt2 vector = new NativeInt2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
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
    }

    public void clear() {
        nativeVectorInt.clear();
    }

}
