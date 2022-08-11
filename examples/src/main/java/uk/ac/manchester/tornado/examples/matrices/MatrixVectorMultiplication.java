/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.matrices;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class MatrixVectorMultiplication {

    private static final int WARMING_UP_ITERATIONS = 15;

    private static void matrixVectorMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float sum = 0.0f;
            for (int j = 0; j < size; j++) {
                sum += A[(i * size) + j] * B[j];
            }
            C[i] = sum;
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

        System.out.println("Computing Matrix Vector Multiplication of " + size + "x" + size);

        float[] matrixA = new float[size * size];
        float[] matrixB = new float[size * size];
        float[] matrixC = new float[size * size];
        float[] resultSeq = new float[size * size];

        Random r = new Random();
        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        IntStream.range(0, size).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });

        //@formatter:off
        TaskGraph t = new TaskGraph("s0")
                .task("t0", MatrixVectorMultiplication::matrixVectorMultiplication, matrixA, matrixB, matrixC, size)
                .streamOut(matrixC);
        //@formatter:on

        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            t.execute();
        }

        long start = System.currentTimeMillis();
        t.execute();
        long end = System.currentTimeMillis();

        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixVectorMultiplication(matrixA, matrixB, resultSeq, size);
        }

        long startSequential = System.currentTimeMillis();
        matrixVectorMultiplication(matrixA, matrixB, resultSeq, size);
        long endSequential = System.currentTimeMillis();

        long msecGPUElapsedTime = (end - start);
        long msecCPUElaptedTime = (endSequential - startSequential);
        double flops = 2 * Math.pow(size, 3);

        double gpuGigaFlops = (1.0E-9 * flops) / (msecGPUElapsedTime / 1000.0f);
        double cpuGigaFlops = (1.0E-9 * flops) / (msecCPUElaptedTime / 1000.0f);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatGPUFGlops = String.format("%.2f", gpuGigaFlops);
        String formatCPUFGlops = String.format("%.2f", cpuGigaFlops);

        System.out.println("\tCPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ms");
        System.out.println("\tGPU Execution: " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ms");
        System.out.println("\tSpeedup: " + speedup + "x");
        System.out.println("\tVerification " + verify(matrixC, resultSeq, size));
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
