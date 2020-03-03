#include <jni.h>
#include <cuda.h>
#include <stdio.h>

jbyteArray from_module(JNIEnv *env, CUmodule *module) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUmodule));

    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUmodule), (void *) module);
    return array;
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
 * Method:    cuFuncGetAttribute
 * Signature: (Ljava/lang/String;I[B)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAModule_cuFuncGetAttribute
  (JNIEnv *env, jclass clazz, jstring func_name, jint attribute, jbyteArray module) {
    CUmodule native_module;
    array_to_module(env, &native_module, module);

    const char *native_function_name = (*env)->GetStringUTFChars(env, func_name, 0);
    CUfunction kernel;
    CUresult result = cuModuleGetFunction(&kernel, native_module, native_function_name);
    (*env)->ReleaseStringUTFChars(env, func_name, native_function_name);

    int return_value;
    result = cuFuncGetAttribute(&return_value, (CUfunction_attribute) attribute, kernel);

    return (jint) return_value;
}