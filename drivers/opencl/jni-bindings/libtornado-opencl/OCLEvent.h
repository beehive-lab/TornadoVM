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
/* Header for class tornado_drivers_opencl_OCLEvent */

#ifndef _Included_tornado_drivers_opencl_OCLEvent
#define _Included_tornado_drivers_opencl_OCLEvent
#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     tornado_drivers_opencl_OCLEvent
     * Method:    clGetEventInfo
     * Signature: (JI[B)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clGetEventInfo
    (JNIEnv *, jclass, jlong, jint, jbyteArray);

    /*
     * Class:     tornado_drivers_opencl_OCLEvent
     * Method:    clGetEventProfilingInfo
     * Signature: (JI[B)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clGetEventProfilingInfo
    (JNIEnv *, jclass, jlong, jint, jbyteArray);

    /*
     * Class:     tornado_drivers_opencl_OCLEvent
     * Method:    clWaitForEvents
     * Signature: ([J)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clWaitForEvents
    (JNIEnv *, jclass, jlongArray);

    /*
     * Class:     tornado_drivers_opencl_OCLEvent
     * Method:    clReleaseEvent
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clReleaseEvent
    (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
