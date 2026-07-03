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
import uk.ac.manchester.tornado.cublas.CuBlasOptions;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * SGEMM with a user-owned 32 MiB cuBLAS workspace ({@code cublasSetWorkspace}
 * via {@link CuBlasOptions#withWorkspace}), executed repeatedly to exercise the
 * grow-only workspace path and teardown; validated against a sequential Java
 * reference.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasWorkspace [size]
 * </code>
 */
public class TestCuBlasWorkspace {

    private static final long WORKSPACE_BYTES = 32L * 1024 * 1024;

    public static void matrixMultiplyJava(FloatArray a, FloatArray b, FloatArray c, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int p = 0; p < size; p++) {
                    sum += a.get(i * size + p) * b.get(p * size + j);
                }
                c.set(i * size + j, sum);
            }
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 256;

        System.out.println("Testing TornadoVM Hybrid API - cublasSgemm with user workspace (" + size + "x" + size + ", " + (WORKSPACE_BYTES / (1024 * 1024)) + " MiB)");

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray matrixC = new FloatArray(size * size);
        FloatArray seqC = new FloatArray(size * size);

        Random random = new Random(42);
        for (int i = 0; i < size * size; i++) {
            matrixA.set(i, random.nextFloat());
            matrixB.set(i, random.nextFloat());
        }

        final CuBlasOptions options = new CuBlasOptions().withWorkspace(WORKSPACE_BYTES);

        TaskGraph taskGraph = new TaskGraph("cuBLAS") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                .libraryTask("sgemm", (Integer ta, Integer tb, Integer m, Integer n, Integer k, Float alpha, FloatArray a, Integer lda, FloatArray b, Integer ldb, Float beta, FloatArray c,
                        Integer ldc) -> CuBlas.cublasSgemm(ta, tb, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc).withTuning(options), //
                        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(), //
                        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            // Multiple executions: first allocates + sets the workspace, the rest reuse it
            for (int i = 0; i < 5; i++) {
                executionPlan.execute();
            }
        }

        matrixMultiplyJava(matrixA, matrixB, seqC, size);

        boolean isResultCorrect = true;
        for (int i = 0; i < size * size; i++) {
            if (Math.abs(seqC.get(i) - matrixC.get(i)) > 0.01f * Math.max(1.0f, Math.abs(seqC.get(i)))) {
                System.out.println("Mismatch at " + i + ": expected " + seqC.get(i) + ", got " + matrixC.get(i));
                isResultCorrect = false;
                break;
            }
        }
        System.out.println(isResultCorrect ? "Result is correct" : "Result is wrong");
    }
}
