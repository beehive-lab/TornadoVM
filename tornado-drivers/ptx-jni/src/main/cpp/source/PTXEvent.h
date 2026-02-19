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
/* Header for class uk_ac_manchester_tornado_drivers_ptx_PTXEvent */

#ifndef _Included_uk_ac_manchester_tornado_drivers_ptx_PTXEvent
#define _Included_uk_ac_manchester_tornado_drivers_ptx_PTXEvent
#ifdef __cplusplus
extern "C" {
#endif

jbyteArray array_from_event(JNIEnv *env, CUevent *event);

jobjectArray wrapper_from_events(JNIEnv *env, CUevent *event1, CUevent *event2);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    cuEventDestroy
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuEventDestroy
        (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    tornadoCUDAEventsSynchronize
 * Signature: ([[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_tornadoCUDAEventsSynchronize
        (JNIEnv *, jclass, jobjectArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    cuEventElapsedTime
 * Signature: ([[B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuEventElapsedTime
        (JNIEnv *env, jclass clazz, jobjectArray wrapper);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    cuEventQuery
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuEventQuery
        (JNIEnv *, jclass, jbyteArray);

/*
* Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
* Method:    cuStreamWaitEvent
* Signature: ([B[B)V
*/
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuStreamWaitEvent
        (JNIEnv *, jclass, jbyteArray, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
