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
 * Single library task: y = A * x via cuBLAS SGEMV, validated against a
 * sequential Java reference.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemv [size]
 * </code>
 */
public class TestCuBlasSgemv {

    public static void matrixVectorJava(FloatArray matrix, FloatArray vector, FloatArray output, int m, int n) {
        for (int i = 0; i < m; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += matrix.get(i * n + j) * vector.get(j);
            }
            output.set(i, sum);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 512;
        final int m = size;
        final int n = size;

        System.out.println("Testing TornadoVM Hybrid API - cublasSgemv (" + m + "x" + n + ")");

        FloatArray matrix = new FloatArray(m * n);
        FloatArray vector = new FloatArray(n);
        FloatArray output = new FloatArray(m);
        FloatArray seqOutput = new FloatArray(m);

        Random random = new Random(42);
        for (int i = 0; i < m * n; i++) {
            matrix.set(i, random.nextFloat());
        }
        for (int i = 0; i < n; i++) {
            vector.set(i, random.nextFloat());
        }

        final float alpha = 1.0f;
        final float beta = 0.0f;
        final int incx = 1;
        final int incy = 1;
        final int lda = m;

        TaskGraph taskGraph = new TaskGraph("cuBLAS") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                // Row-major data: CUBLAS_OP_T on the column-major view computes A * x
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), //
                        m, n, //
                        alpha, matrix, lda, vector, //
                        incx, beta, output, incy) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        matrixVectorJava(matrix, vector, seqOutput, m, n);

        boolean isResultCorrect = true;
        for (int i = 0; i < m; i++) {
            if (Math.abs(seqOutput.get(i) - output.get(i)) > 0.01f) {
                System.out.println("Mismatch at " + i + ": expected " + seqOutput.get(i) + ", got " + output.get(i));
                isResultCorrect = false;
                break;
            }
        }
        System.out.println(isResultCorrect ? "Result is correct" : "Result is wrong");
    }
}
