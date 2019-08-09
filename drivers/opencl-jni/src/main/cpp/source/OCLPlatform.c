/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clGetPlatformInfo
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetPlatformInfo
(JNIEnv *env, jclass clazz, jlong platform_id, jint platform_info) {
    OPENCL_PROLOGUE;

    char value[1024];

    OPENCL_SOFT_ERROR("clGetPlatformInfo",
            clGetPlatformInfo((cl_platform_id) platform_id, (cl_platform_info) platform_info, sizeof (char)*1024, value, NULL), 0);

    return (*env)->NewStringUTF(env, value);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceCount
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetDeviceCount
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type) {
    OPENCL_PROLOGUE;
    cl_uint num_devices = 0;
    OPENCL_SOFT_ERROR("clGetDeviceIDs",
            clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type, 0, NULL, &num_devices), 0);
    return (jint) num_devices;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceIDs
 * Signature: (JJ[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetDeviceIDs
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type, jlongArray array) {
    
    cl_int error_id;

    jlong *devices;
    jsize len;
    jboolean isCopy;

    devices = (*env)->GetLongArrayElements(env, array, &isCopy);
    len = (*env)->GetArrayLength(env, array);

    cl_uint num_devices = 0;
    error_id = clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type, len, (cl_device_id*) devices, &num_devices);
    OPENCL_SOFT_ERROR("clGetDeviceIDs", error_id, 0);

    (*env)->ReleaseLongArrayElements(env, array, devices, 0);
    return (jint) num_devices;

}

void context_notify(const char *errinfo, const void *private_info, size_t cb, void * user_data) {
    printf("uk.ac.manchester.tornado.drivers.opencl> notify error:\n");
    printf("uk.ac.manchester.tornado.drivers.opencl> %s\n", errinfo);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clCreateContext
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clCreateContext
(JNIEnv *env, jclass clazz, jlong platform_id, jlongArray array) {
    OPENCL_PROLOGUE;

    jlong *devices;
    jsize len;
    cl_context context;

    cl_context_properties properties[] = {CL_CONTEXT_PLATFORM, platform_id, 0};

    devices = (*env)->GetLongArrayElements(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    context = clCreateContext(properties, len, (cl_device_id*) devices, &context_notify, NULL, &error_id);
    OPENCL_CHECK_ERROR("clCreateContext", error_id, 0);

    (*env)->ReleaseLongArrayElements(env, array, devices, 0);
    return (jlong) context;
}
