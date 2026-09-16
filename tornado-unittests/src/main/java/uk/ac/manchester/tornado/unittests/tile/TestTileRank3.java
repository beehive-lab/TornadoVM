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
 * Rank-3 views, where a batch or head dimension is addressed instead of folded away.
 *
 * <p>
 * Compare {@code TestTileLlmKernels#bmm}, which does the same batched matmul over a rank-2 view
 * of {@code (batch * m, k)}: it derives the batch from the block index, multiplies it by the
 * number of K tiles, and adds that to every operand index. None of that arithmetic is wrong, but
 * all of it is bookkeeping the view can do. With a rank-3 view the batch is simply the first
 * block index.
 * </p>
 *
 * <p>
 * Rank 3 is for addressing, not arithmetic: {@code mma} and the reductions are rank-2
 * operations, so a loaded {@code [1, M, N]} tile is reshaped to {@code [M, N]} before it is
 * used. That is exactly what NVIDIA's own kernels do - they load a rank-4 tile from a
 * {@code [B, H, S, D]} view and reshape it before the first multiply.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileRank3
 * </pre>
 */
public class TestTileRank3 extends TornadoTestBase {

    private static final int TILE = 32;

    /** Row width of the per-batch scaling kernel: a tile extent, so a constant. */
    private static final int WIDTH = 64;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Batched matmul over rank-3 views: {@code C[b] = A[b] * B[b]}.
     *
     * <p>
     * The grid is (batch, row blocks, column blocks) and the batch index goes straight into the
     * view. The only shape work left is the reshape that turns each {@code [1, TILE, TILE]} load
     * into the rank-2 tile {@code mma} needs.
     * </p>
     */
    public static void batchedMatmul(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int batch, int m, int n, int k) {
        PartitionView aView = tc.partition(tc.view(a, batch, m, k), 1, TILE, TILE);
        PartitionView bView = tc.partition(tc.view(b, batch, k, n), 1, TILE, TILE);
        PartitionView cView = tc.partition(tc.view(c, batch, m, n), 1, TILE, TILE);

        int batchIndex = tc.bidX();
        int rowBlock = tc.bidY();
        int columnBlock = tc.bidZ();

        Tile acc = tc.zeros(DType.F32, TILE, TILE);
        for (int step = 0; step < k / TILE; step++) {
            Tile left = tc.reshape(aView.load(batchIndex, rowBlock, step), TILE, TILE);
            Tile right = tc.reshape(bView.load(batchIndex, step, columnBlock), TILE, TILE);
            acc = tc.mma(left, right, acc);
        }
        cView.store(tc.reshape(acc, 1, TILE, TILE), batchIndex, rowBlock, columnBlock);
    }

    /**
     * Per-batch scaling, to cover a rank-3 load and store without a matmul in between: every
     * element of batch {@code b} is multiplied by {@code b + 1}, so a kernel that ignored the
     * batch index would fail rather than merely be slower.
     *
     * <p>
     * The row width is a constant rather than a parameter because it is a tile extent, and a
     * tile shape is part of the kernel's type. Passing it in is rejected at sketch time with
     * that explanation - the batch and row counts stay runtime values, since they are view
     * extents rather than tile extents.
     * </p>
     */
    public static void scaleByBatch(TileContext tc, FloatArray in, FloatArray out, int batch, int rows) {
        PartitionView iv = tc.partition(tc.view(in, batch, rows, WIDTH), 1, 1, WIDTH);
        PartitionView ov = tc.partition(tc.view(out, batch, rows, WIDTH), 1, 1, WIDTH);

        int batchIndex = tc.bidX();
        int row = tc.bidY();
        Tile tile = iv.load(batchIndex, row, 0);
        ov.store(tc.scale(tile, batchIndex + 1), batchIndex, row, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private void checkBatchedMatmul(int batch, int m, int n, int k) throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(batch * m * k);
        HalfFloatArray b = new HalfFloatArray(batch * k * n);
        FloatArray c = new FloatArray(batch * m * n);
        Random random = new Random(1301 + batch);
        for (int i = 0; i < batch * m * k; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < batch * k * n; i++) {
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        WorkerGrid3D worker = new WorkerGrid3D(batch, m / TILE, n / TILE);
        GridScheduler grid = new GridScheduler("bmm3.k", worker);
        TaskGraph graph = new TaskGraph("bmm3") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileRank3::batchedMatmul, new TileContext(), a, b, c, batch, m, n, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int batchIndex = 0; batchIndex < batch; batchIndex++) {
            for (int row = 0; row < m; row++) {
                for (int column = 0; column < n; column++) {
                    float expected = 0.0f;
                    for (int inner = 0; inner < k; inner++) {
                        expected += a.get(batchIndex * m * k + row * k + inner).getFloat32() //
                                * b.get(batchIndex * k * n + inner * n + column).getFloat32();
                    }
                    assertEquals("batch " + batchIndex + " (" + row + "," + column + ")", expected, //
                            c.get(batchIndex * m * n + row * n + column), 0.05f);
                }
            }
        }
    }

    @Test
    public void testBatchedMatmulSmall() throws TornadoExecutionPlanException {
        checkBatchedMatmul(4, TILE, TILE, TILE);
    }

    @Test
    public void testBatchedMatmulLarger() throws TornadoExecutionPlanException {
        checkBatchedMatmul(8, 64, 64, 64);
    }

    /** A batch of one, where the rank-3 view is only bookkeeping and must still be right. */
    @Test
    public void testBatchedMatmulSingleBatch() throws TornadoExecutionPlanException {
        checkBatchedMatmul(1, 64, 64, 32);
    }

    @Test
    public void testScaleByBatch() throws TornadoExecutionPlanException {
        final int batch = 6;
        final int rows = 8;
        FloatArray in = new FloatArray(batch * rows * WIDTH);
        FloatArray out = new FloatArray(batch * rows * WIDTH);
        Random random = new Random(1307);
        for (int i = 0; i < batch * rows * WIDTH; i++) {
            in.set(i, random.nextFloat());
        }

        WorkerGrid3D worker = new WorkerGrid3D(batch, rows, 1);
        GridScheduler grid = new GridScheduler("scale3.k", worker);
        TaskGraph graph = new TaskGraph("scale3") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRank3::scaleByBatch, new TileContext(), in, out, batch, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int batchIndex = 0; batchIndex < batch; batchIndex++) {
            for (int i = 0; i < rows * WIDTH; i++) {
                int index = batchIndex * rows * WIDTH + i;
                assertEquals("index " + index, in.get(index) * (batchIndex + 1), out.get(index), 1e-5);
            }
        }
    }
}
