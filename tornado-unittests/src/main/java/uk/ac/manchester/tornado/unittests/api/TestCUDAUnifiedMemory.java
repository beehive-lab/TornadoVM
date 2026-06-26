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
 * These tests only run on the CUDA backend with Unified Memory explicitly enabled.
 * They are skipped otherwise (see {@link #requireUnifiedMemoryEnabled()}).
 *
 * <p>
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V -Dtornado.cuda.memory.unified=true \
 *     uk.ac.manchester.tornado.unittests.api.TestCUDAUnifiedMemory
 * </code>
 *
 * <p>
 * To exercise over-subscription beyond the default device-memory accounting cap:
 *
 * <p>
 * <code>
 * tornado-test -V -Dtornado.cuda.memory.unified=true -Dtornado.device.memory=16GB \
 *     uk.ac.manchester.tornado.unittests.api.TestCUDAUnifiedMemory#testLargeUnifiedMemoryBuffer
 * </code>
 */
public class TestCUDAUnifiedMemory extends TornadoTestBase {

    private static final float DELTA = 0.001f;

    /**
     * Skip unless {@code -Dtornado.cuda.memory.unified=true} is set, and restrict to
     * the CUDA backend (Unified Memory is a CUDA-backend-only feature).
     */
    @Before
    public void requireUnifiedMemoryEnabled() {
        Assume.assumeTrue("Skipping: -Dtornado.cuda.memory.unified=true is required", Boolean.getBoolean("tornado.cuda.memory.unified"));
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
     * Verifies that vector addition produces correct results when buffers are
     * allocated through {@code cuMemAllocManaged}.
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
            plan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(3.0f, c.get(i), DELTA);
        }
    }

    /**
     * A {@code WRITE_ONLY}-style output buffer is zero-initialised by
     * {@code cuMemAllocManaged}; this exercises the code path that skips the explicit
     * {@code cuMemsetD8} when Unified Memory is enabled, while still reading back the
     * correct kernel output.
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
            plan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(15, data.get(i));
        }
    }

    /**
     * Allocates buffers larger than a typical GPU L2 cache (~32 MB) to drive the CUDA
     * paging / over-subscription path. The result must still be numerically correct.
     */
    @Test
    public void testLargeUnifiedMemoryBuffer() throws TornadoExecutionPlanException {
        // 32 MB worth of floats per array.
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
            plan.execute();
        }

        assertEquals(2.0f, c.get(0), DELTA);
        assertEquals(2.0f, c.get(size / 2), DELTA);
        assertEquals(2.0f, c.get(size - 1), DELTA);
    }
}
