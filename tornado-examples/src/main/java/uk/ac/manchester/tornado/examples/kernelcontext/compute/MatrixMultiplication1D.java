/*
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
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

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * $ tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.MatrixMultiplication1D
 * </code>
 */
public class MatrixMultiplication1D {
    // CHECKSTYLE:OFF

    private static final int WARMING_UP_ITERATIONS = 15;

    public static void matrixMultiplication(KernelContext context, final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        int idx = context.globalIdx;

        for (int jdx = 0; jdx < size; jdx++) {
            float sum = 0;
            for (int k = 0; k < size; k++) {
                sum += A.get((idx * size) + k) * B.get((k * size) + jdx);
            }
            C.set((idx * size) + jdx, sum);
        }
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

        Random r = new Random();
        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA.set(idx, r.nextFloat());
            matrixB.set(idx, r.nextFloat());
        });

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        KernelContext context = new KernelContext();
        // [Optional] Set the global work size
        workerGrid.setGlobalWork(size, 1, 1);
        // [Optional] Set the local work group
        workerGrid.setLocalWork(((size <= 1024) ? size : size / 2), 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMultiplication1D::matrixMultiplication, context, matrixA, matrixB, matrixC, size) //
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
        System.out.println("\tTornadoVM Execution: " + formatTornadoVMGFlops + " GFlops, Total Time = " + (end - start) + " ms");
        System.out.println("\tSpeedup: " + speedup + "x");
        System.out.println("\tVerification " + verify(matrixC, resultSeq, size));
    }

    private static boolean verify(FloatArray par, FloatArray seq, int size) {
        boolean check = true;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {

                if (Math.abs(par.get(i * size + j) - seq.get(i * size + j)) > 0.1f) {
                    check = false;
                    break;
                }
            }
        }
        return check;
    }
}
// CHECKSTYLE:ON
