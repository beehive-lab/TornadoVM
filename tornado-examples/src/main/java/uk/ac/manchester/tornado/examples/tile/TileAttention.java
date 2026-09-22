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
package uk.ac.manchester.tornado.examples.tile;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Scaled dot-product attention, {@code softmax(Q K^T / sqrt(d)) V}, two ways.
 *
 * <ol>
 * <li>{@code @Parallel} - one thread per query row, walking the whole key sequence.</li>
 * <li>{@code TileContext} - flash attention: one pass over the key sequence with an online
 * softmax, so the {@code [S_q, S_kv]} score matrix never exists.</li>
 * </ol>
 *
 * <p>
 * This is the algorithm a tile-level API exists for. One loop body needs a matmul, a row
 * reduction, a row broadcast, an exponential, a narrowing cast and a second accumulating
 * matmul, with three tiles carried across the loop. The thread-level version below is correct
 * and readable, and it recomputes every dot product three times because a thread has nowhere to
 * put the scores; the usual fix is to materialise them, which costs a buffer the fused kernel
 * never needs.
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileAttention [queries] [keys] [iterations]
 * </pre>
 */
public class TileAttention {

    /** Head dimension, and the full width of every tile, so it is a constant. */
    private static final int HEAD_DIM = 64;

    private static final int BLOCK_M = 32;

    private static final int BLOCK_N = 32;

    /**
     * Stands in for negative infinity as the initial row maximum: with a true {@code -inf} the
     * first rescaling factor is {@code exp(-inf - m)}, which multiplies a zero accumulator and
     * can produce a NaN. NVIDIA's own tile kernels use a large negative constant for this.
     */
    private static final float NEGATIVE_LIMIT = -1.0e30f;

    // -------------------------------------------------------------------------------------
    // 1. One thread per query row
    // -------------------------------------------------------------------------------------

    public static void threadPerQuery(HalfFloatArray q, HalfFloatArray k, HalfFloatArray v, FloatArray out, int queryRows, int kvRows, float scale) {
        for (@Parallel int row = 0; row < queryRows; row++) {
            // Each thread owns one output row, and the loop below accumulates into it, so it
            // clears its own row first. Without this the kernel is not idempotent and a second
            // execution adds to the first one's result.
            for (int d = 0; d < HEAD_DIM; d++) {
                out.set(row * HEAD_DIM + d, 0.0f);
            }
            float maximum = -Float.MAX_VALUE;
            for (int key = 0; key < kvRows; key++) {
                float dot = 0.0f;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get(row * HEAD_DIM + d).getFloat32() * k.get(key * HEAD_DIM + d).getFloat32();
                }
                maximum = TornadoMath.max(maximum, dot * scale);
            }
            float total = 0.0f;
            for (int key = 0; key < kvRows; key++) {
                float dot = 0.0f;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get(row * HEAD_DIM + d).getFloat32() * k.get(key * HEAD_DIM + d).getFloat32();
                }
                total += TornadoMath.exp(dot * scale - maximum);
            }
            for (int key = 0; key < kvRows; key++) {
                float dot = 0.0f;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get(row * HEAD_DIM + d).getFloat32() * k.get(key * HEAD_DIM + d).getFloat32();
                }
                float weight = TornadoMath.exp(dot * scale - maximum) / total;
                for (int d = 0; d < HEAD_DIM; d++) {
                    out.set(row * HEAD_DIM + d, out.get(row * HEAD_DIM + d) + weight * v.get(key * HEAD_DIM + d).getFloat32());
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // 2. Flash attention with an online softmax
    // -------------------------------------------------------------------------------------

    /**
     * One pass over the key sequence. Three tiles are carried across the loop - the running row
     * maximum, the running denominator and the unnormalised output - and whenever a block raises
     * a row's maximum, everything accumulated so far for that row is rescaled by
     * {@code exp(m_old - m_new)}. That rescaling is what makes a single pass numerically equal
     * to the two-pass softmax above.
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

    // -------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int queryRows = args.length > 0 ? Integer.parseInt(args[0]) : 1024;
        int kvRows = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        if (queryRows % BLOCK_M != 0 || kvRows % BLOCK_N != 0) {
            System.out.printf("queries must be a multiple of %d and keys of %d%n", BLOCK_M, BLOCK_N);
            return;
        }
        TileExamples.requireCuda();

        final float scale = (float) (1.0 / Math.sqrt(HEAD_DIM));
        HalfFloatArray q = randomHalf(queryRows * HEAD_DIM, 101);
        HalfFloatArray k = randomHalf(kvRows * HEAD_DIM, 103);
        HalfFloatArray v = randomHalf(kvRows * HEAD_DIM, 107);

        System.out.printf("Attention: Q = [%d, %d], K = V = [%d, %d], %d iterations%n%n", queryRows, HEAD_DIM, kvRows, HEAD_DIM, iterations);

        // Two-pass softmax attention on the CPU: the reference and the baseline.
        float[] expected = new float[queryRows * HEAD_DIM];
        long start = System.nanoTime();
        double[] scores = new double[kvRows];
        for (int row = 0; row < queryRows; row++) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (int key = 0; key < kvRows; key++) {
                double dot = 0.0;
                for (int d = 0; d < HEAD_DIM; d++) {
                    dot += q.get(row * HEAD_DIM + d).getFloat32() * k.get(key * HEAD_DIM + d).getFloat32();
                }
                scores[key] = dot * scale;
                maximum = Math.max(maximum, scores[key]);
            }
            double total = 0.0;
            for (int key = 0; key < kvRows; key++) {
                scores[key] = Math.exp(scores[key] - maximum);
                total += scores[key];
            }
            for (int key = 0; key < kvRows; key++) {
                double weight = scores[key] / total;
                for (int d = 0; d < HEAD_DIM; d++) {
                    expected[row * HEAD_DIM + d] += (float) (weight * v.get(key * HEAD_DIM + d).getFloat32());
                }
            }
        }
        double sequential = (System.nanoTime() - start) / 1e6;

        FloatArray threadOut = new FloatArray(queryRows * HEAD_DIM);
        FloatArray flashOut = new FloatArray(queryRows * HEAD_DIM);

        double threadTime = TileExamples.time(new TaskGraph("perQuery") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, q, k, v) //
                .task("k", TileAttention::threadPerQuery, q, k, v, threadOut, queryRows, kvRows, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadOut), //
                new GridScheduler("perQuery.k", new WorkerGrid1D(queryRows)), iterations);

        double flashTime = TileExamples.time(new TaskGraph("flash") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, q, k, v) //
                .task("k", TileAttention::flashAttention, new TileContext(), q, k, v, flashOut, queryRows, kvRows, kvRows / BLOCK_N, scale) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, flashOut), //
                new GridScheduler("flash.k", new WorkerGrid1D(queryRows / BLOCK_M)), iterations);

        // Attention is 4 * S_q * S_kv * d flops: two matmuls of that shape.
        double gigaFlop = 4.0 * queryRows * kvRows * HEAD_DIM / 1e9;
        final double tolerance = 0.01;
        TileExamples.header();
        TileExamples.report("sequential Java (reference)", sequential, sequential, gigaFlop, 0.0, tolerance);
        TileExamples.report("@Parallel, one thread per query", threadTime, sequential, gigaFlop, TileExamples.maxError(threadOut, expected), tolerance);
        TileExamples.report("TileContext, flash attention", flashTime, sequential, gigaFlop, TileExamples.maxError(flashOut, expected), tolerance);
        TileExamples.footer();
        System.out.println();
        System.out.printf("The fused kernel also never allocates the [%d, %d] score matrix: %d KB of device%n", queryRows, kvRows, queryRows * kvRows * 4 / 1024);
        System.out.println("memory the thread-level version would need if it materialised the scores.");
    }

    private static HalfFloatArray randomHalf(int elements, long seed) {
        HalfFloatArray array = new HalfFloatArray(elements);
        Random random = new Random(seed);
        for (int i = 0; i < elements; i++) {
            array.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        return array;
    }
}
