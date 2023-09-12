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
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
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
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph);

        // Execute the plan (default TornadoVM optimization choices)
        TornadoExecutionResult executionResult = plan.execute();

        assertTrue(executionResult.getProfilerResult().getTotalTime() > 0);
        assertTrue(executionResult.getProfilerResult().getTornadoCompilerTime() > 0);
        assertTrue(executionResult.getProfilerResult().getCompileTime() > 0);
        assertTrue(executionResult.getProfilerResult().getDataTransfersTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceReadTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceWriteTime() >= 0);
        // We do not support dispatch timers for the PTX and SPIRV backends
        if (!isBackendPTXOrSPIRV(driverIndex)) {
            assertTrue(executionResult.getProfilerResult().getDataTransferDispatchTime() > 0);
            assertTrue(executionResult.getProfilerResult().getKernelDispatchTime() > 0);
        }
        assertTrue(executionResult.getProfilerResult().getDeviceWriteTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceReadTime() > 0);

        assertEquals(executionResult.getProfilerResult().getDeviceWriteTime() + executionResult.getProfilerResult().getDeviceReadTime(), executionResult.getProfilerResult().getDataTransfersTime());
        assertEquals(executionResult.getProfilerResult().getTornadoCompilerTime() + executionResult.getProfilerResult().getDriverInstallTime(), executionResult.getProfilerResult().getCompileTime());

        // Disable profiler
        plan.withoutProfiler();
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
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Execute the plan (default TornadoVM optimization choices)
        TornadoExecutionResult executionResult = executionPlan.execute();

        assertEquals(executionResult.getProfilerResult().getTotalTime(), 0);
        assertEquals(executionResult.getProfilerResult().getTornadoCompilerTime(), 0);
        assertEquals(executionResult.getProfilerResult().getCompileTime(), 0);
        assertEquals(executionResult.getProfilerResult().getDataTransfersTime(), 0);
        assertEquals(executionResult.getProfilerResult().getDeviceReadTime(), 0);
        assertEquals(executionResult.getProfilerResult().getDeviceWriteTime(), 0);
        assertEquals(executionResult.getProfilerResult().getDataTransferDispatchTime(), 0);
        assertEquals(executionResult.getProfilerResult().getKernelDispatchTime(), 0);
        assertEquals(executionResult.getProfilerResult().getDeviceKernelTime(), 0);
        assertEquals(executionResult.getProfilerResult().getDeviceKernelTime(), 0);
    }

    @Test
    public void testProfilerFromExecutionPlan() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withProfiler(ProfilerMode.CONSOLE);

        // Execute the plan (default TornadoVM optimization choices)
        TornadoExecutionResult executionResult = executionPlan.execute();

        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();

        assertTrue(executionResult.getProfilerResult().getTotalTime() > 0);
        assertTrue(executionResult.getProfilerResult().getTornadoCompilerTime() > 0);
        assertTrue(executionResult.getProfilerResult().getCompileTime() > 0);
        assertTrue(executionResult.getProfilerResult().getDataTransfersTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceReadTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceWriteTime() >= 0);
        // We do not support dispatch timers for the PTX and SPIRV backends
        if (!isBackendPTXOrSPIRV(driverIndex)) {
            assertTrue(executionResult.getProfilerResult().getDataTransferDispatchTime() > 0);
            assertTrue(executionResult.getProfilerResult().getKernelDispatchTime() > 0);
        }
        assertTrue(executionResult.getProfilerResult().getDeviceWriteTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceReadTime() > 0);

        assertEquals(executionResult.getProfilerResult().getDeviceWriteTime() + executionResult.getProfilerResult().getDeviceReadTime(), executionResult.getProfilerResult().getDataTransfersTime());
        assertEquals(executionResult.getProfilerResult().getTornadoCompilerTime() + executionResult.getProfilerResult().getDriverInstallTime(), executionResult.getProfilerResult().getCompileTime());

    }

    @Test
    public void testProfilerOnAndOff() {
        int numElements = 16;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withProfiler(ProfilerMode.SILENT);

        // Execute the plan (default TornadoVM optimization choices)
        TornadoExecutionResult executionResult = executionPlan.execute();

        int driverIndex = TornadoRuntime.getTornadoRuntime().getDefaultDevice().getDriverIndex();

        assertTrue(executionResult.getProfilerResult().getTotalTime() > 0);
        assertTrue(executionResult.getProfilerResult().getTornadoCompilerTime() > 0);
        assertTrue(executionResult.getProfilerResult().getCompileTime() > 0);
        assertTrue(executionResult.getProfilerResult().getDataTransfersTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceReadTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceWriteTime() >= 0);
        // We do not support dispatch timers for the PTX and SPIRV backends
        if (!isBackendPTXOrSPIRV(driverIndex)) {
            assertTrue(executionResult.getProfilerResult().getDataTransferDispatchTime() > 0);
            assertTrue(executionResult.getProfilerResult().getKernelDispatchTime() > 0);
        }
        assertTrue(executionResult.getProfilerResult().getDeviceWriteTime() >= 0);
        assertTrue(executionResult.getProfilerResult().getDeviceReadTime() > 0);

        assertEquals(executionResult.getProfilerResult().getDeviceWriteTime() + executionResult.getProfilerResult().getDeviceReadTime(), executionResult.getProfilerResult().getDataTransfersTime());
        assertEquals(executionResult.getProfilerResult().getTornadoCompilerTime() + executionResult.getProfilerResult().getDriverInstallTime(), executionResult.getProfilerResult().getCompileTime());

        executionPlan.withoutProfiler().execute();

    }

    private static void reduction(float[] input, @Reduce float[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    @Test
    public void testProfilerReduction() {

        final int SIZE = 1024;
        float[] inputArray = new float[SIZE];
        float[] outputArray = new float[1];

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg);
        executionPlan.withProfiler(ProfilerMode.CONSOLE);

        TornadoExecutionResult executionResult = executionPlan.execute();
        long kernelTime = executionResult.getProfilerResult().getDeviceKernelTime();
        assertTrue(kernelTime > 0);
    }

    @Test
    public void testProfilerReductionOnAndOff() {

        final int SIZE = 1024;
        float[] inputArray = new float[SIZE];
        float[] outputArray = new float[1];

        Random r = new Random();
        IntStream.range(0, SIZE).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg);
        executionPlan.withProfiler(ProfilerMode.CONSOLE);

        TornadoExecutionResult executionResult = executionPlan.execute();
        long kernelTime = executionResult.getProfilerResult().getDeviceKernelTime();
        assertTrue(kernelTime > 0);

        executionPlan.withoutProfiler();

        executionPlan.execute();
        executionPlan.execute();
    }

}
