#ifndef TORNADO_CUDA_MACROS_DATA_COPIES
#define TORNADO_CUDA_MACROS_DATA_COPIES

#define COPY_ARRAY_D_TO_H(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoH ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
\
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        return (*env)->ThrowNew(env, exception, "CUDA: Could not allocate memory. " + result); \
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
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoHAsync ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
\
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        return (*env)->ThrowNew(env, exception, "CUDA: Could not allocate memory. " + result); \
    } \
    result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length); \
 \
    (*env)->Set ## J_TYPE ## ArrayRegion(env, array, host_offset, length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    cuMemFreeHost(native_array); \
 \
    return (jint) -1; \
}

#define COPY_ARRAY_H_TO_D(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoD ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
 \
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        return (*env)->ThrowNew(env, exception, "CUDA: Could not allocate memory. " + result); \
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
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
 \
    void *native_array; \
    CUresult result = cuMemAllocHost(&native_array, (size_t) length); \
    if (result != 0) { \
        char *className = "uk/ac/manchester/tornado/drivers/cuda/mm/NativeMemoryException"; \
        jclass exception; \
        exception = (*env)->FindClass(env, className); \
        return (*env)->ThrowNew(env, exception, "CUDA: Could not allocate memory. " + result); \
    } \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length); \
 \
    cuMemFreeHost(native_array); \
 \
    return (jint) -1; \
}

#endif