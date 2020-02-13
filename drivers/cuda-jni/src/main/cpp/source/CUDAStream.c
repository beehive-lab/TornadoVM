#include <jni.h>
#include <cuda.h>

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJ[BJ[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_writeArrayHtoDAsync__JJJ_3BJ_3I
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong offset, jlong length, jbyteArray array, jlong host_offset, jintArray wait_events) {
    CUdeviceptr start_ptr = (CUdeviceptr) device_ptr + (unsigned int) offset;

    char native_array[(unsigned int) length];
    (*env)->GetByteArrayRegion(env, array, host_offset, length, native_array);

    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length);

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

    int native_array[(unsigned int) length / sizeof(int)];
    (*env)->GetIntArrayRegion(env, array, host_offset / sizeof(int), length / sizeof(int), native_array);

    CUresult result = cuMemcpyHtoD(start_ptr, native_array, (size_t) length);

    return (jint) -1;
}