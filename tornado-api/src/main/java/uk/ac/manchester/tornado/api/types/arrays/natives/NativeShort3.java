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

@Vector
public final class NativeShort3 implements TornadoNativeCollectionsInterface<ShortBuffer> {

    public static final Class<NativeShort3> TYPE = NativeShort3.class;

    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d }";
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    private final NativeVectorShort nativeVectorShort;

    public NativeShort3(NativeVectorShort nativeVectorShort) {
        this.nativeVectorShort = nativeVectorShort;
    }

    public NativeShort3() {
        this(new NativeVectorShort(NUM_ELEMENTS));
    }

    public NativeShort3(short x, short y, short z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /*
     * vector = op( vector, vector )
     */
    public static NativeShort3 add(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()), (short) (a.getZ() + b.getZ()));
    }

    public static NativeShort3 sub(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()), (short) (a.getZ() - b.getZ()));
    }

    public static NativeShort3 div(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()), (short) (a.getZ() / b.getZ()));
    }

    public static NativeShort3 mult(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()), (short) (a.getZ() * b.getZ()));
    }

    public static NativeShort3 min(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()));
    }

    public static NativeShort3 max(NativeShort3 a, NativeShort3 b) {
        return new NativeShort3(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static NativeShort3 add(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() + b), (short) (a.getY() + b), (short) (a.getZ() + b));
    }

    public static NativeShort3 sub(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() - b), (short) (a.getY() - b), (short) (a.getZ() - b));
    }

    public static NativeShort3 mult(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() * b), (short) (a.getY() * b), (short) (a.getZ() * b));
    }

    public static NativeShort3 div(NativeShort3 a, short b) {
        return new NativeShort3((short) (a.getX() / b), (short) (a.getY() / b), (short) (a.getZ() / b));
    }

    public static NativeShort3 inc(NativeShort3 a, short value) {
        return add(a, value);
    }

    public static NativeShort3 dec(NativeShort3 a, short value) {
        return sub(a, value);
    }

    public static NativeShort3 scale(NativeShort3 a, short value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static NativeShort3 clamp(NativeShort3 x, short min, short max) {
        return new NativeShort3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /*
     * vector wide operations
     */
    public static short min(NativeShort3 value) {
        return TornadoMath.min(value.getX(), TornadoMath.min(value.getY(), value.getZ()));
    }

    public static short max(NativeShort3 value) {
        return TornadoMath.max(value.getX(), TornadoMath.max(value.getY(), value.getZ()));
    }

    public static boolean isEqual(NativeShort3 a, NativeShort3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    public void set(NativeShort3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    public short get(int index) {
        return nativeVectorShort.get(index);
    }

    public void set(int index, short value) {
        nativeVectorShort.set(index, value);
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

    public NativeShort3 duplicate() {
        NativeShort3 vector = new NativeShort3();
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
        return nativeVectorShort.getSegment().asByteBuffer().asShortBuffer();
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public short[] toArray() {
        return nativeVectorShort.getSegment().toArray(ValueLayout.JAVA_SHORT);
    }

    private static NativeShort3 loadFromArray(final short[] array, int index) {
        final NativeShort3 result = new NativeShort3();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        result.setZ(array[index + 2]);
        return result;
    }

    private void storeToArray(final short[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
        array[index + 2] = getZ();
    }

}
