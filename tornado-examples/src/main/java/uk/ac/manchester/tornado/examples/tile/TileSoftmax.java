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
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Row-wise softmax, the numerically stable formulation, three ways.
 *
 * <p>
 * {@code softmax(x)_i = exp(x_i - max(x)) / sum_j exp(x_j - max(x))}. The maximum subtraction is
 * what keeps the exponential in range, and it is also what makes this interesting to write with
 * tiles: the row maximum and the row sum are reductions whose results have to reach every
 * element of the row again.
 * </p>
 *
 * <ol>
 * <li>{@code @Parallel} - one thread per row, three passes over global memory.</li>
 * <li>{@code KernelContext} - one thread per row, staging the row in a local array.</li>
 * <li>{@code TileContext} - the row is one tile; reduce, broadcast and divide are operations
 * on it, and the compiler decides how many threads do the work.</li>
 * </ol>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileSoftmax [rows] [iterations]
 * </pre>
 */
public class TileSoftmax {

    /**
     * Row width. A tile shape is part of the kernel's type, so this is a constant while the row
     * count stays a runtime value.
     */
    private static final int WIDTH = 1024;

    // -------------------------------------------------------------------------------------
    // 1. One thread per row, straight over global memory
    // -------------------------------------------------------------------------------------

    public static void threadPerRow(FloatArray in, FloatArray out, int rows) {
        for (@Parallel int row = 0; row < rows; row++) {
            float maximum = -Float.MAX_VALUE;
            for (int i = 0; i < WIDTH; i++) {
                maximum = TornadoMath.max(maximum, in.get(row * WIDTH + i));
            }
            float total = 0.0f;
            for (int i = 0; i < WIDTH; i++) {
                float value = TornadoMath.exp(in.get(row * WIDTH + i) - maximum);
                out.set(row * WIDTH + i, value);
                total += value;
            }
            for (int i = 0; i < WIDTH; i++) {
                out.set(row * WIDTH + i, out.get(row * WIDTH + i) / total);
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // 2. One thread per row, staging the row in local memory
    // -------------------------------------------------------------------------------------

    public static void localMemoryRow(KernelContext ctx, FloatArray in, FloatArray out, int rows) {
        int row = ctx.globalIdx;
        float maximum = -Float.MAX_VALUE;
        for (int i = 0; i < WIDTH; i++) {
            maximum = TornadoMath.max(maximum, in.get(row * WIDTH + i));
        }
        float total = 0.0f;
        for (int i = 0; i < WIDTH; i++) {
            total += TornadoMath.exp(in.get(row * WIDTH + i) - maximum);
        }
        for (int i = 0; i < WIDTH; i++) {
            out.set(row * WIDTH + i, TornadoMath.exp(in.get(row * WIDTH + i) - maximum) / total);
        }
    }

    // -------------------------------------------------------------------------------------
    // 3. The row is one tile
    // -------------------------------------------------------------------------------------

    /**
     * Four operations on a tile, and no loop at all. {@code max} and {@code sum} keep the
     * reduced dimension, so a {@code [1, 1]} result broadcasts back across the row.
     */
    public static void tileRow(TileContext tc, FloatArray in, FloatArray out, int rows) {
        PartitionView iv = tc.partition(tc.view(in, rows, WIDTH), 1, WIDTH);
        PartitionView ov = tc.partition(tc.view(out, rows, WIDTH), 1, WIDTH);

        int row = tc.bidX();
        Tile values = iv.load(row, 0);
        Tile shifted = tc.exp(tc.sub(values, tc.max(values, 1)));
        ov.store(tc.div(shifted, tc.sum(shifted, 1)), row, 0);
    }

    // -------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int rows = args.length > 0 ? Integer.parseInt(args[0]) : 4096;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        TileExamples.requireCuda();

        FloatArray in = new FloatArray(rows * WIDTH);
        Random random = new Random(11);
        for (int i = 0; i < rows * WIDTH; i++) {
            in.set(i, 8.0f * random.nextFloat() - 4.0f);
        }

        System.out.printf("Row softmax, %d rows of %d, %d iterations%n%n", rows, WIDTH, iterations);

        float[] expected = new float[rows * WIDTH];
        long start = System.nanoTime();
        for (int row = 0; row < rows; row++) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < WIDTH; i++) {
                maximum = Math.max(maximum, in.get(row * WIDTH + i));
            }
            double total = 0.0;
            for (int i = 0; i < WIDTH; i++) {
                total += Math.exp(in.get(row * WIDTH + i) - maximum);
            }
            for (int i = 0; i < WIDTH; i++) {
                expected[row * WIDTH + i] = (float) (Math.exp(in.get(row * WIDTH + i) - maximum) / total);
            }
        }
        double sequential = (System.nanoTime() - start) / 1e6;

        FloatArray threadOut = new FloatArray(rows * WIDTH);
        FloatArray localOut = new FloatArray(rows * WIDTH);
        FloatArray tileOut = new FloatArray(rows * WIDTH);

        double threadTime = TileExamples.time(new TaskGraph("perRow") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("k", TileSoftmax::threadPerRow, in, threadOut, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadOut), //
                new GridScheduler("perRow.k", new WorkerGrid1D(rows)), iterations);

        WorkerGrid1D localWorker = new WorkerGrid1D(rows);
        localWorker.setLocalWork(64, 1, 1);
        double localTime = TileExamples.time(new TaskGraph("local") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("k", TileSoftmax::localMemoryRow, new KernelContext(), in, localOut, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, localOut), //
                new GridScheduler("local.k", localWorker), iterations);

        // One tile block per row.
        double tileTime = TileExamples.time(new TaskGraph("tile") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("k", TileSoftmax::tileRow, new TileContext(), in, tileOut, rows) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileOut), //
                new GridScheduler("tile.k", new WorkerGrid1D(rows)), iterations);

        final double tolerance = 1e-6;
        TileExamples.header();
        TileExamples.report("sequential Java (reference)", sequential, sequential, 0.0, 0.0, tolerance);
        TileExamples.report("@Parallel, one thread per row", threadTime, sequential, 0.0, TileExamples.maxError(threadOut, expected), tolerance);
        TileExamples.report("KernelContext, one thread per row", localTime, sequential, 0.0, TileExamples.maxError(localOut, expected), tolerance);
        TileExamples.report("TileContext, the row is a tile", tileTime, sequential, 0.0, TileExamples.maxError(tileOut, expected), tolerance);
        TileExamples.footer();
    }
}
