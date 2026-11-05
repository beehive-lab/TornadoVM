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
import uk.ac.manchester.tornado.api.KernelContext;
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
        assumeTrue(CudaMemory.isAvailable(), () -> "the CUDA driver library is not loadable: " + CudaMemory.unavailableCause());
        CudaMemory.enterPrimaryContext();
    }

    /** A view allocates nothing and refuses the host operations it cannot serve. */
    @Test
    public void testViewHasNoHostStorage() {
        FloatArray view = FloatArray.cudaDeviceView(1024);
        assertTrue(view.isCUDADeviceView());
        assertEquals(1024, view.getSize());
        assertEquals(1024L * Float.BYTES, view.getNumBytesOfSegment());
        // No header on the device, so both byte counts agree.
        assertEquals(view.getNumBytesOfSegment(), view.getNumBytesOfSegmentWithHeader());
        assertThrows(UnsupportedOperationException.class, view::getSegment);
        assertThrows(UnsupportedOperationException.class, view::getSegmentWithHeader);

        // An ordinary array is untouched by any of this.
        FloatArray normal = new FloatArray(1024);
        assertTrue(!normal.isCUDADeviceView());
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
            FloatArray view = FloatArray.cudaDeviceView(64);
            TaskGraph graph = new TaskGraph("lengths").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            plan.bindCUDADeviceBuffer(view, pointer, 64L * Float.BYTES);
            assertEquals(64, view.getSize());

            plan.unbindCUDADeviceBuffer(view);
            plan.bindCUDADeviceBuffer(view, pointer, 16L * Float.BYTES, 10, -1, false);
            assertEquals(10, view.getSize());

            plan.unbindCUDADeviceBuffer(view);
            // A binding larger than the view can address is refused rather than silently clamped.
            assertThrows(IllegalArgumentException.class, () -> plan.bindCUDADeviceBuffer(view, pointer, 4096L * Float.BYTES));
            // A normal array cannot be bound to a foreign address at all.
            assertThrows(IllegalArgumentException.class, () -> plan.bindCUDADeviceBuffer(new FloatArray(8), pointer, 32));
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

            FloatArray in = FloatArray.cudaDeviceView(rows);
            FloatArray out = FloatArray.cudaDeviceView(rows);

            TaskGraph graph = new TaskGraph("mixed") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights) //
                    .task("scale", TestDeviceView::scaleMixed, weights, in, out, rows) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindCUDADeviceBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindCUDADeviceBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
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
        FloatArray a = FloatArray.cudaDeviceView(capacity);
        FloatArray b = FloatArray.cudaDeviceView(capacity);
        FloatArray out = FloatArray.cudaDeviceView(capacity);

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
                    plan.unbindCUDADeviceBuffer(a);
                    plan.unbindCUDADeviceBuffer(b);
                    plan.unbindCUDADeviceBuffer(out);
                }
                plan.bindCUDADeviceBuffer(a, leftPointer, (long) rows * Float.BYTES, rows, -1, true);
                plan.bindCUDADeviceBuffer(b, rightPointer, (long) rows * Float.BYTES, rows, -1, true);
                plan.bindCUDADeviceBuffer(out, pointers[4], (long) rows * Float.BYTES, rows, -1, false);
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
            FloatArray in = FloatArray.cudaDeviceView(rows);
            FloatArray out = FloatArray.cudaDeviceView(rows);
            TaskGraph graph = new TaskGraph("loop").task("loop", TestDeviceView::loopOverViews, in, out, rows, iterations);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindCUDADeviceBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindCUDADeviceBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
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
            IntArray indexView = IntArray.cudaDeviceView(rows);
            FloatArray valueView = FloatArray.cudaDeviceView(rows);
            FloatArray outView = FloatArray.cudaDeviceView(rows);
            TaskGraph graph = new TaskGraph("gather").task("gather", TestDeviceView::gatherThroughIntView, indexView, valueView, outView, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindCUDADeviceBuffer(indexView, indexPointer, (long) rows * Integer.BYTES, rows, -1, true);
            plan.bindCUDADeviceBuffer(valueView, valuePointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindCUDADeviceBuffer(outView, outPointer, (long) rows * Float.BYTES, rows, -1, false);
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
            FloatArray view = FloatArray.cudaDeviceView(256);
            TaskGraph graph = new TaskGraph("owner").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan first = new TornadoExecutionPlan(graph.snapshot());
            TornadoExecutionPlan second = new TornadoExecutionPlan(graph.snapshot());
            first.bindCUDADeviceBuffer(view, pointer, 256L * Float.BYTES);
            assertThrows(IllegalStateException.class, () -> second.bindCUDADeviceBuffer(view, pointer, 256L * Float.BYTES));
            assertThrows(IllegalStateException.class, () -> second.unbindCUDADeviceBuffer(view));
            first.unbindCUDADeviceBuffer(view);
            second.bindCUDADeviceBuffer(view, pointer, 256L * Float.BYTES);
            second.unbindCUDADeviceBuffer(view);
        } finally {
            CudaMemory.free(pointer);
        }
    }

    /** Executing with a view that was never bound fails instead of launching against address 0. */
    @Test
    public void testUnboundViewIsRefused() {
        FloatArray in = FloatArray.cudaDeviceView(32);
        FloatArray out = FloatArray.cudaDeviceView(32);
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
            FloatArray viewA = FloatArray.cudaDeviceView(rows);
            FloatArray viewB = FloatArray.cudaDeviceView(rows);
            FloatArray viewOut = FloatArray.cudaDeviceView(rows);
            TaskGraph viewGraph = new TaskGraph("headerless").task("add", TestDeviceView::addViews, viewA, viewB, viewOut, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(viewGraph.snapshot());
            plan.bindCUDADeviceBuffer(viewA, leftPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindCUDADeviceBuffer(viewB, rightPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindCUDADeviceBuffer(viewOut, outPointer, (long) rows * Float.BYTES, rows, -1, false);
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

    /**
     * A kernel whose first parameter is a {@link KernelContext} it never uses. The ABI drops that
     * parameter, which used to renumber the arrays behind it.
     */
    public static void copyAfterUnusedContext(KernelContext unused, FloatArray in, FloatArray out, int numRows) {
        for (@Parallel int i = 0; i < numRows; i++) {
            out.set(i, in.get(i));
        }
    }

    /**
     * An unused context argument must not move the arrays behind it.
     *
     * <p>
     * The facts that say which arguments are headerless are positions in the parameter list as the
     * compiler receives it, and ABI lowering removes the unused context from that same list while it
     * is reading those positions. The first headerless array then answered to the position of the
     * one before it and was read a header's width -- sixteen bytes, four floats -- into the buffer.
     * The input here is eight floats and only the first four are bound, so a shifted read lands on
     * [50,60,70,80] while staying inside a real allocation.
     * </p>
     */
    @Test
    public void testUnusedKernelContextDoesNotShiftHeaderlessArguments() {
        float[] values = { 10, 20, 30, 40, 50, 60, 70, 80 };
        long inPointer = CudaMemory.allocate((long) values.length * Float.BYTES);
        long outPointer = CudaMemory.allocate(4L * Float.BYTES);
        try {
            CudaMemory.copyToDevice(inPointer, values);
            CudaMemory.copyToDevice(outPointer, new float[] { -99, -99, -99, -99 });

            FloatArray in = FloatArray.cudaDeviceView(4);
            FloatArray out = FloatArray.cudaDeviceView(4);
            TaskGraph graph = new TaskGraph("unused-context") //
                    .task("copy", TestDeviceView::copyAfterUnusedContext, new KernelContext(), in, out, 4);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindCUDADeviceBuffer(in, inPointer, 4L * Float.BYTES, 4, -1, true);
            plan.bindCUDADeviceBuffer(out, outPointer, 4L * Float.BYTES, 4, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();

            assertArrayEquals(new float[] { 10, 20, 30, 40 }, CudaMemory.copyFloatsToHost(outPointer, 4), 0.0f,
                    "the unused context argument shifted which array was treated as headerless");
        } finally {
            CudaMemory.free(inPointer);
            CudaMemory.free(outPointer);
        }
    }

    /**
     * A view whose length the kernel can see has that length compiled in, so it may not then change.
     *
     * <p>
     * Rebinding such a view used to update only the Java-side metadata. The installed kernel kept
     * the constant it was compiled with and kept reading that many elements, which after a shrink is
     * past the end of what is now bound. Refusing the rebind is what makes the two mechanisms
     * consistent: a length can be a compile-time constant, or it can vary, not both.
     * </p>
     */
    @Test
    public void testALengthVisibleViewRefusesToChangeLength() {
        long pointer = CudaMemory.allocate(16L * Float.BYTES);
        try {
            FloatArray view = FloatArray.cudaDeviceView(16);
            view.cudaDeviceViewBinding().setLengthVisibleToKernel(true);
            TaskGraph graph = new TaskGraph("fixed-length").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            plan.bindCUDADeviceBuffer(view, pointer, 16L * Float.BYTES, 16, -1, false);
            plan.unbindCUDADeviceBuffer(view);
            // Shrinking is the dangerous direction and growing is the confusing one; both are refused.
            assertThrows(IllegalStateException.class, () -> plan.bindCUDADeviceBuffer(view, pointer, 8L * Float.BYTES, 8, -1, false));
            assertThrows(IllegalStateException.class, () -> view.cudaDeviceViewBinding().setLogicalLength(4));
            // Rebinding at the same length is still fine.
            plan.bindCUDADeviceBuffer(view, pointer, 16L * Float.BYTES, 16, -1, false);
            assertEquals(16, view.getSize());
            plan.unbindCUDADeviceBuffer(view);
        } finally {
            CudaMemory.free(pointer);
        }
    }

    /** A view may never claim more elements than the memory actually bound to it holds. */
    @Test
    public void testLogicalLengthCannotExceedTheBoundSpan() {
        long pointer = CudaMemory.allocate(64L * Float.BYTES);
        try {
            FloatArray view = FloatArray.cudaDeviceView(64);
            TaskGraph graph = new TaskGraph("span").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindCUDADeviceBuffer(view, pointer, 8L * Float.BYTES, 8, -1, false);
            // Within the view's capacity of 64, but beyond the 8 elements actually bound.
            assertThrows(IllegalArgumentException.class, () -> view.cudaDeviceViewBinding().setLogicalLength(32));
            assertEquals(8, view.getSize());
            plan.unbindCUDADeviceBuffer(view);
        } finally {
            CudaMemory.free(pointer);
        }
    }

    /**
     * An output rebound read-only must not still be written by a plan that was already compiled.
     *
     * <p>
     * The access contract was checked where the external buffer wrapper is allocated, and a plan
     * reusing a locked buffer never goes through that path again. The protection existed and the
     * reuse walked past it, so the task wrote into a column that had asked not to be written.
     * </p>
     */
    @Test
    public void testAWritableOutputCannotBeReboundReadOnly() {
        long inPointer = CudaMemory.allocate(Float.BYTES);
        long firstOut = CudaMemory.allocate(Float.BYTES);
        long protectedOut = CudaMemory.allocate(Float.BYTES);
        try {
            CudaMemory.copyToDevice(inPointer, new float[] { 10 });
            CudaMemory.copyToDevice(firstOut, new float[] { -99 });
            CudaMemory.copyToDevice(protectedOut, new float[] { -99 });

            FloatArray in = FloatArray.cudaDeviceView(1);
            FloatArray out = FloatArray.cudaDeviceView(1);
            TaskGraph graph = new TaskGraph("readonly-reuse").task("add", TestDeviceView::addViews, in, in, out, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindCUDADeviceBuffer(in, inPointer, Float.BYTES, 1, -1, true);
            plan.bindCUDADeviceBuffer(out, firstOut, Float.BYTES, 1, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();
            assertEquals(20.0f, CudaMemory.copyFloatsToHost(firstOut, 1)[0], DELTA);

            plan.unbindCUDADeviceBuffer(out);
            plan.bindCUDADeviceBuffer(out, protectedOut, Float.BYTES, 1, -1, true);
            assertThrows(RuntimeException.class, plan::execute, "a task that writes must not run against a read-only binding");
            CudaMemory.synchronizeContext();
            assertEquals(-99.0f, CudaMemory.copyFloatsToHost(protectedOut, 1)[0], DELTA,
                    "the read-only column was written anyway");
        } finally {
            CudaMemory.free(inPointer);
            CudaMemory.free(firstOut);
            CudaMemory.free(protectedOut);
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
