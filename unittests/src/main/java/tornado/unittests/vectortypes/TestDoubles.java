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
 */
package tornado.unittests.vectortypes;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.collections.types.Double2;
import tornado.collections.types.Double3;
import tornado.collections.types.Double4;
import tornado.collections.types.VectorDouble;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TestBase;

public class TestDoubles extends TestBase {
	
	private static void addDouble2(Double2 a, Double2 b, VectorDouble results) {
		Double2 d2 = Double2.add(a, b);
		double r = d2.getX() + d2.getY();
        results.set(0, r);
    }
	
	@Test
	public void doubleAdd2() {
		int size = 1;
		Double2 a = new Double2(new double[] {1, 2});
		Double2 b = new Double2(new double[] {3, 2});
		VectorDouble output = new VectorDouble(size);
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestDoubles::addDouble2, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	assertEquals(4, output.get(i), 0.001); 
        }	
	}
	
	private static void addDouble3(Double3 a, Double3 b, VectorDouble results) {
		Double3 d3 = Double3.add(a, b);
		double r = d3.getX() + d3.getY() + d3.getZ();
        results.set(0, r);
    }
	
	@Test
	public void doubleAdd3() {
		int size = 1;
		Double3 a = new Double3(new double[] {1, 2, 3});
		Double3 b = new Double3(new double[] {3, 2, 1});
		VectorDouble output = new VectorDouble(size);
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestDoubles::addDouble3, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	assertEquals(4, output.get(i), 0.001); 
        }	
	}
	
	private static void addDouble4(Double4 a, Double4 b, VectorDouble results) {
		Double4 d4 = Double4.add(a, b);
		double r = d4.getX() + d4.getY() + d4.getZ() + d4.getW();
        results.set(0, r);
    }
	
	@Test
	public void doubleAdd4() {
		int size = 1;
		Double4 a = new Double4(new double[] {1, 2, 3, 4});
		Double4 b = new Double4(new double[] {5, 3, 2, 1});
		VectorDouble output = new VectorDouble(size);
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestDoubles::addDouble4, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	assertEquals(5, output.get(i), 0.001); 
        }	
	}
	
	private static void addDouble(double[] a, double[] b, double[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
    }

	@Test
	public void testAddDouble1() {

		int size = 8;

		double[] a = new double[size];
		double[] b = new double[size];
		double[] output = new double[size];

		for (int i = 0; i < size; i++) {
			a[i] = (double)i;
			b[i] = (double)i;
		}
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestDoubles::addDouble, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	assertEquals(i + i, output[i], 0.001); 
        }	
	}
	
	public static void dotProductFunctionMap(double[] a, double[] b, double[] results) {
        for (@Parallel int i = 0; i < a.length; i++) {
            results[i] = a[i] * b[i];
        }
    }
	
	public static void dotProductFunctionReduce(double[] input, double[] results) {
		double sum = 0.0f;
        for (int i = 0; i < input.length; i++) {
            sum += input[i];
        }
        results[0] = sum;
    }
	
	@Test
	public void testDotProductDouble() {
		
		int size = 8;
		
		double[] a = new double[size];
		double[] b = new double[size];
		double[] outputMap = new double[size];
		double[] outputReduce = new double[1];
		
		double[] seqMap = new double[size];
		double[] seqReduce = new double[1];
		
		Random r = new Random();
		for (int i = 0; i < size; i++) {
            a[i] = r.nextDouble();
            b[i] = r.nextDouble();
        }

		// Sequential computation
		dotProductFunctionMap(a, b, seqMap);
		dotProductFunctionReduce(seqMap, seqReduce);
		
		// Parallel computation with Tornado
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0-MAP", TestDoubles::dotProductFunctionMap, a, b, outputMap)
                .task("t1-REDUCE", TestDoubles::dotProductFunctionReduce, outputMap, outputReduce)
                .streamOut(outputReduce)
                .execute();
        //@formatter:on
        
        assertEquals(seqReduce[0], outputReduce[0], 0.001);
	}
}