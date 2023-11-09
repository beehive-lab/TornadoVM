/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
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
/* Header for class uk_ac_manchester_tornado_drivers_ptx_PTXStream */

#ifndef _Included_uk_ac_manchester_tornado_drivers_ptx_PTXStream
#define _Included_uk_ac_manchester_tornado_drivers_ptx_PTXStream
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3BJ_3B
  (JNIEnv *, jclass, jlong, jlong, jbyteArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3SJ_3B
  (JNIEnv *, jclass, jlong, jlong, jshortArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3CJ_3B
  (JNIEnv *, jclass, jlong, jlong, jcharArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3IJ_3B
  (JNIEnv *, jclass, jlong, jlong, jintArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3JJ_3B
  (JNIEnv *, jclass, jlong, jlong, jlongArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3FJ_3B
  (JNIEnv *, jclass, jlong, jlong, jfloatArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3DJ_3B
  (JNIEnv *, jclass, jlong, jlong, jdoubleArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3BJ_3B
  (JNIEnv *, jclass, jlong, jlong, jbyteArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3SJ_3B
  (JNIEnv *, jclass, jlong, jlong, jshortArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3CJ_3B
  (JNIEnv *, jclass, jlong, jlong, jcharArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3IJ_3B
  (JNIEnv *, jclass, jlong, jlong, jintArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3JJ_3B
  (JNIEnv *, jclass, jlong, jlong, jlongArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3FJ_3B
  (JNIEnv *, jclass, jlong, jlong, jfloatArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3DJ_3B
  (JNIEnv *, jclass, jlong, jlong, jdoubleArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3BJ_3B
  (JNIEnv *, jclass, jlong, jlong, jbyteArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3SJ_3B
  (JNIEnv *, jclass, jlong, jlong, jshortArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3CJ_3B
  (JNIEnv *, jclass, jlong, jlong, jcharArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3IJ_3B
  (JNIEnv *, jclass, jlong, jlong, jintArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3JJ_3B
  (JNIEnv *, jclass, jlong, jlong, jlongArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3FJ_3B
  (JNIEnv *, jclass, jlong, jlong, jfloatArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[DJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3DJ_3B
  (JNIEnv *, jclass, jlong, jlong, jdoubleArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3BJ_3B
  (JNIEnv *, jclass, jlong, jlong, jbyteArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3SJ_3B
  (JNIEnv *, jclass, jlong, jlong, jshortArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3CJ_3B
  (JNIEnv *, jclass, jlong, jlong, jcharArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3IJ_3B
  (JNIEnv *, jclass, jlong, jlong, jintArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3JJ_3B
  (JNIEnv *, jclass, jlong, jlong, jlongArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3FJ_3B
  (JNIEnv *, jclass, jlong, jlong, jfloatArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[DJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3DJ_3B
  (JNIEnv *, jclass, jlong, jlong, jdoubleArray, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuLaunchKernel
 * Signature: ([BLjava/lang/String;IIIIIIJ[B[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuLaunchKernel
  (JNIEnv *, jclass, jbyteArray, jstring, jint, jint, jint, jint, jint, jint, jlong, jbyteArray, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuCreateStream
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuCreateStream
  (JNIEnv *, jclass);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuDestroyStream
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuDestroyStream
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuStreamSynchronize
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuStreamSynchronize
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuEventCreateAndRecord
 * Signature: (Z[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuEventCreateAndRecord
  (JNIEnv *, jclass, jboolean, jbyteArray);


/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJJJ_3B
  (JNIEnv *, jclass, jlong, jlong, jlong, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJJJ_3B
  (JNIEnv *, jclass, jlong, jlong, jlong, jlong, jbyteArray);

/*
* Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
* Method:    writeArrayHtoD
* Signature: (JJJJ[B)[[B
*/
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJJJ_3B
(JNIEnv *, jclass, jlong, jlong, jlong, jlong, jbyteArray);

/*
* Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
* Method:    writeArrayHtoDAsync
* Signature: (JJ[DJ[I)[[B
*/
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJJJ_3B
(JNIEnv *, jclass, jlong, jlong, jlong, jlong, jbyteArray);


#ifdef __cplusplus
}
#endif
#endif
