#include <jni.h>
#include <cuda.h>

#include "macros.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXPlatform
 * Method:    cuDeviceGetCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXPlatform_cuDeviceGetCount
  (JNIEnv *env, jclass clazz) {
    CUresult result;
    int device_count;
    CUDA_CHECK_ERROR("cuDeviceGetCount", cuDeviceGetCount(&device_count));
    return (jint) device_count;
}