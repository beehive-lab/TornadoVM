package tornado.unittests.virtualization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
	
	/**
	 * Test there are at least two OpenCL devices available
	 */
	@Test
	public void testDevices() {
		TornadoDriver driver = getTornadoRuntime().getDriver(0);
		assertNotNull(driver.getDevice(0));
		assertNotNull(driver.getDevice(1));
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

		Arrays.fill(data, 0);

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
			assertEquals((initValue + numKernels), data[i], 0.1);
		}
		
		initValue += numKernels;
		
		s0.mapAllTo(driver.getDevice(1));
		s0.execute();
		
		for (int i = 0; i < numElements; i++) {
			assertEquals((initValue + numKernels), data[i], 0.1);
		}
	}

}
