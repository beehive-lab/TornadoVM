#include <jni.h>
#include <cuda.h>
#include <stdio.h>

#include "PTXContext.h"
#include "macros.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxCreate
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxCreate
  (JNIEnv *env, jclass clazz, jint device_index) {
    CUresult result;
    CUdevice dev;

    CUDA_CHECK_ERROR("cuDeviceGet", cuDeviceGet(&dev, (int) device_index));

    CUcontext *ctx = malloc(sizeof(CUcontext));
    CUDA_CHECK_ERROR("cuCtxCreate", cuCtxCreate(ctx, CU_CTX_SCHED_YIELD, dev));

    return (jlong) ctx;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxDestroy
 * Signature: (J)V
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxDestroy
  (JNIEnv *env, jclass clazz, jlong cuContext) {
    CUresult result;
    CUcontext *ctx = (CUcontext*) cuContext;

    CUDA_CHECK_ERROR("cuCtxDestroy", cuCtxDestroy(*ctx));

    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuMemAlloc
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuMemAlloc
  (JNIEnv *env, jclass clazz, jlong cuContext, jlong num_bytes) {
    CUresult result;
    CUcontext* ctx = (CUcontext*) cuContext;

    CUDA_CHECK_ERROR("cuCtxSetCurrent", cuCtxSetCurrent(*ctx));

    CUdeviceptr dev_ptr;
    CUDA_CHECK_ERROR("cuMemAlloc", cuMemAlloc(&dev_ptr, (size_t) num_bytes));

    if (result != 0) return (jlong) result;
    return (jlong) dev_ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuMemFree
 * Signature: (JJ)V
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuMemFree
  (JNIEnv *env, jclass clazz, jlong cuContext, jlong dev_ptr) {
    CUresult result;
    CUcontext* ctx = (CUcontext*) cuContext;
    CUDA_CHECK_ERROR("cuCtxSetCurrent", cuCtxSetCurrent(*ctx));

    CUDA_CHECK_ERROR("cuMemFree", cuMemFree((CUdeviceptr) dev_ptr));

    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxSetCurrent
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxSetCurrent
  (JNIEnv *env, jclass clazz, jlong cuContext) {
    CUresult result;
    CUcontext* ctx = (CUcontext*) cuContext;
    CUDA_CHECK_ERROR("cuCtxSetCurrent", cuCtxSetCurrent(*ctx));

    return (jlong) result;
}