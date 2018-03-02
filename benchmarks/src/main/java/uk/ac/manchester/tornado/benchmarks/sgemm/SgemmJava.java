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
package uk.ac.manchester.tornado.benchmarks.sgemm;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.*;

import java.util.Random;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class SgemmJava extends BenchmarkDriver {

	private final int m, n;
	
	private float[] a, b, c;
	
	public SgemmJava(int iterations, int m, int n){
		super(iterations);
		this.m = m;
		this.n = n;
	}
	
	@Override
	public void setUp() {
		a = new float[m * n];
		b = new float[m * n];
		c = new float[m * n];
		
		final Random random = new Random();
		
		for(int i=0;i<m;i++){
			a[i*(m+1)] = 1;
		}
		
		for(int i=0;i<m*n;i++){
			b[i] = random.nextFloat();
		}

	}
	
	@Override
	public void tearDown() {
		a = null;
		b = null;
		c = null;
		super.tearDown();
	}

	@Override
	public void code() {
			sgemm(m,n,m, a, b, c);
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
