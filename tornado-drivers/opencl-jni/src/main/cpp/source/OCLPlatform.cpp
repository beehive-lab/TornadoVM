/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
