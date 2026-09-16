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
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The shape operations: {@code broadcast}, {@code reshape} and {@code extract}.
 *
 * <p>
 * Each replaces a workaround that earlier kernels in this suite had to use. Broadcasting a row
 * vector was done by adding a zero tile of the target shape, which is an arithmetic operation
 * standing in for a layout one. Changing a reduction result from {@code [rows, 1]} to
 * {@code [1, rows]} meant a transpose, which is a data movement rather than a reinterpretation.
 * Splitting a packed row meant loading the same row through two partition views.
 * </p>
 *
 * <p>
 * {@code permute} is deliberately absent: on a rank-2 tile the only non-identity permutation is
 * the transpose that already exists, so it would add surface without adding capability.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileShapes
 * </pre>
 */
public class TestTileShapes extends TornadoTestBase {

    private static final int WIDTH = 64;

    private static final int ROWS = 16;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Adds a bias row to every row of a tile: the bias is loaded once as {@code [1, WIDTH]} and
     * broadcast to the tile shape, rather than added as a shape-mismatched operand.
     */
    public static void broadcastBias(TileContext tc, FloatArray in, FloatArray bias, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), ROWS, WIDTH);
        PartitionView bv = tc.partition(tc.view(bias, 1, WIDTH), 1, WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, WIDTH), ROWS, WIDTH);
        int block = tc.bidX();

        Tile biasRow = tc.broadcast(bv.load(0, 0), ROWS, WIDTH);
        ov.store(tc.add(iv.load(block, 0), biasRow), block, 0);
    }

    /**
     * Normalises each row by its own sum, where the row sum comes back as {@code [ROWS, 1]} and
     * has to reach every column. A reshape to {@code [1, ROWS]} would be the wrong orientation;
     * this broadcasts the column instead, which is the shape rule working as documented.
     */
    public static void broadcastColumn(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), ROWS, WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, WIDTH), ROWS, WIDTH);
        int block = tc.bidX();

        Tile tile = iv.load(block, 0);
        Tile sums = tc.broadcast(tc.sum(tile, 1), ROWS, WIDTH);
        ov.store(tc.div(tile, sums), block, 0);
    }

    /**
     * Writes a row's sum to a rank-1 output, which needs the {@code [1, 1]} reduction result as
     * a {@code [1]} tile: a rank change, so a reshape rather than a broadcast.
     */
    public static void reshapeToRank1(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), 1, WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows), 1);
        int row = tc.bidX();

        Tile total = tc.sum(iv.load(row, 0), 1);
        ov.store(tc.reshape(total, 1), row);
    }

    /**
     * SiLU-and-multiply from a single load: the packed row is loaded once and split with two
     * extracts, rather than loaded twice through a narrower partition view.
     */
    public static void siluAndMultiplyByExtract(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, 2 * WIDTH), 1, 2 * WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, WIDTH), 1, WIDTH);
        int row = tc.bidX();

        Tile packed = iv.load(row, 0);
        Tile gate = tc.extract(packed, 1, WIDTH, 0, 0);
        Tile value = tc.extract(packed, 1, WIDTH, 0, 1);

        Tile ones = tc.full(DType.F32, 1.0, 1, WIDTH);
        Tile silu = tc.div(gate, tc.add(ones, tc.exp(tc.scale(gate, -1.0))));
        ov.store(tc.mul(silu, value), row, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private static FloatArray randomRows(int rows, int columns, long seed) {
        FloatArray array = new FloatArray(rows * columns);
        Random random = new Random(seed);
        for (int i = 0; i < rows * columns; i++) {
            array.set(i, 0.25f + random.nextFloat());
        }
        return array;
    }

    private static void run(String taskId, TaskGraph graph, int blocks) throws TornadoExecutionPlanException {
        WorkerGrid1D worker = new WorkerGrid1D(blocks);
        GridScheduler grid = new GridScheduler(taskId, worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    @Test
    public void testBroadcastBiasRow() throws TornadoExecutionPlanException {
        final int rows = 4 * ROWS;
        FloatArray in = randomRows(rows, WIDTH, 1201);
        FloatArray bias = randomRows(1, WIDTH, 1203);
        FloatArray out = new FloatArray(rows * WIDTH);

        run("bias.k", new TaskGraph("bias") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, bias) //
                .task("k", TestTileShapes::broadcastBias, new TileContext(), in, bias, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows / ROWS);

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < WIDTH; column++) {
                assertEquals("(" + row + "," + column + ")", in.get(row * WIDTH + column) + bias.get(column), //
                        out.get(row * WIDTH + column), 1e-5);
            }
        }
    }

    @Test
    public void testBroadcastRowSumColumn() throws TornadoExecutionPlanException {
        final int rows = 2 * ROWS;
        FloatArray in = randomRows(rows, WIDTH, 1207);
        FloatArray out = new FloatArray(rows * WIDTH);

        run("col.k", new TaskGraph("col") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileShapes::broadcastColumn, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows / ROWS);

        for (int row = 0; row < rows; row++) {
            double total = 0.0;
            for (int column = 0; column < WIDTH; column++) {
                total += in.get(row * WIDTH + column);
            }
            for (int column = 0; column < WIDTH; column++) {
                assertEquals("(" + row + "," + column + ")", in.get(row * WIDTH + column) / total, //
                        out.get(row * WIDTH + column), 1e-5);
            }
        }
    }

    @Test
    public void testReshapeToRank1() throws TornadoExecutionPlanException {
        final int rows = 32;
        FloatArray in = randomRows(rows, WIDTH, 1213);
        FloatArray out = new FloatArray(rows);

        run("reshape.k", new TaskGraph("reshape") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileShapes::reshapeToRank1, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int row = 0; row < rows; row++) {
            double total = 0.0;
            for (int column = 0; column < WIDTH; column++) {
                total += in.get(row * WIDTH + column);
            }
            assertEquals("row " + row, total, out.get(row), 1e-4);
        }
    }

    @Test
    public void testExtractSplitsAPackedRow() throws TornadoExecutionPlanException {
        final int rows = 48;
        FloatArray in = randomRows(rows, 2 * WIDTH, 1217);
        FloatArray out = new FloatArray(rows * WIDTH);

        run("extract.k", new TaskGraph("extract") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileShapes::siluAndMultiplyByExtract, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < WIDTH; i++) {
                double gate = in.get(row * 2 * WIDTH + i);
                double value = in.get(row * 2 * WIDTH + WIDTH + i);
                double expected = gate / (1.0 + Math.exp(-gate)) * value;
                assertEquals("(" + row + "," + i + ")", expected, out.get(row * WIDTH + i), 1e-4);
            }
        }
    }
}
