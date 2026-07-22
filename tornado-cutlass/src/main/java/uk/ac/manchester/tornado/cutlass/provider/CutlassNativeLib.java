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
package uk.ac.manchester.tornado.cutlass.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * JNI bindings to libtornado-cutlass. CUTLASS device-API GEMM kernels are
 * instantiated ahead of time in the native library; each entry point takes raw
 * device pointers (row-major operands) and the CUstream the TornadoVM
 * interpreter bound for the execution plan. Status codes are
 * {@code cutlass::Status} ordinals ({@code 0 == kSuccess}).
 */
final class CutlassNativeLib {

    private static boolean loaded = false;

    private CutlassNativeLib() {
    }

    static synchronized void load() {
        if (!loaded) {
            try {
                System.loadLibrary("tornado-cutlass");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                throw new TornadoRuntimeException("[ERROR] Unable to load libtornado-cutlass. Build TornadoVM with the CUDA backend: " + e.getMessage());
            }
        }
    }

    /** Workspace size (bytes) CUTLASS needs for an m x n x k SGEMM; may be 0. */
    static native long sgemmWorkspace(int m, int n, int k);

    /** Row-major FP32 SIMT GEMM: C = alpha*A*B + beta*C. Returns cutlass::Status. */
    static native int sgemm(int m, int n, int k, float alpha, long dA, long dB, float beta, long dC, long workspace, long stream);

    static native long hgemmWorkspace(int m, int n, int k);

    /** Row-major FP16 tensor-core GEMM (FP32 accumulate): D = alpha*A*B + beta*D. */
    static native int hgemm(int m, int n, int k, float alpha, long dA, long dB, float beta, long dD, long workspace, long stream);

    static native long gemmBiasReluWorkspace(int m, int n, int k);

    /** Fused FP16 D = relu(A*B + bias); bias is a length-n row vector (broadcast). */
    static native int gemmBiasRelu(int m, int n, int k, long dA, long dB, long dBias, long dD, long workspace, long stream);

    static native long gemmBiasGeluWorkspace(int m, int n, int k);

    /** Fused FP16 D = gelu(A*B + bias); bias is a length-n row vector (broadcast). */
    static native int gemmBiasGelu(int m, int n, int k, long dA, long dB, long dBias, long dD, long workspace, long stream);

    static native long gemmBiasSiluWorkspace(int m, int n, int k);

    /** Fused FP16 D = silu(A*B + bias); bias is a length-n row vector (broadcast). */
    static native int gemmBiasSilu(int m, int n, int k, long dA, long dB, long dBias, long dD, long workspace, long stream);

    static native long gemmBiasSigmoidWorkspace(int m, int n, int k);

    /** Fused FP16 D = sigmoid(A*B + bias); bias is a length-n row vector (broadcast). */
    static native int gemmBiasSigmoid(int m, int n, int k, long dA, long dB, long dBias, long dD, long workspace, long stream);

    static native long gemmBiasTanhWorkspace(int m, int n, int k);

    /** Fused FP16 D = tanh(A*B + bias); bias is a length-n row vector (broadcast). */
    static native int gemmBiasTanh(int m, int n, int k, long dA, long dB, long dBias, long dD, long workspace, long stream);

    static native long gemmBiasHardSwishWorkspace(int m, int n, int k);

    /** Fused FP16 D = hardswish(A*B + bias); bias is a length-n row vector (broadcast). */
    static native int gemmBiasHardSwish(int m, int n, int k, long dA, long dB, long dBias, long dD, long workspace, long stream);

    static native long hgemmBatchedWorkspace(int m, int n, int k, int batchCount);

    /** Strided-batched FP16 tensor-core GEMM: C[i] = alpha*A[i]*B[i] + beta*C[i], i in [0,batchCount). */
    static native int hgemmBatched(int m, int n, int k, float alpha, long dA, long dB, float beta, long dC, int batchCount, long workspace, long stream);

    static native long allocateDeviceMemory(long bytes);

    static native int freeDeviceMemory(long ptr);

    /** Maps a {@code cutlass::Status} ordinal to its name via the native runtime. */
    static native String statusString(int status);

    static void checkStatus(int status, String function) {
        if (status != 0) {
            throw new TornadoRuntimeException("[ERROR] " + function + " failed with CUTLASS status: " + statusString(status));
        }
    }
}
