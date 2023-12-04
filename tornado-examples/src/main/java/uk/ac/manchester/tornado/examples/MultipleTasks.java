/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * Run with.
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.MultipleTasks
 * </code>
 *
 */
public class MultipleTasks {

    private static final int MAX_ITERATIONS = 100;

    private static void foo(FloatArray x, FloatArray y) {
        for (@Parallel int i = 0; i < y.getSize(); i++) {
            y.set(i, x.get(i) + 100);
        }
    }

    private static void bar(FloatArray y) {
        for (@Parallel int i = 0; i < y.getSize(); i++) {
            y.set(i, y.get(i) + 200);
        }
    }

    public static void main(String[] args) {

        int numElements = 512;

        if (args.length > 0) {
            numElements = Integer.parseInt(args[0]);
        }

        final FloatArray x = new FloatArray(numElements);
        final FloatArray y = new FloatArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).parallel().forEach(i -> x.set(i, r.nextFloat()));

        TaskGraph taskGraph = new TaskGraph("example") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("foo", MultipleTasks::foo, x, y) //
                .task("bar", MultipleTasks::bar, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.execute();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            executor.execute();
        }
    }
}
