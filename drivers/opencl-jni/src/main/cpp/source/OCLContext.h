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
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OCLContext */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OCLContext
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OCLContext
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clReleaseContext
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clReleaseContext
        (JNIEnv *, jclass, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clGetContextInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clGetContextInfo
        (JNIEnv *, jclass, jlong, jint, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateCommandQueue
 * Signature: (JJJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateCommandQueue
        (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    allocateOffHeapMemory
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_allocateOffHeapMemory
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    freeOffHeapMemory
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_freeOffHeapMemory
        (JNIEnv *, jclass, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    asByteBuffer
 * Signature: (JJ)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_asByteBuffer
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    createBuffer
 * Signature: (JJJJ)Luk/ac/manchester/tornado/drivers/opencl/OCLContext/OCLBufferResult;
 */
JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_createBuffer
        (JNIEnv *, jclass, jlong, jlong, jlong, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    createSubBuffer
 * Signature: (JJI[B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_createSubBuffer
        (JNIEnv *, jclass, jlong, jlong, jint, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clReleaseMemObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clReleaseMemObject
        (JNIEnv *, jclass, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithSource
 * Signature: (J[B[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateProgramWithSource
        (JNIEnv *, jclass, jlong, jbyteArray, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithBinary
 * Signature: (JJ[B[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateProgramWithBinary
        (JNIEnv *, jclass, jlong, jlong, jbyteArray, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLContext
 * Method:    clCreateProgramWithIL
 * Signature: (J[B[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLContext_clCreateProgramWithIL
        (JNIEnv *env, jclass clazz, jlong context_id, jbyteArray javaSourceBinaryArray, jlongArray javaSizeArray);

#ifdef __cplusplus
}
#endif
#endif
