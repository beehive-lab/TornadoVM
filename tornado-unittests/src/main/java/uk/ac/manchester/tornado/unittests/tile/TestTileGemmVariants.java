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
 * The matmul variants from NVIDIA's TileGym suite that the plain tiled GEMM does not reach:
 * the static-persistent kernel with group-M swizzling
 * ({@code ops/tilecpp/persistent_matmul.cuh}) and the four transposition cases of
 * {@code ops/tilecpp/matmul.cuh}.
 *
 * <p>
 * Each is a different demand on the code generator rather than a different arithmetic result.
 * The persistent kernel decouples the grid from the output shape - one block emits several
 * output tiles, so the accumulator loop sits inside a second loop whose trip count depends on
 * {@code numBlocksX()}, and both loops carry tiles. The transposed cases put a
 * {@code transpose} between a load and an {@code mma}, which is where the tile abstraction earns
 * its keep: the operand layout is the tile compiler's problem, not the kernel author's.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileGemmVariants
 * </pre>
 */
public class TestTileGemmVariants extends TornadoTestBase {

    private static final int TILE_M = 32;

    private static final int TILE_N = 32;

    private static final int TILE_K = 32;

    /**
     * Row blocks per swizzle group. TileGym's {@code GROUP_SIZE_M}: consecutive tile ids are
     * grouped so that the blocks running at the same time share operand tiles, which is a cache
     * decision and does not change the result.
     */
    private static final int GROUP_M = 2;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Static-persistent matmul with group-M swizzling.
     *
     * <p>
     * The grid is sized to the machine rather than to the problem: each block starts at its own
     * block index and strides by the total number of blocks, so a launch of four blocks covers
     * sixteen output tiles in four passes. That is the whole point of the persistent form - the
     * launch is independent of the output shape - and it is why this kernel is the one that
     * needs {@code numBlocksX()}.
     * </p>
     *
     * <p>
     * The swizzle itself is integer arithmetic on the flat tile id, reproducing TileGym's
     * {@code group_id} / {@code first_bid_m} / {@code group_size_m} computation. The clamp of the
     * last group is written as a conditional rather than {@code Math.min} so the kernel says
     * exactly what it lowers to.
     * </p>
     */
    public static void persistentMatmul(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, int rowBlocks, int columnBlocks, int kBlocks, int tiles) {
        PartitionView aView = tc.partition(tc.view(a, m, k), TILE_M, TILE_K);
        PartitionView bView = tc.partition(tc.view(b, k, n), TILE_K, TILE_N);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE_M, TILE_N);

        int start = tc.bidX();
        int programs = tc.numBlocksX();
        int blocksInGroup = GROUP_M * columnBlocks;

        for (int tileId = start; tileId < tiles; tileId += programs) {
            int groupId = tileId / blocksInGroup;
            int firstRowBlock = groupId * GROUP_M;
            int remaining = rowBlocks - firstRowBlock;
            int groupRows = remaining < GROUP_M ? remaining : GROUP_M;
            int rowBlock = firstRowBlock + (tileId % groupRows);
            int columnBlock = (tileId % blocksInGroup) / groupRows;

            Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
            for (int step = 0; step < kBlocks; step++) {
                acc = tc.mma(aView.load(rowBlock, step), bView.load(step, columnBlock), acc);
            }
            cView.store(acc, rowBlock, columnBlock);
        }
    }

    /**
     * {@code C = A * Bt}, with B stored as {@code [n, k]}.
     *
     * <p>
     * The B tile arrives as {@code [TILE_N, TILE_K]} and is transposed before the multiply.
     * TileGym reaches the same place with a {@code permute} of a rank-4 tile; a rank-2 view needs
     * only {@code transpose}. Either way the transposition never becomes a memory movement the
     * kernel author has to stage - {@code tileiras} folds it into the operand layout.
     * </p>
     */
    public static void matmulTransposedB(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, int kBlocks) {
        PartitionView aView = tc.partition(tc.view(a, m, k), TILE_M, TILE_K);
        PartitionView bView = tc.partition(tc.view(b, n, k), TILE_N, TILE_K);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE_M, TILE_N);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
        for (int step = 0; step < kBlocks; step++) {
            acc = tc.mma(aView.load(rowBlock, step), tc.transpose(bView.load(columnBlock, step)), acc);
        }
        cView.store(acc, rowBlock, columnBlock);
    }

    /** {@code C = At * B}, with A stored as {@code [k, m]}. */
    public static void matmulTransposedA(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, int kBlocks) {
        PartitionView aView = tc.partition(tc.view(a, k, m), TILE_K, TILE_M);
        PartitionView bView = tc.partition(tc.view(b, k, n), TILE_K, TILE_N);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE_M, TILE_N);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
        for (int step = 0; step < kBlocks; step++) {
            acc = tc.mma(tc.transpose(aView.load(step, rowBlock)), bView.load(step, columnBlock), acc);
        }
        cView.store(acc, rowBlock, columnBlock);
    }

    /** {@code C = At * Bt}, the remaining branch of TileGym's transposition matrix. */
    public static void matmulTransposedBoth(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, int kBlocks) {
        PartitionView aView = tc.partition(tc.view(a, k, m), TILE_K, TILE_M);
        PartitionView bView = tc.partition(tc.view(b, n, k), TILE_N, TILE_K);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE_M, TILE_N);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.F32, TILE_M, TILE_N);
        for (int step = 0; step < kBlocks; step++) {
            acc = tc.mma(tc.transpose(aView.load(step, rowBlock)), tc.transpose(bView.load(columnBlock, step)), acc);
        }
        cView.store(acc, rowBlock, columnBlock);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private static HalfFloatArray randomHalf(int elements, long seed) {
        HalfFloatArray array = new HalfFloatArray(elements);
        Random random = new Random(seed);
        for (int i = 0; i < elements; i++) {
            array.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        return array;
    }

    /**
     * Reference {@code C = A * B} read back from the fp16 buffers, with either operand optionally
     * stored transposed.
     */
    private static void assertMatmul(HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k, boolean transposeA, boolean transposeB) {
        for (int row = 0; row < m; row++) {
            for (int column = 0; column < n; column++) {
                float expected = 0.0f;
                for (int inner = 0; inner < k; inner++) {
                    float left = transposeA ? a.get(inner * m + row).getFloat32() : a.get(row * k + inner).getFloat32();
                    float right = transposeB ? b.get(column * k + inner).getFloat32() : b.get(inner * n + column).getFloat32();
                    expected += left * right;
                }
                assertEquals("(" + row + "," + column + ")", expected, c.get(row * n + column), 0.05f);
            }
        }
    }

    private void checkPersistentMatmul(int m, int n, int k, int programs) throws TornadoExecutionPlanException {
        HalfFloatArray a = randomHalf(m * k, 401);
        HalfFloatArray b = randomHalf(k * n, 409);
        FloatArray c = new FloatArray(m * n);

        int rowBlocks = m / TILE_M;
        int columnBlocks = n / TILE_N;
        int tiles = rowBlocks * columnBlocks;

        WorkerGrid1D worker = new WorkerGrid1D(programs);
        GridScheduler grid = new GridScheduler("persistent.k", worker);
        TaskGraph graph = new TaskGraph("persistent") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("k", TestTileGemmVariants::persistentMatmul, new TileContext(), a, b, c, m, n, k, rowBlocks, columnBlocks, k / TILE_K, tiles) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        assertMatmul(a, b, c, m, n, k, false, false);
    }

    /** Four blocks for sixteen output tiles: every block emits four of them. */
    @Test
    public void testPersistentMatmulFewerBlocksThanTiles() throws TornadoExecutionPlanException {
        checkPersistentMatmul(128, 128, 64, 4);
    }

    /** One block for the whole output, the extreme of the persistent form. */
    @Test
    public void testPersistentMatmulSingleBlock() throws TornadoExecutionPlanException {
        checkPersistentMatmul(64, 64, 64, 1);
    }

    /**
     * More blocks than output tiles, so some blocks emit one tile and the rest none - the
     * loop-entry guard has to hold for a block whose first tile id is already past the end.
     */
    @Test
    public void testPersistentMatmulMoreBlocksThanTiles() throws TornadoExecutionPlanException {
        checkPersistentMatmul(64, 64, 32, 8);
    }

    /**
     * A row-block count that is not a multiple of {@link #GROUP_M}, so the final swizzle group is
     * clamped. Getting the clamp wrong aliases two tile ids onto one output tile and leaves
     * another unwritten, which the full-output comparison catches.
     */
    @Test
    public void testPersistentMatmulRaggedSwizzleGroup() throws TornadoExecutionPlanException {
        checkPersistentMatmul(96, 64, 64, 3);
    }

    private void checkTransposed(int m, int n, int k, boolean transposeA, boolean transposeB) throws TornadoExecutionPlanException {
        HalfFloatArray a = randomHalf(m * k, 503);
        HalfFloatArray b = randomHalf(k * n, 509);
        FloatArray c = new FloatArray(m * n);
        int kBlocks = k / TILE_K;

        WorkerGrid2D worker = new WorkerGrid2D(m / TILE_M, n / TILE_N);
        GridScheduler grid = new GridScheduler("transposed.k", worker);
        TaskGraph graph = new TaskGraph("transposed") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);
        if (transposeA && transposeB) {
            graph.task("k", TestTileGemmVariants::matmulTransposedBoth, new TileContext(), a, b, c, m, n, k, kBlocks);
        } else if (transposeA) {
            graph.task("k", TestTileGemmVariants::matmulTransposedA, new TileContext(), a, b, c, m, n, k, kBlocks);
        } else {
            graph.task("k", TestTileGemmVariants::matmulTransposedB, new TileContext(), a, b, c, m, n, k, kBlocks);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        assertMatmul(a, b, c, m, n, k, transposeA, transposeB);
    }

    @Test
    public void testMatmulTransposedB() throws TornadoExecutionPlanException {
        checkTransposed(64, 64, 64, false, true);
    }

    @Test
    public void testMatmulTransposedA() throws TornadoExecutionPlanException {
        checkTransposed(64, 64, 64, true, false);
    }

    @Test
    public void testMatmulTransposedBoth() throws TornadoExecutionPlanException {
        checkTransposed(64, 64, 64, true, true);
    }

    /** A non-square case, so a transposed operand cannot accidentally agree by symmetry. */
    @Test
    public void testMatmulTransposedBRectangular() throws TornadoExecutionPlanException {
        checkTransposed(96, 64, 32, false, true);
    }
}
