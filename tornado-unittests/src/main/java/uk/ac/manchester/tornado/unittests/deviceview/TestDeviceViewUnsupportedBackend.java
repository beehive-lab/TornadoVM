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
package uk.ac.manchester.tornado.unittests.deviceview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceViewNotSupported;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The other half of {@link TestDeviceView}: what a backend that has no device views does when one
 * is offered to it.
 *
 * <p>
 * Binding borrows a raw device address, and OpenCL and Metal pass a buffer handle rather than an
 * address, so there is nothing for one to be bound to. The refusal has to come from the bind call,
 * while the caller is still in a position to do something else, rather than from a kernel reading
 * an address its device cannot use. These run where {@link TestDeviceView} is skipped and are
 * skipped where it runs, so between them one of the two covers whatever backend is installed.
 * </p>
 */
public class TestDeviceViewUnsupportedBackend extends TornadoTestBase {

    public static void scale(FloatArray in, FloatArray out, int numRows) {
        for (@Parallel int i = 0; i < numRows; i++) {
            out.set(i, in.get(i) * 2.0f);
        }
    }

    private TornadoVMBackendType backend;

    @BeforeEach
    public void requireNonCudaBackend() {
        super.before();
        backend = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        assumeTrue(backend != TornadoVMBackendType.CUDA, "this is the CUDA backend, where device views are supported");
    }

    /** The view itself is a plain object: creating one touches no device, so it is allowed. */
    @Test
    public void testAViewCanStillBeCreated() {
        FloatArray view = FloatArray.cudaDeviceView(1024);
        assertTrue(view.isCUDADeviceView());
        assertEquals(1024, view.getSize());
    }

    /** Binding is where a device first comes into it, and where the refusal belongs. */
    @Test
    public void testBindingIsRefusedWithTheBackendNamed() {
        final int rows = 64;
        FloatArray in = FloatArray.cudaDeviceView(rows);
        FloatArray out = FloatArray.cudaDeviceView(rows);
        TaskGraph graph = new TaskGraph("unsupported") //
                .task("scale", TestDeviceViewUnsupportedBackend::scale, in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

        // The address is deliberately not a real allocation: the backend must be rejected before
        // anything looks at what was passed, which is the whole point of refusing at bind time.
        TornadoDeviceViewNotSupported refusal = assertThrows(TornadoDeviceViewNotSupported.class,
                () -> plan.bindCUDADeviceBuffer(in, 0xDEADBEEFL, (long) rows * Float.BYTES, rows, -1, true));
        assertTrue(refusal.getMessage().contains(backend.toString()), "the refusal should name the backend that cannot do this: " + refusal.getMessage());
    }

    /** The short overload refuses for the same reason, not only the one that states every argument. */
    @Test
    public void testTheShortBindOverloadIsRefusedToo() {
        final int rows = 64;
        FloatArray view = FloatArray.cudaDeviceView(rows);
        TaskGraph graph = new TaskGraph("unsupported-short") //
                .task("scale", TestDeviceViewUnsupportedBackend::scale, view, view, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, view);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

        assertThrows(TornadoDeviceViewNotSupported.class, () -> plan.bindCUDADeviceBuffer(view, 0xDEADBEEFL, (long) rows * Float.BYTES));
    }

    /** An ordinary array is untouched by any of this: the backend keeps working as it always did. */
    @Test
    public void testOrdinaryArraysStillRun() {
        final int rows = 256;
        FloatArray in = new FloatArray(rows);
        FloatArray out = new FloatArray(rows);
        for (int i = 0; i < rows; i++) {
            in.set(i, i * 0.5f);
        }
        TaskGraph graph = new TaskGraph("ordinary") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("scale", TestDeviceViewUnsupportedBackend::scale, in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        } catch (Exception e) {
            throw new AssertionError("an ordinary task graph must be unaffected on " + backend, e);
        }
        for (int i = 0; i < rows; i++) {
            assertEquals(i * 1.0f, out.get(i), 0.001f);
        }
    }
}
