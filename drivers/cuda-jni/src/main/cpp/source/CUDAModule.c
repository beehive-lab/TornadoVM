#include <jni.h>
#include <cuda.h>

void to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper) {
    char *module = (char *) module_ptr;
    (*env)->GetByteArrayRegion(env, javaWrapper, 0, sizeof(CUmodule), module);
}

jbyteArray from_module(JNIEnv *env, CUmodule *module) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUmodule));

    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUmodule), (char *) module);
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

    return from_module(env, &module);
}