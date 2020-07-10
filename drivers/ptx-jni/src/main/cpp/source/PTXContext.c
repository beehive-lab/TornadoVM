#include <jni.h>
#include <cuda.h>
#include <stdio.h>

#include "PTXContext.h"

CUcontext **g_contexts = NULL;
int g_contexts_length = 0;

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxCreate
 * Signature: (I)I
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxCreate
  (JNIEnv *env, jclass clazz, jint device_index) {
    if (g_contexts == NULL) {
        int no_of_devices;
        cuDeviceGetCount(&no_of_devices);
        g_contexts = malloc(no_of_devices * sizeof(CUcontext*));
    }

    CUdevice dev;
    cuDeviceGet(&dev, (int) device_index);

    CUcontext *ctx = malloc(sizeof(CUcontext));
    CUresult result = cuCtxCreate(ctx, CU_CTX_SCHED_YIELD, dev);
    g_contexts[device_index] = ctx; // I assume there is one context created for each device

    g_contexts_length++;

    return;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxDestroy
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxDestroy
  (JNIEnv *env, jclass clazz, jint device_index) {
    CUcontext ctx;
    cuCtxGetCurrent(&ctx);

    if (ctx == NULL) {
        ctx = *g_contexts[(int) device_index];
    }

    CUresult result = cuCtxDestroy(ctx);

    free(g_contexts[(int) device_index]);
    g_contexts_length--;
    if (g_contexts_length == 0) {
        free(g_contexts);
    }

    return;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuMemAlloc
 * Signature: (IJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuMemAlloc
  (JNIEnv *env, jclass clazz, jint device_index, jlong num_bytes) {
    cuCtxSetCurrent(*g_contexts[(int) device_index]);

    CUdeviceptr dev_ptr;
    CUresult result = cuMemAlloc(&dev_ptr, (size_t) num_bytes);

    if (result != 0) return (jlong) -1;
    return (jlong) dev_ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuMemFree
 * Signature: (IJ)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuMemFree
  (JNIEnv *env, jclass clazz, jint device_index, jlong dev_ptr) {
    cuCtxSetCurrent(*g_contexts[(int) device_index]);

    CUresult result = cuMemFree((CUdeviceptr) dev_ptr);

    return;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXContext
 * Method:    cuCtxSetCurrent
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXContext_cuCtxSetCurrent
  (JNIEnv *env, jclass clazz, jint device_index) {
    CUcontext ctx;
    cuCtxGetCurrent(&ctx);

    if (ctx == NULL) {
        ctx = *g_contexts[(int) device_index];
        cuCtxSetCurrent(ctx);
    }

    return;
}