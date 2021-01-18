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
 * Method:    clEnqueueNDRangeKernel
 * Signature: (JJI[J[J[J[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_clEnqueueNDRangeKernel
        (JNIEnv *, jclass, jlong, jlong, jint, jlongArray, jlongArray, jlongArray, jlongArray);

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
 * Signature: (J[BJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3BJZJJJ_3J
        (JNIEnv *, jclass, jlong, jbyteArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[CJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3CJZJJJ_3J
        (JNIEnv *, jclass, jlong, jcharArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);


/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[SJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3SJZJJJ_3J
        (JNIEnv *, jclass, jlong, jshortArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[IJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3IJZJJJ_3J
        (JNIEnv *, jclass, jlong, jintArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3JJZJJJ_3J
        (JNIEnv *, jclass, jlong, jlongArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[FJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3FJZJJJ_3J
        (JNIEnv *, jclass, jlong, jfloatArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    writeArrayToDevice
 * Signature: (J[DJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__J_3DJZJJJ_3J
        (JNIEnv *, jclass, jlong, jdoubleArray, jlong hostOffset, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[BJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3BJZJJJ_3J
        (JNIEnv *, jclass , jlong, jbyteArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[CJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3CJZJJJ_3J
        (JNIEnv *, jclass , jlong, jcharArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);


/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[SJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3SJZJJJ_3J
        (JNIEnv *, jclass, jlong, jshortArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[IJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3IJZJJJ_3J
        (JNIEnv *, jclass, jlong, jintArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[JJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3JJZJJJ_3J
        (JNIEnv *, jclass, jlong, jlongArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[FJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3FJZJJJ_3J
        (JNIEnv *, jclass, jlong, jfloatArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue
 * Method:    readArrayFromDevice
 * Signature: (J[DJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDevice__J_3DJZJJJ_3J
        (JNIEnv *, jclass, jlong, jdoubleArray, jlong, jboolean, jlong, jlong, jlong, jlongArray);

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
