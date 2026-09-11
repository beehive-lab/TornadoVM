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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression tests for a kernel that reads and writes the same {@link HalfFloatArray}.
 *
 * <p>
 * A half-float read lowers to a backend {@code ReadHalfFloatNode} rather than to a
 * {@code ReadNode}/{@code JavaReadNode}, and the sketch-tier dataflow analysis did not
 * recognise it while it did recognise the matching write ({@code MarkWriteNode}). The
 * parameter was therefore classified {@code WRITE_ONLY}, the runtime skipped the
 * host-to-device copy for it, and the kernel read an uninitialised device buffer: the result
 * was silently all zeros, with no exception and no bailout. The read nodes now implement
 * {@code MarkReadNode}.
 * </p>
 *
 * <p>
 * These tests fail on the buggy classification rather than on code generation, so they are
 * not backend-specific: any backend whose half-float read node is unmarked reproduces them.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestHalfFloatInPlaceUpdate
 * </pre>
 */
public class TestHalfFloatInPlaceUpdate extends TornadoTestBase {

    private static final int SIZE = 64;

    /**
     * Reads and writes the same element of the same half array.
     *
     * <p>
     * The size is a parameter on purpose. Calling {@code inOut.getSize()} reads a field of the
     * array, which the dataflow analysis counts as a read of the parameter and which therefore
     * hides the missing half-float read entirely: the buggy classification comes out
     * {@code READ_WRITE} anyway and the test passes for the wrong reason.
     * </p>
     */
    public static void scaleInPlace(HalfFloatArray inOut, int size, float factor) {
        for (@Parallel int i = 0; i < size; i++) {
            inOut.set(i, new HalfFloat(inOut.get(i).getFloat32() * factor));
        }
    }

    /** The same update from the kernel-parallel API, which is how this was first seen. */
    public static void scaleInPlaceKernelContext(KernelContext context, HalfFloatArray inOut, float factor) {
        int i = context.globalIdx;
        inOut.set(i, new HalfFloat(inOut.get(i).getFloat32() * factor));
    }

    /**
     * Accumulates a float array into a half array. The half array is read and written while a
     * second parameter is read only, so a fix that marked everything read would not pass this.
     */
    public static void accumulateInto(FloatArray addend, HalfFloatArray inOut, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            inOut.set(i, new HalfFloat(inOut.get(i).getFloat32() + addend.get(i)));
        }
    }

    private static HalfFloatArray ramp(int size) {
        HalfFloatArray array = new HalfFloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, new HalfFloat(i + 1.0f));
        }
        return array;
    }

    @Test
    public void testScaleInPlace() throws TornadoExecutionPlanException {
        HalfFloatArray data = ramp(SIZE);

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestHalfFloatInPlaceUpdate::scaleInPlace, data, SIZE, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, 2.0f * (i + 1.0f), data.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testScaleInPlaceWithKernelContext() throws TornadoExecutionPlanException {
        HalfFloatArray data = ramp(SIZE);

        WorkerGrid1D worker = new WorkerGrid1D(SIZE);
        GridScheduler grid = new GridScheduler("s0.t0", worker);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestHalfFloatInPlaceUpdate::scaleInPlaceKernelContext, new KernelContext(), data, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, 2.0f * (i + 1.0f), data.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testAccumulateIntoHalfArray() throws TornadoExecutionPlanException {
        HalfFloatArray data = ramp(SIZE);
        FloatArray addend = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            addend.set(i, 0.5f * i);
        }

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, addend, data) //
                .task("t0", TestHalfFloatInPlaceUpdate::accumulateInto, addend, data, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, (i + 1.0f) + 0.5f * i, data.get(i).getFloat32(), 0.05f);
        }
    }

    /**
     * Two executions of the same plan. The second must see the host values again, which is the
     * part that silently broke: an {@code EVERY_EXECUTION} transfer that the access
     * classification has decided is unnecessary is not re-issued either.
     */
    @Test
    public void testScaleInPlaceTwoExecutions() throws TornadoExecutionPlanException {
        HalfFloatArray data = ramp(SIZE);

        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("t0", TestHalfFloatInPlaceUpdate::scaleInPlace, data, SIZE, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            plan.execute();
        }

        // The second execution doubles what the first left on the host.
        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, 4.0f * (i + 1.0f), data.get(i).getFloat32(), 0.01f);
        }
    }
}
