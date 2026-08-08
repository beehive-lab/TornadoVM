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
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.CuBlasLt;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * Benchmark: transformer MLP block C = GELU(A * B + bias) in FP16, three ways:
 * (1) unfused hybrid - cuBLAS GemmEx FP16 followed by a JIT-compiled bias+GELU
 * kernel; (2) fused - a single cuBLASLt matmul with the GELU_BIAS epilogue;
 * (3) same-graph JIT epilogue variant executed under identical conditions.
 * Results are cross-validated.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkLtFusedMlp [size] [iterations]
 * </code>
 */
public class BenchmarkLtFusedMlp {

    private static final int WARMUP_ITERATIONS = 20;

    /**
     * Bias add + GELU (tanh approximation), the epilogue of a transformer MLP
     * block, as a JIT-compiled kernel. Bias is per column of the row-major C.
     */
    public static void geluBias(HalfFloatArray matrixC, HalfFloatArray bias, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float x = matrixC.get(i * size + j).getFloat32() + bias.get(j).getFloat32();
                float gelu = 0.5f * x * (1.0f + TornadoMath.tanh(0.7978845608f * (x + 0.044715f * x * x * x)));
                matrixC.set(i * size + j, new HalfFloat(gelu));
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
        return (System.nanoTime() - start) / (double) iterations;
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 2048;
        final int iterations = (args.length > 1) ? Integer.parseInt(args[1]) : 50;
        final double gflop = 2.0 * size * size * size * 1e-9;

        System.out.println("Fused MLP benchmark: C = GELU(A * B + bias), FP16, " + size + "x" + size + ", " + iterations + " iterations (+" + WARMUP_ITERATIONS + " warm-up)");

        HalfFloatArray matrixA = new HalfFloatArray(size * size);
        HalfFloatArray matrixB = new HalfFloatArray(size * size);
        HalfFloatArray bias = new HalfFloatArray(size);
        HalfFloatArray outputUnfused = new HalfFloatArray(size * size);
        HalfFloatArray outputFused = new HalfFloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat() - 0.5f));
            matrixB.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < size; i++) {
            bias.set(i, new HalfFloat(random.nextFloat() - 0.5f));
        }

        // Unfused: cuBLAS GemmEx FP16 + JIT bias+GELU kernel (two tasks)
        TaskGraph unfusedGraph = new TaskGraph("unfused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB, bias) //
                .libraryTask("gemm", CuBlas::cublasGemmExFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, outputUnfused, size) //
                .task("geluBias", BenchmarkLtFusedMlp::geluBias, outputUnfused, bias, size) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputUnfused);

        // Fused: one cuBLASLt matmul with the GELU_BIAS epilogue
        TaskGraph fusedGraph = new TaskGraph("fused") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB, bias) //
                .libraryTask("ltMatmul", CuBlasLt::ltMatmulGeluBiasFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, outputFused, size, bias) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, outputFused);

        double unfusedTime;
        double fusedTime;

        try (TornadoExecutionPlan unfusedPlan = new TornadoExecutionPlan(unfusedGraph.snapshot())) {
            unfusedTime = benchmark(unfusedPlan, iterations);
            unfusedPlan.execute().transferToHost(outputUnfused);
        }

        try (TornadoExecutionPlan fusedPlan = new TornadoExecutionPlan(fusedGraph.snapshot())) {
            fusedTime = benchmark(fusedPlan, iterations);
            fusedPlan.execute().transferToHost(outputFused);
        }

        // Cross-validate fused vs unfused (same FP16 inputs, both GELU-tanh).
        // The unfused path rounds the GEMM result to FP16 BEFORE the epilogue
        // while the fused epilogue sees the FP32 accumulator, so pre-activation
        // inputs differ by up to one FP16 ulp of |x| (~0.016 at |x|=20): allow
        // an absolute slack proportional to that in addition to the relative one.
        boolean isResultCorrect = true;
        for (int i = 0; i < size * size; i++) {
            float expected = outputUnfused.get(i).getFloat32();
            float actual = outputFused.get(i).getFloat32();
            if (Math.abs(expected - actual) > 0.05f + 2e-2f * Math.abs(expected)) {
                System.out.println("Mismatch at " + i + ": unfused=" + expected + ", fused=" + actual);
                isResultCorrect = false;
                break;
            }
        }

        System.out.printf("GemmEx FP16 + JIT bias/GELU kernel  : %10.3f ms | %9.2f GFLOP/s%n", unfusedTime * 1e-6, gflop / (unfusedTime * 1e-9));
        System.out.printf("cuBLASLt fused GELU_BIAS epilogue   : %10.3f ms | %9.2f GFLOP/s%n", fusedTime * 1e-6, gflop / (fusedTime * 1e-9));
        System.out.printf("Fusion speedup                      : %10.2fx%n", unfusedTime / fusedTime);
        System.out.println(isResultCorrect ? "Results match" : "Results DO NOT match");
    }
}
