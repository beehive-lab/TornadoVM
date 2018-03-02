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
 * Authors: Juan Fumero
 *
 */

package examples;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

public class TestTornado { 

  public static void main(String[] args) {
	testVectorAdditionDouble(4096);
  }
	

  // Method to be executed on the parallel device (eg. GPU)
  public static void vectorAddDouble(double[] a, double[] b, double[] c) {
    for (@Parallel int i = 0; i < c.length; i++) {
    	c[i] = a[i] + b[i];
    }
  }

  public static void testVectorAdditionDouble(int size) {

    double[] a = new double[size];
    double[] b = new double[size];
    double[] c = new double[size];

    Random r = new Random();
    IntStream.range(0, size).sequential().forEach(i -> {
        a[i] = r.nextDouble();
        b[i] = r.nextDouble();
    });

    // Tornado Task API 
    new TaskSchedule("s0")     // new group of Tasks
        .streamIn(a, b)        // copy in from the host to the device (a and b arrays)
        .task("t0", TestArrays::vectorAddDouble, a, b, c)   // task 0 
        .streamOut(c)          // copy out from the device to host
        .execute();            // run the task (Tornado bytecode generation, Tornado tasks graph, 
                               // OpenCL JIT compilation and execution)
  }
}


