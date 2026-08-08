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
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * SGEMM with the TF32 Tensor Core math mode ({@code CuBlas.cublasSgemmTF32}):
 * validates the TF32 result against the default FP32 cuBLAS result with a
 * relaxed tolerance (TF32 has a 10-bit mantissa; accumulation stays FP32), and
 * reports the timing of both modes.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemmTF32 [size]
 * </code>
 */
public class TestCuBlasSgemmTF32 {

    private static final int WARMUP_ITERATIONS = 20;
    private static final int ITERATIONS = 50;

    private static double benchmark(TornadoExecutionPlan executionPlan) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executionPlan.execute();
        }
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            executionPlan.execute();
        }
        return (System.nanoTime() - start) / (double) ITERATIONS;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 2048;
        final double gflop = 2.0 * size * size * size * 1e-9;

        System.out.println("Testing TornadoVM Hybrid API - cublasSgemm with TF32 math mode (" + size + "x" + size + ")");

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray outputFP32 = new FloatArray(size * size);
        FloatArray outputTF32 = new FloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            matrixA.set(i, random.nextFloat());
            matrixB.set(i, random.nextFloat());
        }

        TaskGraph fp32Graph = new TaskGraph("fp32") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemm", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, outputFP32, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputFP32);

        TaskGraph tf32Graph = new TaskGraph("tf32") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemmTF32", CuBlas::cublasSgemmTF32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, outputTF32, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputTF32);

        double fp32Time;
        double tf32Time;
        try (TornadoExecutionPlan fp32Plan = new TornadoExecutionPlan(fp32Graph.snapshot())) {
            fp32Time = benchmark(fp32Plan);
            fp32Plan.execute().transferToHost(outputFP32);
        }
        try (TornadoExecutionPlan tf32Plan = new TornadoExecutionPlan(tf32Graph.snapshot())) {
            tf32Time = benchmark(tf32Plan);
            tf32Plan.execute().transferToHost(outputTF32);
        }

        // TF32 vs FP32: relaxed relative tolerance (10-bit mantissa multiply, FP32 accumulate)
        boolean isResultCorrect = true;
        float maxRelError = 0.0f;
        for (int i = 0; i < size * size; i++) {
            float expected = outputFP32.get(i);
            float relError = Math.abs(expected - outputTF32.get(i)) / Math.max(1.0f, Math.abs(expected));
            maxRelError = Math.max(maxRelError, relError);
            if (relError > 5e-2f) {
                System.out.println("Mismatch at " + i + ": fp32=" + expected + ", tf32=" + outputTF32.get(i));
                isResultCorrect = false;
                break;
            }
        }

        System.out.printf("cuBLAS SGEMM FP32 : %10.3f ms | %9.2f GFLOP/s%n", fp32Time * 1e-6, gflop / (fp32Time * 1e-9));
        System.out.printf("cuBLAS SGEMM TF32 : %10.3f ms | %9.2f GFLOP/s%n", tf32Time * 1e-6, gflop / (tf32Time * 1e-9));
        System.out.printf("TF32 speedup      : %10.2fx | max rel error = %.6f%n", fp32Time / tf32Time, maxRelError);
        System.out.println(isResultCorrect ? "Result is correct" : "Result is wrong");
    }
}
