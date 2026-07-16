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
 * Single library task: C = A * B via cuBLAS SGEMM, validated against a
 * sequential Java reference. Row-major inputs are handled with the standard
 * column-major trick: C_cm = B_cm * A_cm.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm [size]
 * </code>
 */
public class TestCuBlasSgemm {

    public static void matrixMultiplyJava(FloatArray a, FloatArray b, FloatArray c, int m, int n, int k) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float sum = 0.0f;
                for (int p = 0; p < k; p++) {
                    sum += a.get(i * k + p) * b.get(p * n + j);
                }
                c.set(i * n + j, sum);
            }
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 256;
        final int m = size;
        final int n = size;
        final int k = size;

        System.out.println("Testing TornadoVM Hybrid API - cublasSgemm (" + m + "x" + k + " * " + k + "x" + n + ")");

        FloatArray matrixA = new FloatArray(m * k);
        FloatArray matrixB = new FloatArray(k * n);
        FloatArray matrixC = new FloatArray(m * n);
        FloatArray seqC = new FloatArray(m * n);

        Random random = new Random(42);
        for (int i = 0; i < m * k; i++) {
            matrixA.set(i, random.nextFloat());
        }
        for (int i = 0; i < k * n; i++) {
            matrixB.set(i, random.nextFloat());
        }

        final float alpha = 1.0f;
        final float beta = 0.0f;

        TaskGraph taskGraph = new TaskGraph("cuBLAS") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrixA, matrixB) //
                // Row-major C = A * B computed as column-major C_cm = B_cm * A_cm
                .libraryTask("sgemm", CuBlas::cublasSgemm, //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        CuBlasOperation.CUBLAS_OP_N.operation(), //
                        n, m, k, //
                        alpha, matrixB, n, matrixA, k, //
                        beta, matrixC, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        matrixMultiplyJava(matrixA, matrixB, seqC, m, n, k);

        boolean isResultCorrect = true;
        for (int i = 0; i < m * n; i++) {
            if (Math.abs(seqC.get(i) - matrixC.get(i)) > 0.1f) {
                System.out.println("Mismatch at " + i + ": expected " + seqC.get(i) + ", got " + matrixC.get(i));
                isResultCorrect = false;
                break;
            }
        }
        System.out.println(isResultCorrect ? "Result is correct" : "Result is wrong");
    }
}
