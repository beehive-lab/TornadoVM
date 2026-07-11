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
 * JNI bindings for uk.ac.manchester.tornado.cutensor.provider.CutensorNativeLib.
 *
 * Wrappers around the NVIDIA cuTENSOR v2 contraction API. A contraction is an
 * einsum-style tensor product: each operand's dimensions are labelled with
 * integer "modes", and modes shared by A and B but absent from C are summed
 * over. Tensors are ROW-MAJOR (last mode contiguous), matching TornadoVM's
 * native array layout - the packed strides are derived from the extents here.
 *
 * Per-shape state (tensor descriptors, the contraction plan, and its workspace
 * size) is built once in createContractionPlan and cached on the Java side, so
 * the workspace can be allocated before CUDA graph capture starts.
 */

#include <jni.h>
#include <cuda_runtime_api.h>
#include <cutensor.h>

#include <pthread.h>
#include <vector>

extern "C" {

// cutensorCreatePlan performs deep kernel-selection work that overflows the JVM
// main thread's ~1 MB stack (it runs on the TornadoVM interpreter thread). Run
// it on a dedicated pthread with a large stack so plan creation is robust
// regardless of the caller's stack size.
struct CreatePlanArgs {
    cutensorHandle_t handle;
    cutensorOperationDescriptor_t op;
    cutensorPlanPreference_t pref;
    uint64_t workspaceSize;
    cutensorPlan_t plan;
    cutensorStatus_t status;
};

static void *createPlanThread(void *arg) {
    CreatePlanArgs *a = (CreatePlanArgs *) arg;
    a->status = cutensorCreatePlan(a->handle, &a->plan, a->op, a->pref, a->workspaceSize);
    return nullptr;
}

static cutensorStatus_t createPlanLargeStack(cutensorHandle_t handle, cutensorPlan_t *plan, cutensorOperationDescriptor_t op, cutensorPlanPreference_t pref, uint64_t workspaceSize) {
    CreatePlanArgs args{handle, op, pref, workspaceSize, nullptr, CUTENSOR_STATUS_INTERNAL_ERROR};
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 64 * 1024 * 1024);
    pthread_t tid;
    if (pthread_create(&tid, &attr, createPlanThread, &args) != 0) {
        pthread_attr_destroy(&attr);
        // Fall back to the calling thread (may overflow, but preserves behaviour).
        return cutensorCreatePlan(handle, plan, op, pref, workspaceSize);
    }
    pthread_join(tid, nullptr);
    pthread_attr_destroy(&attr);
    *plan = args.plan;
    return args.status;
}

typedef struct cutensor_plan_s {
    cutensorPlan_t plan;
    uint64_t workspaceSize;
} cutensor_plan_t;

// TornadoVM array data starts 24 bytes past a >=256-byte-aligned base, so the
// guaranteed pointer alignment is 8 bytes.
static constexpr uint32_t kAlignment = 8;

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    cutensorCreateHandle
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_cutensorCreateHandle
        (JNIEnv *env, jclass clazz) {
    cutensorHandle_t handle;
    if (cutensorCreate(&handle) != CUTENSOR_STATUS_SUCCESS) {
        return 0;
    }
    return (jlong) handle;
}

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    cutensorDestroyHandle
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_cutensorDestroyHandle
        (JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) {
        cutensorDestroy((cutensorHandle_t) handle);
    }
}

// Row-major (last mode contiguous) packed strides for the given extents.
static std::vector<int64_t> rowMajorStrides(const std::vector<int64_t> &extent) {
    size_t n = extent.size();
    std::vector<int64_t> stride(n);
    int64_t acc = 1;
    for (size_t i = n; i-- > 0;) {
        stride[i] = acc;
        acc *= extent[i];
    }
    return stride;
}

static bool makeDescriptor(cutensorHandle_t handle, jlong *extentPtr, jint numModes, cutensorTensorDescriptor_t *desc) {
    std::vector<int64_t> extent(extentPtr, extentPtr + numModes);
    std::vector<int64_t> stride = rowMajorStrides(extent);
    return cutensorCreateTensorDescriptor(handle, desc, (uint32_t) numModes, extent.data(), stride.data(), CUTENSOR_R_32F, kAlignment) == CUTENSOR_STATUS_SUCCESS;
}

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    createContractionPlan
 * Signature: (J[I[J[I[J[I[J)J
 *
 * modes* are int32 mode labels; extent* are the matching int64 extents. Returns
 * an opaque cutensor_plan_t* (0 on failure). FP32 data, FP32 compute.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_createContractionPlan
        (JNIEnv *env, jclass clazz, jlong handlePtr,
         jintArray modesA, jlongArray extentA, jintArray modesB, jlongArray extentB, jintArray modesC, jlongArray extentC) {
    cutensorHandle_t handle = (cutensorHandle_t) handlePtr;

    jint nA = env->GetArrayLength(modesA);
    jint nB = env->GetArrayLength(modesB);
    jint nC = env->GetArrayLength(modesC);
    jint *mA = env->GetIntArrayElements(modesA, nullptr);
    jint *mB = env->GetIntArrayElements(modesB, nullptr);
    jint *mC = env->GetIntArrayElements(modesC, nullptr);
    jlong *eA = env->GetLongArrayElements(extentA, nullptr);
    jlong *eB = env->GetLongArrayElements(extentB, nullptr);
    jlong *eC = env->GetLongArrayElements(extentC, nullptr);

    cutensor_plan_t *holder = nullptr;
    cutensorTensorDescriptor_t dA = nullptr, dB = nullptr, dC = nullptr;
    cutensorOperationDescriptor_t op = nullptr;
    cutensorPlanPreference_t pref = nullptr;

    do {
        if (!makeDescriptor(handle, eA, nA, &dA)) break;
        if (!makeDescriptor(handle, eB, nB, &dB)) break;
        if (!makeDescriptor(handle, eC, nC, &dC)) break;

        // cutensor modes are int32; JNI jint is already 32-bit.
        if (cutensorCreateContraction(handle, &op,
                dA, (const int32_t *) mA, CUTENSOR_OP_IDENTITY,
                dB, (const int32_t *) mB, CUTENSOR_OP_IDENTITY,
                dC, (const int32_t *) mC, CUTENSOR_OP_IDENTITY,
                dC, (const int32_t *) mC,
                CUTENSOR_COMPUTE_DESC_32F) != CUTENSOR_STATUS_SUCCESS) {
            break;
        }
        if (cutensorCreatePlanPreference(handle, &pref, CUTENSOR_ALGO_DEFAULT, CUTENSOR_JIT_MODE_NONE) != CUTENSOR_STATUS_SUCCESS) {
            break;
        }
        uint64_t workspaceSize = 0;
        if (cutensorEstimateWorkspaceSize(handle, op, pref, CUTENSOR_WORKSPACE_DEFAULT, &workspaceSize) != CUTENSOR_STATUS_SUCCESS) {
            break;
        }
        cutensorPlan_t plan = nullptr;
        if (createPlanLargeStack(handle, &plan, op, pref, workspaceSize) != CUTENSOR_STATUS_SUCCESS) {
            break;
        }
        holder = new cutensor_plan_t{plan, workspaceSize};
    } while (false);

    if (pref) cutensorDestroyPlanPreference(pref);
    if (op) cutensorDestroyOperationDescriptor(op);
    if (dA) cutensorDestroyTensorDescriptor(dA);
    if (dB) cutensorDestroyTensorDescriptor(dB);
    if (dC) cutensorDestroyTensorDescriptor(dC);

    env->ReleaseIntArrayElements(modesA, mA, JNI_ABORT);
    env->ReleaseIntArrayElements(modesB, mB, JNI_ABORT);
    env->ReleaseIntArrayElements(modesC, mC, JNI_ABORT);
    env->ReleaseLongArrayElements(extentA, eA, JNI_ABORT);
    env->ReleaseLongArrayElements(extentB, eB, JNI_ABORT);
    env->ReleaseLongArrayElements(extentC, eC, JNI_ABORT);

    return (jlong) holder;
}

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    planWorkspaceBytes
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_planWorkspaceBytes
        (JNIEnv *env, jclass clazz, jlong planPtr) {
    if (planPtr == 0) return 0;
    return (jlong) ((cutensor_plan_t *) planPtr)->workspaceSize;
}

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    contract
 * Signature: (JJFJJFJJJ)I
 *
 * D = alpha * (A contracted-with B) + beta * C, with C and D aliased.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_contract
        (JNIEnv *env, jclass clazz, jlong handlePtr, jlong planPtr, jfloat alpha,
         jlong dA, jlong dB, jfloat beta, jlong dC, jlong workspace, jlong workspaceSize, jlong stream) {
    cutensor_plan_t *holder = (cutensor_plan_t *) planPtr;
    float a = alpha, b = beta;
    return (jint) cutensorContract((cutensorHandle_t) handlePtr, holder->plan,
            &a, (const void *) dA, (const void *) dB,
            &b, (const void *) dC, (void *) dC,
            (void *) workspace, (uint64_t) workspaceSize, (cudaStream_t) stream);
}

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    destroyPlan
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_destroyPlan
        (JNIEnv *env, jclass clazz, jlong planPtr) {
    if (planPtr != 0) {
        cutensor_plan_t *holder = (cutensor_plan_t *) planPtr;
        cutensorDestroyPlan(holder->plan);
        delete holder;
    }
}

JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_allocateDeviceMemory
        (JNIEnv *env, jclass clazz, jlong bytes) {
    if (bytes <= 0) return 0;
    void *ptr = nullptr;
    if (cudaMalloc(&ptr, (size_t) bytes) != cudaSuccess) return 0;
    return (jlong) ptr;
}

JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_freeDeviceMemory
        (JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr == 0) return (jint) cudaSuccess;
    return (jint) cudaFree((void *) ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib
 * Method:    statusString
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_cutensor_provider_CutensorNativeLib_statusString
        (JNIEnv *env, jclass clazz, jint status) {
    return env->NewStringUTF(cutensorGetErrorString((cutensorStatus_t) status));
}

} // extern "C"
