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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceViewNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.memory.CUDADeviceViewBinding;
import uk.ac.manchester.tornado.api.memory.DeviceViewBinding;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

/**
 * Device views: native arrays with no host storage, bound to device memory another library owns.
 *
 * <p>
 * The foreign memory here is allocated with the backend's own API and owned by the test. On CUDA it
 * is allocated in the device's primary context (see {@link CudaMemory}), which is the situation the
 * Spark/RAPIDS adapter is in: cuDF allocates through RMM on the primary context and hands TornadoVM
 * an address it must read in place, with no native-array header in front of it and no copy either
 * way. On Metal it is an {@code MTLBuffer} of the same device (see {@link MetalMemory}).
 * </p>
 *
 * <p>
 * How to run:
 * {@code tornado-test -V uk.ac.manchester.tornado.unittests.deviceview.TestDeviceView}
 * </p>
 */
public class TestDeviceView extends TornadoTestBase {

    private static final String LIBRARY = "test/device-view";

    private static final float UNTOUCHED = -99.0f;

    private ForeignDeviceMemory memory;

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

    /** A reduction whose result would have to live in a view. */
    public static void sumIntoView(FloatArray in, @Reduce FloatArray result, int numRows) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < numRows; i++) {
            result.set(0, result.get(0) + in.get(i));
        }
    }

    /** A library that reads and writes its arguments on the host, through the addresses it is handed. */
    public static final class HostCopyProvider implements TornadoLibraryProvider {

        @Override
        public String libraryName() {
            return LIBRARY;
        }

        @Override
        public boolean canHandle(TornadoXPUDevice device) {
            // Needs CPU-visible device memory.
            return device.getTornadoVMBackend() == TornadoVMBackendType.METAL;
        }

        @Override
        public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
            return new LibraryContext() {
            };
        }

        @Override
        public void dispatch(String functionName, LibraryInvocation invocation) {
            long bytes = (long) (Integer) invocation.getArg(2) * Float.BYTES;
            // FFMSupport performs the restricted reinterpret inside tornado.runtime, which has native access.
            MemorySegment in = FFMSupport.asSegment(invocation.getDevicePointer(0), bytes);
            MemorySegment out = FFMSupport.asSegment(invocation.getDevicePointer(1), bytes);
            MemorySegment.copy(in, ValueLayout.JAVA_FLOAT, 0, out, ValueLayout.JAVA_FLOAT, 0, bytes / Float.BYTES);
        }

        @Override
        public void destroyContext(LibraryContext context) {
        }
    }

    public static LibraryTaskDescriptor hostCopy(FloatArray in, FloatArray out, int numRows) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY) //
                .withFunction("hostCopy") //
                .withParameters(new Object[] { in, out, numRows }) //
                .withAccess(new Access[] { Access.READ_ONLY, Access.WRITE_ONLY, Access.READ_ONLY });
    }

    private static TornadoVMBackendType backend() {
        return getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
    }

    @BeforeEach
    public void requireDeviceViews() {
        super.before();
        switch (backend()) {
            case CUDA -> {
                if (!CudaMemory.isAvailable()) {
                    throw new UnsupportedConfigurationException("The CUDA driver library is not loadable: " + CudaMemory.unavailableCause());
                }
                CudaMemory.enterPrimaryContext();
                memory = new CudaMemory();
            }
            case METAL -> {
                if (!MetalMemory.isAvailable()) {
                    throw new UnsupportedConfigurationException("Metal is not reachable: " + MetalMemory.unavailableCause());
                }
                memory = new MetalMemory();
            }
            default -> throw new TornadoDeviceViewNotSupported("Device views are not implemented for the " + backend() + " backend");
        }
    }

    /** For what only the Metal backend can do or check: the actual length of a buffer, private storage. */
    private static void requireMetal() {
        if (backend() != TornadoVMBackendType.METAL) {
            throw new UnsupportedConfigurationException("This case is specific to the Metal backend");
        }
    }

    /** Asserts that {@code action} fails with an error, or a cause, of {@code type} naming {@code reason}. */
    private static void assertFails(Class<? extends Throwable> type, Executable action, String reason) {
        RuntimeException error = assertThrows(RuntimeException.class, action);
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause) && cause.getMessage() != null && cause.getMessage().contains(reason)) {
                return;
            }
        }
        fail("Expected " + type.getSimpleName() + " naming \"" + reason + "\", got: " + error);
    }

    private static float[] filled(int count, float value) {
        float[] values = new float[count];
        Arrays.fill(values, value);
        return values;
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
        long pointer = memory.allocate(64L * Float.BYTES);
        try {
            FloatArray view = FloatArray.deviceView(64);
            TaskGraph graph = new TaskGraph("lengths").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            plan.bindDeviceBuffer(view, pointer, 64L * Float.BYTES);
            assertEquals(64, view.getSize());

            plan.unbindDeviceBuffer(view);
            plan.bindDeviceBuffer(view, pointer, 16L * Float.BYTES, 10, -1, false);
            assertEquals(10, view.getSize());

            plan.unbindDeviceBuffer(view);
            // A binding larger than the view can address is refused rather than silently clamped.
            assertThrows(IllegalArgumentException.class, () -> plan.bindDeviceBuffer(view, pointer, 4096L * Float.BYTES));
            // A normal array cannot be bound to a foreign buffer at all.
            assertThrows(IllegalArgumentException.class, () -> plan.bindDeviceBuffer(new FloatArray(8), pointer, 32));
        } finally {
            memory.free(pointer);
        }
    }

    /**
     * The arithmetic of a binding needs no device. Whether a handle is well formed is its backend's
     * question, so an odd handle gets through here: it is never dereferenced.
     */
    @Test
    public void testBindingArithmeticIsChecked() {
        DeviceViewBinding binding = new DeviceViewBinding(16, Float.BYTES);
        long handle = 0x1001L;
        TornadoVMBackendType anyBackend = TornadoVMBackendType.METAL;
        assertThrows(NullPointerException.class, () -> binding.bind(null, handle, 0, 16, -1, -1, false, null));
        assertThrows(IllegalArgumentException.class, () -> binding.bind(anyBackend, 0, 0, 16, -1, -1, false, null));
        assertThrows(IllegalArgumentException.class, () -> binding.bind(anyBackend, handle, -4, 16, -1, -1, false, null));
        assertThrows(IllegalArgumentException.class, () -> binding.bind(anyBackend, handle, 2, 16, -1, -1, false, null));
        assertThrows(IllegalArgumentException.class, () -> binding.bind(anyBackend, handle, 0, 2, -1, -1, false, null));
        assertThrows(IllegalArgumentException.class, () -> binding.bind(anyBackend, handle, 0, 68, -1, -1, false, null));
        assertThrows(IllegalArgumentException.class, () -> binding.bind(anyBackend, handle, Long.MAX_VALUE - 3, 16, -1, -1, false, null));
        assertFalse(binding.isBound(), "a refused binding must leave the view as it was");

        binding.bind(anyBackend, handle, 8, 16, -1, -1, false, null);
        assertEquals(handle, binding.deviceBuffer());
        assertEquals(8, binding.byteOffset());
        assertEquals(anyBackend, binding.backend());
        assertEquals(4, binding.logicalLength());
        binding.unbind(null);
        assertFalse(binding.isBound());
        assertEquals(null, binding.backend());

        // A capacity whose size in bytes does not fit in a long is refused, and nothing is allocated either way.
        long largest = Long.MAX_VALUE / Long.BYTES;
        assertEquals(largest * Long.BYTES, new DeviceViewBinding(largest, Long.BYTES).capacityBytes());
        assertThrows(IllegalArgumentException.class, () -> new DeviceViewBinding(largest + 1, Long.BYTES));
        assertThrows(IllegalArgumentException.class, () -> LongArray.deviceView(largest + 1));
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

        long inPointer = memory.allocate((long) rows * Float.BYTES);
        long outPointer = memory.allocate((long) rows * Float.BYTES);
        try {
            memory.copyToDevice(inPointer, inputValues);

            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);

            TaskGraph graph = new TaskGraph("mixed") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights) //
                    .task("scale", TestDeviceView::scaleMixed, weights, in, out, rows) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            memory.synchronize();

            float[] actual = memory.copyFloatsToHost(outPointer, rows);
            float[] expected = new float[rows];
            for (int i = 0; i < rows; i++) {
                expected[i] = inputValues[i] * 2.0f + 1.0f;
            }
            assertArrayEquals(expected, actual, DELTA);

            // The foreign input is untouched: no header was written over its first elements.
            float[] inputAfter = memory.copyFloatsToHost(inPointer, rows);
            assertArrayEquals(inputValues, inputAfter, 0.0f);
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /**
     * One allocation, three views at different offsets, in one kernel: the offset belongs to the
     * argument, not to the handle. The elements around them show that nothing was written outside the
     * views, a header least of all.
     */
    @Test
    public void testViewsOfOneBufferAtDifferentOffsets() {
        final int rows = 512;
        final int guard = 4;
        final long bytes = (long) rows * Float.BYTES;
        final long first = (long) guard * Float.BYTES;
        float[] left = new float[rows];
        float[] right = new float[rows];
        for (int i = 0; i < rows; i++) {
            left[i] = i;
            right[i] = i * 3.0f;
        }
        int total = guard + 3 * rows + guard;
        long pointer = memory.allocate((long) total * Float.BYTES);
        try {
            memory.copyToDevice(pointer, filled(total, UNTOUCHED));
            memory.copyToDevice(pointer, first, left);
            memory.copyToDevice(pointer, first + bytes, right);

            FloatArray a = FloatArray.deviceView(rows);
            FloatArray b = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("offsets").task("add", TestDeviceView::addViews, a, b, out, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(a, pointer, first, bytes, rows, -1, true);
            plan.bindDeviceBuffer(b, pointer, first + bytes, bytes, rows, -1, true);
            plan.bindDeviceBuffer(out, pointer, first + 2 * bytes, bytes, rows, -1, false);
            plan.execute();
            memory.synchronize();

            float[] actual = memory.copyFloatsToHost(pointer, first + 2 * bytes, rows);
            for (int i = 0; i < rows; i++) {
                assertEquals(left[i] + right[i], actual[i], DELTA, "row " + i);
            }
            assertArrayEquals(left, memory.copyFloatsToHost(pointer, first, rows), 0.0f);
            assertArrayEquals(right, memory.copyFloatsToHost(pointer, first + bytes, rows), 0.0f);
            assertArrayEquals(filled(guard, UNTOUCHED), memory.copyFloatsToHost(pointer, 0, guard), 0.0f);
            assertArrayEquals(filled(guard, UNTOUCHED), memory.copyFloatsToHost(pointer, first + 3 * bytes, guard), 0.0f);
        } finally {
            memory.free(pointer);
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
            pointers[i] = memory.allocate((long) capacity * Float.BYTES);
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
                memory.copyToDevice(leftPointer, left);
                memory.copyToDevice(rightPointer, right);

                if (round > 0) {
                    plan.unbindDeviceBuffer(a);
                    plan.unbindDeviceBuffer(b);
                    plan.unbindDeviceBuffer(out);
                }
                plan.bindDeviceBuffer(a, leftPointer, (long) rows * Float.BYTES, rows, -1, true);
                plan.bindDeviceBuffer(b, rightPointer, (long) rows * Float.BYTES, rows, -1, true);
                plan.bindDeviceBuffer(out, pointers[4], (long) rows * Float.BYTES, rows, -1, false);
                plan.execute();
                memory.synchronize();

                float[] actual = memory.copyFloatsToHost(pointers[4], rows);
                for (int i = 0; i < rows; i++) {
                    assertEquals(left[i] + right[i], actual[i], DELTA, "round " + round + " row " + i);
                }
            }
        } finally {
            for (long pointer : pointers) {
                memory.free(pointer);
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
        long inPointer = memory.allocate((long) rows * Float.BYTES);
        long outPointer = memory.allocate((long) rows * Float.BYTES);
        try {
            memory.copyToDevice(inPointer, inputValues);
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("loop").task("loop", TestDeviceView::loopOverViews, in, out, rows, iterations);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            memory.synchronize();

            float[] actual = memory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                float expected = inputValues[i];
                for (int k = 0; k < iterations; k++) {
                    expected = expected * 1.0001f + 0.5f;
                }
                assertEquals(expected, actual[i], DELTA);
            }
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /**
     * A plan gives the owner of an {@code MTLBuffer} nothing to wait on for its command queue, so the
     * results of a long kernel have to be there when {@code execute()} returns: this read uses the owner's own
     * access to the buffer, with nothing in between. Run it with {@code -Dtornado.vmflush=false} and
     * with the Metal batching options too, which leave a kernel in an open command buffer.
     */
    @Test
    public void testResultsAreVisibleToTheOwnerWhenExecuteReturns() {
        requireMetal();
        final int rows = 1 << 16;
        final int iterations = 2000;
        float[] inputValues = new float[rows];
        for (int i = 0; i < rows; i++) {
            inputValues[i] = i * 0.001f;
        }
        long inPointer = memory.allocate((long) rows * Float.BYTES);
        long outPointer = memory.allocate((long) rows * Float.BYTES);
        try {
            memory.copyToDevice(inPointer, inputValues);
            memory.copyToDevice(outPointer, filled(rows, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("visible").task("loop", TestDeviceView::loopOverViews, in, out, rows, iterations);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);

            // Twice: the second execution reuses everything the first one set up.
            for (int execution = 0; execution < 2; execution++) {
                plan.execute();
                float[] actual = memory.copyFloatsToHost(outPointer, rows);
                for (int i = 0; i < rows; i++) {
                    assertNotEquals(UNTOUCHED, actual[i], "row " + i + " was not written when execute() returned");
                }
                for (int i = 0; i < rows; i += 4096) {
                    float expected = inputValues[i];
                    for (int k = 0; k < iterations; k++) {
                        expected = expected * 1.0001f + 0.5f;
                    }
                    assertEquals(expected, actual[i], 1.0f, "row " + i);
                }
                memory.copyToDevice(outPointer, filled(rows, UNTOUCHED));
            }
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
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
        long indexPointer = memory.allocate((long) rows * Integer.BYTES);
        long valuePointer = memory.allocate((long) rows * Float.BYTES);
        long outPointer = memory.allocate((long) rows * Float.BYTES);
        try {
            memory.copyToDevice(indexPointer, indices);
            memory.copyToDevice(valuePointer, values);
            IntArray indexView = IntArray.deviceView(rows);
            FloatArray valueView = FloatArray.deviceView(rows);
            FloatArray outView = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("gather").task("gather", TestDeviceView::gatherThroughIntView, indexView, valueView, outView, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(indexView, indexPointer, (long) rows * Integer.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(valueView, valuePointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(outView, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            memory.synchronize();

            float[] actual = memory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                assertEquals(values[indices[i]], actual[i], DELTA);
            }
        } finally {
            memory.free(indexPointer);
            memory.free(valuePointer);
            memory.free(outPointer);
        }
    }

    /** A view belongs to one plan at a time, so two tasks cannot rebind it under each other. */
    @Test
    public void testAViewCannotBeBoundByTwoPlans() {
        long pointer = memory.allocate(256L * Float.BYTES);
        try {
            FloatArray view = FloatArray.deviceView(256);
            TaskGraph graph = new TaskGraph("owner").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan first = new TornadoExecutionPlan(graph.snapshot());
            TornadoExecutionPlan second = new TornadoExecutionPlan(graph.snapshot());
            first.bindDeviceBuffer(view, pointer, 256L * Float.BYTES);
            assertThrows(IllegalStateException.class, () -> second.bindDeviceBuffer(view, pointer, 256L * Float.BYTES));
            assertThrows(IllegalStateException.class, () -> second.unbindDeviceBuffer(view));
            first.unbindDeviceBuffer(view);
            second.bindDeviceBuffer(view, pointer, 256L * Float.BYTES);
            second.unbindDeviceBuffer(view);
        } finally {
            memory.free(pointer);
        }
    }

    /** Executing with a view that was never bound fails instead of launching against nothing. */
    @Test
    public void testUnboundViewIsRefused() {
        FloatArray in = FloatArray.deviceView(32);
        FloatArray out = FloatArray.deviceView(32);
        TaskGraph graph = new TaskGraph("unbound").task("add", TestDeviceView::addViews, in, in, out, 32);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
        assertFails(IllegalStateException.class, plan::execute, "no device buffer bound");
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
        long leftPointer = memory.allocate((long) rows * Float.BYTES);
        long rightPointer = memory.allocate((long) rows * Float.BYTES);
        long outPointer = memory.allocate((long) rows * Float.BYTES);
        try {
            memory.copyToDevice(leftPointer, values);
            memory.copyToDevice(rightPointer, values);
            FloatArray viewA = FloatArray.deviceView(rows);
            FloatArray viewB = FloatArray.deviceView(rows);
            FloatArray viewOut = FloatArray.deviceView(rows);
            TaskGraph viewGraph = new TaskGraph("headerless").task("add", TestDeviceView::addViews, viewA, viewB, viewOut, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(viewGraph.snapshot());
            plan.bindDeviceBuffer(viewA, leftPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(viewB, rightPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindDeviceBuffer(viewOut, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            memory.synchronize();

            float[] actual = memory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                assertEquals(values[i] * 2, actual[i], DELTA, "row " + i);
            }
        } finally {
            memory.free(leftPointer);
            memory.free(rightPointer);
            memory.free(outPointer);
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
        long inPointer = memory.allocate((long) values.length * Float.BYTES);
        long outPointer = memory.allocate(4L * Float.BYTES);
        try {
            memory.copyToDevice(inPointer, values);
            memory.copyToDevice(outPointer, new float[] { -99, -99, -99, -99 });

            FloatArray in = FloatArray.deviceView(4);
            FloatArray out = FloatArray.deviceView(4);
            TaskGraph graph = new TaskGraph("unused-context") //
                    .task("copy", TestDeviceView::copyAfterUnusedContext, new KernelContext(), in, out, 4);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, 4L * Float.BYTES, 4, -1, true);
            plan.bindDeviceBuffer(out, outPointer, 4L * Float.BYTES, 4, -1, false);
            plan.execute();
            memory.synchronize();

            assertArrayEquals(new float[] { 10, 20, 30, 40 }, memory.copyFloatsToHost(outPointer, 4), 0.0f,
                    "the unused context argument shifted which array was treated as headerless");
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
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
        long pointer = memory.allocate(16L * Float.BYTES);
        try {
            FloatArray view = FloatArray.deviceView(16);
            view.deviceViewBinding().setLengthVisibleToKernel(true);
            TaskGraph graph = new TaskGraph("fixed-length").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            plan.bindDeviceBuffer(view, pointer, 16L * Float.BYTES, 16, -1, false);
            plan.unbindDeviceBuffer(view);
            // Shrinking is the dangerous direction and growing is the confusing one; both are refused.
            assertThrows(IllegalStateException.class, () -> plan.bindDeviceBuffer(view, pointer, 8L * Float.BYTES, 8, -1, false));
            assertThrows(IllegalStateException.class, () -> view.deviceViewBinding().setLogicalLength(4));
            // Rebinding at the same length is still fine.
            plan.bindDeviceBuffer(view, pointer, 16L * Float.BYTES, 16, -1, false);
            assertEquals(16, view.getSize());
            plan.unbindDeviceBuffer(view);
        } finally {
            memory.free(pointer);
        }
    }

    /** A view may never claim more elements than the memory actually bound to it holds. */
    @Test
    public void testLogicalLengthCannotExceedTheBoundSpan() {
        long pointer = memory.allocate(64L * Float.BYTES);
        try {
            FloatArray view = FloatArray.deviceView(64);
            TaskGraph graph = new TaskGraph("span").task("t", TestDeviceView::addViews, view, view, view, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(view, pointer, 8L * Float.BYTES, 8, -1, false);
            // Within the view's capacity of 64, but beyond the 8 elements actually bound.
            assertThrows(IllegalArgumentException.class, () -> view.deviceViewBinding().setLogicalLength(32));
            assertEquals(8, view.getSize());
            plan.unbindDeviceBuffer(view);
        } finally {
            memory.free(pointer);
        }
    }

    /**
     * An output rebound read-only must not still be written by a plan that was already compiled, and
     * the plan can run again once the output is bound properly.
     *
     * <p>
     * The access contract was checked where the external buffer wrapper is allocated, and a plan
     * reusing a locked buffer never goes through that path again. The protection existed and the
     * reuse walked past it, so the task wrote into a column that had asked not to be written. The
     * message matters too: with {@code -Dtornado.recover.bailout=True} a graph with views must report
     * this rather than try the task in sequential Java, on arrays that have no host storage.
     * </p>
     */
    @Test
    public void testAWritableOutputCannotBeReboundReadOnly() {
        long inPointer = memory.allocate(Float.BYTES);
        long firstOut = memory.allocate(Float.BYTES);
        long protectedOut = memory.allocate(Float.BYTES);
        try {
            memory.copyToDevice(inPointer, new float[] { 10 });
            memory.copyToDevice(firstOut, new float[] { -99 });
            memory.copyToDevice(protectedOut, new float[] { -99 });

            FloatArray in = FloatArray.deviceView(1);
            FloatArray out = FloatArray.deviceView(1);
            TaskGraph graph = new TaskGraph("readonly-reuse").task("add", TestDeviceView::addViews, in, in, out, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, Float.BYTES, 1, -1, true);
            plan.bindDeviceBuffer(out, firstOut, Float.BYTES, 1, -1, false);
            plan.execute();
            memory.synchronize();
            assertEquals(20.0f, memory.copyFloatsToHost(firstOut, 1)[0], DELTA);

            plan.unbindDeviceBuffer(out);
            plan.bindDeviceBuffer(out, protectedOut, Float.BYTES, 1, -1, true);
            assertFails(RuntimeException.class, plan::execute, "bound read-only");
            memory.synchronize();
            assertEquals(-99.0f, memory.copyFloatsToHost(protectedOut, 1)[0], DELTA,
                    "the read-only column was written anyway");

            // A refused execution leaves the plan able to run.
            memory.copyToDevice(inPointer, new float[] { 30 });
            plan.unbindDeviceBuffer(out);
            plan.bindDeviceBuffer(out, firstOut, Float.BYTES, 1, -1, false);
            plan.execute();
            memory.synchronize();
            assertEquals(60.0f, memory.copyFloatsToHost(firstOut, 1)[0], DELTA);
        } finally {
            memory.free(inPointer);
            memory.free(firstOut);
            memory.free(protectedOut);
        }
    }

    /** As above, when the refused execution is the plan's first: nothing has been set up yet. */
    @Test
    public void testAPlanRefusedOnItsFirstExecutionCanRunAfterwards() {
        long inPointer = memory.allocate(Float.BYTES);
        long outPointer = memory.allocate(Float.BYTES);
        try {
            memory.copyToDevice(inPointer, new float[] { 10 });
            memory.copyToDevice(outPointer, new float[] { UNTOUCHED });

            FloatArray in = FloatArray.deviceView(1);
            FloatArray out = FloatArray.deviceView(1);
            TaskGraph graph = new TaskGraph("refused-first").task("add", TestDeviceView::addViews, in, in, out, 1);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, Float.BYTES, 1, -1, true);
            plan.bindDeviceBuffer(out, outPointer, Float.BYTES, 1, -1, true);
            assertFails(RuntimeException.class, plan::execute, "bound read-only");
            memory.synchronize();
            assertEquals(UNTOUCHED, memory.copyFloatsToHost(outPointer, 1)[0], 0.0f);

            plan.unbindDeviceBuffer(out);
            plan.bindDeviceBuffer(out, outPointer, Float.BYTES, 1, -1, false);
            plan.execute();
            memory.synchronize();
            assertEquals(20.0f, memory.copyFloatsToHost(outPointer, 1)[0], DELTA);
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /**
     * A handle bound for another backend is refused before anything uses it. The handle is bogus on
     * purpose: sending it a message, or launching a kernel on it, would crash the run.
     */
    @Test
    public void testAHandleOfAnotherBackendIsRefusedBeforeItIsUsed() {
        TornadoVMBackendType other = backend() == TornadoVMBackendType.METAL ? TornadoVMBackendType.CUDA : TornadoVMBackendType.METAL;
        long bogus = 0x1000L;
        FloatArray in = FloatArray.deviceView(4);
        FloatArray out = FloatArray.deviceView(4);
        in.deviceViewBinding().bind(other, bogus, 0, 16, -1, -1, true, null);
        out.deviceViewBinding().bind(other, bogus, 0, 16, -1, -1, false, null);
        TaskGraph graph = new TaskGraph("wrong-backend").task("add", TestDeviceView::addViews, in, in, out, 4);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
        assertFails(TornadoDeviceViewNotSupported.class, plan::execute, "handle");
    }

    /** The device a binding names is checked on every execution, not only by the bind call. */
    @Test
    public void testADeviceMismatchIsRefused() {
        TornadoDevice device = getTornadoRuntime().getDefaultDevice();
        int otherDevice = device.getDeviceContext().getDeviceIndex() + 1;
        long pointer = memory.allocate(4L * Float.BYTES);
        try {
            memory.copyToDevice(pointer, filled(4, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(4);
            FloatArray out = FloatArray.deviceView(4);
            TaskGraph graph = new TaskGraph("wrong-device").task("add", TestDeviceView::addViews, in, in, out, 4);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            assertThrows(IllegalArgumentException.class, () -> plan.bindDeviceBuffer(in, pointer, 4L * Float.BYTES, 4, otherDevice, true));

            in.deviceViewBinding().bind(backend(), pointer, 0, 4L * Float.BYTES, 4, otherDevice, true, null);
            out.deviceViewBinding().bind(backend(), pointer, 0, 4L * Float.BYTES, 4, otherDevice, false, null);
            assertFails(RuntimeException.class, plan::execute, "only valid on its own device");
            memory.synchronize();
            assertArrayEquals(filled(4, UNTOUCHED), memory.copyFloatsToHost(pointer, 4), 0.0f);
        } finally {
            memory.free(pointer);
        }
    }

    /** Metal knows how long a buffer really is, so a binding past its end never reaches a kernel. */
    @Test
    public void testARangePastTheEndOfTheBufferIsRefused() {
        requireMetal();
        long inPointer = memory.allocate(64L * Float.BYTES);
        long outPointer = memory.allocate(16L * Float.BYTES);
        try {
            memory.copyToDevice(outPointer, filled(16, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(64);
            FloatArray out = FloatArray.deviceView(64);
            TaskGraph graph = new TaskGraph("past-the-end").task("add", TestDeviceView::addViews, in, in, out, 16);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, 32L * Float.BYTES, 32, -1, true);
            plan.bindDeviceBuffer(out, outPointer, 32L * Float.BYTES, 32, -1, false);
            assertFails(RuntimeException.class, plan::execute, "past the end");

            // In range by length, past the end by offset.
            plan.unbindDeviceBuffer(out);
            plan.bindDeviceBuffer(out, outPointer, 8L * Float.BYTES, 16L * Float.BYTES, 16, -1, false);
            assertFails(RuntimeException.class, plan::execute, "past the end");
            assertArrayEquals(filled(16, UNTOUCHED), memory.copyFloatsToHost(outPointer, 16), 0.0f);
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /**
     * A buffer only the GPU can reach works as a kernel argument. Its results come out through a
     * second kernel, into shared storage the owner can read.
     */
    @Test
    public void testAPrivateBufferAsAKernelArgument() {
        requireMetal();
        final int rows = 256;
        final long bytes = (long) rows * Float.BYTES;
        float[] values = new float[rows];
        for (int i = 0; i < rows; i++) {
            values[i] = i;
        }
        long inPointer = memory.allocate(bytes);
        long privatePointer = ((MetalMemory) memory).allocatePrivate(bytes);
        long outPointer = memory.allocate(bytes);
        try {
            memory.copyToDevice(inPointer, values);
            FloatArray a = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("private").task("add", TestDeviceView::addViews, a, a, out, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());

            plan.bindDeviceBuffer(a, inPointer, bytes, rows, -1, true);
            plan.bindDeviceBuffer(out, privatePointer, bytes, rows, -1, false);
            plan.execute();

            plan.unbindDeviceBuffer(a);
            plan.unbindDeviceBuffer(out);
            plan.bindDeviceBuffer(a, privatePointer, bytes, rows, -1, true);
            plan.bindDeviceBuffer(out, outPointer, bytes, rows, -1, false);
            plan.execute();

            float[] actual = memory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i++) {
                assertEquals(values[i] * 4, actual[i], DELTA, "row " + i);
            }
        } finally {
            memory.free(inPointer);
            memory.free(privatePointer);
            memory.free(outPointer);
        }
    }

    /**
     * A graph of library tasks only, whose results the owner reads straight away. A library task asks
     * for a CPU address, which a private buffer does not have, and that is refused rather than
     * answered with an offset from null. The plan can run again afterwards.
     */
    @Test
    public void testALibraryOnlyGraphOverViews() throws TornadoExecutionPlanException {
        requireMetal();
        final int rows = 64;
        final long bytes = (long) rows * Float.BYTES;
        final long offset = 4L * Float.BYTES;
        float[] values = new float[rows];
        for (int i = 0; i < rows; i++) {
            values[i] = i + 1;
        }
        long inPointer = memory.allocate(offset + bytes);
        long outPointer = memory.allocate(bytes);
        long privatePointer = ((MetalMemory) memory).allocatePrivate(bytes);
        try {
            memory.copyToDevice(inPointer, offset, values);
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("library").libraryTask("copy", TestDeviceView::hostCopy, in, out, rows);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.bindDeviceBuffer(in, inPointer, offset, bytes, rows, -1, true);
                plan.bindDeviceBuffer(out, outPointer, bytes);
                plan.execute();
                assertArrayEquals(values, memory.copyFloatsToHost(outPointer, rows), 0.0f);

                plan.unbindDeviceBuffer(out);
                plan.bindDeviceBuffer(out, privatePointer, bytes);
                assertFails(RuntimeException.class, plan::execute, "private storage");

                memory.copyToDevice(outPointer, filled(rows, UNTOUCHED));
                plan.unbindDeviceBuffer(out);
                plan.bindDeviceBuffer(out, outPointer, bytes);
                plan.execute();
                assertArrayEquals(values, memory.copyFloatsToHost(outPointer, rows), 0.0f);
            }
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
            memory.free(privatePointer);
        }
    }

    /** Closing a plan gives a borrowed buffer back as it was: the owner still reads it and frees it. */
    @Test
    public void testClosingThePlanLeavesTheBufferToItsOwner() throws TornadoExecutionPlanException {
        final int rows = 32;
        final long bytes = (long) rows * Float.BYTES;
        long inPointer = memory.allocate(bytes);
        long outPointer = memory.allocate(bytes);
        try {
            memory.copyToDevice(inPointer, filled(rows, 1.5f));
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("closed").task("add", TestDeviceView::addViews, in, in, out, rows);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.bindDeviceBuffer(in, inPointer, bytes, rows, -1, true);
                plan.bindDeviceBuffer(out, outPointer, bytes, rows, -1, false);
                plan.execute();
                memory.synchronize();
            }
            assertArrayEquals(filled(rows, 1.5f), memory.copyFloatsToHost(inPointer, rows), 0.0f);
            assertArrayEquals(filled(rows, 3.0f), memory.copyFloatsToHost(outPointer, rows), DELTA);
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /** A captured CUDA graph would replay an old address without looking at the binding again. */
    @Test
    public void testCUDAGraphCaptureIsRefused() {
        long pointer = memory.allocate(4L * Float.BYTES);
        try {
            memory.copyToDevice(pointer, filled(4, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(4);
            FloatArray out = FloatArray.deviceView(4);
            TaskGraph graph = new TaskGraph("captured").task("add", TestDeviceView::addViews, in, in, out, 4);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, pointer, 4L * Float.BYTES, 4, -1, true);
            plan.bindDeviceBuffer(out, pointer, 4L * Float.BYTES, 4, -1, false);
            assertFails(RuntimeException.class, () -> plan.withCUDAGraph().execute(), "withCUDAGraph");
            memory.synchronize();
            assertArrayEquals(filled(4, UNTOUCHED), memory.copyFloatsToHost(pointer, 4), 0.0f);
        } finally {
            memory.free(pointer);
        }
    }

    /** TornadoVM cannot split an allocation it does not own into chunks. */
    @Test
    public void testMemoryChunkingIsRefused() {
        long pointer = memory.allocate(1024L * Float.BYTES);
        try {
            memory.copyToDevice(pointer, filled(1024, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(1024);
            FloatArray out = FloatArray.deviceView(1024);
            TaskGraph graph = new TaskGraph("chunked").task("add", TestDeviceView::addViews, in, in, out, 1024);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, pointer, 1024L * Float.BYTES, 1024, -1, true);
            plan.bindDeviceBuffer(out, pointer, 1024L * Float.BYTES, 1024, -1, false);
            assertFails(RuntimeException.class, () -> plan.withBatch("1MB").execute(), "withBatch");
            memory.synchronize();
            assertArrayEquals(filled(1024, UNTOUCHED), memory.copyFloatsToHost(pointer, 1024), 0.0f);
        } finally {
            memory.free(pointer);
        }
    }

    /**
     * A plan that has run does not compile again for a mode set afterwards, so the modes are refused
     * when they are asked for, and the refusal leaves the plan as it was.
     */
    @Test
    public void testUnsupportedModesAreRefusedAfterAnExecution() {
        final int rows = 16;
        final long bytes = (long) rows * Float.BYTES;
        long inPointer = memory.allocate(bytes);
        long outPointer = memory.allocate(bytes);
        try {
            memory.copyToDevice(inPointer, filled(rows, 2.0f));
            memory.copyToDevice(outPointer, filled(rows, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("modes-after").task("add", TestDeviceView::addViews, in, in, out, rows);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, inPointer, bytes, rows, -1, true);
            plan.bindDeviceBuffer(out, outPointer, bytes, rows, -1, false);
            plan.execute();
            memory.synchronize();
            assertArrayEquals(filled(rows, 4.0f), memory.copyFloatsToHost(outPointer, rows), DELTA);

            assertFails(RuntimeException.class, plan::withCUDAGraph, "withCUDAGraph");
            assertFails(RuntimeException.class, () -> plan.withBatch("1MB"), "withBatch");

            memory.copyToDevice(inPointer, filled(rows, 3.0f));
            plan.execute();
            memory.synchronize();
            assertArrayEquals(filled(rows, 6.0f), memory.copyFloatsToHost(outPointer, rows), DELTA);
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /**
     * A plan-wide mode that a view graph refuses reaches no other graph of the plan. The first graph
     * here consumes host storage from unified memory, which refuses both modes when it compiles, so
     * if either had reached it the plan could not run.
     */
    @Test
    public void testARefusedModeLeavesTheOtherGraphsAsTheyWere() throws TornadoExecutionPlanException {
        TornadoDevice device = getTornadoRuntime().getDefaultDevice();
        if (!device.isUnifiedMemorySupported()) {
            throw new UnsupportedConfigurationException("The device does not share memory with the host");
        }
        final int rows = 16;
        final long bytes = (long) rows * Float.BYTES;
        FloatArray weights = FloatArray.fromSegmentShallow(Arena.ofAuto().allocate(65536, 65536));
        FloatArray hostIn = new FloatArray(rows);
        FloatArray hostOut = new FloatArray(rows);
        for (int i = 0; i < rows; i++) {
            weights.set(i, 2.0f);
            hostIn.set(i, i);
        }
        long inPointer = memory.allocate(bytes);
        long outPointer = memory.allocate(bytes);
        try {
            memory.copyToDevice(inPointer, filled(rows, 5.0f));
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph ordinary = new TaskGraph("modes-ordinary") //
                    .consumeFromUnifiedMemory(weights) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, hostIn) //
                    .task("scale", TestDeviceView::scaleMixed, weights, hostIn, hostOut, rows) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, hostOut);
            TaskGraph viewed = new TaskGraph("modes-viewed").task("add", TestDeviceView::addViews, in, in, out, rows);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(ordinary.snapshot(), viewed.snapshot())) {
                plan.bindDeviceBuffer(in, inPointer, bytes, rows, -1, true);
                plan.bindDeviceBuffer(out, outPointer, bytes, rows, -1, false);

                assertFails(RuntimeException.class, plan::withCUDAGraph, "withCUDAGraph");
                assertFails(RuntimeException.class, () -> plan.withBatch("1MB"), "withBatch");

                plan.execute();
                memory.synchronize();
                for (int i = 0; i < rows; i++) {
                    assertEquals(i * 2.0f + 1.0f, hostOut.get(i), DELTA, "row " + i);
                }
                assertArrayEquals(filled(rows, 10.0f), memory.copyFloatsToHost(outPointer, rows), DELTA);
            }
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /** A reduction result is compiled with a header, which a view does not have. */
    @Test
    public void testAReductionIntoAViewIsRefused() {
        long pointer = memory.allocate(8L * Float.BYTES);
        try {
            memory.copyToDevice(pointer, filled(8, UNTOUCHED));
            FloatArray in = FloatArray.deviceView(8);
            FloatArray result = FloatArray.deviceView(8);
            TaskGraph graph = new TaskGraph("reduced").task("sum", TestDeviceView::sumIntoView, in, result, 8);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindDeviceBuffer(in, pointer, 8L * Float.BYTES, 8, -1, true);
            plan.bindDeviceBuffer(result, pointer, 8L * Float.BYTES, 8, -1, false);
            assertFails(RuntimeException.class, plan::execute, "reduction");
            memory.synchronize();
            assertArrayEquals(filled(8, UNTOUCHED), memory.copyFloatsToHost(pointer, 8), 0.0f);
        } finally {
            memory.free(pointer);
        }
    }

    /**
     * Storage consumed from unified memory and a view in one kernel. The check for overlapping
     * buffers looks at the host storage of the graph's arrays, and a view has none to look at.
     */
    @Test
    public void testAConsumedHostInputBesideAView() throws TornadoExecutionPlanException {
        TornadoDevice device = getTornadoRuntime().getDefaultDevice();
        if (!device.isUnifiedMemorySupported()) {
            throw new UnsupportedConfigurationException("The device does not share memory with the host");
        }
        FloatArray weights = FloatArray.fromSegmentShallow(Arena.ofAuto().allocate(65536, 65536));
        assertTrue(device.isOnUnifiedMemory(weights));
        final int rows = weights.getSize();
        final long bytes = (long) rows * Float.BYTES;
        float[] values = new float[rows];
        for (int i = 0; i < rows; i++) {
            weights.set(i, 3.0f);
            values[i] = i;
        }
        long inPointer = memory.allocate(bytes);
        long outPointer = memory.allocate(bytes);
        try {
            memory.copyToDevice(inPointer, values);
            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);
            TaskGraph graph = new TaskGraph("host-and-view") //
                    .consumeFromUnifiedMemory(weights) //
                    .task("scale", TestDeviceView::scaleMixed, weights, in, out, rows);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.bindDeviceBuffer(in, inPointer, bytes, rows, -1, true);
                plan.bindDeviceBuffer(out, outPointer, bytes, rows, -1, false);
                plan.execute();
                memory.synchronize();
            }
            float[] actual = memory.copyFloatsToHost(outPointer, rows);
            for (int i = 0; i < rows; i += 512) {
                assertEquals(values[i] * 3.0f + 1.0f, actual[i], DELTA, "row " + i);
            }
        } finally {
            memory.free(inPointer);
            memory.free(outPointer);
        }
    }

    /** What code written against the CUDA-only API calls keeps working, with no device involved. */
    @Test
    public void testCUDAAliasesKeepTheirContract() {
        FloatArray view = FloatArray.cudaDeviceView(8);
        assertTrue(view.isDeviceView());
        assertTrue(view.isCUDADeviceView());
        assertEquals(8, view.getSize());
        CUDADeviceViewBinding binding = view.cudaDeviceViewBinding();
        assertSame(binding, view.deviceViewBinding());

        assertThrows(IllegalArgumentException.class, () -> binding.bind(0x1002L, 32L, -1, -1, false, null), "a misaligned address");
        assertThrows(IllegalStateException.class, binding::requireBoundPointer);
        binding.bind(0x1000L, 32L, 4, 0, true, null);
        assertEquals(0x1000L, binding.devicePointer());
        assertEquals(0x1000L, binding.requireBoundPointer());
        assertEquals(TornadoVMBackendType.CUDA, binding.backend());
        assertEquals(4, binding.logicalLength());
        assertEquals(0, binding.deviceIndex());
        assertTrue(binding.isReadOnly());
        binding.unbind(null);
        assertEquals(0L, binding.devicePointer());

        // Its handle is a CUDA address, whatever the backend asked for.
        assertThrows(TornadoDeviceViewNotSupported.class, () -> binding.bind(TornadoVMBackendType.METAL, 0x1000L, 0, 32, -1, -1, false, null));

        assertTrue(ByteArray.cudaDeviceView(8).isCUDADeviceView());
        assertTrue(IntArray.cudaDeviceView(8L).isCUDADeviceView());
        assertTrue(LongArray.cudaDeviceView(8).isCUDADeviceView());
        assertTrue(DoubleArray.cudaDeviceView(8).isCUDADeviceView());

        FloatArray generic = FloatArray.deviceView(8);
        assertTrue(generic.isDeviceView());
        assertFalse(generic.isCUDADeviceView());
        assertThrows(IllegalStateException.class, generic::cudaDeviceViewBinding);
        assertThrows(IllegalStateException.class, () -> new FloatArray(8).cudaDeviceViewBinding());
    }

    /**
     * The CUDA bind methods are CUDA-only: on any other plan, a pointer must not be taken for that
     * backend's handle. On CUDA, they and the generic methods take either kind of view.
     */
    @Test
    public void testCUDABindAliasesAreCUDAOnly() {
        long pointer = memory.allocate(8L * Float.BYTES);
        try {
            FloatArray generic = FloatArray.deviceView(8);
            FloatArray cuda = FloatArray.cudaDeviceView(8);
            TaskGraph graph = new TaskGraph("aliases").task("add", TestDeviceView::addViews, generic, generic, cuda, 8);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            if (backend() == TornadoVMBackendType.CUDA) {
                plan.bindCUDADeviceBuffer(generic, pointer, 8L * Float.BYTES);
                assertEquals(TornadoVMBackendType.CUDA, generic.deviceViewBinding().backend());
                plan.unbindCUDADeviceBuffer(generic);
                plan.bindDeviceBuffer(cuda, pointer, 8L * Float.BYTES);
                assertEquals(pointer, cuda.cudaDeviceViewBinding().devicePointer());
                plan.unbindDeviceBuffer(cuda);
            } else {
                assertThrows(TornadoDeviceViewNotSupported.class, () -> plan.bindCUDADeviceBuffer(generic, pointer, 8L * Float.BYTES));
                assertThrows(TornadoDeviceViewNotSupported.class, () -> plan.bindCUDADeviceBuffer(cuda, pointer, 8L * Float.BYTES));
                assertThrows(TornadoDeviceViewNotSupported.class, () -> plan.bindDeviceBuffer(cuda, pointer, 8L * Float.BYTES));
                assertFalse(generic.deviceViewBinding().isBound());
                assertFalse(cuda.deviceViewBinding().isBound());
            }
        } finally {
            memory.free(pointer);
        }
    }

    /**
     * A {@code cudaDeviceView} bound directly holds a CUDA address, which another backend must refuse
     * before using it. The address is bogus on purpose.
     */
    @Test
    public void testACUDAViewIsRefusedOnAnotherBackend() {
        requireMetal();
        FloatArray in = FloatArray.cudaDeviceView(4);
        FloatArray out = FloatArray.cudaDeviceView(4);
        in.cudaDeviceViewBinding().bind(0x1000L, 16L, -1, -1, true, null);
        out.cudaDeviceViewBinding().bind(0x1000L, 16L, -1, -1, false, null);
        TaskGraph graph = new TaskGraph("cuda-view").task("add", TestDeviceView::addViews, in, in, out, 4);
        TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
        assertFails(TornadoDeviceViewNotSupported.class, plan::execute, "handle");
    }
}
