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
package tornado.benchmarks.convolvearray;

import tornado.benchmarks.BenchmarkDriver;
import static tornado.benchmarks.BenchmarkUtils.createFilter;
import static tornado.benchmarks.BenchmarkUtils.createImage;
import static tornado.benchmarks.GraphicsKernels.*;
public class ConvolveImageArrayJava extends BenchmarkDriver {

	private final int imageSizeX, imageSizeY, filterSize;
	
	private float[] input, output, filter;
	
	public ConvolveImageArrayJava(int iterations, int imageSizeX, int imageSizeY, int filterSize){
		super(iterations);
		this.imageSizeX = imageSizeX;
		this.imageSizeY = imageSizeY;
		this.filterSize = filterSize;
	}
	
	@Override
	public void setUp() {
		input = new float[imageSizeX * imageSizeY];
        output = new float[imageSizeX * imageSizeY];
        filter = new float[filterSize * filterSize];

        createImage(input, imageSizeX, imageSizeY);
        createFilter(filter, filterSize, filterSize);

	}
	
	@Override
	public void tearDown() {
		input = null;
		output = null;
		filter = null;
		super.tearDown();
	}

	@Override
	public void code() {
			convolveImageArray(input,filter,output,imageSizeX,imageSizeY,filterSize,filterSize);
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
