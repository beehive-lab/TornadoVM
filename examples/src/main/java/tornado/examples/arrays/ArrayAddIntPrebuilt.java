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
import tornado.api.Parallel;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.PrebuiltTask;
import tornado.runtime.api.TaskSchedule;
import tornado.runtime.api.TaskUtils;

public class ArrayAddIntPrebuilt {

    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        final PrebuiltTask add = TaskUtils.createTask("t0",
                "add",
                "opencl/add.cl",
                new Object[]{a, b, c},
                new Access[]{Access.READ, Access.READ, Access.WRITE},
                OpenCL.defaultDevice(),
                new int[]{numElements});

        new TaskSchedule("s0")
                .task(add)
                .streamOut(c)
                .execute();

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("b: " + Arrays.toString(b));
        System.out.println("c: " + Arrays.toString(c));
    }

}
