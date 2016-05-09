package tornado.examples;

import java.util.Arrays;
import java.util.Random;

import tornado.api.ReadWrite;
import static tornado.runtime.api.TaskUtils.*;

public class NewApiTest1 {

	public static void addOne(@ReadWrite int[] array){
		for(int i=0;i<array.length;i++){
			array[i] = array[i] + 1;
		}
	}
	
	public static void main(String[] args) {
		final int[] values = new int[16];
		System.out.println("values: " + Arrays.toString(values));
		
		createTask(NewApiTest1::addOne,values).execute();
		
		System.out.println("values: " + Arrays.toString(values));
		
		createTask((x) -> {
			for(int i=0;i<x.length;i++){
				x[i] = x[i] + 1;
			}
		},values).execute();
		
		System.out.println("values: " + Arrays.toString(values));
		
		Random rand = new Random(42);
		final int z = rand.nextInt(16);
		
		createTask((x) -> {
			for(int i=0;i<x.length;i++){
				x[i] = x[i] + z;
			}
		},values).execute();
		
		System.out.printf("values: z=%d %s\n",z, Arrays.toString(values));
	}

}
