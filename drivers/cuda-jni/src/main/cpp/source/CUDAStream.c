#include <jni.h>
#include <cuda.h>

#include "CUDAModule.h"
#include "../macros/data_copies.h"

//TODO: Make async calls async (create stream, destroy stream, manage events)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH
 * Signature: (JJJ[BJ[I)I
 */
COPY_ARRAY_D_TO_H_SYNC(__JJJ_3BJ_3I, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH
 * Signature: (JJJ[IJ[I)I
 */
COPY_ARRAY_D_TO_H_SYNC(__JJJ_3IJ_3I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH
 * Signature: (JJJ[JJ[I)I
 */
COPY_ARRAY_D_TO_H_SYNC(__JJJ_3JJ_3I, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[BJ[I)I
 */
COPY_ARRAY_D_TO_H_ASYNC(__JJJ_3BJ_3I, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[IJ[I)I
 */
COPY_ARRAY_D_TO_H_ASYNC(__JJJ_3IJ_3I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[JJ[I)I
 */
COPY_ARRAY_D_TO_H_ASYNC(__JJJ_3JJ_3I, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[BJ[I)V
 */
COPY_ARRAY_H_TO_D_SYNC(__JJJ_3BJ_3I, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[IJ[I)V
 */
COPY_ARRAY_H_TO_D_SYNC(__JJJ_3IJ_3I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[JJ[I)V
 */
COPY_ARRAY_H_TO_D_SYNC(__JJJ_3JJ_3I, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJ[BJ[I)I
 */
COPY_ARRAY_H_TO_D_ASYNC(__JJJ_3BJ_3I, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJ[IJ[I)I
 */
COPY_ARRAY_H_TO_D_ASYNC(__JJJ_3IJ_3I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJ[JJ[I)I
 */
COPY_ARRAY_H_TO_D_ASYNC(__JJJ_3JJ_3I, jlong, Long)

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