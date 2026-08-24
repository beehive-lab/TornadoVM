/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * On-demand copy-outs: a task-graph that produces four results, of which the host usually only
 * needs one. Declaring the outputs with {@link DataTransferMode#UNDER_DEMAND} keeps them on the
 * device until {@link TornadoExecutionResult#transferToHost} asks for them, and asking for
 * several at once costs one host wait rather than one per object.
 *
 * <p>
 * Run with.
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.OnDemandTransfers
 * </code>
 */
public class OnDemandTransfers {

    private static final int SIZE = 1 << 20;
    private static final int ITERATIONS = 20;

    private static void scale(FloatArray input, FloatArray output, float alpha) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, alpha * input.get(i));
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(1.0f);

        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        FloatArray outC = new FloatArray(SIZE);
        FloatArray outD = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemand") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", OnDemandTransfers::scale, input, outA, 1.0f) //
                .task("b", OnDemandTransfers::scale, input, outB, 2.0f) //
                .task("c", OnDemandTransfers::scale, input, outC, 3.0f) //
                .task("d", OnDemandTransfers::scale, input, outD, 4.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outA, outB, outC, outD);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Only the last iteration's results are needed, so nothing is copied back until then.
            TornadoExecutionResult executionResult = null;
            long start = System.nanoTime();
            for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
                input.init(iteration);
                executionResult = executionPlan.execute();
            }
            long compute = System.nanoTime() - start;

            // One call for all four outputs: the reads are issued together and waited for once.
            start = System.nanoTime();
            executionResult.transferToHost(outA, outB, outC, outD);
            long transfer = System.nanoTime() - start;

            System.out.printf("%d executions in %.2f ms, copy-out of 4 x %d floats in %.3f ms%n", //
                    ITERATIONS, compute * 1e-6, SIZE, transfer * 1e-6);
            System.out.printf("outA[0]=%.1f outB[0]=%.1f outC[0]=%.1f outD[0]=%.1f (expected %d, %d, %d, %d)%n", //
                    outA.get(0), outB.get(0), outC.get(0), outD.get(0), //
                    ITERATIONS, 2 * ITERATIONS, 3 * ITERATIONS, 4 * ITERATIONS);
        }
    }
}
