#include <jni.h>
#include <cuda.h>
#include <stdio.h>

#include "CUDAModule.h"

#define SUB_OPTIMAL_MAX_DISTANCE 0.1

jbyteArray from_module(JNIEnv *env, CUmodule *module) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUmodule));

    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUmodule), (void *) module);
    return array;
}

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper) {
    (*env)->GetByteArrayRegion(env, javaWrapper, 0, sizeof(CUmodule), (void *) module_ptr);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAModule_cuModuleLoadData
  (JNIEnv *env, jclass clazz, jbyteArray source) {
    size_t ptx_length = (*env)->GetArrayLength(env, source);
    char ptx[ptx_length + 1];
    (*env)->GetByteArrayRegion(env, source, 0, ptx_length, ptx);
    ptx[ptx_length] = 0; // Make sure string terminates with a 0

    CUmodule module;
    CUresult result = cuModuleLoadData(&module, ptx);

    if (result != 0) {
        printf("PTX to cubin JIT compilation failed! (%d)\n", result);
        fflush(stdout);
        jbyteArray error_array = (*env)->NewByteArray(env, 0);
        return error_array;
    }

    return from_module(env, &module);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAModule
 * Method:    calcMaximalBlockSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAModule_calcMaximalBlockSize
  (JNIEnv *env, jclass clazz, jbyteArray module_wrapper, jstring func_name) {
    CUmodule module;
    array_to_module(env, &module, module_wrapper);

    const char *native_function_name = (*env)->GetStringUTFChars(env, func_name, 0);
    CUfunction kernel;
    CUresult result = cuModuleGetFunction(&kernel, module, native_function_name);
    (*env)->ReleaseStringUTFChars(env, func_name, native_function_name);

    int min_grid_size;
    int block_size;
    cuOccupancyMaxPotentialBlockSize (&min_grid_size, &block_size, kernel, 0, 0, 0);
    return block_size;
}