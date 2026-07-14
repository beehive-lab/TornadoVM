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
package uk.ac.manchester.tornado.cutlass;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;

/**
 * Factory methods for NVIDIA CUTLASS library tasks (GEMM).
 *
 * <pre>
 * taskGraph.libraryTask("gemm", Cutlass::cutlassSgemm,
 *         m, n, k, alpha, a, b, beta, c);
 * </pre>
 *
 * All operands are ROW-MAJOR: {@code A} is {@code m x k}, {@code B} is
 * {@code k x n}, {@code C} is {@code m x n}, and the kernel computes
 * {@code C = alpha * A * B + beta * C}. This matches TornadoVM's native array
 * layout directly - there is no column-major transposition to reason about (as
 * there is with cuBLAS). The per-shape device workspace is prepared once and
 * cached in the per-(device, execution plan) context.
 */
public final class Cutlass {

    public static final String LIBRARY_NAME = "nvidia/cutlass";

    private Cutlass() {
    }

    /**
     * FP32 SIMT GEMM: {@code C = alpha * A * B + beta * C}, all operands
     * row-major ({@code A}: m x k, {@code B}: k x n, {@code C}: m x n). When
     * {@code beta != 0} the output is read back, so {@code C} is READ_WRITE;
     * otherwise it is WRITE_ONLY.
     */
    public static LibraryTaskDescriptor cutlassSgemm(int m, int n, int k, float alpha, FloatArray a, FloatArray b, float beta, FloatArray c) {
        Access[] access = new Access[8];
        Arrays.fill(access, Access.READ_ONLY);
        access[7] = (beta == 0.0f) ? Access.WRITE_ONLY : Access.READ_WRITE;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cutlassSgemm") //
                .withParameters(new Object[] { m, n, k, alpha, a, b, beta, c }) //
                .withAccess(access);
    }

    /**
     * The FP16 tensor-core kernels load operands in 8-byte (4-half) vectors, so
     * the row-major leading dimensions {@code k} (of A) and {@code n} (of B, C)
     * must be multiples of 4. {@code m} is unconstrained.
     */
    private static void checkHalfShape(int n, int k) {
        if ((k & 3) != 0 || (n & 3) != 0) {
            throw new TornadoRuntimeException("[ERROR] CUTLASS FP16 GEMM requires k and n to be multiples of 4 (8-byte operand alignment); got n=" + n + ", k=" + k);
        }
    }

    /**
     * FP16 tensor-core GEMM (FP32 accumulate): {@code D = alpha * A * B + beta * D},
     * all operands row-major half. Requires {@code k, n} multiples of 4.
     */
    public static LibraryTaskDescriptor cutlassHgemm(int m, int n, int k, float alpha, HalfFloatArray a, HalfFloatArray b, float beta, HalfFloatArray d) {
        checkHalfShape(n, k);
        Access[] access = new Access[8];
        Arrays.fill(access, Access.READ_ONLY);
        access[7] = (beta == 0.0f) ? Access.WRITE_ONLY : Access.READ_WRITE;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cutlassHgemm") //
                .withParameters(new Object[] { m, n, k, alpha, a, b, beta, d }) //
                .withAccess(access);
    }

    /**
     * Fused FP16 GEMM + bias + ReLU: {@code D = relu(A * B + bias)}, where
     * {@code bias} is a length-{@code n} row vector broadcast over all rows. All
     * operands row-major half; requires {@code k, n} multiples of 4.
     */
    public static LibraryTaskDescriptor cutlassGemmBiasRelu(int m, int n, int k, HalfFloatArray a, HalfFloatArray b, HalfFloatArray bias, HalfFloatArray d) {
        return biasActivation("cutlassGemmBiasRelu", m, n, k, a, b, bias, d);
    }

    /**
     * Fused FP16 GEMM + bias + GELU: {@code D = gelu(A * B + bias)}, same shape
     * and layout rules as {@link #cutlassGemmBiasRelu}.
     */
    public static LibraryTaskDescriptor cutlassGemmBiasGelu(int m, int n, int k, HalfFloatArray a, HalfFloatArray b, HalfFloatArray bias, HalfFloatArray d) {
        return biasActivation("cutlassGemmBiasGelu", m, n, k, a, b, bias, d);
    }

    private static LibraryTaskDescriptor biasActivation(String function, int m, int n, int k, HalfFloatArray a, HalfFloatArray b, HalfFloatArray bias, HalfFloatArray d) {
        checkHalfShape(n, k);
        // params: m, n, k, a, b, bias, d - only d is written
        Access[] access = new Access[7];
        Arrays.fill(access, Access.READ_ONLY);
        access[6] = Access.WRITE_ONLY;
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction(function) //
                .withParameters(new Object[] { m, n, k, a, b, bias, d }) //
                .withAccess(access);
    }
}
