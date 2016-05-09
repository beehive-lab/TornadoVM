package tornado.collections.types;

import java.nio.FloatBuffer;

import tornado.collections.types.StorageFormats;
import tornado.common.exceptions.TornadoInternalError;

public class ImageFloat3  implements PrimitiveStorage<FloatBuffer> {
    
	/**
	 * backing array
	 */
	final protected float[]				storage;

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
    public ImageFloat3(int width, int height, float[] array){
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
    public ImageFloat3(int width,int height){
    	this(width,height,new float[width*height*elementSize]);
    }
    
    public ImageFloat3(float[][] matrix){
    	this(matrix.length/elementSize,matrix[0].length/elementSize,StorageFormats.toRowMajor(matrix));
    }
    
    
    private final int toIndex(int x, int y){
    	return elementSize * (x + (y * X));
    }
    
    public Float3 get(int x){
    	return get(x,0);
    }
    
    public void set(int x, Float3 value){
    	set(x,0,value);
    }
    
    public Float3 get(int x, int y){
    	final int offset = toIndex(x,y);
    	return Float3.loadFromArray(storage, offset);
    }
    
    public void set(int x, int y, Float3 value){
    	final int offset = toIndex(x,y);
    	value.storeToArray(storage, offset);
    }
    
    public int X(){
    	return X;
    }
    
    public int Y(){
    	return Y;
    }
    
    @Deprecated
    public VectorFloat3 row(int row){
    	TornadoInternalError.shouldNotReachHere();
    	return null;
//    	int index = toIndex(0,row);
//    	VectorFloat3 v = new VectorFloat3(X,index,1,getElementSize(),storage);
//    	return v;
    }
    
    @Deprecated
    public VectorFloat3 column(int col){
    	TornadoInternalError.shouldNotReachHere();
    	return null;
//    	int index = toIndex(col, 0);
//    	VectorFloat3 v = new VectorFloat3(Y,index,getStep(),getElementSize(),storage );
//    	return v;
    }
    
    @Deprecated
    public VectorFloat3 diag(){
    	TornadoInternalError.shouldNotReachHere();
    	return null;
//    	VectorFloat3 v = new VectorFloat3(Math.min(X,Y), getOffset(), getStep() + 1,getElementSize(),storage);
//    	return v;
    }
    
    @Deprecated
    public ImageFloat3 subImage(int x0, int y0, int x1, int y1){
    	TornadoInternalError.shouldNotReachHere();
    	return null;
//    	int index = get(x0,y0).getOffset();
//    	ImageFloat3 subM = new ImageFloat3(x1,y1,index,getStep(),getElementSize(),storage);
//    	return subM;
    }
    
    public void fill(float value){
    	for(int i=0;i<storage.length;i++)
    		storage[i] = value;
    }
 
    
    /**
     * Transposes the matrix in-place
     * @param m matrix to transpose
     */
    @Deprecated
    public void transpose() {
    	TornadoInternalError.shouldNotReachHere();

//        if(X == Y){
//            // transpose square matrix
//            for(int i=0;i<Y;i++){
//                for(int j=0;j<i;j++){
//                    Float3 tmp = new Float3();
//                    tmp.set(get(i, j));
//                    set(i, j, get(j, i));
//                    set(j, i, tmp);
//                }
//            }
//        } else {
//            // transpose rectangular matrix
//           
//        	// not implemented
//            
//        }
    }
    
    public ImageFloat3 duplicate(){
    	ImageFloat3 matrix = new ImageFloat3(X,Y);
    	matrix.set(this);
    	return matrix;
    }
    
    public void set(ImageFloat3 m) {
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
    	String result = String.format("ImageFloat3 <%d x %d>",X,Y);
		 if(X<=8 && Y<=8)
			result += "\n" + toString(FloatOps.fmt3);
		return result;
	 }

    @Deprecated
	public void scale(float alpha) {
    	TornadoInternalError.shouldNotReachHere();
	}

	
	public Float3 mean(){
		Float3 result = new Float3();
		for(int row = 0;row<Y;row++)
			for(int col = 0;col<X;col++)
				result = Float3.add(result, get(col,row));
		
		return Float3.div(result,(float) (X*Y));
	}
	
	public Float3 min(){
		Float3 result = new Float3(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
			
		for(int row = 0;row<Y;row++)
			for(int col = 0;col<X;col++)
				result = Float3.min(result, get(col,row));
		
		return result;
	}
	
	public Float3 max(){
		Float3 result = new Float3(Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE);
		
		for(int row = 0;row<Y;row++)
			for(int col = 0;col<X;col++)
				result = Float3.max(result, get(col,row));
		
		return result;
	}
	
	public Float3 stdDev(){
		final Float3 mean = mean();
		Float3 varience = new Float3();
		for(int row = 0;row<Y;row++){
			for(int col = 0;col<X;col++){
				Float3 v = Float3.sub(mean, get(col,row));
				v = Float3.mult(v,v);
				v = Float3.div(v, (float) X);
				varience = Float3.add (v , varience);
			}
		}
		
		return Float3.sqrt(varience);
	}
	
	public String summerise(){
		return String.format("ImageFloat3<%dx%d>: min=%s, max=%s, mean=%s, sd=%s",X,Y,min(),max(),mean(),stdDev());
	}
	
    @Override
	public void loadFromBuffer(FloatBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public FloatBuffer asBuffer() {
		return FloatBuffer.wrap(storage);
	}

	@Override
	public int size() {
		return numElements;
	}
	
	public FloatingPointError calculateULP(ImageFloat3 ref){
		float maxULP = Float.MIN_VALUE;
		float minULP = Float.MAX_VALUE;
		float averageULP = 0f;
		
		/*
		 * check to make sure dimensions match
		 */
		if(ref.X != X && ref.Y != Y){
			return new FloatingPointError(-1f,0f,0f,0f);
		}

		int errors = 0;
		for(int j=0;j<Y;j++){
			for(int i=0;i<X;i++){
				final Float3 v = get(i, j);
				final Float3 r = ref.get(i, j);
				
				final float ulpFactor = Float3.findULPDistance(v, r);
				averageULP += ulpFactor;
				minULP = Math.min(ulpFactor, minULP);
				maxULP = Math.max(ulpFactor, maxULP);
				
				if(ulpFactor > 5f){
					errors++;
					if(i==318 && j==239)
					System.out.printf("[%d, %d]: %f -> error %s != %s\n",i,j,ulpFactor,v.toString(FloatOps.fmt3e),r.toString(FloatOps.fmt3e));
				}
				
			}
		}
		
		averageULP /= (float) X * Y;

		return new FloatingPointError(averageULP, minULP, maxULP, -1f, errors);
	}

}
