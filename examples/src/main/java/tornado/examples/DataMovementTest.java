package tornado.examples;


import java.util.Arrays;

import tornado.drivers.opencl.OpenCL;
import tornado.runtime.DataMovementTask;
import tornado.runtime.api.TaskUtils;
public class DataMovementTest {
	
	private static void printArray(int[] array){
		System.out.printf("array = [");
		for(int value : array)
			System.out.printf("%d ",value);
		System.out.println("]");
	}

	public static void main(String[] args) {
		
		int[] array = {1,2,3,4};
		printArray(array);
		final DataMovementTask writeTask = TaskUtils.write(array);
		writeTask.mapTo(OpenCL.defaultDevice());
		writeTask.schedule();
		writeTask.waitOn();
		
		Arrays.fill(array, -1);
		printArray(array);
		final DataMovementTask readTask = TaskUtils.read(array);
		readTask.mapTo(OpenCL.defaultDevice());
		readTask.schedule();
		readTask.waitOn();
		
		printArray(array);
		
		System.out.printf("write: %.4e s\n",writeTask.getExecutionTime());
		System.out.printf("read : %.4e s\n",readTask.getExecutionTime());
		
	}

}
