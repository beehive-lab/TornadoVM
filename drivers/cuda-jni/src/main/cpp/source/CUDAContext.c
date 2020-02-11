#include <jni.h>
#include <cuda.h>

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    cuCtxCreate
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_cuCtxCreate
  (JNIEnv *env, jclass clazz, jint device_index) {
    CUdevice dev;
    cuDeviceGet(&dev, (int) device_index);

    CUcontext ctx;
    cuCtxCreate(&ctx, CU_CTX_SCHED_YIELD, dev);
    return (jint) 0;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    cuCtxDestroy
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_cuCtxDestroy
  (JNIEnv *env, jclass clazz, jint device_index) {
    CUcontext ctx;
    cuCtxGetCurrent(&ctx);
    cuCtxDestroy(ctx);
    return;
}