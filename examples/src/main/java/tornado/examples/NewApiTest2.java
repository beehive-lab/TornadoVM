package tornado.examples;

import java.util.Arrays;
import java.util.Random;

import tornado.api.Parallel;
import tornado.api.ReadWrite;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.ExecutableTask;
import static tornado.runtime.api.TaskUtils.*;
public class NewApiTest2 {

	public static void addOne(@ReadWrite int[] array){
		for(int i=0;i<array.length;i++){
			array[i] = array[i] + 1;
		}
	}
	
	public static void main(String[] args) {
		final int[] values = new int[16];
		System.out.println("values: " + Arrays.toString(values));
		
		ExecutableTask<?> task = createTask(NewApiTest2::addOne,values);
		task.mapTo(new OCLDeviceMapping(0,2));
		task.execute();
		
		System.out.println("values: " + Arrays.toString(values));
		
		ExecutableTask<?> task2 = createTask((x) -> {
			for(int i=0;i<x.length;i++){
				x[i] = x[i] + 1;
			}
		},values);
		
		task2.mapTo(new OCLDeviceMapping(0,2));
		task2.execute();
		
		System.out.println("values: " + Arrays.toString(values));
		
		Random rand = new Random(42);
		final int z = rand.nextInt(16);
		
		ExecutableTask<?> task3 = createTask((x) -> {
			for(@Parallel int i=0;i<x.length;i++){
				x[i] = x[i] + z;
			}
		},values);
		
		task3.mapTo(new OCLDeviceMapping(0,2));
		task3.execute();
		
		System.out.printf("values: z=%d %s\n",z, Arrays.toString(values));
	}

}
