package uk.ac.manchester.tornado.unittests.streams;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Functional correctness tests for the multi-stream mode (CUDA Streams) of the PTX backend.
 *
 * Included tests:
 * <ol>
 *     <li>Baseline Sequential Execution (no CUDA Streams)</li>
 *     <li>Two Independent Tasks: Ensures independent kernels can execute concurrently.</li>
 *     <li>Task Dependency: Validates correct ordering of dependent kernels.</li>
 *     <li>Transfer / Compute Overlap: Tests overlapping host-device transfers with kernel execution.</li>
 *     <li>Multiple Iterations: Verifies runtime stability when executing the same plan repeatedly.</li>
 *     <li>Chunk Pipeline: Tests pipelined execution of multiple independent chunks.</li>
 *     <li>Many Independent Tasks: Ensures multiple kernels can run concurrently across streams.</li>
 *     <li>Mixed Dependency Graph: Validates execution of graphs with both parallel and dependent tasks.</li>
 *     <li>Repeated Execution with Streams: Tests safe reuse of CUDA streams across repeated executions.</li>
 * </ol>
 *
 * How to run:
 *
 * tornado-test -V uk.ac.manchester.tornado.unittests.streams.TestCUDAStreams
 */
public class TestCUDAStreams extends TornadoTestBase {

    private static final int N = 52428800; // 200MB each array
    private static final int COMPUTE_ITERATIONS = 2048;
    private static final int REPEAT_ITERATIONS = 100;
    private static final int CHUNKS = 4;

    private static final float DELTA = 1e-3f;
    private static final float ALPHA = 0.5f;

    // -------------------------------------------------------------------------
    // Kernel
    // -------------------------------------------------------------------------

    public static void computeKernel(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {

            float xi = x.get(i);
            float yi = y.get(i);

            float val = alpha * xi + yi;

            for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
                val = val * alpha + yi;
            }

            result.set(i, val);
        }
    }

    // -------------------------------------------------------------------------
    // CPU reference
    // -------------------------------------------------------------------------

    private static float expectedValue(float x, float y, float alpha) {

        float val = alpha * x + y;

        for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
            val = val * alpha + y;
        }

        return val;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * 1. Baseline sequential execution without CUDA streams.
     */
    @Test
    public void testSequentialBaseline() throws TornadoExecutionPlanException {

        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);
        FloatArray result = new FloatArray(N);

        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("baseline")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::computeKernel, x, y, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected = expectedValue(1.0f, 2.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected, result.get(i), DELTA);
        }
    }

    /**
     * 2. Two independent tasks executed with CUDA streams.
     * Validates that independent kernels can execute asynchronously.
     */
    @Test
    public void testTwoIndependentTasks() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray inputA = new FloatArray(N);
        FloatArray inputB = new FloatArray(N);
        FloatArray result1 = new FloatArray(N);

        FloatArray inputC = new FloatArray(N);
        FloatArray inputD = new FloatArray(N);
        FloatArray result2 = new FloatArray(N);

        inputA.init(1.0f);
        inputB.init(2.0f);

        inputC.init(3.0f);
        inputD.init(4.0f);

        TaskGraph tg = new TaskGraph("streamsTwoTasks")

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputA, inputB)
                .task("t0", TestCUDAStreams::computeKernel, inputA, inputB, result1, ALPHA)

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputC, inputD)
                .task("t1", TestCUDAStreams::computeKernel, inputC, inputD, result2, ALPHA)

                .transferToHost(DataTransferMode.EVERY_EXECUTION, result1, result2)

                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected1 = expectedValue(1.0f, 2.0f, ALPHA);
        float expected2 = expectedValue(3.0f, 4.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected1, result1.get(i), DELTA);
            assertEquals(expected2, result2.get(i), DELTA);
        }
    }

    /**
     * 3. Dependent tasks executed with CUDA streams.
     * Verifies that dependencies between tasks enforce correct ordering.
     */
    @Test
    public void testTaskDependency() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray a = new FloatArray(N);
        FloatArray b = new FloatArray(N);
        FloatArray r1 = new FloatArray(N);
        FloatArray r2 = new FloatArray(N);

        a.init(1.0f);
        b.init(2.0f);

        TaskGraph tg = new TaskGraph("streamsDependency")

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)

                .task("t0", TestCUDAStreams::computeKernel, a, b, r1, ALPHA)

                .task("t1", TestCUDAStreams::computeKernel, r1, b, r2, ALPHA)

                .transferToHost(DataTransferMode.EVERY_EXECUTION, r2)

                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected1 = expectedValue(1.0f, 2.0f, ALPHA);
        float expected2 = expectedValue(expected1, 2.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected2, r2.get(i), DELTA);
        }
    }

    /**
     * 4. Transfer/compute overlap scenario.
     * Ensures host-to-device transfers can overlap with kernel execution.
     */
    @Test
    public void testTransferComputeOverlap() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray x1 = new FloatArray(N);
        FloatArray y1 = new FloatArray(N);
        FloatArray r1 = new FloatArray(N);

        FloatArray x2 = new FloatArray(N);
        FloatArray y2 = new FloatArray(N);
        FloatArray r2 = new FloatArray(N);

        x1.init(1.0f);
        y1.init(2.0f);

        x2.init(3.0f);
        y2.init(4.0f);

        TaskGraph tg = new TaskGraph("streamsOverlap")

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x1, y1)
                .task("t0", TestCUDAStreams::computeKernel, x1, y1, r1, ALPHA)

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x2, y2)
                .task("t1", TestCUDAStreams::computeKernel, x2, y2, r2, ALPHA)

                .transferToHost(DataTransferMode.EVERY_EXECUTION, r1, r2)

                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected1 = expectedValue(1.0f, 2.0f, ALPHA);
        float expected2 = expectedValue(3.0f, 4.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected1, r1.get(i), DELTA);
            assertEquals(expected2, r2.get(i), DELTA);
        }
    }

    /**
     * 5. Repeated execution test.
     * Validates runtime stability across multiple executions.
     */
    @Test
    public void testMultipleIterations() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);
        FloatArray result = new FloatArray(N);

        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("streamsMultiIter")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::computeKernel, x, y, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result)
                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {

            for (int i = 0; i < REPEAT_ITERATIONS; i++) {
                plan.execute();
            }
        }

        float expected = expectedValue(1.0f, 2.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected, result.get(i), DELTA);
        }
    }

    /**
     * 6. Chunk pipeline execution.
     * Tests pipelined execution across multiple data chunks.
     */
    @Test
    public void testChunkPipeline() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int chunkSize = N / CHUNKS;

        FloatArray[] x = new FloatArray[CHUNKS];
        FloatArray[] y = new FloatArray[CHUNKS];
        FloatArray[] result = new FloatArray[CHUNKS];

        for (int chunk = 0; chunk < CHUNKS; chunk++) {

            x[chunk] = new FloatArray(chunkSize);
            y[chunk] = new FloatArray(chunkSize);
            result[chunk] = new FloatArray(chunkSize);

            x[chunk].init(1.0f);
            y[chunk].init(2.0f);
        }

        TaskGraph tg = new TaskGraph("streamsPipelineOfChunks");

        for (int chunk = 0; chunk < CHUNKS; chunk++) {

            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[chunk], y[chunk])
                    .task("chunk" + chunk, TestCUDAStreams::computeKernel, x[chunk], y[chunk], result[chunk], ALPHA)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, result[chunk]);
        }
        tg.withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected = expectedValue(1.0f, 2.0f, ALPHA);

        for (int chunk = 0; chunk < CHUNKS; chunk++) {
            for (int i = 0; i < chunkSize; i++) {
                assertEquals(expected, result[chunk].get(i), DELTA);
            }
        }
    }

    /**
     * 7. Many Independent Tasks
     * Verifies that several independent kernels can execute concurrently when
     * CUDA streams are enabled.
     */
    @Test
    public void testManyIndependentTasks() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray x1 = new FloatArray(N);
        FloatArray y1 = new FloatArray(N);
        FloatArray r1 = new FloatArray(N);

        FloatArray x2 = new FloatArray(N);
        FloatArray y2 = new FloatArray(N);
        FloatArray r2 = new FloatArray(N);

        FloatArray x3 = new FloatArray(N);
        FloatArray y3 = new FloatArray(N);
        FloatArray r3 = new FloatArray(N);

        x1.init(1.0f);
        y1.init(2.0f);

        x2.init(3.0f);
        y2.init(4.0f);

        x3.init(5.0f);
        y3.init(6.0f);

        TaskGraph tg = new TaskGraph("manyTasks")

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x1, y1)
                .task("t0", TestCUDAStreams::computeKernel, x1, y1, r1, ALPHA)

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x2, y2)
                .task("t1", TestCUDAStreams::computeKernel, x2, y2, r2, ALPHA)

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x3, y3)
                .task("t2", TestCUDAStreams::computeKernel, x3, y3, r3, ALPHA)

                .transferToHost(DataTransferMode.EVERY_EXECUTION, r1, r2, r3)

                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected1 = expectedValue(1.0f, 2.0f, ALPHA);
        float expected2 = expectedValue(3.0f, 4.0f, ALPHA);
        float expected3 = expectedValue(5.0f, 6.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected1, r1.get(i), DELTA);
            assertEquals(expected2, r2.get(i), DELTA);
            assertEquals(expected3, r3.get(i), DELTA);
        }
    }

    /**
     * 8. Mixed Dependency Graph
     *
     * Tests a graph with both parallel and dependent tasks. Two kernels execute
     * independently and their result is consumed by a third kernel.
     */
    @Test
    public void testMixedDependencyGraph() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);

        FloatArray r0 = new FloatArray(N);
        FloatArray r1 = new FloatArray(N);
        FloatArray r2 = new FloatArray(N);

        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("mixedGraph")

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)

                .task("t0", TestCUDAStreams::computeKernel, x, y, r0, ALPHA)
                .task("t1", TestCUDAStreams::computeKernel, x, y, r1, ALPHA)

                .task("t2", TestCUDAStreams::computeKernel, r0, r1, r2, ALPHA)

                .transferToHost(DataTransferMode.EVERY_EXECUTION, r2)

                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float v = expectedValue(1.0f, 2.0f, ALPHA);
        float expected = expectedValue(v, v, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected, r2.get(i), DELTA);
        }
    }

    /**
     * 9. Repeated Execution with Streams
     *
     * Verifies that CUDA streams are reused safely across multiple executions
     * of the same execution plan.
     */
    @Test
    public void testRepeatedExecutionWithStreams() throws TornadoExecutionPlanException {

        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);
        FloatArray result = new FloatArray(N);

        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("repeatStreams")

                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::computeKernel, x, y, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result)

                .withCUDAStreams();

        ImmutableTaskGraph itg = tg.snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {

            for (int i = 0; i < 20; i++) {
                plan.execute();
            }
        }

        float expected = expectedValue(1.0f, 2.0f, ALPHA);

        for (int i = 0; i < N; i++) {
            assertEquals(expected, result.get(i), DELTA);
        }
    }

    @After
    public void tearDown() {
        // necessary to turn off streams for the other tests in the test suite
        new TaskGraph("tearDown").withoutCUDAStreams();
    }
}