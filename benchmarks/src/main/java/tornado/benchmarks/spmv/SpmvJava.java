/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.benchmarks.spmv;

import tornado.benchmarks.BenchmarkDriver;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;
import static tornado.benchmarks.LinearAlgebraArrays.*;

public class SpmvJava extends BenchmarkDriver {

	private final CSRMatrix<float[]> matrix;
	
	private float[] v, y;
	
	public SpmvJava(int iterations, CSRMatrix<float[]> matrix){
		super(iterations);
		this.matrix = matrix;
	}
	
	@Override
	public void setUp() {

		v = new float[matrix.size];
		y = new float[matrix.size];

		Benchmark.populateVector(v);

	}
	
	@Override
	public void tearDown() {
		v = null;
		y = null;
		
		super.tearDown();
	}

	@Override
	public void code() {
			spmv(matrix.vals, matrix.cols, matrix.rows, v,
					matrix.size, y);
	}
	
	@Override
	public void barrier(){
		
	}

	@Override
	public boolean validate() {
		return true;
	}
	
	public void printSummary(){
		System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n",getElapsed(),getElapsedPerIteration());
	}

}
