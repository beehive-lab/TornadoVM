/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * $ tornado --threadInfo --jvm="-Ds0.t0.device=0:0" -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D
 * </code>
 */

public class MatrixMultiplication2D {
    // CHECKSTYLE:OFF
    private static final int WARMING_UP_ITERATIONS = 15;

    private static void matrixMultiplication(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            }
        }
    }

    private static void parallelStreamsMxM(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        IntStream.range(0, size).parallel().forEach(i -> {
            IntStream.range(0, size).parallel().forEach(j -> {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            });
        });
    }

    public static void main(String[] args) {

        int size = 512;
        if (args.length >= 1) {
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                size = 512;
            }
        }

        System.out.println("Computing MxM of " + size + "x" + size);

        Matrix2DFloat matrixA = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixB = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixC = new Matrix2DFloat(size, size);
        Matrix2DFloat resultSeq = new Matrix2DFloat(size, size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA.set(i, j, r.nextFloat());
                matrixB.set(i, j, r.nextFloat());
            }
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMultiplication2D::matrixMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withWarmUp();

        // 1. Warm up Tornado
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            executor.execute();
        }

        // 2. Run parallel on the GPU with Tornado
        long start = System.nanoTime();
        executor.execute();
        long end = System.nanoTime();

        // Run sequential
        // 1. Warm up sequential
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixMultiplication(matrixA, matrixB, resultSeq, size);
        }

        // 2. Run the sequential code
        long startSequential = System.nanoTime();
        matrixMultiplication(matrixA, matrixB, resultSeq, size);
        long endSequential = System.nanoTime();

        // Run multithread
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            parallelStreamsMxM(matrixA, matrixB, resultSeq, size);
        }

        // 2. Run the sequential code
        long startStream = System.nanoTime();
        parallelStreamsMxM(matrixA, matrixB, resultSeq, size);
        long endStream = System.nanoTime();

        // Compute Gigaflops and performance
        long nanoSecGPUElapsedTime = (end - start);
        long nanoSecCPUElaptedTime = (endSequential - startSequential);
        long nanoSecStreamElaptedTime = (endStream - startStream);

        double flops = 2 * Math.pow(size, 3);
        double gpuGigaFlops = (1.0E-9 * flops) / (nanoSecGPUElapsedTime / 1000000000.0f);
        double cpuGigaFlops = (1.0E-9 * flops) / (nanoSecCPUElaptedTime / 1000000000.0f);
        double streamGigaFlops = (1.0E-9 * flops) / (nanoSecStreamElaptedTime / 1000000000.0f);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatGPUFGlops = String.format("%.2f", gpuGigaFlops);
        String formatCPUFGlops = String.format("%.2f", cpuGigaFlops);
        String formatStreamFGlops = String.format("%.2f", streamGigaFlops);

        TornadoDeviceType deviceType = executor.getDevice(0).getDeviceType();
        System.out.println("\tSingle Threaded CPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ns");
        System.out.println("\tStreams Execution: " + formatStreamFGlops + " GFlops, Total time = " + (nanoSecStreamElaptedTime) + " ns");
        System.out.println("\tTornadoVM Execution on " + deviceType + " (Accelerated): " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ns");
        System.out.println("\tSpeedup: " + speedup + "x");
        System.out.println("\tVerification " + verify(matrixC, resultSeq, size));
    }

    private static boolean verify(Matrix2DFloat par, Matrix2DFloat seq, int size) {
        boolean check = true;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(par.get(i, j) - seq.get(i, j)) > 0.1f) {
                    check = false;
                    break;
                }
            }
        }
        return check;
    }
}
// CHECKSTYLE:OFF
