/*
 * Copyright (c) 2013-2021, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D
 * </code>
 */
public class MatrixMultiplication1D {

    private static final int WARMING_UP_ITERATIONS = 15;

    private static void matrixMultiplication(final float[] matrixA, final float[] matrixB, final float[] result, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += matrixA[(i * size) + k] * matrixB[(k * size) + j];
                }
                result[(i * size) + j] = sum;
            }
        }
    }

    public static void main(String[] args) throws NumberFormatException {

        int size = 512;
        if (args.length >= 1) {
            size = Integer.parseInt(args[0]);
        }

        System.out.println("Computing MxM of " + size + "x" + size);

        float[] matrixA = new float[size * size];
        float[] matrixB = new float[size * size];
        float[] matrixC = new float[size * size];
        float[] resultSeq = new float[size * size];

        Random r = new Random();
        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .lockObjectsInMemory(matrixA, matrixB, matrixC) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMultiplication1D::matrixMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(matrixC); //

        // 1. Warm up Tornado
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            taskGraph.execute();
        }

        // 2. Run parallel on the GPU with Tornado
        long start = System.currentTimeMillis();
        taskGraph.execute();
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

        // Compute Gigaflops and performance
        long msecGPUElapsedTime = (end - start);
        long msecCPUElaptedTime = (endSequential - startSequential);
        double flops = 2 * Math.pow(size, 3);
        double gpuGigaFlops = (1.0E-9 * flops) / (msecGPUElapsedTime / 1000.0f);
        double cpuGigaFlops = (1.0E-9 * flops) / (msecCPUElaptedTime / 1000.0f);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatGPUFGlops = String.format("%.2f", gpuGigaFlops);
        String formatCPUFGlops = String.format("%.2f", cpuGigaFlops);

        TornadoDeviceType deviceType = taskGraph.getDevice().getDeviceType();

        // @formatter:off
        String buffer = "\tSingle Threaded CPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ms" +
                "\n\tTornadoVM Execution on " + deviceType + " (Accelerated): " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ms" +
                "\n\tSpeedup: " + speedup + "x" +
                "\n\tVerification " + verify(matrixC, resultSeq, size) + "\n";
        // @formatter:on

        System.out.println(buffer);
    }

    private static boolean verify(float[] par, float[] seq, int size) {
        boolean check = true;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {

                if (Math.abs(par[i * size + j] - seq[i * size + j]) > 0.1f) {
                    check = false;
                    break;
                }
            }
        }
        return check;
    }
}