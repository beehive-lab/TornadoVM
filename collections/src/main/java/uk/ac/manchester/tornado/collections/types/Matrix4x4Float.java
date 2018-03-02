/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.types;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.nio.FloatBuffer.wrap;
import static uk.ac.manchester.tornado.collections.types.Float4.loadFromArray;
import static uk.ac.manchester.tornado.collections.types.FloatOps.findMaxULP;
import static uk.ac.manchester.tornado.collections.types.FloatOps.fmt4m;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

import java.nio.FloatBuffer;


public class Matrix4x4Float implements PrimitiveStorage<FloatBuffer> {
    
	/**
	 * backing array
	 */
	final protected float[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 16;

	
    /**
     * Number of rows
     */
	final protected int M = 4;
    
    /**
     * Number of columns
     */
	final protected int N = 4;
    
    
    public Matrix4x4Float(){
    	this(new float[numElements]);
    }
    
    public Matrix4x4Float(float[] array){
    	storage = array;
    }
    
    private int toIndex(int i, int j){
    	return j + (i * N);
    }
    
    private float get(int index){
    	return storage[index];
    }
    
    private void set(int index, float value){
    	storage[index] = value;
    }
    
    /**
     * Returns the value
     * @param i row index
     * @param j col index
     * @return
     */
    public float get(int i, int j){
    	return storage[toIndex(i,j)];
    }
    
    /**
     * Sets the value
     * @param i row index
     * @param j col index
     * @return
     */
    public void set(int i, int j, float value){
    	storage[toIndex(i,j)] = value;
    }
    
    /**
     * Returns the number of rows in this matrix
     * @return
     */
    public int M(){
    	return M;
    }
    
    /**
     * Returns the number of columns in the matrix
     * @return
     */
    public int N(){
    	return N;
    }
    
    public Float4 row(int row){
    	int offset = M * row;
    	return loadFromArray(storage, offset);
    }
    
    public Float4 column(int col){
    	return new Float4(get(col),get(col+M),get(col+(2*M)),get(col+(3*M)));
    }
    
    public Float4 diag(){
    	return new Float4(get(0),get(1+M),get(2+(2*M)),get(3+(3*M)));
    }
    

    public void fill(float value){
    	for(int i=0;i<storage.length;i++)
    		storage[i] = value;
    }
    
    @Deprecated
    public void multiply(Matrix4x4Float a, Matrix4x4Float b){
    	shouldNotReachHere();
//    	 for(int row=0; row < M(); row++){
//             for(int col=0; col< N(); col++){
//                 //final float sum = VectorFloatOps.dot(a.row(row),b.column(col));
//                 //set(row, col, sum);
//             }
//         }
    }
    
    /**
     * Transposes the matrix in-place
     * @param m matrix to transpose
     */
    @Deprecated
    public void transpose() {
    	shouldNotReachHere();
      
//            // transpose square matrix
//            for(int i=0;i<M;i++){
//                for(int j=0;j<i;j++){
//                    float tmp = get(i, j);
//                    set(i, j, get(j, i));
//                    set(j, i, tmp);
//                }
//            }
    }
    
    public Matrix4x4Float duplicate(){
    	Matrix4x4Float matrix = new Matrix4x4Float();
    	matrix.set(this);
    	return matrix;
    }
    
    public void set(Matrix4x4Float m) {
		for(int i=0;i<M;i++){
			int offset = M * i;
    		m.row(i).storeToArray(storage, offset);
		}
	}

  
    @Deprecated
	public void inverse2()
    {
    	shouldNotReachHere();
//    	Matrix4x4Float rref = duplicate();
//    	Matrix4x4Float ident = this;
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
    }
    
    public String toString(String fmt){
    	 String str = "";

         for(int i=0;i<M;i++){
             str += row(i).toString(fmt) + "\n";
         }
         str.trim();

         return str;
    }
    
    public String toString(){
    	String result = format("MatrixFloat <%d x %d>",M,N);
		result += "\n" + toString(fmt4m);
		return result;
	 }

    @Deprecated
	public void scale(float alpha) {
    	shouldNotReachHere();
//		for(int i=0;i<M;i++)
//			row(i).scale(alpha);
	}

	/**
	 * Turns this matrix into an identity matrix
	 */
    @Deprecated
	public void identity() {
    	//TornadoInternalError.shouldNotReachHere();
		fill(0f);
		set(0,1f);
		set(1+M,1f);
		set(2+(2*M),1f);
		set(3+(3*M),1f);
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
	
	public FloatingPointError calculateULP(Matrix4x4Float ref){
		float maxULP = MIN_VALUE;
		float minULP = MAX_VALUE;
		float averageULP = 0f;
		
		/*
		 * check to make sure dimensions match
		 */
		if(ref.M != M && ref.N != N){
			return new FloatingPointError(-1f,0f,0f,0f);
		}

		for(int j=0;j<M;j++){
			for(int i=0;i<N;i++){
				final float v = get(i, j);
				final float r = ref.get(i, j);
				
				final float ulpFactor = findMaxULP(v, r);
				averageULP += ulpFactor;
				minULP = min(ulpFactor, minULP);
				maxULP = max(ulpFactor, maxULP);
				
			}
		}
		
		averageULP /= (float) M * N;

		return new FloatingPointError(averageULP, minULP, maxULP, -1f);
	}

}
