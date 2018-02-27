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
package uk.ac.manchester.tornado.benchmarks.rotatevector;

import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.*;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.collections.types.VectorFloat3;

public class RotateJava extends BenchmarkDriver {

	private final int numElements;
	
	private VectorFloat3 input, output;
	private Matrix4x4Float m;
	
	public RotateJava(int iterations, int numElements){
		super(iterations);
		this.numElements = numElements;
	}
	
	@Override
	public void setUp() {
		input = new VectorFloat3(numElements);
		output = new VectorFloat3(numElements);
		
		m = new Matrix4x4Float();
		m.identity();
		
		final Float3 value = new Float3(1f,2f,3f);
		for(int i=0;i<numElements;i++){
			input.set(i,value);
		}

	}
	
	@Override
	public void tearDown() {
		input = null;
		output = null;
		m = null;
		super.tearDown();
	}

	@Override
	public void code() {
			rotateVector(output,m,input);
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
