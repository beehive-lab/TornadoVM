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

package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Random;
import java.util.stream.IntStream;

public class MultipleTasks {

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

        // @formatter:off
        TaskSchedule ts = new TaskSchedule("example")
                .streamIn(x)
                .task("foo", MultipleTasks::foo, x, y)
                .task("bar", MultipleTasks::bar, y)
                .streamOut(y);
        //@formatter:on

        for (int i = 0; i < 100; i++) {
            ts.execute();
        }

    }
}
