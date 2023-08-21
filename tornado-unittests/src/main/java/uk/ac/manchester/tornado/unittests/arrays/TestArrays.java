/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.data.nativetypes.DoubleArray;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;
import uk.ac.manchester.tornado.api.data.nativetypes.IntArray;
import uk.ac.manchester.tornado.api.data.nativetypes.LongArray;
import uk.ac.manchester.tornado.api.data.nativetypes.ShortArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 *     tornado-test -V --fast uk.ac.manchester.tornado.unittests.arrays.TestArrays
 * </code>
 * </p>
 */
public class TestArrays extends TornadoTestBase {

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

    public static void vectorChars(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 'f';
        }
    }

    public static void vectorAddByte(byte[] a, byte[] b, byte[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = (byte) (a[i] + b[i]);
        }
    }

    public static void addChars(char[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += b[i];
        }
    }

    public static void initializeSequentialByte(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) 21;
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

        IntStream.range(0, N).parallel().forEach(idx -> {
            data.set(idx, idx);
        });

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
        byte[] data = new byte[N];

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeSequentialByte, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph) //
                .withWarmUp() //
                .execute();

        for (int i = 0; i < N; i++) {
            assertEquals((byte) 21, data[i]);
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
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 'a';
            b[idx] = '0';
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorChars, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (char value : c) {
            assertEquals('f', value);
        }
    }

    @Test
    public void testVectorBytes() {
        final int numElements = 4096;
        byte[] a = new byte[numElements];
        byte[] b = new byte[numElements];
        byte[] c = new byte[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 10;
            b[idx] = 11;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddByte, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (byte value : c) {
            assertEquals(21, value);
        }
    }

    /**
     * Inspired by the CUDA Hello World from Computer Graphics:
     *
     *
     * @see <a href=
     *      "http://computer-graphics.se/hello-world-for-cuda.html">http://computer-graphics.se/hello-world-for-cuda.html
     *      </a>
     *
     *      How to run?
     *
     *      <code>
     *      $ tornado-test.py -V --fast --debug --threadInfo uk.ac.manchester.tornado.unittests.arrays.TestArrays#testVectorCharsMessage
     *      </code>
     */
    @Test
    public void testVectorCharsMessage() {
        char[] a = new char[] { 'h', 'e', 'l', 'l', 'o', ' ', '\0', '\0', '\0', '\0', '\0', '\0' };
        int[] b = new int[] { 15, 10, 6, 0, -11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::addChars, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals('w', a[0]);
        assertEquals('o', a[1]);
        assertEquals('r', a[2]);
        assertEquals('l', a[3]);
        assertEquals('d', a[4]);
        assertEquals('!', a[5]);
    }
}
