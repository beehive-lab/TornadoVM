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

#ifdef __cplusplus
}
#endif
#endif
