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
package tornado.unittests.virtualization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static tornado.common.Tornado.setProperty;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.util.Arrays;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.runtime.TornadoDriver;
import tornado.runtime.api.TaskSchedule;

public class TestsVirtualLayer {
	
	public static void acc(int[] a, int value) {
		for (@Parallel int i = 0; i < a.length; i++) {
			a[i] += value;
		}
	}
	
	public static void testA(int[] a, int value) {
		for (@Parallel int i = 0; i < a.length; i++) {
			a[i] = a[i] + value;
		}
	}
	
	public static void testB(int[] a, int value) {
		for (@Parallel int i = 0; i < a.length; i++) {
			a[i] = a[i] * value;
		}
	}
	
	/**
	 * Test there are at least two OpenCL devices available
	 */
	@Test
	public void testDevices() {
		TornadoDriver driver = getTornadoRuntime().getDriver(0);
		assertNotNull(driver.getDevice(0));
		assertNotNull(driver.getDevice(1));
	}
	
	@Test
	public void testDriverAndDevices() {
		int numDrivers = getTornadoRuntime().getNumDrivers();
		for (int i = 0; i < numDrivers; i++) {
			TornadoDriver driver = getTornadoRuntime().getDriver(i);
			assertNotNull(driver);
			int numDevices = driver.getDeviceCount();
			for (int j = 0; j < numDevices; j++) {
				assertNotNull(driver.getDevice(j));
			}
		}
	}
	
	/**
	 * Test to change execution from one device to another (migration).
	 */
	@Test
	public void testArrayMigration() {
		
		final int numElements = 8;
		final int numKernels = 8;
		
		int[] data = new int[numElements];
		int initValue = 0;

        TaskSchedule s0 = new TaskSchedule("s0");
        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestsVirtualLayer::acc, data, 1);
        }
        s0.streamOut(data);

		TornadoDriver driver = getTornadoRuntime().getDriver(0);
		
		if (driver.getDeviceCount() < 2) {
			assertFalse("The current driver has less than 2 devices", true);
		}
		
		s0.mapAllTo(driver.getDevice(0));
		s0.execute();
		
		for (int i = 0; i < numElements; i++) {
			assertEquals((initValue + numKernels), data[i]);
		}
		
		initValue += numKernels;
		
		s0.mapAllTo(driver.getDevice(1));
		s0.execute();
		
		for (int i = 0; i < numElements; i++) {
			assertEquals((initValue + numKernels), data[i]);
		}
	}
	
	@Test
	public void testVirtualLayer01() {
		/*
		 * The following expression is not correct for Tornado to
		 * execute on different devices. 
		 */
		final int N = 128;
		
		int[] data = new int[N];
		Arrays.fill(data, 100);

		TornadoDriver driver = getTornadoRuntime().getDriver(0);
        TaskSchedule s0 = new TaskSchedule("s0");
        
        // This test only is executed once (the first task)
        
        // Assign task to device 0
        s0.setDevice(driver.getDevice(0));
        s0.task("t0", TestsVirtualLayer::testA, data, 1);
        s0.streamOut(data);
        s0.execute();
        
        // Assign another task to device 1
        s0.setDevice(driver.getDevice(1));
        s0.task("t1", TestsVirtualLayer::testA, data, 10);
        s0.streamOut(data);
		s0.execute();
	}
	
	@Test
	public void testVirtualLayer02() {
		final int N = 128;
		int[] data = new int[N];
		
		Arrays.fill(data, 100);
		TornadoDriver driver = getTornadoRuntime().getDriver(0);
        TaskSchedule s0 = new TaskSchedule("s0");
        
        s0.setDevice(driver.getDevice(0));
        s0.task("t0", TestsVirtualLayer::testA, data, 1);
        s0.setDevice(driver.getDevice(1));
        s0.task("t1", TestsVirtualLayer::testA, data, 10);
        s0.streamOut(data);
        s0.execute();
        
        for (int i = 0; i < N; i++) {
        	assertEquals(111, data[i]);
        }	
	}
	
	@Test
	public void testVirtualLayer03() {
		final int N = 128;
		int[] dataA = new int[N];
		int[] dataB = new int[N];
		
		Arrays.fill(dataA, 100);
		Arrays.fill(dataB, 200);
		TornadoDriver driver = getTornadoRuntime().getDriver(0);
        TaskSchedule s0 = new TaskSchedule("s0");
        
        s0.setDevice(driver.getDevice(0));
        s0.task("t0", TestsVirtualLayer::testA, dataA, 1);
        s0.setDevice(driver.getDevice(1));
        s0.task("t1", TestsVirtualLayer::testA, dataB, 10);
        s0.streamOut(dataA);
        s0.streamOut(dataB);
        s0.execute();
        
        for (int i = 0; i < N; i++) {
        	assertEquals(101, dataA[i]);
        	assertEquals(210, dataB[i]);
        }	
	}
	
	@Test
	public void testVirtualLayer04() {
		final int N = 128;
		int[] data = new int[N];
				
		Arrays.fill(data, 100);
		
		final int numDrivers = getTornadoRuntime().getNumDrivers();
		for (int driverIndex = 0; driverIndex < numDrivers; driverIndex++) {
	        TaskSchedule s0 = new TaskSchedule("s" + driverIndex);
			final TornadoDriver driver = getTornadoRuntime().getDriver(driverIndex);
			driver.getDefaultDevice().reset();
			final int numDevices = driver.getDeviceCount();
			for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
				//System.out.println("s" + driverIndex + ".device="+ driverIndex + ":" + deviceIndex);
				setProperty("s" + driverIndex + ".device=", driverIndex + ":" + deviceIndex);
				s0.setDevice(driver.getDevice(deviceIndex));
		        s0.task("t" + deviceIndex, TestsVirtualLayer::testA, data, 1);
			}
			s0.streamOut(data);
	        s0.execute();
		}
        
        for (int i = 0; i < N; i++) {
        	assertEquals(102, data[i]);
        }	
	}

}
