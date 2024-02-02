package uk.ac.manchester.tornado.api.types.vectors;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;

import java.nio.ShortBuffer;

@Vector
public final class Half3 implements TornadoVectorsInterface<ShortBuffer> {

    public static final Class<Half3> TYPE = Half3.class;

    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 3;
    /**
     * backing array.
     */
    @Payload
    short[] storage = new short[3];

    private Half3(HalfFloat[] storage) {
        this.storage[0] = storage[0].getHalfFloatValue();
        this.storage[1] = storage[1].getHalfFloatValue();
        this.storage[2] = storage[2].getHalfFloatValue();
    }

    public Half3() {
        this.storage = new short[3];
    }

    public Half3(HalfFloat x, HalfFloat y, HalfFloat z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /**
     * * Operations on Float3 vectors.
     */
    /*
     * vector = op( vector, vector )
     */
    public static Half3 add(Half3 a, Half3 b) {
        return new Half3(HalfFloat.add(a.getX(), b.getX()), HalfFloat.add(a.getY(), b.getY()), HalfFloat.add(a.getZ(), b.getZ()));
    }

    public static Half3 sub(Half3 a, Half3 b) {
        return new Half3(HalfFloat.sub(a.getX(), b.getX()), HalfFloat.sub(a.getY(), b.getY()), HalfFloat.sub(a.getZ(), b.getZ()));
    }

    public static Half3 div(Half3 a, Half3 b) {
        return new Half3(HalfFloat.div(a.getX(), b.getX()), HalfFloat.div(a.getY(), b.getY()), HalfFloat.div(a.getZ(), b.getZ()));
    }

    public static Half3 mult(Half3 a, Half3 b) {
        return new Half3(HalfFloat.mult(a.getX(), b.getX()), HalfFloat.mult(a.getY(), b.getY()), HalfFloat.mult(a.getZ(), b.getZ()));
    }

    //    public static Float3 min(Float3 a, Float3 b) {
    //        return new Float3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    //    }
    //
    //    public static Float3 max(Float3 a, Float3 b) {
    //        return new Float3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    //    }
    //
    //    public static Float3 cross(Float3 a, Float3 b) {
    //        return new Float3(a.getY() * b.getZ() - a.getZ() * b.getY(), a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
    //    }

    /*
     * vector = op (vector, scalar)
     */
    public static Half3 add(Half3 a, HalfFloat b) {
        return new Half3(HalfFloat.add(a.getX(), b), HalfFloat.add(a.getY(), b), HalfFloat.add(a.getZ(), b));
    }

    public static Half3 sub(Half3 a, HalfFloat b) {
        return new Half3(HalfFloat.sub(a.getX(), b), HalfFloat.sub(a.getY(), b), HalfFloat.sub(a.getZ(), b));
    }

    public static Half3 mult(Half3 a, HalfFloat b) {
        return new Half3(HalfFloat.mult(a.getX(), b), HalfFloat.mult(a.getY(), b), HalfFloat.mult(a.getZ(), b));
    }

    public static Half3 div(Half3 a, HalfFloat b) {
        return new Half3(HalfFloat.div(a.getX(), b), HalfFloat.div(a.getY(), b), HalfFloat.div(a.getZ(), b));
    }

    //    public static Float3 inc(Float3 a, float value) {
    //        return new Float3(a.getX() + value, a.getY() + value, a.getZ() + value);
    //    }
    //
    //    public static Float3 dec(Float3 a, float value) {
    //        return new Float3(a.getX() - value, a.getY() - value, a.getZ() - value);
    //    }
    //
    //    public static Float3 scaleByInverse(Float3 a, float value) {
    //        return mult(a, 1f / value);
    //    }
    //
    //    public static Float3 scale(Float3 a, float value) {
    //        return mult(a, value);
    //    }
    //
    //    /*
    //     * vector = op(vector)
    //     */
    //    public static Float3 sqrt(Float3 a) {
    //        return new Float3(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()));
    //    }
    //
    //    public static Float3 floor(Float3 a) {
    //        return new Float3(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()));
    //    }
    //
    //    public static Float3 fract(Float3 a) {
    //        return new Float3(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()));
    //    }
    //
    //    /*
    //     * misc inplace vector ops
    //     */
    //    public static Float3 clamp(Float3 x, float min, float max) {
    //        return new Float3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    //    }
    //
    //    public static Float3 normalise(Float3 value) {
    //        final float len = 1f / length(value);
    //        return mult(value, len);
    //    }
    //
    //    /*
    //     * vector wide operations
    //     */
    //    public static float min(Float3 value) {
    //        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    //    }
    //
    //    public static float max(Float3 value) {
    //        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    //    }

    public static HalfFloat dot(Half3 a, Half3 b) {
        final Half3 m = mult(a, b);
        return HalfFloat.add(HalfFloat.add(m.getX(), m.getY()), m.getZ());
    }

    /**
     * Returns the vector length e.g. the sqrt of all elements squared.
     *
     * @return float
     */
    //    public static float length(Float3 value) {
    //        return TornadoMath.sqrt(dot(value, value));
    //    }
    //
    //    public static boolean isEqual(Float3 a, Float3 b) {
    //        return TornadoMath.isEqual(a.toArray(), b.toArray());
    //    }
    //
    //    public static boolean isEqualULP(Float3 a, Float3 b, float numULP) {
    //        return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
    //    }
    //
    //    public static float findULPDistance(Float3 a, Float3 b) {
    //        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    //    }

    public HalfFloat get(int index) {
        return new HalfFloat(storage[index]);
    }

    public void set(int index, HalfFloat value) {
        storage[index] = value.getHalfFloatValue();
    }

    public void set(Half3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
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

    /**
     * Duplicates this vector.
     *
     * @return {@link Float3}
     */
    public Half3 duplicate() {
        final Half3 vector = new Half3();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    @Override
    public String toString() {
        return toString(FloatOps.FMT_3);
    }

    /**
     * Cast vector From Half3 into a Half2.
     *
     * @return {@link Half2}
     */
    public Half2 aHalf2() {
        return new Half2(getX(), getY());
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

}
