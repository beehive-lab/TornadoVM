#include <jni.h>
#include <cuda.h>

#include "CUDAModule.h"
#include "../macros/data_copies.h"

//TODO: Make async calls async (create stream, destroy stream, manage events)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[BJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3BJ_3I, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[SJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3SJ_3I, jshort, Short)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[CJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3CJ_3I, jchar, Char)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[IJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3IJ_3I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[JJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3JJ_3I, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[FJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3FJ_3I, jfloat, Float)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[DJ[I)I
 */
COPY_ARRAY_D_TO_H(__JJJ_3DJ_3I, jdouble, Double)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[BJ[I)V
 */
COPY_ARRAY_H_TO_D(__JJJ_3BJ_3I, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[SJ[I)V
 */
COPY_ARRAY_H_TO_D(__JJJ_3SJ_3I, jshort, Short)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[CJ[I)I
 */
COPY_ARRAY_H_TO_D(__JJJ_3CJ_3I, jchar, Char)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[IJ[I)V
 */
COPY_ARRAY_H_TO_D(__JJJ_3IJ_3I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[JJ[I)V
 */
COPY_ARRAY_H_TO_D(__JJJ_3JJ_3I, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[FJ[I)V
 */
COPY_ARRAY_H_TO_D(__JJJ_3FJ_3I, jfloat, Float)

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[DJ[I)V
 */
COPY_ARRAY_H_TO_D(__JJJ_3DJ_3I, jdouble, Double)

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