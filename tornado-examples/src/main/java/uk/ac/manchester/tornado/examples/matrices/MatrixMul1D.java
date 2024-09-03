/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.matrices;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.matrices.MatrixMul1D
 * </code>
 *
 */
public class MatrixMul1D {
    // CHECKSTYLE:OFF
    public static final int WARMUP_ITERATIONS = 15;
    public static final int EXECUTE_ITERATIONS = 100;

    private static void matrixMultiplication(final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get((i * size) + k) * B.get((k * size) + j);
                }
                C.set((i * size) + j, sum);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int N = 512;
        if (args.length == 1) {
            N = Integer.parseInt(args[0]);
        }

        FloatArray matrixA = new FloatArray(N * N);
        FloatArray matrixB = new FloatArray(N * N);
        FloatArray matrixCSeq = new FloatArray(N * N);
        FloatArray matrixCCUDA = new FloatArray(N * N);
        FloatArray matrixCOCL = new FloatArray(N * N);

        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA.set(idx, 2.5f);
            matrixB.set(idx, 3.5f);
        });

        TaskGraph cudaTaskGraph = new TaskGraph("cuda_old_api") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixCCUDA, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixCCUDA);

        TornadoBackend cudaDriver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0);
        TornadoDevice cudaDevice = cudaDriver.getDevice(0);

        ImmutableTaskGraph immutableTaskGraph = cudaTaskGraph.snapshot();
        TornadoExecutionPlan executorCUDA = new TornadoExecutionPlan(immutableTaskGraph);
        executorCUDA.withDevice(cudaDevice);

        // Warm up CUDA
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executorCUDA.execute();
        }

        // Time CUDA
        long start, stop;
        long[] execTimesCUDA = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesCUDA.length; i++) {
            start = System.currentTimeMillis();
            executorCUDA.execute();
            stop = System.currentTimeMillis();
            execTimesCUDA[i] = stop - start;
        }

        OptionalDouble avgCudaOptional = Arrays.stream(execTimesCUDA).average();
        double averageCUDA;
        if (avgCudaOptional.isPresent()) {
            averageCUDA = avgCudaOptional.getAsDouble();
        } else {
            throw new Exception("Could not get average execution time");
        }

        TaskGraph oclTaskGraph = new TaskGraph("ocl_old_api") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixMul1D::matrixMultiplication, matrixA, matrixB, matrixCOCL, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixCOCL);

        // Get the same device but running the OCL backend
        TornadoBackend oclDriver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(1);
        TornadoDevice oclDevice = null;
        for (int i = 0; i < oclDriver.getNumDevices(); i++) {
            TornadoDevice device = oclDriver.getDevice(i);
            if (device.getPhysicalDevice().getDeviceName().equalsIgnoreCase(cudaDevice.getPhysicalDevice().getDeviceName())) {
                oclDevice = device;
            }
        }
        if (oclDevice == null) {
            System.err.println("There is no device with both OpenCL and CUDA-PTX support");
            System.exit(1);
        }

        ImmutableTaskGraph immutableTaskGraph1 = oclTaskGraph.snapshot();
        TornadoExecutionPlan executorOCL = new TornadoExecutionPlan(immutableTaskGraph1);
        executorOCL.withDevice(oclDevice);

        // Warm up OpenCL
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executorOCL.execute();
        }

        // Time OpenCL
        long[] execTimesOCL = new long[EXECUTE_ITERATIONS];
        for (int i = 0; i < execTimesOCL.length; i++) {
            start = System.currentTimeMillis();
            executorOCL.execute();
            stop = System.currentTimeMillis();
            execTimesOCL[i] = stop - start;
        }

        OptionalDouble avgOpenCLOptional = Arrays.stream(execTimesOCL).average();
        double averageOpenCL;
        if (avgOpenCLOptional.isPresent())
            averageOpenCL = avgOpenCLOptional.getAsDouble();
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

        // Compute Gigaflops and performance
        double flops = 2 * Math.pow(N, 3);
        double CUDAGigaFlops = (1.0E-9 * flops) / (averageCUDA / 1000.0f);
        double OpenCLGigaFlops = (1.0E-9 * flops) / (averageOpenCL / 1000.0f);
        double CUDAspeedup = averageSeq / averageCUDA;
        double OpenCLspeedup = averageSeq / averageOpenCL;

        String formatCUDAFGlops = String.format("%.2f", CUDAGigaFlops);
        String formatOpenCLFGlops = String.format("%.2f", OpenCLGigaFlops);

        System.out.println("\tOpenCL Execution: " + formatOpenCLFGlops + " GFlops, Total time = " + averageOpenCL + " ms");
        System.out.println("\tPTX Execution: " + formatCUDAFGlops + " GFlops, Total Time = " + averageCUDA + " ms");
        System.out.println("\tOpenCL Speedup: " + OpenCLspeedup + "x");
        System.out.println("\tPTX Speedup: " + CUDAspeedup + "x");
        System.out.println();

    }
}
// CHECKSTYLE:ON
