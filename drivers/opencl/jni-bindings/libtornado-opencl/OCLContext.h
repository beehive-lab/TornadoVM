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
/* Header for class tornado_drivers_opencl_OCLContext */

#ifndef _Included_tornado_drivers_opencl_OCLContext
#define _Included_tornado_drivers_opencl_OCLContext
#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    clReleaseContext
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_clReleaseContext
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    clGetContextInfo
     * Signature: (JI[B)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_clGetContextInfo
    (JNIEnv *, jclass, jlong, jint, jbyteArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    clCreateCommandQueue
     * Signature: (JJJ)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_clCreateCommandQueue
    (JNIEnv *, jclass, jlong, jlong, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    allocateOffHeapMemory
     * Signature: (JJ)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_allocateOffHeapMemory
    (JNIEnv *, jclass, jlong, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    freeOffHeapMemory
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_freeOffHeapMemory
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    asByteBuffer
     * Signature: (JJ)Ljava/nio/ByteBuffer;
     */
    JNIEXPORT jobject JNICALL Java_tornado_drivers_opencl_OCLContext_asByteBuffer
    (JNIEnv *, jclass, jlong, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    createBuffer
     * Signature: (JJJJ)Ltornado/drivers/opencl/OCLContext/OCLBufferResult;
     */
    JNIEXPORT jobject JNICALL Java_tornado_drivers_opencl_OCLContext_createBuffer
    (JNIEnv *, jclass, jlong, jlong, jlong, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    createSubBuffer
     * Signature: (JJI[B)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createSubBuffer
    (JNIEnv *, jclass, jlong, jlong, jint, jbyteArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    clReleaseMemObject
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLContext_clReleaseMemObject
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    createArrayOnDevice
     * Signature: (JJ[B)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3B
    (JNIEnv *, jclass, jlong, jlong, jbyteArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    createArrayOnDevice
     * Signature: (JJ[I)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3I
    (JNIEnv *, jclass, jlong, jlong, jintArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    createArrayOnDevice
     * Signature: (JJ[F)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3F
    (JNIEnv *, jclass, jlong, jlong, jfloatArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    createArrayOnDevice
     * Signature: (JJ[D)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_createArrayOnDevice__JJ_3D
    (JNIEnv *, jclass, jlong, jlong, jdoubleArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    clCreateProgramWithSource
     * Signature: (J[B[J)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_clCreateProgramWithSource
    (JNIEnv *, jclass, jlong, jbyteArray, jlongArray);

    /*
     * Class:     tornado_drivers_opencl_OCLContext
     * Method:    clCreateProgramWithBinary
     * Signature: (JJ[B[J)J
     */
    JNIEXPORT jlong JNICALL Java_tornado_drivers_opencl_OCLContext_clCreateProgramWithBinary
    (JNIEnv *, jclass, jlong, jlong, jbyteArray, jlongArray);

#ifdef __cplusplus
}
#endif
#endif
/* Header for class tornado_drivers_opencl_OCLContext_OCLBufferResult */

#ifndef _Included_tornado_drivers_opencl_OCLContext_OCLBufferResult
#define _Included_tornado_drivers_opencl_OCLContext_OCLBufferResult
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
}
#endif
#endif
