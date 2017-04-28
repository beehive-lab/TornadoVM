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
#ifdef _OSX
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif
#include <stdio.h>
#include "macros.h"
#include "utils.h"

/*
 * Class:     jacc_runtime_drivers_opencl_OpenCL
 * Method:    clGetPlatformCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OpenCL_clGetPlatformCount
(JNIEnv *env, jclass clazz) {
    OPENCL_PROLOGUE;
    cl_uint num_platforms = 0;
    OPENCL_SOFT_ERROR("clGetPlatformIDs",
            clGetPlatformIDs(0, NULL, &num_platforms), 0);
    return (jint) num_platforms;
}

/*
 * Class:     jacc_runtime_drivers_opencl_OpenCL
 * Method:    clGetPlatformIDs
 * Signature: ([J)I
 */
JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OpenCL_clGetPlatformIDs
(JNIEnv *env, jclass clazz, jlongArray array) {
    OPENCL_PROLOGUE;

    jlong *platforms;
    jsize len;

    platforms = (*env)->GetPrimitiveArrayCritical(env, array, NULL);
    len = (*env)->GetArrayLength(env, array);

    cl_uint num_platforms = 0;
    OPENCL_SOFT_ERROR("clGetPlatformIDs",
            clGetPlatformIDs(len, (cl_platform_id*) platforms, &num_platforms), 0);

    (*env)->ReleasePrimitiveArrayCritical(env, array, platforms, 0);
    return (jint) num_platforms;
}

///*
// * Class:     tornado_drivers_opencl_OpenCL
// * Method:    registerCallback
// * Signature: ()Z
// */
//JNIEXPORT jboolean JNICALL Java_tornado_drivers_opencl_OpenCL_registerCallback
//  (JNIEnv *env, jclass clazz){
//	jboolean result = true;
//
//
//	return result;
//}
