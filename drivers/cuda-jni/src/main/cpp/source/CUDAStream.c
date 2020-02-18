#include <jni.h>
#include <cuda.h>

#include "CUDAModule.h"

//TODO: Make async calls async (create stream, destroy stream, manage events), DRY

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH
 * Signature: (JJJ[BJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoH__JJJ_3BJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jbyteArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    char *native_array = malloc((unsigned int) length);
    CUresult result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length);

    (*env)->SetIntArrayRegion(env, array, host_offset, length, native_array);

    free(native_array);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH
 * Signature: (JJJ[IJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoH__JJJ_3IJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jintArray array, jlong host_offset, jintArray wait_events) {

    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    int *native_array = (int *) malloc((unsigned int) length * sizeof(int));
    CUresult result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length);

    (*env)->SetIntArrayRegion(env, array, host_offset, length / sizeof(int), native_array);

    free(native_array);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[BJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoHAsync__JJJ_3BJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jbyteArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    char *native_array = malloc((unsigned int) length);
    CUresult result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length);

    (*env)->SetIntArrayRegion(env, array, host_offset, length, native_array);

    free(native_array);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[IJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoHAsync__JJJ_3IJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jintArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    int *native_array = (int *) malloc((unsigned int) length * sizeof(int));
    CUresult result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length);

    (*env)->SetIntArrayRegion(env, array, host_offset, length / sizeof(int), native_array);

    free(native_array);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[IJ[I)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoD__JJJ_3IJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jintArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    int *native_array = (int *) malloc((unsigned int) length * sizeof(int));
    (*env)->GetIntArrayRegion(env, array, host_offset / sizeof(int), length / sizeof(int), native_array);

    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length);

    free(native_array);

    return;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[BJ[I)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoD__JJJ_3BJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jbyteArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    char *native_array = malloc((unsigned int) length);
    (*env)->GetByteArrayRegion(env, array, host_offset, length, native_array);

    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length);

    free(native_array);

    return;
}


/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJ[BJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync__JJJ_3BJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jbyteArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    char *native_array = malloc((unsigned int) length);
    (*env)->GetByteArrayRegion(env, array, host_offset, length, native_array);

    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length);

    free(native_array);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJ[IJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync__JJJ_3IJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jintArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    int *native_array = (int *) malloc((unsigned int) length * sizeof(int));
    (*env)->GetIntArrayRegion(env, array, host_offset / sizeof(int), length / sizeof(int), native_array);

    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length);

    free(native_array);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    cuLaunchKernel
 * Signature: ([BLjava/lang/String;IIIIIIJ[B[B)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_cuLaunchKernel(
        JNIEnv *env,
        jclass clazz,
        jbyteArray module,
        jstring function_name,
        jint gridDimX, jint gridDimY, jint gridDimZ,
        jint blockDimX, jint blockDimY, jint blockDimZ,
        jlong sharedMemBytes,
        jbyteArray stream,
        jbyteArray args) {

    CUmodule native_module;
    array_to_module(env, &native_module, module);

    const char *native_function_name = (*env)->GetStringUTFChars(env, function_name, 0);
    CUfunction kernel;
    CUresult result = cuModuleGetFunction(&kernel, native_module, native_function_name);
    (*env)->ReleaseStringUTFChars(env, function_name, native_function_name);

    size_t arg_buffer_size = (*env)->GetArrayLength(env, args);
    char arg_buffer[arg_buffer_size];
    (*env)->GetByteArrayRegion(env, args, 0, arg_buffer_size, arg_buffer);


    void *arg_config[] = {
        CU_LAUNCH_PARAM_BUFFER_POINTER, arg_buffer,
        CU_LAUNCH_PARAM_BUFFER_SIZE,    &arg_buffer_size,
        CU_LAUNCH_PARAM_END
    };

    result = cuLaunchKernel(kernel,
            (unsigned int) gridDimX,  (unsigned int) gridDimY,  (unsigned int) gridDimZ,
            (unsigned int) blockDimX, (unsigned int) blockDimY, (unsigned int) blockDimZ,
            (unsigned int) sharedMemBytes, NULL,
            NULL,
            arg_config
    );

    result = cuCtxSynchronize();

    return (jint) -1;
}