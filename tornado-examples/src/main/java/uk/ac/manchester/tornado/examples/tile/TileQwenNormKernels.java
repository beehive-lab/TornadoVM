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
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Three kernels from the Qwen3 port in
 * <a href="https://github.com/beehive-lab/GPULlama3.java/pull/150">GPULlama3.java#150</a>, each
 * written twice: as the thread-level kernel that PR uses, and against {@code TileContext}.
 *
 * <p>
 * All three are from the commits at the end of that PR's performance work
 * ({@code 7aa44d7e} onwards), and all three share a shape: a workgroup owns one row, a lane owns
 * one element, and the row's reduction is a log-stride loop over a shared array with a barrier
 * per step. That is the standard way to reduce within a workgroup, and it is exactly what a
 * tile-level reduction replaces.
 * </p>
 *
 * <ol>
 * <li>{@code l2NormPerHeadWide} - normalise each head by its own L2 norm.</li>
 * <li>{@code gatedNormPerHeadWide} - the same, then a per-element weight and a SiLU gate.</li>
 * <li>{@code rmsApplyAndQuantizeActivationQ8Blocks} - apply an RMS norm and quantise the result
 * to Q8 blocks in one pass: a max-abs reduction per 32-element block, then the quantised
 * values, the block scale and the block sum.</li>
 * </ol>
 *
 * <p>
 * The third is the interesting one. The thread-level version runs two log-stride reductions over
 * shared memory, keeps a second shared array for the quantised values, and packs four of them
 * into an {@code int} by hand. The tile version views the row as {@code [blocks, 32]}, so both
 * reductions are one operation each along the row axis, and the quantised values go straight
 * into an {@code Int8Array} - no packing, because a view of 8-bit elements is a view like any
 * other.
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileQwenNormKernels [heads] [iterations]
 * </pre>
 */
public class TileQwenNormKernels {

    /** Head width. Qwen3's delta-net heads are 256 wide, and a tile shape is a constant. */
    private static final int HEAD_DIM = 256;

    /** Weights per Q8 block, as in the PR. */
    private static final int QK = 32;

    /** Blocks per row of the quantised activation. */
    private static final int BLOCKS = HEAD_DIM / QK;

    private static final float EPSILON = 1.0e-6f;

    // -------------------------------------------------------------------------------------
    // 1. L2 norm per head
    // -------------------------------------------------------------------------------------

    /** The thread-level kernel: a workgroup per head, a lane per element, shared-array reduction. */
    public static void l2NormThreads(KernelContext context, FloatArray values, int headDim, float eps) {
        int head = context.groupIdx;
        int lane = context.localIdx;
        int index = head * headDim + lane;

        float[] shared = context.allocateFloatLocalArray(HEAD_DIM);
        float v = values.get(index);
        shared[lane] = v * v;
        context.localBarrier();
        for (int stride = HEAD_DIM >> 1; stride > 0; stride >>= 1) {
            if (lane < stride) {
                shared[lane] = shared[lane] + shared[lane + stride];
            }
            context.localBarrier();
        }
        if (lane == 0) {
            shared[0] = 1.0f / TornadoMath.max(TornadoMath.sqrt(shared[0]), eps);
        }
        context.localBarrier();
        values.set(index, v * shared[0]);
    }

    /** The same thing: the head is a tile, so the reduction is one operation. */
    public static void l2NormTiles(TileContext tc, FloatArray values, int heads) {
        PartitionView view = tc.partition(tc.view(values, heads, HEAD_DIM), 1, HEAD_DIM);
        int head = tc.bidX();

        Tile v = view.load(head, 0);
        Tile squares = tc.sum(tc.mul(v, v), 1);
        Tile floor = tc.full(DType.F32, EPSILON, 1, 1);
        Tile inverse = tc.div(tc.ones(DType.F32, 1, 1), tc.maximum(tc.sqrt(squares), floor));
        view.store(tc.mul(v, tc.broadcast(inverse, 1, HEAD_DIM)), head, 0);
    }

    // -------------------------------------------------------------------------------------
    // 2. Gated norm per head
    // -------------------------------------------------------------------------------------

    /**
     * Out of place, unlike the PR's version.
     *
     * <p>
     * Theirs writes {@code values} in place, which is right inside a model: the kernel runs once
     * per layer per token on a fresh activation. Repeated on its own output it is not
     * idempotent - it is a contraction, so a timing loop that re-runs it drives the values to
     * zero and the result then depends on the iteration count. The L2 norm above survives the
     * same treatment because normalising a normalised vector is a no-op.
     * </p>
     */
    public static void gatedNormThreads(KernelContext context, FloatArray values, FloatArray gate, FloatArray weight, FloatArray out, int headDim, float eps) {
        int head = context.groupIdx;
        int lane = context.localIdx;
        int index = head * headDim + lane;

        float[] shared = context.allocateFloatLocalArray(HEAD_DIM);
        float v = values.get(index);
        shared[lane] = v * v;
        context.localBarrier();
        for (int stride = HEAD_DIM >> 1; stride > 0; stride >>= 1) {
            if (lane < stride) {
                shared[lane] = shared[lane] + shared[lane + stride];
            }
            context.localBarrier();
        }
        if (lane == 0) {
            shared[0] = 1.0f / TornadoMath.sqrt(shared[0] / HEAD_DIM + eps);
        }
        context.localBarrier();
        float inv = shared[0];

        float z = gate.get(index);
        float silu = z / (1.0f + TornadoMath.exp(-z));
        out.set(index, weight.get(lane) * (inv * v) * silu);
    }

    public static void gatedNormTiles(TileContext tc, FloatArray values, FloatArray gate, FloatArray weight, FloatArray out, int heads) {
        PartitionView view = tc.partition(tc.view(values, heads, HEAD_DIM), 1, HEAD_DIM);
        PartitionView outView = tc.partition(tc.view(out, heads, HEAD_DIM), 1, HEAD_DIM);
        PartitionView gateView = tc.partition(tc.view(gate, heads, HEAD_DIM), 1, HEAD_DIM);
        // The weight is one row shared by every head, so it is a view of a single row.
        PartitionView weightView = tc.partition(tc.view(weight, 1, HEAD_DIM), 1, HEAD_DIM);
        int head = tc.bidX();

        Tile v = view.load(head, 0);
        Tile meanSquare = tc.scale(tc.sum(tc.mul(v, v), 1), 1.0 / HEAD_DIM);
        Tile inverse = tc.rsqrt(tc.add(meanSquare, tc.full(DType.F32, EPSILON, 1, 1)));

        Tile z = gateView.load(head, 0);
        Tile ones = tc.ones(DType.F32, 1, HEAD_DIM);
        Tile silu = tc.div(z, tc.add(ones, tc.exp(tc.scale(z, -1.0))));

        Tile normalised = tc.mul(v, tc.broadcast(inverse, 1, HEAD_DIM));
        outView.store(tc.mul(tc.mul(weightView.load(0, 0), normalised), silu), head, 0);
    }

    // -------------------------------------------------------------------------------------
    // 3. RMS apply fused with Q8 block quantisation
    // -------------------------------------------------------------------------------------

    /**
     * The thread-level kernel, as the PR writes it: a workgroup per 32-element block, a max-abs
     * reduction and a sum reduction over shared memory, and four quantised values packed into
     * one {@code int} by hand.
     */
    public static void rmsQuantizeThreads(KernelContext context, FloatArray x, FloatArray rmsWeights, FloatArray temp, FloatArray xb, IntArray quants, FloatArray scales,
            IntArray sums) {
        int block = context.groupIdx;
        int lane = context.localIdx;
        int index = block * QK + lane;

        float[] shared = context.allocateFloatLocalArray(QK);
        int[] sharedQuants = context.allocateIntLocalArray(QK);

        float ss = temp.get(0);
        float value = rmsWeights.get(index) * (ss * x.get(index));
        xb.set(index, value);

        shared[lane] = TornadoMath.abs(value);
        context.localBarrier();
        for (int stride = QK / 2; stride > 0; stride >>= 1) {
            if (lane < stride) {
                shared[lane] = TornadoMath.max(shared[lane], shared[lane + stride]);
            }
            context.localBarrier();
        }
        float maxAbs = shared[0];
        context.localBarrier();

        float inverse = maxAbs > 0.0f ? 127.0f / maxAbs : 0.0f;
        float scaled = value * inverse;
        int q = (int) (scaled + (scaled >= 0.0f ? 0.5f : -0.5f));
        q = TornadoMath.min(127, TornadoMath.max(-127, q));
        sharedQuants[lane] = q;
        shared[lane] = q;
        context.localBarrier();

        if (lane < QK / 4) {
            int packed = (sharedQuants[lane * 4] & 0xFF) //
                    | ((sharedQuants[lane * 4 + 1] & 0xFF) << 8) //
                    | ((sharedQuants[lane * 4 + 2] & 0xFF) << 16) //
                    | ((sharedQuants[lane * 4 + 3] & 0xFF) << 24);
            quants.set(block * (QK / 4) + lane, packed);
        }

        for (int stride = QK / 2; stride > 0; stride >>= 1) {
            if (lane < stride) {
                shared[lane] += shared[lane + stride];
            }
            context.localBarrier();
        }
        if (lane == 0) {
            scales.set(block, maxAbs / 127.0f);
            sums.set(block, (int) shared[0]);
        }
    }

    /**
     * The same fused pass over a tile shaped {@code [BLOCKS, QK]}: one row per Q8 block, so both
     * reductions are one operation along the row axis, and the quantised values are stored into
     * an {@code Int8Array} rather than packed four to an {@code int}.
     *
     * <p>
     * Rounding is half away from zero, as the thread-level version does it. There is no
     * {@code sign}, so the two directions are a {@code select} over the comparison, which is what
     * the arithmetic actually needs anyway.
     * </p>
     */
    public static void rmsQuantizeTiles(TileContext tc, FloatArray x, FloatArray rmsWeights, FloatArray temp, FloatArray xb, Int8Array quants, FloatArray scales, FloatArray sums,
            int rows) {
        PartitionView xView = tc.partition(tc.view(x, rows * BLOCKS, QK), BLOCKS, QK);
        PartitionView weightView = tc.partition(tc.view(rmsWeights, rows * BLOCKS, QK), BLOCKS, QK);
        PartitionView scaleInput = tc.partition(tc.view(temp, 1, 1), 1, 1);
        PartitionView xbView = tc.partition(tc.view(xb, rows * BLOCKS, QK), BLOCKS, QK);
        PartitionView quantView = tc.partition(tc.view(quants, rows * BLOCKS, QK), BLOCKS, QK);
        PartitionView scaleView = tc.partition(tc.view(scales, rows, BLOCKS), 1, BLOCKS);
        PartitionView sumView = tc.partition(tc.view(sums, rows, BLOCKS), 1, BLOCKS);

        int row = tc.bidX();
        Tile ss = tc.broadcast(scaleInput.load(0, 0), BLOCKS, QK);
        Tile value = tc.mul(weightView.load(row, 0), tc.mul(ss, xView.load(row, 0)));
        xbView.store(value, row, 0);

        // One max-abs per block: the reduction the thread-level version spends a log-stride loop
        // and five barriers on.
        Tile maxAbs = tc.max(tc.abs(value), 1);
        Tile positive = tc.greaterThan(maxAbs, 0.0);
        Tile inverse = tc.select(positive, tc.div(tc.full(DType.F32, 127.0, BLOCKS, 1), maxAbs), tc.zeros(DType.F32, BLOCKS, 1));

        Tile scaled = tc.mul(value, tc.broadcast(inverse, BLOCKS, QK));
        Tile half = tc.full(DType.F32, 0.5, BLOCKS, QK);
        Tile rounded = tc.select(tc.greaterOrEqual(scaled, 0.0), //
                tc.floor(tc.add(scaled, half)), //
                tc.scale(tc.floor(tc.add(tc.scale(scaled, -1.0), half)), -1.0));
        Tile limit = tc.full(DType.F32, 127.0, BLOCKS, QK);
        Tile clamped = tc.minimum(tc.maximum(rounded, tc.scale(limit, -1.0)), limit);

        quantView.store(tc.cast(clamped, DType.S8), row, 0);
        scaleView.store(tc.reshape(tc.scale(maxAbs, 1.0 / 127.0), 1, BLOCKS), row, 0);
        sumView.store(tc.reshape(tc.sum(clamped, 1), 1, BLOCKS), row, 0);
    }

    // -------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int heads = args.length > 0 ? Integer.parseInt(args[0]) : 1024;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        TileExamples.requireCuda();

        Random random = new Random(5003);
        float[] seed = new float[heads * HEAD_DIM];
        float[] gateSeed = new float[heads * HEAD_DIM];
        float[] weightSeed = new float[HEAD_DIM];
        for (int i = 0; i < seed.length; i++) {
            seed[i] = 2.0f * random.nextFloat() - 1.0f;
            gateSeed[i] = 2.0f * random.nextFloat() - 1.0f;
        }
        for (int i = 0; i < HEAD_DIM; i++) {
            weightSeed[i] = 0.5f + random.nextFloat();
        }

        System.out.printf("Qwen3 norm and quantisation kernels: %d heads of %d, %d iterations%n", heads, HEAD_DIM, iterations);
        System.out.println("each written as the thread-level kernel from GPULlama3.java#150 and against TileContext");
        System.out.println();

        String only = System.getProperty("tile.example.only", "");
        if (only.isEmpty() || only.equals("1")) {
            runL2Norm(seed, heads, iterations);
        }
        if (only.isEmpty() || only.equals("2")) {
            runGatedNorm(seed, gateSeed, weightSeed, heads, iterations);
        }
        if (only.isEmpty() || only.equals("3")) {
            runRmsQuantize(seed, weightSeed, heads, iterations);
        }

        System.out.println();
        System.out.println("Speedups compare the two GPU kernels, not sequential Java. Wall clock includes");
        System.out.println("JVM dispatch; see TileExamples for the nsys recipe that measures kernel time.");
    }

    private static void runL2Norm(float[] seed, int heads, int iterations) {
        float[] expected = new float[seed.length];
        for (int head = 0; head < heads; head++) {
            double total = 0.0;
            for (int i = 0; i < HEAD_DIM; i++) {
                total += seed[head * HEAD_DIM + i] * seed[head * HEAD_DIM + i];
            }
            double inverse = 1.0 / Math.max(Math.sqrt(total), EPSILON);
            for (int i = 0; i < HEAD_DIM; i++) {
                expected[head * HEAD_DIM + i] = (float) (seed[head * HEAD_DIM + i] * inverse);
            }
        }

        FloatArray threadValues = copy(seed);
        WorkerGrid1D threadWorker = new WorkerGrid1D(heads * HEAD_DIM);
        threadWorker.setLocalWork(HEAD_DIM, 1, 1);
        double threadTime = TileExamples.time(new TaskGraph("l2Threads") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, threadValues) //
                .task("k", TileQwenNormKernels::l2NormThreads, new KernelContext(), threadValues, HEAD_DIM, EPSILON) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadValues), //
                new GridScheduler("l2Threads.k", threadWorker), iterations);

        FloatArray tileValues = copy(seed);
        double tileTime = TileExamples.time(new TaskGraph("l2Tiles") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tileValues) //
                .task("k", TileQwenNormKernels::l2NormTiles, new TileContext(), tileValues, heads) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileValues), //
                new GridScheduler("l2Tiles.k", new WorkerGrid1D(heads)), iterations);

        System.out.println("1. L2 norm per head");
        TileExamples.header();
        TileExamples.report("   KernelContext, shared-array reduce", threadTime, threadTime, 0.0, TileExamples.maxError(threadValues, expected), 1e-5);
        TileExamples.report("   TileContext, the head is a tile", tileTime, threadTime, 0.0, TileExamples.maxError(tileValues, expected), 1e-5);
        System.out.println();
    }

    private static void runGatedNorm(float[] seed, float[] gateSeed, float[] weightSeed, int heads, int iterations) {
        float[] expected = new float[seed.length];
        for (int head = 0; head < heads; head++) {
            double total = 0.0;
            for (int i = 0; i < HEAD_DIM; i++) {
                total += seed[head * HEAD_DIM + i] * seed[head * HEAD_DIM + i];
            }
            double inverse = 1.0 / Math.sqrt(total / HEAD_DIM + EPSILON);
            for (int i = 0; i < HEAD_DIM; i++) {
                double z = gateSeed[head * HEAD_DIM + i];
                double silu = z / (1.0 + Math.exp(-z));
                expected[head * HEAD_DIM + i] = (float) (weightSeed[i] * (inverse * seed[head * HEAD_DIM + i]) * silu);
            }
        }

        FloatArray gate = copy(gateSeed);
        FloatArray weight = copy(weightSeed);

        FloatArray values = copy(seed);
        FloatArray threadOut = new FloatArray(seed.length);
        WorkerGrid1D threadWorker = new WorkerGrid1D(heads * HEAD_DIM);
        threadWorker.setLocalWork(HEAD_DIM, 1, 1);
        double threadTime = TileExamples.time(new TaskGraph("gatedThreads") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values, gate, weight) //
                .task("k", TileQwenNormKernels::gatedNormThreads, new KernelContext(), values, gate, weight, threadOut, HEAD_DIM, EPSILON) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadOut), //
                new GridScheduler("gatedThreads.k", threadWorker), iterations);

        FloatArray tileOut = new FloatArray(seed.length);
        double tileTime = TileExamples.time(new TaskGraph("gatedTiles") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values, gate, weight) //
                .task("k", TileQwenNormKernels::gatedNormTiles, new TileContext(), values, gate, weight, tileOut, heads) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileOut), //
                new GridScheduler("gatedTiles.k", new WorkerGrid1D(heads)), iterations);

        System.out.println("2. Gated norm per head (weight and SiLU gate)");
        TileExamples.header();
        TileExamples.report("   KernelContext, shared-array reduce", threadTime, threadTime, 0.0, TileExamples.maxError(threadOut, expected), 1e-4);
        TileExamples.report("   TileContext, the head is a tile", tileTime, threadTime, 0.0, TileExamples.maxError(tileOut, expected), 1e-4);
        System.out.println();
    }

    private static void runRmsQuantize(float[] seed, float[] weightSeed, int rows, int iterations) {
        // One RMS scale for the whole activation, as the PR's kernel takes it.
        FloatArray temp = new FloatArray(1);
        temp.set(0, 0.75f);
        FloatArray x = copy(seed);
        FloatArray rmsWeights = new FloatArray(rows * HEAD_DIM);
        for (int i = 0; i < rows * HEAD_DIM; i++) {
            rmsWeights.set(i, weightSeed[i % HEAD_DIM]);
        }

        int blocks = rows * BLOCKS;
        float[] expectedXb = new float[rows * HEAD_DIM];
        float[] expectedScales = new float[blocks];
        float[] expectedSums = new float[blocks];
        for (int block = 0; block < blocks; block++) {
            float maxAbs = 0.0f;
            for (int i = 0; i < QK; i++) {
                int index = block * QK + i;
                float value = rmsWeights.get(index) * (temp.get(0) * x.get(index));
                expectedXb[index] = value;
                maxAbs = Math.max(maxAbs, Math.abs(value));
            }
            float inverse = maxAbs > 0.0f ? 127.0f / maxAbs : 0.0f;
            int sum = 0;
            for (int i = 0; i < QK; i++) {
                float scaled = expectedXb[block * QK + i] * inverse;
                int q = (int) (scaled + (scaled >= 0.0f ? 0.5f : -0.5f));
                q = Math.min(127, Math.max(-127, q));
                sum += q;
            }
            expectedScales[block] = maxAbs / 127.0f;
            expectedSums[block] = sum;
        }

        FloatArray threadXb = new FloatArray(rows * HEAD_DIM);
        IntArray threadQuants = new IntArray(blocks * (QK / 4));
        FloatArray threadScales = new FloatArray(blocks);
        IntArray threadSums = new IntArray(blocks);
        WorkerGrid1D threadWorker = new WorkerGrid1D(blocks * QK);
        threadWorker.setLocalWork(QK, 1, 1);
        double threadTime = TileExamples.time(new TaskGraph("q8Threads") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsWeights, temp) //
                .task("k", TileQwenNormKernels::rmsQuantizeThreads, new KernelContext(), x, rmsWeights, temp, threadXb, threadQuants, threadScales, threadSums) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadXb, threadQuants, threadScales, threadSums), //
                new GridScheduler("q8Threads.k", threadWorker), iterations);

        FloatArray tileXb = new FloatArray(rows * HEAD_DIM);
        Int8Array tileQuants = new Int8Array(rows * HEAD_DIM);
        FloatArray tileScales = new FloatArray(blocks);
        FloatArray tileSums = new FloatArray(blocks);
        double tileTime = TileExamples.time(new TaskGraph("q8Tiles") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, rmsWeights, temp) //
                .task("k", TileQwenNormKernels::rmsQuantizeTiles, new TileContext(), x, rmsWeights, temp, tileXb, tileQuants, tileScales, tileSums, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileXb, tileQuants, tileScales, tileSums), //
                new GridScheduler("q8Tiles.k", new WorkerGrid1D(rows)), iterations);

        double threadError = Math.max(TileExamples.maxError(threadXb, expectedXb), TileExamples.maxError(threadScales, expectedScales));
        double tileError = Math.max(TileExamples.maxError(tileXb, expectedXb), TileExamples.maxError(tileScales, expectedScales));
        // The block sums are integers in both, so they must agree exactly.
        for (int block = 0; block < blocks; block++) {
            threadError = Math.max(threadError, Math.abs(threadSums.get(block) - expectedSums[block]));
            tileError = Math.max(tileError, Math.abs(tileSums.get(block) - expectedSums[block]));
        }

        System.out.println("3. RMS apply fused with Q8 block quantisation");
        TileExamples.header();
        TileExamples.report("   KernelContext, two shared reduces", threadTime, threadTime, 0.0, threadError, 1e-4);
        TileExamples.report("   TileContext, row is [blocks, 32]", tileTime, threadTime, 0.0, tileError, 1e-4);
    }

    private static FloatArray copy(float[] source) {
        FloatArray array = new FloatArray(source.length);
        for (int i = 0; i < source.length; i++) {
            array.set(i, source[i]);
        }
        return array;
    }
}
