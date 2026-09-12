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
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Matrix multiplication, the textbook algorithm, three ways.
 *
 * <ol>
 * <li>{@code @Parallel} - one GPU thread per output element.</li>
 * <li>{@code KernelContext} - shared-memory tiles staged and barriered by hand.</li>
 * <li>{@code TileContext} - a tile is the unit of work: {@code acc = tc.mma(a, b, acc)}.</li>
 * </ol>
 *
 * <p>
 * All three compute the same {@code C = A x B} and are checked against a sequential Java
 * reference computed over the same FP16-rounded inputs, so the comparison measures the kernels
 * and not the rounding.
 * </p>
 *
 * <pre>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileMatrixMultiply [n] [iterations]
 * </pre>
 */
public class TileMatrixMultiply {

    /** Tile shape. A CUDA Tile shape is part of the kernel's type, so it is a constant. */
    private static final int TILE = 32;

    // -------------------------------------------------------------------------------------
    // 1. One thread per output element
    // -------------------------------------------------------------------------------------

    public static void threadPerElement(HalfFloatArray a, HalfFloatArray b, FloatArray c, int n) {
        for (@Parallel int row = 0; row < n; row++) {
            for (@Parallel int column = 0; column < n; column++) {
                float sum = 0.0f;
                for (int inner = 0; inner < n; inner++) {
                    sum += a.get(row * n + inner).getFloat32() * b.get(inner * n + column).getFloat32();
                }
                c.set(row * n + column, sum);
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // 2. Shared-memory tiles, staged by hand
    // -------------------------------------------------------------------------------------

    public static void sharedMemoryTiles(KernelContext ctx, HalfFloatArray a, HalfFloatArray b, FloatArray c, int n) {
        int localRow = ctx.localIdx;
        int localColumn = ctx.localIdy;
        int row = ctx.groupIdx * TILE + localRow;
        int column = ctx.groupIdy * TILE + localColumn;

        float[] aTile = ctx.allocateFloatLocalArray(TILE * TILE);
        float[] bTile = ctx.allocateFloatLocalArray(TILE * TILE);

        float sum = 0.0f;
        for (int step = 0; step < n / TILE; step++) {
            aTile[localRow * TILE + localColumn] = a.get(row * n + step * TILE + localColumn).getFloat32();
            bTile[localRow * TILE + localColumn] = b.get((step * TILE + localRow) * n + column).getFloat32();
            ctx.localBarrier();
            for (int inner = 0; inner < TILE; inner++) {
                sum += aTile[localRow * TILE + inner] * bTile[inner * TILE + localColumn];
            }
            ctx.localBarrier();
        }
        c.set(row * n + column, sum);
    }

    // -------------------------------------------------------------------------------------
    // 3. Tiles as the unit of work
    // -------------------------------------------------------------------------------------

    /**
     * The whole kernel. No thread index, no shared memory, no barrier, and no instruction
     * shape: the tile compiler selects the tensor-core instruction and the layouts.
     */
    public static void tiles(TileContext tc, HalfFloatArray a, HalfFloatArray b, FloatArray c, int m, int n, int k) {
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

    // -------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 512;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        if (n % TILE != 0) {
            System.out.printf("n must be a multiple of %d%n", TILE);
            return;
        }
        TileExamples.requireCuda();

        HalfFloatArray a = new HalfFloatArray(n * n);
        HalfFloatArray b = new HalfFloatArray(n * n);
        Random random = new Random(7);
        for (int i = 0; i < n * n; i++) {
            a.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            b.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        System.out.printf("Matrix multiply, %d x %d, FP16 inputs with FP32 accumulation, %d iterations%n%n", n, n, iterations);

        // Sequential Java over the same FP16 values: the correctness reference, and the
        // baseline every speedup below is measured against.
        float[] expected = new float[n * n];
        long start = System.nanoTime();
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                float sum = 0.0f;
                for (int inner = 0; inner < n; inner++) {
                    sum += a.get(row * n + inner).getFloat32() * b.get(inner * n + column).getFloat32();
                }
                expected[row * n + column] = sum;
            }
        }
        double sequential = (System.nanoTime() - start) / 1e6;

        FloatArray threadOut = new FloatArray(n * n);
        FloatArray sharedOut = new FloatArray(n * n);
        FloatArray tileOut = new FloatArray(n * n);

        WorkerGrid2D threadWorker = new WorkerGrid2D(n, n);
        double threadTime = TileExamples.time(new TaskGraph("perElement") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("k", TileMatrixMultiply::threadPerElement, a, b, threadOut, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, threadOut), //
                new GridScheduler("perElement.k", threadWorker), iterations);

        WorkerGrid2D sharedWorker = new WorkerGrid2D(n, n);
        sharedWorker.setLocalWork(TILE, TILE, 1);
        double sharedTime = TileExamples.time(new TaskGraph("shared") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("k", TileMatrixMultiply::sharedMemoryTiles, new KernelContext(), a, b, sharedOut, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sharedOut), //
                new GridScheduler("shared.k", sharedWorker), iterations);

        // The tile worker grid counts TILE BLOCKS, not threads.
        WorkerGrid2D tileWorker = new WorkerGrid2D(n / TILE, n / TILE);
        double tileTime = TileExamples.time(new TaskGraph("tile") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("k", TileMatrixMultiply::tiles, new TileContext(), a, b, tileOut, n, n, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, tileOut), //
                new GridScheduler("tile.k", tileWorker), iterations);

        double gigaFlop = 2.0 * n * n * n / 1e9;
        double tolerance = 0.02 * Math.sqrt(n);
        TileExamples.header();
        TileExamples.report("sequential Java (reference)", sequential, sequential, gigaFlop, 0.0, tolerance);
        TileExamples.report("@Parallel, one thread per element", threadTime, sequential, gigaFlop, TileExamples.maxError(threadOut, expected), tolerance);
        TileExamples.report("KernelContext, shared-memory tiles", sharedTime, sequential, gigaFlop, TileExamples.maxError(sharedOut, expected), tolerance);
        TileExamples.report("TileContext, ct::mma", tileTime, sequential, gigaFlop, TileExamples.maxError(tileOut, expected), tolerance);
        TileExamples.footer();
    }
}
