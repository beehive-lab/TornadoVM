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
/* Header for class tornado_drivers_opencl_OCLKernel */

#ifndef _Included_tornado_drivers_opencl_OCLKernel
#define _Included_tornado_drivers_opencl_OCLKernel
#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     tornado_drivers_opencl_OCLKernel
     * Method:    clReleaseKernel
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clReleaseKernel
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     tornado_drivers_opencl_OCLKernel
     * Method:    clSetKernelArg
     * Signature: (JIJ[B)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clSetKernelArg
    (JNIEnv *, jclass, jlong, jint, jlong, jbyteArray);

    /*
     * Class:     tornado_drivers_opencl_OCLKernel
     * Method:    clGetKernelInfo
     * Signature: (JI[B)V
     */
    JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLKernel_clGetKernelInfo
    (JNIEnv *, jclass, jlong, jint, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
