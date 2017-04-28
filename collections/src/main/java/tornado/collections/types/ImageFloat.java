/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.collections.types;

import java.nio.FloatBuffer;
import tornado.api.Parallel;
import tornado.collections.math.TornadoMath;
import tornado.common.exceptions.TornadoInternalError;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.String.format;
import static java.lang.System.arraycopy;
import static java.nio.FloatBuffer.wrap;
import static tornado.collections.math.TornadoMath.sqrt;
import static tornado.collections.types.FloatOps.findMaxULP;
import static tornado.collections.types.FloatOps.fmt;
import static tornado.collections.types.StorageFormats.toRowMajor;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;


public class ImageFloat  implements PrimitiveStorage<FloatBuffer> {

	/**
	 * backing array
	 */
	final protected float[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private int			numElements;

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
    public ImageFloat(int width, int height, float[] array){
    	storage = array;
    	X = width;
    	Y = height;
    	numElements = X*Y;
    }

    /**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     */
    public ImageFloat(int width,int height){
    	this(width,height,new float[width*height]);
    }


    public ImageFloat(float[][] matrix){
    	this(matrix.length,matrix[0].length,toRowMajor(matrix));
    }

    public float get(int i){
    	return storage[i];
    }

    public void set(int i, float value){
    	storage[i] = value;
    }


    /***
     * returns the ith column of the jth row
     * @param i row index
     * @param j column index
     * @return
     */
    public float get(int i, int j){
    	return storage[toRowMajor(j, i, X)];
    }

    /***
     * sets the ith column of the jth row to value
     * @param i row index
     * @param j column index
     * @param value new value
     */
    public void set(int i, int j, float value){
    	storage[toRowMajor(j, i, X)] = value;
    }

    public void put(float[] array){
    	arraycopy(array, 0, storage, 0, array.length);
    }

    public int Y(){
    	return Y;
    }

    public int X(){
    	return X;
    }

//    public VectorFloat row(int row){
//    	int index = getOffset() + StorageFormats.toRowMajor(row,0, LDA);
//    	VectorFloat v = new VectorFloat(X,index,getStep(),getElementSize(),storage );
//    	return v;
//    }
//
//    public VectorFloat column(int col){
//    	int index = getOffset() + StorageFormats.toRowMajor(0, col, LDA);
//    	VectorFloat v = new VectorFloat(Y,index,LDA,getElementSize(),storage );
//    	return v;
//    }
//
//    public VectorFloat diag(){
//    	VectorFloat v = new VectorFloat(Math.min(Y,X), getOffset(), LDA + 1,getElementSize(),storage);
//    	return v;
//    }
//
//    public ImageFloat subImage(int x0, int y0, int x1, int y1){
//    	int index = getOffset() + StorageFormats.toRowMajor(y0, x0, LDA);
//    	ImageFloat subM = new ImageFloat(x1,y1,LDA,index,getStep(),getElementSize(),storage);
//    	return subM;
//    }

    public void fill(float value){
    	for(@Parallel int i=0;i<Y;i++)
    		for(@Parallel int j=0;j<X;j++)
    			set(i,j,value);
    }

    @Deprecated
    public void multiply(ImageFloat a, ImageFloat b){
    	shouldNotReachHere();
    	int i,j,k;
		for(i=0;i<Y;i++)
			for(j=0;j<X;j++){
				float sum = 0.0f;
				for(k=0;k<a.Y;k++){
					sum += a.get(i, k) * b.get(k, j);
				}
				set(i, j, sum);
			}
    }

    /**
     * Transposes the matrix in-place
     * @param m matrix to transpose
     */
    @Deprecated
    public void transpose() {
    	shouldNotReachHere();
        if(X == Y){
            // transpose square matrix
            for(int i=0;i<Y;i++){
                for(int j=0;j<i;j++){
                    float tmp = get(i, j);
                    set(i, j, get(j, i));
                    set(j, i, tmp);
                }
            }
        } else {
            // transpose rectangular matrix

        	// not implemented

        }
    }

    public ImageFloat duplicate(){
    	final ImageFloat matrix = new ImageFloat(X,Y);
    	matrix.set(this);
    	return matrix;
    }

    public void set(ImageFloat m) {
    	for(int i=0;i<storage.length;i++)
    		storage[i] = m.storage[i];
	}


    public String toString(String fmt){
    	 String str = "";

    	 for(int i=0;i<Y;i++){
        	 for(int j=0;j<X;j++){
        		 str += format(fmt,get(j,i)) + " ";
        	 }
        	 str += "\n";
         }

         return str;
    }

    public String toString(){
    	String result = format("ImageFloat <%d x %d>",X,Y);
		 if(Y<16 && X<16)
			result += "\n" + toString(fmt);
		return result;
	 }

	public static void scale(ImageFloat image,  float alpha) {
    	for(int i=0;i<image.storage.length;i++ )
    		image.storage[i] *= alpha;
	}


	public float mean(){
		float result = 0f;
		for(int i=0;i<storage.length;i++)
			result += storage[i];
		return result / (float) (X*Y);
	}

	public float min(){
		float result = MAX_VALUE;
			for(int i=0;i<storage.length;i++)
				result = Math.min(result,storage[i]);
		return result;
	}

	public float max(){
		float result = MIN_VALUE;
		for(int i=0;i<storage.length;i++)
			result = Math.max(result,storage[i]);
		return result;
	}

	public float stdDev(){
		final float mean = mean();
		float varience = 0f;
		for(int i=0;i<storage.length;i++){
			float v = storage[i];
			v -= mean;
			v *=v;
			varience = v / (float) X;
		}
		return sqrt(varience);
	}

	public String summerise(){
		return format("ImageFloat<%dx%d>: min=%e, max=%e, mean=%e, sd=%e",X,Y,min(),max(),mean(),stdDev());
	}

	 @Override
		public void loadFromBuffer(FloatBuffer buffer) {
			asBuffer().put(buffer);
		}

		@Override
		public FloatBuffer asBuffer() {
			return wrap(storage);
		}

		@Override
		public int size() {
			return numElements;
		}

		public FloatingPointError calculateULP(ImageFloat ref){
			float maxULP = MIN_VALUE;
			float minULP = MAX_VALUE;
			float averageULP = 0f;

			/*
			 * check to make sure dimensions match
			 */
			if(ref.X != X && ref.Y != Y){
				return new FloatingPointError(-1f,0f,0f,0f);
			}

			for(int j=0;j<Y;j++){
				for(int i=0;i<X;i++){
					final float v = get(i, j);
					final float r = ref.get(i, j);

					final float ulpFactor = findMaxULP(v, r);
					averageULP += ulpFactor;
					minULP = Math.min(ulpFactor, minULP);
					maxULP = Math.max(ulpFactor, maxULP);

				}
			}

			averageULP /= (float) X * Y;

			return new FloatingPointError(averageULP, minULP, maxULP, -1f);
		}

//	public void map(ImageFloat dest, FloatToFloatFunction function){
//		for(@Parallel int i=0;i<Y;i++)
//			for(@Parallel int j=0;j<X;j++)
//				dest.set(i,j,function.apply(get(i,j)));
//	}
//
//	public void apply(FloatToFloatFunction function){
//		map(this,function);
//	}
//
//	/*
//	 * maps an image patch to a float
//	 */
//	public void stencilMap(ImageFloat dest, int rx, int ry, ToFloatFunction<ImageFloat> function){
//		for(@Parallel int i=0;i<Y;i++)
//			for(@Parallel int j=0;j<X;j++){
//				int x0 = Math.max(j-rx,0);
//				int y0 = Math.max(i-ry,0);
//				int sx = (j - x0) + Math.min(j+rx,X) - j;
//				int sy = (i - y0) + Math.min(i+ry,Y) - i;
//				final ImageFloat patch = subImage(x0,y0, sx,sy);
//				dest.set(i,j,function.apply(patch));
//			}
//	}
//
//	public void stencilApply(int rx, int ry, ToFloatFunction<ImageFloat> function){
//		stencilMap(this,rx,ry,function);
//	}

}
