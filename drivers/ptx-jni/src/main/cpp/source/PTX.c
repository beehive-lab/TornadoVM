#include <jni.h>
#include <cuda.h>

#include "macros.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTX
 * Method:    cuInit
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTX_cuInit
  (JNIEnv *env, jclass clazz) {

    CUresult result;
    CUDA_CHECK_ERROR("cuInit", cuInit(0));

    return (jlong) result;
}