/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.collections.types;

import java.nio.ByteBuffer;
import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

import static java.lang.Byte.MAX_VALUE;
import static java.lang.Byte.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.ByteBuffer.wrap;

/**
 * Class that represents a vector of 3x bytes e.g. <byte,byte,byte>
 *
 * @author jamesclarkson
 */
@Vector
public final class Byte8 implements PrimitiveStorage<ByteBuffer> {

    private static final String numberFormat = "{ s0=%-3d, s1=%-3d, s2=%-3d, s3=%-3d, s4=%-3d, s5=%-3d, s6=%-3d, s7=%-3d }";

    public static final Class<Byte8> TYPE = Byte8.class;

    /**
     * backing array
     */
    @Payload
    final protected byte[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 8;

    public Byte8(byte[] storage) {
        this.storage = storage;
    }

    public Byte8() {
        this(new byte[numElements]);
    }

    public Byte8(byte s0, byte s1, byte s2, byte s3, byte s4, byte s5, byte s6, byte s7) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);

        setS4(s4);
        setS5(s5);
        setS6(s6);
        setS7(s7);
    }

    public void set(Byte8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
    }

    public byte get(int index) {
        return storage[index];
    }

    public void set(int index, byte value) {
        storage[index] = value;
    }

    public byte getS0() {
        return get(0);
    }

    public byte getS1() {
        return get(1);
    }

    public byte getS2() {
        return get(2);
    }

    public byte getS3() {
        return get(3);
    }

    public byte getS4() {
        return get(4);
    }

    public byte getS5() {
        return get(5);
    }

    public byte getS6() {
        return get(6);
    }

    public byte getS7() {
        return get(7);
    }

    public void setS0(byte value) {
        set(0, value);
    }

    public void setS1(byte value) {
        set(1, value);
    }

    public void setS2(byte value) {
        set(2, value);
    }

    public void setS3(byte value) {
        set(3, value);
    }

    public void setS4(byte value) {
        set(4, value);
    }

    public void setS5(byte value) {
        set(5, value);
    }

    public void setS6(byte value) {
        set(6, value);
    }

    public void setS7(byte value) {
        set(7, value);
    }

    public Byte4 getHi() {
        return new Byte4(getS4(), getS5(), getS6(), getS7());
    }

    public Byte4 getLo() {
        return new Byte4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Byte8 duplicate() {
        Byte8 vector = new Byte8();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7());
    }

    @Override
    public String toString() {
        return toString(numberFormat);
    }

    protected static final Byte8 loadFromArray(final byte[] array, int index) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, array[index + i]);
        }
        return result;
    }

    protected final void storeToArray(final byte[] array, int index) {
        for (int i = 0; i < numElements; i++) {
            array[index + i] = get(i);
        }
    }

    @Override
    public void loadFromBuffer(ByteBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ByteBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Byte8 add(Byte8 a, Byte8 b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) + b.get(i)));
        }
        return result;
    }

    public static Byte8 sub(Byte8 a, Byte8 b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) - b.get(i)));
        }
        return result;
    }

    public static Byte8 div(Byte8 a, Byte8 b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) / b.get(i)));
        }
        return result;
    }

    public static Byte8 mult(Byte8 a, Byte8 b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) * b.get(i)));
        }
        return result;
    }

    public static Byte8 min(Byte8 a, Byte8 b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, TornadoMath.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static Byte8 max(Byte8 a, Byte8 b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, TornadoMath.max(a.get(i), b.get(i)));
        }
        return result;
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Byte8 add(Byte8 a, byte b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) + b));
        }
        return result;
    }

    public static Byte8 sub(Byte8 a, byte b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) - b));
        }
        return result;
    }

    public static Byte8 mult(Byte8 a, byte b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) * b));
        }
        return result;
    }

    public static Byte8 div(Byte8 a, byte b) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, (byte) (a.get(i) / b));
        }
        return result;
    }

    public static Byte8 inc(Byte8 a, byte value) {
        return add(a, value);
    }

    public static Byte8 dec(Byte8 a, byte value) {
        return sub(a, value);
    }

    public static Byte8 scale(Byte8 a, byte value) {
        return mult(a, value);
    }

    /*
     * misc inplace vector ops
     */
    public static Byte8 clamp(Byte8 x, byte min, byte max) {
        final Byte8 result = new Byte8();
        for (int i = 0; i < numElements; i++) {
            result.set(i, TornadoMath.clamp(x.get(i), min, max));
        }
        return result;
    }

    /*
     * vector wide operations
     */
    public static byte min(Byte8 value) {
        byte result = MAX_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = TornadoMath.min(result, value.get(i));
        }
        return result;
    }

    public static byte max(Byte8 value) {
        byte result = MIN_VALUE;
        for (int i = 0; i < numElements; i++) {
            result = TornadoMath.max(result, value.get(i));
        }
        return result;
    }

    public static boolean isEqual(Byte8 a, Byte8 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }
}
