/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.collections.types;

import java.nio.FloatBuffer;
import java.util.Arrays;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static java.util.Arrays.copyOfRange;
import static tornado.collections.types.FloatOps.fmt;
import static tornado.collections.types.StorageFormats.toRowMajor;



public class MatrixFloat  implements PrimitiveStorage<FloatBuffer> {
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
	final protected int M;
    
    /**
     * Number of columns
     */
	final protected int N;

    
	 /**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     * @param data array reference which contains data
     */
    public MatrixFloat(int width, int height, float[] array){
    	storage = array;
    	N = width;
    	M = height;
    	numElements = width * height;
    }
    
    /**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     */
    public MatrixFloat(int width,int height){
    	this(width,height,new float[width*height]);
    }

    
    public MatrixFloat(float[][] matrix){
    	this(matrix.length,matrix[0].length,toRowMajor(matrix));
    }
    
    public float get(int i, int j){
    	return storage[toRowMajor(i, j, N)];
    }
    
    public void set(int i, int j, float value){
    	storage[toRowMajor(i, j, N)] = value;
    }
    
    public int M(){
    	return M;
    }
    
    public int N(){
    	return N;
    }
    
    public VectorFloat row(int row){
    	int index = toRowMajor(row, 0, N);
    	return  new VectorFloat(N,copyOfRange(storage, index, N));
    }
    
    public VectorFloat column(int col){
    	int index = toRowMajor(0, col, N);
    	final VectorFloat v = new VectorFloat(M);
    	for(int i=0;i<M;i++)
    		v.set(i,storage[index + (i*N)]);
    	return v;
    }
    
    public VectorFloat diag(){
    	final VectorFloat v = new VectorFloat(min(M, N));
    	for(int i=0;i<M;i++)
    		v.set(i,storage[i*(N+1)]);
    	return v;
    }
//    
//    public MatrixFloat subMatrix(int i, int j, int m, int n){
//    	int index = getOffset() + StorageFormats.toRowMajor(i, j, LDA);
//    	MatrixFloat subM = new MatrixFloat(m,n,LDA,index,getStep(),getElementSize(),storage);
//    	return subM;
//    }
    
    public void fill(float value){
    	for(int i=0;i<storage.length;i++)
    		storage[i] = value;
    }
    
    public void multiply(MatrixFloat a, MatrixFloat b){
    	 for(int row=0; row < M(); row++){
             for(int col=0; col< N(); col++){
                 float sum = 0f;
                 for(int k=0; k < b.M(); k++){
                     sum += a.get(row, k) * b.get(k, col);
                 }
                 set(row, col, sum);
             }
         }
    }
    
    /**
     * Transposes the matrix in-place
     * @param m matrix to transpose
     */
    public static void transpose(MatrixFloat matrix) {

        if(matrix.N == matrix.M){
            // transpose square matrix
            for(int i=0;i<matrix.M;i++){
                for(int j=0;j<i;j++){
                    final float tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        } else {
            // transpose rectangular matrix
           
        	// not implemented
            
        }
    }
    
    public MatrixFloat duplicate(){
    	MatrixFloat matrix = new MatrixFloat(N,M);
    	matrix.set(this);
    	return matrix;
    }
    
    public void set(MatrixFloat m) {
    	for(int i=0;i<m.storage.length;i++)
				storage[i] = m.storage[i];
	}

  
//    @Deprecated
//	public void inverse2()
//    {
//    	MatrixFloat rref = duplicate();
//    	MatrixFloat ident = this;
//
//        ident.identity();
//        
//        for (int p = 0; p < rref.N(); ++p)
//        {
//            /* Make this pivot 1 */
//            final float pv = rref.get(p, p);
//            if (pv != 0)
//            {
//                final float pvInv = 1.0f / pv;
//                for (int i = 0; i < rref.M(); ++i)
//                {
//                	rref.set(i,p,rref.get(i, p) * pvInv);
//                	ident.set(i, p, ident.get(i,p) * pvInv);
//                }
//            }
//
//            /* Make other rows zero */
//            for (int r = 0; r < rref.M(); ++r)
//            {
//                if (r != p)
//                {
//                	final float f = rref.get(p, r);
//                    for (int i = 0; i < rref.N(); ++i)
//                    {
//                    	rref.set(i, r,  rref.get(i,r) - (f * rref.get(i, p)));
//                    	ident.set(i, r,  ident.get(i,r) - (f * ident.get(i, p)));
//                    }
//                }
//            }
//        }
//    }
    
    public String toString(String fmt){
    	 String str = "";

         for(int i=0;i<M;i++){
        	 for(int j=0;j<N;j++){
             str += format(fmt,get(i,j)) + " ";
        	 }
        	 str+= "\n";
         }
         str.trim();

         return str;
    }
    
    public String toString(){
    	String result = format("MatrixFloat <%d x %d>",M,N);
		 if(M<16 && N<16)
			result += "\n" + toString(fmt);
		return result;
	 }

	public static void scale(MatrixFloat matrix, float value) {
		for(int i=0;i<matrix.storage.length;i++)
			matrix.storage[i] *= value;
	}
//
//	@Override
//	public StorageFloat subVector(int start, int size) {
//		// TODO Auto-generated method stub
//		return null;
//	}

//	/**
//	 * Turns this matrix into an identity matrix
//	 */
//	public void identity() {
//		fill(0f);
//		diag().fill(1f);
//	}
    
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

}
