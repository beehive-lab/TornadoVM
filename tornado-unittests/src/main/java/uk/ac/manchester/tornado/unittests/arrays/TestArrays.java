/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V --fast uk.ac.manchester.tornado.unittests.arrays.TestArrays
 * </code>
 * </p>
 */
public class TestArrays extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void addAccumulator(IntArray a, int value) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + value);
        }
    }

    public static void vectorAddDouble(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorAddFloat(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorAddInteger(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorAddIntegerKernelContext(IntArray a, KernelContext context, IntArray b, IntArray c) {
        int idx = context.globalIdx;
        c.set(idx, a.get(idx) + b.get(idx));
    }

    public static void vectorAddLong(LongArray a, LongArray b, LongArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorAddShort(ShortArray a, ShortArray b, ShortArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, (short) (a.get(i) + b.get(i)));
        }
    }

    public static void vectorChars(CharArray a, CharArray b, CharArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, 'f');
        }
    }

    public static void vectorAddByte(ByteArray a, ByteArray b, ByteArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, (byte) (a.get(i) + b.get(i)));
        }
    }

    public static void addChars(CharArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (char) (a.get(i) + b.get(i)));
        }
    }

    public static void initializeSequentialByte(ByteArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, (byte) 21);
        }
    }

    public static void initializeSequential(IntArray a) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, 1);
        }
    }

    public static void initializeToOneParallel(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, 1);
        }
    }

    @Test
    public void testWarmUp() {

        final int N = 128;
        int numKernels = 16;

        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> data.set(idx, idx));

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, data);
        for (int i = 0; i < numKernels; i++) {
            taskGraph.task("t" + i, TestArrays::addAccumulator, data, 1);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph) //
                .withWarmUp() //
                .execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data.get(i));
        }
    }

    @Test
    public void testInitByteArray() {
        // Initialization: there is no copy-in.
        final int N = 128;
        ByteArray data = new ByteArray(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeSequentialByte, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph) //
                .withWarmUp() //
                .execute();

        for (int i = 0; i < N; i++) {
            assertEquals((byte) 21, data.get(i));
        }
    }

    @Test
    public void testInitNotParallel() {
        // Initialization: there is no copy-in.

        final int N = 128;
        IntArray data = new IntArray(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeSequential, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph) //
                .withWarmUp() //
                .execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data.get(i), 0.0001);
        }
    }

    @Test
    public void testInitParallel() {
        // Initialization: there is no copy-in.

        final int N = 128;
        IntArray data = new IntArray(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeToOneParallel, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph) //
                .withWarmUp() //
                .execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data.get(i), 0.0001);
        }
    }

    @Test
    public void testAdd() {

        final int N = 128;
        int numKernels = 8;

        IntArray data = new IntArray(N);

        IntStream.range(0, N).parallel().forEach(idx -> {
            data.set(idx, idx);
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, data);
        for (int i = 0; i < numKernels; i++) {
            taskGraph.task("t" + i, TestArrays::addAccumulator, data, 1);
        }
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlanPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlanPlan.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data.get(i), 0.0001);
        }
    }

    @Test
    public void testVectorAdditionDouble() {
        final int numElements = 4096;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddDouble, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01);
        }
    }

    @Test
    public void testVectorAdditionFloat() {
        final int numElements = 4096;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testVectorAdditionInteger() {
        final int numElements = 4096;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    /**
     * Run multiple times an execution plan built with the Kernel Context API.
     */
    @Test
    public void testVectorAdditionIntegerKernelContext() {
        final int numElements = 4096;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
        });

        KernelContext context = new KernelContext();
        WorkerGrid grid = new WorkerGrid1D(numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", grid);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, context) //
                .task("t0", TestArrays::vectorAddIntegerKernelContext, a, context, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler).execute();

        for (int i = 0; i < 10; i++) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionLong() {
        final int numElements = 4096;
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, i);
            b.set(i, i);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionShort() {
        final int numElements = 4096;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, (short) 20);
            b.set(idx, (short) 34);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddShort, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorChars() {
        final int numElements = 4096;
        CharArray a = new CharArray(numElements);
        CharArray b = new CharArray(numElements);
        CharArray c = new CharArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, 'a');
            b.set(idx, '0');
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorChars, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals('f', c.get(i));
        }
    }

    @Test
    public void testVectorBytes() {
        final int numElements = 4096;
        ByteArray a = new ByteArray(numElements);
        ByteArray b = new ByteArray(numElements);
        ByteArray c = new ByteArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, (byte) 10);
            b.set(idx, (byte) 11);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddByte, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(21, c.get(i));
        }
    }

    /**
     * Inspired by the CUDA Hello World from Computer Graphics:
     *
     *
     * @see <a href=
     *     "http://computer-graphics.se/hello-world-for-cuda.html">http://computer-graphics.se/hello-world-for-cuda.html
     *     </a>
     *
     *     How to run?
     *
     *     <code>
     *     $ tornado-test -V --fast --debug --threadInfo uk.ac.manchester.tornado.unittests.arrays.TestArrays#testVectorCharsMessage
     *     </code>
     */
    @Test
    public void testVectorCharsMessage() {

        CharArray a = new CharArray(12);
        a.set(0, 'h');
        a.set(1, 'e');
        a.set(2, 'l');
        a.set(3, 'l');
        a.set(4, 'o');
        a.set(5, ' ');
        a.set(6, '\0');
        a.set(7, '\0');
        a.set(8, '\0');
        a.set(9, '\0');
        a.set(10, '\0');
        a.set(11, '\0');

        IntArray b = new IntArray(16);
        b.set(0, 15);
        b.set(1, 10);
        b.set(2, 6);
        b.set(3, 0);
        b.set(4, -11);
        b.set(5, 1);
        b.set(6, 0);
        b.set(7, 0);
        b.set(8, 0);
        b.set(9, 0);
        b.set(10, 0);
        b.set(11, 0);
        b.set(12, 0);
        b.set(13, 0);
        b.set(14, 0);
        b.set(15, 0);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::addChars, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals('w', a.get(0));
        assertEquals('o', a.get(1));
        assertEquals('r', a.get(2));
        assertEquals('l', a.get(3));
        assertEquals('d', a.get(4));
        assertEquals('!', a.get(5));
    }
    // CHECKSTYLE:ON

}
