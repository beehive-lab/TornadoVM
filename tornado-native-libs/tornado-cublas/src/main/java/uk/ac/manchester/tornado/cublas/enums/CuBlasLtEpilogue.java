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
 * Mirrors {@code cublasLtEpilogue_t}: the operation fused into the tail of a
 * cuBLASLt matmul.
 */
public enum CuBlasLtEpilogue {

    /** No epilogue: D = alpha * op(A) * op(B) + beta * C. */
    CUBLASLT_EPILOGUE_DEFAULT(1),

    /** ReLU applied to the result. */
    CUBLASLT_EPILOGUE_RELU(2),

    /** Bias vector (broadcast over columns) added to the result. */
    CUBLASLT_EPILOGUE_BIAS(4),

    /** Bias added, then ReLU. */
    CUBLASLT_EPILOGUE_RELU_BIAS(6),

    /** GELU (tanh approximation) applied to the result. */
    CUBLASLT_EPILOGUE_GELU(32),

    /** Bias added, then GELU (tanh approximation). */
    CUBLASLT_EPILOGUE_GELU_BIAS(36);

    private final int value;

    CuBlasLtEpilogue(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
