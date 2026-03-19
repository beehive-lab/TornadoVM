/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <jni.h>
#include <cuda.h>

#include <iostream>
#include <cstring>
#include "PTXStream.h"
#include "PTXModule.h"
#include "PTXEvent.h"
#include "ptx_utils.h"
#include "ptx_log.h"



jobjectArray transferFromDeviceToHost(
        JNIEnv *env,
        jclass javaClass,
        jlong devicePtr,
        jlong length,
        jbyteArray hostArray,
        jlong hostOffset,
        jbyteArray streamWrapper
) {
    CUresult result;
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, streamWrapper);
    record_events_create(&beforeEvent, &afterEvent);
    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    record_event(&beforeEvent, &stream);
    result = cuMemcpyDtoHAsync(&buffer[hostOffset], devicePtr, (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHAsync", result);
    record_event(&afterEvent, &stream);
    result = cuStreamSynchronize(stream);
    LOG_PTX_AND_VALIDATE("cuStreamSynchronize", result);
    env->ReleasePrimitiveArrayCritical(hostArray, buffer, 0);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3BJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jbyteArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, array, host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3SJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jshortArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3CJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jcharArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3IJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jintArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3JJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jlongArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3FJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jfloatArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3DJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jdoubleArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3BJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jbyteArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, array, hostOffset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3SJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jshortArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, reinterpret_cast<jbyteArray>(array), hostOffset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3CJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jcharArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, reinterpret_cast<jbyteArray>(array), hostOffset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3IJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jintArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, reinterpret_cast<jbyteArray>(array), hostOffset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3JJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jlongArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, reinterpret_cast<jbyteArray>(array), hostOffset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3FJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jfloatArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, reinterpret_cast<jbyteArray>(array), hostOffset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3DJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jdoubleArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    return transferFromDeviceToHost(env, klass, devicePtr, length, reinterpret_cast<jbyteArray>(array), hostOffset, stream_wrapper);
}

jobjectArray transferFromHostToDevice(JNIEnv* env,
                                      jclass javaClass,
                                      jlong devicePtr,
                                      jlong length,
                                      jbyteArray hostArray,
                                      jlong hostOffset,
                                      jbyteArray stream_wrapper
                                      ) {
    CUresult result;
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    record_event(&beforeEvent, &stream);
    result = cuMemcpyHtoDAsync(devicePtr, &buffer[hostOffset], (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDAsync", result);
    record_event(&afterEvent, &stream);
    result = cuStreamSynchronize(stream);
    LOG_PTX_AND_VALIDATE("cuStreamSynchronize", result);
    env->ReleasePrimitiveArrayCritical(hostArray, buffer, JNI_ABORT);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Staged async H2D transfer via a per-stream pinned buffer.
 *
 * 1. cuStreamSynchronize  – ensures the staging buffer is no longer being read
 *    by the previous in-flight DMA before we overwrite it.
 * 2. GetPrimitiveArrayCritical / memcpy  – copies the JVM array into the pinned
 *    staging region; the JVM critical section is released immediately after.
 * 3. cuMemcpyHtoDAsync  – enqueues a truly async DMA from pinned staging to the
 *    device; returns to the caller without blocking.
 */
static jobjectArray stagedTransferFromHostToDevice(JNIEnv *env,
                                                   jlong devicePtr,
                                                   jlong length,
                                                   jbyteArray hostArray,
                                                   jlong hostOffset,
                                                   jlong stagingPtr,
                                                   jbyteArray stream_wrapper
                                                   ) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);

    CUresult result = cuStreamSynchronize(stream);
    LOG_PTX_AND_VALIDATE("cuStreamSynchronize (stage guard)", result);

    jbyte *buffer = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(hostArray, NULL));
    memcpy(reinterpret_cast<void *>(stagingPtr), &buffer[hostOffset], (size_t) length);
    env->ReleasePrimitiveArrayCritical(hostArray, buffer, JNI_ABORT);

    record_event(&beforeEvent, &stream);
    result = cuMemcpyHtoDAsync(devicePtr, reinterpret_cast<void *>(stagingPtr), (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDAsyncStaged", result);
    record_event(&afterEvent, &stream);

    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuMemAllocHost
 * Signature: (J)J
 *
 * Allocates a page-locked (pinned) host buffer. Used as a per-stream staging
 * area so that JVM arrays can be copied to it and immediately released from
 * the GC critical section before the async DMA is enqueued.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuMemAllocHost
        (JNIEnv *env, jclass clazz, jlong numBytes) {
    void *ptr = nullptr;
    CUresult result = cuMemAllocHost(&ptr, (size_t) numBytes);
    LOG_PTX_AND_VALIDATE("cuMemAllocHost", result);
    if (result != CUDA_SUCCESS) {
        return 0L;
    }
    return (jlong) ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuMemFreeHost
 * Signature: (J)V
 *
 * Releases a pinned host buffer previously allocated by cuMemAllocHost.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuMemFreeHost
        (JNIEnv *env, jclass clazz, jlong hostPtr) {
    CUresult result = cuMemFreeHost((void *) hostPtr);
    LOG_PTX_AND_VALIDATE("cuMemFreeHost", result);
}


/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3BJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jbyteArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, array, host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3SJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jshortArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3CJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jcharArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3IJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jintArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3JJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlongArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3FJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jfloatArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[DJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3DJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jdoubleArray array, jlong host_offset, jbyteArray stream_wrapper) {
    return transferFromHostToDevice(env, klass, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[BJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3BJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jbyteArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, array, host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[SJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3SJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jshortArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[CJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3CJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jcharArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[IJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3IJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jintArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[JJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3JJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlongArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[FJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3FJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jfloatArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[DJJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3DJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jdoubleArray array, jlong host_offset, jlong staging, jbyteArray stream_wrapper) {
    return stagedTransferFromHostToDevice(env, device_ptr, length, reinterpret_cast<jbyteArray>(array), host_offset, staging, stream_wrapper);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuLaunchKernel
 * Signature: ([BLjava/lang/String;IIIIIIJ[B[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuLaunchKernel(
        JNIEnv *env,
        jclass clazz,
        jbyteArray module,
        jstring function_name,
        jint gridDimX, jint gridDimY, jint gridDimZ,
        jint blockDimX, jint blockDimY, jint blockDimZ,
        jlong sharedMemBytes,
        jbyteArray stream_wrapper,
        jbyteArray args) {

    CUevent beforeEvent;
    CUevent afterEvent;
    CUmodule native_module;
    array_to_module(env, &native_module, module);

    const char *native_function_name = env->GetStringUTFChars(function_name, 0);
    CUfunction kernel;
    CUresult result = cuModuleGetFunction(&kernel, native_module, native_function_name);
    LOG_PTX_AND_VALIDATE("cuModuleGetFunction", result);

    size_t arg_buffer_size = env->GetArrayLength(args);
#ifdef _WIN32
    char *arg_buffer = new char[arg_buffer_size];
#else
    char arg_buffer[arg_buffer_size];
#endif
    env->GetByteArrayRegion(args, 0, arg_buffer_size, reinterpret_cast<jbyte *>(arg_buffer));

    void *arg_config[] = {
        CU_LAUNCH_PARAM_BUFFER_POINTER, arg_buffer,
        CU_LAUNCH_PARAM_BUFFER_SIZE,    &arg_buffer_size,
        CU_LAUNCH_PARAM_END
    };

    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    result = cuLaunchKernel(
            kernel,
            (unsigned int) gridDimX,  (unsigned int) gridDimY,  (unsigned int) gridDimZ,
            (unsigned int) blockDimX, (unsigned int) blockDimY, (unsigned int) blockDimZ,
            (unsigned int) sharedMemBytes, stream,
            NULL,
            arg_config);
    LOG_PTX_AND_VALIDATE("cuLaunchKernel", result);
#ifdef _WIN32
    delete[] arg_buffer;
#endif

    record_event(&afterEvent, &stream);
    env->ReleaseStringUTFChars(function_name, native_function_name);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuCreateStream
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuCreateStream
  (JNIEnv *env, jclass clazz) {
    int lowestPriority, highestPriority;
    CUresult result = cuCtxGetStreamPriorityRange (&lowestPriority, &highestPriority);
    LOG_PTX_AND_VALIDATE("cuCtxGetStreamPriorityRange", result);

    CUstream stream;
    result = cuStreamCreateWithPriority(&stream, CU_STREAM_NON_BLOCKING, highestPriority);
    LOG_PTX_AND_VALIDATE("cuStreamCreateWithPriority", result);
    return array_from_stream(env, &stream);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuDestroyStream
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuDestroyStream
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    CUresult result = cuStreamDestroy(stream);
    LOG_PTX_AND_VALIDATE("cuStreamDestroy", result);

    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuStreamSynchronize
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuStreamSynchronize
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUresult result = cuStreamSynchronize(stream);
    LOG_PTX_AND_VALIDATE("cuStreamSynchronize", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuEventCreateAndRecord
 * Signature: (Z[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuEventCreateAndRecord
  (JNIEnv *env, jclass clazz, jboolean is_timing, jbyteArray stream_wrapper) {
    CUevent beforeEvent;
    CUevent afterEvent;
    CUstream stream;

    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    record_event(&afterEvent, &stream);

    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJJJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jlong hostPointer, jlong host_offset, jbyteArray stream_wrapper) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyDtoHAsync((void*) (hostPointer + host_offset), device_ptr, (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHMemSeg", result);
    record_event(&afterEvent, &stream);
    // Only synchronize when NOT capturing — sync is illegal during stream capture
    CUstreamCaptureStatus captureStatus;
    cuStreamIsCapturing(stream, &captureStatus);
    if (captureStatus != CU_STREAM_CAPTURE_STATUS_ACTIVE) {
        if (cuEventQuery(afterEvent) != CUDA_SUCCESS) {
            cuEventSynchronize(afterEvent);
        }
    }

    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJJJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jlong hostPointer, jlong hostOffset, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUevent beforeEvent;
    CUevent afterEvent;
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyDtoHAsync((void *) (hostPointer + hostOffset), devicePtr, (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHAsyncMemSeg", result);
    record_event(&afterEvent, &stream);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlong hostPointer, jlong host_offset, jbyteArray stream_wrapper) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyHtoDAsync(device_ptr, (void *) (hostPointer + host_offset), (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDMemSeg", result);
    record_event(&afterEvent, &stream);
    CUstreamCaptureStatus captureStatus;
    cuStreamIsCapturing(stream, &captureStatus);
    // Only synchronize when NOT capturing — sync is illegal during stream capture
    if (captureStatus != CU_STREAM_CAPTURE_STATUS_ACTIVE) {
        if (cuEventQuery(afterEvent) != CUDA_SUCCESS) {
            cuEventSynchronize(afterEvent);
        }
    }
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlong hostPointer, jlong host_offset, jbyteArray stream_wrapper) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyHtoDAsync(device_ptr, (void *) (hostPointer + host_offset), (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDAsyncMemSeg", result);
    record_event(&afterEvent, &stream);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuStreamBeginCapture
 * Signature: ([BI)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuStreamBeginCapture
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper, jint mode) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUresult result = cuStreamBeginCapture(stream, (CUstreamCaptureMode) mode);
    LOG_PTX_AND_VALIDATE("cuStreamBeginCapture", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuStreamEndCapture
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuStreamEndCapture
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUgraph graph;
    CUresult result = cuStreamEndCapture(stream, &graph);
    LOG_PTX_AND_VALIDATE("cuStreamEndCapture", result);
    jbyteArray array = env->NewByteArray(sizeof(CUgraph));
    env->SetByteArrayRegion(array, 0, sizeof(CUgraph),
                            reinterpret_cast<const jbyte *>(&graph));
    return array;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuGraphInstantiate
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuGraphInstantiate
  (JNIEnv *env, jclass clazz, jbyteArray graph_wrapper) {
    CUgraph graph;
    env->GetByteArrayRegion(graph_wrapper, 0, sizeof(CUgraph),
                            reinterpret_cast<jbyte *>(&graph));
    CUgraphExec graphExec;
    #if CUDA_VERSION >= 12000
        CUresult result = cuGraphInstantiate(&graphExec, graph, 0);
    #else
        CUresult result = cuGraphInstantiate(&graphExec, graph, NULL, NULL, 0);
    #endif
    LOG_PTX_AND_VALIDATE("cuGraphInstantiate", result);
    jbyteArray array = env->NewByteArray(sizeof(CUgraphExec));
    env->SetByteArrayRegion(array, 0, sizeof(CUgraphExec),
                            reinterpret_cast<const jbyte *>(&graphExec));
    return array;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuGraphLaunch
 * Signature: ([B[B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuGraphLaunch
  (JNIEnv *env, jclass clazz, jbyteArray graph_exec_wrapper, jbyteArray stream_wrapper) {
    CUgraphExec graphExec;
    env->GetByteArrayRegion(graph_exec_wrapper, 0, sizeof(CUgraphExec),
                            reinterpret_cast<jbyte *>(&graphExec));
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUresult result = cuGraphLaunch(graphExec, stream);
    LOG_PTX_AND_VALIDATE("cuGraphLaunch", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuGraphExecDestroy
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuGraphExecDestroy
  (JNIEnv *env, jclass clazz, jbyteArray graph_exec_wrapper) {
    CUgraphExec graphExec;
    env->GetByteArrayRegion(graph_exec_wrapper, 0, sizeof(CUgraphExec),
                            reinterpret_cast<jbyte *>(&graphExec));
    CUresult result = cuGraphExecDestroy(graphExec);
    LOG_PTX_AND_VALIDATE("cuGraphExecDestroy", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuGraphDestroy
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuGraphDestroy
  (JNIEnv *env, jclass clazz, jbyteArray graph_wrapper) {
    CUgraph graph;
    env->GetByteArrayRegion(graph_wrapper, 0, sizeof(CUgraph),
                            reinterpret_cast<jbyte *>(&graph));
    CUresult result = cuGraphDestroy(graph);
    LOG_PTX_AND_VALIDATE("cuGraphDestroy", result);
    return (jlong) result;
}