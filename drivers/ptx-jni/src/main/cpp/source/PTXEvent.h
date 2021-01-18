/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

#ifdef __cplusplus
}
#endif
#endif
