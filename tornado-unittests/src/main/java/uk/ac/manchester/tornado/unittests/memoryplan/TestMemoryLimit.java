/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.memoryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.memoryplan.TestMemoryLimit
 * </code>
 * </p>
 */
public class TestMemoryLimit extends TornadoTestBase {

    /**
     * Set the number of elements to select ~300MB per array.
     */
    private static final int NUM_ELEMENTS = 78643200;   // 314MB for an array of Integers
    private static IntArray a = new IntArray(NUM_ELEMENTS);
    private static IntArray b = new IntArray(NUM_ELEMENTS);
    private static IntArray c = new IntArray(NUM_ELEMENTS);

    private static int value = 10000000;

    @BeforeAll
    public static void setUpBeforeClass() {
        a = new IntArray(NUM_ELEMENTS);
        b = new IntArray(NUM_ELEMENTS);
        c = new IntArray(NUM_ELEMENTS);
        a.init(1);
        b.init(2);
    }

    @Test
    public void testWithMemoryLimitOver() {

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Limit the amount of memory to be used on the target accelerator.
        executionPlan.withMemoryLimit("1GB").execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
        executionPlan.freeDeviceMemory();
    }

    @Test
    public void testWithMemoryLimitUnder() {
        Assertions.assertThrows(TornadoMemoryException.class, () -> {
            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                    .task("t0", TestHello::add, a, b, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

            // Limit the amount of memory to be used on the target accelerator.
            // Since the memory required is ~900MB, the TornadoVM runtime will throw an
            // exception because we set the limit to 512MB.
            executionPlan.withMemoryLimit("512MB").execute();
        });

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
    }

    /**
     * Test that sets a limit before executing the execution plan the first time,
     * and it disables it for the second execution.
     */
    @Test
    public void enableAndDisable() {

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Limit the amount of memory to be used on the target accelerator.
        executionPlan.withMemoryLimit("1GB").execute();

        // Unlimit the amount of memory. 
        executionPlan.withoutMemoryLimit().execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
        executionPlan.freeDeviceMemory();
    }
}
