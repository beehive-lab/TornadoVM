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
package uk.ac.manchester.tornado.cusparse.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-cusparse (NVIDIA cuSPARSE generic-API sparse BLAS,
 * CSR / FP32). Descriptors reference device pointers so they are built per call;
 * the small external workspace is allocated on the Java side. Status codes are
 * {@code cusparseStatus_t} ordinals ({@code 0 == CUSPARSE_STATUS_SUCCESS}).
 */
final class CusparseNativeLib {

    private static boolean loaded = false;

    private CusparseNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-cusparse");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cusparse. Build TornadoVM with the CUDA backend: " + e.getMessage());
            }
        }
    }

    static native long cusparseCreateHandle();

    static native int cusparseSetStreamNative(long handle, long stream);

    static native void cusparseDestroyHandle(long handle);

    /** External buffer size (bytes) for the given SpMV; -1 on error. */
    static native long spmvBufferSize(long handle, int rows, int cols, int nnz, long dRow, long dCol, long dVal, long dX, long dY, float alpha, float beta);

    /** y = alpha*A*x + beta*y (A: rows x cols CSR). Returns cusparseStatus_t. */
    static native int spmv(long handle, int rows, int cols, int nnz, long dRow, long dCol, long dVal, long dX, long dY, float alpha, float beta, long workspace);

    /** External buffer size (bytes) for the given SpMM; -1 on error. */
    static native long spmmBufferSize(long handle, int rows, int k, int n, int nnz, long dRow, long dCol, long dVal, long dB, long dC, float alpha, float beta);

    /** C = alpha*A*B + beta*C (A: rows x k CSR; B: k x n, C: rows x n, row-major). */
    static native int spmm(long handle, int rows, int k, int n, int nnz, long dRow, long dCol, long dVal, long dB, long dC, float alpha, float beta, long workspace);

    static native long allocateDeviceMemory(long bytes);

    static native int freeDeviceMemory(long ptr);

    static native String statusString(int status);

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with cuSPARSE status: " + statusString(status));
        }
    }
}
