/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class Init {

    public static void init(float[] x) {
        for (@Parallel int i = 0; i < x.length; i++) {
            x[i] = 100;
        }
    }

    public static void main(String[] args) {

        int numElements = 256;

        float[] x = new float[numElements];

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", Init::init, x).streamOut(x);

        s0.execute();

        boolean wrongResult = false;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != 100) {
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
