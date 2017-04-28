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

import java.nio.IntBuffer;
import java.util.Arrays;



public class MatrixInt  implements PrimitiveStorage<IntBuffer> {
	/**
	 * backing array
	 */
	final protected int[]				storage;

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
    public MatrixInt(int width, int height, int[] array){
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
    public MatrixInt(int width,int height){
    	this(width,height,new int[width*height]);
    }

    
    public MatrixInt(int[][] matrix){
    	this(matrix.length,matrix[0].length,StorageFormats.toRowMajor(matrix));
    }
    
    public int get(int i, int j){
    	return storage[StorageFormats.toRowMajor(i, j, N)];
    }
    
    public void set(int i, int j, int value){
    	storage[StorageFormats.toRowMajor(i, j, N)] = value;
    }
    
    public int M(){
    	return M;
    }
    
    public int N(){
    	return N;
    }
    
    public VectorInt row(int row){
    	int index = StorageFormats.toRowMajor(row, 0, N);
    	return  new VectorInt(N,Arrays.copyOfRange(storage, index, N));
    }
    
    public VectorInt column(int col){
    	int index = StorageFormats.toRowMajor(0, col, N);
    	final VectorInt v = new VectorInt(M);
    	for(int i=0;i<M;i++)
    		v.set(i,storage[index + (i*N)]);
    	return v;
    }
    
    public VectorInt diag(){
    	final VectorInt v = new VectorInt(Math.min(M, N));
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
    
    public void fill(int value){
    	for(int i=0;i<storage.length;i++)
    		storage[i] = value;
    }
    
    public void multiply(MatrixInt a, MatrixInt b){
    	 for(int row=0; row < M(); row++){
             for(int col=0; col< N(); col++){
                 int sum = 0;
                 for(int k=0; k < b.M(); k++){
                     sum += a.get(row, k) * b.get(k, col);
                 }
                 set(row, col, sum);
             }
         }
    }
    
    public void tmultiply(MatrixInt a, MatrixInt b){
    	System.out.printf("tmult: M=%d (expect %d)\n", M(),a.M());
        System.out.printf("tmult: N=%d (expect %d)\n", N(),b.M());
        for(int row=0; row < M(); row++){
             for(int col=0; col< b.M(); col++){
                 int sum = 0;
                 for(int k=0; k < b.N(); k++){
                     sum += a.get(row, k) * b.get(col, k);
                 }
                 set(row, col, sum);
             }
         }
    }
    
    /**
     * Transposes the matrix in-place
     * @param m matrix to transpose
     */
    public static void transpose(MatrixInt matrix) {

        if(matrix.N == matrix.M){
            // transpose square matrix
            for(int i=0;i<matrix.M;i++){
                for(int j=0;j<i;j++){
                    final int tmp = matrix.get(i, j);
                    matrix.set(i, j, matrix.get(j, i));
                    matrix.set(j, i, tmp);
                }
            }
        } else {
            // transpose rectangular matrix
           
        	// not implemented
            
        }
    }
    
    public MatrixInt duplicate(){
    	MatrixInt matrix = new MatrixInt(N,M);
    	matrix.set(this);
    	return matrix;
    }
    
    public void set(MatrixInt m) {
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
//            final int pv = rref.get(p, p);
//            if (pv != 0)
//            {
//                final int pvInv = 1.0f / pv;
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
//                	final int f = rref.get(p, r);
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
             str += String.format(fmt,get(i,j)) + " ";
        	 }
        	 str+= "\n";
         }

         return str.trim();
    }
    
        @Override
    public String toString(){
    	String result = String.format("MatrixInt <%d x %d>",M,N);
		 if(M<16 && N<16)
			result += "\n" + toString(IntOps.fmt);
		return result;
	 }

	public static void scale(MatrixInt matrix, int value) {
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
   	public void loadFromBuffer(IntBuffer buffer) {
   		asBuffer().put(buffer);
   	}

   	@Override
   	public IntBuffer asBuffer() {
   		return IntBuffer.wrap(storage);
   	}

   	@Override
   	public int size() {
   		return numElements;
   	}

}
