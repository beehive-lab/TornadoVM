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
package uk.ac.manchester.tornado.benchmarks;

import java.util.Random;

import uk.ac.manchester.tornado.api.collections.types.ImageFloat;

public final class BenchmarkUtils {
	
	 public static final void createFilter(final float[] filter, final int width,
             final int height) {
     float filterSum = 0.0f;
     final Random rand = new Random();

     for (int x = 0; x < height; x++)
             for (int y = 0; y < width; y++) {
                     final float f = rand.nextFloat();
                     filterSum += f;
                     filter[(y * width) + x] = f;
             }

     for (int x = 0; x < height; x++)
             for (int y = 0; y < width; y++)
                     filter[(y * width) + x] /= filterSum;

}
	 
	 public static final void createFilter(final ImageFloat filter){
		 createFilter(filter.asBuffer().array(),filter.X(),filter.Y());
	 }
	 
	   public static final void createImage(final float[] image, final int width,
               final int height) {
       final Random rand = new Random();
       rand.setSeed(7);
       for (int x = 0; x < height; x++)
               for (int y = 0; y < width; y++)
                       image[(y * width) + x] = rand.nextInt(256);
}
	 
	   public static final void createImage(final ImageFloat image) {
		   createImage(image.asBuffer().array(),image.X(),image.Y());
	   }
	   
	   
}
