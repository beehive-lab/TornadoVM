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
 * Attention kernels ported from NVIDIA's TileGym suite: flash attention with online softmax
 * ({@code ops/tilecpp/attention.cuh}) and the split-K decode reduction
 * ({@code ops/tilecpp/splitk_reduce.cuh}).
 *
 * <p>
 * These are the most demanding ports in the suite, and they are here because flash attention is
 * the kernel the whole tile abstraction exists for. In one loop body it needs a matmul, a row
 * reduction, a row broadcast against a full tile, a transcendental, a narrowing cast back to the
 * operand type and a second matmul that accumulates into a tile carried across the loop — three
 * loop-carried tiles at once, over a trip count that is only known at runtime.
 * </p>
 *
 * <p>
 * Two deliberate deviations from the TileGym originals, both because the Java surface has no
 * equivalent yet:
 * </p>
 * <ul>
 * <li>{@code exp} replaces {@code ct::exp2}. TileGym folds {@code 1/ln 2} into the QK scale and
 * exponentiates base two because that is the cheaper instruction; with a natural exponential the
 * scale is used as given. The softmax is identical either way.</li>
 * <li>Causal masking is omitted. It needs a boolean tile from a comparison and a
 * {@code ct::select}, and neither is exposed by {@link TileContext} yet; masking the ragged tail
 * of a KV sequence has the same requirement, so the KV length is kept a multiple of the KV block
 * here. Zero-padded {@code loadMasked} is not a substitute: a padded key contributes
 * {@code exp(0 - m)} to the denominator rather than nothing.</li>
 * </ul>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileAttention
 * </pre>
 */
public class TestTileAttention extends TornadoTestBase {

    /** Head dimension, and therefore the full width of every tile: a compile-time literal. */
    private static final int HEAD_DIM = 64;

    /** Query rows per tile block. */
    private static final int BLOCK_M = 32;

    /** Key/value rows per tile block. */
    private static final int BLOCK_N = 32;

    /**
     * Stands in for negative infinity as the initial row maximum. TileGym does the same (its
     * {@code SPLITK_NEG_INF}) and for the same reason: with a true {@code -inf} the first
     * rescaling factor is {@code exp(-inf - m)}, and the zero it produces multiplies an
     * accumulator that is itself zero, which on some paths is a NaN rather than a zero.
     */
    private static final float NEGATIVE_LIMIT = -1.0e30f;

    /** Split counts for the decode reduction, baked in as tile shapes. */
    private static final int SPLITS_SMALL = 4;

    private static final int SPLITS_LARGE = 16;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * Flash attention forward, one head, non-causal.
     *
     * <p>
     * The online softmax is the point of the kernel: rather than materialising the full
     * {@code [S_QO, S_KV]} score matrix, it walks the KV sequence one block at a time and keeps
     * three running tiles — the row maximum {@code m}, the row denominator {@code l} and the
     * unnormalised output {@code acc}. When a block raises a row's maximum, everything
     * accumulated so far for that row is rescaled by {@code exp(m_old - m_new)}, which is what
     * makes the single pass numerically equal to a two-pass softmax.
     * </p>
     */
    public static void flashAttention(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int queryRows, int kvRows, int kvBlocks, float scale) {
        PartitionView qView = tc.partition(tc.view(q, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView kView = tc.partition(tc.view(k, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView vView = tc.partition(tc.view(v, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView outView = tc.partition(tc.view(out, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);

        int queryBlock = tc.bidX();
        Tile query = qView.load(queryBlock, 0);

        Tile rowMax = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, 1);
        Tile rowSum = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        for (int block = 0; block < kvBlocks; block++) {
            // scores = (Q Kt) * scale, with K transposed from [BLOCK_N, HEAD_DIM] in place of
            // TileGym's permute of a rank-4 tile.
            Tile keys = tc.transpose(kView.load(block, 0));
            Tile scores = tc.scale(tc.mma(query, keys, tc.zeros(DType.F32, BLOCK_M, BLOCK_N)), scale);

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
     * Flash attention over several heads, with grouped-query attention.
     *
     * <p>
     * There is no rank-4 view, and the kernel does not need one: with a two-dimensional grid the
     * head comes from {@code bidY} and every tensor is viewed as {@code (heads * sequence,
     * HEAD_DIM)}, so selecting a head is arithmetic on the block row. That is also what makes
     * GQA a one-line change — several query heads map to one KV head by integer division, which
     * is exactly what TileGym's {@code off_kv_h} computes.
     * </p>
     */
    public static void flashAttentionHeads(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int totalQueryRows, int totalKvRows, int queryBlocksPerHead,
            int kvBlocksPerHead, int queryGroupSize, float scale) {
        PartitionView qView = tc.partition(tc.view(q, totalQueryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView kView = tc.partition(tc.view(k, totalKvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView vView = tc.partition(tc.view(v, totalKvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView outView = tc.partition(tc.view(out, totalQueryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);

        int head = tc.bidY();
        int queryBlock = head * queryBlocksPerHead + tc.bidX();
        int kvBase = (head / queryGroupSize) * kvBlocksPerHead;

        Tile query = qView.load(queryBlock, 0);
        Tile rowMax = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, 1);
        Tile rowSum = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        for (int block = 0; block < kvBlocksPerHead; block++) {
            Tile keys = tc.transpose(kView.load(kvBase + block, 0));
            Tile scores = tc.scale(tc.mma(query, keys, tc.zeros(DType.F32, BLOCK_M, BLOCK_N)), scale);
            Tile newMax = tc.maximum(rowMax, tc.max(scores, 1));
            Tile probabilities = tc.exp(tc.sub(scores, newMax));
            Tile correction = tc.exp(tc.sub(rowMax, newMax));
            rowSum = tc.add(tc.mul(rowSum, correction), tc.sum(probabilities, 1));
            acc = tc.mul(acc, correction);
            acc = tc.mma(tc.cast(probabilities, DType.F16), vView.load(kvBase + block, 0), acc);
            rowMax = newMax;
        }

        outView.store(tc.div(acc, rowSum), queryBlock, 0);
    }

    /**
     * The split-K decode reduction, four splits.
     *
     * <p>
     * Attention decode splits the KV sequence across blocks, each producing an unnormalised
     * output row and the log-sum-exp of its own scores. This kernel combines them, and it cannot
     * simply average: each partial carries a different softmax denominator, so it is weighted by
     * {@code exp(lse - max(lse))} and divided by the total. One tile block handles one row, so
     * the whole set of splits for that row is a single {@code [splits, HEAD_DIM]} tile.
     * </p>
     *
     * <p>
     * This is the {@code USE_DOT = false} branch of TileGym's kernel, the one that weights with a
     * broadcast multiply and an axis-0 reduction rather than a matmul against the weight vector.
     * The weight column comes from transposing the {@code [1, splits]} log-sum-exp tile, which is
     * how a rank-2 API expresses TileGym's {@code reshape}.
     * </p>
     *
     * <p>
     * The split count is a constant here, and it exists twice for that reason — it is
     * {@code NUM_KV_SPLITS_POW2}, a template parameter of the original, and a tile shape has to
     * be a compile-time constant on this side too. Writing it as a kernel parameter is rejected
     * at sketch time with an explicit message, and factoring the body into a shared helper does
     * not help: inlining happens after the shape has already had to fold.
     * </p>
     */
    public static void splitKReduceFourWay(TileContext tc, FloatArray partials, FloatArray logSumExp, FloatArray out, int rows) {
        PartitionView partialView = tc.partition(tc.view(partials, rows * SPLITS_SMALL, HEAD_DIM), SPLITS_SMALL, HEAD_DIM);
        PartitionView lseView = tc.partition(tc.view(logSumExp, rows, SPLITS_SMALL), 1, SPLITS_SMALL);
        PartitionView outView = tc.partition(tc.view(out, rows, HEAD_DIM), 1, HEAD_DIM);

        int row = tc.bidX();
        Tile lse = lseView.load(row, 0);
        Tile weights = tc.exp(tc.sub(lse, tc.max(lse, 1)));
        Tile total = tc.sum(weights, 1);

        Tile weighted = tc.mul(partialView.load(row, 0), tc.transpose(weights));
        outView.store(tc.div(tc.sum(weighted, 0), total), row, 0);
    }

    /** {@link #splitKReduceFourWay} with sixteen splits; see there for why the count is baked in. */
    public static void splitKReduceSixteenWay(TileContext tc, FloatArray partials, FloatArray logSumExp, FloatArray out, int rows) {
        PartitionView partialView = tc.partition(tc.view(partials, rows * SPLITS_LARGE, HEAD_DIM), SPLITS_LARGE, HEAD_DIM);
        PartitionView lseView = tc.partition(tc.view(logSumExp, rows, SPLITS_LARGE), 1, SPLITS_LARGE);
        PartitionView outView = tc.partition(tc.view(out, rows, HEAD_DIM), 1, HEAD_DIM);

        int row = tc.bidX();
        Tile lse = lseView.load(row, 0);
        Tile weights = tc.exp(tc.sub(lse, tc.max(lse, 1)));
        Tile total = tc.sum(weights, 1);

        Tile weighted = tc.mul(partialView.load(row, 0), tc.transpose(weights));
        outView.store(tc.div(tc.sum(weighted, 0), total), row, 0);
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
     * Reference attention for one head: the two-pass softmax the online version has to match.
     * Reads the fp16 inputs back so the comparison is against the values the device saw.
     */
    private static double[] referenceAttention(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, int queryOffset, int kvOffset, int queryRows, int kvRows, float scale) {
        double[] result = new double[queryRows * HEAD_DIM];
        for (int row = 0; row < queryRows; row++) {
            double[] scores = new double[kvRows];
            double maximum = Double.NEGATIVE_INFINITY;
            for (int key = 0; key < kvRows; key++) {
                double dot = 0.0;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get((queryOffset + row) * HEAD_DIM + d).getFloat32() * k.get((kvOffset + key) * HEAD_DIM + d).getFloat32();
                }
                scores[key] = dot * scale;
                maximum = Math.max(maximum, scores[key]);
            }
            double denominator = 0.0;
            for (int key = 0; key < kvRows; key++) {
                scores[key] = Math.exp(scores[key] - maximum);
                denominator += scores[key];
            }
            for (int key = 0; key < kvRows; key++) {
                double weight = scores[key] / denominator;
                for (int d = 0; d < HEAD_DIM; d++) {
                    result[row * HEAD_DIM + d] += weight * v.get((kvOffset + key) * HEAD_DIM + d).getFloat32();
                }
            }
        }
        return result;
    }

    private void checkFlashAttention(int queryRows, int kvRows) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 101);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 103);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 107);
        FloatArray out = new FloatArray(queryRows * HEAD_DIM);

        WorkerGrid1D worker = new WorkerGrid1D(queryRows / BLOCK_M);
        GridScheduler grid = new GridScheduler("fa.k", worker);
        TaskGraph graph = new TaskGraph("fa") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .task("k", TestTileAttention::flashAttention, new TileContext(), q, k, v, out, queryRows, kvRows, kvRows / BLOCK_N, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        double[] expected = referenceAttention(q, k, v, 0, 0, queryRows, kvRows, scale);
        for (int i = 0; i < queryRows * HEAD_DIM; i++) {
            assertEquals("element " + i, expected[i], out.get(i), 0.01);
        }
    }

    /** One KV block, so the rescaling path runs exactly once. */
    @Test
    public void testFlashAttentionSingleKvBlock() throws TornadoExecutionPlanException {
        checkFlashAttention(64, BLOCK_N);
    }

    /** Eight KV blocks: the running maximum moves repeatedly and every rescale must be applied. */
    @Test
    public void testFlashAttentionManyKvBlocks() throws TornadoExecutionPlanException {
        checkFlashAttention(128, 8 * BLOCK_N);
    }

    /** A KV sequence longer than the query sequence, as in cross attention. */
    @Test
    public void testFlashAttentionLongerKeys() throws TornadoExecutionPlanException {
        checkFlashAttention(BLOCK_M, 16 * BLOCK_N);
    }

    private void checkFlashAttentionHeads(int heads, int kvHeads, int queryRowsPerHead, int kvRowsPerHead) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(heads * queryRowsPerHead * HEAD_DIM, 211);
        HalfFloatArray k = randomHalf(kvHeads * kvRowsPerHead * HEAD_DIM, 223);
        HalfFloatArray v = randomHalf(kvHeads * kvRowsPerHead * HEAD_DIM, 227);
        FloatArray out = new FloatArray(heads * queryRowsPerHead * HEAD_DIM);

        int queryBlocksPerHead = queryRowsPerHead / BLOCK_M;
        WorkerGrid2D worker = new WorkerGrid2D(queryBlocksPerHead, heads);
        GridScheduler grid = new GridScheduler("gqa.k", worker);
        TaskGraph graph = new TaskGraph("gqa") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .task("k", TestTileAttention::flashAttentionHeads, new TileContext(), q, k, v, out, //
                        heads * queryRowsPerHead, kvHeads * kvRowsPerHead, queryBlocksPerHead, kvRowsPerHead / BLOCK_N, heads / kvHeads, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int head = 0; head < heads; head++) {
            int kvHead = head / (heads / kvHeads);
            double[] expected = referenceAttention(q, k, v, head * queryRowsPerHead, kvHead * kvRowsPerHead, queryRowsPerHead, kvRowsPerHead, scale);
            for (int i = 0; i < queryRowsPerHead * HEAD_DIM; i++) {
                assertEquals("head " + head + " element " + i, expected[i], out.get(head * queryRowsPerHead * HEAD_DIM + i), 0.01);
            }
        }
    }

    /** Multi-head attention: one KV head per query head. */
    @Test
    public void testFlashAttentionMultiHead() throws TornadoExecutionPlanException {
        checkFlashAttentionHeads(4, 4, 64, 2 * BLOCK_N);
    }

    /** Grouped-query attention: four query heads sharing two KV heads. */
    @Test
    public void testFlashAttentionGroupedQuery() throws TornadoExecutionPlanException {
        checkFlashAttentionHeads(4, 2, 64, 4 * BLOCK_N);
    }

    private void checkSplitKReduce(int rows, int splits) throws TornadoExecutionPlanException {
        FloatArray partials = new FloatArray(rows * splits * HEAD_DIM);
        FloatArray logSumExp = new FloatArray(rows * splits);
        FloatArray out = new FloatArray(rows * HEAD_DIM);
        Random random = new Random(307 + splits);
        for (int i = 0; i < rows * splits * HEAD_DIM; i++) {
            partials.set(i, random.nextFloat() - 0.5f);
        }
        for (int i = 0; i < rows * splits; i++) {
            // A spread wide enough that the maximum subtraction matters.
            logSumExp.set(i, 4.0f * random.nextFloat() - 2.0f);
        }

        WorkerGrid1D worker = new WorkerGrid1D(rows);
        GridScheduler grid = new GridScheduler("splitk.k", worker);
        TaskGraph graph = new TaskGraph("splitk") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, partials, logSumExp);
        if (splits == SPLITS_SMALL) {
            graph.task("k", TestTileAttention::splitKReduceFourWay, new TileContext(), partials, logSumExp, out, rows);
        } else {
            graph.task("k", TestTileAttention::splitKReduceSixteenWay, new TileContext(), partials, logSumExp, out, rows);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int row = 0; row < rows; row++) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (int split = 0; split < splits; split++) {
                maximum = Math.max(maximum, logSumExp.get(row * splits + split));
            }
            double total = 0.0;
            double[] weights = new double[splits];
            for (int split = 0; split < splits; split++) {
                weights[split] = Math.exp(logSumExp.get(row * splits + split) - maximum);
                total += weights[split];
            }
            for (int d = 0; d < HEAD_DIM; d++) {
                double numerator = 0.0;
                for (int split = 0; split < splits; split++) {
                    numerator += weights[split] * partials.get((row * splits + split) * HEAD_DIM + d);
                }
                assertEquals("row " + row + " dim " + d, numerator / total, out.get(row * HEAD_DIM + d), 1e-4);
            }
        }
    }

    @Test
    public void testSplitKReduceFourSplits() throws TornadoExecutionPlanException {
        checkSplitKReduce(96, SPLITS_SMALL);
    }

    @Test
    public void testSplitKReduceSixteenSplits() throws TornadoExecutionPlanException {
        checkSplitKReduce(32, SPLITS_LARGE);
    }
}
