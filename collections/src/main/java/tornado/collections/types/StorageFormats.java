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

public final class StorageFormats {

    private StorageFormats() {

    }

    /**
     * Converts a given i,j index to a column-major index
     * @param i row index
     * @param j column index
     * @param ld length of a column
     * @return
     */
    public final static int toColumnMajor(int i,int j,int ld){
        return (j*ld) + i;
    }

    /**
     * Converts a given i,j index to a row-major index
     * @param i row index
     * @param j column index
     * @param ld length of a row
     * @return
     */
    public final static int toRowMajor(int i, int j, int ld){
        return (i*ld) + j;
    }
    
    /**
     * Converts a given i,j index to row-major index
     * @param i row index
     * @param j column index
     * @param ld length of a row
     * @param el length of each element in a row
     * @return
     */
    public final static int toRowMajor(int i, int j, int ld, int el){
    	return (i*ld) + (j*el);
    }
    
    /**
     * Converts a given i,j,k index to row-major index
     * @param i index in 1st dimension
     * @param j index in 2nd dimension
     * @param k index in 3rd dimension
     * @param ld1 leading edge length 1st dimension
     * @param ld2 leading edge length 2dn dimension
     * @param el basic element length
     * @return
     */
    public final static int toRowMajor(int i, int j, int k, int ld1, int ld2, int el){
    	return toRowMajor(i,j,ld1,el) + (k*ld2);
    }
    
    /**
     * Converts a given i,j index to a row-major index
     * @param i row index
     * @param j column index
     * @param incm row step
     * @param incn col step
     * @param ld length of a row
     * @return
     */
    public final static int toRowMajor(int i, int j,int incm, int incn, int ld){
        return (i*ld*incn) + (j*incm);
    }

    /**
     * Converts a given i,j index to Fortran index
     * @param i row index
     * @param j column index
     * @param ld length of a column
     * @return
     */
    public final static int toFortran(int i, int j, int ld){
        return ((j-1) * ld) + (i-1);
    }
    
    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     * @param matrix
     * @return
     */
	public static double[] toRowMajor(double[][] matrix) {
	
		final int m = matrix[0].length;
		final int n = matrix.length;
		double[] matrixRM = new double[m * n];
		
		for(int i=0;i<m;i++)
			for(int j=0;j<n;j++){
				matrixRM[toRowMajor(i, j, m)] = matrix[i][j];
			}
		
		return matrixRM;
	}
	
    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     * @param matrix
     * @return
     */
	public static float[] toRowMajor(float[][] matrix) {
	
		final int m = matrix[0].length;
		final int n = matrix.length;
		float[] matrixRM = new float[m * n];
		
		for(int i=0;i<m;i++)
			for(int j=0;j<n;j++){
				matrixRM[toRowMajor(i, j, m)] = matrix[i][j];
			}
		
		return matrixRM;
	}
        
    /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     * @param matrix
     * @return
     */
	public static int[] toRowMajor(int[][] matrix) {
	
		final int m = matrix[0].length;
		final int n = matrix.length;
		int[] matrixRM = new int[m * n];
		
		for(int i=0;i<m;i++)
			for(int j=0;j<n;j++){
				matrixRM[toRowMajor(i, j, m)] = matrix[i][j];
			}
		
		return matrixRM;
	}
	
	 /**
     * Converts a matrix stored in multi-dimensional arrays into Row-Major format
     * @param matrix
     * @return
     */
	public static byte[] toRowMajor(byte[][] matrix) {
	
		final int m = matrix[0].length;
		final int n = matrix.length;
		byte[] matrixRM = new byte[m * n];
		
		for(int i=0;i<m;i++)
			for(int j=0;j<n;j++){
				matrixRM[toRowMajor(i, j, m)] = matrix[i][j];
			}
		
		return matrixRM;
	}

}
