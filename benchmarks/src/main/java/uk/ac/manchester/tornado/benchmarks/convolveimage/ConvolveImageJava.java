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
package uk.ac.manchester.tornado.benchmarks.convolveimage;

import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createFilter;
import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createImage;
import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.*;

import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
public class ConvolveImageJava extends BenchmarkDriver {

	private final int imageSizeX, imageSizeY, filterSize;
	
	private ImageFloat input, output, filter;
	
	public ConvolveImageJava(int iterations, int imageSizeX, int imageSizeY, int filterSize){
		super(iterations);
		this.imageSizeX = imageSizeX;
		this.imageSizeY = imageSizeY;
		this.filterSize = filterSize;
	}
	
	@Override
	public void setUp() {
		input = new ImageFloat(imageSizeX,imageSizeY);
        output = new ImageFloat(imageSizeX,imageSizeY);
        filter = new ImageFloat(filterSize,filterSize);

        createImage(input);
        createFilter(filter);
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
			convolveImage(input,filter,output);
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
