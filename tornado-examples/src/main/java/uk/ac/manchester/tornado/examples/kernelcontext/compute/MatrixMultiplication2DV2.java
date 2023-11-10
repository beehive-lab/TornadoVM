/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.kernelcontext.compute;

import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * Example of Matrix Multiplication for square matrices written in Java. This
 * implementation follows the OpenCL implementation description provided in
 * {@url https://github.com/cnugteren/myGEMM}.
 *
 * <p>
 * In detail, it applies the following optimizations: (i) Thread attributes to
 * utilize two dimensions, and (ii) Local memory & Loop tiling.
 * </p>
 *
 * <p>
 * How to run:
 * </p>
 * <code>
 * $ tornado --debug uk.ac.manchester.tornado.examples.kernelcontext.compute.MatrixMultiplication2DV2
 * </code>
 */
public class MatrixMultiplication2DV2 {
    // CHECKSTYLE:OFF
    private static final int WARMING_UP_ITERATIONS = 15;
    private static final int TS = 32;

    public static void matrixMultiplication(KernelContext context, final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        int row = context.localIdx;
        int col = context.localIdy;
        int globalRow = TS * context.groupIdx + row;
        int globalCol = TS * context.groupIdy + col;

        float[] aSub = context.allocateFloatLocalArray(TS * TS);
        float[] bSub = context.allocateFloatLocalArray(TS * TS);

        float sum = 0;

        // Loop over all tiles
        int numTiles = size / TS;
        for (int t = 0; t < numTiles; t++) {

            // Load one tile of A and B into local memory
            int tiledRow = TS * t + row;
            int tiledCol = TS * t + col;
            aSub[col * TS + row] = A.get(tiledCol * size + globalRow);
            bSub[col * TS + row] = B.get(globalCol * size + tiledRow);

            // Synchronise to make sure the tile is loaded
            context.localBarrier();

            // Perform the computation for a single tile
            for (int k = 0; k < TS; k++) {
                sum += aSub[k * TS + row] * bSub[col * TS + k];
            }
            // Synchronise before loading the next tile
            context.globalBarrier();
        }

        // Store the final result in C
        C.set((globalCol * size) + globalRow, sum);
    }

    private static void matrixMultiplication(final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get((i * size) + k) * B.get((k * size) + j);
                }
                C.set((i * size) + j, sum);
            }
        }
    }

    public static void main(String[] args) {

        int size = 512;
        if (args.length >= 1) {
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        System.out.println("Computing MxM of " + size + "x" + size);

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray matrixC = new FloatArray(size * size);
        FloatArray resultSeq = new FloatArray(size * size);

        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA.set(idx, 2.5f);
            matrixB.set(idx, 3.5f);
        });

        WorkerGrid workerGrid = new WorkerGrid2D(size, size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        KernelContext context = new KernelContext();
        // The local work group is configured to be TSxTS, to match the Tile Size (TS)
        workerGrid.setLocalWork(TS, TS, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMultiplication2DV2::matrixMultiplication, context, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withGridScheduler(gridScheduler);

        // 1. Warm up Tornado
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            executor.execute();
        }

        // 2. Run parallel on the GPU with Tornado
        long start = System.currentTimeMillis();
        executor.execute();
        long end = System.currentTimeMillis();

        // Run sequential
        // 1. Warm up sequential
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixMultiplication(matrixA, matrixB, resultSeq, size);
        }

        // 2. Run the sequential code
        long startSequential = System.currentTimeMillis();
        matrixMultiplication(matrixA, matrixB, resultSeq, size);
        long endSequential = System.currentTimeMillis();

        // Compute GigaFlops and performance
        long msecTornadoVMElapsedTime = (end - start);
        long msecSequentialElaptedTime = (endSequential - startSequential);
        double flops = 2 * Math.pow(size, 3);
        double tornadoVMGigaFlops = (1.0E-9 * flops) / (msecTornadoVMElapsedTime / 1000.0f);
        double sequentialGigaFlops = (1.0E-9 * flops) / (msecSequentialElaptedTime / 1000.0f);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatTornadoVMGFlops = String.format("%.2f", tornadoVMGigaFlops);
        String formatSequentialGFlops = String.format("%.2f", sequentialGigaFlops);

        System.out.println("\tSequential Execution: " + formatSequentialGFlops + " GFlops, Total time = " + (endSequential - startSequential) + " ms");
        System.out.println("\tTornadoVM Execution with Local Memory and Loop Tiling: " + formatTornadoVMGFlops + " GFlops, Total Time = " + (end - start) + " ms");
        System.out.println("\tSpeedup: " + speedup + "x");
        System.out.println("\tVerification " + verify(matrixC, resultSeq, size));
    }

    private static boolean verify(FloatArray par, FloatArray seq, int size) {
        boolean check = true;
        for (int i = 0; i < size * size; i++) {
            if (Math.abs(par.get(i) - seq.get(i)) > 0.01f) {
                check = false;
                break;
            }
        }
        return check;
    }
}
// CHECKSTYLE:ON
