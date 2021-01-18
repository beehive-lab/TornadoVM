/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

#include <jni.h>
#include <cuda.h>

#include <iostream>
#include "PTXContext.h"
#include "ptx_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxCreate
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxCreate
  (JNIEnv *env, jclass clazz, jlong cuDevice) {
    CUdevice *dev = (CUdevice *) cuDevice;
    CUcontext *ctx = static_cast<CUcontext *>(malloc(sizeof(CUcontext)));
    CUresult result = cuCtxCreate(ctx, CU_CTX_SCHED_YIELD, *dev);
    LOG_PTX_AND_VALIDATE("cuCtxCreate", result);
    return (jlong) ctx;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxDestroy
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxDestroy
  (JNIEnv *env, jclass clazz, jlong cuContext) {
    CUcontext *ctx = (CUcontext*) cuContext;
    CUresult result = cuCtxDestroy(*ctx);
    LOG_PTX_AND_VALIDATE("cuCtxDestroy", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuMemAlloc
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuMemAlloc
  (JNIEnv *env, jclass clazz, jlong cuContext, jlong num_bytes) {
    CUcontext* ctx = (CUcontext*) cuContext;
    CUresult result = cuCtxSetCurrent(*ctx);
    LOG_PTX_AND_VALIDATE("cuCtxSetCurrent", result);

    CUdeviceptr dev_ptr;
    result = cuMemAlloc(&dev_ptr, (size_t) num_bytes);
    LOG_PTX_AND_VALIDATE("cuMemAlloc", result);
    if (result != CUDA_SUCCESS) return (jlong) result;
    return (jlong) dev_ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuMemFree
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuMemFree
  (JNIEnv *env, jclass clazz, jlong cuContext, jlong dev_ptr) {
    CUcontext* ctx = (CUcontext*) cuContext;
    CUresult result = cuCtxSetCurrent(*ctx);
    LOG_PTX_AND_VALIDATE("cuCtxSetCurrent", result);

    result = cuMemFree((CUdeviceptr) dev_ptr);
    LOG_PTX_AND_VALIDATE("cuMemFree", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxSetCurrent
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxSetCurrent
  (JNIEnv *env, jclass clazz, jlong cuContext) {
    CUcontext* ctx = (CUcontext*) cuContext;
    CUresult result = cuCtxSetCurrent(*ctx);
    LOG_PTX_AND_VALIDATE("cuCtxSetCurrent", result);
    return (jlong) result;
}