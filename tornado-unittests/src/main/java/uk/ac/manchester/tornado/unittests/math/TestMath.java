/*
 * Copyright (c) 2013-2022, 2024, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.math.TestMath
 * </code>
 */
public class TestMath extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void testCos(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.cos(a.get(i)));
        }
    }

    public static void testAtan(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.atan(a.get(i)));
        }
    }

    public static void testTan(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.tan(a.get(i)));
        }
    }

    public static void testAtan2(DoubleArray a, DoubleArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.atan2(a.get(i), b.get(i)));
        }
    }

    public static void testTanh(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.tanh(a.get(i)));
        }
    }

    public static void testLog(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.log(a.get(i)));
        }
    }

    public static void testSqrt(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.sqrt(a.get(i)));
        }
    }

    public static void testExp(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.exp(a.get(i)));
        }
    }

    public static void testExpDouble(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.exp(a.get(i)));
        }
    }

    public static void testExpLong(LongArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (long) Math.exp(a.get(i)));
        }
    }

    public static void testExpFloat(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (float) Math.exp(a.get(i)));
        }
    }

    public static void testPow(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (float) Math.pow(2.0, a.get(i)));
        }
    }

    public static void testPowDouble(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.pow(a.get(i), 2));
        }
    }

    public static void testAcos(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (Math.acos(a.get(i))));
        }
    }

    public static void testAsin(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, (Math.asin(a.get(i))));
        }
    }

    public static void testAbs(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, Math.abs(a.get(i)));
        }
    }

    public static void testMin(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, Math.min(a.get(i), b.get(i)));
        }
    }

    public static void testMax(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            c.set(i, Math.max(a.get(i), b.get(i)));
        }
    }

    public static void testNegate(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, -a.get(i));
        }
    }

    public static void testRemainder(IntArray a, IntArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, b.get(i) % a.get(i));
        }
    }

    public static void testFMA(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + b.get(i) * a.get(i));
        }
    }

    public static void testFMA2(FloatArray a, DoubleArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + b.get(i) * a.get(i));
        }
    }

    private static void testSignumFloat(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++)
            a.set(i, Math.signum(a.get(i)));
    }

    private static void testSignumDouble(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++)
            a.set(i, Math.signum(a.get(i)));
    }

    public static void testCeil(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, TornadoMath.ceil(a.get(i)));
        }
    }

    /**
     * Test that verifies the InverseSquareRootPhase optimization replaces 1.0/sqrt(x) with rsqrt(x).
     */
    public static void testInverseSquareRootJavaMath(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            // This pattern (1.0 / Math.sqrt(x)) should be recognized and optimized to rsqrt(x)
            a.set(i, 1.0 / Math.sqrt(a.get(i)));
        }
    }

    public static void testInverseSquareRootTornadoMath(DoubleArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            // This pattern (1.0 / Math.sqrt(x)) should be recognized and optimized to rsqrt(x)
            a.set(i, 1.0 / TornadoMath.sqrt(a.get(i)));
        }
    }


    @Test
    public void testOptimizedInverseSquareRoot() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        // Initialize with positive random values to avoid sqrt of negative numbers
        IntStream.range(0, size).parallel().forEach(i -> {
            double value = Math.random() + 0.1; // Ensure positive values
            data.set(i, value);
            seq.set(i, value);
        });

        // Create and execute the task graph that should trigger the optimization
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data)
                .task("t0", TestMath::testInverseSquareRootTornadoMath, data)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            // Execute the optimized version
            executionPlan.execute();
        }

        // Execute the sequential version for comparison
        testInverseSquareRootTornadoMath(seq);

        // Compare results
        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testOptimizedInverseSquareRootJavaMath() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        // Initialize with positive random values to avoid sqrt of negative numbers
        IntStream.range(0, size).parallel().forEach(i -> {
            double value = Math.random() + 0.1; // Ensure positive values
            data.set(i, value);
            seq.set(i, value);
        });

        // Create and execute the task graph that should trigger the optimization
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data)
                .task("t0", TestMath::testInverseSquareRootJavaMath, data)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            // Execute the optimized version
            executionPlan.execute();
        }

        // Execute the sequential version for comparison
        testInverseSquareRootJavaMath(seq);

        // Compare results
        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }
    }


    @Test
    public void testMathCos() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testCos, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testCos(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testMathAtan() throws TornadoExecutionPlanException {
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
                .task("t0", TestMath::testAtan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCompilerFlags(TornadoVMBackendType.OPENCL, "-cl-opt-disable");
            executionPlan.execute();
        }

        testAtan(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testMathTan() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testTan, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTan(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testMathTanh() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testTanh, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testTanh(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testMathLog() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testLog, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testLog(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testMathSqrt() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testSqrt, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testSqrt(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testMathExp() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testExp, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testExp(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01);
        }

    }

    @Test
    public void testMathExpFloat() throws TornadoExecutionPlanException {
        final int size = 128;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testExpFloat, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testExpFloat(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testMathExpDouble() throws TornadoExecutionPlanException {
        final int size = 128;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testExpDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testExpDouble(seq);

        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testMathPowDouble() throws TornadoExecutionPlanException {
        final int size = 32;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).sequential().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testPow, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testPow(seq);
        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testMathPow() throws TornadoExecutionPlanException {
        final int size = 8192;
        DoubleArray data = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testPowDouble, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        testPowDouble(seq);
        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }

    }

    @Test
    public void testMathAbs() throws TornadoExecutionPlanException {
        final int size = 8192;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            data.set(i, (float) -Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testAbs, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testAbs(seq);
        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

    @Test
    public void testMathMin() throws TornadoExecutionPlanException {
        final int size = 8192;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMath::testMin, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testMin(a, b, seq);

        for (int i = 0; i < size; i++) {
            assertEquals(c.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testMathMax() throws TornadoExecutionPlanException {
        final int size = 8192;
        DoubleArray a = new DoubleArray(size);
        DoubleArray b = new DoubleArray(size);
        DoubleArray c = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, (float) Math.random());
            b.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestMath::testMax, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testMax(a, b, seq);

        for (int i = 0; i < size; i++) {
            assertEquals(c.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testNegate() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 8192;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        float min = -10000;
        float max = 10000;

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, min + r.nextFloat() * (max - min));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testNegate, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testNegate(a, seq);
        for (int i = 0; i < size; i++) {
            assertEquals(b.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testRem() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 8192;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray seq = new IntArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, r.nextInt());
            b.set(i, r.nextInt());
            seq.set(i, b.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testRemainder, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testRemainder(a, seq);
        for (int i = 0; i < size; i++) {
            assertEquals(b.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testFMA() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 8192;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
            seq.set(i, b.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testFMA, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testFMA(a, seq);

        for (int i = 0; i < size; i++) {
            assertEquals(b.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testFMA2() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 8192;
        FloatArray a = new FloatArray(size);
        DoubleArray b = new DoubleArray(size);
        DoubleArray seq = new DoubleArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
            seq.set(i, b.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testFMA2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testFMA2(a, seq);

        for (int i = 0; i < size; i++) {
            assertEquals(b.get(i), seq.get(i), 0.01);
        }
    }

    @Test
    public void testMathATan2() throws TornadoExecutionPlanException {
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
                .task("t0", TestMath::testAtan2, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testAtan2(seqA, seqB);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathAcos() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        DoubleArray a = new DoubleArray(size);
        DoubleArray seqA = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, Math.random());
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testAcos, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testAcos(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }

    }

    @Test
    public void testMathASin() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);

        final int size = 128;
        DoubleArray a = new DoubleArray(size);
        DoubleArray seqA = new DoubleArray(size);

        IntStream.range(0, size).parallel().forEach(i -> {
            a.set(i, Math.random());
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testAsin, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testAsin(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathSignumFloat() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 128;
        FloatArray a = new FloatArray(size);
        FloatArray seqA = new FloatArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, r.nextFloat() * (r.nextBoolean() ? -1 : 1));
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumFloat, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testSignumFloat(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathSignumFloatNaN() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        FloatArray a = new FloatArray(size);
        FloatArray seqA = new FloatArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, Float.NaN);
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumFloat, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testSignumFloat(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathSignumDouble() throws TornadoExecutionPlanException {
        Random r = new Random();
        final int size = 128;
        DoubleArray a = new DoubleArray(size);
        DoubleArray seqA = new DoubleArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, r.nextDouble() * (r.nextBoolean() ? -1 : 1));
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumDouble, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testSignumDouble(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathSignumDoubleNaN() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 128;
        DoubleArray a = new DoubleArray(size);
        DoubleArray seqA = new DoubleArray(size);

        IntStream.range(0, size).forEach(i -> {
            a.set(i, Double.NaN);
            seqA.set(i, a.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestMath::testSignumDouble, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testSignumDouble(seqA);

        for (int i = 0; i < size; i++) {
            assertEquals(a.get(i), seqA.get(i), 0.01);
        }
    }

    @Test
    public void testMathCeil() throws TornadoExecutionPlanException {
        final int size = 32;
        FloatArray data = new FloatArray(size);
        FloatArray seq = new FloatArray(size);

        IntStream.range(0, size).sequential().forEach(i -> {
            data.set(i, (float) Math.random());
            seq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data) //
                .task("t0", TestMath::testCeil, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        testCeil(seq);
        for (int i = 0; i < size; i++) {
            assertEquals(data.get(i), seq.get(i), 0.01f);
        }
    }

}
// CHECKSTYLE:ON
