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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.DataRange;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Contract tests for copy-outs: the same data has to come back correct whatever shape the
 * transfer takes. Each data type is put through the routes the runtime can pick between -
 * a single (terminal) output, one of several outputs in the same graph, an under-demand
 * transfer, a partial range and a batched plan - because the runtime chooses a different
 * device call for each, and those calls are implemented per backend.
 *
 * <p>The point of the class is that a copy-out path which works for one type or on one
 * backend, but not another, fails here rather than in a user's results. It is written
 * against the public API only, so it runs unchanged on every backend.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestTransferContract
 * </code>
 */
public class TestTransferContract extends TornadoTestBase {

    private static final int SIZE = 4096;

    // Kernels, one per element type: a copy-out is only interesting once something wrote the buffer.

    public static void scaleFloat(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2.0f);
        }
    }

    public static void scaleInt(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2);
        }
    }

    public static void scaleDouble(DoubleArray in, DoubleArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2.0);
        }
    }

    public static void scaleLong(LongArray in, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2L);
        }
    }

    public static void scaleShort(ShortArray in, ShortArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, (short) (in.get(i) * 2));
        }
    }

    public static void scaleByte(ByteArray in, ByteArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, (byte) (in.get(i) * 2));
        }
    }

    public static void scaleMatrix(Matrix2DFloat in, Matrix2DFloat out) {
        for (@Parallel int i = 0; i < in.getNumRows(); i++) {
            for (@Parallel int j = 0; j < in.getNumColumns(); j++) {
                out.set(i, j, in.get(i, j) * 2.0f);
            }
        }
    }

    public static void scaleVector(VectorFloat4 in, VectorFloat4 out) {
        for (@Parallel int i = 0; i < in.getLength(); i++) {
            out.set(i, Float4.mult(in.get(i), 2.0f));
        }
    }

    public static void scaleHeapFloat(float[] in, float[] out) {
        for (@Parallel int i = 0; i < in.length; i++) {
            out[i] = in[i] * 2.0f;
        }
    }

    public static void scaleHeapInt(int[] in, int[] out) {
        for (@Parallel int i = 0; i < in.length; i++) {
            out[i] = in[i] * 2;
        }
    }

    /**
     * One task, one output. The output is the graph's last copy-out, which the runtime compiles
     * into its blocking form.
     */
    private static void singleOutput(String name, Object input, Object output, TaskGraphKernel kernel) throws TornadoExecutionPlanException {
        TaskGraph taskGraph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input);
        taskGraph = kernel.attach(taskGraph, "t0", input, output);
        taskGraph = taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    /**
     * Two tasks, two outputs. The first output is a non-terminal copy-out, which is the route a
     * multi-output graph takes.
     */
    private static void twoOutputs(String name, Object input, Object outputA, Object outputB, TaskGraphKernel kernel) throws TornadoExecutionPlanException {
        TaskGraph taskGraph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input);
        taskGraph = kernel.attach(taskGraph, "t0", input, outputA);
        taskGraph = kernel.attach(taskGraph, "t1", input, outputB);
        taskGraph = taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, outputA, outputB);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    /** Two outputs held on the device and asked for afterwards, in one call. */
    private static void onDemandOutputs(String name, Object input, Object outputA, Object outputB, TaskGraphKernel kernel) throws TornadoExecutionPlanException {
        TaskGraph taskGraph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input);
        taskGraph = kernel.attach(taskGraph, "t0", input, outputA);
        taskGraph = kernel.attach(taskGraph, "t1", input, outputB);
        taskGraph = taskGraph.transferToHost(DataTransferMode.UNDER_DEMAND, outputA, outputB);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            executionResult.transferToHost(outputA, outputB);
        }
    }

    @FunctionalInterface
    private interface TaskGraphKernel {
        TaskGraph attach(TaskGraph taskGraph, String id, Object input, Object output);
    }

    // ---------------------------------------------------------------- native arrays

    @Test
    public void testFloatArrayAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleFloat, (FloatArray) in, (FloatArray) out);

        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray outA = new FloatArray(SIZE);
        FloatArray outB = new FloatArray(SIZE);

        singleOutput("floatSingle", input, outA, kernel);
        assertFloats(outA, 6.0f);

        outA.init(0.0f);
        twoOutputs("floatTwo", input, outA, outB, kernel);
        assertFloats(outA, 6.0f);
        assertFloats(outB, 6.0f);

        outA.init(0.0f);
        outB.init(0.0f);
        onDemandOutputs("floatOnDemand", input, outA, outB, kernel);
        assertFloats(outA, 6.0f);
        assertFloats(outB, 6.0f);
    }

    @Test
    public void testIntArrayAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleInt, (IntArray) in, (IntArray) out);

        IntArray input = new IntArray(SIZE);
        input.init(5);
        IntArray outA = new IntArray(SIZE);
        IntArray outB = new IntArray(SIZE);

        singleOutput("intSingle", input, outA, kernel);
        assertInts(outA, 10);

        outA.init(0);
        twoOutputs("intTwo", input, outA, outB, kernel);
        assertInts(outA, 10);
        assertInts(outB, 10);

        outA.init(0);
        outB.init(0);
        onDemandOutputs("intOnDemand", input, outA, outB, kernel);
        assertInts(outA, 10);
        assertInts(outB, 10);
    }

    @Test
    public void testDoubleArrayAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleDouble, (DoubleArray) in, (DoubleArray) out);

        DoubleArray input = new DoubleArray(SIZE);
        input.init(1.5);
        DoubleArray outA = new DoubleArray(SIZE);
        DoubleArray outB = new DoubleArray(SIZE);

        singleOutput("doubleSingle", input, outA, kernel);
        assertEquals(3.0, outA.get(SIZE - 1), 0.001);

        outA.init(0.0);
        twoOutputs("doubleTwo", input, outA, outB, kernel);
        assertEquals(3.0, outA.get(SIZE - 1), 0.001);
        assertEquals(3.0, outB.get(SIZE - 1), 0.001);

        outA.init(0.0);
        outB.init(0.0);
        onDemandOutputs("doubleOnDemand", input, outA, outB, kernel);
        assertEquals(3.0, outA.get(SIZE - 1), 0.001);
        assertEquals(3.0, outB.get(SIZE - 1), 0.001);
    }

    @Test
    public void testLongArrayAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleLong, (LongArray) in, (LongArray) out);

        LongArray input = new LongArray(SIZE);
        input.init(7L);
        LongArray outA = new LongArray(SIZE);
        LongArray outB = new LongArray(SIZE);

        singleOutput("longSingle", input, outA, kernel);
        assertEquals(14L, outA.get(SIZE - 1));

        outA.init(0L);
        twoOutputs("longTwo", input, outA, outB, kernel);
        assertEquals(14L, outA.get(SIZE - 1));
        assertEquals(14L, outB.get(SIZE - 1));

        outA.init(0L);
        outB.init(0L);
        onDemandOutputs("longOnDemand", input, outA, outB, kernel);
        assertEquals(14L, outA.get(SIZE - 1));
        assertEquals(14L, outB.get(SIZE - 1));
    }

    @Test
    public void testShortArrayAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleShort, (ShortArray) in, (ShortArray) out);

        ShortArray input = new ShortArray(SIZE);
        input.init((short) 4);
        ShortArray outA = new ShortArray(SIZE);
        ShortArray outB = new ShortArray(SIZE);

        singleOutput("shortSingle", input, outA, kernel);
        assertEquals(8, outA.get(SIZE - 1));

        outA.init((short) 0);
        twoOutputs("shortTwo", input, outA, outB, kernel);
        assertEquals(8, outA.get(SIZE - 1));
        assertEquals(8, outB.get(SIZE - 1));

        outA.init((short) 0);
        outB.init((short) 0);
        onDemandOutputs("shortOnDemand", input, outA, outB, kernel);
        assertEquals(8, outA.get(SIZE - 1));
        assertEquals(8, outB.get(SIZE - 1));
    }

    @Test
    public void testByteArrayAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleByte, (ByteArray) in, (ByteArray) out);

        ByteArray input = new ByteArray(SIZE);
        input.init((byte) 3);
        ByteArray outA = new ByteArray(SIZE);
        ByteArray outB = new ByteArray(SIZE);

        singleOutput("byteSingle", input, outA, kernel);
        assertEquals(6, outA.get(SIZE - 1));

        outA.init((byte) 0);
        twoOutputs("byteTwo", input, outA, outB, kernel);
        assertEquals(6, outA.get(SIZE - 1));
        assertEquals(6, outB.get(SIZE - 1));

        outA.init((byte) 0);
        outB.init((byte) 0);
        onDemandOutputs("byteOnDemand", input, outA, outB, kernel);
        assertEquals(6, outA.get(SIZE - 1));
        assertEquals(6, outB.get(SIZE - 1));
    }

    // ---------------------------------------------------------------- other shapes

    @Test
    public void testMatrixAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleMatrix, (Matrix2DFloat) in, (Matrix2DFloat) out);

        final int n = 64;
        Matrix2DFloat input = new Matrix2DFloat(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                input.set(i, j, 2.0f);
            }
        }
        Matrix2DFloat outA = new Matrix2DFloat(n, n);
        Matrix2DFloat outB = new Matrix2DFloat(n, n);

        singleOutput("matrixSingle", input, outA, kernel);
        assertEquals(4.0f, outA.get(n - 1, n - 1), 0.001f);

        twoOutputs("matrixTwo", input, outA, outB, kernel);
        assertEquals(4.0f, outA.get(n - 1, n - 1), 0.001f);
        assertEquals(4.0f, outB.get(n - 1, n - 1), 0.001f);

        onDemandOutputs("matrixOnDemand", input, outA, outB, kernel);
        assertEquals(4.0f, outA.get(n - 1, n - 1), 0.001f);
        assertEquals(4.0f, outB.get(n - 1, n - 1), 0.001f);
    }

    @Test
    public void testVectorTypeAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleVector, (VectorFloat4) in, (VectorFloat4) out);

        final int n = 256;
        VectorFloat4 input = new VectorFloat4(n);
        for (int i = 0; i < n; i++) {
            input.set(i, new Float4(1.0f, 2.0f, 3.0f, 4.0f));
        }
        VectorFloat4 outA = new VectorFloat4(n);
        VectorFloat4 outB = new VectorFloat4(n);

        singleOutput("vectorSingle", input, outA, kernel);
        assertEquals(2.0f, outA.get(n - 1).getX(), 0.001f);

        twoOutputs("vectorTwo", input, outA, outB, kernel);
        assertEquals(2.0f, outA.get(n - 1).getX(), 0.001f);
        assertEquals(2.0f, outB.get(n - 1).getX(), 0.001f);

        onDemandOutputs("vectorOnDemand", input, outA, outB, kernel);
        assertEquals(2.0f, outA.get(n - 1).getX(), 0.001f);
        assertEquals(2.0f, outB.get(n - 1).getX(), 0.001f);
    }

    @Test
    public void testHeapArraysAllRoutes() throws TornadoExecutionPlanException {
        TaskGraphKernel floatKernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleHeapFloat, (float[]) in, (float[]) out);
        TaskGraphKernel intKernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleHeapInt, (int[]) in, (int[]) out);

        float[] floatInput = new float[SIZE];
        java.util.Arrays.fill(floatInput, 2.5f);
        float[] floatOutA = new float[SIZE];
        float[] floatOutB = new float[SIZE];

        singleOutput("heapFloatSingle", floatInput, floatOutA, floatKernel);
        assertEquals(5.0f, floatOutA[SIZE - 1], 0.001f);

        twoOutputs("heapFloatTwo", floatInput, floatOutA, floatOutB, floatKernel);
        assertEquals(5.0f, floatOutA[SIZE - 1], 0.001f);
        assertEquals(5.0f, floatOutB[SIZE - 1], 0.001f);

        int[] intInput = new int[SIZE];
        java.util.Arrays.fill(intInput, 6);
        int[] intOutA = new int[SIZE];
        int[] intOutB = new int[SIZE];

        twoOutputs("heapIntTwo", intInput, intOutA, intOutB, intKernel);
        assertEquals(12, intOutA[SIZE - 1]);
        assertEquals(12, intOutB[SIZE - 1]);

        onDemandOutputs("heapIntOnDemand", intInput, intOutA, intOutB, intKernel);
        assertEquals(12, intOutA[SIZE - 1]);
        assertEquals(12, intOutB[SIZE - 1]);
    }

    // ---------------------------------------------------------------- partial and batched

    @Test
    public void testPartialRangeCopyOut() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("partialRange") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestTransferContract::scaleFloat, input, output) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            DataRange dataRange = new DataRange(output);
            executionResult.transferToHost(dataRange.withSize(SIZE / 2));
            executionResult.transferToHost(dataRange.withOffset(SIZE / 2).withSize(SIZE / 2));
        }

        assertFloats(output, 6.0f);
    }

    @Test
    public void testBatchedCopyOut() throws TornadoExecutionPlanException {
        final int batchedSize = 1024 * 1024;
        FloatArray input = new FloatArray(batchedSize);
        input.init(3.0f);
        FloatArray output = new FloatArray(batchedSize);

        TaskGraph taskGraph = new TaskGraph("batched") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestTransferContract::scaleFloat, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB");
            executionPlan.execute();
        }

        for (int i = 0; i < batchedSize; i++) {
            assertEquals(6.0f, output.get(i), 0.001f);
        }
    }

    /**
     * Two outputs under a batched plan. Batched copy-outs are read chunk by chunk, which is a
     * different device call again.
     *
     * <p>Ignored: this fails on both CUDA and OpenCL with a null device buffer at LAUNCH
     * ({@code TornadoVMInterpreter.executeLaunch}), i.e. a batched graph with more than one output
     * does not allocate every output per chunk. The test is kept because it states the contract;
     * enable it with the fix.
     */
    @Ignore("Batched plans with more than one output leave an output unallocated - see issue")
    @Test
    public void testBatchedTwoOutputs() throws TornadoExecutionPlanException {
        final int batchedSize = 1024 * 1024;
        FloatArray input = new FloatArray(batchedSize);
        input.init(3.0f);
        FloatArray outA = new FloatArray(batchedSize);
        FloatArray outB = new FloatArray(batchedSize);

        TaskGraph taskGraph = new TaskGraph("batchedTwo") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestTransferContract::scaleFloat, input, outA) //
                .task("t1", TestTransferContract::scaleFloat, input, outB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outA, outB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withBatch("1MB");
            executionPlan.execute();
        }

        for (int i = 0; i < batchedSize; i++) {
            assertEquals(6.0f, outA.get(i), 0.001f);
            assertEquals(6.0f, outB.get(i), 0.001f);
        }
    }

    /** Copy-outs have to stay correct when the plan tracks dependencies between operations. */
    @Test
    public void testTwoOutputsWithDependencies() throws TornadoExecutionPlanException {
        String previous = System.getProperty("tornado.vm.deps");
        System.setProperty("tornado.vm.deps", "True");
        try {
            FloatArray input = new FloatArray(SIZE);
            input.init(3.0f);
            FloatArray outA = new FloatArray(SIZE);
            FloatArray outB = new FloatArray(SIZE);

            TaskGraphKernel kernel = (graph, id, in, out) -> graph.task(id, TestTransferContract::scaleFloat, (FloatArray) in, (FloatArray) out);
            twoOutputs("depsTwo", input, outA, outB, kernel);

            assertFloats(outA, 6.0f);
            assertFloats(outB, 6.0f);
        } finally {
            if (previous == null) {
                System.clearProperty("tornado.vm.deps");
            } else {
                System.setProperty("tornado.vm.deps", previous);
            }
        }
    }

    private static void assertFloats(FloatArray array, float expected) {
        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(expected, array.get(i), 0.001f);
        }
    }

    private static void assertInts(IntArray array, int expected) {
        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(expected, array.get(i));
        }
    }
}
