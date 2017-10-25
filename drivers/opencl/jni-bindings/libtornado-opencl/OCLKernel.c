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
#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"

/*
 * Class:     jacc_runtime_drivers_opencl_OCLKernel
 * Method:    clReleaseKernel
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clReleaseKernel
(JNIEnv *env, jclass clazz, jlong kernel_id) {
    OPENCL_PROLOGUE;
    OPENCL_SOFT_ERROR("clReleaseKernel", clReleaseKernel((cl_kernel) kernel_id),);
}

/*
 * Class:     jacc_runtime_drivers_opencl_OCLKernel
 * Method:    clSetKernelArg
 * Signature: (JIJ[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clSetKernelArg
(JNIEnv *env, jclass clazz, jlong kernel_id, jint index, jlong size, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value = (array == NULL) ? NULL : (*env)->GetPrimitiveArrayCritical(env, array, NULL);

    OPENCL_SOFT_ERROR("clSetKernelArg", clSetKernelArg((cl_kernel) kernel_id, (cl_uint) index, (size_t) size, (void*) value),);

    if (value != NULL)
        (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}

/*
 * Class:     tornado_drivers_opencl_OCLKernel
 * Method:    clGetKernelInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clGetKernelInfo
(JNIEnv *env, jclass clazz, jlong kernel_id, jint kernel_info, jbyteArray array) {
    OPENCL_PROLOGUE;

    jbyte *value;
    jsize len;

    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetKernelInfo",
            clGetKernelInfo((cl_kernel) kernel_id, (cl_kernel_info) kernel_info, len, (void *) value, &return_size),);

    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}
