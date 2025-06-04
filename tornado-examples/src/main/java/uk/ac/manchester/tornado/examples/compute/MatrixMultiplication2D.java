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

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
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
    private static final int WARMING_UP_ITERATIONS = 100;

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

    public static void main(String[] args) throws TornadoExecutionPlanException, FileNotFoundException {

        int size = 512;
        if (args.length >= 1) {
            size = Integer.parseInt(args[0]);
        }

        boolean verify = true;
        if (args.length >= 2) {
            verify = Boolean.parseBoolean(args[1]);
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

        ArrayList<Long> tornadoElapsedTime = new ArrayList<>();
        ArrayList<Long> javaElapsedTime = new ArrayList<>();
        ArrayList<Long> streamsElapsedTime = new ArrayList<>();

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        long start;
        long end;
        TornadoDeviceType deviceType;
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.withPreCompilation();

            // 1. Warm up Tornado
            for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
                long s = System.nanoTime();
                executor.execute();
                long e = System.nanoTime();
                tornadoElapsedTime.add(e - s);
            }

            // Run parallel on the GPU with Tornado
            start = System.nanoTime();
            executor.execute();
            end = System.nanoTime();
            tornadoElapsedTime.add(end - start);
            deviceType = executor.getDevice(0).getDeviceType();
        }

        // Run sequential
        // 2. Warm up sequential
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            long s = System.nanoTime();
            matrixMultiplication(matrixA, matrixB, resultSeq, size);
            long e = System.nanoTime();
            javaElapsedTime.add(e - s);
        }

        // Run the sequential code
        long startSequential = System.nanoTime();
        matrixMultiplication(matrixA, matrixB, resultSeq, size);
        long endSequential = System.nanoTime();
        javaElapsedTime.add(endSequential - startSequential);

        // Multithreaded version
        // 3. Multithreaded version warmup
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            long s = System.nanoTime();
            parallelStreamsMxM(matrixA, matrixB, resultSeq, size);
            long e = System.nanoTime();
            streamsElapsedTime.add(e - s);
        }

        // Run multithreaded version
        long startStream = System.nanoTime();
        parallelStreamsMxM(matrixA, matrixB, resultSeq, size);
        long endStream = System.nanoTime();
        streamsElapsedTime.add(endStream - startStream);

        // Compute Gigaflops and performance
        long nanoSecGPUElapsedTime = (end - start);
        long nanoSecCPUElaptedTime = (endSequential - startSequential);
        long nanoSecStreamElaptedTime = (endStream - startStream);

        double flops = 2 * Math.pow(size, 3);
        final float timeScaleSec = 1000000000.0f;
        double gpuGigaFlops = (1.0E-9 * flops) / (nanoSecGPUElapsedTime / timeScaleSec);
        double cpuGigaFlops = (1.0E-9 * flops) / (nanoSecCPUElaptedTime / timeScaleSec);
        double streamGigaFlops = (1.0E-9 * flops) / (nanoSecStreamElaptedTime / timeScaleSec);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatGPUFGlops = String.format("%.2f", gpuGigaFlops);
        String formatCPUFGlops = String.format("%.2f", cpuGigaFlops);
        String formatStreamFGlops = String.format("%.2f", streamGigaFlops);

        System.out.println("\tSingle Threaded CPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ns");
        System.out.println("\tStreams Execution: " + formatStreamFGlops + " GFlops, Total time = " + (nanoSecStreamElaptedTime) + " ns");
        System.out.println("\tTornadoVM Execution on " + deviceType + " (Accelerated): " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ns");
        System.out.println("\tSpeedup: " + speedup + "x");
        if (verify) {
            System.out.println("\tVerification " + verify(matrixC, resultSeq, size));
        }

        // Store the CSV Table for all metrics
        PrintWriter fileWriter = new PrintWriter("stats-mxm-" + size + ".txt");
        fileWriter.println("Java, Stream, TornadoVM");
        for (int i = 0; i < javaElapsedTime.size(); i++) {
            fileWriter.println(javaElapsedTime.get(i) + "," + streamsElapsedTime.get(i) + "," + tornadoElapsedTime.get(i));
        }
        fileWriter.close();

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
