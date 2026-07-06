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

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADriver
 * Method:    registerCallback
 * Signature: ()Z
 *
 * Initialises the CUDA Driver API. The OpenCL clone calls registerCallback as
 * an opportunity to install an error callback; here we use it to run cuInit.
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADriver_registerCallback
        (JNIEnv *env, jclass clazz) {
    CUresult result = cuInit(0);
    LOG_CUDA_AND_VALIDATE("cuInit", result);
    return (result == CUDA_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADriver
 * Method:    clGetPlatformCount
 * Signature: ()I
 *
 * CUDA exposes a single (driver) platform; we report 1 if a device is present.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADriver_clGetPlatformCount
        (JNIEnv *env, jclass clazz) {
    CUresult result = cuInit(0);
    LOG_CUDA_AND_VALIDATE("cuInit", result);
    if (result != CUDA_SUCCESS) {
        return 0;
    }
    int count = 0;
    result = cuDeviceGetCount(&count);
    LOG_CUDA_AND_VALIDATE("cuDeviceGetCount", result);
    return (count > 0) ? 1 : 0;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADriver
 * Method:    clGetPlatformIDs
 * Signature: ([J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADriver_clGetPlatformIDs
        (JNIEnv *env, jclass clazz, jlongArray array) {
    jsize len = env->GetArrayLength(array);
    if (len <= 0) {
        return 0;
    }
    jlong handle = TORNADO_CUDA_PLATFORM_HANDLE;
    env->SetLongArrayRegion(array, 0, 1, &handle);
    return 1;
}

} // extern "C"
