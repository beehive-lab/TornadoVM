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
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestArrays
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

    public static void vectorSubDouble(DoubleArray a, DoubleArray b, DoubleArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
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

    public static void initHalfFloatVector(HalfFloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, new HalfFloat(100.0f));
        }
    }

    public static void vectorAddHalfFloat(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, HalfFloat.add(a.get(i), b.get(i)));
        }
    }

    public static void vectorSubHalfFloat(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, HalfFloat.sub(a.get(i), b.get(i)));
        }
    }

    public static void vectorMultHalfFloat(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, HalfFloat.mult(a.get(i), b.get(i)));
        }
    }

    public static void vectorDivHalfFloat(HalfFloatArray a, HalfFloatArray b, HalfFloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, HalfFloat.div(a.get(i), b.get(i)));
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
    public void testWarmUp() throws TornadoExecutionPlanException {

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data.get(i));
        }
    }

    @Test
    public void testInitByteArray() throws TornadoExecutionPlanException {
        // Initialization: there is no copy-in.
        final int N = 128;
        ByteArray data = new ByteArray(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeSequentialByte, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals((byte) 21, data.get(i));
        }
    }

    @Test
    public void testInitNotParallel() throws TornadoExecutionPlanException {
        // Initialization: there is no copy-in.

        final int N = 128;
        IntArray data = new IntArray(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeSequential, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(1, data.get(i), 0.0001);
        }
    }

    @Test
    public void testInitParallel() throws TornadoExecutionPlanException {
        // Initialization: there is no copy-in.

        final int N = 128;
        IntArray data = new IntArray(N);

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestArrays::initializeToOneParallel, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(1, data.get(i), 0.0001);
        }
    }

    @Test
    public void testAdd() throws TornadoExecutionPlanException {

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data.get(i), 0.0001);
        }
    }

    @Test
    public void testVectorAdditionDouble() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01);
        }
    }

    @Test
    public void testVectorAdditionDoubleCUDAGraph() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements); // stores output of add tornado
        DoubleArray c_s = new DoubleArray(numElements); // stores serial output of add
        DoubleArray d = new DoubleArray(numElements);
        DoubleArray e = new DoubleArray(numElements); // stores output of sub tornado
        DoubleArray e_s = new DoubleArray(numElements); // stores serial output of sub

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, 3.0);
            b.set(i, 2.0);
            d.set(i, 1.0);
        });

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, d)
                .task("t0", TestArrays::vectorAddDouble, a, b, c)
                .task("t1", TestArrays::vectorSubDouble, c, d, e)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, e);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCUDAGraph();
            executionPlan.execute();
        }

        vectorAddDouble(a, b, c_s);
        vectorSubDouble(c_s, d, e_s);

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(e.get(i), e_s.get(i), 0.01);
        }
    }

    @Test
    public void testVectorAdditionDoubleUpdateCUDAGraph() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray a_s = new DoubleArray(numElements);
        DoubleArray b_s = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, 1);
            a_s.set(i, 1);
            b.set(i, 2);
            b_s.set(i, 2);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddDouble, a, b, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withCUDAGraph();
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < 10; i++) {
            vectorAddDouble(a_s, b_s, b_s);
        }

        for (int i = 0; i < b.getSize(); i++) {
            assertEquals(b.get(i), b_s.get(i), 0.01);
        }
    }

    public static void copyWithContext(KernelContext ctx, FloatArray in, FloatArray out) {
        int idx = ctx.globalIdx;
        out.set(idx, in.get(idx));
    }

    public static void addWithContext(KernelContext ctx, FloatArray a, FloatArray b, FloatArray out) {
        int idx = ctx.globalIdx;
        out.set(idx, a.get(idx) + b.get(idx));
    }

    public static void doubleWithContext(KernelContext ctx, FloatArray in, FloatArray out) {
        int idx = ctx.globalIdx;
        out.set(idx, in.get(idx) * 2.0f);
    }

    @Test
    public void testCUDAGraphWithKernelContextChained() throws TornadoExecutionPlanException {
        final int numElements = 256;

        // Shared buffer between graphs
        FloatArray shared = new FloatArray(numElements);

        FloatArray input0 = new FloatArray(numElements);
        IntStream.range(0, numElements).forEach(i -> input0.set(i, 1.0f));

        KernelContext ctx0 = new KernelContext();
        TaskGraph tg0 = new TaskGraph("g0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input0)
                .task("t0", TestArrays::copyWithContext, ctx0, input0, shared)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, shared);

        // Graph 1: shared + weights → intermediate → output (multi-task)
        FloatArray weights = new FloatArray(numElements);
        FloatArray intermediate = new FloatArray(numElements);
        FloatArray output = new FloatArray(numElements);
        IntStream.range(0, numElements).forEach(i -> weights.set(i, 10.0f));

        KernelContext ctx1 = new KernelContext();
        TaskGraph tg1 = new TaskGraph("g1")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, shared)
                .task("t1", TestArrays::addWithContext, ctx1, shared, weights, intermediate)
                .task("t2", TestArrays::doubleWithContext, ctx1, intermediate, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph itg0 = tg0.snapshot();
        ImmutableTaskGraph itg1 = tg1.snapshot();

        WorkerGrid wg0 = new WorkerGrid1D(numElements);
        WorkerGrid wg1 = new WorkerGrid1D(numElements);
        GridScheduler grid = new GridScheduler("g0.t0", wg0);
        grid.addWorkerGrid("g1.t1", wg1);
        grid.addWorkerGrid("g1.t2", wg1);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg0, itg1)) {
            plan.withCUDAGraph();

            for (int iter = 0; iter < 3; iter++) {
                final float val = iter + 1;
                IntStream.range(0, numElements).forEach(i -> input0.set(i, val));

                plan.withGraph(0).withGridScheduler(grid).execute();
                plan.withGraph(1).withGridScheduler(grid).execute();

                // shared = copy of input0 = val
                // intermediate = shared + weights = val + 10
                // output = intermediate * 2 = (val + 10) * 2
                float expected = (val + 10.0f) * 2.0f;

                for (int i = 0; i < numElements; i++) {
                    assertEquals("output[" + i + "] at iter " + iter,
                            expected, output.get(i), 0.01f);
                }
            }
        }
    }

    @Test
    public void testCUDAGraphPersistConsume() throws TornadoExecutionPlanException {
        final int numElements = 256;

        // Shared buffer that stays on device
        FloatArray shared = new FloatArray(numElements);
        FloatArray input = new FloatArray(numElements);
        FloatArray weights = new FloatArray(numElements);
        FloatArray output = new FloatArray(numElements);

        IntStream.range(0, numElements).forEach(i -> {
            input.set(i, 1.0f);
            weights.set(i, 10.0f);
        });

        KernelContext ctx0 = new KernelContext();
        KernelContext ctx1 = new KernelContext();

        // Graph 0: input → shared (persisted on device, NO transferToHost)
        TaskGraph tg0 = new TaskGraph("g0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestArrays::copyWithContext, ctx0, input, shared)
                .persistOnDevice(shared);

        // Graph 1: consumes shared from device (NO transferToDevice for shared)
        TaskGraph tg1 = new TaskGraph("g1")
                .consumeFromDevice(shared)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("t1", TestArrays::addWithContext, ctx1, shared, weights, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph itg0 = tg0.snapshot();
        ImmutableTaskGraph itg1 = tg1.snapshot();

        WorkerGrid wg = new WorkerGrid1D(numElements);
        GridScheduler grid = new GridScheduler("g0.t0", wg);
        grid.addWorkerGrid("g1.t1", wg);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg0, itg1)) {
            plan.withCUDAGraph();

            for (int iter = 0; iter < 3; iter++) {
                final float val = iter + 1;
                IntStream.range(0, numElements).forEach(i -> input.set(i, val));

                plan.withGraph(0).withGridScheduler(grid).execute();
                plan.withGraph(1).withGridScheduler(grid).execute();

                // shared = input = val (stays on device)
                // output = shared + weights = val + 10
                float expected = val + 10.0f;

                for (int i = 0; i < numElements; i++) {
                    assertEquals("output[" + i + "] at iteration " + iter,
                            expected, output.get(i), 0.01f);
                }
            }
        }
    }

    @Test
    public void testVectorAdditionFloat() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.01f);
        }
    }

    @Test
    public void testVectorAdditionInteger() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    /**
     * Run multiple times an execution plan built with the Kernel Context API.
     */
    @Test
    public void testVectorAdditionIntegerKernelContext() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionLong() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorAdditionShort() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i));
        }
    }

    @Test
    public void testVectorChars() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals('f', c.get(i));
        }
    }

    @Test
    public void testVectorBytes() throws TornadoExecutionPlanException {
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(21, c.get(i));
        }
    }

    @Test
    public void testHalfFloatInitialization() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        HalfFloatArray c = new HalfFloatArray(numElements);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestArrays::initHalfFloatVector, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(100.0f, c.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testVectorAdditionHalfFloat() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        HalfFloatArray a = new HalfFloatArray(numElements);
        HalfFloatArray b = new HalfFloatArray(numElements);
        HalfFloatArray c = new HalfFloatArray(numElements);
        a.init(new HalfFloat(6.0f));
        b.init(new HalfFloat(2.0f));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorAddHalfFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(8.0f, c.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testVectorSubtractionHalfFloat() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        HalfFloatArray a = new HalfFloatArray(numElements);
        HalfFloatArray b = new HalfFloatArray(numElements);
        HalfFloatArray c = new HalfFloatArray(numElements);
        a.init(new HalfFloat(6.0f));
        b.init(new HalfFloat(2.0f));
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorSubHalfFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(4.0f, c.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testVectorMultiplicationHalfFloat() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        HalfFloatArray a = new HalfFloatArray(numElements);
        HalfFloatArray b = new HalfFloatArray(numElements);
        HalfFloatArray c = new HalfFloatArray(numElements);
        a.init(new HalfFloat(6.0f));
        b.init(new HalfFloat(2.0f));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorMultHalfFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(12.0f, c.get(i).getFloat32(), 0.01f);
        }
    }

    @Test
    public void testVectorDivisionHalfFloat() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        HalfFloatArray a = new HalfFloatArray(numElements);
        HalfFloatArray b = new HalfFloatArray(numElements);
        HalfFloatArray c = new HalfFloatArray(numElements);
        a.init(new HalfFloat(6.0f));
        b.init(new HalfFloat(2.0f));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestArrays::vectorDivHalfFloat, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(3.0f, c.get(i).getFloat32(), 0.01f);
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
     *     $ tornado-test -V --debug --threadInfo uk.ac.manchester.tornado.unittests.arrays.TestArrays#testVectorCharsMessage
     *     </code>
     */
    @Test
    public void testVectorCharsMessage() throws TornadoExecutionPlanException {

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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals('w', a.get(0));
        assertEquals('o', a.get(1));
        assertEquals('r', a.get(2));
        assertEquals('l', a.get(3));
        assertEquals('d', a.get(4));
        assertEquals('!', a.get(5));
    }
    // CHECKSTYLE:ON

}
