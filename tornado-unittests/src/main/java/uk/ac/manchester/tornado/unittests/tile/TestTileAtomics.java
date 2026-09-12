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

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid3D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Atomic accumulation through a partition view.
 *
 * <p>
 * Every test here is one several tile blocks could not pass without atomicity: each writes to
 * an output region that another block writes too, so a plain store would lose contributions
 * non-deterministically. That is the point - a test that would also pass with a normal store
 * would tell us nothing.
 * </p>
 *
 * <p>
 * CUDA Tile has no atomic read-modify-write on a view. It offers the view {@code atomic_load}
 * and {@code atomic_store}, and puts {@code ct::atomic_add} on a <em>tile of pointers</em>
 * instead, so the code generator computes that pointer tile from the view's base pointer and
 * extents. Measured lowering on this device: {@code ATOMG.E.ADD.F32.FTZ.RN.STRONG.GPU}.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileAtomics
 * </pre>
 */
public class TestTileAtomics extends TornadoTestBase {

    private static final int TILE = 32;

    private static final int ROW_WIDTH = 64;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Every block adds its own row into one shared accumulator row, so all {@code blocks}
     * contributions land on the same 64 elements. Without atomicity the result is whichever
     * block happened to write last.
     */
    public static void accumulateRows(TileContext tc, FloatArray in, FloatArray accumulator, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView av = tc.partition(tc.view(accumulator, 1, ROW_WIDTH), 1, ROW_WIDTH);
        int row = tc.bidX();
        av.atomicAdd(iv.load(row, 0), 0, 0);
    }

    /** The same thing over a rank-1 view, which takes the simpler pointer arithmetic. */
    public static void accumulateFlat(TileContext tc, FloatArray in, FloatArray accumulator, int elements) {
        PartitionView iv = tc.partition(tc.view(in, elements), ROW_WIDTH);
        PartitionView av = tc.partition(tc.view(accumulator, ROW_WIDTH), ROW_WIDTH);
        av.atomicAdd(iv.load(tc.bidX()), 0);
    }

    /**
     * Split-K matmul in a single kernel.
     *
     * <p>
     * This is the kernel atomics exist for. The K dimension is split across blocks in the
     * {@code bidZ} direction, each computing a partial product of the same output tile and
     * accumulating it atomically - so the two-kernel arrangement the decode tests use (write
     * per-split partials, reduce in a second pass) collapses into one launch with no
     * intermediate buffer.
     * </p>
     */
    public static void splitKMatmul(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, int stepsPerSplit) {
        PartitionView aView = tc.partition(tc.view(a, m, k), TILE, TILE);
        PartitionView bView = tc.partition(tc.view(b, k, n), TILE, TILE);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        int split = tc.bidZ();
        int firstStep = split * stepsPerSplit;

        Tile acc = tc.zeros(DType.F32, TILE, TILE);
        for (int step = 0; step < stepsPerSplit; step++) {
            acc = tc.mma(aView.load(rowBlock, firstStep + step), bView.load(firstStep + step, columnBlock), acc);
        }
        cView.atomicAdd(acc, rowBlock, columnBlock);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    @Test
    public void testAccumulateRowsRank2() throws TornadoExecutionPlanException {
        final int rows = 96;
        FloatArray in = new FloatArray(rows * ROW_WIDTH);
        FloatArray accumulator = new FloatArray(ROW_WIDTH);
        Random random = new Random(1009);
        for (int i = 0; i < rows * ROW_WIDTH; i++) {
            in.set(i, random.nextFloat());
        }

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("acc.k", worker);
        TaskGraph graph = new TaskGraph("acc") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, accumulator) //
                .task("k", TestTileAtomics::accumulateRows, new TileContext(), in, accumulator, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, accumulator);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int column = 0; column < ROW_WIDTH; column++) {
            double expected = 0.0;
            for (int row = 0; row < rows; row++) {
                expected += in.get(row * ROW_WIDTH + column);
            }
            // 96 additions of values in [0, 1): the ordering is arbitrary, so the bound allows
            // for float summation in any order rather than a fixed one.
            assertEquals("column " + column, expected, accumulator.get(column), 1e-3);
        }
    }

    @Test
    public void testAccumulateRank1() throws TornadoExecutionPlanException {
        final int blocks = 64;
        final int elements = blocks * ROW_WIDTH;
        FloatArray in = new FloatArray(elements);
        FloatArray accumulator = new FloatArray(ROW_WIDTH);
        Random random = new Random(1013);
        for (int i = 0; i < elements; i++) {
            in.set(i, random.nextFloat());
        }

        WorkerGrid1D worker = new WorkerGrid1D(blocks);
        GridScheduler grid = new GridScheduler("flat.k", worker);
        TaskGraph graph = new TaskGraph("flat") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, accumulator) //
                .task("k", TestTileAtomics::accumulateFlat, new TileContext(), in, accumulator, elements) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, accumulator);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < ROW_WIDTH; i++) {
            double expected = 0.0;
            for (int block = 0; block < blocks; block++) {
                expected += in.get(block * ROW_WIDTH + i);
            }
            assertEquals("element " + i, expected, accumulator.get(i), 1e-3);
        }
    }

    private void checkSplitK(int size, int splits) throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(size * size);
        HalfFloatArray b = new HalfFloatArray(size * size);
        FloatArray c = new FloatArray(size * size);
        Random random = new Random(1019 + splits);
        for (int i = 0; i < size * size; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        int stepsPerSplit = size / TILE / splits;
        // The split axis is the third grid dimension, so the worker grid is 3D: one point per
        // (output tile row, output tile column, K split).
        WorkerGrid3D splitWorker = new WorkerGrid3D(size / TILE, size / TILE, splits);
        GridScheduler grid = new GridScheduler("splitk.k", splitWorker);
        TaskGraph graph = new TaskGraph("splitk") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c) //
                .task("k", TestTileAtomics::splitKMatmul, new TileContext(), a, b, c, size, size, size, stepsPerSplit) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float expected = 0.0f;
                for (int inner = 0; inner < size; inner++) {
                    expected += a.get(row * size + inner).getFloat32() * b.get(inner * size + column).getFloat32();
                }
                assertEquals("(" + row + "," + column + ")", expected, c.get(row * size + column), 0.05f);
            }
        }
    }

    /** Two splits: each block pair contends on every output tile. */
    @Test
    public void testSplitKTwoSplits() throws TornadoExecutionPlanException {
        checkSplitK(64, 2);
    }

    /** Four splits over a larger K, so four blocks contend per output tile. */
    @Test
    public void testSplitKFourSplits() throws TornadoExecutionPlanException {
        checkSplitK(128, 4);
    }
}
