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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt 256
 * </code>
 */
public class VectorAddInt {

    private static void vectorAdd(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);

        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray c = new IntArray(size);
        IntArray result = new IntArray(size);

        a.init(10);
        b.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") // T
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", VectorAddInt::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph);

        boolean wrongResult;
        String profileLog = null;
        for (int idx = 0; idx < 10; idx++) {
            // Parallel
            TornadoExecutionResult executionResult = tornadoExecutor.execute();
            // Sequential
            vectorAdd(a, b, result);

            // Check Result
            wrongResult = false;
            for (int i = 0; i < c.getSize(); i++) {
                if (c.get(i) != 30) {
                    wrongResult = true;
                    break;
                }
            }
            if (wrongResult) {
                System.out.println("Result is wrong");
            } else {
                System.out.println("Result is correct. Total time: " + executionResult.getProfilerResult().getTotalTime() + " (ns)");
            }
            profileLog = executionResult.getProfilerResult().getProfileLog();
        }

        System.out.println(profileLog);
    }
}
