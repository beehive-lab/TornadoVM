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
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Row-wise kernels ported from NVIDIA's TileGym suite, which is the reference body of CUDA Tile
 * kernels. Softmax and RMS norm are the two that exercise the parts of the API a GEMM does not:
 * reductions that keep their dimension, broadcasting that reduced row back across the tile, and
 * the transcendental math.
 *
 * <p>
 * The shapes follow TileGym's own parameterisation rather than round numbers, including the
 * awkward ones - n = 9 is smaller than any sensible tile, and n = 1009 and n = 768 are not
 * powers of two, so the last tile in every row is partial and the masked forms are mandatory.
 * A tile shape must still be a power of two, so the kernels tile at a fixed width and mask.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileRowKernels
 * </pre>
 */
public class TestTileRowKernels extends TornadoTestBase {

    /**
     * Row width covered by one tile. Rows wider than this are not handled by these kernels; the
     * chunked variants TileGym uses for n = 32768 need a second loop and are left out.
     */
    private static final int ROW_TILE = 2048;

    private static final float EPSILON = 1e-5f;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Softmax over one row per tile block: {@code exp(x - max(x)) / sum(exp(x - max(x)))}.
     *
     * <p>
     * Subtracting the row maximum is not decoration, it is what keeps {@code exp} from
     * overflowing, and it is also the step that needs a reduction to broadcast back across the
     * row. Masked loads pad the tail of a partial row with zero, which is harmless for the sum
     * because those lanes are masked out of the store, but it does mean the row maximum is at
     * least zero - the reference below does the same so the two agree.
     * </p>
     */
    public static void softmaxRow(TileContext tc, FloatArray in, FloatArray out, int rows, int n) {
        PartitionView iv = tc.partition(tc.view(in, rows, n), 1, ROW_TILE);
        PartitionView ov = tc.partition(tc.view(out, rows, n), 1, ROW_TILE);
        int row = tc.bidX();

        Tile x = iv.loadMasked(row, 0);
        Tile shifted = tc.sub(x, tc.max(x, 1));
        Tile weights = tc.exp(shifted);
        ov.storeMasked(tc.div(weights, tc.sum(weights, 1)), row, 0);
    }

    /**
     * RMS norm over one row per tile block: {@code x * rsqrt(mean(x^2) + eps)}.
     */
    public static void rmsNormRow(TileContext tc, FloatArray in, FloatArray out, int rows, int n, float inverseN) {
        PartitionView iv = tc.partition(tc.view(in, rows, n), 1, ROW_TILE);
        PartitionView ov = tc.partition(tc.view(out, rows, n), 1, ROW_TILE);
        int row = tc.bidX();

        Tile x = iv.loadMasked(row, 0);
        Tile meanSquare = tc.scale(tc.sum(tc.mul(x, x), 1), inverseN);
        Tile scale = tc.rsqrt(tc.add(meanSquare, tc.full(DType.F32, EPSILON, 1, 1)));
        ov.storeMasked(tc.mul(x, scale), row, 0);
    }

    private static FloatArray randomRows(int rows, int n, long seed) {
        java.util.Random random = new java.util.Random(seed);
        FloatArray array = new FloatArray(rows * n);
        for (int i = 0; i < rows * n; i++) {
            array.set(i, (random.nextFloat() - 0.5f) * 8.0f);
        }
        return array;
    }

    private static void runRowKernel(String name, TaskGraph graph, int rows) throws TornadoExecutionPlanException {
        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler(name, worker);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    private void checkSoftmax(int rows, int n) throws TornadoExecutionPlanException {
        FloatArray in = randomRows(rows, n, 7 + n);
        FloatArray out = new FloatArray(rows * n);

        TaskGraph graph = new TaskGraph("sm") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::softmaxRow, new TileContext(), in, out, rows, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("sm.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            // Reference in double, mirroring the kernel including the zero-padded tail.
            double max = 0.0;
            for (int i = 0; i < n; i++) {
                max = Math.max(max, in.get(row * n + i));
            }
            double total = 0.0;
            for (int i = 0; i < n; i++) {
                total += Math.exp(in.get(row * n + i) - max);
            }
            total += (ROW_TILE - n) * Math.exp(-max);
            for (int i = 0; i < n; i++) {
                double expected = Math.exp(in.get(row * n + i) - max) / total;
                assertEquals("row " + row + " column " + i, expected, out.get(row * n + i), 1e-5);
            }
        }
    }

    private void checkRmsNorm(int rows, int n) throws TornadoExecutionPlanException {
        FloatArray in = randomRows(rows, n, 31 + n);
        FloatArray out = new FloatArray(rows * n);

        TaskGraph graph = new TaskGraph("rms") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::rmsNormRow, new TileContext(), in, out, rows, n, 1.0f / n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("rms.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            double sumSquares = 0.0;
            for (int i = 0; i < n; i++) {
                double v = in.get(row * n + i);
                sumSquares += v * v;
            }
            double scale = 1.0 / Math.sqrt(sumSquares / n + EPSILON);
            for (int i = 0; i < n; i++) {
                assertEquals("row " + row + " column " + i, in.get(row * n + i) * scale, out.get(row * n + i), 5e-2);
            }
        }
    }

    // TileGym parameterises softmax over (256, 256), (256, 2048), (256, 9) and (256, 1009).
    // The 32768 case needs a chunked kernel and is out of scope here.

    @Test
    public void testSoftmaxSquare() throws TornadoExecutionPlanException {
        checkSoftmax(256, 256);
    }

    @Test
    public void testSoftmaxFullRowTile() throws TornadoExecutionPlanException {
        checkSoftmax(256, ROW_TILE);
    }

    @Test
    public void testSoftmaxTinyRow() throws TornadoExecutionPlanException {
        checkSoftmax(256, 9);
    }

    @Test
    public void testSoftmaxNonPowerOfTwoRow() throws TornadoExecutionPlanException {
        checkSoftmax(256, 1009);
    }

    @Test
    public void testSoftmaxSingleRow() throws TornadoExecutionPlanException {
        checkSoftmax(1, 1009);
    }

    // TileGym parameterises RMS norm over n = 256, 768 and 4096, with 768 the non-power-of-two.

    @Test
    public void testRmsNormSquare() throws TornadoExecutionPlanException {
        checkRmsNorm(128, 256);
    }

    @Test
    public void testRmsNormNonPowerOfTwoRow() throws TornadoExecutionPlanException {
        checkRmsNorm(128, 768);
    }

    @Test
    public void testRmsNormTinyRow() throws TornadoExecutionPlanException {
        checkRmsNorm(64, 9);
    }
}
