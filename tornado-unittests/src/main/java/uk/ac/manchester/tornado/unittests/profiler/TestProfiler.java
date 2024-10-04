/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.profiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.TornadoProfilerResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.profiler.TestProfiler
 * </code>
 */
public class TestProfiler extends TornadoTestBase {

    private static void reduction(float[] input, @Reduce float[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    private boolean isBackendPTXOrSPIRV(int driverIndex) {
        TornadoVMBackendType type = TornadoRuntimeProvider.getTornadoRuntime().getBackend(driverIndex).getBackendType();
        return switch (type) {
            case PTX, SPIRV -> true;
            default -> false;
        };
    }

    @Test
    public void testProfilerEnabled() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        // testProfilerDisabled might execute first. We must make sure that the code
        // cache is reset.
        // Otherwise, we get 0 compile time.
        TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().clean();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)//
                .task("t0", TestHello::add, a, b, c)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        int driverIndex = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex();

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Execute the plan (default TornadoVM optimization choices)
            TornadoExecutionResult executionResult = plan.withProfiler(ProfilerMode.SILENT).execute();

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

            assertEquals(executionResult.getProfilerResult().getDeviceWriteTime() + executionResult.getProfilerResult().getDeviceReadTime(), executionResult.getProfilerResult()
                    .getDataTransfersTime());
            assertEquals(executionResult.getProfilerResult().getTornadoCompilerTime() + executionResult.getProfilerResult().getDriverInstallTime(), executionResult.getProfilerResult()
                    .getCompileTime());

            // Disable profiler
            plan.withoutProfiler();
        }
    }

    @Test
    public void testProfilerDisabled() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Execute the plan (default TornadoVM optimization choices)
            TornadoExecutionResult executionResult = executionPlan.withoutProfiler().execute();

            assertEquals(0, executionResult.getProfilerResult().getTotalTime());
            assertEquals(0, executionResult.getProfilerResult().getTornadoCompilerTime());
            assertEquals(0, executionResult.getProfilerResult().getCompileTime());
            assertEquals(0, executionResult.getProfilerResult().getDataTransfersTime());
            assertEquals(0, executionResult.getProfilerResult().getDeviceReadTime());
            assertEquals(0, executionResult.getProfilerResult().getDeviceWriteTime());
            assertEquals(0, executionResult.getProfilerResult().getDataTransferDispatchTime());
            assertEquals(0, executionResult.getProfilerResult().getKernelDispatchTime());
            assertEquals(0, executionResult.getProfilerResult().getDeviceKernelTime());
            assertEquals(0, executionResult.getProfilerResult().getDeviceKernelTime());
        }
    }

    @Test
    public void testProfilerFromExecutionPlan() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            executionPlan.withProfiler(ProfilerMode.CONSOLE);

            // Execute the plan (default TornadoVM optimization choices)
            TornadoExecutionResult executionResult = executionPlan.execute();

            int driverIndex = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex();

            TornadoProfilerResult profilerResult = executionResult.getProfilerResult();

            assertTrue(profilerResult.getTotalTime() > 0);
            assertTrue(profilerResult.getTornadoCompilerTime() > 0);
            assertTrue(profilerResult.getCompileTime() > 0);
            assertTrue(profilerResult.getDataTransfersTime() >= 0);
            assertTrue(profilerResult.getDeviceReadTime() >= 0);
            assertTrue(profilerResult.getDeviceWriteTime() >= 0);
            // We do not support dispatch timers for the PTX and SPIRV backends
            if (!isBackendPTXOrSPIRV(driverIndex)) {
                assertTrue(profilerResult.getDataTransferDispatchTime() > 0);
                assertTrue(profilerResult.getKernelDispatchTime() > 0);
            }
            assertTrue(profilerResult.getDeviceWriteTime() >= 0);
            assertTrue(profilerResult.getDeviceReadTime() > 0);

            assertEquals(profilerResult.getDeviceWriteTime() + profilerResult.getDeviceReadTime(), profilerResult.getDataTransfersTime());
            assertEquals(profilerResult.getTornadoCompilerTime() + profilerResult.getDriverInstallTime(), profilerResult.getCompileTime());
        }
    }

    @Test
    public void testProfilerOnAndOff() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        // Build ImmutableTaskGraph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Build executionPlan
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            executionPlan.withProfiler(ProfilerMode.SILENT);

            // Execute the plan (default TornadoVM optimization choices)
            TornadoExecutionResult executionResult = executionPlan.execute();

            int driverIndex = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex();

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

            assertEquals(executionResult.getProfilerResult().getDeviceWriteTime() + executionResult.getProfilerResult().getDeviceReadTime(), executionResult.getProfilerResult()
                    .getDataTransfersTime());
            assertEquals(executionResult.getProfilerResult().getTornadoCompilerTime() + executionResult.getProfilerResult().getDriverInstallTime(), executionResult.getProfilerResult()
                    .getCompileTime());

            executionPlan.withoutProfiler().execute();
        }

    }

    @Test
    public void testProfilerReduction() throws TornadoExecutionPlanException {

        final int size = 1024;
        float[] inputArray = new float[size];
        float[] outputArray = new float[1];

        Random r = new Random();
        IntStream.range(0, size).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg)) {
            executionPlan.withProfiler(ProfilerMode.CONSOLE);
            TornadoExecutionResult executionResult = executionPlan.execute();
            long kernelTime = executionResult.getProfilerResult().getDeviceKernelTime();
            assertTrue(kernelTime > 0);
        }
    }

    @Test
    public void testProfilerReductionOnAndOff() throws TornadoExecutionPlanException {

        final int size = 1024;
        float[] inputArray = new float[size];
        float[] outputArray = new float[1];

        Random r = new Random();
        IntStream.range(0, size).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg)) {
            executionPlan.withProfiler(ProfilerMode.CONSOLE);
            TornadoExecutionResult executionResult = executionPlan.execute();
            long kernelTime = executionResult.getProfilerResult().getDeviceKernelTime();
            assertTrue(kernelTime > 0);

            executionPlan.withoutProfiler();

            executionPlan.execute();
            executionPlan.execute();
        }
    }

    @Test
    public void testProfilerReductionOffAndOn() throws TornadoExecutionPlanException {

        final int size = 1024;
        float[] inputArray = new float[size];
        float[] outputArray = new float[1];

        Random r = new Random(71);
        IntStream.range(0, size).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        ImmutableTaskGraph itg = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(itg)) {

            TornadoExecutionResult executionResult = executionPlan.execute();

            long kernelTime = executionResult.getProfilerResult().getDeviceKernelTime();
            assertEquals(0, kernelTime);

            executionResult = executionPlan.withProfiler(ProfilerMode.SILENT) //
                    .execute();

            kernelTime = executionResult.getProfilerResult().getDeviceKernelTime();
            assertTrue(kernelTime > 0);
        }
    }

    @Test
    public void testKernelOnAndOff() throws TornadoExecutionPlanException {

        final int size = 1024;
        float[] inputArray = new float[size];
        float[] outputArray = new float[1];

        Random r = new Random(71);
        IntStream.range(0, size).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Enable print kernel
            executionPlan.withPrintKernel().execute();

            // disable print kernel
            executionPlan.withoutPrintKernel().execute();
        }
    }

    @Test
    public void testThreadInfoOnAndOff() throws TornadoExecutionPlanException {

        final int size = 1024;
        float[] inputArray = new float[size];
        float[] outputArray = new float[1];

        Random r = new Random(71);
        IntStream.range(0, size).forEach(i -> inputArray[i] = r.nextFloat());

        TaskGraph taskGraph = new TaskGraph("compute");
        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, inputArray) //
                .task("reduce", TestProfiler::reduction, inputArray, outputArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputArray);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            // Enable print kernel
            executionPlan.withThreadInfo().execute();

            // disable print kernel
            executionPlan.withoutThreadInfo().execute();
        }
    }
}
