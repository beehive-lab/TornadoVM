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
/* Header for class jacc_runtime_drivers_opencl_OCLPlatform */

#ifndef _Included_tornado_drivers_opencl_OCLPlatform
#define _Included_tornado_drivers_opencl_OCLPlatform
#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     jacc_runtime_drivers_opencl_OCLPlatform
     * Method:    clGetPlatformInfo
     * Signature: (JI)Ljava/lang/String;
     */
    JNIEXPORT jstring JNICALL Java_tornado_drivers_opencl_OCLPlatform_clGetPlatformInfo
    (JNIEnv *, jclass, jlong, jint);

    /*
     * Class:     jacc_runtime_drivers_opencl_OCLPlatform
     * Method:    clGetDeviceCount
     * Signature: (JJ)I
     */
    JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OCLPlatform_clGetDeviceCount
    (JNIEnv *, jclass, jlong, jlong);

    /*
     * Class:     jacc_runtime_drivers_opencl_OCLPlatform
     * Method:    clGetDeviceIDs
     * Signature: (JJ[J)I
     */
    JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OCLPlatform_clGetDeviceIDs
    (JNIEnv *, jclass, jlong, jlong, jlongArray);

    /*
     * Class:     jacc_runtime_drivers_opencl_OCLPlatform
     * Method:    clCreateContext
     * Signature: (J[J)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLPlatform_clCreateContext
    (JNIEnv *, jclass, jlong, jlongArray);

#ifdef __cplusplus
}
#endif
#endif
