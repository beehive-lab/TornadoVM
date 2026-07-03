/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.cublas.tests;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * Benchmark: FP32 matrix multiplication (C = A * B) with the TornadoVM
 * JIT-generated kernel (naive {@code @Parallel} and tiled local-memory
 * KernelContext versions) vs a cuBLAS SGEMM library task on the same device
 * buffers. Inputs are transferred once (FIRST_EXECUTION) and the output is
 * fetched UNDER_DEMAND after the timed loop, so the measured time is dominated
 * by the GEMM itself.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkSgemm [size] [iterations]
 * </code>
 */
public class BenchmarkSgemm {

    private static final int WARMUP_ITERATIONS = 20;

    /** Tile size for the local-memory KernelContext kernel (size must be a multiple). */
    private static final int TS = 32;

    public static void matrixMultiplication(FloatArray a, FloatArray b, FloatArray c, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += a.get(i * size + k) * b.get(k * size + j);
                }
                c.set(i * size + j, sum);
            }
        }
    }

    /**
     * Tiled matrix multiplication with local memory (row-major adaptation of
     * the myGEMM kernel used in the kernelcontext examples). Consecutive
     * localIdx threads access consecutive columns for coalesced loads/stores.
     */
    public static void matrixMultiplicationTiled(KernelContext context, FloatArray a, FloatArray b, FloatArray c, int size) {
        int localCol = context.localIdx;
        int localRow = context.localIdy;
        int globalCol = TS * context.groupIdx + localCol;
        int globalRow = TS * context.groupIdy + localRow;

        float[] aSub = context.allocateFloatLocalArray(TS * TS);
        float[] bSub = context.allocateFloatLocalArray(TS * TS);

        float sum = 0.0f;
        int numTiles = size / TS;
        for (int t = 0; t < numTiles; t++) {
            int tiledCol = TS * t + localCol;
            int tiledRow = TS * t + localRow;
            aSub[localRow * TS + localCol] = a.get(globalRow * size + tiledCol);
            bSub[localRow * TS + localCol] = b.get(tiledRow * size + globalCol);
            context.localBarrier();
            for (int k = 0; k < TS; k++) {
                sum += aSub[localRow * TS + k] * bSub[k * TS + localCol];
            }
            context.localBarrier();
        }
        c.set(globalRow * size + globalCol, sum);
    }

    private static double benchmark(TornadoExecutionPlan executionPlan, GridScheduler gridScheduler, int iterations) {
        if (gridScheduler != null) {
            executionPlan.withGridScheduler(gridScheduler);
        }
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executionPlan.execute();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            executionPlan.execute();
        }
        long end = System.nanoTime();
        return (end - start) / (double) iterations;
    }

    private static boolean validate(String name, FloatArray expected, FloatArray actual, int size, float relativeTolerance) {
        for (int i = 0; i < size * size; i++) {
            float e = expected.get(i);
            float a = actual.get(i);
            if (Math.abs(e - a) > relativeTolerance * Math.max(1.0f, Math.abs(e))) {
                System.out.println("[" + name + "] Mismatch at " + i + ": expected=" + e + ", actual=" + a);
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 1024;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 100;
        final double gflop = 2.0 * size * size * size * 1e-9;

        System.out.println("SGEMM benchmark: " + size + "x" + size + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up)");

        if (size % TS != 0) {
            throw new IllegalArgumentException("Size must be a multiple of the tile size (" + TS + ")");
        }

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray outputJit = new FloatArray(size * size);
        FloatArray outputTiled = new FloatArray(size * size);
        FloatArray outputCuBlas = new FloatArray(size * size);
        FloatArray outputCuBlasTF32 = new FloatArray(size * size);
        HalfFloatArray matrixAFP16 = new HalfFloatArray(size * size);
        HalfFloatArray matrixBFP16 = new HalfFloatArray(size * size);
        HalfFloatArray outputCuBlasFP16 = new HalfFloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            matrixA.set(i, random.nextFloat());
            matrixB.set(i, random.nextFloat());
            matrixAFP16.set(i, new HalfFloat(matrixA.get(i)));
            matrixBFP16.set(i, new HalfFloat(matrixB.get(i)));
        }

        TaskGraph jitGraph = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("mxm", BenchmarkSgemm::matrixMultiplication, matrixA, matrixB, outputJit, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputJit);

        // Tiled local-memory KernelContext kernel: TS x TS workgroups over the output
        WorkerGrid2D workerGrid = new WorkerGrid2D(size, size);
        workerGrid.setLocalWork(TS, TS, 1);
        GridScheduler gridScheduler = new GridScheduler("tiled.mxm", workerGrid);

        TaskGraph tiledGraph = new TaskGraph("tiled") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("mxm", BenchmarkSgemm::matrixMultiplicationTiled, new KernelContext(), matrixA, matrixB, outputTiled, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputTiled);

        // Row-major C = A * B computed as column-major C_cm = B_cm * A_cm
        TaskGraph cublasGraph = new TaskGraph("cublas") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemm", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, //
                        1.0f, matrixB, size, matrixA, size, //
                        0.0f, outputCuBlas, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputCuBlas);

        TaskGraph cublasTF32Graph = new TaskGraph("cublasTF32") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemmTF32", CuBlas::cublasSgemmTF32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, //
                        1.0f, matrixB, size, matrixA, size, //
                        0.0f, outputCuBlasTF32, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputCuBlasTF32);

        TaskGraph cublasFP16Graph = new TaskGraph("cublasFP16") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixAFP16, matrixBFP16) //
                .libraryTask("gemmExFP16", CuBlas::cublasGemmExFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, //
                        1.0f, matrixBFP16, size, matrixAFP16, size, //
                        0.0f, outputCuBlasFP16, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputCuBlasFP16);

        double jitTime;
        double tiledTime;
        double cublasTime;
        double cublasTF32Time;
        double cublasFP16Time;

        try (TornadoExecutionPlan jitPlan = new TornadoExecutionPlan(jitGraph.snapshot())) {
            jitTime = benchmark(jitPlan, null, iterations);
            jitPlan.execute().transferToHost(outputJit);
        }

        try (TornadoExecutionPlan tiledPlan = new TornadoExecutionPlan(tiledGraph.snapshot())) {
            tiledTime = benchmark(tiledPlan, gridScheduler, iterations);
            tiledPlan.execute().transferToHost(outputTiled);
        }

        try (TornadoExecutionPlan cublasPlan = new TornadoExecutionPlan(cublasGraph.snapshot())) {
            cublasTime = benchmark(cublasPlan, null, iterations);
            cublasPlan.execute().transferToHost(outputCuBlas);
        }

        try (TornadoExecutionPlan cublasTF32Plan = new TornadoExecutionPlan(cublasTF32Graph.snapshot())) {
            cublasTF32Time = benchmark(cublasTF32Plan, null, iterations);
            cublasTF32Plan.execute().transferToHost(outputCuBlasTF32);
        }

        try (TornadoExecutionPlan cublasFP16Plan = new TornadoExecutionPlan(cublasFP16Graph.snapshot())) {
            cublasFP16Time = benchmark(cublasFP16Plan, null, iterations);
            cublasFP16Plan.execute().transferToHost(outputCuBlasFP16);
        }
        FloatArray outputCuBlasFP16AsFP32 = new FloatArray(size * size);
        for (int i = 0; i < size * size; i++) {
            outputCuBlasFP16AsFP32.set(i, outputCuBlasFP16.get(i).getFloat32());
        }

        // Cross-validate the GPU results against the naive JIT kernel.
        // TF32: 10-bit mantissa multiply (5% rel); FP16: rounded inputs and
        // output (10% rel vs the FP32-input reference).
        boolean isResultCorrect = validate("tiled", outputJit, outputTiled, size, 0.01f) //
                & validate("cublas", outputJit, outputCuBlas, size, 0.01f) //
                & validate("cublasTF32", outputJit, outputCuBlasTF32, size, 0.05f) //
                & validate("cublasFP16", outputJit, outputCuBlasFP16AsFP32, size, 0.10f);

        System.out.printf("TornadoVM JIT kernel (naive @Parallel)  : %10.3f ms | %9.2f GFLOP/s%n", jitTime * 1e-6, gflop / (jitTime * 1e-9));
        System.out.printf("TornadoVM KernelContext (tiled, TS=%d)  : %10.3f ms | %9.2f GFLOP/s%n", TS, tiledTime * 1e-6, gflop / (tiledTime * 1e-9));
        System.out.printf("cuBLAS library task (FP32)              : %10.3f ms | %9.2f GFLOP/s%n", cublasTime * 1e-6, gflop / (cublasTime * 1e-9));
        System.out.printf("cuBLAS library task (TF32 Tensor Cores) : %10.3f ms | %9.2f GFLOP/s%n", cublasTF32Time * 1e-6, gflop / (cublasTF32Time * 1e-9));
        System.out.printf("cuBLAS library task (FP16 GemmEx)       : %10.3f ms | %9.2f GFLOP/s%n", cublasFP16Time * 1e-6, gflop / (cublasFP16Time * 1e-9));
        System.out.printf("Speedup (cuBLAS vs naive @Parallel)     : %10.2fx%n", jitTime / cublasTime);
        System.out.printf("Speedup (cuBLAS vs tiled KernelContext) : %10.2fx%n", tiledTime / cublasTime);
        System.out.printf("Speedup (TF32 vs tiled KernelContext)   : %10.2fx%n", tiledTime / cublasTF32Time);
        System.out.printf("Speedup (FP16 vs tiled KernelContext)   : %10.2fx%n", tiledTime / cublasFP16Time);
        System.out.println(isResultCorrect ? "Results match" : "Results DO NOT match");
    }
}
