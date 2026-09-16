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
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cutlass.Cutlass;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Pipelines that mix all three kinds of task in one {@code TaskGraph}: JIT-compiled SIMT
 * kernels (both {@code @Parallel} and {@code KernelContext}), CUDA Tile kernels, and native
 * library tasks (cuBLAS and CUTLASS).
 *
 * <p>
 * {@link TestTileChaining} establishes that the combination works at all. These tests are the
 * shapes a real workload takes: a transformer block, a library-fused epilogue feeding a tile
 * kernel, a tile kernel between two library calls, and several such chains in flight at once.
 * Each is checked end to end against a sequential reference, because the interesting failures
 * here are not crashes but a stage silently reading the wrong bytes.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileMixedPipelines
 * </pre>
 */
public class TestTileMixedPipelines extends TornadoTestBase {

    private static final int TILE = 32;

    private static final int MODEL = 128;

    private static final int SEQUENCE = 64;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    // -------------------------------------------------------------------------------------
    // Kernels
    // -------------------------------------------------------------------------------------

    /** JIT, thread-level: RMS normalisation of one row per thread. */
    public static void rmsNorm(KernelContext ctx, FloatArray inOut, int width, float epsilon) {
        int row = ctx.globalIdx;
        float total = 0.0f;
        for (int i = 0; i < width; i++) {
            float value = inOut.get(row * width + i);
            total += value * value;
        }
        float scale = 1.0f / TornadoMath.sqrt(total / width + epsilon);
        for (int i = 0; i < width; i++) {
            inOut.set(row * width + i, inOut.get(row * width + i) * scale);
        }
    }

    /** JIT, thread-level: narrow an FP32 buffer to FP16 so a tile or CUTLASS task can read it. */
    public static void toHalf(FloatArray in, HalfFloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, new HalfFloat(in.get(i)));
        }
    }

    /** Tile: FP16 matmul with FP32 accumulation. */
    public static void tileGemm(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k) {
        PartitionView aView = tc.partition(tc.view(a, m, k), TILE, TILE);
        PartitionView bView = tc.partition(tc.view(b, k, n), TILE, TILE);
        PartitionView cView = tc.partition(tc.view(c, m, n), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.F32, TILE, TILE);
        for (int step = 0; step < k / TILE; step++) {
            acc = tc.mma(aView.load(rowBlock, step), bView.load(step, columnBlock), acc);
        }
        cView.store(acc, rowBlock, columnBlock);
    }

    /** Tile: row softmax, the stage a library cannot do. */
    public static void tileSoftmax(TileContext tc, FloatArray inOut, int rows) {
        PartitionView view = tc.partition(tc.view(inOut, rows, SEQUENCE), 1, SEQUENCE);
        int row = tc.bidX();
        Tile values = view.load(row, 0);
        Tile shifted = tc.exp(tc.sub(values, tc.max(values, 1)));
        view.store(tc.div(shifted, tc.sum(shifted, 1)), row, 0);
    }

    /** Tile: SiLU gate, elementwise. */
    public static void tileSilu(TileContext tc, FloatArray inOut, int rows) {
        PartitionView view = tc.partition(tc.view(inOut, rows, MODEL), 1, MODEL);
        int row = tc.bidX();
        Tile x = view.load(row, 0);
        Tile ones = tc.ones(DType.F32, 1, MODEL);
        view.store(tc.div(x, tc.add(ones, tc.exp(tc.scale(x, -1.0)))), row, 0);
    }

    /** JIT, plain @Parallel: residual add. */
    public static void residual(FloatArray inOut, FloatArray residual) {
        for (@Parallel int i = 0; i < inOut.getSize(); i++) {
            inOut.set(i, inOut.get(i) + residual.get(i));
        }
    }

    // -------------------------------------------------------------------------------------
    // 1. A transformer-block shaped pipeline
    // -------------------------------------------------------------------------------------

    /**
     * Five stages, three kinds of task, one graph:
     *
     * <ol>
     * <li>{@code KernelContext} RMS norm over the activations,</li>
     * <li>{@code @Parallel} narrowing to FP16,</li>
     * <li>a tile FP16 matmul producing the attention scores,</li>
     * <li>a tile row softmax over them,</li>
     * <li>a cuBLAS {@code sgemm} applying the values, and a {@code @Parallel} residual add.</li>
     * </ol>
     *
     * <p>
     * The interesting property is that nothing returns to the host between stages, and the
     * FP16 and FP32 views of the same pipeline sit next to each other.
     * </p>
     */
    private void runTransformerBlock(boolean cudaGraph) throws TornadoExecutionPlanException {
        FloatArray activations = new FloatArray(SEQUENCE * MODEL);
        FloatArray weights = new FloatArray(MODEL * SEQUENCE);
        FloatArray values = new FloatArray(SEQUENCE * MODEL);
        FloatArray skip = new FloatArray(SEQUENCE * MODEL);
        HalfFloatArray activationsHalf = new HalfFloatArray(SEQUENCE * MODEL);
        HalfFloatArray weightsHalf = new HalfFloatArray(MODEL * SEQUENCE);
        FloatArray scores = new FloatArray(SEQUENCE * SEQUENCE);
        FloatArray output = new FloatArray(SEQUENCE * MODEL);

        Random random = new Random(2003);
        for (int i = 0; i < SEQUENCE * MODEL; i++) {
            activations.set(i, random.nextFloat() - 0.5f);
            skip.set(i, random.nextFloat() - 0.5f);
            values.set(i, random.nextFloat() - 0.5f);
        }
        for (int i = 0; i < MODEL * SEQUENCE; i++) {
            weights.set(i, random.nextFloat() - 0.5f);
        }
        float[] reference = transformerReference(activations, weights, values, skip);

        WorkerGrid1D normWorker = new WorkerGrid1D(SEQUENCE);
        normWorker.setLocalWork(32, 1, 1);
        WorkerGrid2D gemmWorker = new WorkerGrid2D(SEQUENCE / TILE, SEQUENCE / TILE);
        WorkerGrid1D softmaxWorker = new WorkerGrid1D(SEQUENCE);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("block.norm", normWorker);
        grid.addWorkerGrid("block.scores", gemmWorker);
        grid.addWorkerGrid("block.softmax", softmaxWorker);

        TaskGraph graph = new TaskGraph("block") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, activations, weights, values, skip) //
                .task("norm", TestTileMixedPipelines::rmsNorm, new KernelContext(), activations, MODEL, 1.0e-5f) //
                .task("castA", TestTileMixedPipelines::toHalf, activations, activationsHalf) //
                .task("castW", TestTileMixedPipelines::toHalf, weights, weightsHalf) //
                .task("scores", TestTileMixedPipelines::tileGemm, new TileContext(), activationsHalf, weightsHalf, scores, SEQUENCE, SEQUENCE, MODEL) //
                .task("softmax", TestTileMixedPipelines::tileSoftmax, new TileContext(), scores, SEQUENCE) //
                .libraryTask("apply", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        MODEL, SEQUENCE, SEQUENCE, 1.0f, values, MODEL, scores, SEQUENCE, 0.0f, output, MODEL) //
                .task("residual", TestTileMixedPipelines::residual, output, skip) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid);
            if (cudaGraph) {
                plan.withCUDAGraph();
            }
            plan.execute();
            if (cudaGraph) {
                // A captured graph must replay to the same answer, not just run.
                plan.execute();
            }
        }

        for (int i = 0; i < SEQUENCE * MODEL; i++) {
            assertEquals("element " + i, reference[i], output.get(i), 0.05f);
        }
    }

    /**
     * The same five stages on the host. cuBLAS is column major, so the {@code sgemm} above
     * computes {@code values * scores} with both read in that order, which is what this
     * mirrors.
     */
    private static float[] transformerReference(FloatArray activations, FloatArray weights, FloatArray values, FloatArray skip) {
        float[] normalised = new float[SEQUENCE * MODEL];
        for (int row = 0; row < SEQUENCE; row++) {
            double total = 0.0;
            for (int i = 0; i < MODEL; i++) {
                double value = activations.get(row * MODEL + i);
                total += value * value;
            }
            double scale = 1.0 / Math.sqrt(total / MODEL + 1.0e-5);
            for (int i = 0; i < MODEL; i++) {
                // The device narrows to FP16 before the matmul, so the reference does too.
                normalised[row * MODEL + i] = new HalfFloat((float) (activations.get(row * MODEL + i) * scale)).getFloat32();
            }
        }
        float[] scores = new float[SEQUENCE * SEQUENCE];
        for (int row = 0; row < SEQUENCE; row++) {
            for (int column = 0; column < SEQUENCE; column++) {
                float sum = 0.0f;
                for (int inner = 0; inner < MODEL; inner++) {
                    sum += normalised[row * MODEL + inner] * new HalfFloat(weights.get(inner * SEQUENCE + column)).getFloat32();
                }
                scores[row * SEQUENCE + column] = sum;
            }
        }
        for (int row = 0; row < SEQUENCE; row++) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (int column = 0; column < SEQUENCE; column++) {
                maximum = Math.max(maximum, scores[row * SEQUENCE + column]);
            }
            double total = 0.0;
            for (int column = 0; column < SEQUENCE; column++) {
                total += Math.exp(scores[row * SEQUENCE + column] - maximum);
            }
            for (int column = 0; column < SEQUENCE; column++) {
                scores[row * SEQUENCE + column] = (float) (Math.exp(scores[row * SEQUENCE + column] - maximum) / total);
            }
        }
        float[] out = new float[SEQUENCE * MODEL];
        for (int column = 0; column < SEQUENCE; column++) {
            for (int row = 0; row < MODEL; row++) {
                float sum = 0.0f;
                for (int inner = 0; inner < SEQUENCE; inner++) {
                    sum += values.get(inner * MODEL + row) * scores[column * SEQUENCE + inner];
                }
                out[column * MODEL + row] = sum + skip.get(column * MODEL + row);
            }
        }
        return out;
    }

    @Test
    public void testTransformerBlock() throws TornadoExecutionPlanException {
        runTransformerBlock(false);
    }

    /** The same pipeline captured into one CUDA graph and replayed. */
    @Test
    public void testTransformerBlockUnderCudaGraph() throws TornadoExecutionPlanException {
        runTransformerBlock(true);
    }

    // -------------------------------------------------------------------------------------
    // 2. A CUTLASS fused epilogue feeding a tile kernel
    // -------------------------------------------------------------------------------------

    /**
     * {@code cutlassGemmBiasRelu} does a GEMM, a bias and a ReLU in one library kernel; a tile
     * task then consumes its FP16 output directly, and a JIT task finishes.
     *
     * <p>
     * This is the combination worth having: the library owns the part it is best at, and the
     * tile kernel owns the part no library exposes, with no copy between them.
     * </p>
     */
    @Test
    public void testCutlassEpilogueThenTile() throws TornadoExecutionPlanException {
        final int size = 64;
        HalfFloatArray a = new HalfFloatArray(size * size);
        HalfFloatArray b = new HalfFloatArray(size * size);
        HalfFloatArray bias = new HalfFloatArray(size);
        HalfFloatArray fused = new HalfFloatArray(size * size);
        HalfFloatArray identity = new HalfFloatArray(size * size);
        FloatArray tiled = new FloatArray(size * size);

        Random random = new Random(2011);
        for (int i = 0; i < size * size; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            identity.set(i, new HalfFloat((i / size) == (i % size) ? 1.0f : 0.0f));
        }
        for (int i = 0; i < size; i++) {
            bias.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        WorkerGrid2D tileWorker = new WorkerGrid2D(size / TILE, size / TILE);
        GridScheduler grid = new GridScheduler("fusedChain.tile", tileWorker);
        TaskGraph graph = new TaskGraph("fusedChain") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, bias, identity) //
                .libraryTask("gemm", Cutlass::cutlassGemmBiasRelu, size, size, size, a, b, bias, fused) //
                // The tile task multiplies by the identity, so it must reproduce the library's
                // output exactly: any disagreement about the buffer contents shows up directly.
                .task("tile", TestTileMixedPipelines::tileGemm, new TileContext(), fused, identity, tiled, size, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, fused, tiled);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < size * size; i++) {
            assertEquals("element " + i, fused.get(i).getFloat32(), tiled.get(i), 0.05f);
        }
    }

    // -------------------------------------------------------------------------------------
    // 3. A tile kernel between two library calls
    // -------------------------------------------------------------------------------------

    /**
     * cuBLAS produces, a tile kernel transforms, cuBLAS consumes. The tile stage is a row
     * softmax, which neither library call can do, and both library calls read and write the
     * same device buffers it touches.
     */
    @Test
    public void testTileBetweenTwoLibraryCalls() throws TornadoExecutionPlanException {
        FloatArray left = new FloatArray(SEQUENCE * SEQUENCE);
        FloatArray right = new FloatArray(SEQUENCE * SEQUENCE);
        FloatArray middle = new FloatArray(SEQUENCE * SEQUENCE);
        FloatArray tail = new FloatArray(SEQUENCE * SEQUENCE);
        FloatArray output = new FloatArray(SEQUENCE * SEQUENCE);

        Random random = new Random(2017);
        for (int i = 0; i < SEQUENCE * SEQUENCE; i++) {
            left.set(i, random.nextFloat() - 0.5f);
            right.set(i, random.nextFloat() - 0.5f);
            tail.set(i, random.nextFloat() - 0.5f);
        }

        GridScheduler grid = new GridScheduler("sandwich.softmax", new WorkerGrid1D(SEQUENCE));
        TaskGraph graph = new TaskGraph("sandwich") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, left, right, tail) //
                .libraryTask("first", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SEQUENCE, SEQUENCE, SEQUENCE, 1.0f, left, SEQUENCE, right, SEQUENCE, 0.0f, middle, SEQUENCE) //
                .task("softmax", TestTileMixedPipelines::tileSoftmax, new TileContext(), middle, SEQUENCE) //
                .libraryTask("second", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        SEQUENCE, SEQUENCE, SEQUENCE, 1.0f, middle, SEQUENCE, tail, SEQUENCE, 0.0f, output, SEQUENCE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, middle, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        // Every row of the tile stage's output must be a distribution, and the second library
        // call must have consumed exactly those numbers.
        for (int row = 0; row < SEQUENCE; row++) {
            double total = 0.0;
            for (int column = 0; column < SEQUENCE; column++) {
                total += middle.get(row * SEQUENCE + column);
            }
            assertEquals("row " + row + " is not normalised", 1.0, total, 1e-4);
        }
        for (int row = 0; row < SEQUENCE; row++) {
            for (int column = 0; column < SEQUENCE; column++) {
                float expected = 0.0f;
                for (int inner = 0; inner < SEQUENCE; inner++) {
                    expected += middle.get(inner * SEQUENCE + row) * tail.get(column * SEQUENCE + inner);
                }
                assertEquals("(" + row + "," + column + ")", expected, output.get(column * SEQUENCE + row), 0.02f);
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // 4. Several mixed chains in flight at once
    // -------------------------------------------------------------------------------------

    /**
     * Four independent JIT + tile + library chains in one graph, executed with
     * {@code withIntraPlanConcurrency()} so the runtime may put them on different streams.
     * Each chain has its own buffers, so any cross-talk between the streams shows up as a
     * wrong answer in one of them.
     */
    @Test
    public void testFourMixedChainsConcurrently() throws TornadoExecutionPlanException {
        final int chains = 4;
        final int size = 64;
        FloatArray[] inputs = new FloatArray[chains];
        FloatArray[] matrices = new FloatArray[chains];
        FloatArray[] scaled = new FloatArray[chains];
        FloatArray[] outputs = new FloatArray[chains];
        Random random = new Random(2027);

        TaskGraph graph = new TaskGraph("concurrent");
        GridScheduler grid = new GridScheduler();
        for (int chain = 0; chain < chains; chain++) {
            inputs[chain] = new FloatArray(size);
            matrices[chain] = new FloatArray(size * size);
            scaled[chain] = new FloatArray(size);
            outputs[chain] = new FloatArray(size);
            for (int i = 0; i < size; i++) {
                inputs[chain].set(i, random.nextFloat());
            }
            for (int i = 0; i < size * size; i++) {
                matrices[chain].set(i, random.nextFloat() - 0.5f);
            }
            graph.transferToDevice(DataTransferMode.EVERY_EXECUTION, inputs[chain], matrices[chain]);
            graph.task("silu" + chain, TestTileMixedPipelines::tileSiluVector, new TileContext(), inputs[chain], scaled[chain], size);
            graph.libraryTask("gemv" + chain, CuBlas::cublasSgemv, //
                    CuBlasOperation.CUBLAS_OP_T.operation(), size, size, 1.0f, matrices[chain], size, scaled[chain], 1, 0.0f, outputs[chain], 1);
            graph.task("bias" + chain, TestTileMixedPipelines::addOne, outputs[chain]);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, outputs[chain]);
            grid.addWorkerGrid("concurrent.silu" + chain, new WorkerGrid1D(size / TILE));
        }

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).withIntraPlanConcurrency().execute();
        }

        for (int chain = 0; chain < chains; chain++) {
            for (int row = 0; row < size; row++) {
                double expected = 1.0;
                for (int column = 0; column < size; column++) {
                    double x = inputs[chain].get(column);
                    expected += matrices[chain].get(row * size + column) * (x / (1.0 + Math.exp(-x)));
                }
                assertEquals("chain " + chain + " row " + row, expected, outputs[chain].get(row), 0.02);
            }
        }
    }

    /** Tile: SiLU over a rank-1 view, for the concurrent chains. */
    public static void tileSiluVector(TileContext tc, FloatArray in, FloatArray out, int n) {
        PartitionView iv = tc.partition(tc.view(in, n), TILE);
        PartitionView ov = tc.partition(tc.view(out, n), TILE);
        int block = tc.bidX();
        Tile x = iv.load(block);
        Tile ones = tc.ones(DType.F32, TILE);
        ov.store(tc.div(x, tc.add(ones, tc.exp(tc.scale(x, -1.0)))), block);
    }

    /** JIT: the final bias of each concurrent chain. */
    public static void addOne(FloatArray inOut) {
        for (@Parallel int i = 0; i < inOut.getSize(); i++) {
            inOut.set(i, inOut.get(i) + 1.0f);
        }
    }
}
