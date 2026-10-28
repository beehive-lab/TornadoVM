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
 * Mirrors {@code cudaDataType_t} (library_types.h): element types for the
 * cuBLAS Ex APIs.
 */
public enum CudaDataType {

    CUDA_R_16F(2), //
    CUDA_C_16F(6), //
    CUDA_R_16BF(14), //
    CUDA_C_16BF(15), //
    CUDA_R_32F(0), //
    CUDA_C_32F(4), //
    CUDA_R_64F(1), //
    CUDA_C_64F(5), //
    CUDA_R_8I(3), //
    CUDA_C_8I(7), //
    CUDA_R_8U(8), //
    CUDA_C_8U(9), //
    CUDA_R_32I(10), //
    CUDA_C_32I(11), //
    CUDA_R_8F_E4M3(28), //
    CUDA_R_8F_E5M2(29);

    private final int value;

    CudaDataType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
