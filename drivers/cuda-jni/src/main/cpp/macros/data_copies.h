#ifndef TORNADO_CUDA_MACROS_DATA_COPIES
#define TORNADO_CUDA_MACROS_DATA_COPIES

#define COPY_ARRAY_D_TO_H(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoH__JJJ_3##SIG##J \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
\
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        (*env)->ThrowNew(env, exception, "CUDA: Could not allocate host memory. " + result); \
        return -1; \
    } \
    result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length); \
 \
    (*env)->Set ## J_TYPE ## ArrayRegion(env, array, host_offset, length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    cuMemFreeHost(native_array); \
 \
    return (jint) -1; \
} \
 \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoHAsync__JJJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
\
    void *native_array = (*env)->GetPrimitiveArrayCritical(env, array, 0); \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
    CUresult result = cuMemcpyDtoHAsync(&native_array[host_offset], start_ptr, (size_t) length, stream); \
 \
    cuMemFreeHost(native_array); \
    (*env)->ReleasePrimitiveArrayCritical(env, array, native_array, 0); \
 \
    return (jint) -1; \
}

#define COPY_ARRAY_H_TO_D(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoD__JJJ_3##SIG##J \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
 \
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        (*env)->ThrowNew(env, exception, "CUDA: Could not allocate host memory. " + result); \
        return; \
    } \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length); \
 \
    cuMemFreeHost(native_array); \
 \
    return; \
} \
 \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync__JJJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
 \
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        (*env)->ThrowNew(env, exception, "CUDA: Could not allocate host memory. " + result); \
        return -1; \
    } \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
    result = cuMemcpyHtoDAsync(start_ptr, native_array, (size_t) length, stream); \
 \
    cuMemFreeHost(native_array); \
 \
    return (jint) -1; \
}

#endif