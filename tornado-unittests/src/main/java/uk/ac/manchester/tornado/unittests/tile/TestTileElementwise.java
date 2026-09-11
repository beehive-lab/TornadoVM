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
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceTileNotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Elementwise CUDA Tile kernels: whole-tile arithmetic, scaling, ragged edges.
 *
 * <p>
 * How to run:
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileElementwise
 * </pre>
 */
public class TestTileElementwise extends TornadoTestBase {

    private static final int SIZE = 4096;

    private static final int TILE = 256;

    /**
     * CUDA Tile needs the CUDA backend, compute capability 8.0, toolkit 13.3 and driver R580.
     * Unavailable configurations throw the typed *NotSupported exceptions that the test runner
     * counts as [UNSUPPORTED]; a JUnit Assume would be reported as a pass, which would hide the
     * fact that nothing ran.
     */
    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Runs a plan, translating "this host cannot run tile kernels" into the typed exception the
     * runner reports as UNSUPPORTED. The driver case is the interesting one: compilation
     * succeeds and only the module load fails, so the message has to be matched to tell it
     * apart from a genuine compilation failure.
     */
    private static void executeOrReportUnsupported(TornadoExecutionPlan plan, GridScheduler grid) throws TornadoExecutionPlanException {
        try {
            plan.withGridScheduler(grid).execute();
        } catch (TornadoDeviceTileNotSupported e) {
            throw new TornadoVMCUDANotSupported(e.getMessage());
        } catch (TornadoBailoutRuntimeException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("device kernel image is invalid")) {
                throw new TornadoVMCUDANotSupported("Loading a CUDA Tile cubin requires driver R580 or newer: " + message);
            }
            throw e;
        }
    }

    private static GridScheduler tileGrid(String taskId, int blocks) {
        WorkerGrid1D worker = new WorkerGrid1D(blocks);
        // CUDA Tile owns the thread mapping and requires a block dimension of 1x1x1.
        worker.setLocalWork(1, 1, 1);
        return new GridScheduler(taskId, worker);
    }

    public static void vectorAdd(TileContext tc, FloatArray a, FloatArray b, FloatArray c, int n) {
        PartitionView av = tc.partition(tc.view(a, n), TILE);
        PartitionView bv = tc.partition(tc.view(b, n), TILE);
        PartitionView cv = tc.partition(tc.view(c, n), TILE);
        int block = tc.bidX();
        cv.store(tc.add(av.load(block), bv.load(block)), block);
    }

    public static void scaledAdd(TileContext tc, FloatArray a, FloatArray b, FloatArray c, int n, float alpha) {
        PartitionView av = tc.partition(tc.view(a, n), TILE);
        PartitionView bv = tc.partition(tc.view(b, n), TILE);
        PartitionView cv = tc.partition(tc.view(c, n), TILE);
        int block = tc.bidX();
        cv.store(tc.add(tc.scale(av.load(block), alpha), bv.load(block)), block);
    }

    /**
     * A size that is not a multiple of the tile shape, so the last tile is partial and the
     * masked forms are mandatory. CUDA Tile does not bounds check the plain forms.
     */
    public static void raggedAdd(TileContext tc, FloatArray a, FloatArray b, FloatArray c, int n) {
        PartitionView av = tc.partition(tc.view(a, n), TILE);
        PartitionView bv = tc.partition(tc.view(b, n), TILE);
        PartitionView cv = tc.partition(tc.view(c, n), TILE);
        int block = tc.bidX();
        cv.storeMasked(tc.add(av.loadMasked(block), bv.loadMasked(block)), block);
    }

    public static void fillAndStore(TileContext tc, FloatArray c, int n) {
        PartitionView cv = tc.partition(tc.view(c, n), TILE);
        // Rank must match the view: a rank-2 256x1 tile does not store into a rank-1 view, and
        // CUDA Tile rejects that at compile time through its same_shape constraint.
        Tile filled = tc.full(DType.F32, 7.5, TILE);
        cv.store(filled, tc.bidX());
    }

    /**
     * Transpose keeps the shape when the tile is square, so the result stores back through the
     * same view. Exercises ct::transpose.
     */
    public static void transposeSquare(TileContext tc, FloatArray a, FloatArray c, int n) {
        PartitionView av = tc.partition(tc.view(a, n, n), 32, 32);
        PartitionView cv = tc.partition(tc.view(c, n, n), 32, 32);
        cv.store(tc.transpose(av.load(tc.bidX(), tc.bidY())), tc.bidY(), tc.bidX());
    }

    /**
     * Widening conversion, which is the only direction CUDA Tile allows. Exercises
     * ct::element_cast; there is no ct::cast.
     */
    public static void widen(TileContext tc, HalfFloatArray a, FloatArray c, int n) {
        PartitionView av = tc.partition(tc.view(a, n), TILE);
        PartitionView cv = tc.partition(tc.view(c, n), TILE);
        int block = tc.bidX();
        cv.store(tc.cast(av.load(block), DType.F32), block);
    }

    /**
     * Exercises ct::iota.
     */
    public static void iotaStore(TileContext tc, IntArray c, int n) {
        PartitionView cv = tc.partition(tc.view(c, n), TILE);
        cv.store(tc.iota(DType.S32, TILE), tc.bidX());
    }

    private static FloatArray ramp(int size, float scale) {
        FloatArray array = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            array.set(i, scale * i);
        }
        return array;
    }

    @Test
    public void testVectorAdd() throws TornadoExecutionPlanException {
        FloatArray a = ramp(SIZE, 1.0f);
        FloatArray b = ramp(SIZE, 2.0f);
        FloatArray c = new FloatArray(SIZE);

        TaskGraph graph = new TaskGraph("tileAdd") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("add", TestTileElementwise::vectorAdd, new TileContext(), a, b, c, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, tileGrid("tileAdd.add", SIZE / TILE));
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(3.0f * i, c.get(i), 0.001f);
        }
    }

    @Test
    public void testScaledAdd() throws TornadoExecutionPlanException {
        FloatArray a = ramp(SIZE, 1.0f);
        FloatArray b = ramp(SIZE, 1.0f);
        FloatArray c = new FloatArray(SIZE);
        float alpha = 3.0f;

        TaskGraph graph = new TaskGraph("tileScale") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("saxpy", TestTileElementwise::scaledAdd, new TileContext(), a, b, c, SIZE, alpha) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, tileGrid("tileScale.saxpy", SIZE / TILE));
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(4.0f * i, c.get(i), 0.001f);
        }
    }

    @Test
    public void testRaggedEdge() throws TornadoExecutionPlanException {
        final int size = SIZE + 37;
        final int blocks = (size + TILE - 1) / TILE;
        FloatArray a = ramp(size, 1.0f);
        FloatArray b = ramp(size, 2.0f);
        FloatArray c = new FloatArray(size);

        TaskGraph graph = new TaskGraph("tileRagged") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("add", TestTileElementwise::raggedAdd, new TileContext(), a, b, c, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, tileGrid("tileRagged.add", blocks));
        }
        for (int i = 0; i < size; i++) {
            assertEquals(3.0f * i, c.get(i), 0.001f);
        }
    }

    @Test
    public void testTranspose() throws TornadoExecutionPlanException {
        final int n = 64;
        FloatArray a = ramp(n * n, 1.0f);
        FloatArray c = new FloatArray(n * n);

        TaskGraph graph = new TaskGraph("tileTranspose") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("transpose", TestTileElementwise::transposeSquare, new TileContext(), a, c, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        WorkerGrid2D worker = new WorkerGrid2D(n / 32, n / 32);
        worker.setLocalWork(1, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, new GridScheduler("tileTranspose.transpose", worker));
        }
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                assertEquals(a.get(column * n + row), c.get(row * n + column), 0.001f);
            }
        }
    }

    @Test
    public void testWideningCast() throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            a.set(i, new HalfFloat(i % 128));
        }
        FloatArray c = new FloatArray(SIZE);

        TaskGraph graph = new TaskGraph("tileCast") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("widen", TestTileElementwise::widen, new TileContext(), a, c, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, tileGrid("tileCast.widen", SIZE / TILE));
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i % 128, c.get(i), 0.001f);
        }
    }

    @Test
    public void testIota() throws TornadoExecutionPlanException {
        IntArray c = new IntArray(SIZE);

        TaskGraph graph = new TaskGraph("tileIota") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, c) //
                .task("iota", TestTileElementwise::iotaStore, new TileContext(), c, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, tileGrid("tileIota.iota", SIZE / TILE));
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i % TILE, c.get(i));
        }
    }

    @Test
    public void testNarrowingCastIsRejected() {
        // CUDA Tile refuses narrowing element conversions, and the API says so before the tile
        // compiler has to.
        TileContext tc = new TileContext();
        try {
            tc.cast(tc.zeros(DType.F32, TILE), DType.F16);
            org.junit.Assert.fail("Expected the narrowing conversion F32 to F16 to be rejected");
        } catch (IllegalArgumentException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("narrowing"));
        }
    }

    @Test
    public void testFull() throws TornadoExecutionPlanException {
        FloatArray c = new FloatArray(SIZE);

        TaskGraph graph = new TaskGraph("tileFull") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, c) //
                .task("fill", TestTileElementwise::fillAndStore, new TileContext(), c, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            executeOrReportUnsupported(plan, tileGrid("tileFull.fill", SIZE / TILE));
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(7.5f, c.get(i), 0.001f);
        }
    }
}
