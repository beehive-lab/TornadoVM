#include <jni.h>
#include <cuda.h>
#include <stdio.h>

#include "CUDAModule.h"
#include "CUDAEvent.h"
#include "../macros/data_copies.h"

//TODO: Make async calls async (create stream, destroy stream, manage events)

void stream_from_array(JNIEnv *env, CUstream *stream_ptr, jbyteArray array) {
    (*env)->GetByteArrayRegion(env, array, 0, sizeof(CUstream), (void *) stream_ptr);
}

jbyteArray array_from_stream(JNIEnv *env, CUstream *stream) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUstream));
    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUstream), (void *) stream);
    return array;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[BJ[I)I
 */
COPY_ARRAY_D_TO_H(B, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[SJ[I)I
 */
COPY_ARRAY_D_TO_H(S, jshort, Short)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[CJ[I)I
 */
COPY_ARRAY_D_TO_H(C, jchar, Char)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[IJ[I)I
 */
COPY_ARRAY_D_TO_H(I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[JJ[I)I
 */
COPY_ARRAY_D_TO_H(J, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[FJ[I)I
 */
COPY_ARRAY_D_TO_H(F, jfloat, Float)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[DJ[I)I
 */
COPY_ARRAY_D_TO_H(D, jdouble, Double)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[BJ[I)V
 */
COPY_ARRAY_H_TO_D(B, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[SJ[I)V
 */
COPY_ARRAY_H_TO_D(S, jshort, Short)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[CJ[I)I
 */
COPY_ARRAY_H_TO_D(C, jchar, Char)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[IJ[I)V
 */
COPY_ARRAY_H_TO_D(I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[JJ[I)V
 */
COPY_ARRAY_H_TO_D(J, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[FJ[I)V
 */
COPY_ARRAY_H_TO_D(F, jfloat, Float)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[DJ[I)V
 */
COPY_ARRAY_H_TO_D(D, jdouble, Double)

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
        jbyteArray stream_wrapper,
        jbyteArray args) {

    CUmodule native_module;
    array_to_module(env, &native_module, module);

    const char *native_function_name = (*env)->GetStringUTFChars(env, function_name, 0);
    CUfunction kernel;
    CUresult result = cuModuleGetFunction(&kernel, native_module, native_function_name);

    size_t arg_buffer_size = (*env)->GetArrayLength(env, args);
    char arg_buffer[arg_buffer_size];
    (*env)->GetByteArrayRegion(env, args, 0, arg_buffer_size, arg_buffer);


    void *arg_config[] = {
        CU_LAUNCH_PARAM_BUFFER_POINTER, arg_buffer,
        CU_LAUNCH_PARAM_BUFFER_SIZE,    &arg_buffer_size,
        CU_LAUNCH_PARAM_END
    };

    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    result = cuLaunchKernel(kernel,
            (unsigned int) gridDimX,  (unsigned int) gridDimY,  (unsigned int) gridDimZ,
            (unsigned int) blockDimX, (unsigned int) blockDimY, (unsigned int) blockDimZ,
            (unsigned int) sharedMemBytes, stream,
            NULL,
            arg_config
    );
    if (result != 0) {
        printf("Failed to launch kernel: %s (%d)\n", native_function_name, result); fflush(stdout);
    }

    (*env)->ReleaseStringUTFChars(env, function_name, native_function_name);

    return (jint) -1;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    cuCreateStream
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_cuCreateStream
  (JNIEnv *env, jclass clazz) {
    int lowestPriority, highestPriority;
    CUresult result = cuCtxGetStreamPriorityRange (&lowestPriority, &highestPriority);
    if (result != 0) {
        printf("Failed to get lowest and highest stream priorities! (%d)\n", result); fflush(stdout);
    }

    CUstream stream;
    result = cuStreamCreateWithPriority(&stream, CU_STREAM_NON_BLOCKING, highestPriority);
    if (result != 0) {
        printf("Failed to create stream with priority: %d (%d)\n", highestPriority, result);
    }

    return array_from_stream(env, &stream);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    cuDestroyStream
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_cuDestroyStream
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    CUresult result = cuStreamDestroy(stream);
    if (result != 0) {
        printf("Failed to destroy stream! (%d)\n", result); fflush(stdout);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    cuStreamSynchronize
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_cuStreamSynchronize
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    CUresult result = cuStreamSynchronize(stream);
    if (result != 0) {
        printf("Failed to synchronize with stream! (%d)\n", result); fflush(stdout);
    }
    return;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    cuEventCreateAndRecord
 * Signature: (Z[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAStream_cuEventCreateAndRecord
  (JNIEnv *env, jclass clazz, jboolean is_timing, jbyteArray stream_wrapper) {
    unsigned int flags = CU_EVENT_DEFAULT;
    if (!is_timing) flags |= CU_EVENT_DISABLE_TIMING;

    CUevent event;
    CUresult result = cuEventCreate(&event, flags);
    if (result != 0) {
        printf("Failed to create event! (%d)\n", result); fflush(stdout);
    }

    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    result = cuEventRecord(event, stream);
    if (result != 0) {
        printf("Failed to record event! (%d)\n", result); fflush(stdout);
    }

    return array_from_event(env, &event);
}