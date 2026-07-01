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

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * Benchmark: FP32 matrix multiplication (C = A * B) with the TornadoVM
 * JIT-generated kernel vs a cuBLAS SGEMM library task on the same device
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

    private static double benchmark(TornadoExecutionPlan executionPlan, int iterations) {
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

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 1024;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 100;
        final double gflop = 2.0 * size * size * size * 1e-9;

        System.out.println("SGEMM benchmark: " + size + "x" + size + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up)");

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray outputJit = new FloatArray(size * size);
        FloatArray outputCuBlas = new FloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            matrixA.set(i, random.nextFloat());
            matrixB.set(i, random.nextFloat());
        }

        TaskGraph jitGraph = new TaskGraph("jit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("mxm", BenchmarkSgemm::matrixMultiplication, matrixA, matrixB, outputJit, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputJit);

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

        double jitTime;
        double cublasTime;

        try (TornadoExecutionPlan jitPlan = new TornadoExecutionPlan(jitGraph.snapshot())) {
            jitTime = benchmark(jitPlan, iterations);
            jitPlan.execute().transferToHost(outputJit);
        }

        try (TornadoExecutionPlan cublasPlan = new TornadoExecutionPlan(cublasGraph.snapshot())) {
            cublasTime = benchmark(cublasPlan, iterations);
            cublasPlan.execute().transferToHost(outputCuBlas);
        }

        // Cross-validate the two GPU results
        boolean isResultCorrect = true;
        for (int i = 0; i < size * size; i++) {
            float expected = outputJit.get(i);
            float actual = outputCuBlas.get(i);
            if (Math.abs(expected - actual) > 0.01f * Math.max(1.0f, Math.abs(expected))) {
                System.out.println("Mismatch at " + i + ": jit=" + expected + ", cublas=" + actual);
                isResultCorrect = false;
                break;
            }
        }

        System.out.printf("TornadoVM JIT kernel : %10.3f ms | %8.2f GFLOP/s%n", jitTime * 1e-6, gflop / (jitTime * 1e-9));
        System.out.printf("cuBLAS library task  : %10.3f ms | %8.2f GFLOP/s%n", cublasTime * 1e-6, gflop / (cublasTime * 1e-9));
        System.out.printf("Speedup (cuBLAS/JIT) : %10.2fx%n", jitTime / cublasTime);
        System.out.println(isResultCorrect ? "Results match" : "Results DO NOT match");
    }
}
