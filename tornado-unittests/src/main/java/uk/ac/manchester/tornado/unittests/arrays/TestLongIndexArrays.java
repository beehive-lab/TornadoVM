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
import static org.junit.Assert.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.tools.Exceptions.UnsupportedConfigurationException;

/**
 * Native arrays indexed with {@code long}, including arrays with more than {@link Integer#MAX_VALUE} elements.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestLongIndexArrays
 * </code>
 * </p>
 *
 * <p>
 * {@link #testFloatArrayBeyondIntRange} needs more than 8 GB on the host and on the device, for example:
 * <code>
 * tornado-test -V --jvm="-Xmx12g -Dtornado.device.memory=12GB" uk.ac.manchester.tornado.unittests.arrays.TestLongIndexArrays
 * </code>
 * </p>
 */
public class TestLongIndexArrays extends TornadoTestBase {

    private static final int SIZE = 1024;
    private static final int LOCAL_SIZE = 64;

    /** Elements accessed on each side of the {@link Integer#MAX_VALUE} boundary by the large-array tests. */
    private static final int WINDOW = 4096;

    /** A size just beyond what an {@code int} can index. */
    private static final long LARGE_SIZE = (1L << 31) + WINDOW;

    /** First element touched by the large-array tests: the window crosses index {@link Integer#MAX_VALUE}. */
    private static final long LARGE_BASE = (1L << 31) - WINDOW;

    public static void addOneFloat(KernelContext context, FloatArray input, FloatArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, input.get(i) + 1.0f);
        }
    }

    public static void addOneDouble(KernelContext context, DoubleArray input, DoubleArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, input.get(i) + 1.0);
        }
    }

    public static void addOneInt(KernelContext context, IntArray input, IntArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, input.get(i) + 1);
        }
    }

    public static void addOneLong(KernelContext context, LongArray input, LongArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, input.get(i) + 1L);
        }
    }

    public static void addOneShort(KernelContext context, ShortArray input, ShortArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, (short) (input.get(i) + 1));
        }
    }

    public static void addOneChar(KernelContext context, CharArray input, CharArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, (char) (input.get(i) + 1));
        }
    }

    public static void addOneByte(KernelContext context, ByteArray input, ByteArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, (byte) (input.get(i) + 1));
        }
    }

    public static void addOneInt8(KernelContext context, Int8Array input, Int8Array output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, (byte) (input.get(i) + 1));
        }
    }

    public static void addOneHalf(KernelContext context, HalfFloatArray input, HalfFloatArray output) {
        int id = context.globalIdx;
        if (id < input.getSizeLong()) {
            long i = id;
            output.set(i, HalfFloat.add(input.get(i), new HalfFloat(1.0f)));
        }
    }

    /** Reads a half float at a {@code long} byte offset of a {@link ByteArray} and stores it, plus one, at the same offset. */
    public static void addOneHalfInBytes(KernelContext context, ByteArray data) {
        int id = context.globalIdx;
        if (id < data.getSizeLong() / 2) {
            long byteIndex = 2L * id;
            data.setHalfFloat(byteIndex, HalfFloat.add(data.getHalfFloat(byteIndex), new HalfFloat(1.0f)));
        }
    }

    /** Scales each row of a row-major matrix, addressing elements with a {@code long} offset built from the thread index. */
    public static void scaleRows(KernelContext context, HalfFloatArray data, int rowLength) {
        int row = context.globalIdx;
        long start = (long) row * rowLength;
        if (start < data.getSizeLong()) {
            for (int j = 0; j < rowLength; j++) {
                data.set(start + j, HalfFloat.mult(data.get(start + j), new HalfFloat(0.5f)));
            }
        }
    }

    /** Increments {@code WINDOW * 2} elements starting at {@code base}, which can be beyond {@link Integer#MAX_VALUE}. */
    public static void addOneFromBase(KernelContext context, ByteArray data, long base) {
        int id = context.globalIdx;
        long i = base + id;
        if (i < data.getSizeLong()) {
            data.set(i, (byte) (data.get(i) + 1));
        }
    }

    /** Float variant of {@link #addOneFromBase}. */
    public static void addOneFromBaseFloat(KernelContext context, FloatArray data, long base) {
        int id = context.globalIdx;
        long i = base + id;
        if (i < data.getSizeLong()) {
            data.set(i, data.get(i) + 1.0f);
        }
    }

    /** Runs task {@code s0.t0} of the graph with {@code threads} threads in work-groups of {@link #LOCAL_SIZE}. */
    private static void run(TaskGraph taskGraph, int threads) throws TornadoExecutionPlanException {
        WorkerGrid1D worker = new WorkerGrid1D(threads);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }
    }

    /**
     * Skips the test when the device running task {@code s0.t0}, or its TornadoVM device heap, cannot hold an allocation of {@code bytes}.
     */
    private static void requireDeviceMemory(long bytes) {
        String[] backendAndDevice = System.getProperty("s0.t0.device", System.getProperty("tornado.unittests.device", "0:0")).split(":");
        TornadoDevice device = getTornadoRuntime().getBackend(Integer.parseInt(backendAndDevice[0])).getDevice(Integer.parseInt(backendAndDevice[1]));
        long heap = device.getDeviceContext().getMemoryManager().getHeapSize();
        if (device.getMaxAllocMemory() <= bytes || heap <= bytes) {
            throw new UnsupportedConfigurationException(String.format("Not enough device memory to run the test: needs %d bytes in one allocation (max allocation %d, device heap %d)", bytes,
                    device.getMaxAllocMemory(), heap));
        }
    }

    @Test
    public void testFloatLongIndex() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, i * 0.5f);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneFloat, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i * 0.5f + 1.0f, output.get(i), DELTA);
        }
    }

    @Test
    public void testDoubleLongIndex() throws TornadoExecutionPlanException {
        DoubleArray input = new DoubleArray(SIZE);
        DoubleArray output = new DoubleArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, i * 0.25);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneDouble, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i * 0.25 + 1.0, output.get(i), DELTA);
        }
    }

    @Test
    public void testIntLongIndex() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(SIZE);
        IntArray output = new IntArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, (int) i * 3);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneInt, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i * 3 + 1, output.get(i));
        }
    }

    @Test
    public void testLongLongIndex() throws TornadoExecutionPlanException {
        LongArray input = new LongArray(SIZE);
        LongArray output = new LongArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, i << 33);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneLong, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(((long) i << 33) + 1L, output.get(i));
        }
    }

    @Test
    public void testShortLongIndex() throws TornadoExecutionPlanException {
        ShortArray input = new ShortArray(SIZE);
        ShortArray output = new ShortArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, (short) i);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneShort, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals((short) (i + 1), output.get(i));
        }
    }

    @Test
    public void testCharLongIndex() throws TornadoExecutionPlanException {
        CharArray input = new CharArray(SIZE);
        CharArray output = new CharArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, (char) ('a' + (i % 20)));
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneChar, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals((char) ('a' + (i % 20) + 1), output.get(i));
        }
    }

    @Test
    public void testByteLongIndex() throws TornadoExecutionPlanException {
        ByteArray input = new ByteArray(SIZE);
        ByteArray output = new ByteArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, (byte) i);
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneByte, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals((byte) (i + 1), output.get(i));
        }
    }

    @Test
    public void testInt8LongIndex() throws TornadoExecutionPlanException {
        Int8Array input = new Int8Array(SIZE);
        Int8Array output = new Int8Array(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, (byte) (i % 100));
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneInt8, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals((byte) (i % 100 + 1), output.get(i));
        }
    }

    @Test
    public void testHalfFloatLongIndex() throws TornadoExecutionPlanException {
        HalfFloatArray input = new HalfFloatArray(SIZE);
        HalfFloatArray output = new HalfFloatArray(SIZE);
        for (long i = 0; i < SIZE; i++) {
            input.set(i, new HalfFloat((float) (i % 64)));
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestLongIndexArrays::addOneHalf, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals((i % 64) + 1.0f, output.get(i).getFloat32(), DELTA);
        }
    }

    @Test
    public void testHalfFloatInByteArrayLongIndex() throws TornadoExecutionPlanException {
        ByteArray data = new ByteArray(2 * SIZE);
        for (long i = 0; i < SIZE; i++) {
            data.setHalfFloat(2 * i, new HalfFloat((float) (i % 32)));
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLongIndexArrays::addOneHalfInBytes, new KernelContext(), data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        run(taskGraph, SIZE);
        for (int i = 0; i < SIZE; i++) {
            assertEquals((i % 32) + 1.0f, data.getHalfFloat(2 * i).getFloat32(), DELTA);
        }
    }

    @Test
    public void testRowsWithLongOffsets() throws TornadoExecutionPlanException {
        final int rows = SIZE;
        final int rowLength = 16;
        HalfFloatArray data = new HalfFloatArray((long) rows * rowLength);
        for (long i = 0; i < data.getSizeLong(); i++) {
            data.set(i, new HalfFloat((float) (i % 50)));
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLongIndexArrays::scaleRows, new KernelContext(), data, rowLength) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        run(taskGraph, rows);
        for (int i = 0; i < rows * rowLength; i++) {
            assertEquals((i % 50) * 0.5f, data.get(i).getFloat32(), DELTA);
        }
    }

    @Test
    public void testHostLongAccessors() {
        FloatArray floats = new FloatArray(16L);
        for (long i = 0; i < floats.getSizeLong(); i++) {
            floats.set(i, i);
        }
        assertEquals(16, floats.getSize());
        assertEquals(16L, floats.getSizeLong());
        assertEquals(7.0f, floats.get(7L), 0.0f);
        assertEquals(7.0f, floats.get(7), 0.0f);

        FloatArray slice = floats.slice(4L, 8L);
        assertEquals(8L, slice.getSizeLong());
        assertEquals(4.0f, slice.get(0L), 0.0f);
        assertThrows(IllegalArgumentException.class, () -> floats.slice(10L, 8L));

        FloatArray concat = FloatArray.concat(floats, slice);
        assertEquals(24L, concat.getSizeLong());
        assertEquals(11.0f, concat.get(23L), 0.0f);
    }

    @Test
    public void testHeaderStoresLongSize() {
        IntArray array = new IntArray(1000);
        assertEquals(1000L, array.getSegmentWithHeader().get(ValueLayout.JAVA_LONG, 0));
    }

    @Test
    public void testNegativeNumberOfElements() {
        assertThrows(IllegalArgumentException.class, () -> new FloatArray(-1));
        assertThrows(IllegalArgumentException.class, () -> new FloatArray(-1L));
    }

    /**
     * A shallow array over a segment that reports more than {@link Integer#MAX_VALUE} elements. Only the header is written, so the backing allocation stays small.
     */
    @Test
    public void testSizeBeyondIntRange() {
        long elements = (1L << 32) + 1;
        MemorySegment segment = Arena.ofAuto().allocate(64).reinterpret(TornadoNativeArray.ARRAY_HEADER + elements * Short.BYTES);
        HalfFloatArray array = HalfFloatArray.fromSegmentShallow(segment);
        assertEquals(elements, array.getSizeLong());
        assertEquals(elements, segment.get(ValueLayout.JAVA_LONG, 0));
        assertThrows(IllegalStateException.class, array::getSize);
    }

    @Test
    public void testByteArrayBeyondIntRange() throws TornadoExecutionPlanException {
        requireDeviceMemory(LARGE_SIZE + TornadoNativeArray.ARRAY_HEADER);
        ByteArray data = new ByteArray(LARGE_SIZE);
        for (long i = LARGE_BASE; i < LARGE_SIZE; i++) {
            data.set(i, (byte) (i & 0x3F));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLongIndexArrays::addOneFromBase, new KernelContext(), data, LARGE_BASE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        run(taskGraph, 2 * WINDOW);

        assertEquals(LARGE_SIZE, data.getSizeLong());
        for (long i = LARGE_BASE; i < LARGE_SIZE; i++) {
            assertEquals("element " + i, (byte) ((i & 0x3F) + 1), data.get(i));
        }
        // Elements before the window are untouched
        assertEquals(0, data.get(LARGE_BASE - 1));
    }

    @Test
    public void testFloatArrayBeyondIntRange() throws TornadoExecutionPlanException {
        long bytes = LARGE_SIZE * Float.BYTES + TornadoNativeArray.ARRAY_HEADER;
        requireDeviceMemory(bytes);
        FloatArray data;
        try {
            data = new FloatArray(LARGE_SIZE);
        } catch (OutOfMemoryError e) {
            throw new UnsupportedConfigurationException("Not enough host memory to run the test: needs " + bytes + " bytes");
        }
        for (long i = LARGE_BASE; i < LARGE_SIZE; i++) {
            data.set(i, i - LARGE_BASE);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestLongIndexArrays::addOneFromBaseFloat, new KernelContext(), data, LARGE_BASE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        run(taskGraph, 2 * WINDOW);

        for (long i = LARGE_BASE; i < LARGE_SIZE; i++) {
            assertEquals("element " + i, (i - LARGE_BASE) + 1.0f, data.get(i), 0.0f);
        }
    }
}
