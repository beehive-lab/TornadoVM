/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat4;

/**
 * Full example to show to matrix addition with non vector types
 *
 */
public class MatrixAddition2DVector {

    public static final int WARMING_UP_ITERATIONS = 15;

    public static void matrixAddition(Matrix2DFloat4 A, Matrix2DFloat4 B, Matrix2DFloat4 C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                C.set(i, j, Float4.add(A.get(i, j), B.get(j, j)));
            }
        }
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

        System.out.println("Computing Matrix Addition of " + size + "x" + size);

        size /= 2;

        Matrix2DFloat4 matrixA = new Matrix2DFloat4(size, size);
        Matrix2DFloat4 matrixB = new Matrix2DFloat4(size, size);
        Matrix2DFloat4 matrixC = new Matrix2DFloat4(size, size);
        Matrix2DFloat4 resultSeq = new Matrix2DFloat4(size, size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA.set(i, j, new Float4(new float[] { r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() }));
                matrixB.set(i, j, new Float4(new float[] { r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() }));
            }
        }

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", MatrixAddition2DVector::matrixAddition, matrixA, matrixB, matrixC, size)
                .streamOut(matrixC);
        //@formatter:on

        // 1. Warm up Tornado
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            t.execute();
        }

        // 2. Run parallel on the GPU with Tornado
        long start = System.currentTimeMillis();
        t.execute();
        long end = System.currentTimeMillis();
        System.out.println("Parallel: " + (end - start) + " (ms)");

        // Run sequential
        // 1. Warm up sequential
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixAddition(matrixA, matrixB, resultSeq, size);
        }

        // 2. Run the sequential code
        long startSequential = System.currentTimeMillis();
        matrixAddition(matrixA, matrixB, resultSeq, size);
        long endSequential = System.currentTimeMillis();
        System.out.println("Sequential: " + (endSequential - startSequential) + " (ms)");

        // Compute Gigaflops and performance
        long msecGPUElapsedTime = (end - start);
        long msecCPUElaptedTime = (endSequential - startSequential);
        double flops = 2 * Math.pow(size, 2);
        double gpuGigaFlops = (1.0E-9 * flops) / (msecGPUElapsedTime / 1000.0f);
        double cpuGigaFlops = (1.0E-9 * flops) / (msecCPUElaptedTime / 1000.0f);
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);

        String formatGPUFGlops = String.format("%.2f", gpuGigaFlops);
        String formatCPUFGlops = String.format("%.2f", cpuGigaFlops);

        System.out.println("\tCPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ms");
        System.out.println("\tGPU Execution: " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ms");
        System.out.println("\tSpeedup: " + speedup + "x");
    }

}
