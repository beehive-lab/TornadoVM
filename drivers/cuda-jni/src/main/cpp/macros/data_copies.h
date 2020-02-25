#ifndef TORNADO_CUDA_MACROS_DATA_COPIES
#define TORNADO_CUDA_MACROS_DATA_COPIES

#define COPY_ARRAY_D_TO_H_SYNC(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoH ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
\
    NATIVE_J_TYPE *native_array = (NATIVE_J_TYPE *) malloc((unsigned int) length * sizeof(NATIVE_J_TYPE)); \
    CUresult result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length); \
\
    (*env)->Set ## J_TYPE ## ArrayRegion(env, array, host_offset, length / sizeof(NATIVE_J_TYPE), native_array); \
\
    free(native_array); \
\
    return (jint) -1; \
} \

#define COPY_ARRAY_D_TO_H_ASYNC(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoHAsync ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
\
    NATIVE_J_TYPE *native_array = (NATIVE_J_TYPE *) malloc((unsigned int) length * sizeof(NATIVE_J_TYPE)); \
    CUresult result = cuMemcpyDtoH(native_array, start_ptr, (size_t) length); \
\
    (*env)->Set ## J_TYPE ## ArrayRegion(env, array, host_offset, length / sizeof(NATIVE_J_TYPE), native_array); \
\
    free(native_array); \
\
    return (jint) -1; \
}

#define COPY_ARRAY_H_TO_D_SYNC(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoD ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
 \
    NATIVE_J_TYPE *native_array = (NATIVE_J_TYPE *) malloc((unsigned int) length * sizeof(NATIVE_J_TYPE)); \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length); \
 \
    free(native_array); \
 \
    return; \
}

#define COPY_ARRAY_H_TO_D_ASYNC(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync ##SIG \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jintArray wait_events) { \
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset; \
 \
    NATIVE_J_TYPE *native_array = (NATIVE_J_TYPE *) malloc((unsigned int) length * sizeof(NATIVE_J_TYPE)); \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), native_array); \
 \
    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length); \
 \
    free(native_array); \
 \
    return (jint) -1; \
}

#endif