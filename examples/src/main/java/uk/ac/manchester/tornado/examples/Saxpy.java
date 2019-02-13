/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class Saxpy {

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void main(String[] args) {
        int numElements = 10240;

        float alpha = 2f;

        float[] x = new float[numElements];
        float[] y = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = 450;
            y[i] = 0;
        }

        TaskSchedule s0 = new TaskSchedule("s0").task("t0", Saxpy::saxpy, alpha, x, y).streamOut(y);

        s0.execute();

        System.out.println("Checking result");
        boolean wrongResult = false;
        for (int i = 0; i < y.length; i++) {
            if (Math.abs(y[i] - (alpha * x[i])) > 0.01) {
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
