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
 * Authors: James Clarkson
 *
 */
#include <jni.h>

#define CL_TARGET_OPENCL_VERSION 120
#ifdef __APPLE__
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif

#include <stdio.h>
#include "macros.h"
#include "utils.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
 * Method:    clReleaseProgram
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clReleaseProgram
(JNIEnv *env, jclass clazz, jlong program_id) {
    OPENCL_PROLOGUE;

    OPENCL_SOFT_ERROR("clReleaseProgram", clReleaseProgram((cl_program) program_id),);
}

void notify_compilation_error(cl_program program_id, void *user_data) {
    printf("OpenCL> compilation error: program_id = %p\n", program_id);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
 * Method:    clBuildProgram
 * Signature: (J[J[C)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clBuildProgram
(JNIEnv *env, jclass clazz, jlong program_id, jlongArray array1, jstring str) {
    OPENCL_PROLOGUE;

    jlong *devices = (*env)->GetPrimitiveArrayCritical(env, array1, NULL);
    jsize numDevices = (*env)->GetArrayLength(env, array1);

    const char *options = (*env)->GetStringUTFChars(env, str, NULL);

    // if pfn_notify callback is set, clBuildProgram will return immediately and compilation will be asynchronous
    // otherwise, it will behave synchronously
    OPENCL_SOFT_ERROR("clBuildProgarm", clBuildProgram((cl_program) program_id, (cl_uint) numDevices, (cl_device_id*) devices, options, NULL, NULL),);

    (*env)->ReleasePrimitiveArrayCritical(env, array1, devices, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
 * Method:    clGetProgramInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clGetProgramInfo
(JNIEnv *env, jclass clazz, jlong program_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    jsize len = (*env)->GetArrayLength(env, array);

    debug("size of cl_program_info: %lx\n", sizeof (cl_program_info));
    debug("param_name: %x\n", param_name);
    debug("len: %x\n", len);


    size_t return_size = 0;


    OPENCL_SOFT_ERROR("clGetProgramInfo",
            clGetProgramInfo((cl_program) program_id, (cl_program_info) param_name, len, (void *) value, &return_size),);

    debug("return size: %zx\n", return_size);

    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
 * Method:    clGetProgramBuildInfo
 * Signature: (JJI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clGetProgramBuildInfo
(JNIEnv *env, jclass clazz, jlong program_id, jlong device_id, jint param_name, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    jsize len = (*env)->GetArrayLength(env, array);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetProgramBuildInfo",
            clGetProgramBuildInfo((cl_program) program_id, (cl_device_id) device_id, (cl_program_build_info) param_name, len, (void *) value, &return_size),);


    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
 * Method:    clCreateKernel
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clCreateKernel
(JNIEnv *env, jclass clazz, jlong program_id, jstring str) {
    OPENCL_PROLOGUE;

    const char *kernel_name = (*env)->GetStringUTFChars(env, str, NULL);

    cl_kernel kernel;
    OPENCL_CHECK_ERROR("clCreateKernel", kernel = clCreateKernel((cl_program) program_id, kernel_name, &error_id), -1);

    return (jlong) kernel;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
 * Method:    getBinaries
 * Signature: (JJ[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_getBinaries
(JNIEnv *env, jclass clazz, jlong program_id, jlong num_devices, jobject array) {
    OPENCL_PROLOGUE;

    jbyte *value = (jbyte *) (*env)->GetDirectBufferAddress(env, array);
    size_t return_size = 0;

    size_t *binarySizes = malloc(sizeof (size_t) * num_devices);

    OPENCL_SOFT_ERROR("clGetProgramInfo",
            clGetProgramInfo((cl_program) program_id, CL_PROGRAM_BINARY_SIZES, sizeof (size_t) * num_devices, binarySizes, &return_size),);

    unsigned char **binaries = malloc(sizeof (unsigned char *) * num_devices);
    binaries[0] = value;
    for (int i = 1; i < num_devices; i++) {
        binaries[i] = value + binarySizes[i - 1];
    }

    OPENCL_SOFT_ERROR("clGetProgramInfo",
            clGetProgramInfo((cl_program) program_id, CL_PROGRAM_BINARIES, sizeof (unsigned char**), (void *) binaries, &return_size),);

    free(binarySizes);
    free(binaries);
}
