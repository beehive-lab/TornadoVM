/*
 * Copyright 2012 James Clarkson.
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
 */

#include <jni.h>
/* Header for class tornado_drivers_opencl_OpenCL */

#ifndef _Included_tornado_drivers_opencl_OpenCL
#define _Included_tornado_drivers_opencl_OpenCL
#ifdef __cplusplus
extern "C" {
#endif
#undef tornado_drivers_opencl_OpenCL_CL_TRUE
#define tornado_drivers_opencl_OpenCL_CL_TRUE 1L
#undef tornado_drivers_opencl_OpenCL_CL_FALSE
#define tornado_drivers_opencl_OpenCL_CL_FALSE 0L
    /*
     * Class:     tornado_drivers_opencl_OpenCL
     * Method:    registerCallback
     * Signature: ()Z
     */
    JNIEXPORT jboolean JNICALL Java_tornado_drivers_opencl_OpenCL_registerCallback
    (JNIEnv *, jclass);

    /*
     * Class:     tornado_drivers_opencl_OpenCL
     * Method:    clGetPlatformCount
     * Signature: ()I
     */
    JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OpenCL_clGetPlatformCount
    (JNIEnv *, jclass);

    /*
     * Class:     tornado_drivers_opencl_OpenCL
     * Method:    clGetPlatformIDs
     * Signature: ([J)I
     */
    JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OpenCL_clGetPlatformIDs
    (JNIEnv *, jclass, jlongArray);

#ifdef __cplusplus
}
#endif
#endif
