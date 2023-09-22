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

package uk.ac.manchester.tornado.unittests.math;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run:
 * </p>
 * <code>
 *      tornado-test -V --fast uk.ac.manchester.tornado.unittests.math.TestMath
 * </code>
 */
public class TestMath extends TornadoTestBase {

    public static void testCos(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.cos(a[i]);
        }
    }

    public static void testAtan(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.atan(a[i]);
        }
    }

    public static void testTan(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.tan(a[i]);
        }
    }

    public static void testAtan2(double[] a, double[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.atan2(a[i], b[i]);
        }
    }

    public static void testTanh(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.tanh(a[i]);
        }
    }

    public static void testLog(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.log(a[i]);
        }
    }

    public static void testSqrt(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.sqrt(a[i]);
        }
    }

    public static void testExp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.exp(a[i]);
        }
    }

    public static void testExpDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.exp(a[i]);
        }
    }

    public static void testExpLong(long[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (long) Math.exp(a[i]);
        }
    }

    public static void testExpFloat(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (float) Math.exp(a[i]);
        }
    }

    public static void testPow(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (float) Math.pow(a[i], 2);
        }
    }

    public static void testPowDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.pow(a[i], 2);
        }
    }

    public static void testAcos(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (Math.acos(a[i]));
        }
    }

    public static void testAsin(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (Math.asin(a[i]));
        }
    }

    public static void testAbs(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.abs(a[i]);
        }
    }

    public static void testMin(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = Math.min(a[i], b[i]);
        }
    }

    public static void testMax(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = Math.max(a[i], b[i]);
        }
    }

    public static void testNegate(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = -a[i];
        }
    }

    public static void testRemainder(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = b[i] % a[i];
        }
    }

    public static void testFMA(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + b[i] * a[i];
        }
    }

    public static void testFMA2(float[] a, double[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + b[i] * a[i];
        }
    }

    private static void testSignumFloat(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++)
            a[i] = Math.signum(a[i]);
    }

    private static void testSignumDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++)
            a[i] = Math.signum(a[i]);
    }

    @Test
    public void testMathCos() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testCos, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testCos(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathAtan() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testAtan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testAtan(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathTan() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testTan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testTan(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathTanh() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testTanh, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testTanh(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathLog() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testLog, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testLog(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathSqrt() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testSqrt, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testSqrt(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathExp() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testExp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testExp(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathExpFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testExpFloat, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testExpFloat(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathExpDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testExpDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testExpDouble(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathPowDouble() {
        final int size = 8192;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testPow, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testPow(seq);
        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathPow() {
        final int size = 8192;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testPowDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testPowDouble(seq);
        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathAbs() {
        final int size = 8192;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) -Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testAbs, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testAbs(seq);
        assertArrayEquals(data, seq, 0.01f);
    }

    @Test
    public void testMathMin() {
        final int size = 8192;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] c = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMath::testMin, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testMin(a, b, seq);
        assertArrayEquals(c, seq, 0.01f);
    }

    @Test
    public void testMathMax() {
        final int size = 8192;
        double[] a = new double[size];
        double[] b = new double[size];
        double[] c = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMath::testMax, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testMax(a, b, seq);
        assertArrayEquals(c, seq, 0.01f);
    }

    @Test
    public void testNegate() {
        Random r = new Random();
        final int size = 8192;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] seq = new float[size];

        float min = -10000;
        float max = 10000;

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = min + r.nextFloat() * (max - min);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testNegate, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testNegate(a, seq);
        assertArrayEquals(b, seq, 0.001f);
    }

    @Test
    public void testRem() {
        Random r = new Random();
        final int size = 8192;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] seq = new int[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
            seq[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testRemainder, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testRemainder(a, seq);
        assertArrayEquals(b, seq);
    }

    @Test
    public void testFMA() {
        Random r = new Random();
        final int size = 8192;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            seq[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testFMA, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testFMA(a, seq);

        assertArrayEquals(b, seq, 0.01f);
    }

    @Test
    public void testFMA2() {
        Random r = new Random();
        final int size = 8192;
        float[] a = new float[size];
        double[] b = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            seq[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testFMA2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testFMA2(a, seq);

        assertArrayEquals(b, seq, 0.01f);
    }

    @Test
    public void testMathATan2() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        double[] a = new double[size];
        double[] b = new double[size];
        double[] seqA = new double[size];
        double[] seqB = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = Math.random();
            b[i] = Math.random();
            seqA[i] = a[i];
            seqB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
                .task("t0", TestMath::testAtan2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testAtan2(seqA, seqB);

        assertArrayEquals(a, seqA, 0.01);
    }

    @Test
    public void testMathAcos() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        double[] a = new double[size];
        double[] seqA = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = Math.random();
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testAcos, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testAcos(seqA);

        assertArrayEquals(a, seqA, 0.01);
    }

    @Test
    public void testMathASin() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        double[] a = new double[size];
        double[] seqA = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = Math.random();
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testAsin, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testAsin(seqA);

        assertArrayEquals(a, seqA, 0.01);
    }

    @Test
    public void testMathSignumFloat() {
        Random r = new Random();
        final int size = 128;
        float[] a = new float[size];
        float[] seqA = new float[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextFloat() * (r.nextBoolean() ? -1 : 1);
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumFloat, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testSignumFloat(seqA);

        assertArrayEquals(seqA, a, 0.01f);
    }

    @Test
    public void testMathSignumFloatNaN() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        float[] a = new float[size];
        float[] seqA = new float[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = Float.NaN;
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumFloat, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testSignumFloat(seqA);

        assertArrayEquals(seqA, a, 0.01f);
    }

    @Test
    public void testMathSignumDouble() {
        Random r = new Random();
        final int size = 128;
        double[] a = new double[size];
        double[] seqA = new double[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextDouble() * (r.nextBoolean() ? -1 : 1);
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumDouble, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testSignumDouble(seqA);

        assertArrayEquals(seqA, a, 0.01f);
    }

    @Test
    public void testMathSignumDoubleNaN() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        double[] a = new double[size];
        double[] seqA = new double[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = Double.NaN;
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumDouble, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        testSignumDouble(seqA);

        assertArrayEquals(seqA, a, 0.01f);
    }

}
