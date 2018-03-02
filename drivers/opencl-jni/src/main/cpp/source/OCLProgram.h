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
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OCLProgram */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OCLProgram
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OCLProgram
#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
     * Method:    clReleaseProgram
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clReleaseProgram
    (JNIEnv *, jclass, jlong);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
     * Method:    clBuildProgram
     * Signature: (J[JLjava/lang/String;)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clBuildProgram
    (JNIEnv *, jclass, jlong, jlongArray, jstring);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
     * Method:    clGetProgramInfo
     * Signature: (JI[B)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clGetProgramInfo
    (JNIEnv *, jclass, jlong, jint, jbyteArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
     * Method:    clGetProgramBuildInfo
     * Signature: (JJI[B)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clGetProgramBuildInfo
    (JNIEnv *, jclass, jlong, jlong, jint, jbyteArray);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
     * Method:    clCreateKernel
     * Signature: (JLjava/lang/String;)J
     */
    JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_clCreateKernel
    (JNIEnv *, jclass, jlong, jstring);

    /*
     * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLProgram
     * Method:    getBinaries
     * Signature: (JJ[B)V
     */
    JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLProgram_getBinaries
    (JNIEnv *, jclass, jlong, jlong, jobject);

#ifdef __cplusplus
}
#endif
#endif
