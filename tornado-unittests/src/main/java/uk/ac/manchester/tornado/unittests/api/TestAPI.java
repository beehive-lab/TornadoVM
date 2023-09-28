/*
 * Copyright (c) 2013-2020, 2022 APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import static org.junit.Assert.assertNotNull;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.arrays.TestArrays;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.api.TestAPI
 * </code>
 * </p>
 */
public class TestAPI extends TornadoTestBase {
    // CHECKSTYLE:OFF

    @Test
    public void testLazyCopyOut() {
        final int N = 1024;
        int size = 20;
        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .transferToHost(DataTransferMode.USER_DEFINED, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionResult executionResult = executionPlan.execute();

        // Force data transfers from D->H after the execution of a task-graph
        executionResult.transferToHost(data);

        // Mark all device memory buffers as free, thus the TornadoVM runtime can reuse
        // device buffers for other execution plans.
        executionPlan.freeDeviceMemory();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

    @Test
    public void testLazyCopyOut2() {
        final int N = 128;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        taskGraph.task("t0", TestArrays::addAccumulator, data, 1);
        taskGraph.transferToHost(DataTransferMode.USER_DEFINED, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);

        TornadoExecutionResult executionResult = executionPlanPlan.execute();

        executionResult.transferToHost(data);

        executionPlanPlan.freeDeviceMemory();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

    @Test
    public void testWarmUp() {
        final int N = 128;
        int size = 20;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = size;
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestArrays::addAccumulator, data, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp() //
                .execute();

        executionPlan.freeDeviceMemory();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }
    // CHECKSTYLE:ON
}
