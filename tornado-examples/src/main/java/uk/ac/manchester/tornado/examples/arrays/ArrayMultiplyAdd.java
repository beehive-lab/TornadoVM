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
package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Example of a task-graph with multiple tasks.
 * <p>
 * How to run?:
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.ArrayMultiplyAdd
 * </code>
 */
public class ArrayMultiplyAdd {

    public static void main(final String[] args) {

        final int numElements = (args.length == 1) ? Integer.parseInt(args[0]) : 1024;

        final FloatArray a = new FloatArray(numElements);
        final FloatArray b = new FloatArray(numElements);
        final FloatArray c = new FloatArray(numElements);
        final FloatArray d = new FloatArray(numElements);

        /*
         * Data Initialization
         */
        a.init(3);
        b.init(2);
        c.init(0);
        d.init(0);

        /*
         * build an execution graph
         */
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("t0", (a2, b2, c2) -> {
                    for (@Parallel int i = 0; i < a2.getSize(); i++) {
                        c2.set(i, a2.get(i) * b2.get(i));
                    }
                }, a, b, c) //
                .task("t1", (a1, b1, c1) -> {
                    for (@Parallel int i = 0; i < a1.getSize(); i++) {
                        c1.set(i, a1.get(i) + b1.get(i));
                    }
                }, c, b, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.execute();

        /*
         * Check to make sure result is correct
         */
        for (int i = 0; i < numElements; i++) {
            float value = d.get(i);
            if (value != 8) {
                System.out.println("Invalid result: " + value);
                break;
            }
        }
    }
}
