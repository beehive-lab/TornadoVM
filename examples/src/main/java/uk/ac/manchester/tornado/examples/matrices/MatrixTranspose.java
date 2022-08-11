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

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class MatrixTranspose {

    private static final int WARMING_UP_ITERATIONS = 15;

    private static void matrixTranspose(final float[] A, float[] B, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                B[(i * size) + j] = A[(j * size) + i];
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

        System.out.println("Computing MatrixTranspose of " + size + " elements");

        float[] matrixA = new float[size * size];
        float[] matrixB = new float[size * size];

        float[] resultSeq = new float[size * size];

        Random r = new Random();
        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        Arrays.fill(matrixB, 0f);

        //@formatter:off
        TaskGraph t = new TaskGraph("s0")
                .task("t0", MatrixTranspose::matrixTranspose, matrixA, matrixB, size)
                .streamOut(matrixB);
        //@formatter:on

        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            t.execute();
        }

        long start = System.currentTimeMillis();
        t.execute();
        long end = System.currentTimeMillis();

        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixTranspose(matrixA, resultSeq, size);
        }

        long startSequential = System.currentTimeMillis();
        matrixTranspose(matrixA, resultSeq, size);
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
        System.out.println("\tVerification " + verify(matrixB, resultSeq, size));
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
