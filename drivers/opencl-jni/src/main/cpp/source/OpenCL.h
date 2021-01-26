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
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OpenCL */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OpenCL
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OpenCL
#ifdef __cplusplus
extern "C" {
#endif
#undef uk_ac_manchester_tornado_drivers_opencl_OpenCL_CL_TRUE
#define uk_ac_manchester_tornado_drivers_opencl_OpenCL_CL_TRUE 1L
#undef uk_ac_manchester_tornado_drivers_opencl_OpenCL_CL_FALSE
#define uk_ac_manchester_tornado_drivers_opencl_OpenCL_CL_FALSE 0L
/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OpenCL
 * Method:    registerCallback
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OpenCL_registerCallback
        (JNIEnv *, jclass);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OpenCL
 * Method:    clGetPlatformCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OpenCL_clGetPlatformCount
        (JNIEnv *, jclass);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OpenCL
 * Method:    clGetPlatformIDs
 * Signature: ([J)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OpenCL_clGetPlatformIDs
        (JNIEnv *, jclass, jlongArray);

jint JNI_OnLoad(JavaVM *vm, void *reserved);

void JNI_OnUnload(JavaVM *vm, void *reserved);
#ifdef __cplusplus
}
#endif
#endif
