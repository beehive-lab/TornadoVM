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
 * The attention variants from NVIDIA's TileGym suite that the plain prefill kernel does not
 * reach: split-KV decode ({@code ops/tilecpp/flash_decode.cuh}) chained with the reduction
 * that combines its partials, attention sinks ({@code ops/tilecpp/attention_sink.cuh}) and
 * Gemma-style logit soft-capping ({@code ops/tilecpp/gemma_attention.cuh}).
 *
 * <p>
 * The decode pair is the most interesting thing in this file. Decode has one query row per
 * step and a long KV sequence, so the parallelism has to come from splitting the KV sequence
 * across blocks; each block then holds a partial output that carries its own softmax
 * denominator, and a second kernel has to combine them without ever seeing the original
 * scores. Both kernels run in one {@code TaskGraph} on shared device buffers, which is what
 * makes the pair worth testing as a pair: the partial layout, the log-sum-exp encoding and
 * the streaming rescale all have to agree across a task boundary.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileAttentionVariants
 * </pre>
 */
public class TestTileAttentionVariants extends TornadoTestBase {

    private static final int HEAD_DIM = 64;

    private static final int BLOCK_M = 32;

    private static final int BLOCK_N = 32;

    /** See {@link TestTileAttention} for why this is not {@code -inf}. */
    private static final float NEGATIVE_LIMIT = -1.0e30f;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    // -------------------------------------------------------------------------------------
    // Split-KV decode, and the reduction that combines the splits
    // -------------------------------------------------------------------------------------

    /**
     * One split of a decode attention: the queries attend to their slice of the KV sequence
     * only, and the kernel emits an unnormalised output tile plus the log-sum-exp of the
     * scores it saw.
     *
     * <p>
     * The log-sum-exp is the whole trick. A partial output cannot be combined by averaging,
     * because each split divided by its own denominator; {@code m + log(l)} packs the running
     * maximum and the running sum into one number per row, which is exactly what the combine
     * kernel needs to weight the partials. That is why {@link TileContext#log} exists.
     * </p>
     *
     * <p>
     * Partials are laid out split-major, {@code [splits * queryRows, HEAD_DIM]}, so this
     * kernel writes one contiguous tile and the combine kernel reads one contiguous tile per
     * split. A row-major layout would make one of the two strided, and a partition view
     * addresses whole tiles only.
     * </p>
     */
    public static void flashDecodeSplit(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray partials, FloatArray logSumExp, int queryRows, int kvRows, int rowBlocks,
            int blocksPerSplit, float scale) {
        PartitionView qView = tc.partition(tc.view(q, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView kView = tc.partition(tc.view(k, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView vView = tc.partition(tc.view(v, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);

        int rowBlock = tc.bidX();
        int split = tc.bidY();
        int splits = tc.numBlocksY();

        PartitionView partialView = tc.partition(tc.view(partials, splits * queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView lseView = tc.partition(tc.view(logSumExp, splits, queryRows), 1, BLOCK_M);

        Tile query = qView.load(rowBlock, 0);
        Tile rowMax = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, 1);
        Tile rowSum = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        int firstBlock = split * blocksPerSplit;
        for (int step = 0; step < blocksPerSplit; step++) {
            Tile keys = tc.transpose(kView.load(firstBlock + step, 0));
            Tile scores = tc.scale(tc.mma(query, keys, tc.zeros(DType.F32, BLOCK_M, BLOCK_N)), scale);
            Tile newMax = tc.maximum(rowMax, tc.max(scores, 1));
            Tile probabilities = tc.exp(tc.sub(scores, newMax));
            Tile correction = tc.exp(tc.sub(rowMax, newMax));
            rowSum = tc.add(tc.mul(rowSum, correction), tc.sum(probabilities, 1));
            acc = tc.mul(acc, correction);
            acc = tc.mma(tc.cast(probabilities, DType.F16), vView.load(firstBlock + step, 0), acc);
            rowMax = newMax;
        }

        // The partial output is normalised by this split's own denominator, so the combine
        // kernel weights outputs rather than raw accumulators.
        partialView.store(tc.div(acc, rowSum), split * rowBlocks + rowBlock, 0);
        lseView.store(tc.transpose(tc.add(rowMax, tc.log(rowSum))), split, rowBlock);
    }

    /**
     * Combines the split partials into the final output.
     *
     * <p>
     * Written as a single streaming pass rather than a maximum pass followed by a weighting
     * pass: the running maximum of the log-sum-exp values is carried across splits and every
     * accumulated value is rescaled when it moves, which is the same rescale the attention
     * kernel itself performs one loop up. Two passes would need the log-sum-exp tile read
     * twice; this reads it once.
     * </p>
     */
    public static void splitKCombine(TileContext tc, FloatArray partials, FloatArray logSumExp, FloatArray out, int queryRows, int rowBlocks, int splits) {
        PartitionView partialView = tc.partition(tc.view(partials, splits * queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView lseView = tc.partition(tc.view(logSumExp, splits, queryRows), 1, BLOCK_M);
        PartitionView outView = tc.partition(tc.view(out, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);

        int rowBlock = tc.bidX();
        Tile rowMax = tc.full(DType.F32, NEGATIVE_LIMIT, BLOCK_M, 1);
        Tile total = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        for (int split = 0; split < splits; split++) {
            Tile lse = tc.transpose(lseView.load(split, rowBlock));
            Tile newMax = tc.maximum(rowMax, lse);
            Tile weight = tc.exp(tc.sub(lse, newMax));
            Tile correction = tc.exp(tc.sub(rowMax, newMax));

            acc = tc.add(tc.mul(acc, correction), tc.mul(partialView.load(split * rowBlocks + rowBlock, 0), weight));
            total = tc.add(tc.mul(total, correction), weight);
            rowMax = newMax;
        }

        outView.store(tc.div(acc, total), rowBlock, 0);
    }

    // -------------------------------------------------------------------------------------
    // Attention sinks
    // -------------------------------------------------------------------------------------

    /**
     * Attention with a sink logit.
     *
     * <p>
     * Streaming attention keeps a per-head sink token whose logit joins the softmax
     * denominator without contributing to the output, which lets a row attend to almost
     * nothing when that is the right answer. The kernel therefore starts the running maximum
     * at the sink value and adds {@code exp(sink - m)} to the denominator at the end.
     * </p>
     *
     * <p>
     * The sink is read from a buffer rather than passed as a parameter, because that is where
     * a per-head sink lives, and because a {@code full(...)} fill value has to be a
     * compile-time constant. A one-element tile loaded from a {@code [1, 1]} partition
     * broadcasts against the {@code [BLOCK_M, 1]} row tiles, which is the rank-2 way to say
     * what the original does with a scalar pointer read.
     * </p>
     */
    public static void attentionWithSink(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray sink, FloatArray out, int queryRows, int kvRows, int kvBlocks,
            float scale) {
        PartitionView qView = tc.partition(tc.view(q, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);
        PartitionView kView = tc.partition(tc.view(k, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView vView = tc.partition(tc.view(v, kvRows, HEAD_DIM), BLOCK_N, HEAD_DIM);
        PartitionView sinkView = tc.partition(tc.view(sink, 1, 1), 1, 1);
        PartitionView outView = tc.partition(tc.view(out, queryRows, HEAD_DIM), BLOCK_M, HEAD_DIM);

        int queryBlock = tc.bidX();
        Tile sinkValue = sinkView.load(0, 0);
        Tile query = qView.load(queryBlock, 0);

        Tile rowMax = tc.add(tc.zeros(DType.F32, BLOCK_M, 1), sinkValue);
        Tile rowSum = tc.zeros(DType.F32, BLOCK_M, 1);
        Tile acc = tc.zeros(DType.F32, BLOCK_M, HEAD_DIM);

        for (int block = 0; block < kvBlocks; block++) {
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

        // The sink joins the denominator only: it has no value vector to contribute.
        Tile denominator = tc.add(rowSum, tc.exp(tc.sub(tc.add(tc.zeros(DType.F32, BLOCK_M, 1), sinkValue), rowMax)));
        outView.store(tc.div(acc, denominator), queryBlock, 0);
    }

    // -------------------------------------------------------------------------------------
    // Logit soft-capping
    // -------------------------------------------------------------------------------------

    /**
     * Attention with Gemma-style logit soft-capping, {@code s = cap * tanh(s / cap)}.
     *
     * <p>
     * The cap bounds a score smoothly instead of clipping it, and it is the reason
     * {@link TileContext#tanh} is needed. Both scale factors are runtime values here, which
     * is the point of the test: a {@code scale} operand does not have to fold, unlike a tile
     * shape or a {@code full} fill value.
     * </p>
     */
    public static void softCappedAttention(TileContext tc, HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int queryRows, int kvRows, int kvBlocks, float scale,
            float softCap) {
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
            Tile keys = tc.transpose(kView.load(block, 0));
            Tile raw = tc.scale(tc.mma(query, keys, tc.zeros(DType.F32, BLOCK_M, BLOCK_N)), scale / softCap);
            Tile scores = tc.scale(tc.tanh(raw), softCap);

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
     * Reference attention with an optional sink logit and an optional soft cap, over the fp16
     * values the device sees. A sink of {@code NEGATIVE_LIMIT} and a cap of zero mean "plain".
     */
    private static double[] reference(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, int queryRows, int kvRows, float scale, double sink, double softCap) {
        double[] result = new double[queryRows * HEAD_DIM];
        for (int row = 0; row < queryRows; row++) {
            double[] scores = new double[kvRows];
            double maximum = sink;
            for (int key = 0; key < kvRows; key++) {
                double dot = 0.0;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get(row * HEAD_DIM + d).getFloat32() * k.get(key * HEAD_DIM + d).getFloat32();
                }
                scores[key] = softCap > 0.0 ? softCap * Math.tanh(dot * scale / softCap) : dot * scale;
                maximum = Math.max(maximum, scores[key]);
            }
            double denominator = Math.exp(sink - maximum);
            for (int key = 0; key < kvRows; key++) {
                scores[key] = Math.exp(scores[key] - maximum);
                denominator += scores[key];
            }
            for (int key = 0; key < kvRows; key++) {
                double weight = scores[key] / denominator;
                for (int d = 0; d < HEAD_DIM; d++) {
                    result[row * HEAD_DIM + d] += weight * v.get(key * HEAD_DIM + d).getFloat32();
                }
            }
        }
        return result;
    }

    /**
     * The decode pair: one kernel per (row block, split), then one kernel per row block to
     * combine. Both tile tasks in one graph, so the partials never leave the device.
     */
    private void checkDecodeChain(int queryRows, int kvRows, int splits) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 401);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 409);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 419);
        FloatArray partials = new FloatArray(splits * queryRows * HEAD_DIM);
        FloatArray logSumExp = new FloatArray(splits * queryRows);
        FloatArray out = new FloatArray(queryRows * HEAD_DIM);

        int rowBlocks = queryRows / BLOCK_M;
        int blocksPerSplit = kvRows / BLOCK_N / splits;

        WorkerGrid2D splitWorker = new WorkerGrid2D(rowBlocks, splits);
        WorkerGrid1D combineWorker = new WorkerGrid1D(rowBlocks);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("decode.split", splitWorker);
        grid.addWorkerGrid("decode.combine", combineWorker);

        TaskGraph graph = new TaskGraph("decode") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .task("split", TestTileAttentionVariants::flashDecodeSplit, new TileContext(), q, k, v, partials, logSumExp, queryRows, kvRows, rowBlocks, blocksPerSplit, scale) //
                .task("combine", TestTileAttentionVariants::splitKCombine, new TileContext(), partials, logSumExp, out, queryRows, rowBlocks, splits) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        double[] expected = reference(q, k, v, queryRows, kvRows, scale, Double.NEGATIVE_INFINITY, 0.0);
        for (int i = 0; i < queryRows * HEAD_DIM; i++) {
            assertEquals("element " + i, expected[i], out.get(i), 0.01);
        }
    }

    /** Four splits over eight KV blocks: two KV blocks each. */
    @Test
    public void testFlashDecodeFourSplits() throws TornadoExecutionPlanException {
        checkDecodeChain(BLOCK_M, 8 * BLOCK_N, 4);
    }

    /** Eight splits of one KV block each: the shortest possible split. */
    @Test
    public void testFlashDecodeOneBlockPerSplit() throws TornadoExecutionPlanException {
        checkDecodeChain(BLOCK_M, 8 * BLOCK_N, 8);
    }

    /** Several query row blocks as well as several splits, so both grid axes are exercised. */
    @Test
    public void testFlashDecodeMultipleRowBlocks() throws TornadoExecutionPlanException {
        checkDecodeChain(4 * BLOCK_M, 16 * BLOCK_N, 4);
    }

    /** One split: the combine kernel must be the identity apart from the normalisation. */
    @Test
    public void testFlashDecodeSingleSplit() throws TornadoExecutionPlanException {
        checkDecodeChain(2 * BLOCK_M, 4 * BLOCK_N, 1);
    }

    private void checkSink(int queryRows, int kvRows, float sinkValue) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 503);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 509);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 521);
        FloatArray sink = new FloatArray(1);
        sink.set(0, sinkValue);
        FloatArray out = new FloatArray(queryRows * HEAD_DIM);

        WorkerGrid1D worker = new WorkerGrid1D(queryRows / BLOCK_M);
        GridScheduler grid = new GridScheduler("sink.k", worker);
        TaskGraph graph = new TaskGraph("sink") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v, sink) //
                .task("k", TestTileAttentionVariants::attentionWithSink, new TileContext(), q, k, v, sink, out, queryRows, kvRows, kvRows / BLOCK_N, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        double[] expected = reference(q, k, v, queryRows, kvRows, scale, sinkValue, 0.0);
        for (int i = 0; i < queryRows * HEAD_DIM; i++) {
            assertEquals("element " + i, expected[i], out.get(i), 0.01);
        }
    }

    /** A sink logit large enough to take most of the softmax mass. */
    @Test
    public void testAttentionSinkDominant() throws TornadoExecutionPlanException {
        checkSink(64, 4 * BLOCK_N, 4.0f);
    }

    /** A sink logit small enough that the result is nearly plain attention. */
    @Test
    public void testAttentionSinkNegligible() throws TornadoExecutionPlanException {
        checkSink(64, 4 * BLOCK_N, -8.0f);
    }

    private void checkSoftCap(int queryRows, int kvRows, float softCap) throws TornadoExecutionPlanException {
        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 601);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 607);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 613);
        FloatArray out = new FloatArray(queryRows * HEAD_DIM);

        WorkerGrid1D worker = new WorkerGrid1D(queryRows / BLOCK_M);
        GridScheduler grid = new GridScheduler("cap.k", worker);
        TaskGraph graph = new TaskGraph("cap") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .task("k", TestTileAttentionVariants::softCappedAttention, new TileContext(), q, k, v, out, queryRows, kvRows, kvRows / BLOCK_N, scale, softCap) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        double[] expected = reference(q, k, v, queryRows, kvRows, scale, Double.NEGATIVE_INFINITY, softCap);
        for (int i = 0; i < queryRows * HEAD_DIM; i++) {
            assertEquals("element " + i, expected[i], out.get(i), 0.01);
        }
    }

    /** A cap well above the scores, so tanh is nearly linear and the result nearly plain. */
    @Test
    public void testSoftCapLoose() throws TornadoExecutionPlanException {
        checkSoftCap(64, 4 * BLOCK_N, 30.0f);
    }

    /** A cap below the scores, so the capping actually bends them. */
    @Test
    public void testSoftCapTight() throws TornadoExecutionPlanException {
        checkSoftCap(64, 4 * BLOCK_N, 0.5f);
    }
}
