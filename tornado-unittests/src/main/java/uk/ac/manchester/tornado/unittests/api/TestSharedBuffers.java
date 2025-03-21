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

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
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
     * Used to force buffer transfer to device to be consumed later.
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
     * Tests buffer sharing and persistence between task graphs in Tornado.
     * This test verifies that buffers can be properly copied to a device,
     * persisted, and then consumed by a subsequent task graph.
     *
     * <p>The test executes two task graphs:
     * <ol>
     * <li>A "force copy" graph that transfers input and context buffers to the device
     * without modifying them, and persists the input buffer</li>
     * <li>A computation graph that consumes the persisted input buffer, re-transfers
     * the context buffer, and performs an addition operation</li>
     * </ol>
     *
     */
    @Test
    public void testForcedBufferCopy() throws TornadoExecutionPlanException {
        // Create test arrays
        IntArray inputBuffer = new IntArray(numElements);
        IntArray contextBuffer = new IntArray(numElements);
        IntArray outputBuffer = new IntArray(numElements);

        // Initialize with test values
        inputBuffer.init(25);
        contextBuffer.init(5);

        // Task Graph -1: Hack to force copy of buffers to the device
        TaskGraph forceCopyGraph = new TaskGraph("forceCopyGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputBuffer, contextBuffer) //
                .task("emptyTask", TestSharedBuffers::empty, inputBuffer, contextBuffer) //
                .persistOnDevice(inputBuffer); //

        // Task Graph 0: Simplified main computation with single task
        TaskGraph computeGraph = new TaskGraph("computeGraph")
                // Consume the input buffer from previous graph
                .consumeFromDevice(forceCopyGraph.getTaskGraphName(), inputBuffer)
                // Re-transfer context buffer
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, contextBuffer)
                // Simple task: add inputBuffer and contextBuffer to get outputBuffer
                .task("addTask", TestHello::add, inputBuffer, contextBuffer, outputBuffer)
                // Transfer results back to host
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputBuffer);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(forceCopyGraph.snapshot(), computeGraph.snapshot())) {
            // Execute the forced copy graph first
            executionPlan.withGraph(0).execute();

            // Then execute the main computation graph
            executionPlan.withGraph(1).execute();

            // Verify results: inputBuffer + contextBuffer = 25 + 5 = 30
            for (int i = 0; i < inputBuffer.getSize(); i++) {
                assertEquals(30, outputBuffer.get(i));
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
                //
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

}