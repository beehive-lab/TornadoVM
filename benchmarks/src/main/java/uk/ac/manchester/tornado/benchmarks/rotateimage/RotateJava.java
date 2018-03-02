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
package uk.ac.manchester.tornado.benchmarks.rotateimage;

import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.*;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.collections.types.Matrix4x4Float;

public class RotateJava extends BenchmarkDriver {

	private final int numElementsX, numElementsY;
	
	private ImageFloat3 input, output;
	private Matrix4x4Float m;
	
	public RotateJava(int iterations, int numElementsX, int numElementsY){
		super(iterations);
		this.numElementsX = numElementsX;
		this.numElementsY = numElementsY;
	}
	
	@Override
	public void setUp() {
		input = new ImageFloat3(numElementsX, numElementsY);
		output = new ImageFloat3(numElementsX, numElementsY);
		
		m = new Matrix4x4Float();
		m.identity();
		
		final Float3 value = new Float3(1f,2f,3f);
		for(int i=0;i<input.Y();i++){
			for(int j=0;j<input.X();j++)
				input.set(j,i,value);
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
			rotateImage(output,m,input);
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
