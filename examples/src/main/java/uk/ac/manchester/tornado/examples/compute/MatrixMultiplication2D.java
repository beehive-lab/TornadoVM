/*
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;

import java.util.Random;

public class MatrixMultiplication2D {

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

        WorkerGrid workerGrid = new WorkerGrid2D(size, size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        // [Optional] Set the global work size
        workerGrid.setGlobalWork(size, size, 1);
        // [Optional] Set the local work group to be 32x32
        workerGrid.setLocalWork(32, 32, 1);

        //@formatter:off
        TaskSchedule t = new TaskSchedule("s0")
                .task("t0", MatrixMultiplication2D::matrixMultiplication, matrixA, matrixB, matrixC, size)
                .streamOut(matrixC);
        //@formatter:on

        // 1. Warm up Tornado
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            t.execute(gridScheduler);
        }

        // 2. Run parallel on the GPU with Tornado
        long start = System.currentTimeMillis();
        t.execute(gridScheduler);
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

        TornadoDeviceType deviceType = t.getDevice().getDeviceType();
        System.out.println("\tSingle Threaded CPU Execution: " + formatCPUFGlops + " GFlops, Total time = " + (endSequential - startSequential) + " ms");
        System.out.println("\tTornadoVM Execution on " + deviceType + " (Accelerated): " + formatGPUFGlops + " GFlops, Total Time = " + (end - start) + " ms");
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
