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
package uk.ac.manchester.tornado.unittests.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoUnifiedMemoryNotSupported;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/** Run with tornado-test -V uk.ac.manchester.tornado.unittests.memory.TestConsumeFromUnifiedMemory. */
public class TestConsumeFromUnifiedMemory extends TornadoTestBase {

    private static final int SIZE = 4096;

    public static void multiply(FloatArray weights, FloatArray input, FloatArray output) {
        for (@Parallel int i = 0; i < output.getSize(); i++) {
            output.set(i, weights.get(i) * input.get(i));
        }
    }

    public static void sum(FloatArray input, @Reduce FloatArray output) {
        output.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(0, output.get(0) + input.get(i));
        }
    }

    /** Skips the test unless the default device can consume from unified memory. */
    private void requireUnifiedMemory() {
        TornadoDevice device = getTornadoRuntime().getDefaultDevice();
        if (!device.isUnifiedMemorySupported()) {
            assertNotBackend(device.getTornadoVMBackend(), "The device does not share memory with the host");
        }
    }

    /** Skips the test unless the default device is a Metal device with unified memory. */
    private void requireMetalUnifiedMemory() {
        requireUnifiedMemory();
        assertNotBackend(TornadoVMBackendType.CUDA);
        assertNotBackend(TornadoVMBackendType.OPENCL);
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

    private static void assertRejected(Executable action, String reason) {
        assertFails(RuntimeException.class, action, reason);
    }

    /** Asserts that {@code action} fails because the device cannot consume the storage in place. */
    private static void assertNotOnUnifiedMemory(Executable action, String reason) {
        assertFails(TornadoUnifiedMemoryNotSupported.class, action, reason);
    }

    private boolean isOnUnifiedMemory(Object object) {
        return getTornadoRuntime().getDefaultDevice().isOnUnifiedMemory(object);
    }

    private static MemorySegment mappedStorage(Arena arena, long bytes) throws IOException {
        Path file = Files.createTempFile("tornado-unified", ".bin");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[1]), bytes - 1);
            return channel.map(FileChannel.MapMode.PRIVATE, 0, bytes, arena);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static FloatArray mappedWeights() throws IOException {
        MemorySegment mapping = mappedStorage(Arena.ofAuto(), 131072);
        return FloatArray.fromSegmentShallow(mapping.asSlice(48, TornadoNativeArray.ARRAY_HEADER + SIZE * (long) Float.BYTES));
    }

    private static int liveMetalBuffers() throws ReflectiveOperationException {
        // The unit-test module has no static dependency on any particular driver.
        Class<?> metalObjects = Class.forName("uk.ac.manchester.tornado.drivers.metal.ffm.MetalObjects");
        return (int) metalObjects.getMethod("liveUnifiedMemoryBuffers").invoke(null);
    }

    private static TaskGraph graph(String name, FloatArray weights, FloatArray input, FloatArray output) {
        return new TaskGraph(name) //
                .consumeFromUnifiedMemory(weights) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("multiply", TestConsumeFromUnifiedMemory::multiply, weights, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
    }

    @Test
    public void testMappedOffsetAndRepeatedExecution() throws Exception {
        requireUnifiedMemory();
        Path file = Files.createTempFile("tornado-unified", ".bin");
        Arena arena = Arena.ofAuto();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[1]), 131071);
            MemorySegment mapping = channel.map(FileChannel.MapMode.PRIVATE, 0, 131072, arena);
            FloatArray weights = FloatArray.fromSegmentShallow(mapping.asSlice(49152 + 48, TornadoNativeArray.ARRAY_HEADER + SIZE * (long) Float.BYTES));
            assertTrue(isOnUnifiedMemory(weights));
            FloatArray input = new FloatArray(SIZE);
            FloatArray output = new FloatArray(SIZE);
            var snapshot = graph("mapped", weights, input, output).snapshot();
            // Declaration and snapshot do not bind the storage: initialization is still allowed.
            weights.init(3.0f);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(snapshot)) {
                for (int run = 1; run <= 4; run++) {
                    input.init(run);
                    plan.execute();
                    for (int i = 0; i < SIZE; i++) {
                        assertEquals(3.0f * run, output.get(i), 0.0f);
                    }
                    assertEquals(input.getNumBytesOfSegmentWithHeader() + output.getNumBytesOfSegmentWithHeader(), plan.getCurrentDeviceMemoryUsage());
                }
                assertRejected(() -> plan.transferToDevice(weights), "consumeFromUnifiedMemory");
            }
            // Closing the plan must not free the caller's mapping.
            assertEquals(3.0f, weights.get(0), 0.0f);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void testDisjointViewsAndIndependentPlans() throws Exception {
        requireUnifiedMemory();
        long bytes = TornadoNativeArray.ARRAY_HEADER + SIZE * (long) Float.BYTES;
        MemorySegment backing = mappedStorage(Arena.ofAuto(), 131072);
        FloatArray a = FloatArray.fromSegmentShallow(backing.asSlice(16, bytes));
        FloatArray b = FloatArray.fromSegmentShallow(backing.asSlice(bytes + 48, bytes));
        assertTrue(isOnUnifiedMemory(a));
        assertTrue(isOnUnifiedMemory(b));
        a.init(2.0f);
        b.init(4.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);
        TaskGraph views = new TaskGraph("views").consumeFromUnifiedMemory(a, b) //
                .task("multiply", TestConsumeFromUnifiedMemory::multiply, a, b, outA) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outA);
        try (TornadoExecutionPlan first = new TornadoExecutionPlan(views.snapshot());
                TornadoExecutionPlan second = new TornadoExecutionPlan(graph("second", a, input, outB).snapshot())) {
            first.execute();
            assertEquals(8.0f, outA.get(0), 0.0f);
            second.execute();
            first.freeDeviceMemory();
            second.execute();
            assertEquals(6.0f, outB.get(0), 0.0f);
        }
        assertEquals(2.0f, a.get(0), 0.0f);
    }

    @Test
    public void testSnapshotIsIsolatedFromLaterDeclarations() throws Exception {
        FloatArray weights = mappedWeights();
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        weights.init(2.0f);
        input.init(3.0f);
        TaskGraph builder = new TaskGraph("snapshot") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("multiply", TestConsumeFromUnifiedMemory::multiply, weights, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        var snapshot = builder.snapshot();
        builder.consumeFromUnifiedMemory(weights);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(snapshot)) {
            plan.execute();
            weights.init(4.0f);
            plan.execute();
            assertEquals(6.0f, output.get(0), 0.0f);
            assertEquals(weights.getNumBytesOfSegmentWithHeader() + input.getNumBytesOfSegmentWithHeader() + output.getNumBytesOfSegmentWithHeader(), plan.getCurrentDeviceMemoryUsage());
        }
    }

    @Test
    public void testBindingsReleaseIndependentlyInEitherOrder() throws Exception {
        requireMetalUnifiedMemory();
        for (boolean releaseFirst : new boolean[] { true, false }) {
            FloatArray weights = mappedWeights();
            weights.init(2.0f);
            FloatArray input = new FloatArray(SIZE);
            input.init(3.0f);
            FloatArray outA = new FloatArray(SIZE);
            FloatArray outB = new FloatArray(SIZE);
            int before = liveMetalBuffers();
            try (TornadoExecutionPlan first = new TornadoExecutionPlan(graph("first", weights, input, outA).snapshot());
                    TornadoExecutionPlan second = new TornadoExecutionPlan(graph("second", weights, input, outB).snapshot())) {
                first.execute();
                assertEquals(before + 1, liveMetalBuffers());
                second.execute();
                assertEquals(before + 2, liveMetalBuffers());
                TornadoExecutionPlan released = releaseFirst ? first : second;
                TornadoExecutionPlan retained = releaseFirst ? second : first;
                released.freeDeviceMemory();
                assertEquals(before + 1, liveMetalBuffers());
                released.freeDeviceMemory();
                assertEquals(before + 1, liveMetalBuffers());
                retained.execute();
                assertEquals(6.0f, (releaseFirst ? outB : outA).get(0), 0.0f);
                released.execute();
                assertEquals(before + 2, liveMetalBuffers());
                assertEquals(6.0f, (releaseFirst ? outA : outB).get(0), 0.0f);
            }
            assertEquals(before, liveMetalBuffers());
            assertEquals(2.0f, weights.get(0), 0.0f);
        }
    }

    @Test
    public void testCloseWithoutAnOutputDownload() throws Exception {
        requireMetalUnifiedMemory();
        FloatArray weights = mappedWeights();
        weights.init(2.0f);
        TaskGraph graph = new TaskGraph("pending").consumeFromUnifiedMemory(weights) //
                .task("multiply", TestConsumeFromUnifiedMemory::multiply, weights, weights, new FloatArray(SIZE));
        int before = liveMetalBuffers();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            assertEquals(before + 1, liveMetalBuffers());
        }
        assertEquals(before, liveMetalBuffers());
        assertEquals(2.0f, weights.get(0), 0.0f);
    }

    @Test
    public void testMetalAcceptsWholePageAutomaticStorage() throws Exception {
        requireMetalUnifiedMemory();
        FloatArray weights = FloatArray.fromSegmentShallow(Arena.ofAuto().allocate(65536, 65536));
        assertTrue(isOnUnifiedMemory(weights));
        weights.init(2.0f);
        FloatArray input = new FloatArray(weights.getSize());
        FloatArray output = new FloatArray(weights.getSize());
        input.init(3.0f);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph("owned", weights, input, output).snapshot())) {
            plan.execute();
            assertEquals(6.0f, output.get(0), 0.0f);
            assertEquals(6.0f, output.get(output.getSize() - 1), 0.0f);
        }
        assertEquals(2.0f, weights.get(0), 0.0f);
    }

    @Test
    public void testUnsupportedDeviceRejected() throws Exception {
        TornadoDevice device = getTornadoRuntime().getDefaultDevice();
        if (device.getTornadoVMBackend() != TornadoVMBackendType.METAL) {
            // Only Metal implements consumeFromUnifiedMemory; CUDA keeps the hooks unimplemented.
            assertFalse(device.isUnifiedMemorySupported());
        }
        if (device.isUnifiedMemorySupported()) {
            assertNotBackend(device.getTornadoVMBackend(), "The device supports consumeFromUnifiedMemory");
        }
        FloatArray weights = mappedWeights();
        assertFalse(device.isOnUnifiedMemory(weights));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph("unsupported", weights, new FloatArray(SIZE), new FloatArray(SIZE)).snapshot())) {
            assertNotOnUnifiedMemory(plan::execute, "consumeFromUnifiedMemory is not supported on");
        }
    }

    @Test
    public void testTransferConflictsInEitherOrder() {
        FloatArray weights = new FloatArray(SIZE);
        assertRejected(() -> new TaskGraph("uploadAfter").consumeFromUnifiedMemory(weights).transferToDevice(DataTransferMode.FIRST_EXECUTION, weights), "consumeFromUnifiedMemory");
        assertRejected(() -> new TaskGraph("uploadBefore").transferToDevice(DataTransferMode.FIRST_EXECUTION, weights).consumeFromUnifiedMemory(weights), "consumeFromUnifiedMemory");
        assertRejected(() -> new TaskGraph("downloadAfter").consumeFromUnifiedMemory(weights).transferToHost(DataTransferMode.UNDER_DEMAND, weights), "consumeFromUnifiedMemory");
        assertRejected(() -> new TaskGraph("consumeAfter").consumeFromUnifiedMemory(weights).consumeFromDevice("producer", weights), "consumeFromUnifiedMemory");
        assertRejected(() -> new TaskGraph("consumeBefore").consumeFromDevice("producer", weights).consumeFromUnifiedMemory(weights), "consumeFromUnifiedMemory");
    }

    @Test
    public void testUnsupportedTypes() {
        assertRejected(() -> new TaskGraph("heap").consumeFromUnifiedMemory(new float[SIZE]), "TornadoNativeArray");
        assertRejected(() -> new TaskGraph("null").consumeFromUnifiedMemory((Object) null), "TornadoNativeArray");
        assertFalse(isOnUnifiedMemory(new float[SIZE]));
    }

    @Test
    public void testDeviceWriteRejected() throws Exception {
        FloatArray weights = new FloatArray(SIZE);
        FloatArray input = new FloatArray(SIZE);
        TaskGraph graph = new TaskGraph("write").consumeFromUnifiedMemory(weights) //
                .task("multiply", TestConsumeFromUnifiedMemory::multiply, weights, input, weights);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            assertRejected(plan::execute, "read-only");
        }
    }

    @Test
    public void testOrdinaryAliasRejectedAcrossGraphs() throws Exception {
        requireUnifiedMemory();
        // Mapped storage passes every storage check, so only the overlap with the alias rejects it.
        FloatArray weights = mappedWeights();
        FloatArray alias = FloatArray.fromSegmentShallow(weights.getSegmentWithHeader());
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        TaskGraph imported = graph("imported", weights, input, output);
        TaskGraph ordinary = new TaskGraph("ordinary").transferToDevice(DataTransferMode.FIRST_EXECUTION, alias) //
                .task("multiply", TestConsumeFromUnifiedMemory::multiply, alias, input, output);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(imported.snapshot(), ordinary.snapshot())) {
            assertRejected(plan::execute, "overlaps an ordinary buffer");
        }
    }

    @Test
    public void testClosedStorageRejected() throws Exception {
        requireUnifiedMemory();
        Arena arena = Arena.ofShared();
        FloatArray weights = FloatArray.fromSegmentShallow(arena.allocate(TornadoNativeArray.ARRAY_HEADER + SIZE * (long) Float.BYTES, 16));
        TaskGraph graph = graph("closed", weights, new FloatArray(SIZE), new FloatArray(SIZE));
        arena.close();
        assertFalse(isOnUnifiedMemory(weights));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            assertNotOnUnifiedMemory(plan::execute, "closed");
        }
    }

    @Test
    public void testBatchRejected() throws Exception {
        FloatArray weights = new FloatArray(SIZE);
        TaskGraph graph = graph("batch", weights, new FloatArray(SIZE), new FloatArray(SIZE));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot()).withBatch("1MB")) {
            assertRejected(plan::execute, "chunking");
        }
    }

    @Test
    public void testReductionRejected() throws Exception {
        FloatArray weights = new FloatArray(SIZE);
        TaskGraph graph = new TaskGraph("reduce").consumeFromUnifiedMemory(weights) //
                .task("sum", TestConsumeFromUnifiedMemory::sum, weights, new FloatArray(1));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            assertRejected(plan::execute, "reduction");
        }
    }

    @Test
    public void testIntraPlanConcurrencyRejected() throws Exception {
        FloatArray weights = new FloatArray(SIZE);
        TaskGraph graph = graph("concurrent", weights, new FloatArray(SIZE), new FloatArray(SIZE));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot()).withIntraPlanConcurrency()) {
            assertRejected(plan::execute, "intra-plan concurrency");
        }
    }

    @Test
    public void testCloseableArenaRejected() throws Exception {
        requireUnifiedMemory();
        try (Arena arena = Arena.ofShared()) {
            FloatArray weights = FloatArray.fromSegmentShallow(arena.allocate(65536, 65536));
            assertFalse(isOnUnifiedMemory(weights));
            TaskGraph graph = graph("closeable", weights, new FloatArray(weights.getSize()), new FloatArray(weights.getSize()));
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                assertNotOnUnifiedMemory(plan::execute, "Arena.ofAuto()");
            }
        }
    }

    @Test
    public void testForeignStorageRejected() throws Exception {
        requireUnifiedMemory();
        FloatArray owner = FloatArray.fromSegmentShallow(Arena.ofAuto().allocate(65536, 65536));
        MemorySegment foreign = FFMSupport.asSegment(owner.getSegmentWithHeader().address(), owner.getNumBytesOfSegmentWithHeader());
        FloatArray weights = FloatArray.fromSegmentShallow(foreign);
        assertFalse(isOnUnifiedMemory(weights));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph("foreign", weights, new FloatArray(weights.getSize()), new FloatArray(weights.getSize())).snapshot())) {
            assertNotOnUnifiedMemory(plan::execute, "foreign/global");
        } finally {
            java.lang.ref.Reference.reachabilityFence(owner);
        }
    }

    @Test
    public void testMetalRejectsUnprovenPageExtent() throws Exception {
        requireMetalUnifiedMemory();
        FloatArray weights = new FloatArray(1);
        assertFalse(isOnUnifiedMemory(weights));
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph("small", weights, new FloatArray(1), new FloatArray(1)).snapshot())) {
            TornadoRuntimeException error = assertThrows(TornadoUnifiedMemoryNotSupported.class, plan::execute);
            assertTrue(error.getMessage().contains("whole pages"), error.getMessage());
            // A retry reports the same reason instead of a follow-on error.
            assertEquals(error.getMessage(), assertThrows(TornadoUnifiedMemoryNotSupported.class, plan::execute).getMessage());
        }
    }
}
