package tornado.collections.types;

import java.nio.ShortBuffer;

import tornado.collections.api.Payload;
import tornado.collections.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 3x shorts
 * e.g. <short,short,short>
 * @author jamesclarkson
 *
 */
@Vector
public class Short3 implements PrimitiveStorage<ShortBuffer> {
	public static final Class<Short3>	TYPE		= Short3.class;
	
	
	private static final String numberFormat = "{ x=%-7d, y=%-7d, z=%-7d }";
    
	/**
	 * backing array
	 */
	@Payload final protected short[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 3;
    
	public Short3(short[] storage) {
		this.storage = storage;
	}

	public Short3() {
		this(new short[numElements]);
	}

	public Short3(short x, short y, short z) {
		this();
		setX(x);
		setY(y);
		setZ(z);
	}
	
	public void set(Short3 value){
		setX(value.getX());
		setY(value.getY());
		setZ(value.getZ());
	}
	
	public short get(int index) {
		return storage[index];
	}

	public void set(int index, short value) {
		storage[index] = value;
	}
    
    public short getX(){
    	return get(0);
    }
    
    public short getY(){
    	return get(1);
    }
    
    public short getZ(){
    	return get(2);
    }
    
    public void setX(short value){
    	set(0,value);
    }
    
    public void setY(short value){
    	set(1,value);
    }
    
    public void setZ(short value){
    	set(2,value);
    }
    
	/**
	 * Duplicates this vector
	 * @return
	 */
	public Short3 duplicate(){
		Short3 vector = new Short3();
		vector.set(this);
		return vector;
	}
       
    public String toString(String fmt){
        return String.format(fmt, getX(),getY(),getZ());
   }
   
   public String toString(){
		return toString(numberFormat);
   }
   
   protected static final Short3 loadFromArray(final short[] array, int index){
		final Short3 result = new Short3();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		result.setZ(array[index + 2]);
		return result;
	}
	
	protected final void storeToArray(final short[] array, int index){
		array[index] = getX();
		array[index+1] = getY();
		array[index+2] = getZ();
	}

	 @Override
		public void loadFromBuffer(ShortBuffer buffer) {
			asBuffer().put(buffer);
		}

		@Override
		public ShortBuffer asBuffer() {
			return ShortBuffer.wrap(storage);
		}

		public int size() {
			return numElements;
		}
		
		/*
		 * vector = op( vector, vector )
		 */
		public static Short3 add(Short3 a, Short3 b){
			return new Short3((short) (a.getX() + b.getX()), (short) (a.getY() + b.getY()),  (short) (a.getZ() + b.getZ()));
		}
		
		public static Short3 sub(Short3 a, Short3 b){
			return new Short3((short) (a.getX() - b.getX()), (short) (a.getY() - b.getY()),(short) (a.getZ() - b.getZ()));
		}
		
		public static Short3 div(Short3 a, Short3 b){
			return new Short3((short) (a.getX() / b.getX()), (short) (a.getY() / b.getY()),(short) (a.getZ() / b.getZ()));
		}
		
		public static Short3 mult(Short3 a, Short3 b){
			return new Short3((short) (a.getX() * b.getX()), (short) (a.getY() * b.getY()),(short) (a.getZ() * b.getZ()));
		}
		
		public static Short3 min(Short3 a, Short3 b){
			return new Short3(TornadoMath.min(a.getX() , b.getX()),TornadoMath.min(a.getY() , b.getY()),TornadoMath.min(a.getZ() , b.getZ()));
		}
		
		public static Short3 max(Short3 a, Short3 b){
			return new Short3(TornadoMath.max(a.getX() , b.getX()),TornadoMath.max(a.getY() , b.getY()),TornadoMath.max(a.getZ() , b.getZ()));
		}
		
		/*
		 * vector = op (vector, scalar)
		 */
		
		public static Short3 add(Short3 a, short b){
			return new Short3((short) (a.getX() + b), (short) (a.getY() + b), (short) (a.getZ() + b));
		}
		
		public static Short3 sub(Short3 a, short b){
			return new Short3((short) (a.getX() - b), (short) (a.getY() - b), (short) (a.getZ() - b));
		}
		
		public static Short3 mult(Short3 a, short b){
			return new Short3((short) (a.getX() * b), (short) (a.getY() * b), (short) (a.getZ() * b));
		}
		
		public static Short3 div(Short3 a, short b){
			return new Short3((short) (a.getX() / b), (short) (a.getY() / b), (short) (a.getZ() / b));
		}
		
		/*
		 * vector = op (vector, vector)
		 */
		
		public static void add(Short3 a, Short3 b, Short3 c){
			c.setX((short) (a.getX() + b.getX()));
			c.setY((short) (a.getY() + b.getY()));
			c.setZ((short) (a.getZ() + b.getZ()));
		}
		
		public static void sub(Short3 a, Short3 b, Short3 c){
			c.setX((short) (a.getX() - b.getX()));
			c.setY((short) (a.getY() - b.getY()));
			c.setZ((short) (a.getZ() - b.getZ()));
		}
		
		public static void mult(Short3 a, Short3 b, Short3 c){
			c.setX((short) (a.getX() * b.getX()));
			c.setY((short) (a.getY() * b.getY()));
			c.setZ((short) (a.getZ() * b.getZ()));
		}
		
		public static void div(Short3 a, Short3 b, Short3 c){
			c.setX((short) (a.getX() / b.getX()));
			c.setY((short) (a.getY() / b.getY()));
			c.setZ((short) (a.getZ() / b.getZ()));
		}
		
		public static void min(Short3 a, Short3 b, Short3 c){
			c.setX(TornadoMath.min(a.getX() , b.getX()));
			c.setY(TornadoMath.min(a.getY() , b.getY()));
			c.setZ(TornadoMath.min(a.getZ() , b.getZ()));
		}
		
		public static void max(Short3 a, Short3 b, Short3 c){
			c.setX(TornadoMath.max(a.getX() , b.getX()));
			c.setY(TornadoMath.max(a.getY() , b.getY()));
			c.setZ(TornadoMath.max(a.getZ() , b.getZ()));
		}
		
		/*
		 *  inplace src = op (src, scalar)
		 */
		
		public static void inc(Short3 a, short value){
			a.setX((short) (a.getX() + value));
			a.setY((short) (a.getY() + value));
			a.setZ((short) (a.getZ() + value));
		}
		
		
		public static void dec(Short3 a, short value){
			a.setX((short) (a.getX() - value));
			a.setY((short) (a.getY() - value));
			a.setZ((short) (a.getZ() - value));
		}
		
		
		public static void scale(Short3 a, short value){
			a.setX((short) (a.getX() * value));
			a.setY((short) (a.getY() * value));
			a.setZ((short) (a.getZ() * value));
		}
		
		/*
		 * misc inplace vector ops
		 */

		public static void clamp(Short3 x, short min, short max){
			x.setX(TornadoMath.clamp(x.getX(), min, max));
			x.setY(TornadoMath.clamp(x.getY(), min, max));
			x.setZ(TornadoMath.clamp(x.getZ(), min, max));
		}
		
		/*
		 * vector wide operations
		 */
		

		public static short min(Short3 value){
			return TornadoMath.min(value.getX(), TornadoMath.min(value.getY(),value.getZ()));
		}
		
		public static short max(Short3 value){
			return TornadoMath.max(value.getX(), TornadoMath.max(value.getY(),value.getZ()));
		}
		
		public static boolean isEqual(Short3 a, Short3 b){
			return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
		}
  
}
