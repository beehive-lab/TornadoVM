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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DataRange;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * On-demand copy-outs: objects declared with {@link DataTransferMode#UNDER_DEMAND} are only read
 * back when {@link TornadoExecutionResult#transferToHost} asks for them. Several objects asked for
 * in one call are read together and waited for once, so these tests check that every object is
 * complete and up to date when the call returns.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestOnDemandTransfers
 * </code>
 */
public class TestOnDemandTransfers extends TornadoTestBase {

    private static final int SIZE = 8192;

    public static void scale(FloatArray input, FloatArray output, float alpha) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, alpha * input.get(i));
        }
    }

    public static void accumulate(FloatArray input, FloatArray accumulator) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            accumulator.set(i, accumulator.get(i) + input.get(i));
        }
    }

    public static void scaleMatrix(Matrix2DFloat input, Matrix2DFloat output, float alpha) {
        for (@Parallel int i = 0; i < input.getNumRows(); i++) {
            for (@Parallel int j = 0; j < input.getNumColumns(); j++) {
                output.set(i, j, alpha * input.get(i, j));
            }
        }
    }

    public static void increment(IntArray input, IntArray output, int delta) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i) + delta);
        }
    }

    /** Four on-demand outputs asked for in a single call. */
    @Test
    public void testTransferFourObjectsInOneCall() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(2.0f);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        FloatArray outC = new FloatArray(SIZE);
        FloatArray outD = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemand") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, outA, 1.0f) //
                .task("b", TestOnDemandTransfers::scale, input, outB, 2.0f) //
                .task("c", TestOnDemandTransfers::scale, input, outC, 3.0f) //
                .task("d", TestOnDemandTransfers::scale, input, outD, 4.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outA, outB, outC, outD);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute().transferToHost(outA, outB, outC, outD);

            for (int i = 0; i < SIZE; i++) {
                assertEquals(2.0f, outA.get(i), 0.001f);
                assertEquals(4.0f, outB.get(i), 0.001f);
                assertEquals(6.0f, outC.get(i), 0.001f);
                assertEquals(8.0f, outD.get(i), 0.001f);
            }
        }
    }

    /**
     * Repeated executions with a different input each time. A read that has not landed shows up
     * here as the previous execution's values rather than as a wrong number.
     */
    @Test
    public void testOnDemandTransfersAcrossExecutions() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        FloatArray outC = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandRepeat") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, outA, 1.0f) //
                .task("b", TestOnDemandTransfers::scale, input, outB, 2.0f) //
                .task("c", TestOnDemandTransfers::scale, input, outC, 3.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outA, outB, outC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int iteration = 1; iteration <= 8; iteration++) {
                input.init(iteration);
                executionPlan.execute().transferToHost(outA, outB, outC);
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(iteration, outA.get(i), 0.001f);
                    assertEquals(2.0f * iteration, outB.get(i), 0.001f);
                    assertEquals(3.0f * iteration, outC.get(i), 0.001f);
                }
            }
        }
    }

    /** Objects transferred one at a time must behave exactly as when asked for together. */
    @Test
    public void testTransferObjectsOneByOne() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(4.0f);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandOneByOne") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, outA, 2.0f) //
                .task("b", TestOnDemandTransfers::scale, input, outB, 3.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outA, outB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            executionResult.transferToHost(outA);
            for (int i = 0; i < SIZE; i++) {
                assertEquals(8.0f, outA.get(i), 0.001f);
            }
            executionResult.transferToHost(outB);
            for (int i = 0; i < SIZE; i++) {
                assertEquals(12.0f, outB.get(i), 0.001f);
            }
        }
    }

    /** Mixed types in one on-demand call, so the reads go through different buffer wrappers. */
    @Test
    public void testMixedTypesInOneCall() throws TornadoExecutionPlanException {
        FloatArray floatInput = new FloatArray(SIZE);
        floatInput.init(1.5f);
        IntArray intInput = new IntArray(SIZE);
        intInput.init(7);
        FloatArray floatOutput = new FloatArray(SIZE);
        IntArray intOutput = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandMixed") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, floatInput, intInput) //
                .task("f", TestOnDemandTransfers::scale, floatInput, floatOutput, 2.0f) //
                .task("i", TestOnDemandTransfers::increment, intInput, intOutput, 5) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, floatOutput, intOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute().transferToHost(floatOutput, intOutput);

            for (int i = 0; i < SIZE; i++) {
                assertEquals(3.0f, floatOutput.get(i), 0.001f);
                assertEquals(12, intOutput.get(i));
            }
        }
    }

    /**
     * A partial on-demand copy next to a whole-object one. The partial read keeps the blocking
     * path, so this covers the guard rather than the batched reads.
     */
    @Test
    public void testPartialAndWholeObjectOnDemand() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray whole = new FloatArray(SIZE);
        FloatArray partial = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandPartial") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, whole, 2.0f) //
                .task("b", TestOnDemandTransfers::scale, input, partial, 5.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, whole, partial);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            DataRange dataRange = new DataRange(partial);
            executionResult.transferToHost(dataRange.withSize(SIZE / 2));
            executionResult.transferToHost(dataRange.withOffset(SIZE / 2).withSize(SIZE / 2));
            executionResult.transferToHost(whole);

            for (int i = 0; i < SIZE; i++) {
                assertEquals(6.0f, whole.get(i), 0.001f);
                assertEquals(15.0f, partial.get(i), 0.001f);
            }
        }
    }

    /**
     * A read-write object that stays on the device across executions and is only copied back at
     * the end, next to two write-only outputs asked for in the same call. The accumulator makes a
     * stale read visible as a wrong iteration count rather than as a wrong value.
     */
    @Test
    public void testReadWriteAccumulatorWithOtherOutputs() throws TornadoExecutionPlanException {
        final int iterations = 10;
        FloatArray accumulator = new FloatArray(SIZE);
        accumulator.init(0.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(1.0f);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandAccumulator") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, accumulator) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("acc", TestOnDemandTransfers::accumulate, input, accumulator) //
                .task("a", TestOnDemandTransfers::scale, input, outA, 3.0f) //
                .task("b", TestOnDemandTransfers::scale, input, outB, 4.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, accumulator, outA, outB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = null;
            for (int iteration = 0; iteration < iterations; iteration++) {
                executionResult = executionPlan.execute();
            }
            executionResult.transferToHost(accumulator, outA, outB);

            for (int i = 0; i < SIZE; i++) {
                assertEquals(iterations, accumulator.get(i), 0.001f);
                assertEquals(3.0f, outA.get(i), 0.001f);
                assertEquals(4.0f, outB.get(i), 0.001f);
            }
        }
    }

    /**
     * On-demand outputs alongside an every-execution one. The eager output is copied back by the
     * execution itself, the others only when asked for, and both have to end up correct.
     */
    @Test
    public void testMixedTransferModesInOneGraph() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray eager = new FloatArray(SIZE);
        FloatArray lazyA = new FloatArray(SIZE);
        FloatArray lazyB = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandMixedModes") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("e", TestOnDemandTransfers::scale, input, eager, 2.0f) //
                .task("a", TestOnDemandTransfers::scale, input, lazyA, 3.0f) //
                .task("b", TestOnDemandTransfers::scale, input, lazyB, 4.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, eager) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, lazyA, lazyB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int iteration = 1; iteration <= 4; iteration++) {
                input.init(iteration);
                TornadoExecutionResult executionResult = executionPlan.execute();

                for (int i = 0; i < SIZE; i++) {
                    assertEquals(2.0f * iteration, eager.get(i), 0.001f);
                }

                executionResult.transferToHost(lazyA, lazyB);
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(3.0f * iteration, lazyA.get(i), 0.001f);
                    assertEquals(4.0f * iteration, lazyB.get(i), 0.001f);
                }
            }
        }
    }

    /**
     * Only a subset of the on-demand outputs is asked for, and a different subset each time. The
     * objects that were not requested must keep the values of the execution they were last read
     * after, not pick up the current one.
     */
    @Test
    public void testSubsetOfOutputsRequestedPerExecution() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        FloatArray outC = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandSubset") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, outA, 1.0f) //
                .task("b", TestOnDemandTransfers::scale, input, outB, 2.0f) //
                .task("c", TestOnDemandTransfers::scale, input, outC, 3.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outA, outB, outC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            input.init(1.0f);
            executionPlan.execute().transferToHost(outA);

            input.init(2.0f);
            executionPlan.execute().transferToHost(outB, outC);

            for (int i = 0; i < SIZE; i++) {
                // outA was read after the first execution and never again.
                assertEquals(1.0f, outA.get(i), 0.001f);
                assertEquals(4.0f, outB.get(i), 0.001f);
                assertEquals(6.0f, outC.get(i), 0.001f);
            }

            // Asking again without executing returns the same values.
            executionPlan.execute();
            input.init(3.0f);
            executionPlan.execute().transferToHost(outA, outB, outC);
            for (int i = 0; i < SIZE; i++) {
                assertEquals(3.0f, outA.get(i), 0.001f);
                assertEquals(6.0f, outB.get(i), 0.001f);
                assertEquals(9.0f, outC.get(i), 0.001f);
            }
        }
    }

    /**
     * Two task-graphs in one execution plan. {@code transferToHost} is applied to every graph of
     * the plan, so the batched reads are issued per graph and must all have landed when the call
     * returns.
     */
    @Test
    public void testTwoTaskGraphsInOnePlan() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(2.0f);
        FloatArray firstA = new FloatArray(SIZE);
        FloatArray firstB = new FloatArray(SIZE);
        FloatArray secondA = new FloatArray(SIZE);
        FloatArray secondB = new FloatArray(SIZE);

        TaskGraph first = new TaskGraph("onDemandGraph0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, firstA, 1.0f) //
                .task("b", TestOnDemandTransfers::scale, input, firstB, 2.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, firstA, firstB);

        TaskGraph second = new TaskGraph("onDemandGraph1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scale, input, secondA, 3.0f) //
                .task("b", TestOnDemandTransfers::scale, input, secondB, 4.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, secondA, secondB);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(first.snapshot(), second.snapshot())) {
            executionPlan.execute().transferToHost(firstA, firstB, secondA, secondB);

            for (int i = 0; i < SIZE; i++) {
                assertEquals(2.0f, firstA.get(i), 0.001f);
                assertEquals(4.0f, firstB.get(i), 0.001f);
                assertEquals(6.0f, secondA.get(i), 0.001f);
                assertEquals(8.0f, secondB.get(i), 0.001f);
            }
        }
    }

    /**
     * Matrix outputs, so the reads go through a different buffer wrapper from the flat arrays the
     * other tests use.
     */
    @Test
    public void testMatrixOutputsOnDemand() throws TornadoExecutionPlanException {
        final int n = 64;
        Matrix2DFloat input = new Matrix2DFloat(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                input.set(i, j, i + j);
            }
        }
        Matrix2DFloat outA = new Matrix2DFloat(n, n);
        Matrix2DFloat outB = new Matrix2DFloat(n, n);

        TaskGraph taskGraph = new TaskGraph("onDemandMatrices") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestOnDemandTransfers::scaleMatrix, input, outA, 2.0f) //
                .task("b", TestOnDemandTransfers::scaleMatrix, input, outB, 3.0f) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outA, outB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute().transferToHost(outA, outB);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(2.0f * (i + j), outA.get(i, j), 0.001f);
                assertEquals(3.0f * (i + j), outB.get(i, j), 0.001f);
            }
        }
    }

    /**
     * On-demand transfers on a plan that also defers its outputs: the transfer has to await the
     * execution that is still in flight before it issues its own reads.
     */
    @Test
    public void testOnDemandWithDeferredOutputs() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray eager = new FloatArray(SIZE);
        FloatArray lazyA = new FloatArray(SIZE);
        FloatArray lazyB = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("onDemandDeferred") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("e", TestOnDemandTransfers::scale, input, eager, 2.0f) //
                .task("a", TestOnDemandTransfers::scale, input, lazyA, 3.0f) //
                .task("b", TestOnDemandTransfers::scale, input, lazyB, 4.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, eager) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, lazyA, lazyB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            for (int iteration = 1; iteration <= 4; iteration++) {
                input.init(iteration);
                executionPlan.execute().transferToHost(lazyA, lazyB);

                for (int i = 0; i < SIZE; i++) {
                    assertEquals(2.0f * iteration, eager.get(i), 0.001f);
                    assertEquals(3.0f * iteration, lazyA.get(i), 0.001f);
                    assertEquals(4.0f * iteration, lazyB.get(i), 0.001f);
                }
            }
        }
    }

    /** Eight on-demand outputs of mixed types in one call. */
    @Test
    public void testEightMixedOutputsInOneCall() throws TornadoExecutionPlanException {
        FloatArray floatInput = new FloatArray(SIZE);
        floatInput.init(2.0f);
        IntArray intInput = new IntArray(SIZE);
        intInput.init(10);

        FloatArray[] floatOutputs = new FloatArray[4];
        IntArray[] intOutputs = new IntArray[4];
        for (int i = 0; i < 4; i++) {
            floatOutputs[i] = new FloatArray(SIZE);
            intOutputs[i] = new IntArray(SIZE);
        }

        TaskGraph taskGraph = new TaskGraph("onDemandEight") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, floatInput, intInput);
        for (int i = 0; i < 4; i++) {
            taskGraph = taskGraph.task("f" + i, TestOnDemandTransfers::scale, floatInput, floatOutputs[i], 1.0f + i) //
                    .task("i" + i, TestOnDemandTransfers::increment, intInput, intOutputs[i], i);
        }
        taskGraph = taskGraph.transferToHost(DataTransferMode.UNDER_DEMAND, floatOutputs[0], floatOutputs[1], floatOutputs[2], floatOutputs[3], //
                intOutputs[0], intOutputs[1], intOutputs[2], intOutputs[3]);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute().transferToHost(floatOutputs[0], floatOutputs[1], floatOutputs[2], floatOutputs[3], //
                    intOutputs[0], intOutputs[1], intOutputs[2], intOutputs[3]);
        }

        for (int i = 0; i < SIZE; i++) {
            for (int k = 0; k < 4; k++) {
                assertEquals(2.0f * (1.0f + k), floatOutputs[k].get(i), 0.001f);
                assertEquals(10 + k, intOutputs[k].get(i));
            }
        }
    }
}
