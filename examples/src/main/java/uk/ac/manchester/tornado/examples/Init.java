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

/**
 * Run with:
 * 
 * tornado uk.ac.manchester.tornado.examples.Init <size>
 *
 * 
 */
public class Init {

    private static final boolean CHECK = true;

    public static void compute(float[] array) {
        for (@Parallel int i = 0; i < array.length; i++) {
            array[i] = 100;
        }
    }

    public static void main(String[] args) {

        int size = 300000000;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }

        System.out.println("Running with size: " + size);
        System.out.println("Input size: " + (size * 4 * 1E-6) + " (MB)");
        float[] array = new float[size];

        TaskSchedule ts = new TaskSchedule("s0");
        ts.task("s0", Init::compute, array).streamOut((Object) array);
        ts.execute();

        if (CHECK) {
            for (float v : array) {
                if (v != 100) {
                    System.out.println("Result is wrong");
                    break;
                }
            }
        }

        System.out.println("--");
    }

}
