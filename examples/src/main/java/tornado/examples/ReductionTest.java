package tornado.examples;

import java.util.Arrays;

import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskSchedule;

public class ReductionTest {

    public static void main(String[] args) {
        final int[] data = new int[614400];
        final int[] intermediate = new int[1024];
        final int[] result = new int[32];

        Arrays.fill(data, 1);

//        final TaskSchedule graph = new TaskSchedule()
//                .add(LinearAlgebraArrays::reduceInt, result, data)
//                .collect(result).mapAllTo(new OCLDeviceMapping(0, 0));
//
//        graph.schedule().waitOn();
//
//        // int sum = 0;
//        // for(int value : result)
//        // sum += value;
//
//        System.out.printf("result: %d\n", result[0]);
//
//        graph.dumpTimes();
    }

}
