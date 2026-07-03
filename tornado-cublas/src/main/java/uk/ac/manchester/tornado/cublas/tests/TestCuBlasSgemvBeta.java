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
 * SGEMV with {@code beta != 0}: y = alpha * A * x + beta * y. The output
 * vector is also an input (READ_WRITE), so its initial contents must reach the
 * device and survive across iterations.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvBeta [size]
 * </code>
 */
public class TestCuBlasSgemvBeta {

    public static void main(String[] args) throws TornadoExecutionPlanException {

        final int size = (args.length > 0) ? Integer.parseInt(args[0]) : 512;
        final int m = size;
        final int n = size;

        System.out.println("Testing TornadoVM Hybrid API - cublasSgemv with beta != 0 (" + m + "x" + n + ")");

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
        for (int i = 0; i < m; i++) {
            float value = random.nextFloat();
            output.set(i, value);
            seqOutput.set(i, value);
        }

        final float alpha = 1.0f;
        final float beta = 0.5f;
        final int incx = 1;
        final int incy = 1;
        final int lda = m;

        TaskGraph taskGraph = new TaskGraph("cuBLAS") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector, output) //
                .libraryTask("sgemvBeta", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), //
                        m, n, //
                        alpha, matrix, lda, vector, //
                        incx, beta, output, incy) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        // Sequential reference: y = alpha * A * x + beta * y0
        for (int i = 0; i < m; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += matrix.get(i * n + j) * vector.get(j);
            }
            seqOutput.set(i, alpha * sum + beta * seqOutput.get(i));
        }

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
