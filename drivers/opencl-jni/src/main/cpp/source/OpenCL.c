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
 * Class:     jacc_runtime_drivers_opencl_OpenCL
 * Method:    clGetPlatformCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OpenCL_clGetPlatformCount
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
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OpenCL_clGetPlatformIDs
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
// * Class:     uk_ac_manchester_tornado_drivers_opencl_OpenCL
// * Method:    registerCallback
// * Signature: ()Z
// */
//JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OpenCL_registerCallback
//  (JNIEnv *env, jclass clazz){
//	jboolean result = true;
//
//
//	return result;
//}
