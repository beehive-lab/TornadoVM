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
package uk.ac.manchester.tornado.cublas.provider;

/**
 * JNI bindings to the cuBLASLt part of libtornado-cublas. Plan-based: a plan
 * bundles the matmul descriptor (with epilogue), matrix layouts, and the
 * heuristic-selected algorithm for one problem shape; plans are cached per
 * (device, execution plan) context and replayed with {@link #ltExecutePlan}.
 */
final class CuBlasLtNativeLib {

    private CuBlasLtNativeLib() {
    }

    static native long ltCreate();

    static native void ltDestroy(long handle);

    /**
     * Creates a matmul plan (descriptors + heuristic algo). Returns 0 on
     * failure. Scalars are host floats: FP32-family compute types only.
     */
    static native long ltCreatePlan(long handle, int transa, int transb, int m, int n, int k, int lda, int ldb, int ldc, int aType, int bType, int cType, int computeType, int scaleType, int epilogue,
            long workspaceBytes);

    static native void ltDestroyPlan(long planPtr);

    static native int ltExecutePlan(long handle, long planPtr, long streamPtr, float alpha, long dA, long dB, float beta, long dC, long biasPtr, long workspacePtr, long workspaceBytes);
}
