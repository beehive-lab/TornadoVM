#include <jni.h>
#include <cuda.h>

CUcontext **contexts = NULL;
int no_of_contexts = 0;
/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAContext
 * Method:    cuCtxCreate
 * Signature: (I)I
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAContext_cuCtxCreate
  (JNIEnv *env, jclass clazz, jint device_index) {
    if (contexts == NULL) {
        int no_of_devices;
        cuDeviceGetCount(&no_of_devices);
        contexts = malloc(no_of_devices * sizeof(CUcontext*));
    }

    CUdevice dev;
    cuDeviceGet(&dev, (int) device_index);

    CUcontext *ctx = malloc(sizeof(CUcontext));
    CUresult result = cuCtxCreate(ctx, CU_CTX_SCHED_YIELD, dev);
    contexts[device_index] = ctx; // For now I assume there is one context created for each device

    no_of_contexts++;

    return;
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

    if (ctx == NULL) {
        ctx = *contexts[(int) device_index];
    }

    CUresult result = cuCtxDestroy(ctx);

    free(contexts[(int) device_index]);
    no_of_contexts--;
    if (no_of_contexts == 0) {
        free(contexts);
    }

    return;
}