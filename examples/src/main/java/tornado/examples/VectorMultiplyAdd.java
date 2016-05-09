package tornado.examples;

import java.util.Arrays;

import tornado.collections.math.SimpleMath;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.ExecutableTask;

public class VectorMultiplyAdd {

    public static void main(final String[] args) {

        final int numElements = (args.length == 1) ? Integer.parseInt(args[0])
                : 1024;

        /*
         * allocate data
         */
        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];
        final float[] d = new float[numElements];

        /*
         * populate data
         */
        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);
        Arrays.fill(d, 0);

        /*
         * Create a task to perform vector multiplication and assign it to the
         * cpu
         */
        final ExecutableTask<?> multiply = TaskUtils.createTask(
                SimpleMath::vectorMultiply, a, b, c);
        multiply.mapTo(new OCLDeviceMapping(0, 0));

        /*
         * Create a task to perform vector addition and assign it to the
         * external gpu
         */
        final ExecutableTask<?> add = TaskUtils.createTask(
                SimpleMath::vectorAdd, c, b, d);
        add.mapTo(new OCLDeviceMapping(0, 1));

        /*
         * build an execution graph
         */
        final TaskGraph graph = new TaskGraph().add(multiply).add(add)
                .collect(d);

        /*
         * schedule the execution of the graph
         */
        graph.schedule().waitOn();

        /*
         * Check to make sure result is correct
         */
        for (final float value : d) {
            if (value != 8) {
                System.out.println("Invalid result: " + value);
                break;
            }
        }

    }

}
