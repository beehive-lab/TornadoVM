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
package uk.ac.manchester.tornado.cublas;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Factory methods for NVIDIA cuBLAS library tasks. Each method builds a
 * {@link LibraryTaskDescriptor} consumed by
 * {@code TaskGraph#libraryTask(String, ...)}:
 *
 * <pre>
 * taskGraph.libraryTask("sgemv", CuBlas::cublasSgemv,
 *         CuBlasOperation.CUBLAS_OP_T.operation(), m, n,
 *         alpha, matrix, lda, vector, incx, beta, output, incy);
 * </pre>
 *
 * <p>
 * Note: cuBLAS assumes column-major storage. For row-major TornadoVM arrays,
 * pass {@code CUBLAS_OP_T} (or swap operands for GEMM) as with any C
 * application using cuBLAS.
 * </p>
 */
public final class CuBlas {

    public static final String LIBRARY_NAME = "nvidia/cublas";

    private CuBlas() {
    }

    /**
     * All arguments are READ_ONLY except the output. When {@code beta != 0} the
     * output operand is also read by cuBLAS (y = ... + beta * y), so it must be
     * READ_WRITE for TornadoVM to keep its device contents valid.
     */
    private static Access[] readOnlyExcept(int numArgs, int outputIndex, float beta) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        accesses[outputIndex] = (beta != 0.0f) ? Access.READ_WRITE : Access.WRITE_ONLY;
        return accesses;
    }

    /**
     * Matrix-vector multiplication: {@code y = alpha * op(A) * x + beta * y}.
     *
     * <p>
     * Full description:
     * {@url https://docs.nvidia.com/cuda/cublas/index.html#cublas-t-gemv}
     * </p>
     *
     * @param operation
     *     Operation on matrix A ({@link uk.ac.manchester.tornado.cublas.enums.CuBlasOperation}).
     * @param m
     *     Number of rows of matrix A (column-major).
     * @param n
     *     Number of columns of matrix A (column-major).
     * @param alpha
     *     Scalar used for multiplication with A.
     * @param matrix
     *     Matrix A of dimensions {@code lda x n}.
     * @param lda
     *     Leading dimension of A.
     * @param vector
     *     Input vector x.
     * @param incx
     *     Stride between consecutive elements of x.
     * @param beta
     *     Scalar used for multiplication with y.
     * @param output
     *     Output vector y.
     * @param incy
     *     Stride between consecutive elements of y.
     * @return {@link LibraryTaskDescriptor}
     */
    public static LibraryTaskDescriptor cublasSgemv(int operation, //
            int m, //
            int n, //
            float alpha, //
            FloatArray matrix, //
            int lda, //
            FloatArray vector, //
            int incx, //
            float beta, //
            FloatArray output, //
            int incy) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cublasSgemv") //
                .withParameters(new Object[] { operation, m, n, alpha, matrix, lda, vector, incx, beta, output, incy }) //
                .withAccess(readOnlyExcept(11, 9, beta));
    }

    /**
     * Matrix-matrix multiplication: {@code C = alpha * op(A) * op(B) + beta * C}.
     *
     * <p>
     * Full description:
     * {@url https://docs.nvidia.com/cuda/cublas/index.html#cublas-t-gemm}
     * </p>
     *
     * @param transa
     *     Operation on matrix A.
     * @param transb
     *     Operation on matrix B.
     * @param m
     *     Number of rows of op(A) and C.
     * @param n
     *     Number of columns of op(B) and C.
     * @param k
     *     Number of columns of op(A) and rows of op(B).
     * @param alpha
     *     Scalar used for multiplication with op(A) * op(B).
     * @param matrixA
     *     Matrix A.
     * @param lda
     *     Leading dimension of A.
     * @param matrixB
     *     Matrix B.
     * @param ldb
     *     Leading dimension of B.
     * @param beta
     *     Scalar used for multiplication with C.
     * @param matrixC
     *     Output matrix C.
     * @param ldc
     *     Leading dimension of C.
     * @return {@link LibraryTaskDescriptor}
     */
    public static LibraryTaskDescriptor cublasSgemm(int transa, //
            int transb, //
            int m, //
            int n, //
            int k, //
            float alpha, //
            FloatArray matrixA, //
            int lda, //
            FloatArray matrixB, //
            int ldb, //
            float beta, //
            FloatArray matrixC, //
            int ldc) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cublasSgemm") //
                .withParameters(new Object[] { transa, transb, m, n, k, alpha, matrixA, lda, matrixB, ldb, beta, matrixC, ldc }) //
                .withAccess(readOnlyExcept(13, 11, beta));
    }
}
