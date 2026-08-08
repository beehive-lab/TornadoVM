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
 * Mirrors {@code cublasMath_t}: the math mode used by cuBLAS routines.
 */
public enum CuBlasMathMode {

    /** Highest-performance mode; uses Tensor Cores when possible. */
    CUBLAS_DEFAULT_MATH(0),

    /** Standardized arithmetic, no algorithmic optimizations. */
    CUBLAS_PEDANTIC_MATH(1),

    /** FP32 routines accelerated with TF32 Tensor Cores (Ampere and newer). */
    CUBLAS_TF32_TENSOR_OP_MATH(3);

    private final int value;

    CuBlasMathMode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
