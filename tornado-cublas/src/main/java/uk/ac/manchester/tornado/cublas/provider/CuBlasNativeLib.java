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

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-cublas. All device pointers are raw CUdeviceptr
 * values of TornadoVM-managed buffers; the cuBLAS handle is bound to the
 * TornadoVM execution stream via {@code cublasSetStream}, so calls are ordered
 * with the kernels and transfers of the same task graph.
 */
final class CuBlasNativeLib {

    private static boolean loaded = false;

    private CuBlasNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-cublas");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cublas. Build TornadoVM with the CUDA backend and ensure cuBLAS is installed: " + e.getMessage());
            }
        }
    }

    static native long cublasCreate();

    static native int cublasSetStream(long handle, long streamPtr);

    static native void cublasDestroy(long handle);

    static native int cublasSetMathMode(long handle, int mathMode);

    static native int cublasSetWorkspace(long handle, long workspacePtr, long workspaceBytes);

    /** Returns the device pointer, or 0 on allocation failure. */
    static native long allocateDeviceMemory(long bytes);

    static native void freeDeviceMemory(long ptr);

    static native int cublasSgemv(long handle, int trans, int m, int n, float alpha, long dA, int lda, long dX, int incx, float beta, long dY, int incy);

    static native int cublasSgemm(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int lda, long dB, int ldb, float beta, long dC, int ldc);

    /**
     * Generic mixed-precision GEMM ({@code cublasGemmEx}). Scalars are passed as
     * host floats, which is valid for the FP32-family compute types
     * (CUBLAS_COMPUTE_32F*); other compute types use different scalar types and
     * must not be dispatched through this entry point.
     */
    static native int cublasGemmEx(long handle, int transa, int transb, int m, int n, int k, float alpha, long dA, int aType, int lda, long dB, int bType, int ldb, float beta, long dC, int cType,
            int ldc, int computeType, int algo);

    /**
     * Decodes a {@code cublasStatus_t} value.
     */
    static String decodeStatus(int status) {
        return switch (status) {
            case 0 -> "CUBLAS_STATUS_SUCCESS";
            case 1 -> "CUBLAS_STATUS_NOT_INITIALIZED";
            case 3 -> "CUBLAS_STATUS_ALLOC_FAILED";
            case 7 -> "CUBLAS_STATUS_INVALID_VALUE";
            case 8 -> "CUBLAS_STATUS_ARCH_MISMATCH";
            case 11 -> "CUBLAS_STATUS_MAPPING_ERROR";
            case 13 -> "CUBLAS_STATUS_EXECUTION_FAILED";
            case 14 -> "CUBLAS_STATUS_INTERNAL_ERROR";
            case 15 -> "CUBLAS_STATUS_NOT_SUPPORTED";
            case 16 -> "CUBLAS_STATUS_LICENSE_ERROR";
            default -> "UNKNOWN_CUBLAS_STATUS (" + status + ")";
        };
    }

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with status: " + decodeStatus(status));
        }
    }
}
