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
 */
public final class Cusparse {

    public static final String LIBRARY_NAME = "nvidia/cusparse";

    private Cusparse() {
    }

    /**
     * Sparse matrix-vector product {@code y = A * x}, with {@code A} an
     * {@code rows x cols} CSR matrix and {@code x}, {@code y} dense vectors of
     * length {@code cols} and {@code rows}.
     */
    public static LibraryTaskDescriptor cusparseSpMV(int rows, int cols, int nnz, IntArray csrRowOffsets, IntArray csrColInd, FloatArray csrValues, FloatArray x, FloatArray y) {
        Access[] access = new Access[8];
        Arrays.fill(access, Access.READ_ONLY);
        access[7] = Access.WRITE_ONLY; // y
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cusparseSpMV") //
                .withParameters(new Object[] { rows, cols, nnz, csrRowOffsets, csrColInd, csrValues, x, y }) //
                .withAccess(access);
    }

    /**
     * Sparse matrix-dense-matrix product {@code C = A * B}, with {@code A} an
     * {@code rows x k} CSR matrix, {@code B} a dense {@code k x n} row-major
     * matrix, and {@code C} a dense {@code rows x n} row-major matrix.
     */
    public static LibraryTaskDescriptor cusparseSpMM(int rows, int k, int n, int nnz, IntArray csrRowOffsets, IntArray csrColInd, FloatArray csrValues, FloatArray b, FloatArray c) {
        Access[] access = new Access[9];
        Arrays.fill(access, Access.READ_ONLY);
        access[8] = Access.WRITE_ONLY; // c
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cusparseSpMM") //
                .withParameters(new Object[] { rows, k, n, nnz, csrRowOffsets, csrColInd, csrValues, b, c }) //
                .withAccess(access);
    }
}
