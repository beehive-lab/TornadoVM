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
/* Header for class uk_ac_manchester_tornado_drivers_opencl_OCLEvent */

#ifndef _Included_uk_ac_manchester_tornado_drivers_opencl_OCLEvent
#define _Included_uk_ac_manchester_tornado_drivers_opencl_OCLEvent
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clGetEventInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clGetEventInfo
        (JNIEnv *, jclass, jlong, jint, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clGetEventProfilingInfo
 * Signature: (JJ[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clGetEventProfilingInfo
        (JNIEnv *, jclass, jlong, jlong, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clWaitForEvents
 * Signature: ([J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clWaitForEvents
        (JNIEnv *, jclass, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clReleaseEvent
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clReleaseEvent
        (JNIEnv *, jclass, jlong);

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_OCLEvent
 * Method:    clAttachCallback
 * Signature: (JLjava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_OCLEvent_clAttachCallback
        (JNIEnv *env, jclass clazz, jlong event, jobject callback);

#ifdef __cplusplus
}
#endif
#endif
