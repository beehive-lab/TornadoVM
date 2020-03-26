/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples.fpga;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class VectorAddFloat {

    private static void vectorAdd(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        float[] a = new float[size];
        float[] b = new float[size];
        float[] c = new float[size];
        float[] result = new float[size];

        Arrays.fill(a, 450f);
        Arrays.fill(b, 20f);

        //@formatter:off
        TaskSchedule graph = new TaskSchedule("s0")
                .task("t0", VectorAddFloat::vectorAdd, a, b, c)
        .streamOut(c);
        //@formatter:on

        for (int idx = 0; idx < 10; idx++) {
            graph.execute();
            vectorAdd(a, b, result);
            boolean wrongResult = false;
            for (int i = 0; i < c.length; i++) {
                if (c[i] != 470f) {
                    wrongResult = true;
                    break;
                }
            }
            if (!wrongResult) {
                System.out.println("Test success");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
