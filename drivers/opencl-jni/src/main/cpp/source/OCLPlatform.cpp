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

#ifdef __APPLE__
    #include <OpenCL/cl.h>
#else
    #include <CL/cl.h>
#endif

#include <iostream>
#include "OCLPlatform.h"
#include "ocl_log.h"

#define MAX_CHAR_ARRAY 1024

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clGetPlatformInfo
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetPlatformInfo
(JNIEnv *env, jclass clazz, jlong platform_id, jint platform_info) {
    char value[MAX_CHAR_ARRAY];
    cl_uint status = clGetPlatformInfo((cl_platform_id) platform_id, (cl_platform_info) platform_info, sizeof (char) * MAX_CHAR_ARRAY, value,
                                       NULL);
    LOG_OCL_AND_VALIDATE("clGetPlatformInfo", status);
    return env->NewStringUTF(value);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceCount
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetDeviceCount
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type) {
    cl_uint num_devices = 0;
    cl_uint status = clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type, 0, NULL, &num_devices);
    LOG_OCL_AND_VALIDATE("clGetDeviceIDs", status);
    return (jint) num_devices;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceIDs
 * Signature: (JJ[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetDeviceIDs
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type, jlongArray array) {
    jlong *devices;
    jsize len;
    jboolean isCopy;
    devices = env->GetLongArrayElements(array, &isCopy);
    len = env->GetArrayLength(array);
    cl_uint num_devices = 0;

    cl_uint status = clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type, len, (cl_device_id*) devices, &num_devices);
    LOG_OCL_AND_VALIDATE("clGetDeviceIDs", status);

    env->ReleaseLongArrayElements(array, devices, 0);
    return (jint) num_devices;

}

void context_notify(const char *errinfo, const void *private_info, size_t cb, void * user_data) {
    std::cout << "[JNI] uk.ac.manchester.tornado.drivers.opencl> notify error:\n";
    std::cout << "[JNI] uk.ac.manchester.tornado.drivers.opencl> " <<  errinfo << std::endl;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLPlatform
 * Method:    clCreateContext
 * Signature: (J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clCreateContext
(JNIEnv *env, jclass clazz, jlong platform_id, jlongArray array) {
    jlong *devices;
    jsize len;
    cl_context context;
    jboolean isCopy;
    cl_context_properties properties[] = {CL_CONTEXT_PLATFORM, platform_id, 0};
    devices = env->GetLongArrayElements(array, &isCopy);
    len = env->GetArrayLength(array);
    cl_int status;
    context = clCreateContext(properties, len, (cl_device_id*) devices, &context_notify, NULL, &status);
    LOG_OCL_AND_VALIDATE("clCreateContext", status);
    env->ReleaseLongArrayElements(array, devices, 0);
    return (jlong) context;
}
