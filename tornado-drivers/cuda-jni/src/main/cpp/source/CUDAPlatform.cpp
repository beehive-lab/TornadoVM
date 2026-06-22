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

#include <jni.h>
#include "cuda_jni.h"

extern "C" {

/* OpenCL cl_platform_info values used by the cloned Java CUDAPlatformInfo enum. */
#define CL_PLATFORM_PROFILE    0x0900
#define CL_PLATFORM_VERSION    0x0901
#define CL_PLATFORM_NAME       0x0902
#define CL_PLATFORM_VENDOR     0x0903
#define CL_PLATFORM_EXTENSIONS 0x0904

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform
 * Method:    clGetPlatformInfo
 * Signature: (JI)Ljava/lang/String;
 *
 * Returns CUDA-oriented platform strings. The version string MUST be of the
 * form "<vendor> <major>.<minor>" because the Java side parses split(" ")[1].
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform_clGetPlatformInfo
        (JNIEnv *env, jobject obj, jlong platform_id, jint platform_info) {
    const char *value;
    switch (platform_info) {
        case CL_PLATFORM_PROFILE:
            value = "FULL_PROFILE";
            break;
        case CL_PLATFORM_VERSION: {
            int driver_version = 0;
            cuDriverGetVersion(&driver_version);
            int major = driver_version / 1000;
            int minor = (driver_version % 1000) / 10;
            static thread_local std::string version;
            version = "CUDA " + std::to_string(major) + "." + std::to_string(minor);
            return env->NewStringUTF(version.c_str());
        }
        case CL_PLATFORM_NAME:
            value = "NVIDIA CUDA";
            break;
        case CL_PLATFORM_VENDOR:
            value = "NVIDIA Corporation";
            break;
        case CL_PLATFORM_EXTENSIONS:
            value = "";
            break;
        default:
            value = "";
    }
    return env->NewStringUTF(value);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform
 * Method:    clGetDeviceCount
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform_clGetDeviceCount
        (JNIEnv *env, jobject obj, jlong platform_id, jlong device_type) {
    int count = 0;
    CUresult result = cuDeviceGetCount(&count);
    LOG_CUDA_AND_VALIDATE("cuDeviceGetCount", result);
    return (jint) count;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform
 * Method:    clGetDeviceIDs
 * Signature: (JJ[J)I
 *
 * Boxes each CUdevice into a cuda_device_t whose pointer is the opaque long.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform_clGetDeviceIDs
        (JNIEnv *env, jobject obj, jlong platform_id, jlong device_type, jlongArray array) {
    int count = 0;
    CUresult result = cuDeviceGetCount(&count);
    LOG_CUDA_AND_VALIDATE("cuDeviceGetCount", result);

    jsize len = env->GetArrayLength(array);
    int n = (count < len) ? count : len;

    std::vector<jlong> handles(n);
    for (int i = 0; i < n; i++) {
        cuda_device_t *box = new cuda_device_t();
        box->ordinal = i;
        result = cuDeviceGet(&box->device, i);
        LOG_CUDA_AND_VALIDATE("cuDeviceGet", result);
        handles[i] = (jlong) box;
    }
    if (n > 0) {
        env->SetLongArrayRegion(array, 0, n, handles.data());
    }
    return (jint) n;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform
 * Method:    clCreateContext
 * Signature: (J[J)J
 *
 * The OpenCL clone passes the platform id plus a device-id array; CUDA contexts
 * are per-device, so we create a context for the first device in the array. The
 * per-device split that OpenCL allows is handled at the Java level (one
 * CUDADeviceContext per device) and each pins this context via cuCtxSetCurrent.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAPlatform_clCreateContext
        (JNIEnv *env, jobject obj, jlong platform_id, jlongArray array) {
    jsize len = env->GetArrayLength(array);
    if (len <= 0) {
        return 0;
    }
    jlong first;
    env->GetLongArrayRegion(array, 0, 1, &first);
    cuda_device_t *dev = (cuda_device_t *) first;

    cuda_context_t *ctx = new cuda_context_t();
    ctx->device = dev->device;
    ctx->ordinal = dev->ordinal;

#if CUDA_VERSION >= 13000
    // CUDA 13 changed cuCtxCreate to cuCtxCreate_v4, inserting a
    // CUctxCreateParams* argument (NULL = default, no execution affinity / CIG).
    CUresult result = cuCtxCreate(&ctx->context, NULL, CU_CTX_SCHED_YIELD, dev->device);
#else
    CUresult result = cuCtxCreate(&ctx->context, CU_CTX_SCHED_YIELD, dev->device);
#endif
    LOG_CUDA_AND_VALIDATE("cuCtxCreate", result);
    if (result != CUDA_SUCCESS) {
        delete ctx;
        return 0;
    }
    return (jlong) ctx;
}

} // extern "C"
