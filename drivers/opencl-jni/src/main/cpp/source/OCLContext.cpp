/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
#include <jni.h>

#define CL_TARGET_OPENCL_VERSION 210
#ifdef __APPLE__
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif

#include <iostream>
#include <cstring>
#include "OCLContext.h"
#include "ocl_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clReleaseContext
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clReleaseContext
(JNIEnv *env, jclass clazz, jlong context_id) {
    cl_int status = clReleaseContext((cl_context) context_id);
    LOG_OCL_AND_VALIDATE("clReleaseContext", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clGetContextInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clGetContextInfo
(JNIEnv *env, jclass clazz, jlong context_id, jint param_name, jbyteArray array) {
    jbyte *value = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    int len = env->GetArrayLength(array);
    size_t return_size = 0;
    cl_int status = clGetContextInfo((cl_context) context_id, (cl_context_info) param_name, len, (void *) value, &return_size);
    LOG_OCL_AND_VALIDATE("clGetContextInfo", status);
    env->ReleasePrimitiveArrayCritical(array, value, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateCommandQueue
 * Signature: (JJJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateCommandQueue
(JNIEnv *env, jclass clazz, jlong context_id, jlong device_id, jlong properties) {
    cl_int status;
    cl_command_queue queue = clCreateCommandQueue((cl_context) context_id, (cl_device_id) device_id, (cl_command_queue_properties) properties, &status);
    LOG_OCL_AND_VALIDATE("clCreateCommandQueue", status);
    return (jlong) queue;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    allocateOffHeapMemory
 * Signature: (J)JJ
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_allocateOffHeapMemory
(JNIEnv *env, jclass clazz, jlong size, jlong alignment) {
    void *ptr;
#if _WIN32
    ptr = _aligned_malloc((size_t) alignment, (size_t) size);
    if (ptr == 0) {
        printf("OpenCL off-heap memory allocation (aligned_malloc) failed.\n");
    }
#else
    int rc = posix_memalign(&ptr, (size_t) alignment, (size_t) size);
    if (rc != 0) {
        printf("OpenCL off-heap memory allocation (posix_memalign) failed. Error value: %d.\n", rc);
    }
#endif()
    memset(ptr, 0, (size_t) size);
    size_t i = 0;
    for (; i < (size_t) size / 4; i++) {
        ((int *) ptr)[i] = i;
    }
    return (jlong) ptr;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    freeOffHeapMemory
 * Signature: (J)J
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_freeOffHeapMemory
(JNIEnv *env, jclass clazz, jlong address) {
    free((void *) address);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    asByteBuffer
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_asByteBuffer
(JNIEnv *env, jclass clazz, jlong address, jlong capacity) {
    return env->NewDirectByteBuffer((void *) address, capacity);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    createBuffer
 * Signature: (JJJJ)Luk/ac/manchester/tornado/drivers/opencl/OCLContext/OCLBufferResult;
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_createBuffer
(JNIEnv *env, jclass clazz, jlong context_id, jlong flags, jlong size, jlong host_ptr) {

    jclass resultClass = env->FindClass("uk/ac/manchester/tornado/drivers/opencl/OCLContext$OCLBufferResult");
    jmethodID constructorId = env->GetMethodID(resultClass, "<init>", "(JJI)V");

    cl_mem mem;
    cl_int status;
	if (host_ptr == 0) {
        mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) size, NULL, &status);
	} else {
	    mem = clCreateBuffer((cl_context) context_id, (cl_mem_flags) flags, (size_t) size, (void *) host_ptr, &status);
	}
	LOG_OCL_AND_VALIDATE("clCreateBuffer", status);
    return env->NewObject(resultClass, constructorId, (jlong) mem, (jlong) host_ptr, (jint) status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    createSubBuffer
 * Signature: (JJI[B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_createSubBuffer
(JNIEnv *env, jclass clazz, jlong buffer, jlong flags, jint buffer_create_type, jbyteArray array) {
    jbyte *buffer_create_info = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    cl_int status;
    cl_mem mem = clCreateSubBuffer((cl_mem) buffer, (cl_mem_flags) flags, (cl_buffer_create_type) buffer_create_type, (void *) buffer_create_info, &status);
    LOG_OCL_AND_VALIDATE("clCreateSubBuffer", status);
    env->ReleasePrimitiveArrayCritical(array, buffer_create_info, 0);
    return (jlong) mem;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clReleaseMemObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clReleaseMemObject
(JNIEnv *env, jclass clazz, jlong memobj) {
    cl_int status = clReleaseMemObject((cl_mem) memobj);
    LOG_OCL_AND_VALIDATE("clReleaseMemObject", status);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithSource
 * Signature: (J[B[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateProgramWithSource
(JNIEnv *env, jclass clazz, jlong context_id, jbyteArray array1, jlongArray array2) {
    jbyte *source = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array1, NULL));
    jlong *lengths = static_cast<jlong *>(env->GetPrimitiveArrayCritical(array2, NULL));
    jsize numLengths = env->GetArrayLength(array2);

    cl_int status;
    cl_program program = clCreateProgramWithSource((cl_context) context_id, (cl_uint) numLengths, (const char **) &source, (size_t*) lengths, &status);
    LOG_OCL_AND_VALIDATE("clCreateProgramWithSource", status);
    env->ReleasePrimitiveArrayCritical(array1, source, 0);
    env->ReleasePrimitiveArrayCritical(array2, lengths, 0);
    return (jlong) program;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithBinary
 * Signature: (JJ[B[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateProgramWithBinary
(JNIEnv *env, jclass clazz, jlong context_id, jlong device_id, jbyteArray array1, jlongArray array2) {
    jbyte *binary = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array1, NULL));
    jlong *lengths = static_cast<jlong *>(env->GetPrimitiveArrayCritical(array2, NULL));
    jsize numLengths = env->GetArrayLength(array2);

    cl_int status;
    cl_program program;
    if (numLengths == 1) {
        cl_int binary_status;
        program = clCreateProgramWithBinary((cl_context) context_id, (cl_uint) numLengths, (const cl_device_id *) &device_id, (const size_t*) lengths, (const unsigned char **) &binary, &binary_status, &status);
        LOG_OCL_AND_VALIDATE("clCreateProgramWithBinary", status);
    } else {
        std::cout << "[TornadoVM JNI] OCL> loading multiple binaries not supported\n";
    }
    env->ReleasePrimitiveArrayCritical(array1, binary, 0);
    env->ReleasePrimitiveArrayCritical(array2, lengths, 0);
    return (jlong) program;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithIL
 * Signature: (J[B[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateProgramWithIL
        (JNIEnv *env, jclass clazz, jlong context_id, jbyteArray javaSourceBinaryArray, jlongArray javaSizeArray) {
    jbyte *source = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(javaSourceBinaryArray, NULL));
    jlong *lengths = static_cast<jlong *>(env->GetPrimitiveArrayCritical(javaSizeArray, NULL));
    size_t binarySize = lengths[0];

    cl_int status;
    cl_program program = clCreateProgramWithIL((cl_context) context_id, (const void *) source, binarySize, &status);
    LOG_OCL_AND_VALIDATE("clCreateProgramWithIL", status);
    env->ReleasePrimitiveArrayCritical(javaSourceBinaryArray, source, 0);
    env->ReleasePrimitiveArrayCritical(javaSizeArray, lengths, 0);
    return (jlong) program;
}
