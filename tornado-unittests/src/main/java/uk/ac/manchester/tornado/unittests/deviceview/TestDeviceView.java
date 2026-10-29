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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Device views: native arrays with no host storage, bound to device memory another library owns.
 *
 * <p>
 * The foreign memory here is allocated with the CUDA driver API in the device's primary context
 * (see {@link CudaMemory}), which is the situation the Spark/RAPIDS adapter is in: cuDF allocates
 * through RMM on the primary context and hands TornadoVM an address it must read in place, with no
 * native-array header in front of it and no copy either way.
 * </p>
 *
 * <p>
 * How to run:
 * {@code tornado-test -V uk.ac.manchester.tornado.unittests.deviceview.TestDeviceView}
 * </p>
 */
public class TestDeviceView extends TornadoTestBase {

    /**
     * One kernel over three arrays of two different layouts: {@code weights} is an ordinary
     * TornadoVM array (header, copied in), while {@code in} and {@code out} are headerless views of
     * foreign memory. The row count is an explicit parameter, so the kernel never needs a length it
     * could only have read from a header that is not there.
     */
    public static void scaleMixed(FloatArray weights, FloatArray in, FloatArray out, int numRows) {
        for (@Parallel int i = 0; i < numRows; i++) {
            out.set(i, in.get(i) * weights.get(i) + 1.0f);
        }
    }

    /** Two headerless views only, to show a kernel needs no headered argument at all. */
    public static void addViews(FloatArray a, FloatArray b, FloatArray out, int numRows) {
        for (@Parallel int i = 0; i < numRows; i++) {
            out.set(i, a.get(i) + b.get(i));
        }
    }

    /** A data-dependent loop per row, over headerless views. */
    public static void loopOverViews(FloatArray in, FloatArray out, int numRows, int iterations) {
        for (@Parallel int i = 0; i < numRows; i++) {
            float value = in.get(i);
            for (int k = 0; k < iterations; k++) {
                value = value * 1.0001f + 0.5f;
            }
            out.set(i, value);
        }
    }

    /** Mixed element widths: an int view indexes a float view. */
    public static void gatherThroughIntView(IntArray indices, FloatArray values, FloatArray out, int numRows) {
        for (@Parallel int i = 0; i < numRows; i++) {
            out.set(i, values.get(indices.get(i)));
        }
    }

    @BeforeEach
    public void requireCuda() {
        super.before();
        assumeTrue(getTornadoRuntime().getDefaultDevice().getTornadoVMBackend() == TornadoVMBackendType.CUDA, "device views are implemented for the CUDA backend");
        assumeTrue(CudaMemory.isAvailable(), "the CUDA driver library is not loadable");
        CudaMemory.enterPrimaryContext();
    }

    /** A view allocates nothing and refuses the host operations it cannot serve. */
    @Test
    public void testViewHasNoHostStorage() {
        FloatArray view = FloatArray.deviceView(1024);
        assertTrue(view.isDeviceView());
        assertEquals(1024, view.getSize());
        assertEquals(1024L * Float.BYTES, view.getNumBytesOfSegment());
        // No header on the device, so both byte counts agree.
        assertEquals(view.getNumBytesOfSegment(), view.getNumBytesOfSegmentWithHeader());
        assertThrows(UnsupportedOperationException.class, view::getSegment);
        assertThrows(UnsupportedOperationException.class, view::getSegmentWithHeader);

        // An ordinary array is untouched by any of this.
        FloatArray normal = new FloatArray(1024);
        assertTrue(!normal.isDeviceView());
        assertEquals(1024, normal.getSize());
        assertEquals(1024L * Float.BYTES + 16, normal.getNumBytesOfSegmentWithHeader());
        normal.set(7, 3.5f);
        assertEquals(3.5f, normal.get(7), 0.0f);
    }

    /** Binding reports the logical length, and rebinding to fewer elements changes it. */
    @Test
    public void testLogicalLengthFollowsTheBinding() {
        long pointer = CudaMemory.allocate(64L * Float.BYTES);
        try {
            FloatArray view = FloatArray.deviceView(64);
            TaskGraph graph = new TaskGraph("lengths").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            plan.bindExternalBuffer(view, pointer, 64L * Float.BYTES);
            assertEquals(64, view.getSize());

            plan.unbindExternalBuffer(view);
            plan.bindExternalBuffer(view, pointer, 16L * Float.BYTES, 10, -1, false);
            assertEquals(10, view.getSize());

            plan.unbindExternalBuffer(view);
            // A binding larger than the view can address is refused rather than silently clamped.
            assertThrows(IllegalArgumentException.class, () -> plan.bindExternalBuffer(view, pointer, 4096L * Float.BYTES));
            // A normal array cannot be bound to a foreign address at all.
            assertThrows(IllegalArgumentException.class, () -> plan.bindExternalBuffer(new FloatArray(8), pointer, 32));
        } finally {
            CudaMemory.free(pointer);
        }
    }

    /**
     * The headline case: one kernel taking a headered array and two headerless views, reading and
     * writing the foreign buffers in place.
     */
    @Test
    public void testMixedHeaderedAndHeaderlessArguments() {
        final int rows = 4096;
        float[] inputValues = new float[rows];
        for (int i = 0; i < rows; i++) {
            inputValues[i] = i * 0.25f;
        }
        FloatArray weights = new FloatArray(rows);
        for (int i = 0; i < rows; i++) {
            weights.set(i, 2.0f);
        }

        long inPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        long outPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        try {
            CudaMemory.copyToDevice(inPointer, inputValues);

            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);

            TaskGraph graph = new TaskGraph("mixed") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights) //
                    .task("scale", TestDeviceView::scaleMixed, weights, in, out, rows) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindExternalBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindExternalBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();

            float[] actual = CudaMemory.copyFloatsToHost(outPointer, rows);
            float[] expected = new float[rows];
            for (int i = 0; i < rows; i++) {
                expected[i] = inputValues[i] * 2.0f + 1.0f;
            }
            assertArrayEquals(expected, actual, DELTA);

            // The foreign input is untouched: no header was written over its first elements.
            float[] inputAfter = CudaMemory.copyFloatsToHost(inPointer, rows);
            assertArrayEquals(inputValues, inputAfter, 0.0f);
        } finally {
            CudaMemory.free(inPointer);
            CudaMemory.free(outPointer);
        }
    }

    /** Rebinding between executions: a fresh pointer and a smaller row count, with no recompilation. */
    @Test
    public void testRebindBetweenExecutions() {
        final int capacity = 2048;
        FloatArray a = FloatArray.deviceView(capacity);
        FloatArray b = FloatArray.deviceView(capacity);
        FloatArray out = FloatArray.deviceView(capacity);

        long[] pointers = new long[6];
        for (int i = 0; i < pointers.length; i++) {
            pointers[i] = CudaMemory.allocate((long) capacity * Float.BYTES);
        }
        try {
            TaskGraph graph = new TaskGraph("rebind").task("add", TestDeviceView::addViews, a, b, out, capacity);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            for (int round = 0; round < 3; round++) {
                int rows = capacity >> round;
                float[] left = new float[rows];
                float[] right = new float[rows];
                for (int i = 0; i < rows; i++) {
                    left[i] = i + round;
                    right[i] = i * 0.5f;
                }
                // A different buffer every round, so a stale mapping would show up as wrong data.
                long leftPointer = pointers[(round * 2) % pointers.length];
                long rightPointer = pointers[(round * 2 + 1) % pointers.length];
                CudaMemory.copyToDevice(leftPointer, left);
                CudaMemory.copyToDevice(rightPointer, right);

                if (round > 0) {
                    plan.unbindExternalBuffer(a);
                    plan.unbindExternalBuffer(b);
                    plan.unbindExternalBuffer(out);
                }
                plan.bindExternalBuffer(a, leftPointer, (long) rows * Float.BYTES, rows, -1, true);
                plan.bindExternalBuffer(b, rightPointer, (long) rows * Float.BYTES, rows, -1, true);
                plan.bindExternalBuffer(out, pointers[4], (long) rows * Float.BYTES, rows, -1, false);
                plan.execute();
                CudaMemory.synchronizeContext();

                float[] actual = CudaMemory.copyFloatsToHost(pointers[4], rows);
                for (int i = 0; i < rows; i++) {
                    assertEquals(left[i] + right[i], actual[i], DELTA, "round " + round + " row " + i);
                }
            }
        } finally {
            for (long pointer : pointers) {
                CudaMemory.free(pointer);
            }
        }
    }

    /** A per-row loop, which is the shape the Spark UDFs this exists for compile to. */
    @Test
    public void testDataDependentLoopOverViews() {
        final int rows = 1024;
        final int iterations = 50;
        float[] inputValues = new float[rows];
        for (int i = 0; i < rows; i++) {
            inputValues[i] = i * 0.001f;
        }
        long inPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        long outPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        try {
            CudaMemory.copyToDevice(inPointer, inputValues);
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("loop").task("loop", TestDeviceView::loopOverViews, in, out, rows, iterations);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindExternalBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindExternalBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();

            float[] actual = CudaMemory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                float expected = inputValues[i];
                for (int k = 0; k < iterations; k++) {
                    expected = expected * 1.0001f + 0.5f;
                }
                assertEquals(expected, actual[i], DELTA);
            }
        } finally {
            CudaMemory.free(inPointer);
            CudaMemory.free(outPointer);
        }
    }

    /** Views of different element widths in one kernel, which is what LIST offsets plus values need. */
    @Test
    public void testIntAndFloatViewsTogether() {
        final int rows = 512;
        int[] indices = new int[rows];
        float[] values = new float[rows];
        for (int i = 0; i < rows; i++) {
            indices[i] = rows - 1 - i;
            values[i] = i * 1.5f;
        }
        long indexPointer = CudaMemory.allocate((long) rows * Integer.BYTES);
        long valuePointer = CudaMemory.allocate((long) rows * Float.BYTES);
        long outPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        try {
            CudaMemory.copyToDevice(indexPointer, indices);
            CudaMemory.copyToDevice(valuePointer, values);
            IntArray indexView = IntArray.deviceView(rows);
            FloatArray valueView = FloatArray.deviceView(rows);
            FloatArray outView = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("gather").task("gather", TestDeviceView::gatherThroughIntView, indexView, valueView, outView, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindExternalBuffer(indexView, indexPointer, (long) rows * Integer.BYTES, rows, -1, true);
            plan.bindExternalBuffer(valueView, valuePointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindExternalBuffer(outView, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();

            float[] actual = CudaMemory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                assertEquals(values[indices[i]], actual[i], DELTA);
            }
        } finally {
            CudaMemory.free(indexPointer);
            CudaMemory.free(valuePointer);
            CudaMemory.free(outPointer);
        }
    }

    /** A view belongs to one plan at a time, so two tasks cannot rebind it under each other. */
    @Test
    public void testAViewCannotBeBoundByTwoPlans() {
        long pointer = CudaMemory.allocate(256L * Float.BYTES);
        try {
            FloatArray view = FloatArray.deviceView(256);
            TaskGraph graph = new TaskGraph("owner").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan first = new TornadoExecutionPlan(graph.snapshot());
            TornadoExecutionPlan second = new TornadoExecutionPlan(graph.snapshot());
            first.bindExternalBuffer(view, pointer, 256L * Float.BYTES);
            assertThrows(IllegalStateException.class, () -> second.bindExternalBuffer(view, pointer, 256L * Float.BYTES));
            assertThrows(IllegalStateException.class, () -> second.unbindExternalBuffer(view));
            first.unbindExternalBuffer(view);
            second.bindExternalBuffer(view, pointer, 256L * Float.BYTES);
            second.unbindExternalBuffer(view);
        } finally {
            CudaMemory.free(pointer);
        }
    }

    /** Executing with a view that was never bound fails instead of launching against address 0. */
    @Test
    public void testUnboundViewIsRefused() {
        FloatArray in = FloatArray.deviceView(32);
        FloatArray out = FloatArray.deviceView(32);
        TaskGraph graph = new TaskGraph("unbound").task("add", TestDeviceView::addViews, in, in, out, 32);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
        assertThrows(RuntimeException.class, plan::execute);
    }

    /** Headered and headerless compilations of the same method do not share a cached kernel. */
    @Test
    public void testHeaderedAndHeaderlessDoNotShareAKernel() {
        final int rows = 256;
        float[] values = new float[rows];
        for (int i = 0; i < rows; i++) {
            values[i] = i;
        }

        // All-headered run.
        FloatArray a = FloatArray.fromArray(values);
        FloatArray b = FloatArray.fromArray(values);
        FloatArray headeredOut = new FloatArray(rows);
        TaskGraph headeredGraph = new TaskGraph("headered") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("add", TestDeviceView::addViews, a, b, headeredOut, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, headeredOut);
        new TornadoExecutionPlan(headeredGraph.snapshot()).execute();
        for (int i = 0; i < rows; i++) {
            assertEquals(values[i] * 2, headeredOut.get(i), DELTA);
        }

        // The same method, now with headerless views. If the headered kernel were reused, the reads
        // would be 16 bytes off and the first four results would be wrong.
        long leftPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        long rightPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        long outPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        try {
            CudaMemory.copyToDevice(leftPointer, values);
            CudaMemory.copyToDevice(rightPointer, values);
            FloatArray viewA = FloatArray.deviceView(rows);
            FloatArray viewB = FloatArray.deviceView(rows);
            FloatArray viewOut = FloatArray.deviceView(rows);
            TaskGraph viewGraph = new TaskGraph("headerless").task("add", TestDeviceView::addViews, viewA, viewB, viewOut, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(viewGraph.snapshot());
            plan.bindExternalBuffer(viewA, leftPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindExternalBuffer(viewB, rightPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindExternalBuffer(viewOut, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();

            float[] actual = CudaMemory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                assertEquals(values[i] * 2, actual[i], DELTA, "row " + i);
            }
        } finally {
            CudaMemory.free(leftPointer);
            CudaMemory.free(rightPointer);
            CudaMemory.free(outPointer);
        }
    }

    /** An ordinary task-graph still behaves exactly as before, alongside all of the above. */
    @Test
    public void testOrdinaryArraysAreUnaffected() {
        final int rows = 1024;
        FloatArray a = new FloatArray(rows);
        FloatArray b = new FloatArray(rows);
        FloatArray out = new FloatArray(rows);
        for (int i = 0; i < rows; i++) {
            a.set(i, i);
            b.set(i, 2 * i);
        }
        TaskGraph graph = new TaskGraph("ordinary") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("add", TestDeviceView::addViews, a, b, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph snapshot = graph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(snapshot)) {
            plan.execute();
            plan.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < rows; i++) {
            assertEquals(3 * i, out.get(i), DELTA);
        }
        assertNotEquals(0, out.get(1), DELTA);
    }
}
