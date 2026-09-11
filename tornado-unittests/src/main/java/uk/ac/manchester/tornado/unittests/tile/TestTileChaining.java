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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cublas.provider.CuBlasLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * The point of the whole exercise: a JIT SIMT kernel, a CUDA Tile kernel and a native cuBLAS
 * call chained in one task graph, passing results to each other through device buffers.
 *
 * <p>
 * Nothing here synchronises or copies to the host between stages. Ordering comes for free
 * because a tile kernel is an ordinary module launch on the execution plan's stream, and the
 * library task binds its cuBLAS handle to that same stream. If any of that were untrue the
 * numbers would be wrong, because every stage consumes what the previous one wrote.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileChaining
 * </pre>
 */
public class TestTileChaining extends TornadoTestBase {

    private static final int SIZE = 256;

    private static final int TILE = 64;

    @Before
    public void tileAndCuBlasMustBeAvailable() {
        TileSupport.requireTileSupport();
        if (!CuBlasLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("cuBLAS is not available on this host");
        }
    }

    /** Stage 1, JIT SIMT: scale the input. */
    public static void scale(FloatArray in, FloatArray out, float factor) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * factor);
        }
    }

    /** Stage 2, CUDA Tile: whole-tile self-addition, so out = 2 * in. */
    public static void tileDouble(TileContext tc, FloatArray in, FloatArray out, int n) {
        PartitionView iv = tc.partition(tc.view(in, n), TILE);
        PartitionView ov = tc.partition(tc.view(out, n), TILE);
        int block = tc.bidX();
        ov.store(tc.add(iv.load(block), iv.load(block)), block);
    }

    /** Stage 4, JIT SIMT: bias the cuBLAS result. */
    public static void bias(FloatArray in, FloatArray out, float amount) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) + amount);
        }
    }

    @Test
    public void testSimtThenTileThenCuBlasThenSimt() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray scaled = new FloatArray(SIZE);
        FloatArray doubled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);
        FloatArray result = new FloatArray(SIZE);

        java.util.Random random = new java.util.Random(11);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        // Only the tile task needs a worker grid, and it counts TILE BLOCKS.
        WorkerGrid1D tileWorker = new WorkerGrid1D(SIZE / TILE);
        GridScheduler grid = new GridScheduler("chain.tile", tileWorker);

        TaskGraph graph = new TaskGraph("chain") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, matrix) //
                .task("scale", TestTileChaining::scale, input, scaled, 3.0f) //
                .task("tile", TestTileChaining::tileDouble, new TileContext(), scaled, doubled, SIZE) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, doubled, 1, 0.0f, projected, 1) //
                .task("bias", TestTileChaining::bias, projected, result, 0.25f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        // CPU reference for the whole chain.
        for (int i = 0; i < SIZE; i++) {
            float expectedProjection = 0.0f;
            for (int j = 0; j < SIZE; j++) {
                expectedProjection += matrix.get(i * SIZE + j) * (2.0f * 3.0f * input.get(j));
            }
            assertEquals(expectedProjection + 0.25f, result.get(i), 0.05f);
        }
    }

    /**
     * The same chain under intra-plan concurrency, which is where the interpreter's role-queue
     * join around a library launch actually matters: the host-to-device and device-to-host
     * queues and the compute stream pool have to be joined into the default queue before the
     * native call is issued, or the cuBLAS stage reads what the tile stage has not finished
     * writing.
     */
    @Test
    public void testChainUnderIntraPlanConcurrency() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray scaled = new FloatArray(SIZE);
        FloatArray doubled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);
        FloatArray result = new FloatArray(SIZE);

        java.util.Random random = new java.util.Random(23);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        WorkerGrid1D tileWorker = new WorkerGrid1D(SIZE / TILE);
        GridScheduler grid = new GridScheduler("conc.tile", tileWorker);

        TaskGraph graph = new TaskGraph("conc") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, matrix) //
                .task("scale", TestTileChaining::scale, input, scaled, 3.0f) //
                .task("tile", TestTileChaining::tileDouble, new TileContext(), scaled, doubled, SIZE) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, doubled, 1, 0.0f, projected, 1) //
                .task("bias", TestTileChaining::bias, projected, result, 0.25f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).withIntraPlanConcurrency().execute();
        }

        for (int i = 0; i < SIZE; i++) {
            float expectedProjection = 0.0f;
            for (int j = 0; j < SIZE; j++) {
                expectedProjection += matrix.get(i * SIZE + j) * (2.0f * 3.0f * input.get(j));
            }
            assertEquals(expectedProjection + 0.25f, result.get(i), 0.05f);
        }
    }

    /**
     * Whether a tile kernel can be captured into a CUDA graph is undocumented by NVIDIA, so it
     * is measured rather than assumed. Mechanically it should work: a tile kernel is an ordinary
     * cuLaunchKernel on a stream. If this ever starts failing, the fix is to refuse
     * withCUDAGraph() on a graph containing a tile task with a clear message, rather than to let
     * it fail obscurely.
     */
    @Test
    public void testTileKernelUnderCudaGraph() throws TornadoExecutionPlanException {
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, i);
        }

        WorkerGrid1D worker = new WorkerGrid1D(SIZE / TILE);
        GridScheduler grid = new GridScheduler("cudagraph.tile", worker);

        TaskGraph graph = new TaskGraph("cudagraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("tile", TestTileChaining::tileDouble, new TileContext(), in, out, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).withCUDAGraph().execute();
            // Execute twice: the second run is the one that replays the captured graph.
            plan.withGridScheduler(grid).withCUDAGraph().execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(2.0f * i, out.get(i), 0.001f);
        }
    }

    /**
     * The combined pipeline captured into one CUDA graph: JIT SIMT, CUDA Tile and a native
     * cuBLAS call all replayed from the same graph, not three separate ones.
     *
     * <p>
     * This is the strongest of the chaining tests. Capture is the case where a library task is
     * most likely to misbehave, because the interpreter tells providers they are capturing and
     * a provider that allocates, synchronises or queries during capture would invalidate it.
     * Executing twice matters: the second execution is the replay.
     * </p>
     */
    @Test
    public void testWholeChainInOneCudaGraph() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray scaled = new FloatArray(SIZE);
        FloatArray doubled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);
        FloatArray result = new FloatArray(SIZE);

        java.util.Random random = new java.util.Random(41);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        WorkerGrid1D tileWorker = new WorkerGrid1D(SIZE / TILE);
        GridScheduler grid = new GridScheduler("graph.tile", tileWorker);

        TaskGraph graph = new TaskGraph("graph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, matrix) //
                .task("scale", TestTileChaining::scale, input, scaled, 3.0f) //
                .task("tile", TestTileChaining::tileDouble, new TileContext(), scaled, doubled, SIZE) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, doubled, 1, 0.0f, projected, 1) //
                .task("bias", TestTileChaining::bias, projected, result, 0.25f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            // First execution captures and instantiates; the rest replay.
            for (int replay = 0; replay < 3; replay++) {
                plan.withGridScheduler(grid).withCUDAGraph().execute();

                for (int i = 0; i < SIZE; i++) {
                    float expectedProjection = 0.0f;
                    for (int j = 0; j < SIZE; j++) {
                        expectedProjection += matrix.get(i * SIZE + j) * (2.0f * 3.0f * input.get(j));
                    }
                    assertEquals("replay " + replay + " element " + i, expectedProjection + 0.25f, result.get(i), 0.05f);
                }
            }
        }
    }

    /** Stage for the KernelContext variant: one work-item per element, explicit thread ids. */
    public static void scaleWithKernelContext(KernelContext ctx, FloatArray in, FloatArray out, float factor) {
        out.set(ctx.globalIdx, in.get(ctx.globalIdx) * factor);
    }

    /** A TornadoVM reduction task, a fourth kind of task in the same graph. */
    public static void sumInto(FloatArray in, @Reduce FloatArray total) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            total.set(0, total.get(0) + in.get(i));
        }
    }

    /**
     * Four task kinds in one graph: a `@Parallel` JIT kernel, a `KernelContext` kernel, a
     * `TileContext` kernel and a native cuBLAS call.
     *
     * <p>
     * The `KernelContext` stage matters because it is the existing explicit-thread API, and its
     * launch geometry is the opposite of a tile task's: it wants a real thread block, while a
     * tile task is pinned to one thread per block. Both appear in the same graph with their own
     * worker grids, which is the case a per-device scheduler choice would have broken.
     * </p>
     */
    @Test
    public void testJitAndKernelContextAndTileAndLibrary() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray ctxScaled = new FloatArray(SIZE);
        FloatArray doubled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);
        FloatArray result = new FloatArray(SIZE);

        java.util.Random random = new java.util.Random(59);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        // Two worker grids with opposite geometry in one scheduler.
        WorkerGrid1D ctxWorker = new WorkerGrid1D(SIZE);
        ctxWorker.setLocalWork(64, 1, 1);                       // a real thread block
        WorkerGrid1D tileWorker = new WorkerGrid1D(SIZE / TILE); // tile blocks; block pinned to 1
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("mixed.ctx", ctxWorker);
        grid.addWorkerGrid("mixed.tile", tileWorker);

        TaskGraph graph = new TaskGraph("mixed") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, matrix) //
                .task("ctx", TestTileChaining::scaleWithKernelContext, new KernelContext(), input, ctxScaled, 3.0f) //
                .task("tile", TestTileChaining::tileDouble, new TileContext(), ctxScaled, doubled, SIZE) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, doubled, 1, 0.0f, projected, 1) //
                .task("bias", TestTileChaining::bias, projected, result, 0.25f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            float expected = 0.0f;
            for (int j = 0; j < SIZE; j++) {
                expected += matrix.get(i * SIZE + j) * (2.0f * 3.0f * input.get(j));
            }
            assertEquals(expected + 0.25f, result.get(i), 0.05f);
        }
    }

    // A tile task combined with an @Reduce task is deliberately NOT tested here, because
    // ReduceTaskGraph keeps its own, stricter argument check and rewrites the graph:
    //
    //   * the rewrite creates a new TaskGraph with a generated name, so every task id changes
    //     and a GridScheduler keyed on the original id stops matching. A tile task then runs
    //     with no worker grid, which means one tile block, and the reduction silently returns
    //     only the first tile's contribution.
    //   * its check carries neither TornadoTaskGraph's exemption for native arrays nor an
    //     allowance for a buffer arriving through consumeFromDevice, so an intermediate cannot
    //     be left undeclared and the two stages cannot be split across graphs either.
    //
    // Both are pre-existing reduction behaviours rather than anything specific to tiles, so they
    // are recorded rather than worked around. TileContext was added to that check's exemption
    // list, which is the part that did belong here. Use tc.sum for a reduction inside a tile.

    /**
     * Producer and consumer task graphs sharing a device buffer: the tile task writes it in one
     * graph, `persistOnDevice` keeps it there, and a cuBLAS task in a second graph consumes it
     * through `consumeFromDevice` without a host round-trip.
     */
    @Test
    public void testTileProducerCuBlasConsumerAcrossGraphs() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray doubled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);

        java.util.Random random = new java.util.Random(71);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        WorkerGrid1D tileWorker = new WorkerGrid1D(SIZE / TILE);
        GridScheduler grid = new GridScheduler("producer.tile", tileWorker);

        TaskGraph producer = new TaskGraph("producer") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("tile", TestTileChaining::tileDouble, new TileContext(), input, doubled, SIZE) //
                .persistOnDevice(doubled);

        TaskGraph consumer = new TaskGraph("consumer") //
                .consumeFromDevice(producer.getTaskGraphName(), doubled) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, doubled, 1, 0.0f, projected, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, projected);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            float expected = 0.0f;
            for (int j = 0; j < SIZE; j++) {
                expected += matrix.get(i * SIZE + j) * (2.0f * input.get(j));
            }
            assertEquals(expected, projected.get(i), 0.05f);
        }
    }

    /**
     * The same chain with the tile stage removed, so a failure in the mixed test can be told
     * apart from a failure in the SIMT plus cuBLAS combination that already worked.
     */
    @Test
    public void testSimtThenCuBlasOnly() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray matrix = new FloatArray(SIZE * SIZE);
        FloatArray scaled = new FloatArray(SIZE);
        FloatArray projected = new FloatArray(SIZE);

        java.util.Random random = new java.util.Random(11);
        for (int i = 0; i < SIZE; i++) {
            input.set(i, random.nextFloat());
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        TaskGraph graph = new TaskGraph("noTile") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, matrix) //
                .task("scale", TestTileChaining::scale, input, scaled, 6.0f) //
                .libraryTask("gemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), SIZE, SIZE, 1.0f, matrix, SIZE, scaled, 1, 0.0f, projected, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, projected);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            float expected = 0.0f;
            for (int j = 0; j < SIZE; j++) {
                expected += matrix.get(i * SIZE + j) * (6.0f * input.get(j));
            }
            assertEquals(expected, projected.get(i), 0.05f);
        }
    }
}
