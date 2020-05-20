#ifndef TORNADO_CUDA_MACROS_DATA_COPIES
#define TORNADO_CUDA_MACROS_DATA_COPIES

#define COPY_ARRAY_D_TO_H(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoH__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
\
    StagingAreaList *staging_list = get_first_free_staging_area(length); \
    CUresult result; \
    INSERT_EVENT(before_event) \
\
    result = cuMemcpyDtoHAsync(staging_list->staging_area, device_ptr, (size_t) length, stream); \
\
    INSERT_EVENT(after_event) \
    if (result != 0) { \
        printf("Failed to copy memory from device to host! (%d)\n", result); fflush(stdout); \
    } \
    if (cuEventQuery(after_event) != 0) cuEventSynchronize(after_event); \
 \
    (*env)->Set ## J_TYPE ## ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), staging_list->staging_area); \
    set_to_unused(stream, result, staging_list); \
 \
    return wrapper_from_events(env, &before_event, &after_event); \
} \
 \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayDtoHAsync__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    void *native_array = (*env)->GetPrimitiveArrayCritical(env, array, 0); \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
\
    CUresult result; \
    INSERT_EVENT(before_event) \
\
    result = cuMemcpyDtoHAsync(native_array + host_offset, device_ptr, (size_t) length, stream); \
 \
    INSERT_EVENT(after_event) \
    cuMemFreeHost(native_array); \
    (*env)->ReleasePrimitiveArrayCritical(env, array, native_array, 0); \
 \
    return wrapper_from_events(env, &before_event, &after_event); \
}

#define COPY_ARRAY_H_TO_D(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoD__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
 \
    StagingAreaList *staging_list = get_first_free_staging_area(length); \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), staging_list->staging_area); \
    CUresult result; \
    INSERT_EVENT(before_event) \
 \
    result = cuMemcpyHtoDAsync(device_ptr, staging_list->staging_area, (size_t) length, stream); \
 \
    INSERT_EVENT(after_event) \
    result = cuStreamAddCallback(stream, set_to_unused, staging_list, 0); \
    if (result != 0) { \
        printf("Failed to queue memory free! (%d)\n", result); fflush(stdout); \
    } \
    return wrapper_from_events(env, &before_event, &after_event); \
} \
 \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    StagingAreaList *staging_list = get_first_free_staging_area(length); \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), staging_list->staging_area); \
 \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
    CUresult result; \
    INSERT_EVENT(before_event) \
\
    result = cuMemcpyHtoDAsync(device_ptr, staging_list->staging_area, (size_t) length, stream); \
\
    INSERT_EVENT(after_event) \
 \
    result = cuStreamAddCallback(stream, set_to_unused, staging_list, 0); \
    if (result != 0) { \
        printf("Failed to queue memory free! (%d)\n", result); fflush(stdout); \
    } \
 \
    return wrapper_from_events(env, &before_event, &after_event); \
}

#endif