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
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
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
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * A transformer-block shaped pipeline, built twice: once entirely from JIT-compiled Java
 * kernels, and once from JIT kernels, CUDA Tile kernels and a cuBLAS call together in one
 * {@code TaskGraph}.
 *
 * <p>
 * The stages are the same in both: RMS normalise the activations, compute attention scores,
 * softmax them by row, apply the values, add the residual. What differs is who does each stage:
 * </p>
 *
 * <table border="1">
 * <caption>stage assignment</caption>
 * <tr><th>stage</th><th>all-JIT pipeline</th><th>mixed pipeline</th></tr>
 * <tr><td>RMS norm</td><td>KernelContext</td><td>KernelContext</td></tr>
 * <tr><td>scores</td><td>@Parallel matmul</td><td>CUDA Tile FP16 mma</td></tr>
 * <tr><td>softmax</td><td>@Parallel per row</td><td>CUDA Tile, the row is a tile</td></tr>
 * <tr><td>apply values</td><td>@Parallel matmul</td><td>cuBLAS sgemm</td></tr>
 * <tr><td>residual</td><td>@Parallel</td><td>@Parallel</td></tr>
 * </table>
 *
 * <p>
 * Both are validated against the same sequential reference, and the mixed pipeline is also run
 * captured into a single CUDA graph. Nothing returns to the host between stages in either case.
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileTransformerBlock [sequence] [model] [iterations]
 * </pre>
 */
public class TileTransformerBlock {

    private static final int TILE = 32;

    private static final float EPSILON = 1.0e-5f;

    // -------------------------------------------------------------------------------------
    // Stages shared by both pipelines
    // -------------------------------------------------------------------------------------

    public static void rmsNorm(KernelContext ctx, FloatArray inOut, int width) {
        int row = ctx.globalIdx;
        float total = 0.0f;
        for (int i = 0; i < width; i++) {
            float value = inOut.get(row * width + i);
            total += value * value;
        }
        float scale = 1.0f / TornadoMath.sqrt(total / width + EPSILON);
        for (int i = 0; i < width; i++) {
            inOut.set(row * width + i, inOut.get(row * width + i) * scale);
        }
    }

    /**
     * Out of place on purpose. Written as {@code inOut.set(i, inOut.get(i) + skip.get(i))} the
     * stage is not idempotent: it reads a buffer the graph also returns, so a second execution
     * adds the residual to the previous execution's result rather than to a fresh product.
     */
    public static void residual(FloatArray product, FloatArray skip, FloatArray out) {
        for (@Parallel int i = 0; i < product.getSize(); i++) {
            out.set(i, product.get(i) + skip.get(i));
        }
    }

    // -------------------------------------------------------------------------------------
    // All-JIT stages
    // -------------------------------------------------------------------------------------

    public static void jitScores(FloatArray a, FloatArray b, FloatArray scores, int sequence, int model) {
        for (@Parallel int row = 0; row < sequence; row++) {
            for (@Parallel int column = 0; column < sequence; column++) {
                float sum = 0.0f;
                for (int inner = 0; inner < model; inner++) {
                    sum += a.get(row * model + inner) * b.get(inner * sequence + column);
                }
                scores.set(row * sequence + column, sum);
            }
        }
    }

    public static void jitSoftmax(FloatArray scores, int sequence) {
        for (@Parallel int row = 0; row < sequence; row++) {
            float maximum = -Float.MAX_VALUE;
            for (int column = 0; column < sequence; column++) {
                maximum = TornadoMath.max(maximum, scores.get(row * sequence + column));
            }
            float total = 0.0f;
            for (int column = 0; column < sequence; column++) {
                total += TornadoMath.exp(scores.get(row * sequence + column) - maximum);
            }
            for (int column = 0; column < sequence; column++) {
                scores.set(row * sequence + column, TornadoMath.exp(scores.get(row * sequence + column) - maximum) / total);
            }
        }
    }

    /** The column-major product cuBLAS computes in the mixed pipeline, written as a kernel. */
    public static void jitApply(FloatArray values, FloatArray scores, FloatArray out, int sequence, int model) {
        for (@Parallel int column = 0; column < sequence; column++) {
            for (@Parallel int row = 0; row < model; row++) {
                float sum = 0.0f;
                for (int inner = 0; inner < sequence; inner++) {
                    sum += values.get(inner * model + row) * scores.get(column * sequence + inner);
                }
                out.set(column * model + row, sum);
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Tile stages
    // -------------------------------------------------------------------------------------

    public static void toHalf(FloatArray in, HalfFloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, new HalfFloat(in.get(i)));
        }
    }

    public static void tileScores(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray scores, int sequence, int model) {
        PartitionView aView = tc.partition(tc.view(a, sequence, model), TILE, TILE);
        PartitionView bView = tc.partition(tc.view(b, model, sequence), TILE, TILE);
        PartitionView scoreView = tc.partition(tc.view(scores, sequence, sequence), TILE, TILE);

        int rowBlock = tc.bidX();
        int columnBlock = tc.bidY();
        Tile acc = tc.zeros(DType.F32, TILE, TILE);
        for (int step = 0; step < model / TILE; step++) {
            acc = tc.mma(aView.load(rowBlock, step), bView.load(step, columnBlock), acc);
        }
        scoreView.store(acc, rowBlock, columnBlock);
    }

    /** Row softmax where the row is one tile: four operations and no loop. */
    public static void tileSoftmax(TileContext tc, FloatArray scores, int sequence, int width) {
        PartitionView view = tc.partition(tc.view(scores, sequence, width), 1, WIDTH_CONSTANT);
        int row = tc.bidX();
        Tile values = view.load(row, 0);
        Tile shifted = tc.exp(tc.sub(values, tc.max(values, 1)));
        view.store(tc.div(shifted, tc.sum(shifted, 1)), row, 0);
    }

    /** The sequence length the tile softmax is compiled for; a tile shape must be a constant. */
    private static final int WIDTH_CONSTANT = 256;

    // -------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int sequence = args.length > 0 ? Integer.parseInt(args[0]) : WIDTH_CONSTANT;
        int model = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        if (sequence != WIDTH_CONSTANT) {
            System.out.printf("The tile softmax is compiled for a sequence length of %d; pass that or edit WIDTH_CONSTANT.%n", WIDTH_CONSTANT);
            return;
        }
        if (sequence % TILE != 0 || model % TILE != 0) {
            System.out.printf("sequence and model must be multiples of %d%n", TILE);
            return;
        }
        TileExamples.requireCuda();

        Random random = new Random(2003);
        float[] activationSeed = new float[sequence * model];
        float[] weightSeed = new float[model * sequence];
        float[] valueSeed = new float[sequence * model];
        float[] skipSeed = new float[sequence * model];
        for (int i = 0; i < activationSeed.length; i++) {
            activationSeed[i] = random.nextFloat() - 0.5f;
            valueSeed[i] = random.nextFloat() - 0.5f;
            skipSeed[i] = random.nextFloat() - 0.5f;
        }
        for (int i = 0; i < weightSeed.length; i++) {
            weightSeed[i] = random.nextFloat() - 0.5f;
        }

        System.out.printf("Transformer block: sequence %d, model %d, %d iterations%n", sequence, model, iterations);
        System.out.println("stages: RMS norm -> scores -> row softmax -> apply values -> residual");
        System.out.println();

        long start = System.nanoTime();
        float[] expected = reference(activationSeed, weightSeed, valueSeed, skipSeed, sequence, model);
        double sequential = (System.nanoTime() - start) / 1e6;

        FloatArray jitOut = new FloatArray(sequence * model);
        double jitTime = runAllJit(activationSeed, weightSeed, valueSeed, skipSeed, jitOut, sequence, model, iterations);

        FloatArray mixedOut = new FloatArray(sequence * model);
        double mixedTime = runMixed(activationSeed, weightSeed, valueSeed, skipSeed, mixedOut, sequence, model, iterations, false);

        FloatArray graphOut = new FloatArray(sequence * model);
        double graphTime = runMixed(activationSeed, weightSeed, valueSeed, skipSeed, graphOut, sequence, model, iterations, true);

        // The mixed pipeline narrows the matmul operands to FP16, so its tolerance covers that
        // rounding; the all-JIT pipeline stays in FP32 throughout.
        final double tolerance = 0.05;
        TileExamples.header();
        TileExamples.report("sequential Java (reference)", sequential, sequential, 0.0, 0.0, tolerance);
        TileExamples.report("all JIT (@Parallel + KernelContext)", jitTime, sequential, 0.0, TileExamples.maxError(jitOut, expected), tolerance);
        TileExamples.report("JIT + CUDA Tile + cuBLAS", mixedTime, sequential, 0.0, TileExamples.maxError(mixedOut, expected), tolerance);
        TileExamples.report("the same, in one CUDA graph", graphTime, sequential, 0.0, TileExamples.maxError(graphOut, expected), tolerance);
        TileExamples.footer();
        System.out.println();
        System.out.printf("mixed vs all-JIT: %.2fx;  CUDA graph vs mixed: %.2fx%n", jitTime / mixedTime, mixedTime / graphTime);
        System.out.println("Both pipelines keep every intermediate on the device; the CUDA graph row differs");
        System.out.println("only in how the five stages are submitted.");
    }

    private static double runAllJit(float[] activationSeed, float[] weightSeed, float[] valueSeed, float[] skipSeed, FloatArray out, int sequence, int model, int iterations) {
        FloatArray activations = copy(activationSeed);
        FloatArray weights = copy(weightSeed);
        FloatArray values = copy(valueSeed);
        FloatArray skip = copy(skipSeed);
        FloatArray scores = new FloatArray(sequence * sequence);
        FloatArray product = new FloatArray(sequence * model);

        WorkerGrid1D normWorker = new WorkerGrid1D(sequence);
        normWorker.setLocalWork(32, 1, 1);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("jit.norm", normWorker);
        grid.addWorkerGrid("jit.scores", new WorkerGrid2D(sequence, sequence));
        grid.addWorkerGrid("jit.softmax", new WorkerGrid1D(sequence));
        grid.addWorkerGrid("jit.apply", new WorkerGrid2D(sequence, model));

        TaskGraph graph = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, activations, weights, values, skip) //
                .task("norm", TileTransformerBlock::rmsNorm, new KernelContext(), activations, model) //
                .task("scores", TileTransformerBlock::jitScores, activations, weights, scores, sequence, model) //
                .task("softmax", TileTransformerBlock::jitSoftmax, scores, sequence) //
                .task("apply", TileTransformerBlock::jitApply, values, scores, product, sequence, model) //
                .task("residual", TileTransformerBlock::residual, product, skip, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        return TileExamples.time(graph, grid, iterations);
    }

    private static double runMixed(float[] activationSeed, float[] weightSeed, float[] valueSeed, float[] skipSeed, FloatArray out, int sequence, int model, int iterations,
            boolean cudaGraph) {
        FloatArray activations = copy(activationSeed);
        FloatArray weights = copy(weightSeed);
        FloatArray values = copy(valueSeed);
        FloatArray skip = copy(skipSeed);
        HalfFloatArray activationsHalf = new HalfFloatArray(sequence * model);
        HalfFloatArray weightsHalf = new HalfFloatArray(model * sequence);
        FloatArray scores = new FloatArray(sequence * sequence);
        FloatArray product = new FloatArray(sequence * model);

        WorkerGrid1D normWorker = new WorkerGrid1D(sequence);
        normWorker.setLocalWork(32, 1, 1);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("mixed.norm", normWorker);
        grid.addWorkerGrid("mixed.scores", new WorkerGrid2D(sequence / TILE, sequence / TILE));
        grid.addWorkerGrid("mixed.softmax", new WorkerGrid1D(sequence));

        TaskGraph graph = new TaskGraph("mixed") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, activations, weights, values, skip) //
                .task("norm", TileTransformerBlock::rmsNorm, new KernelContext(), activations, model) //
                .task("castA", TileTransformerBlock::toHalf, activations, activationsHalf) //
                .task("castW", TileTransformerBlock::toHalf, weights, weightsHalf) //
                .task("scores", TileTransformerBlock::tileScores, new TileContext(), activationsHalf, weightsHalf, scores, sequence, model) //
                .task("softmax", TileTransformerBlock::tileSoftmax, new TileContext(), scores, sequence, sequence) //
                .libraryTask("apply", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        model, sequence, sequence, 1.0f, values, model, scores, sequence, 0.0f, product, model) //
                .task("residual", TileTransformerBlock::residual, product, skip, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        if (!cudaGraph) {
            return TileExamples.time(graph, grid, iterations);
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).withCUDAGraph();
            plan.execute();
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                plan.execute();
            }
            return (System.nanoTime() - start) / 1e6 / iterations;
        } catch (Exception e) {
            System.out.println("  [CUDA graph failed] " + e.getMessage());
            return Double.NaN;
        }
    }

    private static FloatArray copy(float[] source) {
        FloatArray array = new FloatArray(source.length);
        for (int i = 0; i < source.length; i++) {
            array.set(i, source[i]);
        }
        return array;
    }

    /** The softmaxed score matrix alone, for diagnosis. */
    private static float[] referenceScores(float[] activations, float[] weights, int sequence, int model) {
        float[] normalised = new float[sequence * model];
        for (int row = 0; row < sequence; row++) {
            double total = 0.0;
            for (int i = 0; i < model; i++) {
                total += activations[row * model + i] * activations[row * model + i];
            }
            double scale = 1.0 / Math.sqrt(total / model + EPSILON);
            for (int i = 0; i < model; i++) {
                normalised[row * model + i] = (float) (activations[row * model + i] * scale);
            }
        }
        float[] scores = new float[sequence * sequence];
        for (int row = 0; row < sequence; row++) {
            for (int column = 0; column < sequence; column++) {
                float sum = 0.0f;
                for (int inner = 0; inner < model; inner++) {
                    sum += normalised[row * model + inner] * weights[inner * sequence + column];
                }
                scores[row * sequence + column] = sum;
            }
            double maximum = Double.NEGATIVE_INFINITY;
            for (int column = 0; column < sequence; column++) {
                maximum = Math.max(maximum, scores[row * sequence + column]);
            }
            double total = 0.0;
            for (int column = 0; column < sequence; column++) {
                total += Math.exp(scores[row * sequence + column] - maximum);
            }
            for (int column = 0; column < sequence; column++) {
                scores[row * sequence + column] = (float) (Math.exp(scores[row * sequence + column] - maximum) / total);
            }
        }
        return scores;
    }

    /** The same five stages, sequentially, in FP32. */
    private static float[] reference(float[] activations, float[] weights, float[] values, float[] skip, int sequence, int model) {
        float[] normalised = new float[sequence * model];
        for (int row = 0; row < sequence; row++) {
            double total = 0.0;
            for (int i = 0; i < model; i++) {
                total += activations[row * model + i] * activations[row * model + i];
            }
            double scale = 1.0 / Math.sqrt(total / model + EPSILON);
            for (int i = 0; i < model; i++) {
                normalised[row * model + i] = (float) (activations[row * model + i] * scale);
            }
        }
        float[] scores = new float[sequence * sequence];
        for (int row = 0; row < sequence; row++) {
            for (int column = 0; column < sequence; column++) {
                float sum = 0.0f;
                for (int inner = 0; inner < model; inner++) {
                    sum += normalised[row * model + inner] * weights[inner * sequence + column];
                }
                scores[row * sequence + column] = sum;
            }
        }
        for (int row = 0; row < sequence; row++) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (int column = 0; column < sequence; column++) {
                maximum = Math.max(maximum, scores[row * sequence + column]);
            }
            double total = 0.0;
            for (int column = 0; column < sequence; column++) {
                total += Math.exp(scores[row * sequence + column] - maximum);
            }
            for (int column = 0; column < sequence; column++) {
                scores[row * sequence + column] = (float) (Math.exp(scores[row * sequence + column] - maximum) / total);
            }
        }
        float[] out = new float[sequence * model];
        for (int column = 0; column < sequence; column++) {
            for (int row = 0; row < model; row++) {
                float sum = 0.0f;
                for (int inner = 0; inner < sequence; inner++) {
                    sum += values[inner * model + row] * scores[column * sequence + inner];
                }
                out[column * model + row] = sum + skip[column * model + row];
            }
        }
        return out;
    }
}
