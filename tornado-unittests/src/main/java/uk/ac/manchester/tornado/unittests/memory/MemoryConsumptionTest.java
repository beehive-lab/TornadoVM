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
package uk.ac.manchester.tornado.unittests.memory;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.memory.MemoryConsumptionTest
 * </code>
 * </p>
 */
public class MemoryConsumptionTest extends TestMemoryCommon {

    @Test
    public void testMemoryTransferBytes() throws TornadoExecutionPlanException {

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMemoryLimit::add, a, b, c, value) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.withProfiler(ProfilerMode.SILENT).execute();
            long totalBytesTransferred = executionResult.getProfilerResult().getTotalBytesTransferred();
            long copyInBytes = executionResult.getProfilerResult().getTotalBytesCopyIn();
            long copyOutBytes = executionResult.getProfilerResult().getTotalBytesCopyOut();
            assertEquals(copyInBytes + copyOutBytes, totalBytesTransferred);
        }
    }

    @Test
    public void testTotalMemoryUsage() throws TornadoExecutionPlanException {

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMemoryLimit::add, a, b, c, value) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.withProfiler(ProfilerMode.SILENT).execute();
            long totalMemoryUsedInBytes = executionResult.getProfilerResult().getTotalDeviceMemoryUsage();

            // 3 Arrays
            final long sizeAllocated = a.getNumBytesOfSegmentWithHeader() * 3;
            assertEquals(sizeAllocated, totalMemoryUsedInBytes);

        }
    }

    @Test
    public void testCurrentMemoryUsage() throws TornadoExecutionPlanException {

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMemoryLimit::add, a, b, c, value) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withProfiler(ProfilerMode.SILENT).execute();
            long currentMemoryUsageInBytes = executionPlan.getCurrentDeviceMemoryUsage();
            final long sizeAllocated = a.getNumBytesOfSegmentWithHeader() * 3;
            assertEquals(sizeAllocated, currentMemoryUsageInBytes);
        }
    }
}
