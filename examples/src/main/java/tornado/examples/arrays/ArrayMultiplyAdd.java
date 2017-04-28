/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.examples.arrays;

import java.util.Arrays;
import tornado.collections.math.SimpleMath;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.TaskSchedule;
import tornado.runtime.api.TaskUtils;

public class ArrayMultiplyAdd {

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
        final CompilableTask multiply = TaskUtils.createTask("t0",
                SimpleMath::vectorMultiply, a, b, c);
        multiply.mapTo(new OCLDeviceMapping(0, 0));

        /*
         * Create a task to perform vector addition and assign it to the
         * external gpu
         */
        final CompilableTask add = TaskUtils.createTask("t1",
                SimpleMath::vectorAdd, c, b, d);
        add.mapTo(new OCLDeviceMapping(0, 2));

        /*
         * build an execution graph
         */
        TaskSchedule schedule = new TaskSchedule("s0")
                .task(multiply)
                .task(add)
                .streamOut(d);

        schedule.execute();

        schedule.dumpTimes();

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
