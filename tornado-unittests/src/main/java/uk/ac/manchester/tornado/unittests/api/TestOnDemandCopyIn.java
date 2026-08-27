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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
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

    public static void weightedCopyWithContext(KernelContext context, FloatArray weights, FloatArray input, FloatArray output) {
        int index = context.globalIdx;
        output.set(index, weights.get(index) * input.get(index));
    }

    /**
     * CUDA graphs are a CUDA-backend feature. On every other backend these tests report the
     * configuration as unsupported rather than failing - a JUnit Assume would be counted as a PASS
     * by the test runner, so the typed *NotSupported exceptions are what the guard has to raise.
     */
    private void requireCudaGraphs() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
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

    /**
     * Updating one object: the new host contents are on the device without the plan running, so
     * the next execution sees them even though the object is declared {@code FIRST_EXECUTION} and
     * would otherwise never be uploaded again.
     */
    @Test
    public void testTransferSingleObject() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInSingle", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(6.0f, output.get(i), 0.001f);
            }

            // FIRST_EXECUTION: without the explicit upload this change would never reach the device.
            weights.init(10.0f);
            executionPlan.transferToDevice(weights);
            executionPlan.execute();

            for (int i = 0; i < SIZE; i++) {
                assertEquals(30.0f, output.get(i), 0.001f);
            }
        }
    }

    /** Uploading one object before the plan has ever run allocates for it on the way. */
    @Test
    public void testTransferSingleObjectBeforeFirstExecution() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(4.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(2.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInSingleUpfront", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.transferToDevice(weights);
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(8.0f, output.get(i), 0.001f);
        }
    }

    /** Several objects in one call, and objects a graph does not know are ignored. */
    @Test
    public void testTransferSeveralObjectsAndUnknownOnes() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);
        FloatArray strangerToThisPlan = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInSeveral", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();

            weights.init(3.0f);
            input.init(5.0f);
            executionPlan.transferToDevice(weights, input, strangerToThisPlan);
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(15.0f, output.get(i), 0.001f);
        }
    }

    /** The targeted upload reaches every task-graph of the plan that takes the object. */
    @Test
    public void testTransferSingleObjectSharedByTwoGraphs() throws TornadoExecutionPlanException {
        FloatArray weightsA = new FloatArray(SIZE);
        weightsA.init(2.0f);
        FloatArray weightsB = new FloatArray(SIZE);
        weightsB.init(3.0f);
        FloatArray sharedInput = new FloatArray(SIZE);
        sharedInput.init(1.0f);
        FloatArray outputA = new FloatArray(SIZE);
        FloatArray outputB = new FloatArray(SIZE);

        ImmutableTaskGraph first = buildGraph("copyInShared0", weightsA, sharedInput, outputA).snapshot();
        ImmutableTaskGraph second = buildGraph("copyInShared1", weightsB, sharedInput, outputB).snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(first, second)) {
            executionPlan.execute();

            weightsA.init(4.0f);
            weightsB.init(5.0f);
            executionPlan.transferToDevice(weightsA, weightsB);
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(4.0f, outputA.get(i), 0.001f);
            assertEquals(5.0f, outputB.get(i), 0.001f);
        }
    }

    /**
     * A transfers-only pass over a plan that uses CUDA graphs. The pass skips the capture and
     * launch bytecodes, but it still has to consume their operands: the reads are positional, so
     * getting one wrong leaves a {@code graphId} in the stream and the rest of the bytecode is
     * decoded from the wrong offset. The plan must still capture and replay correctly afterwards.
     */
    @Test
    public void testTransferToDeviceOnACudaGraphPlan() throws TornadoExecutionPlanException {
        requireCudaGraphs();

        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInCudaGraph", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCUDAGraph();
            executionPlan.transferToDevice();

            // Iteration 0 captures, the rest replay; the upload must not have disturbed either.
            for (int iteration = 0; iteration < 4; iteration++) {
                input.init(3.0f + iteration);
                executionPlan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals("iteration " + iteration, 2.0f * (3.0f + iteration), output.get(i), 0.001f);
                }
            }
        }
    }

    /**
     * The same, with the kernel written against {@link KernelContext} and driven by a
     * {@link GridScheduler}. A KernelContext task carries no {@code @Parallel} loop, so the grid
     * comes from the plan rather than from the bytecode - the transfers-only pass must leave that
     * path alone as well.
     */
    @Test
    public void testTransferToDeviceOnACudaGraphPlanWithKernelContext() throws TornadoExecutionPlanException {
        requireCudaGraphs();

        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        KernelContext context = new KernelContext();
        TaskGraph taskGraph = new TaskGraph("copyInCudaGraphContext") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t", TestOnDemandCopyIn::weightedCopyWithContext, context, weights, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        WorkerGrid workerGrid = new WorkerGrid1D(SIZE);
        GridScheduler gridScheduler = new GridScheduler("copyInCudaGraphContext.t", workerGrid);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withCUDAGraph().withGridScheduler(gridScheduler);
            executionPlan.transferToDevice();

            for (int iteration = 0; iteration < 4; iteration++) {
                input.init(3.0f + iteration);
                executionPlan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals("iteration " + iteration, 2.0f * (3.0f + iteration), output.get(i), 0.001f);
                }
            }
        }
    }

    /**
     * A targeted upload into a captured plan. The weights are {@code FIRST_EXECUTION}, so once the
     * graph is captured nothing would ever refresh them: the upload has to reach the buffer the
     * captured graph reads from, not a new one.
     */
    @Test
    public void testTargetedUploadIntoACapturedCudaGraph() throws TornadoExecutionPlanException {
        requireCudaGraphs();

        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph("copyInCudaGraphTargeted", weights, input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCUDAGraph();

            // Captures on the first execution.
            executionPlan.execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(6.0f, output.get(i), 0.001f);
            }

            // Replays, with new weights placed into the buffer the captured graph reads.
            weights.init(5.0f);
            executionPlan.transferToDevice(weights);
            executionPlan.execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(15.0f, output.get(i), 0.001f);
            }
        }
    }
}
