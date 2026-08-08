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
package uk.ac.manchester.tornado.cublas.enums;

/**
 * Mirrors {@code cublasOperation_t}: the matrix transpose operation applied to
 * an operand of a cuBLAS call.
 */
public enum CuBlasOperation {

    /** The non-transpose operation is selected. */
    CUBLAS_OP_N(0),

    /** The transpose operation is selected. */
    CUBLAS_OP_T(1),

    /** The conjugate transpose operation is selected. */
    CUBLAS_OP_C(2);

    private final int operation;

    CuBlasOperation(int operation) {
        this.operation = operation;
    }

    public int operation() {
        return operation;
    }
}
