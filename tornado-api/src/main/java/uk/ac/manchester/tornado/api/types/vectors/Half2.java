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

    //    public static Float2 min(Float2 a, Float2 b) {
    //        return new Float2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    //    }
    //
    //    public static Float2 max(Float2 a, Float2 b) {
    //        return new Float2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    //    }

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

    public static void div(Half2 a, Half2 b, Half2 c) {
        c.setX(HalfFloat.div(a.getX(), b.getX()));
        c.setY(HalfFloat.div(a.getY(), b.getY()));
    }

    //    public static void min(Float2 a, Float2 b, Float2 c) {
    //        c.setX(Math.min(a.getX(), b.getX()));
    //        c.setY(Math.min(a.getY(), b.getY()));
    //    }
    //
    //    public static void max(Float2 a, Float2 b, Float2 c) {
    //        c.setX(Math.max(a.getX(), b.getX()));
    //        c.setY(Math.max(a.getY(), b.getY()));
    //    }
    //
    //    public static Float2 inc(Float2 a, float value) {
    //        return add(a, value);
    //    }
    //
    //    public static Float2 dec(Float2 a, float value) {
    //        return sub(a, value);
    //    }

    //    public static Float2 scaleByInverse(Float2 a, float value) {
    //        return mult(a, 1f / value);
    //    }
    //
    //    public static Float2 scale(Float2 a, float value) {
    //        return mult(a, value);
    //    }
    //
    //    /*
    //     * vector = op(vector)
    //     */
    //    public static Float2 sqrt(Float2 a) {
    //        return new Float2(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()));
    //    }
    //
    //    public static Float2 floor(Float2 a) {
    //        return new Float2(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()));
    //    }
    //
    //    public static Float2 fract(Float2 a) {
    //        return new Float2(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()));
    //    }
    //
    //    /*
    //     * misc inplace vector ops
    //     */
    //    public static void clamp(Float2 x, float min, float max) {
    //        x.setX(TornadoMath.clamp(x.getX(), min, max));
    //        x.setY(TornadoMath.clamp(x.getY(), min, max));
    //    }
    //
    //    public static Float2 normalise(Float2 value) {
    //        return scaleByInverse(value, length(value));
    //    }
    //
    //    /*
    //     * vector wide operations
    //     */
    //    public static float min(Float2 value) {
    //        return Math.min(value.getX(), value.getY());
    //    }
    //
    //    public static float max(Float2 value) {
    //        return Math.max(value.getX(), value.getY());
    //    }
    //
    public static HalfFloat dot(Half2 a, Half2 b) {
        final Half2 m = mult(a, b);
        return HalfFloat.add(m.getX(), m.getY());
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    //    public static float length(HalfFloat2 value) {
    //        return TornadoMath.sqrt(dot(value, value));
    //    }

    //    public static boolean isEqual(Float2 a, Float2 b) {
    //        return TornadoMath.isEqual(a.toArray(), b.toArray());
    //    }

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
     * @return {@link Float2}
     */
    public Half2 duplicate() {
        Half2 vector = new Half2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_2);
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        //TODO
        asBuffer().put(buffer);
    }

    public ShortBuffer asBuffer() {
        //TODO
        return null;
        //return ShortBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public short[] toArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 2;
    }

}
