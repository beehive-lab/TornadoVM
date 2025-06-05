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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D
 * </code>
 */
public class MatrixMultiplication1D {

    private static final int WARMING_UP_ITERATIONS = 15;

    private static void matrixMultiplication(final FloatArray matrixA, final FloatArray matrixB, final FloatArray result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += matrixA.get((i * size) + k) * matrixB.get((k * size) + j);
                }
                result.set((i * size) + j, sum);
            }
        }
    }

    public static void main(String[] args) throws NumberFormatException {

        int size = 512;
        if (args.length >= 1) {
            size = Integer.parseInt(args[0]);
        }

        System.out.println("Computing MxM of " + size + "x" + size);

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray matrixC = new FloatArray(size * size);
        FloatArray resultSeq = new FloatArray(size * size);

        Random r = new Random();
        IntStream.range(0, size * size).forEach(idx -> {
            matrixA.set(idx, r.nextFloat());
            matrixB.set(idx, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMultiplication1D::matrixMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withPreCompilation();

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

        // Compute GigaFLOPS and performance
        long msecGPUElapsedTime = (end - start);
        long msecCPUElaptedTime = (endSequential - startSequential);
        double flops = 2 * Math.pow(size, 3);
        double gpuGigaFlops = (1.0E-9 * flops) / (msecGPUElapsedTime / 1000.0f);
        double cpuGigaFlops = (1.0E-9 * flops) / (msecCPUElaptedTime / 1000.0f);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatGPUFGlops = String.format("%.2f", gpuGigaFlops);
        String formatCPUFGlops = String.format("%.2f", cpuGigaFlops);

        TornadoDeviceType deviceType = executor.getDevice(0).getDeviceType();

        // @formatter:off
        String buffer = "\tSingle Threaded CPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ms" +
                "\n\tTornadoVM Execution on " + deviceType + " (Accelerated): " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ms" +
                "\n\tSpeedup: " + speedup + "x" +
                "\n\tVerification " + verify(matrixC, resultSeq, size) + "\n";
        // @formatter:on

        System.out.println(buffer);
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
