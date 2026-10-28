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
package uk.ac.manchester.tornado.cusparse;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Factory methods for NVIDIA cuSPARSE library tasks (FP32 sparse BLAS).
 *
 * <pre>
 * // y = A * x, A in CSR
 * taskGraph.libraryTask("spmv", Cusparse::cusparseSpMV,
 *         rows, cols, nnz, csrRowOffsets, csrColInd, csrValues, x, y);
 * </pre>
 *
 * The sparse matrix {@code A} is passed in <b>CSR</b> form (32-bit indices,
 * zero-based): {@code csrRowOffsets} has {@code rows + 1} entries,
 * {@code csrColInd} and {@code csrValues} have {@code nnz} entries. Dense
 * operands are row-major, matching the TornadoVM native array layout. All FP32.
 *
 * <p>
 * Each operation has two forms. The short form computes the plain product
 * ({@code alpha = 1}, {@code beta = 0}); the long form takes {@code alpha} and
 * {@code beta} explicitly and computes the accumulating form
 * {@code y = alpha * A * x + beta * y}. When {@code beta != 0} the output
 * operand is read before it is written, so it is declared
 * {@link Access#READ_WRITE} rather than {@link Access#WRITE_ONLY} and its
 * device contents are kept live by the data-flow graph.
 */
public final class Cusparse {

    public static final String LIBRARY_NAME = "nvidia/cusparse";

    private Cusparse() {
    }

    /**
     * Sparse matrix-vector product {@code y = A * x}, with {@code A} an
     * {@code rows x cols} CSR matrix and {@code x}, {@code y} dense vectors of
     * length {@code cols} and {@code rows}.
     *
     * <p>
     * Equivalent to {@link #cusparseSpMV(int, int, int, float, IntArray, IntArray, FloatArray, FloatArray, float, FloatArray)}
     * with {@code alpha = 1.0f} and {@code beta = 0.0f}.
     */
    public static LibraryTaskDescriptor cusparseSpMV(int rows, int cols, int nnz, IntArray csrRowOffsets, IntArray csrColInd, FloatArray csrValues, FloatArray x, FloatArray y) {
        return cusparseSpMV(rows, cols, nnz, 1.0f, csrRowOffsets, csrColInd, csrValues, x, 0.0f, y);
    }

    /**
     * Scaled sparse matrix-vector product {@code y = alpha * A * x + beta * y},
     * with {@code A} an {@code rows x cols} CSR matrix and {@code x}, {@code y}
     * dense vectors of length {@code cols} and {@code rows}.
     *
     * <p>
     * With {@code beta != 0} the previous contents of {@code y} take part in the
     * result, so {@code y} is declared {@link Access#READ_WRITE} and TornadoVM
     * keeps its device buffer live across the call.
     */
    public static LibraryTaskDescriptor cusparseSpMV(int rows, int cols, int nnz, float alpha, IntArray csrRowOffsets, IntArray csrColInd, FloatArray csrValues, FloatArray x, float beta,
            FloatArray y) {
        Access[] access = new Access[10];
        Arrays.fill(access, Access.READ_ONLY);
        access[9] = (beta != 0.0f) ? Access.READ_WRITE : Access.WRITE_ONLY; // y
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cusparseSpMV") //
                .withParameters(new Object[] { rows, cols, nnz, alpha, csrRowOffsets, csrColInd, csrValues, x, beta, y }) //
                .withAccess(access);
    }

    /**
     * Sparse matrix-dense-matrix product {@code C = A * B}, with {@code A} an
     * {@code rows x k} CSR matrix, {@code B} a dense {@code k x n} row-major
     * matrix, and {@code C} a dense {@code rows x n} row-major matrix.
     *
     * <p>
     * Equivalent to {@link #cusparseSpMM(int, int, int, int, float, IntArray, IntArray, FloatArray, FloatArray, float, FloatArray)}
     * with {@code alpha = 1.0f} and {@code beta = 0.0f}.
     */
    public static LibraryTaskDescriptor cusparseSpMM(int rows, int k, int n, int nnz, IntArray csrRowOffsets, IntArray csrColInd, FloatArray csrValues, FloatArray b, FloatArray c) {
        return cusparseSpMM(rows, k, n, nnz, 1.0f, csrRowOffsets, csrColInd, csrValues, b, 0.0f, c);
    }

    /**
     * Scaled sparse matrix-dense-matrix product
     * {@code C = alpha * A * B + beta * C}, with {@code A} an {@code rows x k}
     * CSR matrix, {@code B} a dense {@code k x n} row-major matrix, and {@code C}
     * a dense {@code rows x n} row-major matrix.
     *
     * <p>
     * With {@code beta != 0} the previous contents of {@code C} take part in the
     * result, so {@code C} is declared {@link Access#READ_WRITE} and TornadoVM
     * keeps its device buffer live across the call.
     */
    public static LibraryTaskDescriptor cusparseSpMM(int rows, int k, int n, int nnz, float alpha, IntArray csrRowOffsets, IntArray csrColInd, FloatArray csrValues, FloatArray b, float beta,
            FloatArray c) {
        Access[] access = new Access[11];
        Arrays.fill(access, Access.READ_ONLY);
        access[10] = (beta != 0.0f) ? Access.READ_WRITE : Access.WRITE_ONLY; // c
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cusparseSpMM") //
                .withParameters(new Object[] { rows, k, n, nnz, alpha, csrRowOffsets, csrColInd, csrValues, b, beta, c }) //
                .withAccess(access);
    }
}
