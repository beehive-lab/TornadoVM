/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
#include <jni.h>
#ifdef __APPLE__
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"

/*
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
 * Method:    clGetPlatformInfo
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetPlatformInfo
(JNIEnv *env, jclass clazz, jlong platform_id, jint platform_info) {
    OPENCL_PROLOGUE;

    char value[512];

    OPENCL_SOFT_ERROR("clGetPlatformInfo",
            clGetPlatformInfo((cl_platform_id) platform_id, (cl_platform_info) platform_info, sizeof (char)*512, value, NULL), 0);

    return (*env)->NewStringUTF(env, value);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
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
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
 * Method:    clGetDeviceIDs
 * Signature: (JJ[J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLPlatform_clGetDeviceIDs
(JNIEnv *env, jclass clazz, jlong platform_id, jlong device_type, jlongArray array) {
    OPENCL_PROLOGUE;

    jlong *devices;
    jsize len;

    devices = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    cl_uint num_devices = 0;
    OPENCL_SOFT_ERROR("clGetDeviceIDs",
            clGetDeviceIDs((cl_platform_id) platform_id, (cl_device_type) device_type, len, (cl_device_id*) devices, &num_devices), 0);

    (*env)->ReleasePrimitiveArrayCritical(env, array, devices, 0);
    return (jint) num_devices;

}

void context_notify(const char *errinfo, const void *private_info, size_t cb, void * user_data) {
    printf("uk.ac.manchester.tornado.drivers.opencl> notify error:\n");
    printf("uk.ac.manchester.tornado.drivers.opencl> %s\n", errinfo);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLPlatform
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

    devices = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    OPENCL_CHECK_ERROR("clCreateContext",
            context = clCreateContext(properties, len, (cl_device_id*) devices, &context_notify, NULL, &error_id), 0);


    (*env)->ReleasePrimitiveArrayCritical(env, array, devices, 0);
    return (jlong) context;
}
