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
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Factory methods for NVIDIA cuBLASLt library tasks: matmul with fused
 * epilogues (bias, GELU). Compared to {@link CuBlas}, cuBLASLt fuses the
 * bias-add and activation of a transformer MLP block into the GEMM itself —
 * one library task instead of a GEMM plus a JIT activation kernel.
 *
 * <p>
 * The bias vector has one element per row of the column-major result. With the
 * standard row-major trick (row-major C = A·B computed as column-major
 * C_cm = B_cm · A_cm), the bias is applied per column of the row-major C —
 * i.e., per output feature, matching the transformer convention.
 * </p>
 *
 * <p>
 * GELU epilogues use the tanh approximation.
 * </p>
 */
public final class CuBlasLt {

    public static final String LIBRARY_NAME = "nvidia/cublaslt";

    private CuBlasLt() {
    }

    private static Access[] readOnlyExcept(int numArgs, int outputIndex, float beta) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        accesses[outputIndex] = (beta != 0.0f) ? Access.READ_WRITE : Access.WRITE_ONLY;
        return accesses;
    }

    /**
     * FP32 matmul via {@code cublasLtMatmul}: C = alpha * op(A) * op(B) + beta * C.
     */
    public static LibraryTaskDescriptor ltMatmulFP32(int transa, int transb, int m, int n, int k, //
            float alpha, FloatArray matrixA, int lda, FloatArray matrixB, int ldb, //
            float beta, FloatArray matrixC, int ldc) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("ltMatmulFP32") //
                .withParameters(new Object[] { transa, transb, m, n, k, alpha, matrixA, lda, matrixB, ldb, beta, matrixC, ldc }) //
                .withAccess(readOnlyExcept(13, 11, beta));
    }

    /**
     * FP16 matmul via {@code cublasLtMatmul} (FP32 Tensor Core accumulation).
     */
    public static LibraryTaskDescriptor ltMatmulFP16(int transa, int transb, int m, int n, int k, //
            float alpha, HalfFloatArray matrixA, int lda, HalfFloatArray matrixB, int ldb, //
            float beta, HalfFloatArray matrixC, int ldc) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("ltMatmulFP16") //
                .withParameters(new Object[] { transa, transb, m, n, k, alpha, matrixA, lda, matrixB, ldb, beta, matrixC, ldc }) //
                .withAccess(readOnlyExcept(13, 11, beta));
    }

    /**
     * FP16 matmul with fused bias add: C = op(A) * op(B) + bias (broadcast).
     * The bias vector has m elements (rows of the column-major result).
     */
    public static LibraryTaskDescriptor ltMatmulBiasFP16(int transa, int transb, int m, int n, int k, //
            float alpha, HalfFloatArray matrixA, int lda, HalfFloatArray matrixB, int ldb, //
            float beta, HalfFloatArray matrixC, int ldc, HalfFloatArray bias) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("ltMatmulBiasFP16") //
                .withParameters(new Object[] { transa, transb, m, n, k, alpha, matrixA, lda, matrixB, ldb, beta, matrixC, ldc, bias }) //
                .withAccess(readOnlyExcept(14, 11, beta));
    }

    /**
     * FP16 matmul with fused bias add and GELU (tanh approximation):
     * C = GELU(op(A) * op(B) + bias).
     */
    public static LibraryTaskDescriptor ltMatmulGeluBiasFP16(int transa, int transb, int m, int n, int k, //
            float alpha, HalfFloatArray matrixA, int lda, HalfFloatArray matrixB, int ldb, //
            float beta, HalfFloatArray matrixC, int ldc, HalfFloatArray bias) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("ltMatmulGeluBiasFP16") //
                .withParameters(new Object[] { transa, transb, m, n, k, alpha, matrixA, lda, matrixB, ldb, beta, matrixC, ldc, bias }) //
                .withAccess(readOnlyExcept(14, 11, beta));
    }
}
