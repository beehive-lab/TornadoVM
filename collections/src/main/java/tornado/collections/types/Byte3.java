package tornado.collections.types;

import java.nio.ByteBuffer;

import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 3x bytes
 * e.g. <byte,byte,byte>
 * @author jamesclarkson
 *
 */
@Vector
public class Byte3 implements PrimitiveStorage<ByteBuffer> {
	private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d }";
    
public static final Class<Byte3>	TYPE		= Byte3.class;
    
	/**
	 * backing array
	 */
	@Payload final protected byte[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 3;
    
	public Byte3(byte[] storage) {
		this.storage = storage;
	}

	public Byte3() {
		this(new byte[numElements]);
	}

	public Byte3(byte x, byte y, byte z) {
		this();
		setX(x);
		setY(y);
		setZ(z);
	}
	
	public void set(Byte3 value){
		setX(value.getX());
		setY(value.getY());
		setZ(value.getZ());
	}
	
	public byte get(int index) {
		return storage[index];
	}

	public void set(int index, byte value) {
		storage[index] = value;
	}
    
    public byte getX(){
    	return get(0);
    }
    
    public byte getY(){
    	return get(1);
    }
    
    public byte getZ(){
    	return get(2);
    }
    
    public void setX(byte value){
    	set(0,value);
    }
    
    public void setY(byte value){
    	set(1,value);
    }
    
    public void setZ(byte value){
    	set(2,value);
    }
    
	/**
	 * Duplicates this vector
	 * @return
	 */
	public Byte3 duplicate(){
		final Byte3 vector = new Byte3();
		vector.set(this);
		return vector;
	}
       
    public String toString(String fmt){
        return String.format(fmt, getX(),getY(),getZ());
   }
   
   public String toString(){
		return toString(numberFormat);
   }

   protected static final Byte3 loadFromArray(final byte[] array, int index){
		final Byte3 result = new Byte3();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		result.setZ(array[index + 2]);
		return result;
	}
	
	protected final void storeToArray(final byte[] array, int index){
		array[index] = getX();
		array[index+1] = getY();
		array[index+2] = getZ();
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
	public static Byte3 add(Byte3 a, Byte3 b){
		return new Byte3((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()),  (byte) (a.getZ() + b.getZ()));
	}
	
	public static Byte3 sub(Byte3 a, Byte3 b){
		return new Byte3((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()),(byte) (a.getZ() - b.getZ()));
	}
	
	public static Byte3 div(Byte3 a, Byte3 b){
		return new Byte3((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()),(byte) (a.getZ() / b.getZ()));
	}
	
	public static Byte3 mult(Byte3 a, Byte3 b){
		return new Byte3((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()),(byte) (a.getZ() * b.getZ()));
	}
	
	public static Byte3 min(Byte3 a, Byte3 b){
		return new Byte3(TornadoMath.min(a.getX() , b.getX()),TornadoMath.min(a.getY() , b.getY()),TornadoMath.min(a.getZ() , b.getZ()));
	}
	
	public static Byte3 max(Byte3 a, Byte3 b){
		return new Byte3(TornadoMath.max(a.getX() , b.getX()),TornadoMath.max(a.getY() , b.getY()),TornadoMath.max(a.getZ() , b.getZ()));
	}
	
	/*
	 * vector = op (vector, scalar)
	 */
	
	public static Byte3 add(Byte3 a, byte b){
		return new Byte3((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b));
	}
	
	public static Byte3 sub(Byte3 a, byte b){
		return new Byte3((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b));
	}
	
	public static Byte3 mult(Byte3 a, byte b){
		return new Byte3((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b));
	}
	
	public static Byte3 div(Byte3 a, byte b){
		return new Byte3((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b));
	}
	
	/*
	 * vector = op (vector, vector)
	 */
	
	public static void add(Byte3 a, Byte3 b, Byte3 c){
		c.setX((byte) (a.getX() + b.getX()));
		c.setY((byte) (a.getY() + b.getY()));
		c.setZ((byte) (a.getZ() + b.getZ()));
	}
	
	public static void sub(Byte3 a, Byte3 b, Byte3 c){
		c.setX((byte) (a.getX() - b.getX()));
		c.setY((byte) (a.getY() - b.getY()));
		c.setZ((byte) (a.getZ() - b.getZ()));
	}
	
	public static void mult(Byte3 a, Byte3 b, Byte3 c){
		c.setX((byte) (a.getX() * b.getX()));
		c.setY((byte) (a.getY() * b.getY()));
		c.setZ((byte) (a.getZ() * b.getZ()));
	}
	
	public static void div(Byte3 a, Byte3 b, Byte3 c){
		c.setX((byte) (a.getX() / b.getX()));
		c.setY((byte) (a.getY() / b.getY()));
		c.setZ((byte) (a.getZ() / b.getZ()));
	}
	
	public static void min(Byte3 a, Byte3 b, Byte3 c){
		c.setX(TornadoMath.min(a.getX() , b.getX()));
		c.setY(TornadoMath.min(a.getY() , b.getY()));
		c.setZ(TornadoMath.min(a.getZ() , b.getZ()));
	}
	
	public static void max(Byte3 a, Byte3 b, Byte3 c){
		c.setX(TornadoMath.max(a.getX() , b.getX()));
		c.setY(TornadoMath.max(a.getY() , b.getY()));
		c.setZ(TornadoMath.max(a.getZ() , b.getZ()));
	}
	
	/*
	 *  inplace src = op (src, scalar)
	 */
	
	public static void inc(Byte3 a, byte value){
		a.setX((byte) (a.getX() + value));
		a.setY((byte) (a.getY() + value));
		a.setZ((byte) (a.getZ() + value));
	}
	
	
	public static void dec(Byte3 a, byte value){
		a.setX((byte) (a.getX() - value));
		a.setY((byte) (a.getY() - value));
		a.setZ((byte) (a.getZ() - value));
	}
	
	
	public static void scale(Byte3 a, byte value){
		a.setX((byte) (a.getX() * value));
		a.setY((byte) (a.getY() * value));
		a.setZ((byte) (a.getZ() * value));
	}
	
	/*
	 * misc inplace vector ops
	 */

	public static void clamp(Byte3 x, byte min, byte max){
		x.setX(TornadoMath.clamp(x.getX(), min, max));
		x.setY(TornadoMath.clamp(x.getY(), min, max));
		x.setZ(TornadoMath.clamp(x.getZ(), min, max));
	}
	
	/*
	 * vector wide operations
	 */
	

	public static byte min(Byte3 value){
		return TornadoMath.min(value.getX(), TornadoMath.min(value.getY(),value.getZ()));
	}
	
	public static byte max(Byte3 value){
		return TornadoMath.max(value.getX(), TornadoMath.max(value.getY(),value.getZ()));
	}
	
	public static boolean isEqual(Byte3 a, Byte3 b){
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}
   
}
