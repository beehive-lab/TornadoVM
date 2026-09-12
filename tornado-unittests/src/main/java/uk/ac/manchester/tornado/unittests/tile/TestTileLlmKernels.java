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
 * Further kernels ported from NVIDIA's TileGym suite: rotary embeddings, batched matmul and a
 * masked dropout.
 *
 * <p>
 * Each one is here because it reaches something the earlier ports do not. RoPE needs
 * position-dependent trigonometry built from an index tile. Batched matmul needs a batch
 * dimension without any slicing API. Dropout needs a second input tile consumed elementwise.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileLlmKernels
 * </pre>
 */
public class TestTileLlmKernels extends TornadoTestBase {

    /** Half of the rotated head dimension, and therefore a tile width, so it is a literal. */
    private static final int HALF_DIM = 64;

    private static final int TILE_M = 32;

    private static final int TILE_N = 32;

    private static final int TILE_K = 32;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Rotary position embedding over one row per tile block.
     *
     * <p>
     * The head dimension is split into halves the same way SiLU-and-multiply splits a packed
     * row: block 0 along the row is the first half, block 1 the second. The rotation is then
     * {@code (x1 cos - x2 sin, x2 cos + x1 sin)}.
     * </p>
     *
     * <p>
     * The angles come from the tile itself rather than a precomputed table: an integer
     * {@code iota} cast to float gives the index within the half-dimension, and the inverse
     * frequency is {@code exp(-log(base) * 2i/dim)}, so the whole schedule is computed on the
     * fly with {@code exp}, {@code sin} and {@code cos}.
     * </p>
     */
    public static void rope(TileContext tc, FloatArray inOut, int rows, float logBaseOverDim, float position) {
        PartitionView v = tc.partition(tc.view(inOut, rows, 2 * HALF_DIM), 1, HALF_DIM);
        int row = tc.bidX();

        Tile index = tc.cast(tc.iota(DType.S32, 1, HALF_DIM), DType.F32);
        Tile invFrequency = tc.exp(tc.scale(index, -logBaseOverDim));
        Tile angle = tc.scale(invFrequency, position);
        Tile cos = tc.cos(angle);
        Tile sin = tc.sin(angle);

        Tile x1 = v.load(row, 0);
        Tile x2 = v.load(row, 1);
        v.store(tc.sub(tc.mul(x1, cos), tc.mul(x2, sin)), row, 0);
        v.store(tc.add(tc.mul(x2, cos), tc.mul(x1, sin)), row, 1);
    }

    /**
     * Batched matmul, {@code C[b] = A[b] * B[b]}.
     *
     * <p>
     * There is no rank-3 view and no slicing, and none is needed: each batch is a contiguous
     * block of rows, so viewing the buffers as {@code (batch * m, k)} and deriving the batch
     * from the block index addresses it with arithmetic alone. The grid is
     * {@code (batch * m / TILE_M, n / TILE_N)}, and the row block within a batch is
     * {@code bidX % (m / TILE_M)}.
     * </p>
     */
    public static void bmm(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int batch, int m, int n, int k, int rowBlocks) {
        PartitionView av = tc.partition(tc.view(a, batch * m, k), TILE_M, TILE_K);
        PartitionView bv = tc.partition(tc.view(b, batch * k, n), TILE_K, TILE_N);
        PartitionView cv = tc.partition(tc.view(c, batch * m, n), TILE_M, TILE_N);

        int flatRowBlock = tc.bidX();
        int batchIndex = flatRowBlock / rowBlocks;
        int columnBlock = tc.bidY();
        int kBlocks = k / TILE_K;
        int bRowBase = batchIndex * kBlocks;

        Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
        for (int step = 0; step < kBlocks; step++) {
            acc = tc.mma(av.load(flatRowBlock, step), bv.load(bRowBase + step, columnBlock), acc);
        }
        cv.store(acc, flatRowBlock, columnBlock);
    }

    /**
     * Dropout applied from a precomputed mask, which is how a deterministic test can check it:
     * {@code out = in * mask * scale}. Exercises a second input tile consumed elementwise.
     */
    public static void dropout(TileContext tc, FloatArray in, FloatArray mask, FloatArray out, int rows, float keepScale) {
        PartitionView iv = tc.partition(tc.view(in, rows, HALF_DIM), 1, HALF_DIM);
        PartitionView mv = tc.partition(tc.view(mask, rows, HALF_DIM), 1, HALF_DIM);
        PartitionView ov = tc.partition(tc.view(out, rows, HALF_DIM), 1, HALF_DIM);
        int row = tc.bidX();
        ov.store(tc.scale(tc.mul(iv.load(row, 0), mv.load(row, 0)), keepScale), row, 0);
    }

    @Test
    public void testRope() throws TornadoExecutionPlanException {
        final int rows = 128;
        final int dim = 2 * HALF_DIM;
        final float base = 10000.0f;
        final float position = 7.0f;
        final float logBaseOverDim = (float) (Math.log(base) * 2.0 / dim);

        FloatArray data = new FloatArray(rows * dim);
        FloatArray original = new FloatArray(rows * dim);
        java.util.Random random = new java.util.Random(17);
        for (int i = 0; i < rows * dim; i++) {
            float v = random.nextFloat() - 0.5f;
            data.set(i, v);
            original.set(i, v);
        }

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("rope.k", worker);
        TaskGraph graph = new TaskGraph("rope") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("k", TestTileLlmKernels::rope, new TileContext(), data, rows, logBaseOverDim, position) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < HALF_DIM; i++) {
                double angle = position * Math.exp(-logBaseOverDim * i);
                double x1 = original.get(row * dim + i);
                double x2 = original.get(row * dim + HALF_DIM + i);
                assertEquals("row " + row + " first half " + i, x1 * Math.cos(angle) - x2 * Math.sin(angle), data.get(row * dim + i), 1e-4);
                assertEquals("row " + row + " second half " + i, x2 * Math.cos(angle) + x1 * Math.sin(angle), data.get(row * dim + HALF_DIM + i), 1e-4);
            }
        }
    }

    private void checkBmm(int batch, int m, int n, int k) throws TornadoExecutionPlanException {
        HalfFloatArray a = new HalfFloatArray(batch * m * k);
        HalfFloatArray b = new HalfFloatArray(batch * k * n);
        FloatArray c = new FloatArray(batch * m * n);
        java.util.Random random = new java.util.Random(29 + batch);
        for (int i = 0; i < batch * m * k; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < batch * k * n; i++) {
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        int rowBlocks = m / TILE_M;
        WorkerGrid2D worker = new WorkerGrid2D(batch * rowBlocks, n / TILE_N);
        GridScheduler grid = new GridScheduler("bmm.k", worker);
        TaskGraph graph = new TaskGraph("bmm") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileLlmKernels::bmm, new TileContext(), a, b, c, batch, m, n, k, rowBlocks) //
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
                    assertEquals("batch " + batchIndex + " (" + row + "," + column + ")", expected, c.get(batchIndex * m * n + row * n + column), 0.05f);
                }
            }
        }
    }

    @Test
    public void testBmmSmallBatch() throws TornadoExecutionPlanException {
        checkBmm(4, 32, 32, 32);
    }

    @Test
    public void testBmmLargerBatch() throws TornadoExecutionPlanException {
        checkBmm(8, 64, 64, 64);
    }

    @Test
    public void testDropoutWithMask() throws TornadoExecutionPlanException {
        final int rows = 96;
        final float keepProbability = 0.8f;
        final float keepScale = 1.0f / keepProbability;
        FloatArray in = new FloatArray(rows * HALF_DIM);
        FloatArray mask = new FloatArray(rows * HALF_DIM);
        FloatArray out = new FloatArray(rows * HALF_DIM);
        java.util.Random random = new java.util.Random(37);
        for (int i = 0; i < rows * HALF_DIM; i++) {
            in.set(i, random.nextFloat() - 0.5f);
            mask.set(i, random.nextFloat() < keepProbability ? 1.0f : 0.0f);
        }

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("drop.k", worker);
        TaskGraph graph = new TaskGraph("drop") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, mask) //
                .task("k", TestTileLlmKernels::dropout, new TileContext(), in, mask, out, rows, keepScale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < rows * HALF_DIM; i++) {
            assertEquals("element " + i, in.get(i) * mask.get(i) * keepScale, out.get(i), 1e-5);
        }
    }
}
