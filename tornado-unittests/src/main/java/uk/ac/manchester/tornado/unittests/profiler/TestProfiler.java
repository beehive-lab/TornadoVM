/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.profiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutor;
import uk.ac.manchester.tornado.api.TornadoExecutorPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.profiler.TestProfiler
 * </code>
 */
public class TestProfiler extends TornadoTestBase {

    private boolean isBackendPTXOrSPIRV(int driverIndex) {
        TornadoVMBackendType type = TornadoRuntime.getTornadoRuntime().getDriver(driverIndex).getBackendType();
        switch (type) {
            case PTX:
            case SPIRV:
                return true;
            case OPENCL:
                return false;
            default:
                return false;
        }
    }

    @Test
    public void testProfilerEnabled() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // testProfilerDisabled might execute first. We must make sure that the code
        // cache is reset.
        // Otherwise, we get 0 compile time.
        TornadoRuntime.getTornadoRuntime().getDefaultDevice().reset();

        // Enable profiler
        System.setProperty("tornado.profiler", "True");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestHello::add, a, b, c)//
                .transferToHost(c);

        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build Executor
        TornadoExecutorPlan executorPlan = new TornadoExecutor(immutableTaskGraph).build();

        // Execute the plan (default TornadoVM optimization choices)
        executorPlan.execute();

        assertTrue(executorPlan.getTotalTime() > 0);
        assertTrue(executorPlan.getTornadoCompilerTime() > 0);
        assertTrue(executorPlan.getCompileTime() > 0);
        assertTrue(executorPlan.getDataTransfersTime() >= 0);
        assertTrue(executorPlan.getReadTime() >= 0);
        assertTrue(executorPlan.getWriteTime() >= 0);
        // We do not support dispatch timers for the PTX and SPIRV backends
        if (!isBackendPTXOrSPIRV(driverIndex)) {
            assertTrue(executorPlan.getDataTransferDispatchTime() > 0);
            assertTrue(executorPlan.getKernelDispatchTime() > 0);
        }
        assertTrue(executorPlan.getDeviceReadTime() >= 0);
        assertTrue(executorPlan.getDeviceWriteTime() >= 0);
        assertTrue(executorPlan.getDeviceKernelTime() > 0);

        assertEquals(executorPlan.getWriteTime() + executorPlan.getReadTime(), executorPlan.getDataTransfersTime());
        assertEquals(executorPlan.getTornadoCompilerTime() + executorPlan.getDriverInstallTime(), executorPlan.getCompileTime());

        // Disable profiler
        System.setProperty("tornado.profiler", "False");
    }

    @Test
    public void testProfilerDisabled() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        // Disable profiler
        System.setProperty("tornado.profiler", "False");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build Executor
        TornadoExecutorPlan executor = new TornadoExecutor(immutableTaskGraph).build();

        // Execute the plan (default TornadoVM optimization choices)
        executor.execute();

        assertEquals(executor.getTotalTime(), 0);
        assertEquals(executor.getTornadoCompilerTime(), 0);
        assertEquals(executor.getCompileTime(), 0);
        assertEquals(executor.getDataTransfersTime(), 0);
        assertEquals(executor.getReadTime(), 0);
        assertEquals(executor.getWriteTime(), 0);
        assertEquals(executor.getDataTransferDispatchTime(), 0);
        assertEquals(executor.getKernelDispatchTime(), 0);
        assertEquals(executor.getDeviceReadTime(), 0);
        assertEquals(executor.getDeviceWriteTime(), 0);
        assertEquals(executor.getDeviceKernelTime(), 0);
        assertEquals(executor.getDeviceKernelTime(), 0);
    }
}
