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
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clReleaseCommandQueue
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clReleaseCommandQueue
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clGetCommandQueueInfo
     * Signature: (JI[B)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clGetCommandQueueInfo
    (JNIEnv *, jclass, jlong, jint, jbyteArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clSetCommandQueueProperty
     * Signature: (JJZ)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clSetCommandQueueProperty
    (JNIEnv *, jclass, jlong, jlong, jboolean);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueNDRangeKernel
     * Signature: (JJI[J[J[J[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueNDRangeKernel
    (JNIEnv *, jclass, jlong, jlong, jint, jlongArray, jlongArray, jlongArray, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueTask
     * Signature: (JJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueTask
    (JNIEnv *, jclass, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueReadBuffer
     * Signature: (JJZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueReadBuffer
    (JNIEnv *, jclass, jlong, jlong, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueWriteBuffer
     * Signature: (JJZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWriteBuffer
    (JNIEnv *, jclass, jlong, jlong, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueMapBuffer
     * Signature: (JJJZIJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMapBuffer
    (JNIEnv *, jclass, jlong, jlong, jlong, jboolean, jint, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueMapBuffer2
     * Signature: (JJZIJJ[J)Luk/ac/manchester/tornado/drivers/opencl/OCLCommandQueue/OCLMapResult;
     */
    JNIEXPORT jobject JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMapBuffer2
    (JNIEnv *, jclass, jlong, jlong, jboolean, jint, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueUnmapMemObject
     * Signature: (JJLjava/nio/ByteBuffer;[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueUnmapMemObject
    (JNIEnv *, jclass, jlong, jlong, jobject, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    writeArrayToDevice
     * Signature: (J[BZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3BZJJJ_3J
    (JNIEnv *, jclass, jlong, jbyteArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    writeArrayToDevice
     * Signature: (J[SZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3SZJJJ_3J
    (JNIEnv *, jclass, jlong, jshortArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    writeArrayToDevice
     * Signature: (J[IZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3IZJJJ_3J
    (JNIEnv *, jclass, jlong, jintArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    writeArrayToDevice
     * Signature: (J[JZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3JZJJJ_3J
    (JNIEnv *, jclass, jlong, jlongArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    writeArrayToDevice
     * Signature: (J[FZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3FZJJJ_3J
    (JNIEnv *, jclass, jlong, jfloatArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    writeArrayToDevice
     * Signature: (J[DZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3DZJJJ_3J
    (JNIEnv *, jclass, jlong, jdoubleArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    readArrayFromDevice
     * Signature: (J[BZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3BZJJJ_3J
    (JNIEnv *, jclass, jlong, jbyteArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    readArrayFromDevice
     * Signature: (J[SZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3SZJJJ_3J
    (JNIEnv *, jclass, jlong, jshortArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    readArrayFromDevice
     * Signature: (J[IZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3IZJJJ_3J
    (JNIEnv *, jclass, jlong, jintArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    readArrayFromDevice
     * Signature: (J[JZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3JZJJJ_3J
    (JNIEnv *, jclass, jlong, jlongArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    readArrayFromDevice
     * Signature: (J[FZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3FZJJJ_3J
    (JNIEnv *, jclass, jlong, jfloatArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    readArrayFromDevice
     * Signature: (J[DZJJJ[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3DZJJJ_3J
    (JNIEnv *, jclass, jlong, jdoubleArray, jboolean, jlong, jlong, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueMarker
     * Signature: (J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarker
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueBarrier
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrier
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueWaitForEvents
     * Signature: (J[J)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueWaitForEvents
    (JNIEnv *, jclass, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueMarkerWithWaitList
     * Signature: (J[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueMarkerWithWaitList
    (JNIEnv *, jclass, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clEnqueueBarrierWithWaitList
     * Signature: (J[J)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueBarrierWithWaitList
    (JNIEnv *, jclass, jlong, jlongArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clFlush
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFlush
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
     * Method:    clFinish
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clFinish
    (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_OCLMapResult */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_OCLMapResult
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_OCLMapResult
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
}
#endif
#endif
