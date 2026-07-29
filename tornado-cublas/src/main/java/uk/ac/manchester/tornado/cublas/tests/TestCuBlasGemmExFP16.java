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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * Mixed-precision GEMM via {@code cublasGemmEx}: FP16 inputs with (a) FP16
 * output and (b) FP32 output, both with FP32 Tensor Core accumulation.
 * Validated against a sequential Java reference computed from the same
 * FP16-rounded inputs, so the only differences are output rounding and
 * accumulation order.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasGemmExFP16 [size]
 * </code>
 */
public class TestCuBlasGemmExFP16 {

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 512;

        System.out.println("Testing TornadoVM Hybrid API - cublasGemmEx FP16 (" + size + "x" + size + ")");

        HalfFloatArray matrixA = new HalfFloatArray(size * size);
        HalfFloatArray matrixB = new HalfFloatArray(size * size);
        HalfFloatArray outputFP16 = new HalfFloatArray(size * size);
        FloatArray outputFP32 = new FloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            matrixA.set(i, new HalfFloat(random.nextFloat()));
            matrixB.set(i, new HalfFloat(random.nextFloat()));
        }

        // FP16 in / FP16 out
        TaskGraph fp16Graph = new TaskGraph("gemmExFP16") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("gemm", CuBlas::cublasGemmExFP16, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, outputFP16, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputFP16);

        // FP16 in / FP32 out
        TaskGraph fp32Graph = new TaskGraph("gemmExFP16FP32") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("gemm", CuBlas::cublasGemmExFP16FP32, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, outputFP32, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputFP32);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(fp16Graph.snapshot())) {
            plan.execute();
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(fp32Graph.snapshot())) {
            plan.execute();
        }

        // Java reference from the same FP16-rounded inputs, FP32 accumulation
        FloatArray reference = new FloatArray(size * size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int p = 0; p < size; p++) {
                    sum += matrixA.get(i * size + p).getFloat32() * matrixB.get(p * size + j).getFloat32();
                }
                reference.set(i * size + j, sum);
            }
        }

        boolean fp16Correct = true;
        boolean fp32Correct = true;
        for (int i = 0; i < size * size; i++) {
            float expected = reference.get(i);
            // FP16 output: rounded to 11-bit mantissa
            if (Math.abs(expected - outputFP16.get(i).getFloat32()) > 2e-3f * Math.max(1.0f, Math.abs(expected))) {
                if (fp16Correct) {
                    System.out.println("[FP16 out] Mismatch at " + i + ": expected " + expected + ", got " + outputFP16.get(i).getFloat32());
                }
                fp16Correct = false;
            }
            // FP32 output: only accumulation-order differences
            if (Math.abs(expected - outputFP32.get(i)) > 1e-3f * Math.max(1.0f, Math.abs(expected))) {
                if (fp32Correct) {
                    System.out.println("[FP32 out] Mismatch at " + i + ": expected " + expected + ", got " + outputFP32.get(i));
                }
                fp32Correct = false;
            }
        }

        System.out.println("FP16 in / FP16 out: " + (fp16Correct ? "OK" : "FAILED"));
        System.out.println("FP16 in / FP32 out: " + (fp32Correct ? "OK" : "FAILED"));
        System.out.println((fp16Correct && fp32Correct) ? "Result is correct" : "Result is wrong");
    }
}
