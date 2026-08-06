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
 * Task graphs with more than one copy-out. Only the last copy-out of a graph is compiled into
 * the blocking bytecode; the others are read asynchronously, so these tests check that every
 * output is still complete and up to date by the time {@code execute()} returns, including the
 * cases that keep the blocking read (partial copies and batches).
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestMultipleCopyOuts
 * </code>
 */
public class TestMultipleCopyOuts extends TornadoTestBase {

    private static final int SIZE = 8192;

    public static void scale(FloatArray input, FloatArray output, float alpha) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, alpha * input.get(i));
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

    /**
     * Four outputs: three of them are non-terminal copy-outs, so three asynchronous reads have
     * to have landed before {@code execute()} returns.
     */
    @Test
    public void testFourOutputsInOneGraph() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(2.0f);

        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        FloatArray outC = new FloatArray(SIZE);
        FloatArray outD = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("multiOut") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestMultipleCopyOuts::scale, input, outA, 1.0f) //
                .task("b", TestMultipleCopyOuts::scale, input, outB, 2.0f) //
                .task("c", TestMultipleCopyOuts::scale, input, outC, 3.0f) //
                .task("d", TestMultipleCopyOuts::scale, input, outD, 4.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outA, outB, outC, outD);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(2.0f, outA.get(i), 0.001f);
            assertEquals(4.0f, outB.get(i), 0.001f);
            assertEquals(6.0f, outC.get(i), 0.001f);
            assertEquals(8.0f, outD.get(i), 0.001f);
        }
    }

    /**
     * Same graph executed repeatedly with a different input each time. A read that has not
     * landed shows up here as an output from the previous execution rather than as a wrong
     * value, which a single-execution test would miss.
     */
    @Test
    public void testMultipleOutputsAcrossExecutions() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        FloatArray outC = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("multiOutRepeat") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestMultipleCopyOuts::scale, input, outA, 1.0f) //
                .task("b", TestMultipleCopyOuts::scale, input, outB, 2.0f) //
                .task("c", TestMultipleCopyOuts::scale, input, outC, 3.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outA, outB, outC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int iteration = 1; iteration <= 8; iteration++) {
                input.init(iteration);
                executionPlan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(iteration, outA.get(i), 0.001f);
                    assertEquals(2.0f * iteration, outB.get(i), 0.001f);
                    assertEquals(3.0f * iteration, outC.get(i), 0.001f);
                }
            }
        }
    }

    /**
     * Mixed output types, so the reads go through different buffer wrappers within one graph.
     */
    @Test
    public void testMixedOutputTypes() throws TornadoExecutionPlanException {
        FloatArray floatInput = new FloatArray(SIZE);
        floatInput.init(1.5f);
        IntArray intInput = new IntArray(SIZE);
        intInput.init(7);

        FloatArray floatOutput = new FloatArray(SIZE);
        IntArray intOutput = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("mixedOut") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, floatInput, intInput) //
                .task("f", TestMultipleCopyOuts::scale, floatInput, floatOutput, 2.0f) //
                .task("i", TestMultipleCopyOuts::increment, intInput, intOutput, 5) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, floatOutput, intOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(3.0f, floatOutput.get(i), 0.001f);
            assertEquals(12, intOutput.get(i));
        }
    }

    /**
     * An under-demand partial copy-out alongside an every-execution one. The partial read keeps
     * the blocking path, so this covers the guard rather than the asynchronous read.
     */
    @Test
    public void testPartialCopyOutWithSecondOutput() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray eagerOutput = new FloatArray(SIZE);
        FloatArray onDemandOutput = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("partialOut") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestMultipleCopyOuts::scale, input, eagerOutput, 2.0f) //
                .task("b", TestMultipleCopyOuts::scale, input, onDemandOutput, 5.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, eagerOutput) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, onDemandOutput);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();

            DataRange dataRange = new DataRange(onDemandOutput);
            executionResult.transferToHost(dataRange.withSize(SIZE / 2));
            executionResult.transferToHost(dataRange.withOffset(SIZE / 2).withSize(SIZE / 2));
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, eagerOutput.get(i), 0.001f);
            assertEquals(15.0f, onDemandOutput.get(i), 0.001f);
        }
    }

    /**
     * Batched copy-out, repeated so the per-chunk reads are exercised more than once. Batch
     * chunks keep the blocking read, so this is the other half of the guard.
     *
     * <p>Deliberately a single output: a batched graph with two outputs fails on {@code develop}
     * with a null device buffer, independently of how the reads are issued.
     */
    @Test
    public void testBatchedCopyOut() throws TornadoExecutionPlanException {
        final int batchedSize = 1024 * 1024;
        FloatArray input = new FloatArray(batchedSize);
        FloatArray output = new FloatArray(batchedSize);

        TaskGraph taskGraph = new TaskGraph("batchedOut") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestMultipleCopyOuts::scale, input, output, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB");
            for (int iteration = 1; iteration <= 3; iteration++) {
                input.init(iteration);
                executionPlan.execute();
                for (int i = 0; i < batchedSize; i++) {
                    assertEquals(2.0f * iteration, output.get(i), 0.001f);
                }
            }
        }
    }

    /**
     * Two matrix outputs: the first one is a non-terminal copy-out, so it goes through the
     * asynchronous read while the second stays blocking. Matrices use a different buffer wrapper
     * from the flat arrays the other tests cover.
     */
    @Test
    public void testTwoMatrixOutputs() throws TornadoExecutionPlanException {
        final int n = 64;
        Matrix2DFloat input = new Matrix2DFloat(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                input.set(i, j, i + j);
            }
        }
        Matrix2DFloat outA = new Matrix2DFloat(n, n);
        Matrix2DFloat outB = new Matrix2DFloat(n, n);

        TaskGraph taskGraph = new TaskGraph("matrixOut") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("a", TestMultipleCopyOuts::scaleMatrix, input, outA, 2.0f) //
                .task("b", TestMultipleCopyOuts::scaleMatrix, input, outB, 3.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outA, outB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(2.0f * (i + j), outA.get(i, j), 0.001f);
                assertEquals(3.0f * (i + j), outB.get(i, j), 0.001f);
            }
        }
    }
}
