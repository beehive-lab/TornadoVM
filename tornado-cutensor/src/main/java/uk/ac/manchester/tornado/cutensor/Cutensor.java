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
package uk.ac.manchester.tornado.cutensor;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Factory methods for NVIDIA cuTENSOR library tasks (FP32 tensor contractions).
 *
 * <pre>
 * // matmul as a contraction C[m,n] = sum_k A[m,k] * B[k,n]
 * taskGraph.libraryTask("gemm", Cutensor::cutensorContraction, m, n, k, a, b, c);
 * </pre>
 *
 * A contraction is an einsum-style tensor product: dimensions carry integer
 * mode labels and modes shared by A and B but absent from C are summed over.
 * All tensors are row-major (last index contiguous), matching the TornadoVM
 * native array layout. Per-shape descriptors, the contraction plan, and its
 * device workspace are created once and cached in the per-(device, execution
 * plan) context.
 */
public final class Cutensor {

    public static final String LIBRARY_NAME = "nvidia/cutensor";

    private Cutensor() {
    }

    private static Access[] inputsThenOutput(int numArgs, int outputIndex) {
        Access[] access = new Access[numArgs];
        Arrays.fill(access, Access.READ_ONLY);
        access[outputIndex] = Access.WRITE_ONLY;
        return access;
    }

    /**
     * Matrix product expressed as a contraction: {@code C[m,n] = sum_k A[m,k] * B[k,n]},
     * all row-major. Equivalent to SGEMM, but routed through cuTENSOR's
     * generalized-contraction engine.
     */
    public static LibraryTaskDescriptor cutensorContraction(int m, int n, int k, FloatArray a, FloatArray b, FloatArray c) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cutensorContraction") //
                .withParameters(new Object[] { m, n, k, a, b, c }) //
                .withAccess(inputsThenOutput(6, 5));
    }

    /**
     * Two-mode contraction {@code C[i,j] = sum_{k,l} A[i,k,l] * B[k,l,j]} - a
     * genuine tensor contraction over two shared modes, the kind cuBLAS cannot
     * express in a single call. All operands row-major: {@code A} is
     * {@code i x k x l}, {@code B} is {@code k x l x j}, {@code C} is
     * {@code i x j}.
     */
    public static LibraryTaskDescriptor cutensorContraction2(int i, int j, int k, int l, FloatArray a, FloatArray b, FloatArray c) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("cutensorContraction2") //
                .withParameters(new Object[] { i, j, k, l, a, b, c }) //
                .withAccess(inputsThenOutput(7, 6));
    }
}
