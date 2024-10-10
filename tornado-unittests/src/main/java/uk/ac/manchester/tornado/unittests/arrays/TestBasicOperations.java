/*
 * Copyright (c) 2020-2022 APT Group, Department of Computer Science,
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

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
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
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestBasicOperations
 * </code>
 * </p>
 */
public class TestBasicOperations extends TornadoTestBase {

    public static void vectorAddDouble(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorSubDouble(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
        }
    }

    public static void vectorMulDouble(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    public static void vectorDivDouble(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) / b.get(i));
        }
    }

    public static void vectorAddFloat(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorSubFloat(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
        }
    }

    public static void vectorMulFloat(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    public static void vectorDivFloat(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) / b.get(i));
        }
    }

    public static void vectorAddInteger(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorSubInteger(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
        }
    }

    public static void vectorMulInteger(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    public static void vectorDivInteger(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) / b.get(i));
        }
    }

    public static void vectorAddLong(LongArray a, LongArray b, LongArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorSubLong(LongArray a, LongArray b, LongArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
        }
    }

    public static void vectorMulLong(LongArray a, LongArray b, LongArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    public static void vectorDivLong(LongArray a, LongArray b, LongArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) / b.get(i));
        }
    }

    public static void vectorAddShort(ShortArray a, ShortArray b, ShortArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, (short) (a.get(i) + b.get(i)));
        }
    }

    public static void vectorSubShort(ShortArray a, ShortArray b, ShortArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, (short) (a.get(i) - b.get(i)));
        }
    }

    public static void vectorMulShort(ShortArray a, ShortArray b, ShortArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, (short) (a.get(i) * b.get(i)));
        }
    }

    public static void vectorDivShort(ShortArray a, ShortArray b, ShortArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, (short) (a.get(i) / b.get(i)));
        }
    }

    public static void vectorAddChar(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = (char) (a[i] + b[i]);
        }
    }

    public static void vectorSubChar(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = (char) (a[i] - b[i]);
        }
    }

    public static void vectorMulChar(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = (char) (a[i] * b[i]);
        }
    }

    public static void vectorDivChar(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = (char) (a[i] / b[i]);
        }
    }

    @Test
    public void testVectorAdditionDouble() {
        final int numElements = 32;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorAddDouble, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01);
        }
    }

    @Test
    public void testVectorSubtractionDouble() {
        final int numElements = 32;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorSubDouble, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) - b.get(i), c.get(i), 0.01);
        }
    }

    @Test
    public void testVectorMultiplicationDouble() {
        final int numElements = 32;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorMulDouble, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) * b.get(i), c.get(i), 0.01);
        }
    }

    @Test
    public void testVectorDivisionDouble() {
        final int numElements = 32;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorDivDouble, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) / b.get(i), c.get(i), 0.01);
        }
    }

    @Test
    public void testVectorAdditionFloat() {
        final int numElements = 32;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorAddFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testVectorSubtractionFloat() {
        final int numElements = 32;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorSubFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) - b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testVectorMultiplicationFloat() {
        final int numElements = 32;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorMulFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) * b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testVectorDivisionFloat() {
        final int numElements = 32;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorDivFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) / b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testVectorAdditionInteger() {
        final int numElements = 32;
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
                .task("t0", TestBasicOperations::vectorAddInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorSubtractionInteger() {
        final int numElements = 32;
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
                .task("t0", TestBasicOperations::vectorSubInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) - b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorMultiplicationInteger() {
        final int numElements = 32;
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
                .task("t0", TestBasicOperations::vectorMulInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) * b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorDivisionInteger() {
        final int numElements = 32;
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
                .task("t0", TestBasicOperations::vectorDivInteger, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) / b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionLong() {
        final int numElements = 32;
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, r.nextLong());
            b.set(i, r.nextLong());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorAddLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorSubtractionLong() {
        final int numElements = 32;
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, r.nextLong());
            b.set(i, r.nextLong());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorSubLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) - b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorMultiplicationLong() {
        final int numElements = 32;
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, r.nextLong());
            b.set(i, r.nextLong());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorMulLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) * b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorDivisionLong() {
        final int numElements = 32;
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        Random r = new Random();
        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, r.nextLong());
            b.set(i, r.nextLong());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorDivLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) / b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionShort() {
        final int numElements = 32;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, (short) 20);
            b.set(idx, (short) 34);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorAddShort, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((short) (a.get(i) + b.get(i)), c.get(i));
        }
    }

    @Test
    public void testVectorSubtractionShort() {
        final int numElements = 32;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, (short) 20);
            a.set(idx, (short) 34);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorSubShort, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((short) (a.get(i) - b.get(i)), c.get(i));
        }
    }

    @Test
    public void testVectorMultiplicationShort() {
        final int numElements = 32;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, (short) 20);
            a.set(idx, (short) 34);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorMulShort, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((short) (a.get(i) * b.get(i)), c.get(i));
        }
    }

    @Test
    public void testVectorDivisionShort() {
        final int numElements = 32;
        ShortArray a = new ShortArray(numElements);
        ShortArray b = new ShortArray(numElements);
        ShortArray c = new ShortArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a.set(idx, (short) 20);
            b.set(idx, (short) 34);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorDivShort, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals((short) (a.get(i) / b.get(i)), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionChar() {
        final int numElements = 32;
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 20;
            b[idx] = 34;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorAddChar, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((char) (a[i] + b[i]), c[i]);
        }
    }

    @Test
    public void testVectorSubtractionChar() {
        final int numElements = 32;
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 20;
            b[idx] = 34;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorSubChar, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((char) (a[i] - b[i]), c[i]);
        }
    }

    @Test
    public void testVectorMultiplicationChar() {
        final int numElements = 32;
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 20;
            b[idx] = 34;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorMulChar, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((char) (a[i] * b[i]), c[i]);
        }
    }

    @Test
    public void testVectorDivisionChar() {
        final int numElements = 32;
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 20;
            b[idx] = 34;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestBasicOperations::vectorDivChar, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < c.length; i++) {
            assertEquals((char) (a[i] / b[i]), c[i]);
        }
    }
}
