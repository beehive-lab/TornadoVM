/*
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run:
 * </p>
 * <code>
 *      tornado-test -V uk.ac.manchester.tornado.unittests.math.TestTornadoMathCollection
 * </code>
 */
public class TestTornadoMathCollection extends TornadoTestBase {
    public static void testTornadoCos(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.cos(a[i]);
        }
    }

    public static void testTornadoCos(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.cos(a[i]);
        }
    }

    public static void testTornadoCosPI(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.cospi(a[i]);
        }
    }

    public static void testTornadoCosPIDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.cospi(a[i]);
        }
    }

    public static void testTornadoSinPI(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.sinpi(a[i]);
        }
    }

    public static void testTornadoSinPIDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.sinpi(a[i]);
        }
    }

    public static void testTornadoSignum(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.signum(a[i]);
        }
    }

    public static void testTornadoSignum(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.signum(a[i]);
        }
    }

    public static void testTornadoSin(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.sin(a[i]);
        }
    }

    public static void testTornadoSin(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.sin(a[i]);
        }
    }

    public static void testTornadoAcos(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (TornadoMath.acos(a[i]));
        }
    }

    public static void testTornadoAcosDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (TornadoMath.acos(a[i]));
        }
    }

    public static void testTornadoAsin(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (TornadoMath.asin(a[i]));
        }
    }

    public static void testTornadoMin(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.min(a[i], 1);
        }
    }

    public static void testTornadoAsinDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (TornadoMath.asin(a[i]));
        }
    }

    public static void testTornadoMax(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.max(a[i], 10);
        }
    }

    public static void testTornadoSqrt(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.sqrt(a[i]);
        }
    }

    public static void testTornadoAtan(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.atan(a[i]);
        }
    }

    public static void testTornadoAtan2(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.atan2(a[i], b[i]);
        }
    }

    public static void testTornadoTan(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.tan(a[i]);
        }
    }

    public static void testTornadoTanh(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.tanh(a[i]);
        }
    }

    public static void testTornadoExp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.exp(a[i]);
        }
    }

    public static void testTornadoExp(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.exp(a[i]);
        }
    }

    public static void testTornadoClamp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.clamp(a[i], 10, 20);
        }
    }

    public static void testTornadoFract(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.fract(a[i]);
        }
    }

    public static void testTornadoLog(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.log(a[i]);
        }
    }

    public static void testTornadoLog(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.log(a[i]);
        }
    }

    public static void testTornadoLog2(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.log2(a[i]);
        }
    }

    public static void testTornadoPI(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.PI();
        }
    }

    public static void testFloor(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (TornadoMath.floor(a[i]));
        }
    }

    public static void testClamp(long[] a, long[] b) {
        long min = 1;
        long max = 10000;
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = TornadoMath.clamp(a[i], min, max);
        }
    }

    public static void testTornadoRadians(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.toRadians(a[i]);
        }
    }

    @Test
    public void testTornadoMathCos() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCos, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoCos(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathCosDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCos, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoCos(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testTornadoMathCosPI() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCosPI, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoCosPI(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathCosPIDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCosPIDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoCosPIDouble(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathSinPI() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSinPI, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSinPI(seq);

        assertArrayEquals(data, seq, 0.01f);
    }

    @Test
    public void testTornadoMathSinPIDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSinPIDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSinPIDouble(seq);

        assertArrayEquals(data, seq, 0.01f);
    }

    @Test
    public void testTornadoMathSignumFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSignum(seq);

        assertArrayEquals(seq, data, 0.01f);

    }

    @Test
    public void testTornadoMathSignumFloatNaN() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Float.NaN;
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSignum(seq);

        assertArrayEquals(seq, data, 0.01f);

    }

    @Test
    public void testTornadoMathSignumDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSignum(seq);

        assertArrayEquals(seq, data, 0.01f);

    }

    @Test
    public void testTornadoMathSignumDoubleNaN() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Double.NaN;
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSignum(seq);

        assertArrayEquals(seq, data, 0.01f);

    }

    @Test
    public void testTornadoMathSinFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSin, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSin(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathSinDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSin, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSin(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathAtan() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoAtan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoAtan(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathTan() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoTan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoTan(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathTanh() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoTanh, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoTanh(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathMin() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoMin, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoMin(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathMax() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoMax, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoMax(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathSqrt() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSqrt, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoSqrt(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathExpDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoExp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoExp(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathExpFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoExp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoExp(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathClamp() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoClamp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoClamp(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathFract() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoFract, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoFract(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathLog2() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoLog2, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoLog2(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathLogDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoLog, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoLog(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathLogFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoLog, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoLog(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathPI() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoPI, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoPI(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathFloor() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testFloor, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testFloor(seq);

        assertArrayEquals(data, seq, 0.01f);
    }

    @Test
    public void testMathClamp() {
        Random r = new Random();
        final int size = 8192;
        long[] a = new long[size];
        long[] b = new long[size];
        long[] seq = new long[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = r.nextLong();
            b[i] = r.nextLong();
            seq[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testClamp, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testClamp(a, seq);
        assertArrayEquals(b, seq);
    }

    @Test
    public void testTornadoMathRadians() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoRadians, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoRadians(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathATan2() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] seqA = new float[size];
        float[] seqB = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
            seqA[i] = a[i];
            seqB[i] = b[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
                .task("t0", TestTornadoMathCollection::testTornadoAtan2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoAtan2(seqA, seqB);

        assertArrayEquals(a, seqA, 0.01f);
    }

    @Test
    public void testMathAcosFloat() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        float[] a = new float[size];
        float[] seqA = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testTornadoAcos, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoAcos(seqA);

        assertArrayEquals(a, seqA, 0.01f);
    }

    @Test
    public void testMathASinFloat() {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        float[] a = new float[size];
        float[] seqA = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            seqA[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testTornadoAsin, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoAsin(seqA);

        assertArrayEquals(a, seqA, 0.01f);
    }

    @Test
    public void testMathAcosDouble() {
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
                .task("t0", TestTornadoMathCollection::testTornadoAcosDouble, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoAcosDouble(seqA);

        assertArrayEquals(a, seqA, 0.01f);
    }

    @Test
    public void testMathASinDobule() {
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
                .task("t0", TestTornadoMathCollection::testTornadoAsinDouble, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        new TornadoExecutionPlan(immutableTaskGraph).execute();

        testTornadoAsinDouble(seqA);

        assertArrayEquals(a, seqA, 0.01f);
    }

}
