/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

import java.nio.ShortBuffer;

@Vector
public final class Half2 implements TornadoVectorsInterface<ShortBuffer> {
    public static final Class<Half2> TYPE = Half2.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 2;
    /**
     * backing array.
     */
    @Payload
    short[] storage = new short[2];

    private Half2(HalfFloat[] storage) {
        this.storage[0] = storage[0].getHalfFloatValue();
        this.storage[1] = storage[1].getHalfFloatValue();
    }

    public Half2() {
        this.storage = new short[2];
    }

    public Half2(HalfFloat x, HalfFloat y) {
        this();
        setX(x);
        setY(y);
    }

    public static Half2 add(Half2 a, Half2 b) {
        return new Half2(HalfFloat.add(a.getX(), b.getX()), HalfFloat.add(a.getY(), b.getY()));
    }

    public static Half2 sub(Half2 a, Half2 b) {
        return new Half2(HalfFloat.sub(a.getX(), b.getX()), HalfFloat.sub(a.getY(), b.getY()));
    }

    public static Half2 div(Half2 a, Half2 b) {
        return new Half2(HalfFloat.div(a.getX(), b.getX()), HalfFloat.div(a.getY(), b.getY()));
    }

    public static Half2 mult(Half2 a, Half2 b) {
        return new Half2(HalfFloat.mult(a.getX(), b.getX()), HalfFloat.mult(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Half2 add(Half2 a, HalfFloat b) {
        return new Half2(HalfFloat.add(a.getX(), b), HalfFloat.add(a.getY(), b));
    }

    public static Half2 sub(Half2 a, HalfFloat b) {
        return new Half2(HalfFloat.sub(a.getX(), b), HalfFloat.sub(a.getY(), b));
    }

    public static Half2 mult(Half2 a, HalfFloat b) {
        return new Half2(HalfFloat.mult(a.getX(), b), HalfFloat.mult(a.getY(), b));
    }

    public static Half2 div(Half2 a, HalfFloat b) {
        return new Half2(HalfFloat.div(a.getX(), b), HalfFloat.div(a.getY(), b));
    }

    /*
     * vector = op (vector, vector)
     */
    public static void add(Half2 a, Half2 b, Half2 c) {
        c.setX(HalfFloat.add(a.getX(), b.getX()));
        c.setY(HalfFloat.add(a.getY(), b.getY()));
    }

    public static void sub(Half2 a, Half2 b, Half2 c) {
        c.setX(HalfFloat.sub(a.getX(), b.getX()));
        c.setY(HalfFloat.sub(a.getY(), b.getY()));
    }

    public static void mult(Half2 a, Half2 b, Half2 c) {
        c.setX(HalfFloat.mult(a.getX(), b.getX()));
        c.setY(HalfFloat.mult(a.getY(), b.getY()));
    }

    public static HalfFloat dot(Half2 a, Half2 b) {
        final Half2 m = mult(a, b);
        return HalfFloat.add(m.getX(), m.getY());
    }

    public HalfFloat get(int index) {
        return new HalfFloat(storage[index]);
    }

    public void set(int index, HalfFloat value) {
        storage[index] = value.getHalfFloatValue();
    }

    public void set(Half2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    public HalfFloat getX() {
        return get(0);
    }

    public void setX(HalfFloat value) {
        set(0, value);
    }

    public HalfFloat getY() {
        return get(1);
    }

    public void setY(HalfFloat value) {
        set(1, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@code Half2}
     */
    public Half2 duplicate() {
        Half2 vector = new Half2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX().getFloat32(), getY().getFloat32());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_2);
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

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
