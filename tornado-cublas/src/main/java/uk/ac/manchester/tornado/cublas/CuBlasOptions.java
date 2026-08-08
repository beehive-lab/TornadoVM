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

import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.cublas.enums.CuBlasMathMode;

/**
 * Per-call cuBLAS tuning options, attached to a library task via
 * {@link LibraryTaskDescriptor#withTuning(Object)}:
 *
 * <pre>
 * taskGraph.libraryTask("sgemm", (ta, tb, m, n, k, al, a, lda, b, ldb, be, c, ldc) ->
 *         CuBlas.cublasSgemm(ta, tb, m, n, k, al, a, lda, b, ldb, be, c, ldc)
 *                 .withTuning(new CuBlasOptions().withMathMode(CuBlasMathMode.CUBLAS_TF32_TENSOR_OP_MATH)),
 *         ...);
 * </pre>
 *
 * Convenience factories such as {@code CuBlas.cublasSgemmTF32} pre-attach common
 * configurations.
 */
public final class CuBlasOptions {

    private CuBlasMathMode mathMode = CuBlasMathMode.CUBLAS_DEFAULT_MATH;
    private long workspaceBytes = 0;

    /**
     * Math mode applied for this call (set before, restored to default after).
     */
    public CuBlasOptions withMathMode(CuBlasMathMode mathMode) {
        this.mathMode = mathMode;
        return this;
    }

    /**
     * Requests a user-owned device workspace of at least the given size for the
     * cuBLAS handle ({@code cublasSetWorkspace}). The workspace is allocated
     * lazily per (device, execution plan) context, grows monotonically across
     * calls, and is freed when the execution plan closes. Useful for
     * reproducibility across streams and required for device-launched graphs.
     */
    public CuBlasOptions withWorkspace(long workspaceBytes) {
        this.workspaceBytes = workspaceBytes;
        return this;
    }

    public CuBlasMathMode getMathMode() {
        return mathMode;
    }

    public long getWorkspaceBytes() {
        return workspaceBytes;
    }
}
