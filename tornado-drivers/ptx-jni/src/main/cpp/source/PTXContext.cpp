/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
    #if (__CUDACC_VER_MAJOR__ >= 13) || (CUDA_VERSION >= 13000)
        /* CUDA 13+, ctxCreateParams is left empty since the documentation for it is not
           informative at this point. Thus it is left empty as per the latest CUDA examples.
        */
        CUctxCreateParams ctxCreateParams = {};
        CUresult result = cuCtxCreate(ctx, &ctxCreateParams, CU_CTX_SCHED_YIELD, *dev);
    #else
        // CUDA <= 12
        CUresult result = cuCtxCreate(ctx, CU_CTX_SCHED_YIELD, *dev);
    #endif
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
    CUresult result =


    cuCtxSetCurrent(*ctx);
    LOG_PTX_AND_VALIDATE("cuCtxSetCurrent", result);
    return (jlong) result;
}