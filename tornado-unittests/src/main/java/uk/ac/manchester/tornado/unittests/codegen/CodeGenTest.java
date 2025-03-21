/*
 * Copyright (c) 2020, 2022 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.codegen;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.codegen.CodeGenTest
 * </code>
 * </p>
 */
public class CodeGenTest extends TornadoTestBase {

    public static void cascadeKernel(IntArray grayIntegralImage, int imageWidth, int imageHeight, IntArray resultsXY) {
        for (@Parallel int y = 0; y < imageHeight; y++) {
            for (@Parallel int x = 0; x < imageWidth; x++) {
                int gradient = grayIntegralImage.get((y * imageWidth) + x);
            }
        }
    }

    public static void badCascadeKernel2() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; (stillLooksLikeAFace || (stage < 100)); stage++) {
                for (int t = 0; t < id; t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    public static void badCascadeKernel3() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; (stillLooksLikeAFace || (stage < 100)); stage++) {
                for (int t = 0; stillLooksLikeAFace && (t < id); t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    public static void badCascadeKernel4() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; stillLooksLikeAFace && (stage < id); stage++) {
                for (int t = 0; t < id; t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    /*
     * The following test is not intended to execute in parallel. This test shows
     * more complex control flow, in which there is an exit block followed by a
     * merge to represent the break in the first if-condition.
     *
     */
    private static void breakStatement(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            if (a.get(i) == 5) {
                break;
            }
            a.set(i, a.get(i) + 5);
        }
        a.set(0, 0);
    }

    public static void testLocalMemoryAllocation(KernelContext context, int localWorkGroupSize) {
        int threadId = context.localIdx;  // Thread ID within work group
        int blockDim = context.localGroupSizeX;  // Work group size

        // Allocate local memory
        float[] localArray = context.allocateFloatLocalArray(localWorkGroupSize);

        // Initialize local memory with threadId (for testing purposes)
        localArray[threadId] = threadId;

        // Synchronize threads
        context.localBarrier();

        // Simple operation to validate memory access
        if (threadId == 0) {
            float sum = 0.0f;
            for (int i = 0; i < blockDim; i++) {
                sum += localArray[i];
            }
        }

        // Synchronize again before exiting
        context.localBarrier();
    }

    @Test
    public void test01() throws TornadoExecutionPlanException {

        TaskGraph taskGraph = new TaskGraph("foo");

        int imageWidth = 512;
        int imageHeight = 512;
        IntArray grayIntegralImage = new IntArray(imageHeight * imageWidth);
        IntArray resultsXY = new IntArray(imageHeight * imageWidth);

        IntStream.range(0, imageHeight * imageHeight).forEach(x -> grayIntegralImage.set(x, x));

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, grayIntegralImage) //
                .task("bar", CodeGenTest::cascadeKernel, grayIntegralImage, imageWidth, imageHeight, resultsXY) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultsXY);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

    private boolean isRunningOnCPU() {
        TornadoDevice device = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(0);
        return device.getDeviceType() == TornadoDeviceType.CPU;
    }

    @Test
    public void test02() throws TornadoExecutionPlanException {
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGenTest::badCascadeKernel2);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withWarmUp();
        }
    }

    @Test
    @Ignore
    public void test03() throws TornadoExecutionPlanException {
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGenTest::badCascadeKernel3);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withWarmUp();
        }
    }

    @Test
    public void test04() throws TornadoExecutionPlanException {
        assertNotBackendOptimization(TornadoVMBackendType.SPIRV);
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGenTest::badCascadeKernel4);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withWarmUp();
        }
    }

    @Test
    public void test05() throws TornadoExecutionPlanException {
        final int size = 8192;
        IntArray a = new IntArray(size);
        a.init(10);
        a.set(12, 5);
        IntArray serial = new IntArray(size);
        serial.init(10);
        serial.set(12, 5);

        breakStatement(serial);

        TaskGraph taskGraph = new TaskGraph("break") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("task", CodeGenTest::breakStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(serial.get(i), a.get(i));
        }
    }

    @Test
    public void test06() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        int localWorkGroupSize = 256;

        TaskGraph taskGraph = new TaskGraph("localMemoryAllocation") //
                .task("task", CodeGenTest::testLocalMemoryAllocation, context, localWorkGroupSize);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
    }

}
