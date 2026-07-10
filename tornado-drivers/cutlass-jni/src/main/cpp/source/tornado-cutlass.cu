/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/*
 * JNI bindings for uk.ac.manchester.tornado.cutlass.provider.CutlassNativeLib.
 *
 * CUTLASS device-API GEMM kernels, instantiated ahead of time for the shapes
 * the hybrid API exposes. All operands are ROW-MAJOR (A: m x k, B: k x n,
 * C/D: m x n), matching TornadoVM's native array layout directly - no
 * column-major transposition dance. The kernels run on the CUstream that the
 * TornadoVM interpreter binds per execution plan, so CUTLASS launches order
 * naturally with the surrounding JIT-compiled tasks.
 */

#include <jni.h>
#include <cuda_runtime_api.h>

#include <cutlass/cutlass.h>
#include <cutlass/gemm/device/gemm_universal.h>
#include <cutlass/layout/matrix.h>
#include <cutlass/numeric_types.h>

extern "C" {

// -----------------------------------------------------------------------------
// FP32 SIMT GEMM (correctness baseline; alignment 1, no shape constraints).
// -----------------------------------------------------------------------------
using SgemmSimt = cutlass::gemm::device::GemmUniversal<
        float, cutlass::layout::RowMajor,
        float, cutlass::layout::RowMajor,
        float, cutlass::layout::RowMajor,
        float, cutlass::arch::OpClassSimt, cutlass::arch::Sm80>;

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    sgemmWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_sgemmWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    SgemmSimt::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm,
            {m, n, k},
            1,
            {1.0f, 0.0f},
            nullptr, nullptr, nullptr, nullptr,
            0, 0, 0, 0,
            k, n, n, n);
    return (jlong) SgemmSimt::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    sgemm
 * Signature: (IIIFJJFJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_sgemm
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jfloat alpha,
         jlong dA, jlong dB, jfloat beta, jlong dC, jlong workspace, jlong stream) {
    SgemmSimt::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm,
            {m, n, k},
            1,
            {alpha, beta},
            (void const *) dA, (void const *) dB, (void const *) dC, (void *) dC,
            0, 0, 0, 0,
            k, n, n, n);

    SgemmSimt gemm_op;
    cutlass::Status status = gemm_op.can_implement(args);
    if (status != cutlass::Status::kSuccess) {
        return (jint) status;
    }
    status = gemm_op.initialize(args, (void *) workspace, (cudaStream_t) stream);
    if (status != cutlass::Status::kSuccess) {
        return (jint) status;
    }
    status = gemm_op.run((cudaStream_t) stream);
    return (jint) status;
}

// -----------------------------------------------------------------------------
// Device-memory helpers (grow-only workspace managed on the Java side).
// -----------------------------------------------------------------------------

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    allocateDeviceMemory
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_allocateDeviceMemory
        (JNIEnv *env, jclass clazz, jlong bytes) {
    if (bytes <= 0) {
        return 0;
    }
    void *ptr = nullptr;
    if (cudaMalloc(&ptr, (size_t) bytes) != cudaSuccess) {
        return 0;
    }
    return (jlong) ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    freeDeviceMemory
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_freeDeviceMemory
        (JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == 0) {
        return (jint) cudaSuccess;
    }
    return (jint) cudaFree((void *) ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    statusString
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_statusString
        (JNIEnv *env, jclass clazz, jint status) {
    const char *s = cutlassGetStatusString((cutlass::Status) status);
    return env->NewStringUTF(s);
}

} // extern "C"
