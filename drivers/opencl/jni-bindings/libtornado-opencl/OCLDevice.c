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
 * Class:     jacc_runtime_drivers_opencl_OCLDevice
 * Method:    clGetDeviceInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLDevice_clGetDeviceInfo
(JNIEnv *env, jclass clazz, jlong device_id, jint device_info, jbyteArray array) {

    OPENCL_PROLOGUE;

    jbyte *value;
    jsize len;

    value = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);
    debug("tornado.drivers.opencl> clGetDeviceInfo param=0x%x\n", device_info);
    size_t return_size = 0;
    OPENCL_SOFT_ERROR("clGetDeviceInfo",
            clGetDeviceInfo((cl_device_id) device_id, (cl_device_info) device_info, len, (void *) value, &return_size),);

    (*env)->ReleasePrimitiveArrayCritical(env, array, value, 0);
}
