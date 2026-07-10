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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

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
}
