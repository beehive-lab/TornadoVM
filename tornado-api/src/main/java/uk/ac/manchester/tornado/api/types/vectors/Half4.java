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
public final class Half4 implements TornadoVectorsInterface<ShortBuffer> {

    public static final Class<Half4> TYPE = Half4.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 4;
    /**
     * backing array.
     */
    @Payload
    short[] storage = new short[4];

    private Half4(HalfFloat[] storage) {
        this.storage[0] = storage[0].getHalfFloatValue();
        this.storage[1] = storage[1].getHalfFloatValue();
        this.storage[2] = storage[2].getHalfFloatValue();
        this.storage[3] = storage[3].getHalfFloatValue();
    }

    public Half4() {
        this.storage = new short[4];
    }

    public Half4(HalfFloat x, HalfFloat y, HalfFloat z, HalfFloat w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    public static Half4 add(Half4 a, Half4 b) {
        return new Half4(HalfFloat.add(a.getX(), b.getX()), HalfFloat.add(a.getY(), b.getY()), HalfFloat.add(a.getZ(), b.getZ()), HalfFloat.add(a.getW(), b.getW()));
    }

    public static Half4 sub(Half4 a, Half4 b) {
        return new Half4(HalfFloat.sub(a.getX(), b.getX()), HalfFloat.sub(a.getY(), b.getY()), HalfFloat.sub(a.getZ(), b.getZ()), HalfFloat.sub(a.getW(), b.getW()));
    }

    public static Half4 div(Half4 a, Half4 b) {
        return new Half4(HalfFloat.div(a.getX(), b.getX()), HalfFloat.div(a.getY(), b.getY()), HalfFloat.div(a.getZ(), b.getZ()), HalfFloat.div(a.getW(), b.getW()));
    }

    public static Half4 mult(Half4 a, Half4 b) {
        return new Half4(HalfFloat.mult(a.getX(), b.getX()), HalfFloat.mult(a.getY(), b.getY()), HalfFloat.mult(a.getZ(), b.getZ()), HalfFloat.mult(a.getW(), b.getW()));
    }

    public static Half4 add(Half4 a, HalfFloat b) {
        return new Half4(HalfFloat.add(a.getX(), b), HalfFloat.add(a.getY(), b), HalfFloat.add(a.getZ(), b), HalfFloat.add(a.getW(), b));
    }

    public static Half4 sub(Half4 a, HalfFloat b) {
        return new Half4(HalfFloat.sub(a.getX(), b), HalfFloat.sub(a.getY(), b), HalfFloat.sub(a.getZ(), b), HalfFloat.sub(a.getW(), b));
    }

    public static Half4 mult(Half4 a, HalfFloat b) {
        return new Half4(HalfFloat.mult(a.getX(), b), HalfFloat.mult(a.getY(), b), HalfFloat.mult(a.getZ(), b), HalfFloat.mult(a.getW(), b));
    }

    public static Half4 div(Half4 a, HalfFloat b) {
        return new Half4(HalfFloat.div(a.getX(), b), HalfFloat.div(a.getY(), b), HalfFloat.div(a.getZ(), b), HalfFloat.div(a.getW(), b));
    }

    public static HalfFloat dot(Half4 a, Half4 b) {
        final Half4 m = mult(a, b);
        return HalfFloat.add(HalfFloat.add(m.getX(), m.getY()), HalfFloat.add(m.getZ(), m.getW()));
    }

    public HalfFloat get(int index) {
        return new HalfFloat(storage[index]);
    }

    public void set(int index, HalfFloat value) {
        storage[index] = value.getHalfFloatValue();
    }

    public void set(Half4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
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

    public HalfFloat getZ() {
        return get(2);
    }

    public void setZ(HalfFloat value) {
        set(2, value);
    }

    public HalfFloat getW() {
        return get(3);
    }

    public void setW(HalfFloat value) {
        set(3, value);
    }

    public HalfFloat getS0() {
        return get(0);
    }

    public void setS0(HalfFloat value) {
        set(0, value);
    }

    public HalfFloat getS1() {
        return get(1);
    }

    public void setS1(HalfFloat value) {
        set(1, value);
    }

    public HalfFloat getS2() {
        return get(2);
    }

    public void setS2(HalfFloat value) {
        set(2, value);
    }

    public HalfFloat getS3() {
        return get(3);
    }

    public void setS3(HalfFloat value) {
        set(3, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Float3}
     */
    public Half4 duplicate() {
        final Half4 vector = new Half4();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX().getFloat32(), getY().getFloat32(), getZ().getFloat32(), getW().getFloat32());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_4);
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
