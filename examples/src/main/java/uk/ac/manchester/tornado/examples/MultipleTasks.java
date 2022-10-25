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

package uk.ac.manchester.tornado.examples;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * <p>
 * Run with:
 * </p>
 * <code>
 *      tornado -m tornado.examples/uk.ac.manchester.tornado.examples.MultipleTasks
 * </code>
 *
 */
public class MultipleTasks {

    private static final int MAX_ITERATIONS = 100;

    private static void foo(float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = x[i] + 100;
        }
    }

    private static void bar(float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = y[i] + 200;
        }
    }

    public static void main(String[] args) {

        int numElements = 512;

        if (args.length > 0) {
            numElements = Integer.parseInt(args[0]);
        }

        final float[] x = new float[numElements];
        final float[] y = new float[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).parallel().forEach(i -> x[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("example") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("foo", MultipleTasks::foo, x, y) //
                .task("bar", MultipleTasks::bar, y) //
                .transferToHost(y);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            taskGraph.execute();
        }
    }
}
