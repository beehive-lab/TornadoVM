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
import static tornado.collections.types.Float3.add;

import java.util.Random;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.collections.types.Float2;
import tornado.collections.types.Float3;
import tornado.collections.types.Float4;
import tornado.collections.types.VectorFloat3;
import tornado.collections.types.VectorFloat4;
import tornado.runtime.api.TaskSchedule;

public class TestFloats {
	
	
	private static void test(Float3 a, Float3 b, VectorFloat3 results) {
        results.set(0, add(a, b));
    }
	
	@Test
	public void simpleVectorAddition() {
		int size = 1;
		Float3 a = new Float3(new float[] {1, 2, 3});
		Float3 b = new Float3(new float[] {3, 2, 1});
		VectorFloat3 output = new VectorFloat3(size);
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::test, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	assertEquals(4, output.get(i).getX(), 0.001); 
        	assertEquals(4, output.get(i).getY(), 0.001);
        	assertEquals(4, output.get(i).getZ(), 0.001);
        }	
	}
	
	
	/**
	 * Test using the {@link Float} Java Wrapper class 
	 * @param a
	 * @param b
	 * @param result
	 */	
	private static void addFloat(Float[] a, Float[] b, Float[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
    }

	@Test
	public void testFloat1() {

		int size = 8;

		Float[] a = new Float[size];
		Float[] b = new Float[size];
		Float[] output = new Float[size];

		for (int i = 0; i < size; i++) {
			a[i] = (float)i;
			b[i] = (float)i;
		}
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addFloat, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	assertEquals(i + i, output[i], 0.001); 
        }	
	}
	
	/**
	 * Test using the {@link Float2} Tornado wrapper class 
	 * @param a
	 * @param b
	 * @param result
	 */
	private static void addFloat2(Float2[] a, Float2[] b, Float2[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = Float2.add(a[i], b[i]);
        }
    }

	@Test
	public void testFloat2() {

		int size = 8;

		Float2[] a = new Float2[size];
		Float2[] b = new Float2[size];
		Float2[] output = new Float2[size];

		for (int i = 0; i < size; i++) {
			a[i] = new Float2(i, i);
			b[i] = new Float2(i, i);
		}
		
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addFloat2, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
        
        for (int i = 0; i < size; i++) {
        	Float2 sequential = new Float2(i + i, i + i);
        	assertEquals(sequential.getX(), output[i].getX(), 0.001); 
        	assertEquals(sequential.getY(), output[i].getY(), 0.001);
        }
	}
	
	/**
	 *  Test using Tornado {@link VectorFloat3} data type  
	 * @param a 
	 * @param b
	 * @param results
	 */
	public static void addVectorFloat3(VectorFloat3 a, VectorFloat3 b, VectorFloat3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float3.add(a.get(i), b.get(i)));
        }
    }
	
	@Test
	public void testVectorFloat3() {
		
		int size = 8;
		
		VectorFloat3 a = new VectorFloat3(size);
		VectorFloat3 b = new VectorFloat3(size);
		VectorFloat3 output = new VectorFloat3(size);
		
		for (int i = 0; i < size; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3(size - i, size - i, size - i));
        }
		
		
		 //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat3, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
		
        for (int i = 0; i < size; i++) {
        	Float3 sequential = new Float3(i + (size-i), i + (size-i), i + (size-i));
        	assertEquals(sequential.getX(), output.get(i).getX(), 0.001); 
        	assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
        	assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        }
	}
	
	/**
	 *  Test using Tornado {@link VectorFloat4} data type  
	 * @param a 
	 * @param b
	 * @param results
	 */
	public static void addVectorFloat4(VectorFloat4 a, VectorFloat4 b, VectorFloat4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }
	
	@Test
	public void testVectorFloat4() {
		
		int size = 8;
		
		VectorFloat4 a = new VectorFloat4(size);
		VectorFloat4 b = new VectorFloat4(size);
		VectorFloat4 output = new VectorFloat4(size);
		
		for (int i = 0; i < size; i++) {
            a.set(i, new Float4(i, i, i, i));
            b.set(i, new Float4(size - i, size - i, size - i, size));
        }
		
		
		 //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat4, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on
		
        for (int i = 0; i < size; i++) {
        	Float4 sequential = new Float4(i + (size-i), i + (size-i), i + (size-i), i + size);
        	assertEquals(sequential.getX(), output.get(i).getX(), 0.001); 
        	assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
        	assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        	assertEquals(sequential.getW(), output.get(i).getW(), 0.001);
        }
	}
	
	
	public static void dotProductFunctionMap(float[] a, float[] b, float[] results) {
        for (@Parallel int i = 0; i < a.length; i++) {
            results[i] = a[i] * b[i];
        }
    }
	
	public static void dotProductFunctionReduce(float[] input, float[] results) {
		float sum = 0.0f;
        for (int i = 0; i < input.length; i++) {
            sum += input[i];
        }
        results[0] = sum;
    }
	
	@Test
	public void testDotProduct() {
		
		int size = 8;
		
		float[] a = new float[size];
		float[] b = new float[size];
		float[] outputMap = new float[size];
		float[] outputReduce = new float[1];
		
		float[] seqMap = new float[size];
		float[] seqReduce = new float[1];
		
		Random r = new Random();
		for (int i = 0; i < size; i++) {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
        }

		// Sequential computation
		dotProductFunctionMap(a, b, seqMap);
		dotProductFunctionReduce(seqMap, seqReduce);
		
		// Parallel computation with Tornado
		//@formatter:off
        new TaskSchedule("s0")
                .task("t0-MAP", TestFloats::dotProductFunctionMap, a, b, outputMap)
                .task("t1-REDUCE", TestFloats::dotProductFunctionReduce, outputMap, outputReduce)
                .streamOut(outputReduce)
                .execute();
        //@formatter:on
        
        assertEquals(seqReduce[0], outputReduce[0], 0.001);
	}
}