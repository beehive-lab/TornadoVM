package tornado.examples;

import java.util.Arrays;
import java.util.Random;

import tornado.api.Parallel;
import tornado.api.ReadWrite;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class NewApiTest3 {

	public static void addOne(@ReadWrite int[] array){
		for(@Parallel int i=0;i<array.length;i++){
			array[i] = array[i] + 1;
		}
	}
	
	public static void main(String[] args) {
		final int[] values = new int[16];
		System.out.println("values: " + Arrays.toString(values));
		Random rand = new Random(42);
		final int z = rand.nextInt(16);
		
		TaskGraph graph = new TaskGraph()
			.add(NewApiTest3::addOne,values)
			.add((x) -> {
				for(@Parallel int i=0;i<x.length;i++){
					x[i] = x[i] + 2;
				}
			},values)
			.add((x) -> {
				for(@Parallel int i=0;i<x.length;i++){
					x[i] = x[i] + z;
				}
			},values)
			.collect(values)
			.mapAllTo(new OCLDeviceMapping(0,2));
		
		graph.schedule().waitOn();
		
		
		System.out.printf("values: %s\n", Arrays.toString(values));
	}

}
