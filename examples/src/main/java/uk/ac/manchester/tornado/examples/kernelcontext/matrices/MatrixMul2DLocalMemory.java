/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.kernelcontext.matrices;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

/**
 * Example of Matrix Multiplication of two dimensional arrays using Local Memory
 * and Loop Tiling.
 *
 * This program requires both OpenCL and PTX backends to be built. It compares
 * the following implementations against the Java functionally equivalent code.
 * 
 * a) CUDA/OpenCL Old API: using the TaskSchedule API and the @Parallel
 * annotations to express loop parallelism.
 * 
 * b) CUDA/OpenCL Advanced API: using a {@link KernelContext} to express
 * Threading Attributes, Local Memory Allocation and Loop Tiling.
 *
 * How to run:
 *
 * <code>
 *     $ make BACKEND=opencl,ptx
 *     $ tornado --debug uk.ac.manchester.tornado.examples.kernelcontext.matrices.MatrixMul2DLocalMemory
 * </code>
 *
 */
public class MatrixMul2DLocalMemory {

    public static final int WARMUP_ITERATIONS = 15;
    public static final int EXECUTE_ITERATIONS = 15;
    private static final boolean CHECK_RESULT = true;
    private static final float DELTA = 0.01f;
    public static final int TS = 32;

    private static void matrixMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A[(i * size) + k] * B[(k * size) + j];
                }
                C[(i * size) + j] = sum;
            }
        }
    }

    /**
     * Example of Matrix Multiplication with Local Memory and Loop Tiling. The tiles
     * are flattened in one dimension.
     *
     * The algorithm of this method follows the implementation in:
     * https://github.com/cnugteren/myGEMM.
     *
     */
    public static void matrixMultiplicationLocalMemory(KernelContext context, final float[] A, final float[] B, final float[] C, final int size) {
        // Thread identifiers
        int row = context.localIdx; // Local row ID (max: TS)
        int col = context.localIdy; // Local col ID (max: TS)
        int globalRow = TS * context.groupIdx + row; // Row ID of C (0..M)
        int globalCol = TS * context.groupIdy + col; // Col ID of C (0..N)

        float[] aSub = context.allocateFloatLocalArray(TS * TS);
        float[] bSub = context.allocateFloatLocalArray(TS * TS);

        float sum = 0;

        // Loop over all tiles
        int numTiles = size / TS;
        for (int t = 0; t < numTiles; t++) {

            // Load one tile of A and B into local memory
            int tiledRow = TS * t + row;
            int tiledCol = TS * t + col;
            aSub[col * TS + row] = A[tiledCol * size + globalRow];
            bSub[col * TS + row] = B[globalCol * size + tiledRow];

            // Synchronise to make sure the tile is loaded
            context.localBarrier();

            // Perform the computation for a single tile
            for (int k = 0; k < TS; k++) {
                sum += aSub[k * TS + row] * bSub[col * TS + k];
            }
            // Synchronise before loading the next tile
            context.localBarrier();
        }

        // Store the final result in C
        C[(globalCol * size) + globalRow] = sum;
    }

    public static void main(String[] args) throws Exception {
        int N = 512;
        // The local work group is configured to be TSxTS, to match the Tile Size (TS)
        long local_x = TS;
        long local_y = TS;
        if (args.length == 1) {
            N = Integer.parseInt(args[0]);
        } else if (args.length == 3) {
            N = Integer.parseInt(args[0]);
            local_x = Long.parseLong(args[1]);
            local_y = Long.parseLong(args[2]);
        }

        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] matrixCSeq = new float[N * N];
        float[] matrixCCUDA = new float[N * N];
        float[] matrixCOCL = new float[N * N];
        float[] matrixCOCLNewApi = new float[N * N];
        float[] matrixCCUDANewApi = new float[N * N];

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = 2.5f;
            matrixB[idx] = 3.5f;
        });

        WorkerGrid workerCUDAOld = new WorkerGrid2D(N, N);
        GridScheduler gridSchedulerCUDAOld = new GridScheduler("cuda_old_api.t0", workerCUDAOld);
        TaskSchedule scheduleCUDA = new TaskSchedule("cuda_old_api").task("t0", MatrixMul2DLocalMemory::matrixMultiplication, matrixA, matrixB, matrixCCUDA, N).streamOut(matrixCCUDA);

        TornadoDriver cudaDriver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        TornadoDevice cudaDevice = cudaDriver.getDevice(0);
        workerCUDAOld.setGlobalWork(N, N, 1);
        workerCUDAOld.setLocalWork(local_x, local_y, 1);
        scheduleCUDA.mapAllTo(cudaDevice);

        // Warm up CUDA
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            scheduleCUDA.execute(gridSchedulerCUDAOld);
        }

        // Time CUDA
        long start,stop;
        long[] execTimesCUDA = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesCUDA.length; i++) {
            start = System.currentTimeMillis();
            scheduleCUDA.execute(gridSchedulerCUDAOld);
            stop = System.currentTimeMillis();
            execTimesCUDA[i] = stop - start;
        }

        OptionalDouble avgCudaOptional = Arrays.stream(execTimesCUDA).average();
        double averageCUDA;
        if (avgCudaOptional.isPresent())
            averageCUDA = avgCudaOptional.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        WorkerGrid workerOpenCLOld = new WorkerGrid2D(N, N);
        GridScheduler gridSchedulerOpenCLOld = new GridScheduler("ocl_old_api.t0", workerOpenCLOld);

        TaskSchedule scheduleOCL = new TaskSchedule("ocl_old_api").task("t0", MatrixMul2DLocalMemory::matrixMultiplication, matrixA, matrixB, matrixCOCL, N).streamOut(matrixCOCL);

        // Get the same device but running the OCL backend
        TornadoDriver oclDriver = TornadoRuntime.getTornadoRuntime().getDriver(1);
        TornadoDevice oclDevice = null;
        for (int i = 0; i < oclDriver.getDeviceCount(); i++) {
            TornadoDevice device = oclDriver.getDevice(i);
            if (device.getPhysicalDevice().getDeviceName().equalsIgnoreCase(cudaDevice.getPhysicalDevice().getDeviceName())) {
                oclDevice = device;
            }
        }
        if (oclDevice == null) {
            System.err.println("There is no device with both OpenCL and CUDA-PTX support");
            System.exit(1);
        }
        workerOpenCLOld.setGlobalWork(N, N, 1);
        workerOpenCLOld.setLocalWork(local_x, local_y, 1);
        scheduleOCL.mapAllTo(oclDevice);

        // Warm up OpenCL
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            scheduleOCL.execute(gridSchedulerOpenCLOld);
        }

        // Time OpenCL
        long[] execTimesOCL = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesOCL.length; i++) {
            start = System.currentTimeMillis();
            scheduleOCL.execute(gridSchedulerOpenCLOld);
            stop = System.currentTimeMillis();
            execTimesOCL[i] = stop - start;
        }

        OptionalDouble avgOpenCLOptional = Arrays.stream(execTimesOCL).average();
        double averageOpenCL;
        if (avgOpenCLOptional.isPresent())
            averageOpenCL = avgOpenCLOptional.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Time New API OpenCL
        WorkerGrid workerOpenCLNew = new WorkerGrid2D(N, N);
        GridScheduler gridSchedulerOpenCLNew = new GridScheduler("ocl_advanced_api.t0", workerOpenCLNew);
        KernelContext context = new KernelContext();

        TaskSchedule oclNewApiTask = new TaskSchedule("ocl_advanced_api") //
                .task("t0", MatrixMul2DLocalMemory::matrixMultiplicationLocalMemory, context, matrixA, matrixB, matrixCOCLNewApi, N) //
                .streamOut(matrixCOCLNewApi); //
        // Change the Grid
        workerOpenCLNew.setGlobalWork(N, N, 1); // TS / WPT
        workerOpenCLNew.setLocalWork(local_x, local_y, 1);
        oclNewApiTask.mapAllTo(oclDevice);

        // Warmup New Api OPENCL
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            oclNewApiTask.execute(gridSchedulerOpenCLNew);
        }

        // Time OPENCL
        long[] execTimesOCLNewApi = new long[EXECUTE_ITERATIONS];

        for (int i = 0; i < EXECUTE_ITERATIONS; i++) {
            start = System.currentTimeMillis();
            oclNewApiTask.execute(gridSchedulerOpenCLNew);
            stop = System.currentTimeMillis();
            execTimesOCLNewApi[i] = stop - start;
        }

        OptionalDouble avgOpenCLOptionalNewApi = Arrays.stream(execTimesOCLNewApi).average();
        double averageOpenCLNewApi;
        if (avgOpenCLOptionalNewApi.isPresent())
            averageOpenCLNewApi = avgOpenCLOptionalNewApi.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Time New API CUDA
        WorkerGrid workerCudaNew = new WorkerGrid2D(N, N);
        GridScheduler gridSchedulerCudaNew = new GridScheduler("cuda_advanced_api.t0", workerCudaNew);
        KernelContext contextCUDA = new KernelContext();

        TaskSchedule cudaNewApiTask = new TaskSchedule("cuda_advanced_api") //
                .task("t0", MatrixMul2DLocalMemory::matrixMultiplicationLocalMemory, contextCUDA, matrixA, matrixB, matrixCCUDANewApi, N) //
                .streamOut(matrixCCUDANewApi); //
        // Change the Grid
        workerCudaNew.setGlobalWork(N, N, 1);
        workerCudaNew.setLocalWork(local_x, local_y, 1);
        cudaNewApiTask.mapAllTo(cudaDevice);

        // Warmup New Api OPENCL
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            cudaNewApiTask.execute(gridSchedulerCudaNew);
        }

        // Time OPENCL
        long[] execTimesCUDANewApi = new long[EXECUTE_ITERATIONS];

        for (int i = 0; i < EXECUTE_ITERATIONS; i++) {
            start = System.currentTimeMillis();
            cudaNewApiTask.execute(gridSchedulerCudaNew);
            stop = System.currentTimeMillis();
            execTimesCUDANewApi[i] = stop - start;
        }

        OptionalDouble avgCUDAOptionalNewApi = Arrays.stream(execTimesCUDANewApi).average();
        double averageCUDANewApi;
        if (avgCUDAOptionalNewApi.isPresent())
            averageCUDANewApi = avgCUDAOptionalNewApi.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Warm up sequential
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            matrixMultiplication(matrixA, matrixB, matrixCSeq, N);
        }

        // Time sequential
        long[] execTimesSeq = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesSeq.length; i++) {
            start = System.currentTimeMillis();
            matrixMultiplication(matrixA, matrixB, matrixCSeq, N);
            stop = System.currentTimeMillis();
            execTimesSeq[i] = stop - start;
        }

        OptionalDouble avgSeqOptional = Arrays.stream(execTimesSeq).average();
        double averageSeq;
        if (avgSeqOptional.isPresent())
            averageSeq = avgSeqOptional.getAsDouble();
        else
            throw new Exception("Could not get average execution time");

        // Validate Results
        boolean correctResult = true;
        boolean validationCUDA = true;
        boolean validationOCL = true;
        boolean validationOCLNewApi = true;
        boolean validationCUDANewApi = true;

        if (CHECK_RESULT) {
            for (int i = 0; i < N * N; i++) {
                if (Math.abs(matrixCCUDA[i] - matrixCSeq[i]) > DELTA) {
                    validationCUDA = false;
                    System.out.println("CUDA validation failed");
                }
                if (Math.abs(matrixCOCL[i] - matrixCSeq[i]) > DELTA) {
                    validationOCL = false;
                    System.out.println("OpenCL validation failed");
                }
                if (Math.abs(matrixCOCLNewApi[i] - matrixCSeq[i]) > DELTA) {
                    validationOCLNewApi = false;
                    System.out.println("OpenCL new api validation failed");
                    System.out.println("Result is (" + matrixCOCLNewApi[i] + ") - while should be (" + matrixCSeq[i] + ")");
                }
                if (Math.abs(matrixCCUDANewApi[i] - matrixCSeq[i]) > DELTA) {
                    validationCUDANewApi = false;
                    System.out.println("CUDA new api validation failed");
                    System.out.println("Result is (" + matrixCCUDANewApi[i] + ") - while should be (" + matrixCSeq[i] + ")");
                }
                correctResult = validationCUDA && validationOCL && validationOCLNewApi && validationCUDANewApi;

                if (!correctResult) {
                    break;
                }
            }
        }

        if (correctResult) {
            System.out.println("[RESULT] correct");
        } else {
            System.out.println("[RESULT] wrong");
        }

        // Compute Gigaflops and performance
        double flops = 2 * Math.pow(N, 3);
        double CUDAGigaFlops = (1.0E-9 * flops) / (averageCUDA / 1000.0f);
        double OpenCLGigaFlops = (1.0E-9 * flops) / (averageOpenCL / 1000.0f);
        double OpenCLNewApiGigaFlops = (1.0E-9 * flops) / (averageOpenCLNewApi / 1000.0f);
        double CUDANewApiGigaFlops = (1.0E-9 * flops) / (averageCUDANewApi / 1000.0f);
        double CUDAspeedup = averageSeq / averageCUDA;
        double OpenCLspeedup = averageSeq / averageOpenCL;
        double OpenCLNewApispeedup = averageSeq / averageOpenCLNewApi;
        double CUDANewApispeedup = averageSeq / averageCUDANewApi;

        String formatCUDAFGlops = String.format("%.2f", CUDAGigaFlops);
        String formatOpenCLFGlops = String.format("%.2f", OpenCLGigaFlops);
        String formatOpenCLNewApiFGlops = String.format("%.2f", OpenCLNewApiGigaFlops);
        String formatCUDANewApiFGlops = String.format("%.2f", CUDANewApiGigaFlops);

        System.out.println("\tOpenCL Execution: " + formatOpenCLFGlops + " GFlops, Total time = " + averageOpenCL + " ms");
        System.out.println("\tOpenCL Execution with Local Memory and Loop Tiling: " + formatOpenCLNewApiFGlops + " GFlops, Total time = " + averageOpenCLNewApi + " ms");
        System.out.println("\tPTX Execution: " + formatCUDAFGlops + " GFlops, Total Time = " + averageCUDA + " ms");
        System.out.println("\tPTX Execution with Local Memory and Loop Tiling: " + formatCUDANewApiFGlops + " GFlops, Total time = " + averageCUDANewApi + " ms");
        System.out.println("\tOpenCL Speedup: " + OpenCLspeedup + "x");
        System.out.println("\tOpenCL Speedup with Local Memory and Loop Tiling: " + OpenCLNewApispeedup + "x");
        System.out.println("\tPTX Speedup: " + CUDAspeedup + "x");
        System.out.println("\tPTX Speedup with Local Memory and Loop Tiling: " + CUDANewApispeedup + "x");
        System.out.println();

    }
}
