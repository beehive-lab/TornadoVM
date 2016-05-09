package tornado.collections.types;

import java.nio.ByteBuffer;
import tornado.collections.types.Byte3;
import tornado.collections.types.ByteOps;


public class ImageByte3  implements PrimitiveStorage<ByteBuffer> {
	/**
	 * backing array
	 */
	final protected byte[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private int			numElements;
	final private static int elementSize = 3;
	
	
    /**
     * Number of rows
     */
	final protected int Y;
    
    /**
     * Number of columns
     */
	final protected int X;
    
	/**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     * @param data array reference which contains data
     */
    public ImageByte3(int width, int height, byte[] array){
    	storage = array;
    	X = width;
    	Y = height;
    	numElements = X*Y*elementSize;
    }
    
    /**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     */
    public ImageByte3(int width,int height){
    	this(width,height,new byte[width*height*elementSize]);
    }
    
    public ImageByte3(byte[][] matrix){
    	this(matrix.length/elementSize,matrix[0].length/elementSize,StorageFormats.toRowMajor(matrix));
    }
    
    
    private final int toIndex(int x, int y){
    	return (x * elementSize) + (y * elementSize * X);
    }
    
    public Byte3 get(int x){
    	return get(x,0);
    }
    
    public void set(int x, Byte3 value){
    	set(x,0,value);
    }
    
    public Byte3 get(int x, int y){
    	final int offset = toIndex(x,y);
    	return Byte3.loadFromArray(storage, offset);
    }
    
    public void set(int x, int y, Byte3 value){
    	final int offset = toIndex(x,y);
    	value.storeToArray(storage, offset);
    }
    
    public int X(){
    	return X;
    }
    
    public int Y(){
    	return Y;
    }
    
    public void fill(byte value){
    	for(int i=0;i<storage.length;i++)
    		storage[i] = value;
    }
 
    
   
    
    public ImageByte3 duplicate(){
    	final ImageByte3 image = new ImageByte3(X,Y);
    	image.set(this);
    	return image;
    }
    
    public void set(ImageByte3 m) {
    	for(int i=0;i<storage.length;i++)
    		storage[i] = m.storage[i];
	}

    
    public String toString(String fmt){
    	 String str = "";

    	 for(int i=0;i<Y;i++){
        	 for(int j=0;j<X;j++){
        		 str += get(j,i).toString(fmt) + "\n";
        	 }
         }

         return str;
    }
    
    public String toString(){
    	String result = String.format("ImageByte3 <%d x %d>",X,Y);
		 if(X<=8 && Y<=8)
			result += "\n" + toString(ByteOps.fmt3);
		return result;
	 }

	
    @Override
	public void loadFromBuffer(ByteBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public ByteBuffer asBuffer() {
		return ByteBuffer.wrap(storage);
	}

	@Override
	public int size() {
		return numElements;
	}

}
