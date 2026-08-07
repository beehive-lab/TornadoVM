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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests for {@link TornadoExecutionPlan#transferToDevice()}: the plan's inputs are uploaded
 * without running any task, so a first execution does not have to pay for them and an
 * application does not have to run its plan once on dummy data to make the upload happen.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestOnDemandCopyIn
 * </code>
 */
public class TestOnDemandCopyIn extends TornadoTestBase {

    private static final int SIZE = 8192;

    public static void weightedCopy(FloatArray weights, FloatArray input, FloatArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, weights.get(i) * input.get(i));
        }
    }

    private static TaskGraph buildGraph(String name, FloatArray weights, FloatArray input, FloatArray output) {
        return new TaskGraph(name) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t", TestOnDemandCopyIn::weightedCopy, weights, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
    }

    /** Uploading up front must not change what the plan then computes. */
    @Test
    public void testTransferToDeviceThenExecute() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInThenRun", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.transferToDevice();
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, output.get(i), 0.001f);
        }
    }

    /** The upload runs the transfers only: no task runs, so no output is produced. */
    @Test
    public void testTransferToDeviceDoesNotRunTasks() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);
        output.init(-1.0f);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInOnly", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.transferToDevice();

            for (int i = 0; i < SIZE; i++) {
                assertEquals(-1.0f, output.get(i), 0.001f);
            }

            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, output.get(i), 0.001f);
        }
    }

    /**
     * A {@code FIRST_EXECUTION} input really is uploaded by the call, not by the execution that
     * follows: changing the host copy in between has no effect on the result.
     */
    @Test
    public void testFirstExecutionInputIsUploadedByTheCall() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInFirstExecution", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.transferToDevice();

            // Only the FIRST_EXECUTION array is expected to keep the value it had at upload time.
            weights.init(100.0f);
            executionPlan.execute();

            for (int i = 0; i < SIZE; i++) {
                assertEquals(6.0f, output.get(i), 0.001f);
            }

            // EVERY_EXECUTION inputs are re-uploaded, so a change to those does show up.
            input.init(4.0f);
            executionPlan.execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(8.0f, output.get(i), 0.001f);
            }
        }
    }

    /** The call applies to every task-graph of the plan. */
    @Test
    public void testTransferToDeviceWithTwoTaskGraphs() throws TornadoExecutionPlanException {
        FloatArray weightsA = new FloatArray(SIZE);
        weightsA.init(2.0f);
        FloatArray weightsB = new FloatArray(SIZE);
        weightsB.init(5.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray outputA = new FloatArray(SIZE);
        FloatArray outputB = new FloatArray(SIZE);

        ImmutableTaskGraph first = buildGraph("copyInGraph0", weightsA, input, outputA).snapshot();
        ImmutableTaskGraph second = buildGraph("copyInGraph1", weightsB, input, outputB).snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(first, second)) {
            executionPlan.transferToDevice();
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, outputA.get(i), 0.001f);
            assertEquals(15.0f, outputB.get(i), 0.001f);
        }
    }

    /** Calling it more than once, and after an execution, is harmless. */
    @Test
    public void testRepeatedTransferToDevice() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInRepeat", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.transferToDevice();
            executionPlan.transferToDevice();
            executionPlan.execute();
            executionPlan.transferToDevice();
            input.init(5.0f);
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(10.0f, output.get(i), 0.001f);
        }
    }

    /** Pre-compilation and up-front upload compose: neither needs the other. */
    @Test
    public void testTransferToDeviceAfterPreCompilation() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(3.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInPreCompiled", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation();
            executionPlan.transferToDevice();
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(9.0f, output.get(i), 0.001f);
        }
    }
}
