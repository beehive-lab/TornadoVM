/*
 * Copyright (c) 2020-2022, 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.math;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.math.TestTornadoMathCollection
 * </code>
 */
public class TestTornadoMathCollection extends TornadoTestBase {
    public static void testTornadoCos(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.cos(a.get(i)));
        }
    }

    public static void testTornadoAcosh(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.acosh(a.get(i)));
        }
    }

    public static void testTornadoSignum(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.signum(a.get(i)));
        }
    }

    public static void testTornadoCosPI(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.cospi(a.get(i)));
        }
    }

    public static void testTornadoCosPIDouble(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.cospi(a.get(i)));
        }
    }

    public static void testTornadoSinPI(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.sinpi(a.get(i)));
        }
    }

    public static void testTornadoSinPIDouble(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.sinpi(a.get(i)));
        }
    }

    public static void testTornadoSignum(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.signum(a.get(i)));
        }
    }

    public static void testTornadoSin(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.sin(a.get(i)));
        }
    }

    public static void testTornadoAcos(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (TornadoMath.acos(a.get(i))));
        }
    }

    public static void testTornadoAsin(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (TornadoMath.asin(a.get(i))));
        }
    }

    public static void testTornadoAsinh(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (TornadoMath.asinh(a.get(i))));
        }
    }

    public static void testTornadoMin(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.min(a.get(i), 1));
        }
    }

    public static void testTornadoMax(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.max(a.get(i), 10));
        }
    }

    public static void testTornadoSqrt(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
    }

    public static void testTornadoAtan(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.atan(a.get(i)));
        }
    }

    public static void testTornadoAtan(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.atan(a.get(i)));
        }
    }

    public static void testTornadoAtan2(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.atan2(a.get(i), b.get(i)));
        }
    }

    public static void testTornadoAtan2(DoubleArray a, DoubleArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.atan2(a.get(i), b.get(i)));
        }
    }

    public static void testTornadoTan(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.tan(a.get(i)));
        }
    }

    public static void testTornadoTan(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.tan(a.get(i)));
        }
    }

    public static void testTornadoTanh(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.tanh(a.get(i)));
        }
    }

    public static void testTornadoTanh(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.tanh(a.get(i)));
        }
    }

    public static void testTornadoExp(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.exp(a.get(i)));
        }
    }

    public static void testTornadoExp(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.exp(a.get(i)));
        }
    }

    public static void testTornadoClamp(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.clamp(a.get(i), 10, 20));
        }
    }

    public static void testTornadoFract(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.fract(a.get(i)));
        }
    }

    public static void testTornadoLog(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.log(a.get(i)));
        }
    }

    public static void testTornadoLog(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.log(a.get(i)));
        }
    }

    public static void testTornadoLog2(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.log2(a.get(i)));
        }
    }

    public static void testTornadoPI(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.PI());
        }
    }

    public static void testFloor(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (TornadoMath.floor(a.get(i))));
        }
    }

    public static void testClamp(LongArray a, LongArray b) {
        long min = 1;
        long max = 10000;
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, TornadoMath.clamp(a.get(i), min, max));
        }
    }

    public static void testTornadoRadians(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.toRadians(a.get(i)));
        }
    }

    public static void testTornadoRadians(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.toRadians(a.get(i)));
        }
    }

    @Test
    public void testTornadoMathCos() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCos, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoCos(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathAcosh() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoAcosh, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoAcosh(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathCosPI() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCosPI, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoCosPI(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathCosPIDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoCosPIDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCompilerFlags(TornadoVMBackendType.OPENCL, "-cl-opt-disable");
            executionPlan.execute();
        }

        testTornadoCosPIDouble(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathSinPI() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSinPI, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSinPI(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

    @Test
    public void testTornadoMathSinPIDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSinPIDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCompilerFlags(TornadoVMBackendType.OPENCL, "-cl-opt-disable");
            executionPlan.execute();
        }

        testTornadoSinPIDouble(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

    @Test
    public void testTornadoMathSignumFloat() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSignum(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathSignumFloatNaN() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Float.NaN);
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSignum(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathSignumDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSignum(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathSignumDoubleNaN() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Double.NaN);
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSignum, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSignum(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathSin() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSin, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSin(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathAtan() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoAtan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoAtan(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

    @Test
    public void testTornadoMathAtanDouble() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoAtan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCompilerFlags(TornadoVMBackendType.OPENCL, "-cl-opt-disable");
            executionPlan.execute();
        }

        testTornadoAtan(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathTan() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoTan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoTan(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

    @Test
    public void testTornadoMathTanDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoTan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoTan(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathTanh() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoTanh, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoTanh(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathTanhDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoTanh, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoTanh(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathMin() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoMin, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoMin(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathMax() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoMax, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoMax(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathSqrt() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoSqrt, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoSqrt(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathExpDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoExp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoExp(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathExpFloat() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoExp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoExp(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathClamp() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoClamp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoClamp(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathFract() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoFract, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoFract(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathLog2() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoLog2, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoLog2(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathLogDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoLog, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoLog(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testTornadoMathLogFloat() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoLog, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoLog(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathPI() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoPI, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoPI(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testMathFloor() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testFloor, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testFloor(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

    @Test
    public void testMathClamp() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 8192;
        LongArray a = new LongArray(size);
        LongArray b = new LongArray(size);
        LongArray seq = new LongArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, r.nextLong());
            b.set(i, r.nextLong());
            seq.set(i, b.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testClamp, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testClamp(a, seq);
        for (int i = 0; i < size; i++) {
            assertEquals(b.get(i), seq.get(i));
        }
    }

    @Test
    public void testTornadoMathRadians() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoRadians, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoRadians(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testTornadoMathRadiansDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestTornadoMathCollection::testTornadoRadians, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoRadians(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testMathATan2() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray seqA = new FloatArray(size);
        FloatArray seqB = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
            seqA.set(i, a.get(i));
            seqB.set(i, b.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
                .task("t0", TestTornadoMathCollection::testTornadoAtan2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoAtan2(seqA, seqB);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01f);
        }
    }

    @Test
    public void testMathATan2Double() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        DoubleArray a = new DoubleArray(size);
        DoubleArray b = new DoubleArray(size);
        DoubleArray seqA = new DoubleArray(size);
        DoubleArray seqB = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, Math.random());
            b.set(i, Math.random());
            seqA.set(i, a.get(i));
            seqB.set(i, b.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
                .task("t0", TestTornadoMathCollection::testTornadoAtan2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoAtan2(seqA, seqB);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathAcos() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        FloatArray a = new FloatArray(size);
        FloatArray seqA = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, (float) Math.random());
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testTornadoAcos, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTornadoAcos(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01f);
        }
    }

    @Test
    public void testMathASin() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        FloatArray a = new FloatArray(size);
        FloatArray seqA = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, (float) Math.random());
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testTornadoAsin, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        testTornadoAsin(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01f);
        }
    }

    @Test
    public void testMathASinh() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray a = new FloatArray(size);
        FloatArray seqA = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, (float) Math.random());
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestTornadoMathCollection::testTornadoAsinh, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        testTornadoAsinh(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01f);
        }
    }

}
