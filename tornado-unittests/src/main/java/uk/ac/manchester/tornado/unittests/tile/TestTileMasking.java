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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The data-dependent kernels, which need a comparison and a {@code ct::select}.
 *
 * <p>
 * Until those two existed a tile kernel could only compute the same arithmetic for every
 * element, and the suite said so: causal attention and a ragged key tail were both listed as
 * not expressible. They are the reason comparisons are in the API, so they are what tests
 * them - together with the elementwise cases that are easy to check by hand (ReLU, clamp,
 * thresholding) and the two reductions that arrived with them.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMasking
 * </pre>
 */
public class TestTileMasking extends TornadoTestBase {

    private static final int HEAD_DIM = 64;

    private static final int BLOCK_M = 32;

    private static final int BLOCK_N = 32;

    private static final int ROW_WIDTH = 256;

    /** See {@link TestTileAttention} for why this is not {@code -inf}. */
    private static final float NEGATIVE_LIMIT = -1.0e30f;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    // -------------------------------------------------------------------------------------
    // Elementwise, where the expected value is obvious
    // -------------------------------------------------------------------------------------

    /** ReLU from a comparison and a select, rather than from {@code maximum} against zero. */
    public static void reluBySelect(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, ROW_WIDTH), 1, ROW_WIDTH);
        int row = tc.bidX();

        Tile x = iv.load(row, 0);
        Tile zeros = tc.zeros(DType.F32, 1, ROW_WIDTH);
        ov.store(tc.select(tc.greaterThan(x, 0.0), x, zeros), row, 0);
    }

    /**
     * Clamp to {@code [low, high]} with the elementwise two-operand forms, which is what
     * {@code ct::min} and {@code ct::max} are for; {@code minimum} is the one that was missing.
     */
    public static void clamp(TileContext tc, FloatArray in, FloatArray out, int rows, float low, float high) {
        PartitionView iv = tc.partition(tc.view(in, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, ROW_WIDTH), 1, ROW_WIDTH);
        int row = tc.bidX();

        Tile x = iv.load(row, 0);
        Tile lowTile = tc.zeros(DType.F32, 1, ROW_WIDTH);
        Tile highTile = tc.zeros(DType.F32, 1, ROW_WIDTH);
        // The bounds are runtime values, so they arrive through scale rather than full.
        Tile ones = tc.full(DType.F32, 1.0, 1, ROW_WIDTH);
        lowTile = tc.add(lowTile, tc.scale(ones, low));
        highTile = tc.add(highTile, tc.scale(ones, high));
        ov.store(tc.minimum(tc.maximum(x, lowTile), highTile), row, 0);
    }

    /**
     * Counts the elements of a row above a runtime threshold, by selecting ones and zeros and
     * summing. Exercises the tile-to-scalar comparison, where the right-hand side cannot fold.
     */
    public static void countAbove(TileContext tc, FloatArray in, FloatArray out, int rows, float threshold) {
        PartitionView iv = tc.partition(tc.view(in, rows, ROW_WIDTH), 1, ROW_WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, 1), 1, 1);
        int row = tc.bidX();

        Tile x = iv.load(row, 0);
        Tile ones = tc.full(DType.F32, 1.0, 1, ROW_WIDTH);
        Tile zeros = tc.zeros(DType.F32, 1, ROW_WIDTH);
        Tile hits = tc.select(tc.greaterThan(x, threshold), ones, zeros);
        ov.store(tc.sum(hits, 1), row, 0);
    }

    /** Row minimum and row product, the two reductions that arrived with the comparisons. */
    public static void minAndProduct(TileContext tc, FloatArray in, FloatArray minimums, FloatArray products, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, 8), 1, 8);
        PartitionView minView = tc.partition(tc.view(minimums, rows, 1), 1, 1);
        PartitionView productView = tc.partition(tc.view(products, rows, 1), 1, 1);
        int row = tc.bidX();

        Tile x = iv.load(row, 0);
        minView.store(tc.min(x, 1), row, 0);
        productView.store(tc.prod(x, 1), row, 0);
    }

    // -------------------------------------------------------------------------------------
    // Causal attention, and a ragged key tail
    // -------------------------------------------------------------------------------------

    /**
     * Causal flash attention: a query attends only to keys at or before its own position.
     *
     * <p>
     * This is TileGym's {@code IS_CAUSAL} branch of {@code ops/tilecpp/attention.cuh}. The mask
     * is built the way the original builds it - from index tiles rather than from a table - but
     * a rank-2 API has to say it slightly differently. The row and column index tiles come from
     * {@code iota} broadcast to the score shape, and the comparison is against
     * {@code keyBlock * BLOCK_N - queryBlock * BLOCK_M}: shifting the threshold instead of the
     * indices keeps both block offsets, which are runtime values, out of the tiles, where they
     * would need a {@code full(...)} whose fill value has to be a compile-time constant.
     * </p>
     *
     * <p>
     * Keys strictly above the diagonal become {@link #NEGATIVE_LIMIT} before the exponential,
     * which is the part a zero-padded masked load cannot do: a padded key would contribute
     * {@code exp(0 - m)} to the denominator rather than nothing.
     * </p>
     */
    public static void causalAttention(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int queryRows, int kvRows, float scale) {
        PartitionView qView = tc.partition(tc.view(q, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView kView = tc.partition(tc.view(k, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView vView = tc.partition(tc.view(v, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView outView = tc.partition(tc.view(out, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);

        int queryBlock = tc.bidX();

        // Index tiles at the score shape: iota down the rows and along the columns, each
        // stretched to [BLOCK_M, BLOCK_N] by broadcast.
        Tile rowIndex = tc.broadcast(tc.iota(DType.S32, BLOCK_M, 1), BLOCK_M, BLOCK_N);
        Tile columnIndex = tc.broadcast(tc.iota(DType.S32, 1, BLOCK_N), BLOCK_M, BLOCK_N);
        Tile rowMinusColumn = tc.sub(rowIndex, columnIndex);
        Tile masked = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, BLOCK_N);

        Tile query = qView.load(queryBlock, 0);
        Tile rowMax = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, 1);
        Tile rowSum = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        // Only the key blocks that can contain a visible key: everything past the diagonal
        // block contributes nothing, so the loop stops there rather than masking it all away.
        int lastBlock = (queryBlock * BLOCK_M + BLOCK_M) / BLOCK_N;
        for (int block = 0; block < lastBlock; block++) {
            Tile keys = tc.transpose(kView.load(block, 0));
            Tile raw = tc.scale(tc.mma(query, keys, tc.zeros(DType.F32, BLOCK_M, BLOCK_N)), scale);

            int threshold = block * BLOCK_N - queryBlock * BLOCK_M;
            Tile visible = tc.greaterOrEqual(rowMinusColumn, threshold);
            Tile scores = tc.select(visible, raw, masked);

            Tile newMax = tc.maximum(rowMax, tc.max(scores, 1));
            Tile probabilities = tc.exp(tc.sub(scores, newMax));
            Tile correction = tc.exp(tc.sub(rowMax, newMax));
            rowSum = tc.add(tc.mul(rowSum, correction), tc.sum(probabilities, 1));
            acc = tc.mul(acc, correction);
            acc = tc.mma(tc.cast(probabilities, DType.F16), vView.load(block, 0), acc);
            rowMax = newMax;
        }

        outView.store(tc.div(acc, rowSum), queryBlock, 0);
    }

    /**
     * Attention over a KV sequence whose length is not a multiple of the KV block.
     *
     * <p>
     * The tail block is loaded with {@code loadMasked}, which zero-pads it, and then the padded
     * columns are masked out of the softmax with a comparison against the runtime key count.
     * Both halves are needed: the masked load keeps the read in bounds, the select keeps the
     * padding out of the denominator.
     * </p>
     */
    public static void raggedKeyTail(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int queryRows, int kvRows, int kvBlocks, float scale) {
        PartitionView qView = tc.partition(tc.view(q, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView kView = tc.partition(tc.view(k, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView vView = tc.partition(tc.view(v, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView outView = tc.partition(tc.view(out, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);

        int queryBlock = tc.bidX();
        Tile columnIndex = tc.broadcast(tc.iota(DType.S32, 1, BLOCK_N), BLOCK_M, BLOCK_N);
        Tile masked = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, BLOCK_N);

        Tile query = qView.load(queryBlock, 0);
        Tile rowMax = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, 1);
        Tile rowSum = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        for (int block = 0; block < kvBlocks; block++) {
            Tile keys = tc.transpose(kView.loadMasked(block, 0));
            Tile raw = tc.scale(tc.mma(query, keys, tc.zeros(DType.F32, BLOCK_M, BLOCK_N)), scale);

            // Columns at or past this many are padding rather than keys.
            int validColumns = kvRows - block * BLOCK_N;
            Tile scores = tc.select(tc.lessThan(columnIndex, validColumns), raw, masked);

            Tile newMax = tc.maximum(rowMax, tc.max(scores, 1));
            Tile probabilities = tc.exp(tc.sub(scores, newMax));
            Tile correction = tc.exp(tc.sub(rowMax, newMax));
            rowSum = tc.add(tc.mul(rowSum, correction), tc.sum(probabilities, 1));
            acc = tc.mul(acc, correction);
            acc = tc.mma(tc.cast(probabilities, DType.F16), vView.loadMasked(block, 0), acc);
            rowMax = newMax;
        }

        outView.store(tc.div(acc, rowSum), queryBlock, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private static FloatArray randomRows(int rows, int columns, long seed) {
        FloatArray array = new FloatArray(rows * columns);
        Random random = new Random(seed);
        for (int i = 0; i < rows * columns; i++) {
            array.set(i, 2.0f * random.nextFloat() - 1.0f);
        }
        return array;
    }

    private static HalfFloatArray randomHalf(int elements, long seed) {
        HalfFloatArray array = new HalfFloatArray(elements);
        Random random = new Random(seed);
        for (int i = 0; i < elements; i++) {
            array.set(i, new HalfFloat(random.nextFloat() - 0.5f));
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
    public void testReluBySelect() throws TornadoExecutionPlanException {
        final int rows = 64;
        FloatArray in = randomRows(rows, ROW_WIDTH, 701);
        FloatArray out = new FloatArray(rows * ROW_WIDTH);

        run("relu.k", new TaskGraph("relu") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMasking::reluBySelect, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int i = 0; i < rows * ROW_WIDTH; i++) {
            assertEquals("element " + i, Math.max(0.0f, in.get(i)), out.get(i), 1e-6);
        }
    }

    @Test
    public void testClamp() throws TornadoExecutionPlanException {
        final int rows = 48;
        final float low = -0.25f;
        final float high = 0.5f;
        FloatArray in = randomRows(rows, ROW_WIDTH, 703);
        FloatArray out = new FloatArray(rows * ROW_WIDTH);

        run("clamp.k", new TaskGraph("clamp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMasking::clamp, new TileContext(), in, out, rows, low, high) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int i = 0; i < rows * ROW_WIDTH; i++) {
            assertEquals("element " + i, Math.min(Math.max(in.get(i), low), high), out.get(i), 1e-6);
        }
    }

    @Test
    public void testCountAboveThreshold() throws TornadoExecutionPlanException {
        final int rows = 32;
        final float threshold = 0.25f;
        FloatArray in = randomRows(rows, ROW_WIDTH, 705);
        FloatArray out = new FloatArray(rows);

        run("count.k", new TaskGraph("count") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMasking::countAbove, new TileContext(), in, out, rows, threshold) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), rows);

        for (int row = 0; row < rows; row++) {
            int expected = 0;
            for (int i = 0; i < ROW_WIDTH; i++) {
                if (in.get(row * ROW_WIDTH + i) > threshold) {
                    expected++;
                }
            }
            assertEquals("row " + row, expected, out.get(row), 1e-6);
        }
    }

    @Test
    public void testMinAndProduct() throws TornadoExecutionPlanException {
        final int rows = 32;
        final int width = 8;
        FloatArray in = new FloatArray(rows * width);
        Random random = new Random(707);
        for (int i = 0; i < rows * width; i++) {
            // Kept near one so the eight-element product stays well inside float range.
            in.set(i, 0.5f + random.nextFloat());
        }
        FloatArray minimums = new FloatArray(rows);
        FloatArray products = new FloatArray(rows);

        run("stats.k", new TaskGraph("stats") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileMasking::minAndProduct, new TileContext(), in, minimums, products, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, minimums, products), rows);

        for (int row = 0; row < rows; row++) {
            float expectedMinimum = Float.MAX_VALUE;
            double expectedProduct = 1.0;
            for (int i = 0; i < width; i++) {
                float value = in.get(row * width + i);
                expectedMinimum = Math.min(expectedMinimum, value);
                expectedProduct *= value;
            }
            assertEquals("row " + row + " minimum", expectedMinimum, minimums.get(row), 1e-6);
            assertEquals("row " + row + " product", expectedProduct, products.get(row), 1e-3);
        }
    }

    /**
     * Reference attention over a window of keys: {@code [0, visible)} for that query row, which
     * is the whole sequence for the non-causal case and the diagonal for the causal one.
     */
    private static double[] referenceAttention(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, int queryRows, int kvRows, float scale, boolean causal) {
        double[] result = new double[queryRows * HEAD_DIM];
        for (int row = 0; row < queryRows; row++) {
            int visible = causal ? Math.min(row + 1, kvRows) : kvRows;
            double[] scores = new double[visible];
            double maximum = Double.NEGATIVE_INFINITY;
            for (int key = 0; key < visible; key++) {
                double dot = 0.0;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get(row * HEAD_DIM + d).getFloat32() * k.get(key * HEAD_DIM + d).getFloat32();
                }
                scores[key] = dot * scale;
                maximum = Math.max(maximum, scores[key]);
            }
            double denominator = 0.0;
            for (int key = 0; key < visible; key++) {
                scores[key] = Math.exp(scores[key] - maximum);
                denominator += scores[key];
            }
            for (int key = 0; key < visible; key++) {
                double weight = scores[key] / denominator;
                for (int d = 0; d < HEAD_DIM; d++) {
                    result[row * HEAD_DIM + d] += weight * v.get(key * HEAD_DIM + d).getFloat32();
                }
            }
        }
        return result;
    }

    private void checkCausal(int queryRows, int kvRows) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 801);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 803);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 809);
        FloatArray out = new FloatArray(queryRows * HEAD_DIM);

        run("causal.k", new TaskGraph("causal") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .task("k", TestTileMasking::causalAttention, new TileContext(), q, k, v, out, queryRows, kvRows, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), queryRows / BLOCK_M);

        double[] expected = referenceAttention(q, k, v, queryRows, kvRows, scale, true);
        for (int i = 0; i < queryRows * HEAD_DIM; i++) {
            assertEquals("element " + i, expected[i], out.get(i), 0.01);
        }
    }

    /** One query block against one key block: the mask is the diagonal of a single tile. */
    @Test
    public void testCausalSingleBlock() throws TornadoExecutionPlanException {
        checkCausal(BLOCK_M, BLOCK_N);
    }

    /**
     * Four query blocks: the later ones have fully visible key blocks as well as a diagonal
     * one, so both the loop bound and the mask have to be right.
     */
    @Test
    public void testCausalSeveralBlocks() throws TornadoExecutionPlanException {
        checkCausal(4 * BLOCK_M, 4 * BLOCK_N);
    }

    /** Eight query blocks, to make the diagonal block a small fraction of the work. */
    @Test
    public void testCausalLongerSequence() throws TornadoExecutionPlanException {
        checkCausal(8 * BLOCK_M, 8 * BLOCK_N);
    }

    private void checkRagged(int queryRows, int kvRows) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 811);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 821);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 823);
        FloatArray out = new FloatArray(queryRows * HEAD_DIM);
        int kvBlocks = (kvRows + BLOCK_N - 1) / BLOCK_N;

        run("ragged.k", new TaskGraph("ragged") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .task("k", TestTileMasking::raggedKeyTail, new TileContext(), q, k, v, out, queryRows, kvRows, kvBlocks, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out), queryRows / BLOCK_M);

        double[] expected = referenceAttention(q, k, v, queryRows, kvRows, scale, false);
        for (int i = 0; i < queryRows * HEAD_DIM; i++) {
            assertEquals("element " + i, expected[i], out.get(i), 0.01);
        }
    }

    /** A tail of one key: the most lopsided masked block there is. */
    @Test
    public void testRaggedTailOneKey() throws TornadoExecutionPlanException {
        checkRagged(BLOCK_M, 2 * BLOCK_N + 1);
    }

    /** A tail of half a block. */
    @Test
    public void testRaggedTailHalfBlock() throws TornadoExecutionPlanException {
        checkRagged(2 * BLOCK_M, 3 * BLOCK_N + BLOCK_N / 2);
    }

    /** A KV length shorter than one block, so every block is the tail. */
    @Test
    public void testRaggedShorterThanOneBlock() throws TornadoExecutionPlanException {
        checkRagged(BLOCK_M, 17);
    }
}
