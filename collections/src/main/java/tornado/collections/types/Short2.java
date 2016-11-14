package tornado.collections.types;

import java.nio.ShortBuffer;
import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 2x shorts e.g. <short,short>
 *
 * @author jamesclarkson
 *
 */
@Vector
public class Short2 implements PrimitiveStorage<ShortBuffer> {

    public static final Class<Short2> TYPE = Short2.class;

    /**
     * backing array
     */
    @Payload
    final protected short[] storage;

    /**
     * number of elements in the storage
     */
    final private static int numElements = 2;

    public Short2(short[] storage) {
        this.storage = storage;
    }

    public Short2() {
        this(new short[numElements]);
    }

    public Short2(short x, short y) {
        this();
        setX(x);
        setY(y);
    }

    public short get(int index) {
        return storage[index];
    }

    public void set(int index, short value) {
        storage[index] = value;
    }

    public void set(Short2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    public short getX() {
        return get(0);
    }

    public short getY() {
        return get(1);
    }

    public void setX(short value) {
        set(0, value);
    }

    public void setY(short value) {
        set(1, value);
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public Short2 duplicate() {
        Short2 vector = new Short2();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
    }

    @Override
    public String toString() {
        return toString(ShortOps.fmt2);
    }

    protected static final Short2 loadFromArray(final short[] array, int index) {
        final Short2 result = new Short2();
        result.setX(array[index]);
        result.setY(array[index + 1]);
        return result;
    }

    protected final void storeToArray(final short[] array, int index) {
        array[index] = getX();
        array[index + 1] = getY();
    }

    @Override
    public void loadFromBuffer(ShortBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public ShortBuffer asBuffer() {
        return ShortBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

    /*
     * vector = op( vector, vector )
     */
    public static Short2 add(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()));
    }

    public static Short2 sub(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()));
    }

    public static Short2 div(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()));
    }

    public static Short2 mult(Short2 a, Short2 b) {
        return new Short2((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()));
    }

    public static Short2 min(Short2 a, Short2 b) {
        return new Short2(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()));
    }

    public static Short2 max(Short2 a, Short2 b) {
        return new Short2(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()));
    }

    /*
     * vector = op (vector, scalar)
     */
    public static Short2 add(Short2 a, short b) {
        return new Short2((short) (a.getX() + b), (short) (a.getY() + b));
    }

    public static Short2 sub(Short2 a, short b) {
        return new Short2((short) (a.getX() - b), (short) (a.getY() - b));
    }

    public static Short2 mult(Short2 a, short b) {
        return new Short2((short) (a.getX() * b), (short) (a.getY() * b));
    }

    public static Short2 div(Short2 a, short b) {
        return new Short2((short) (a.getX() / b), (short) (a.getY() / b));
    }

    public static Short2 inc(Short2 a, short value) {
        return add(a, value);
    }

    public static Short2 dec(Short2 a, short value) {
        return sub(a, value);
    }

    public static Short2 scale(Short2 a, short value) {
        return mult(a, value);
    }

    public static Short2 clamp(Short2 x, short min, short max) {
        return new Short2(
                TornadoMath.clamp(x.getX(), min, max),
                TornadoMath.clamp(x.getY(), min, max));
    }

    /*
     * vector wide operations
     */
    public static short min(Short2 value) {
        return TornadoMath.min(value.getX(), value.getY());
    }

    public static short max(Short2 value) {
        return TornadoMath.max(value.getX(), value.getY());
    }

    public static boolean isEqual(Short2 a, Short2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

}
