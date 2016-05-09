package tornado.collections.types;

import java.nio.ByteBuffer;

import tornado.collections.api.Payload;
import tornado.collections.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 3x bytes
 * e.g. <byte,byte,byte>
 * 
 * @author jamesclarkson
 */
@Vector
public class Byte4 implements PrimitiveStorage<ByteBuffer> {
	private static final String			numberFormat	= "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";

	public static final Class<Byte4>	TYPE			= Byte4.class;

	/**
	 * backing array
	 */
	@Payload
	final protected byte[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements		= 4;

	public Byte4(byte[] storage) {
		this.storage = storage;
	}

	public Byte4() {
		this(new byte[numElements]);
	}

	public Byte4(byte x, byte y, byte z, byte w) {
		this();
		setX(x);
		setY(y);
		setZ(z);
		setW(w);
	}

	public void set(Byte4 value) {
		setX(value.getX());
		setY(value.getY());
		setZ(value.getZ());
		setW(value.getW());
	}

	public byte get(int index) {
		return storage[index];
	}

	public void set(int index, byte value) {
		storage[index] = value;
	}

	public byte getX() {
		return get(0);
	}

	public byte getY() {
		return get(1);
	}

	public byte getZ() {
		return get(2);
	}

	public byte getW() {
		return get(3);
	}

	public void setX(byte value) {
		set(0, value);
	}

	public void setY(byte value) {
		set(1, value);
	}

	public void setZ(byte value) {
		set(2, value);
	}

	public void setW(byte value) {
		set(3, value);
	}

	/**
	 * Duplicates this vector
	 * 
	 * @return
	 */
	public Byte4 duplicate() {
		Byte4 vector = new Byte4();
		vector.set(this);
		return vector;
	}

	public String toString(String fmt) {
		return String.format(fmt, getX(), getY(), getZ(), getW());
	}

	public String toString() {
		return toString(numberFormat);
	}

	protected static final Byte4 loadFromArray(final byte[] array, int index) {
		final Byte4 result = new Byte4();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		result.setZ(array[index + 2]);
		result.setW(array[index + 3]);
		return result;
	}

	protected final void storeToArray(final byte[] array, int index) {
		array[index] = getX();
		array[index + 1] = getY();
		array[index + 2] = getZ();
		array[index + 3] = getW();
	}

	@Override
	public void loadFromBuffer(ByteBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public ByteBuffer asBuffer() {
		return ByteBuffer.wrap(storage);
	}

	public int size() {
		return numElements;
	}

	/*
	 * vector = op( vector, vector )
	 */
	public static Byte4 add(Byte4 a, Byte4 b) {
		return new Byte4((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()),
				(byte) (a.getZ() + b.getZ()), (byte) (a.getW() + b.getW()));
	}

	public static Byte4 sub(Byte4 a, Byte4 b) {
		return new Byte4((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()),
				(byte) (a.getZ() - b.getZ()), (byte) (a.getW() - b.getW()));
	}

	public static Byte4 div(Byte4 a, Byte4 b) {
		return new Byte4((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()),
				(byte) (a.getZ() / b.getZ()), (byte) (a.getW() / b.getW()));
	}

	public static Byte4 mult(Byte4 a, Byte4 b) {
		return new Byte4((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()),
				(byte) (a.getZ() * b.getZ()), (byte) (a.getW() * b.getW()));
	}

	public static Byte4 min(Byte4 a, Byte4 b) {
		return new Byte4(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()),
				TornadoMath.min(a.getZ(), b.getZ()), TornadoMath.min(a.getW(), b.getW()));
	}

	public static Byte4 max(Byte4 a, Byte4 b) {
		return new Byte4(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()),
				TornadoMath.max(a.getZ(), b.getZ()), TornadoMath.max(a.getW(), b.getW()));
	}

	/*
	 * vector = op (vector, scalar)
	 */

	public static Byte4 add(Byte4 a, byte b) {
		return new Byte4((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b),
				(byte) (a.getW() + b));
	}

	public static Byte4 sub(Byte4 a, byte b) {
		return new Byte4((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b),
				(byte) (a.getW() - b));
	}

	public static Byte4 mult(Byte4 a, byte b) {
		return new Byte4((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b),
				(byte) (a.getW() * b));
	}

	public static Byte4 div(Byte4 a, byte b) {
		return new Byte4((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b),
				(byte) (a.getW() / b));
	}

	/*
	 * vector = op (vector, vector)
	 */

	public static void add(Byte4 a, Byte4 b, Byte4 c) {
		c.setX((byte) (a.getX() + b.getX()));
		c.setY((byte) (a.getY() + b.getY()));
		c.setZ((byte) (a.getZ() + b.getZ()));
		c.setW((byte) (a.getW() + b.getW()));
	}

	public static void sub(Byte4 a, Byte4 b, Byte4 c) {
		c.setX((byte) (a.getX() - b.getX()));
		c.setY((byte) (a.getY() - b.getY()));
		c.setZ((byte) (a.getZ() - b.getZ()));
		c.setW((byte) (a.getW() - b.getW()));
	}

	public static void mult(Byte4 a, Byte4 b, Byte4 c) {
		c.setX((byte) (a.getX() * b.getX()));
		c.setY((byte) (a.getY() * b.getY()));
		c.setZ((byte) (a.getZ() * b.getZ()));
		c.setW((byte) (a.getW() * b.getW()));
	}

	public static void div(Byte4 a, Byte4 b, Byte4 c) {
		c.setX((byte) (a.getX() / b.getX()));
		c.setY((byte) (a.getY() / b.getY()));
		c.setZ((byte) (a.getZ() / b.getZ()));
		c.setW((byte) (a.getW() / b.getW()));
	}

	public static void min(Byte4 a, Byte4 b, Byte4 c) {
		c.setX(TornadoMath.min(a.getX(), b.getX()));
		c.setY(TornadoMath.min(a.getY(), b.getY()));
		c.setZ(TornadoMath.min(a.getZ(), b.getZ()));
		c.setW(TornadoMath.min(a.getW(), b.getW()));
	}

	public static void max(Byte4 a, Byte4 b, Byte4 c) {
		c.setX(TornadoMath.max(a.getX(), b.getX()));
		c.setY(TornadoMath.max(a.getY(), b.getY()));
		c.setZ(TornadoMath.max(a.getZ(), b.getZ()));
		c.setW(TornadoMath.max(a.getW(), b.getW()));
	}

	/*
	 * inplace src = op (src, scalar)
	 */

	public static void inc(Byte4 a, byte value) {
		a.setX((byte) (a.getX() + value));
		a.setY((byte) (a.getY() + value));
		a.setZ((byte) (a.getZ() + value));
		a.setW((byte) (a.getW() + value));
	}

	public static void dec(Byte4 a, byte value) {
		a.setX((byte) (a.getX() - value));
		a.setY((byte) (a.getY() - value));
		a.setZ((byte) (a.getZ() - value));
		a.setW((byte) (a.getW() - value));
	}

	public static void scale(Byte4 a, byte value) {
		a.setX((byte) (a.getX() * value));
		a.setY((byte) (a.getY() * value));
		a.setZ((byte) (a.getZ() * value));
		a.setW((byte) (a.getW() * value));
	}

	/*
	 * misc inplace vector ops
	 */

	public static void clamp(Byte4 x, byte min, byte max) {
		x.setX(TornadoMath.clamp(x.getX(), min, max));
		x.setY(TornadoMath.clamp(x.getY(), min, max));
		x.setZ(TornadoMath.clamp(x.getZ(), min, max));
		x.setW(TornadoMath.clamp(x.getW(), min, max));
	}

	/*
	 * vector wide operations
	 */

	public static byte min(Byte4 value) {
		return TornadoMath.min(TornadoMath.min(value.getX(), value.getY()),
				TornadoMath.min(value.getZ(), value.getW()));
	}

	public static byte max(Byte4 value) {
		return TornadoMath.max(TornadoMath.max(value.getX(), value.getY()),
				TornadoMath.max(value.getZ(), value.getW()));
	}

	public static boolean isEqual(Byte4 a, Byte4 b) {
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}
}
