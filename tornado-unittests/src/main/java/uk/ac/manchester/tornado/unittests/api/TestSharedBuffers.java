/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestSharedBuffers
 * </code>
 * </p>
 */
public class TestSharedBuffers extends TornadoTestBase {
    private static final int numElements = 16;

    /**
     * An empty utility method that performs no operations.
     * Used to force array transfer to device to be consumed later.
     *
     * @param a
     *     First integer array to be transferred to device
     * @param b
     *     Second integer array to be transferred to device
     */
    public static void empty(IntArray a, IntArray b) {
        if (a.getSize() != b.getSize()) {
        }
    }

    // Utility methods to satisfy task requirements for each graph
    public static void initializeContext(IntArray context, int size) {
        for (int i = 0; i < size; i++) {
            context.set(i, 1);  // Set initial context values
        }
    }

    public static void forcePropagate(IntArray output) {
        output.set(0, output.get(0));
    }

    public static void updateContext(IntArray context, int size) {
        for (int i = 0; i < size; i++) {
            context.set(i, context.get(i) + 1);  // Increment context
        }
    }

    public static void prepareOutput(IntArray context, int size) {
        for (int i = 0; i < size; i++) {
            context.set(i, context.get(i) * 2);  // Modify context before final processing
        }
    }

    public static void finalizeContext(IntArray context, int size) {
        for (int i = 0; i < size; i++) {
            context.set(i, context.get(i) + 10);  // Final context modification
        }
    }

    public static void processBuffer(IntArray input, IntArray output, IntArray context, int size) {
        for (int i = 0; i < size; i++) {
            output.set(i, input.get(i) + context.get(i));
        }
    }

    @Test
    public void testSingleReadWriteSharedObject() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(10);
        b.init(20);

        // Create first task graph named "s0"
        TaskGraph tg1 = new TaskGraph("s0") //
                // Transfer arrays 'a' and 'b' to the device only on first execution
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                // Execute the add method (a + b = c)
                .task("t0", TestHello::add, a, b, c) //
                // Keep array 'c' on the device memory for later use
                .persistOnDevice(c);

        // Create second task graph named "s1"
        TaskGraph tg2 = new TaskGraph("s1") //
                // Get array 'c' from the first task graph (no new transfer needed)
                .consumeFromDevice(tg1.getTaskGraphName(), c) //
                // Execute the add method (c + c = c), effectively doubling the value
                .task("t1", TestHello::add, c, c, c) //
                // Transfer results back to host memory after execution
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            // Execute the first graph (a + b = c) -> c = 30
            executionPlan.withGraph(0).execute();

            // Execute the second graph (c + c = c) -> c = 60
            executionPlan.withGraph(1).execute();

            // Verify results: for each element, check if value is 60
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(60, c.get(i)); // Expected: (10 + 20) + (30 + 30) = 60
            }

        }
    }

    @Test
    public void testMixInputConsumeAndCopy() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);

        a.init(10);
        b.init(20);
        d.init(50);

        // Create first task graph named "s0"
        TaskGraph tg1 = new TaskGraph("s0") //
                // Transfer arrays 'a' and 'b' to the device only on first execution
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                // Execute the add method (a + b = c)
                .task("t0", TestHello::add, a, b, c) //
                // Keep array 'c' on the device memory for later use
                .persistOnDevice(c);

        // Create second task graph named "s1"
        TaskGraph tg2 = new TaskGraph("s1") //
                // Get array 'c' from the first task graph (no new transfer needed)
                .consumeFromDevice(tg1.getTaskGraphName(), c) //
                // Transfer array 'd' to the device (this is a new input, not from first graph)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, d) //
                // Execute the add method (c + d = c), adding 'd' to the existing result in 'c'
                .task("t1", TestHello::add, c, d, c) //
                // Transfer results back to host memory after execution
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            // Execute the first graph (a + b = c) -> c = 30
            executionPlan.withGraph(0).execute();
            // Execute the second graph (c + d = c) -> c = 80
            executionPlan.withGraph(1).execute();

            // Verify results: for each element, check if value is 80
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(80, c.get(i)); // Expected: (10 + 20) + 50 = 80
            }
        }
    }

    /**
     * Tests input data sharing and persistence between task graphs in Tornado.
     * This test verifies that input data can be properly copied to a device,
     * persisted, and then consumed by a subsequent task graph.
     *
     * <p>The test executes two task graphs:
     * <ol>
     * <li>A "force copy" graph that transfers input and intermediateValues arrays to the device
     * without modifying them, and persists the input </li>
     * <li>A computation graph that consumes the persisted input data, re-transfers
     * the intermediateValues data, and performs an addition operation</li>
     * </ol>
     *
     */
    @Test
    public void testForcedCopyInData() throws TornadoExecutionPlanException {
        // Create test arrays
        IntArray input = new IntArray(numElements);
        IntArray intermediateValues = new IntArray(numElements);
        IntArray output = new IntArray(numElements);

        // Initialize with test values
        input.init(25);
        intermediateValues.init(5);

        // Task Graph -1: Hack to force copy-in data to the device
        TaskGraph forceCopyGraph = new TaskGraph("forceCopyGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, intermediateValues) //
                .task("emptyTask", TestSharedBuffers::empty, input, intermediateValues) //
                .persistOnDevice(input); //

        // Task Graph 0: Simplified main computation with single task
        TaskGraph computeGraph = new TaskGraph("computeGraph")
                // Consume the input data from previous graph
                .consumeFromDevice(forceCopyGraph.getTaskGraphName(), input)
                // Re-transfrer
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, intermediateValues)
                // Simple task: add input and intermediateValues to get output
                .task("addTask", TestHello::add, input, intermediateValues, output)
                // Transfer results back to host
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(forceCopyGraph.snapshot(), computeGraph.snapshot())) {
            // Execute the forced copy graph first
            executionPlan.withGraph(0).execute();

            // Then execute the main computation graph
            executionPlan.withGraph(1).execute();

            // Verify results: input + intermediateValues = 25 + 5 = 30
            for (int i = 0; i < input.getSize(); i++) {
                assertEquals(30, output.get(i));
            }
        }
    }

    @Test
    public void testMultipleSharedObjects() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);

        a.init(10);
        b.init(20);

        // Create first task graph named "s0"
        TaskGraph tg1 = new TaskGraph("s0") //
                // Transfer arrays 'a' and 'b' to the device only on first execution
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                // Execute the add method (a + b = c)
                .task("t0", TestHello::add, a, b, c) //
                // Keep arrays 'a' and 'b' on the device memory for later use
                .persistOnDevice(a, b);

        // Create second task graph named "s1"
        TaskGraph tg2 = new TaskGraph("s1") //
                // Get arrays 'a' and 'b' from the first task graph (no new transfer needed)
                .consumeFromDevice(tg1.getTaskGraphName(), a, b) //
                // Execute the add method (a + b = d), creating separate output in 'd'
                .task("t1", TestHello::add, a, b, d) //
                // Transfer results back to host memory after execution
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            // Execute the first graph (a + b = c)
            executionPlan.withGraph(0).execute();

            // Execute the second graph (a + b = d)
            executionPlan.withGraph(1).execute();

            // Verify results: for each element, check if value is 30
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(30, d.get(i)); // Expected: 10 + 20 = 30
            }
        }
    }

    @Test
    public void testMultipleSharedObjectsEmptyConsume() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);

        a.init(10);
        b.init(20);

        // Create first task graph named "s0"
        TaskGraph tg1 = new TaskGraph("s0") //
                // Transfer arrays 'a' and 'b' to the device only on first execution
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                // Execute the add method (a + b = c)
                .task("t0", TestHello::add, a, b, c) //
                // Keep arrays 'a' and 'b' on the device memory for later use
                .persistOnDevice(a, b);

        // Create second task graph named "s1"
        TaskGraph tg2 = new TaskGraph("s1") //
                // Get arrays 'a' and 'b' from the first task graph (no new transfer needed)
                .consumeFromDevice(a, b) //
                // Execute the add method (a + b = d), creating separate output in 'd'
                .task("t1", TestHello::add, a, b, d) //
                // Transfer results back to host memory after execution
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {
            // Execute the first graph (a + b = c)
            executionPlan.withGraph(0).execute();

            // Execute the second graph (a + b = d)
            executionPlan.withGraph(1).execute();

            // Verify results: for each element, check if value is 30
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(30, d.get(i)); // Expected: 10 + 20 = 30
            }
        }
    }

    @Test
    public void testThreeTaskGraphsWithSharedBuffers() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);
        IntArray r = new IntArray(numElements);

        a.init(10);
        b.init(20);
        d.init(5);

        // Create first task graph named "s0"
        TaskGraph tg1 = new TaskGraph("s0") //
                // Transfer arrays 'a' and 'b' to the device only on first execution
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                // Execute the add method (a + b = c)
                .task("t0", TestHello::add, a, b, c) //
                // Keep arrays 'a' and 'b' on the device memory for later use
                .persistOnDevice(c);

        // Create second task graph named "s1"
        TaskGraph tg2 = new TaskGraph("s1") //
                // Get arrays 'a' and 'b' from the first task graph (no new transfer needed)
                .consumeFromDevice("s0", c) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, d) //
                // Execute the add method (a + b = d), creating separate output in 'd'
                .task("t1", TestHello::add, c, d, r) //
                // Transfer results back to host memory after execution
                .persistOnDevice(r);

        // Create third task graph named "s2"
        TaskGraph tg3 = new TaskGraph("s2") //
                // Get arrays 'a' and 'b' from the first task graph (no new transfer needed)
                .consumeFromDevice("s1", r) //
                // Execute the add method (a + b = d), creating separate output in 'd'
                .task("t1", TestHello::add, r, r, r) //
                // Transfer results back to host memory after execution
                .transferToHost(DataTransferMode.EVERY_EXECUTION, r);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot(), tg3.snapshot())) {
            // Execute the first graph (a + b = c)
            executionPlan.withGraph(0).execute();
            // Execute the second graph (a + b = d)
            executionPlan.withGraph(1).execute();
            // Execute the second graph (a + b = d)
            executionPlan.withGraph(2).execute();

            // Verify results: for each element, check if value is 30
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(70, r.get(i)); // Expected: 35 + 35 = 70
            }
        }
    }

    @Test
    public void testThreeTaskGraphsWithSharedBuffersEmptyConsume() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);
        IntArray d = new IntArray(numElements);
        IntArray r = new IntArray(numElements);

        a.init(10);
        b.init(20);
        d.init(5);

        // Create first task graph named "s0"
        TaskGraph tg1 = new TaskGraph("s0") //
                // Transfer arrays 'a' and 'b' to the device only on first execution
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                // Execute the add method (a + b = c)
                .task("t0", TestHello::add, a, b, c) //
                // Keep arrays 'a' and 'b' on the device memory for later use
                .persistOnDevice(c);

        // Create second task graph named "s1"
        TaskGraph tg2 = new TaskGraph("s1") //
                // Get arrays 'a' and 'b' from the first task graph (no new transfer needed)
                .consumeFromDevice(c) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, d) //
                // Execute the add method (a + b = d), creating separate output in 'd'
                .task("t1", TestHello::add, c, d, r) //
                // Transfer results back to host memory after execution
                .persistOnDevice(r);

        // Create third task graph named "s2"
        TaskGraph tg3 = new TaskGraph("s2") //
                // Get arrays 'a' and 'b' from the first task graph (no new transfer needed)
                .consumeFromDevice(r) //
                // Execute the add method (a + b = d), creating separate output in 'd'
                .task("t1", TestHello::add, r, r, r) //
                // Transfer results back to host memory after execution
                .transferToHost(DataTransferMode.EVERY_EXECUTION, r);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot(), tg3.snapshot())) {
            // Execute the first graph (a + b = c)
            executionPlan.withGraph(0).execute();
            // Execute the second graph (c + d = r)
            executionPlan.withGraph(1).execute();
            // Execute the second graph (r + r = r)
            TornadoExecutionResult executionResult = executionPlan.withGraph(2).execute();

            // Verify results: for each element, check if value is 30
            for (int i = 0; i < a.getSize(); i++) {
                assertEquals(70, r.get(i)); // Expected: 35 + 35 = 70
            }
        }
    }

    @Test
    public void testFourTaskGraphsWithPersistentBuffers() throws TornadoExecutionPlanException {
        int numElements = 10;

        // Create arrays for task graphs
        IntArray inputBuffer = new IntArray(numElements);
        IntArray intermediateBuffer1 = new IntArray(numElements);
        IntArray intermediateBuffer2 = new IntArray(numElements);
        IntArray outputBuffer = new IntArray(numElements);

        IntArray contextBuffer = new IntArray(numElements);

        inputBuffer.init(10);
        contextBuffer.init(2);

        // @formatter:off
        // First Task Graph: Initial Processing
        TaskGraph firstGraph = new TaskGraph("firstProcessing")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inputBuffer, contextBuffer)
                .task("initializeContext", TestSharedBuffers::initializeContext, contextBuffer, numElements)
                .task("processInitial", TestSharedBuffers::processBuffer, inputBuffer, intermediateBuffer1, contextBuffer, numElements)
                .persistOnDevice(intermediateBuffer1, contextBuffer);

        // Second Task Graph: Intermediate Processing
        TaskGraph secondGraph = new TaskGraph("intermediateProcessing")
                .consumeFromDevice(firstGraph.getTaskGraphName(), intermediateBuffer1, contextBuffer)
                .task("updateContext", TestSharedBuffers::updateContext, contextBuffer, numElements)
                .task("processIntermediate", TestSharedBuffers::processBuffer, intermediateBuffer1, intermediateBuffer2, contextBuffer, numElements)
                .persistOnDevice(intermediateBuffer2, contextBuffer);

        // Third Task Graph: Pre-Final Processing
        TaskGraph thirdGraph = new TaskGraph("preFinalProcessing")
                .consumeFromDevice(secondGraph.getTaskGraphName(), intermediateBuffer2, contextBuffer)
                .task("prepareOutput", TestSharedBuffers::prepareOutput, contextBuffer, numElements)
                .task("processPreFinal", TestSharedBuffers::processBuffer, intermediateBuffer2, outputBuffer, contextBuffer, numElements)
                .persistOnDevice(outputBuffer, contextBuffer);

        // Fourth Task Graph: Final Transfer
        TaskGraph fourthGraph = new TaskGraph("finalTransfer")
                .consumeFromDevice(thirdGraph.getTaskGraphName(), outputBuffer, contextBuffer)
                .task("finalizeContext", TestSharedBuffers::finalizeContext, contextBuffer, numElements)
                .task("empty", TestSharedBuffers::forcePropagate, outputBuffer)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputBuffer, contextBuffer);

        // @formatter:on
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(firstGraph.snapshot(), secondGraph.snapshot(), thirdGraph.snapshot(), fourthGraph.snapshot())) {

            // Execute each graph sequentially
            for (int graphIndex = 0; graphIndex < 4; graphIndex++) {
                executionPlan.withGraph(graphIndex).execute();
            }

            // Minimal verification
            boolean hasNonZeroOutput = false;
            for (int i = 0; i < numElements; i++) {
                if (outputBuffer.get(i) != 0) {
                    hasNonZeroOutput = true;
                }
            }

            assertTrue("Output array should have non-zero values", hasNonZeroOutput);
        }
    }

    @Test
    public void testThreeTaskGraphsWithSharedContextBuffer() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        // This is the shared array that will be persisted and consumed across all task graphs
        IntArray sharedContext = new IntArray(numElements);

        a.init(10);
        b.init(20);
        sharedContext.init(1);

        // First task graph: initialize and use the context array
        TaskGraph tg1 = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b).task("t0", TestHello::add, a, b, c)
                // Persist both the result and the input arrays
                .persistOnDevice(c);

        // Second task graph: reuse the shared context array
        TaskGraph tg2 = new TaskGraph("s1")
                // Consume both the result and the context from the previous graph
                .consumeFromDevice("s0", c)
                // Modify the shared context (using updateContext from the failing test)
                .task("updateContext", TestSharedBuffers::updateContext, sharedContext, numElements)
                // Use the context in computation
                .task("addWithContext", TestSharedBuffers::processBuffer, c, c, sharedContext, numElements)
                // Persist both again for the next graph
                .persistOnDevice(c, sharedContext);

        // Third task graph: reuse the shared context array again
        TaskGraph tg3 = new TaskGraph("s2")
                // Consume both the result and the context from the previous graph
                .consumeFromDevice("s1", c, sharedContext)
                // Modify the context one more time
                .task("finalizeContext", TestSharedBuffers::finalizeContext, sharedContext, numElements)
                // Apply the context to the result
                .task("processWithFinalContext", TestSharedBuffers::processBuffer, c, c, sharedContext, numElements)
                // Add a force propagation operation to ensure proper transfer
                .task("forcePropagate", TestSharedBuffers::forcePropagate, c)
                // Transfer both back to the host
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, sharedContext);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot(), tg3.snapshot())) {

            for (int i = 0; i < 5; i++) {
                executionPlan.withGraph(0).execute();

                // Execute the second graph
                executionPlan.withGraph(1).execute();

                // Execute the third graph
                executionPlan.withGraph(2).execute();
            }

            executionPlan.getTraceExecutionPlan();

            // Add assertions based on expected values
            boolean hasNonZeroOutput = false;
            for (int i = 0; i < numElements; i++) {
                if (c.get(i) != 0) {
                    hasNonZeroOutput = true;
                    break;
                }
            }

            assertTrue("Output array should have non-zero values", hasNonZeroOutput);
        }
    }
}