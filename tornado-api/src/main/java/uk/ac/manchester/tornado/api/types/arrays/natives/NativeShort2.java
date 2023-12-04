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

import java.lang.foreign.ValueLayout;
import java.nio.ShortBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.utils.ShortOps;

@Vector
public final class NativeShort2 implements TornadoNativeCollectionsInterface<ShortBuffer> {

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
