package tornado.examples;

import java.util.Arrays;

import tornado.api.Parallel;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.ExecutableTask;
import static tornado.runtime.api.TaskUtils.createTask;
public class NewApiTest4 {
	
	public static void main(String[] args) {
		final int[] values = new int[16];
		
		System.out.println("values: " + Arrays.toString(values));
		
		ExecutableTask<?> task = createTask(new Runnable(){
			@Override
			public void run() {
				for(@Parallel int i=0;i<values.length;i++){
					values[i] = values[i] + 1;
				}
				
			}	
		});
		
		task.mapTo(new OCLDeviceMapping(0,2));
		task.execute();
	
		System.out.println("values: " + Arrays.toString(values));
	}

}
