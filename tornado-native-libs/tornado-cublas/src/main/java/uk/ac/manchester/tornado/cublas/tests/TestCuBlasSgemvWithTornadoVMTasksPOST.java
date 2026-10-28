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

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

/**
 * MVP integration test for the hybrid API: a task graph mixing a JIT-compiled
 * task (pre), a cuBLAS library task, and another JIT-compiled task (post), all
 * sharing TornadoVM-managed device buffers, executed 10 times.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST
 * </code>
 */
public class TestCuBlasSgemvWithTornadoVMTasksPOST {

    public static void matrixVectorJava(FloatArray matrix, FloatArray vector, FloatArray output, int m, int n) {
        for (int i = 0; i < m; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += matrix.get(i * n + j) * vector.get(j);
            }
            output.set(i, sum);
        }
    }

    public static void mutateData(FloatArray matrixA) {
        for (@Parallel int i = 0; i < matrixA.getSize(); i++) {
            matrixA.set(i, matrixA.get(i) + 1.0f);
        }
    }

    public static void mutateDataPost(FloatArray matrixA) {
        for (@Parallel int i = 0; i < matrixA.getSize(); i++) {
            matrixA.set(i, matrixA.get(i) + 12.0f);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {

        System.out.println("Testing TornadoVM Hybrid API - cublasSgemv with pre/post tasks");

        final int m = 2;
        final int n = 2;

        FloatArray matrix = new FloatArray(m * n);

        /*
         * Matrix = | 1.0 2.0 |
         *          | 3.0 4.0 |
         *
         * Vector = | 5.0 6.0 |
         */
        matrix.set(0, 1.0f);
        matrix.set(1, 2.0f);
        matrix.set(2, 3.0f);
        matrix.set(3, 4.0f);
        FloatArray vector = new FloatArray(n);
        vector.set(0, 5.0f);
        vector.set(1, 6.0f);

        FloatArray output = new FloatArray(m);
        FloatArray seqOutput = new FloatArray(m);

        TaskGraph taskGraph = new TaskGraph("cuBLAS");

        final float alpha = 1.0f;
        final float beta = 0.0f;
        final int incx = 1;
        final int incy = 1;
        final int lda = m;

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .task("mutateData", TestCuBlasSgemvWithTornadoVMTasksPOST::mutateData, matrix) //
                .libraryTask("cublasSgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), //
                        m, n, //
                        alpha, matrix, lda, vector, //
                        incx, beta, output, incy) //
                .task("mutateDataPost", TestCuBlasSgemvWithTornadoVMTasksPOST::mutateDataPost, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            int it = 0;
            while (it < 10) {
                // Reset host matrix to original values before each execution
                // This ensures transferToDevice(EVERY_EXECUTION) copies fresh data
                matrix.set(0, 1.0f);
                matrix.set(1, 2.0f);
                matrix.set(2, 3.0f);
                matrix.set(3, 4.0f);

                executionPlan.execute();

                /*
                 * Expected Result after mutateData: matrix = | 2.00 3.00 | 4.00 5.00 |
                 * Expected Result after cublasSgemv: y = | 17.00 39.00 |
                 * Expected Result after mutateDataPost: y = | 29.00 51.00 |
                 */
                FloatArray matrixSeqCopy = new FloatArray(m * n);
                matrixSeqCopy.set(0, 1.0f);
                matrixSeqCopy.set(1, 2.0f);
                matrixSeqCopy.set(2, 3.0f);
                matrixSeqCopy.set(3, 4.0f);

                // Simulate mutateData: matrix + 1.0
                for (int i = 0; i < matrixSeqCopy.getSize(); i++) {
                    matrixSeqCopy.set(i, matrixSeqCopy.get(i) + 1.0f);
                }

                // Compute matrix-vector multiplication
                matrixVectorJava(matrixSeqCopy, vector, seqOutput, m, n);

                // Simulate mutateDataPost: output + 12.0
                for (int i = 0; i < seqOutput.getSize(); i++) {
                    seqOutput.set(i, seqOutput.get(i) + 12.0f);
                }

                // Check Result
                boolean isResultCorrect = true;
                for (int i = 0; i < n; i++) {
                    if ((Math.abs(seqOutput.get(i) - output.get(i))) > 0.01f) {
                        isResultCorrect = false;
                        break;
                    }
                }
                if (isResultCorrect) {
                    System.out.println("Result is correct");
                } else {
                    System.out.println("Result is wrong");
                }
                System.out.println("MATRIX : " + Arrays.toString(matrix.toHeapArray()));
                System.out.println("VECTOR : " + Arrays.toString(vector.toHeapArray()));
                System.out.println("OUTPUT : " + Arrays.toString(output.toHeapArray()));
                System.out.println("SEQ OUT: " + Arrays.toString(seqOutput.toHeapArray()));
                System.out.println("---");
                it++;
            }

        }
    }
}
