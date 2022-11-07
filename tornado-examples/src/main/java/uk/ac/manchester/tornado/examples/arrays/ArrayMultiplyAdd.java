/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.arrays;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.collections.math.SimpleMath;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * Example of a task-graph with multiple tasks.
 * <p>
 * How to run?:
 * </p>
 * <code>
 *     tornado -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.ArrayMultiplyAdd
 * </code>
 */
public class ArrayMultiplyAdd {

    public static void main(final String[] args) {

        final int numElements = (args.length == 1) ? Integer.parseInt(args[0]) : 1024;

        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];
        final float[] d = new float[numElements];

        /*
         * Data Initialization
         */
        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);
        Arrays.fill(d, 0);

        /*
         * build an execution graph
         */
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("t0", SimpleMath::vectorMultiply, a, b, c) //
                .task("t1", SimpleMath::vectorAdd, c, b, d) //
                .transferToHost(d);

        taskGraph.execute();

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
