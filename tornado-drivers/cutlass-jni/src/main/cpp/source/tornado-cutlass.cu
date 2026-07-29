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
#include <cutlass/gemm/gemm.h>
#include <cutlass/layout/matrix.h>
#include <cutlass/numeric_types.h>
#include <cutlass/bfloat16.h>
#include <cutlass/epilogue/thread/linear_combination.h>
#include <cutlass/epilogue/thread/linear_combination_relu.h>
#include <cutlass/epilogue/thread/linear_combination_gelu.h>
#include <cutlass/epilogue/thread/linear_combination_silu.h>
#include <cutlass/epilogue/thread/linear_combination_sigmoid.h>
#include <cutlass/epilogue/thread/linear_combination_hardswish.h>
#include <cutlass/epilogue/thread/linear_combination_generic.h>
#include <cutlass/epilogue/thread/activation.h>
#include <cutlass/gemm/threadblock/threadblock_swizzle.h>

// Shared driver: can_implement -> initialize(workspace,stream) -> run(stream).
// Returns the cutlass::Status ordinal of the first non-success step.
template <typename Gemm>
static int runGemm(typename Gemm::Arguments const &args, void *workspace, cudaStream_t stream) {
    Gemm op;
    cutlass::Status status = op.can_implement(args);
    if (status != cutlass::Status::kSuccess) {
        return (int) status;
    }
    status = op.initialize(args, workspace, stream);
    if (status != cutlass::Status::kSuccess) {
        return (int) status;
    }
    return (int) op.run(stream);
}

// -----------------------------------------------------------------------------
// FP16 tensor-core GEMM types (Ampere MMA, runs on sm_80+ incl. Ada sm_89).
//
// Operand alignment is 4 elements (8 bytes): TornadoVM device pointers are the
// array base plus a 24-byte header, so only 8-byte alignment is guaranteed. The
// default 128-bit (8-half) CUTLASS alignment would be rejected. The 4-element
// alignment implies k and n must be multiples of 4 (enforced in the Java
// factory). Accumulation is FP32; output is FP16. These are alias/function
// templates, so they live outside the extern "C" block.
// -----------------------------------------------------------------------------
using ElementHalf = cutlass::half_t;
using ElementAccum = float;
using ElementCompute = float;
static constexpr int kAlign = 4;

using ThreadblockShape = cutlass::gemm::GemmShape<128, 128, 32>;
using WarpShape = cutlass::gemm::GemmShape<64, 64, 32>;
using InstructionShape = cutlass::gemm::GemmShape<16, 8, 16>;
using Swizzle = cutlass::gemm::threadblock::GemmIdentityThreadblockSwizzle<>;
static constexpr int kStages = 3;

template <typename EpilogueOp>
using HalfTensorOpGemm = cutlass::gemm::device::GemmUniversal<
        ElementHalf, cutlass::layout::RowMajor,
        ElementHalf, cutlass::layout::RowMajor,
        ElementHalf, cutlass::layout::RowMajor,
        ElementAccum, cutlass::arch::OpClassTensorOp, cutlass::arch::Sm80,
        ThreadblockShape, WarpShape, InstructionShape,
        EpilogueOp, Swizzle, kStages, kAlign, kAlign>;

using HgemmTensor = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombination<ElementHalf, kAlign, ElementAccum, ElementCompute>>;
using GemmBiasRelu = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombinationRelu<ElementHalf, kAlign, ElementAccum, ElementCompute>>;
// BF16 tensor-core GEMM (FP32 accumulate). Same MMA shapes and 4-element (8-byte)
// operand alignment as the FP16 path - bfloat16 is also 2 bytes, so the k/n
// multiple-of-4 constraint is identical. BF16 keeps FP32's exponent range and is
// the de-facto LLM datatype; Ampere (sm_80) and later run it on the tensor cores
// at FP16 throughput.
using ElementBF16 = cutlass::bfloat16_t;
using BF16TensorOpGemm = cutlass::gemm::device::GemmUniversal<
        ElementBF16, cutlass::layout::RowMajor,
        ElementBF16, cutlass::layout::RowMajor,
        ElementBF16, cutlass::layout::RowMajor,
        ElementAccum, cutlass::arch::OpClassTensorOp, cutlass::arch::Sm80,
        ThreadblockShape, WarpShape, InstructionShape,
        cutlass::epilogue::thread::LinearCombination<ElementBF16, kAlign, ElementAccum, ElementCompute>,
        Swizzle, kStages, kAlign, kAlign>;

using GemmBiasGelu = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombinationGELU<ElementHalf, kAlign, ElementAccum, ElementCompute>>;
using GemmBiasSilu = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombinationSilu<ElementHalf, kAlign, ElementAccum, ElementCompute>>;
using GemmBiasSigmoid = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombinationSigmoid<ElementHalf, kAlign, ElementAccum, ElementCompute>>;
using GemmBiasTanh = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombinationGeneric<
                cutlass::epilogue::thread::Tanh, ElementHalf, kAlign, ElementAccum, ElementCompute>>;
using GemmBiasHardSwish = HalfTensorOpGemm<
        cutlass::epilogue::thread::LinearCombinationHardSwish<ElementHalf, kAlign, ElementAccum, ElementCompute>>;

// Fused GEMM + bias + activation. The length-n bias vector is supplied as the C
// source operand with ldc = 0 (row broadcast): every output row reads the same
// bias, so the epilogue computes act(alpha*A*B + bias) with alpha = beta = 1.
template <typename Gemm>
static int gemmBiasAct(int m, int n, int k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    typename Gemm::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            (void const *) dA, (void const *) dB, (void const *) dBias, (void *) dD,
            0, 0, 0, 0, k, n, 0, n);
    return runGemm<Gemm>(args, (void *) workspace, (cudaStream_t) stream);
}

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
// FP16 tensor-core GEMM entry points (types declared above, outside extern "C").
// -----------------------------------------------------------------------------

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    hgemmWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_hgemmWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    HgemmTensor::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 0.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, n, n);
    return (jlong) HgemmTensor::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    hgemm
 * Signature: (IIIFJJFJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_hgemm
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jfloat alpha,
         jlong dA, jlong dB, jfloat beta, jlong dC, jlong workspace, jlong stream) {
    HgemmTensor::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {alpha, beta},
            (void const *) dA, (void const *) dB, (void const *) dC, (void *) dC,
            0, 0, 0, 0, k, n, n, n);
    return runGemm<HgemmTensor>(args, (void *) workspace, (cudaStream_t) stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    bgemmWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_bgemmWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    BF16TensorOpGemm::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 0.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, n, n);
    return (jlong) BF16TensorOpGemm::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    bgemm
 * Signature: (IIIFJJFJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_bgemm
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jfloat alpha,
         jlong dA, jlong dB, jfloat beta, jlong dC, jlong workspace, jlong stream) {
    BF16TensorOpGemm::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {alpha, beta},
            (void const *) dA, (void const *) dB, (void const *) dC, (void *) dC,
            0, 0, 0, 0, k, n, n, n);
    return runGemm<BF16TensorOpGemm>(args, (void *) workspace, (cudaStream_t) stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasReluWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasReluWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    GemmBiasRelu::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, 0, n);
    return (jlong) GemmBiasRelu::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasRelu
 * Signature: (IIIJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasRelu
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    return gemmBiasAct<GemmBiasRelu>(m, n, k, dA, dB, dBias, dD, workspace, stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasGeluWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasGeluWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    GemmBiasGelu::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, 0, n);
    return (jlong) GemmBiasGelu::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasGelu
 * Signature: (IIIJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasGelu
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    return gemmBiasAct<GemmBiasGelu>(m, n, k, dA, dB, dBias, dD, workspace, stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasSiluWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasSiluWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    GemmBiasSilu::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, 0, n);
    return (jlong) GemmBiasSilu::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasSilu
 * Signature: (IIIJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasSilu
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    return gemmBiasAct<GemmBiasSilu>(m, n, k, dA, dB, dBias, dD, workspace, stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasSigmoidWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasSigmoidWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    GemmBiasSigmoid::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, 0, n);
    return (jlong) GemmBiasSigmoid::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasSigmoid
 * Signature: (IIIJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasSigmoid
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    return gemmBiasAct<GemmBiasSigmoid>(m, n, k, dA, dB, dBias, dD, workspace, stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasTanhWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasTanhWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    GemmBiasTanh::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, 0, n);
    return (jlong) GemmBiasTanh::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasTanh
 * Signature: (IIIJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasTanh
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    return gemmBiasAct<GemmBiasTanh>(m, n, k, dA, dB, dBias, dD, workspace, stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasHardSwishWorkspace
 * Signature: (III)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasHardSwishWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k) {
    GemmBiasHardSwish::Arguments args(
            cutlass::gemm::GemmUniversalMode::kGemm, {m, n, k}, 1, {1.0f, 1.0f},
            nullptr, nullptr, nullptr, nullptr, 0, 0, 0, 0, k, n, 0, n);
    return (jlong) GemmBiasHardSwish::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    gemmBiasHardSwish
 * Signature: (IIIJJJJJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_gemmBiasHardSwish
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jlong dA, jlong dB, jlong dBias, jlong dD, jlong workspace, jlong stream) {
    return gemmBiasAct<GemmBiasHardSwish>(m, n, k, dA, dB, dBias, dD, workspace, stream);
}

// -----------------------------------------------------------------------------
// Batched FP16 tensor-core GEMM (strided): batchCount independent m x n x k
// GEMMs packed contiguously (batch strides m*k, k*n, m*n). Reuses the FP16
// GemmUniversal kernel in kBatched mode - the core primitive behind batched
// linear layers and multi-head attention projections.
// -----------------------------------------------------------------------------

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    hgemmBatchedWorkspace
 * Signature: (IIII)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_hgemmBatchedWorkspace
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jint batchCount) {
    HgemmTensor::Arguments args(
            cutlass::gemm::GemmUniversalMode::kBatched, {m, n, k}, batchCount, {1.0f, 0.0f},
            nullptr, nullptr, nullptr, nullptr,
            (int64_t) m * k, (int64_t) k * n, (int64_t) m * n, (int64_t) m * n, k, n, n, n);
    return (jlong) HgemmTensor::get_workspace_size(args);
}

/*
 * Class:     uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib
 * Method:    hgemmBatched
 * Signature: (IIIFJJFJIJJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutlass_provider_CutlassNativeLib_hgemmBatched
        (JNIEnv *env, jclass clazz, jint m, jint n, jint k, jfloat alpha,
         jlong dA, jlong dB, jfloat beta, jlong dC, jint batchCount, jlong workspace, jlong stream) {
    HgemmTensor::Arguments args(
            cutlass::gemm::GemmUniversalMode::kBatched, {m, n, k}, batchCount, {alpha, beta},
            (void const *) dA, (void const *) dB, (void const *) dC, (void *) dC,
            (int64_t) m * k, (int64_t) k * n, (int64_t) m * n, (int64_t) m * n, k, n, n, n);
    return runGemm<HgemmTensor>(args, (void *) workspace, (cudaStream_t) stream);
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
