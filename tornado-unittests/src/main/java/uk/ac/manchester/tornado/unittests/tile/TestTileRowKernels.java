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

    /**
     * Layer norm over one row per tile block: {@code (x - mean) * rsqrt(var + eps)}.
     *
     * <p>
     * Needs two reductions over the same row and both of them broadcast back, which is a step
     * beyond RMS norm: the mean is subtracted before the variance is taken, so the second
     * reduction consumes the result of the first.
     * </p>
     */
    public static void layerNormRow(TileContext tc, FloatArray in, FloatArray out, int rows, int n, float inverseN) {
        PartitionView iv = tc.partition(tc.view(in, rows, n), 1, ROW_TILE);
        PartitionView ov = tc.partition(tc.view(out, rows, n), 1, ROW_TILE);
        int row = tc.bidX();

        Tile x = iv.loadMasked(row, 0);
        Tile mean = tc.scale(tc.sum(x, 1), inverseN);
        Tile centred = tc.sub(x, mean);
        Tile variance = tc.scale(tc.sum(tc.mul(centred, centred), 1), inverseN);
        Tile scale = tc.rsqrt(tc.add(variance, tc.full(DType.F32, EPSILON, 1, 1)));
        ov.storeMasked(tc.mul(centred, scale), row, 0);
    }

    /**
     * SiLU-and-multiply, as TileGym defines it: the row is {@code 2 * hidden} wide, the first
     * half is passed through SiLU and multiplied by the second half.
     *
     * <p>
     * The split needs no slicing API: with a tile width of exactly {@code hidden}, block index 0
     * along the row is the first half and block index 1 is the second. That is what a partition
     * view is for.
     * </p>
     *
     * <p>
     * The hidden size is a compile-time constant, not a parameter, because it is the tile width
     * and a CUDA Tile shape is part of the kernel's type. That is why there are two kernels
     * below rather than one taking a size: a differently shaped variant is a differently
     * compiled kernel, which is the model's own rule rather than a limitation of this API. The
     * row count stays dynamic, because it is the grid.
     * </p>
     *
     * <p>
     * {@code silu(v) = v * sigmoid(v) = v / (1 + exp(-v))}, built from the primitives rather
     * than a fused intrinsic, which is also a test that they compose.
     * </p>
     */
    /** Hidden sizes are tile widths, so they must be literals in the kernel body. */
    private static final int HIDDEN_SMALL = 512;

    private static final int HIDDEN_LARGE = 2048;

    public static void siluAndMul512(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, 2 * HIDDEN_SMALL), 1, HIDDEN_SMALL);
        PartitionView ov = tc.partition(tc.view(out, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        int row = tc.bidX();

        Tile gate = iv.load(row, 0);
        Tile value = iv.load(row, 1);
        Tile ones = tc.full(DType.F32, 1.0, 1, HIDDEN_SMALL);
        Tile silu = tc.div(gate, tc.add(ones, tc.exp(tc.scale(gate, -1.0))));
        ov.store(tc.mul(silu, value), row, 0);
    }

    public static void siluAndMul2048(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, 2 * HIDDEN_LARGE), 1, HIDDEN_LARGE);
        PartitionView ov = tc.partition(tc.view(out, rows, HIDDEN_LARGE), 1, HIDDEN_LARGE);
        int row = tc.bidX();

        Tile gate = iv.load(row, 0);
        Tile value = iv.load(row, 1);
        Tile ones = tc.full(DType.F32, 1.0, 1, HIDDEN_LARGE);
        Tile silu = tc.div(gate, tc.add(ones, tc.exp(tc.scale(gate, -1.0))));
        ov.store(tc.mul(silu, value), row, 0);
    }

    /** Column-block width for kernels that walk a row wider than one tile. */
    private static final int CHUNK = 256;

    /**
     * Softmax over a row wider than one tile, the shape TileGym reaches with n = 32768.
     *
     * <p>
     * Three passes over the row's column blocks, which is what the online formulation avoids but
     * is the clearer thing to test first: a running elementwise maximum, then the exponential
     * sum, then normalise and store. The running maximum is why an elementwise {@code ct::max}
     * is needed alongside the {@code ct::reduce_max} reduction - the first combines two tiles,
     * the second collapses one.
     * </p>
     */
    public static void softmaxChunked(TileContext tc, FloatArray in, FloatArray out, int rows, int n, int chunks) {
        PartitionView iv = tc.partition(tc.view(in, rows, n), 1, CHUNK);
        PartitionView ov = tc.partition(tc.view(out, rows, n), 1, CHUNK);
        int row = tc.bidX();

        Tile running = tc.full(DType.F32, -1.0e30, 1, CHUNK);
        for (int c = 0; c < chunks; c++) {
            running = tc.maximum(running, iv.loadMasked(row, c));
        }
        Tile rowMax = tc.max(running, 1);

        Tile partial = tc.zeros(DType.F32, 1, CHUNK);
        for (int c = 0; c < chunks; c++) {
            partial = tc.add(partial, tc.exp(tc.sub(iv.loadMasked(row, c), rowMax)));
        }
        Tile total = tc.sum(partial, 1);

        for (int c = 0; c < chunks; c++) {
            ov.storeMasked(tc.div(tc.exp(tc.sub(iv.loadMasked(row, c), rowMax)), total), row, c);
        }
    }

    /**
     * Persistent RMS norm: the grid is sized to the device rather than the data, and each tile
     * block strides over rows. This is the only kernel here that reads
     * {@link TileContext#numBlocksX()}, and the loop bound is a runtime value, which is allowed
     * because it is a grid quantity and not a tile shape.
     */
    public static void rmsNormPersistent(TileContext tc, FloatArray in, FloatArray out, int rows, int n, float inverseN) {
        PartitionView iv = tc.partition(tc.view(in, rows, n), 1, ROW_TILE);
        PartitionView ov = tc.partition(tc.view(out, rows, n), 1, ROW_TILE);

        for (int row = tc.bidX(); row < rows; row += tc.numBlocksX()) {
            Tile x = iv.loadMasked(row, 0);
            Tile meanSquare = tc.scale(tc.sum(tc.mul(x, x), 1), inverseN);
            Tile scale = tc.rsqrt(tc.add(meanSquare, tc.full(DType.F32, EPSILON, 1, 1)));
            ov.storeMasked(tc.mul(x, scale), row, 0);
        }
    }

    /** {@code sqrt(2/pi)}, the coefficient of the tanh approximation of GELU. */
    private static final double GELU_COEFFICIENT = 0.7978845608028654;

    private static final double GELU_CUBIC_TERM = 0.044715;

    /**
     * GELU, tanh approximation: {@code 0.5 x (1 + tanh(sqrt(2/pi) (x + 0.044715 x^3)))}.
     *
     * <p>
     * This is TileGym's {@code OP == 1} branch of {@code ops/tilecpp/activation/gelu.cuh}. The
     * exact form needs an error function, which CUDA Tile does not expose as a tile operation;
     * the tanh approximation is what production inference uses anyway, and it is the reason
     * {@link TileContext#tanh} is in the API.
     * </p>
     */
    public static void gelu(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        PartitionView ov = tc.partition(tc.view(out, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        int row = tc.bidX();

        Tile x = iv.load(row, 0);
        Tile ones = tc.full(DType.F32, 1.0, 1, HIDDEN_SMALL);
        Tile cubic = tc.mul(tc.mul(x, x), x);
        Tile inner = tc.scale(tc.add(x, tc.scale(cubic, GELU_CUBIC_TERM)), GELU_COEFFICIENT);
        ov.store(tc.scale(tc.mul(x, tc.add(ones, tc.tanh(inner))), 0.5), row, 0);
    }

    /**
     * GEGLU: the packed row splits into {@code a} and {@code b}, and the result is
     * {@code a * gelu(b)}. Same packed-row split as silu-and-multiply, different activation.
     */
    public static void geglu(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, 2 * HIDDEN_SMALL), 1, HIDDEN_SMALL);
        PartitionView ov = tc.partition(tc.view(out, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        int row = tc.bidX();

        Tile a = iv.load(row, 0);
        Tile b = iv.load(row, 1);
        Tile ones = tc.full(DType.F32, 1.0, 1, HIDDEN_SMALL);
        Tile cubic = tc.mul(tc.mul(b, b), b);
        Tile inner = tc.scale(tc.add(b, tc.scale(cubic, GELU_CUBIC_TERM)), GELU_COEFFICIENT);
        Tile gelu = tc.scale(tc.mul(b, tc.add(ones, tc.tanh(inner))), 0.5);
        ov.store(tc.mul(a, gelu), row, 0);
    }

    /**
     * SwiGLU with the gate and the up-projection in separate buffers, as TileGym's swiglu takes
     * them, rather than split from one packed row as silu_and_mul does.
     */
    public static void swiglu(TileContext tc, FloatArray gate, FloatArray up, FloatArray out, int rows) {
        PartitionView gv = tc.partition(tc.view(gate, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        PartitionView uv = tc.partition(tc.view(up, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        PartitionView ov = tc.partition(tc.view(out, rows, HIDDEN_SMALL), 1, HIDDEN_SMALL);
        int row = tc.bidX();

        Tile g = gv.load(row, 0);
        Tile ones = tc.full(DType.F32, 1.0, 1, HIDDEN_SMALL);
        Tile silu = tc.div(g, tc.add(ones, tc.exp(tc.scale(g, -1.0))));
        ov.store(tc.mul(silu, uv.load(row, 0)), row, 0);
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

    private void checkLayerNorm(int rows, int n) throws TornadoExecutionPlanException {
        FloatArray in = randomRows(rows, n, 97 + n);
        FloatArray out = new FloatArray(rows * n);

        TaskGraph graph = new TaskGraph("ln") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::layerNormRow, new TileContext(), in, out, rows, n, 1.0f / ROW_TILE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("ln.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            // The kernel normalises over the whole tile, so a short row contributes zeros to
            // both reductions. The reference mirrors that rather than pretending otherwise.
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += in.get(row * n + i);
            }
            double mean = sum / ROW_TILE;
            double variance = 0.0;
            for (int i = 0; i < n; i++) {
                double centred = in.get(row * n + i) - mean;
                variance += centred * centred;
            }
            variance += (ROW_TILE - n) * mean * mean;
            double scale = 1.0 / Math.sqrt(variance / ROW_TILE + EPSILON);
            for (int i = 0; i < n; i++) {
                assertEquals("row " + row + " column " + i, (in.get(row * n + i) - mean) * scale, out.get(row * n + i), 5e-2);
            }
        }
    }

    private void checkSiluAndMul(int rows, int hidden) throws TornadoExecutionPlanException {
        FloatArray in = randomRows(rows, 2 * hidden, 137 + hidden);
        FloatArray out = new FloatArray(rows * hidden);

        TaskGraph graph = new TaskGraph("silu") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in);
        if (hidden == 512) {
            graph.task("k", TestTileRowKernels::siluAndMul512, new TileContext(), in, out, rows);
        } else {
            graph.task("k", TestTileRowKernels::siluAndMul2048, new TileContext(), in, out, rows);
        }
        graph.transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("silu.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < hidden; i++) {
                double gate = in.get(row * 2 * hidden + i);
                double value = in.get(row * 2 * hidden + hidden + i);
                double expected = gate / (1.0 + Math.exp(-gate)) * value;
                assertEquals("row " + row + " column " + i, expected, out.get(row * hidden + i), 1e-2);
            }
        }
    }

    // TileGym parameterises silu_and_mul over batch x seq rows and hidden 512..4096, plus
    // irregular rows (7, 13 batches; 100, 333 sequence lengths). The hidden size has to be a
    // power of two here, because it is the tile width that splits the row in half.

    @Test
    public void testSiluAndMulSmallHidden() throws TornadoExecutionPlanException {
        checkSiluAndMul(64, 512);
    }

    @Test
    public void testSiluAndMulLargeHidden() throws TornadoExecutionPlanException {
        checkSiluAndMul(16, 2048);
    }

    @Test
    public void testSiluAndMulIrregularRows() throws TornadoExecutionPlanException {
        // 7 * 100 rows: TileGym's prime batch and odd sequence length.
        checkSiluAndMul(700, 512);
    }

    // TileGym parameterises layer norm over n = 256, 768 and 4096.

    @Test
    public void testLayerNormSquare() throws TornadoExecutionPlanException {
        checkLayerNorm(128, 256);
    }

    @Test
    public void testLayerNormFullRowTile() throws TornadoExecutionPlanException {
        checkLayerNorm(64, ROW_TILE);
    }

    @Test
    public void testLayerNormNonPowerOfTwoRow() throws TornadoExecutionPlanException {
        checkLayerNorm(128, 768);
    }

    private void checkSoftmaxChunked(int rows, int n) throws TornadoExecutionPlanException {
        FloatArray in = randomRows(rows, n, 211 + n);
        FloatArray out = new FloatArray(rows * n);
        int chunks = (n + CHUNK - 1) / CHUNK;

        TaskGraph graph = new TaskGraph("smc") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::softmaxChunked, new TileContext(), in, out, rows, n, chunks) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("smc.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                max = Math.max(max, in.get(row * n + i));
            }
            // Masked lanes in the final chunk read as zero, so they join the sum as exp(-max).
            int padded = chunks * CHUNK;
            double total = 0.0;
            for (int i = 0; i < n; i++) {
                total += Math.exp(in.get(row * n + i) - max);
            }
            total += (padded - n) * Math.exp(-max);
            for (int i = 0; i < n; i++) {
                assertEquals("row " + row + " column " + i, Math.exp(in.get(row * n + i) - max) / total, out.get(row * n + i), 1e-5);
            }
        }
    }

    @Test
    public void testSoftmaxChunkedWideRow() throws TornadoExecutionPlanException {
        // TileGym's widest softmax case, walked in 128 column blocks.
        checkSoftmaxChunked(64, 32768);
    }

    @Test
    public void testSoftmaxChunkedRaggedTail() throws TornadoExecutionPlanException {
        checkSoftmaxChunked(32, 1009);
    }

    @Test
    public void testRmsNormPersistent() throws TornadoExecutionPlanException {
        final int rows = 512;
        final int n = 1024;
        FloatArray in = randomRows(rows, n, 313);
        FloatArray out = new FloatArray(rows * n);

        TaskGraph graph = new TaskGraph("rmsp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::rmsNormPersistent, new TileContext(), in, out, rows, n, 1.0f / ROW_TILE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        // Grid deliberately smaller than the row count, so every block strides over many rows.
        runRowKernel("rmsp.k", graph, 32);

        for (int row = 0; row < rows; row++) {
            double sumSquares = 0.0;
            for (int i = 0; i < n; i++) {
                double v = in.get(row * n + i);
                sumSquares += v * v;
            }
            double scale = 1.0 / Math.sqrt(sumSquares / ROW_TILE + EPSILON);
            for (int i = 0; i < n; i++) {
                assertEquals("row " + row + " column " + i, in.get(row * n + i) * scale, out.get(row * n + i), 5e-2);
            }
        }
    }

    @Test
    public void testSwiglu() throws TornadoExecutionPlanException {
        final int rows = 96;
        FloatArray gate = randomRows(rows, HIDDEN_SMALL, 401);
        FloatArray up = randomRows(rows, HIDDEN_SMALL, 402);
        FloatArray out = new FloatArray(rows * HIDDEN_SMALL);

        TaskGraph graph = new TaskGraph("swiglu") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, gate, up) //
                .task("k", TestTileRowKernels::swiglu, new TileContext(), gate, up, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("swiglu.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < HIDDEN_SMALL; i++) {
                double g = gate.get(row * HIDDEN_SMALL + i);
                double expected = g / (1.0 + Math.exp(-g)) * up.get(row * HIDDEN_SMALL + i);
                assertEquals("row " + row + " column " + i, expected, out.get(row * HIDDEN_SMALL + i), 1e-2);
            }
        }
    }

    @Test
    public void testGeluTanhApproximation() throws TornadoExecutionPlanException {
        final int rows = 96;
        FloatArray in = randomRows(rows, HIDDEN_SMALL, 501);
        FloatArray out = new FloatArray(rows * HIDDEN_SMALL);

        TaskGraph graph = new TaskGraph("gelu") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::gelu, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("gelu.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < HIDDEN_SMALL; i++) {
                double x = in.get(row * HIDDEN_SMALL + i);
                double inner = GELU_COEFFICIENT * (x + GELU_CUBIC_TERM * x * x * x);
                double expected = 0.5 * x * (1.0 + Math.tanh(inner));
                assertEquals("row " + row + " column " + i, expected, out.get(row * HIDDEN_SMALL + i), 1e-3);
            }
        }
    }

    @Test
    public void testGeglu() throws TornadoExecutionPlanException {
        final int rows = 64;
        FloatArray in = randomRows(rows, 2 * HIDDEN_SMALL, 503);
        FloatArray out = new FloatArray(rows * HIDDEN_SMALL);

        TaskGraph graph = new TaskGraph("geglu") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("k", TestTileRowKernels::geglu, new TileContext(), in, out, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        runRowKernel("geglu.k", graph, rows);

        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < HIDDEN_SMALL; i++) {
                double a = in.get(row * 2 * HIDDEN_SMALL + i);
                double b = in.get(row * 2 * HIDDEN_SMALL + HIDDEN_SMALL + i);
                double inner = GELU_COEFFICIENT * (b + GELU_CUBIC_TERM * b * b * b);
                double expected = a * 0.5 * b * (1.0 + Math.tanh(inner));
                assertEquals("row " + row + " column " + i, expected, out.get(row * HIDDEN_SMALL + i), 1e-3);
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
