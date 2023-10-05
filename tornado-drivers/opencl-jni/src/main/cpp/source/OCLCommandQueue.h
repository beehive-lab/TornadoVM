/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 * Method:    writeArrayToDevice
 * Signature: (JJJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_writeArrayToDevice__JJJZJJJ_3J
        (JNIEnv *, jclass, jlong, jlong, jlong, jboolean, jlong, jlong, jlong, jlongArray);

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
 * Method:    readArrayFromDeviceOffHeap
 * Signature: (JJJZJJJ[J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLCommandQueue_readArrayFromDeviceOffHeap__JJJZJJJ_3J
        (JNIEnv *, jclass, jlong, jlong, jlong, jboolean, jlong, jlong, jlong, jlongArray);

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
