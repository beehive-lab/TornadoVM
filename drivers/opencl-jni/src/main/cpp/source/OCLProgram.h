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
