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
 * Mirrors {@code cublasComputeType_t}: compute precision for the cuBLAS Ex APIs.
 */
public enum CublasComputeType {

    /** Half precision compute (default for FP16). */
    CUBLAS_COMPUTE_16F(64), //
    CUBLAS_COMPUTE_16F_PEDANTIC(65), //
    /** Single precision compute (default for FP32). */
    CUBLAS_COMPUTE_32F(68), //
    CUBLAS_COMPUTE_32F_PEDANTIC(69), //
    /** FP32 with inputs down-converted to FP16 on Tensor Cores. */
    CUBLAS_COMPUTE_32F_FAST_16F(74), //
    /** FP32 with inputs down-converted to BF16 on Tensor Cores. */
    CUBLAS_COMPUTE_32F_FAST_16BF(75), //
    /** FP32 with inputs down-converted to TF32 on Tensor Cores. */
    CUBLAS_COMPUTE_32F_FAST_TF32(77), //
    CUBLAS_COMPUTE_64F(70), //
    CUBLAS_COMPUTE_64F_PEDANTIC(71), //
    CUBLAS_COMPUTE_32I(72), //
    CUBLAS_COMPUTE_32I_PEDANTIC(73);

    private final int value;

    CublasComputeType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
