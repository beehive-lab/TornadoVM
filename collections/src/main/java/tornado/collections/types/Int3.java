package tornado.collections.types;

import java.nio.IntBuffer;

import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 3x ints
 * e.g. <int,int,int>
 * @author jamesclarkson
 *
 */
@Vector 
public class Int3 implements PrimitiveStorage<IntBuffer> {
	public static final Class<Int3>	TYPE		= Int3.class;
	
	private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d }";
    
	/**
	 * backing array
	 */
	@Payload final protected int[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 3;
    
	public Int3(int[] storage) {
		this.storage = storage;
	}

	public Int3() {
		this(new int[numElements]);
	}

	public Int3(int x, int y, int z){
		this();
		setX(x);
		setY(y);
		setZ(z);
	}

	public int get(int index) {
		return storage[index];
	}

	public void set(int index, int value) {
		storage[index] = value;
	}
	
	public void set(Int3 value){
		setX(value.getX());
		setY(value.getY());
		setZ(value.getZ());
	}
    
    public int getX(){
    	return get(0);
    }
    
    public int getY(){
    	return get(1);
    }
    
    public int getZ(){
    	return get(2);
    }
    
    public void setX(int value){
    	set(0,value);
    }
    
    public void setY(int value){
    	set(1,value);
    }
    
    public void setZ(int value){
    	set(2,value);
    }
    
	/**
	 * Duplicates this vector
	 * @return
	 */
	public Int3 duplicate(){
		Int3 vector = new Int3();
		vector.set(this);
		return vector;
	}
	
	public Int2 asInt2(){
		return new Int2(getX(),getY());
	}
       
    public String toString(String fmt){
        return String.format(fmt, getX(),getY(),getZ());
   }
   
   public String toString(){
		return toString(numberFormat);
   }
   
   protected static final Int3 loadFromArray(final int[] array, int index){
		final Int3 result = new Int3();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		result.setZ(array[index + 2]);
		return result;
	}
	
	protected final void storeToArray(final int[] array, int index){
		array[index] = getX();
		array[index+1] = getY();
		array[index+2] = getZ();
	}

   @Override
	public void loadFromBuffer(IntBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public IntBuffer asBuffer() {
		return IntBuffer.wrap(storage);
	}

	public int size() {
		return numElements;
	}
	
	/*
	 * vector = op( vector, vector )
	 */
	public static Int3 add(Int3 a, Int3 b){
		return new Int3(a.getX() + b.getX(),a.getY() + b.getY(),a.getZ() + b.getZ());
	}
	
	public static Int3 sub(Int3 a, Int3 b){
		return new Int3(a.getX() - b.getX(),a.getY() - b.getY(),a.getZ() - b.getZ());
	}
	
	public static Int3 div(Int3 a, Int3 b){
		return new Int3(a.getX() / b.getX(),a.getY() / b.getY(),a.getZ() / b.getZ());
	}
	
	public static Int3 mult(Int3 a, Int3 b){
		return new Int3(a.getX() * b.getX(),a.getY() * b.getY(),a.getZ() * b.getZ());
	}
	
	public static Int3 min(Int3 a, Int3 b){
		return new Int3(Math.min(a.getX() , b.getX()),Math.min(a.getY() , b.getY()),Math.min(a.getZ() , b.getZ()));
	}
	
	public static Int3 max(Int3 a, Int3 b){
		return new Int3(Math.max(a.getX() , b.getX()),Math.max(a.getY() , b.getY()),Math.max(a.getZ() , b.getZ()));
	}
	
	/*
	 * vector = op (vector, scalar)
	 */
	
	public static Int3 add(Int3 a, int b){
		return new Int3(a.getX() + b,a.getY() + b,a.getZ() + b);
	}
	
	public static Int3 sub(Int3 a, int b){
		return new Int3(a.getX() - b,a.getY() - b,a.getZ() - b);
	}
	
	public static Int3 mult(Int3 a, int b){
		return new Int3(a.getX() * b,a.getY() * b,a.getZ() * b);
	}
	
	public static Int3 div(Int3 a, int b){
		return new Int3(a.getX() / b,a.getY() / b,a.getZ() / b);
	}
	
	/*
	 * vector = op (vector, vector)
	 */
	
	public static void add(Int3 a, Int3 b, Int3 c){
		c.setX(a.getX() + b.getX());
		c.setY(a.getY() + b.getY());
		c.setZ(a.getZ() + b.getZ());
	}
	
	public static void sub(Int3 a, Int3 b, Int3 c){
		c.setX(a.getX() - b.getX());
		c.setY(a.getY() - b.getY());
		c.setZ(a.getZ() - b.getZ());
	}
	
	public static void mult(Int3 a, Int3 b, Int3 c){
		c.setX(a.getX() * b.getX());
		c.setY(a.getY() * b.getY());
		c.setZ(a.getZ() * b.getZ());
	}
	
	public static void div(Int3 a, Int3 b, Int3 c){
		c.setX(a.getX() / b.getX());
		c.setY(a.getY() / b.getY());
		c.setZ(a.getZ() / b.getZ());
	}
	
	public static void min(Int3 a, Int3 b, Int3 c){
		c.setX(Math.min(a.getX() , b.getX()));
		c.setY(Math.min(a.getY() , b.getY()));
		c.setZ(Math.min(a.getZ() , b.getZ()));
	}
	
	public static void max(Int3 a, Int3 b, Int3 c){
		c.setX(Math.max(a.getX() , b.getX()));
		c.setY(Math.max(a.getY() , b.getY()));
		c.setZ(Math.max(a.getZ() , b.getZ()));
	}
	
	/*
	 *  inplace src = op (src, scalar)
	 */
	
	public static void inc(Int3 a, int value){
		a.setX(a.getX() + value);
		a.setY(a.getY() + value);
		a.setZ(a.getZ() + value);
	}
	
	
	public static void dec(Int3 a, int value){
		a.setX(a.getX() - value);
		a.setY(a.getY() - value);
		a.setZ(a.getZ() - value);
	}
	
	
	public static void scale(Int3 a, int value){
		a.setX(a.getX() * value);
		a.setY(a.getY() * value);
		a.setZ(a.getZ() * value);
	}
	
	/*
	 * misc inplace vector ops
	 */

	public static void clamp(Int3 x, int min, int max){
		x.setX(TornadoMath.clamp(x.getX(), min, max));
		x.setY(TornadoMath.clamp(x.getY(), min, max));
		x.setZ(TornadoMath.clamp(x.getZ(), min, max));
	}
	
	/*
	 * vector wide operations
	 */
	

	public static int min(Int3 value){
		return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
	}
	
	public static int max(Int3 value){
		return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
	}
	
	public static boolean isEqual(Int3 a, Int3 b){
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}
}
