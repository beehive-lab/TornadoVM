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

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests for CUDA Managed (Unified) Memory allocation in the CUDA backend.
 *
 * <p>
 * Most tests use the per-plan {@link TornadoExecutionPlan#withCudaUM()} API, so they
 * run on the CUDA backend without any extra flag. The global-flag path
 * ({@code -Dtornado.cuda.memory.unified=true}) is covered by a separate test that is
 * skipped unless the flag is set.
 *
 * <p>
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestCUDAUnifiedMemory
 * </code>
 */
public class TestCUDAUnifiedMemory extends TornadoTestBase {

    private static final float DELTA = 0.001f;

    /**
     * Unified Memory is a CUDA-backend-only feature; skip on every other backend.
     */
    @Before
    public void onlyCuda() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    public static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void integerAccumulate(IntArray data, int value) {
        for (@Parallel int i = 0; i < data.getSize(); i++) {
            data.set(i, data.get(i) + value);
        }
    }

    /**
     * Vector addition with Unified Memory selected per-plan via {@code withCudaUM()}.
     */
    @Test
    public void testVectorAddWithUnifiedMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        a.init(1.0f);
        b.init(2.0f);

        TaskGraph taskGraph = new TaskGraph("um_add") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestCUDAUnifiedMemory::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCudaUM().execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(3.0f, c.get(i), DELTA);
        }
    }

    /**
     * A WRITE_ONLY-style output buffer is zero-initialised by {@code cuMemAllocManaged};
     * this exercises the path that skips the explicit {@code cuMemsetD8} when Unified
     * Memory is enabled, while still reading back the correct kernel output.
     */
    @Test
    public void testWriteOnlyBufferZeroInitialisedByUM() throws TornadoExecutionPlanException {
        final int size = 512;
        IntArray data = new IntArray(size);
        data.init(5);

        TaskGraph taskGraph = new TaskGraph("um_writeonly") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("accumulate", TestCUDAUnifiedMemory::integerAccumulate, data, 10) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCudaUM().execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(15, data.get(i));
        }
    }

    /**
     * Managed and default allocation must produce identical results for the same plan.
     */
    @Test
    public void testUnifiedMemoryMatchesDefault() throws TornadoExecutionPlanException {
        final int size = 2048;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray cDefault = new FloatArray(size);
        FloatArray cUM = new FloatArray(size);
        a.init(3.0f);
        b.init(4.0f);

        TaskGraph defaultGraph = new TaskGraph("def") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestCUDAUnifiedMemory::vectorAdd, a, b, cDefault) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cDefault);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(defaultGraph.snapshot())) {
            plan.execute();
        }

        TaskGraph umGraph = new TaskGraph("um") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestCUDAUnifiedMemory::vectorAdd, a, b, cUM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cUM);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(umGraph.snapshot())) {
            plan.withCudaUM().execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(cDefault.get(i), cUM.get(i), DELTA);
        }
    }

    /**
     * Allocates buffers larger than a typical GPU L2 cache (~32 MB) to drive the CUDA
     * paging path. The result must still be numerically correct.
     */
    @Test
    public void testLargeUnifiedMemoryBuffer() throws TornadoExecutionPlanException {
        final int size = 32 * 1024 * 1024 / Float.BYTES;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        a.init(1.5f);
        b.init(0.5f);

        TaskGraph taskGraph = new TaskGraph("um_large") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestCUDAUnifiedMemory::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCudaUM().execute();
        }

        assertEquals(2.0f, c.get(0), DELTA);
        assertEquals(2.0f, c.get(size / 2), DELTA);
        assertEquals(2.0f, c.get(size - 1), DELTA);
    }

    /**
     * Covers the global-flag path ({@code -Dtornado.cuda.memory.unified=true}); skipped
     * unless that flag is set.
     */
    @Test
    public void testGlobalFlagUnifiedMemory() throws TornadoExecutionPlanException {
        Assume.assumeTrue("Skipping: -Dtornado.cuda.memory.unified=true is required", Boolean.getBoolean("tornado.cuda.memory.unified"));
        final int size = 1024;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        a.init(2.0f);
        b.init(5.0f);

        TaskGraph taskGraph = new TaskGraph("um_global") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("vectorAdd", TestCUDAUnifiedMemory::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(7.0f, c.get(i), DELTA);
        }
    }
}
