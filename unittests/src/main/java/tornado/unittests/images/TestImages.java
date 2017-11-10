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

package tornado.unittests.images;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.collections.types.ImageByte3;
import tornado.collections.types.ImageFloat;
import tornado.runtime.api.TaskSchedule;

/**
 * Test for {@link ImageFloat} and {@link ImageByte3} data structures in Tornado.
 *
 */
public class TestImages {
	
	/**
	 * Test for image::fill kernel with square image.
	 */
	@Test
	public void testImageFloat01() {
		
		final int N = 128;
		final int M = 128;
		
        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskSchedule task = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);
        
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(1f, image.get(i, j), 0.001);
        	}
		}
	}
	
	/**
	 * Test for image::fill kernel with non-square image.
	 */
	@Test
	public void testImageFloat02() {
		
		final int M = 128;
		final int N = 32;
		
        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskSchedule task = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);
        
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(1f, image.get(i, j), 0.001);
        	}
		}
	}
	
	/**
	 * Test for image::fill kernel with non-square image.
	 */
	@Test
	public void testImageFloat03() {
		
		final int M = 32;
		final int N = 512;
		
        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskSchedule task = new TaskSchedule("s0")
                .task("t0", image::fill, 1f)
                .streamOut(image);
        
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(1f, image.get(i, j), 0.001);
        	}
		}
	}
	
	public static void taskWithImages(final ImageFloat a, final ImageFloat b) {
		for (@Parallel int i = 0; i < a.X(); i++) {
			for (@Parallel int j = 0; j < a.Y(); j++) {
				float value = a.get(i, j);
				b.set(i, j, value);
			}
		}
	}
	
	/**
	 * Test for computing a referenced method using {@link ImageFloat} 
	 * on the OpenCL device using square-matrices. 
	 */
	@Test
	public void testImageFloat04() {
		
		final int M = 32;
		final int N = 32;
		
        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(100f);
        
        final TaskSchedule task = new TaskSchedule("s0")
                .task("t1", TestImages::taskWithImages, imageA, imageB)
                .streamOut(imageB);
        task.execute();
        
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(100f, imageB.get(i, j), 0.001);
        	}
		}
	}
	
	/**
	 * Test for computing a referenced method using {@link ImageFloat} 
	 * on the OpenCL device using non square matrices and small size. 
	 */
	@Test
	public void testImageFloat05() {
		
		final int M = 16;
		final int N = 4;
		
        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(100f);
        imageB.fill(-1f);
 
        final TaskSchedule task = new TaskSchedule("s0")
                .task("t1", TestImages::taskWithImages, imageA, imageB)
                .streamOut(imageB);
        task.execute();
                
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(100f, imageB.get(i, j), 0.001);
        	}
		}
	}
	
	/**
	 * Test for computing a referenced method using {@link ImageFloat} 
	 * on the OpenCL device using non square matrices with big size. 
	 */
	@Test
	public void testImageFloat06() {
		
		final int M = 256;
		final int N = 512;
		
        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(100f);
        imageB.fill(-1f);
 
        final TaskSchedule task = new TaskSchedule("s0")
                .task("t1", TestImages::taskWithImages, imageA, imageB)
                .streamOut(imageB);
        task.execute();
                
        for (int i = 0; i < M; i++) {
        	for (int j = 0; j < N; j++) {
        		assertEquals(100f, imageB.get(i, j), 0.001);
        	}
		}
	}

}
