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
#include <cuda.h>

#include <iostream>
#include "PTXEvent.h"
#include "ptx_log.h"

jbyteArray array_from_event(JNIEnv *env, CUevent *event) {
    jbyteArray array = env->NewByteArray(sizeof(CUevent));
    env->SetByteArrayRegion(array, 0, sizeof(CUevent), reinterpret_cast<const jbyte *>(event));
    return array;
}

jobjectArray wrapper_from_events(JNIEnv *env, CUevent *event1, CUevent *event2) {
    jbyteArray array1 = array_from_event(env, event1);
    jbyteArray array2 = array_from_event(env, event2);
    jclass byteClass = env->FindClass("[B");
    jobjectArray wrapper_2d_array = env->NewObjectArray(2, byteClass, NULL);
    env->SetObjectArrayElement(wrapper_2d_array, (jsize) 0, array1);
    env->SetObjectArrayElement(wrapper_2d_array, (jsize) 1, array2);
    return wrapper_2d_array;
}

void event_from_array(JNIEnv *env, CUevent *event, jbyteArray array) {
    env->GetByteArrayRegion(array, 0, sizeof(CUevent), reinterpret_cast<jbyte *>(event));
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    cuEventDestroy
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuEventDestroy
  (JNIEnv *env, jclass clazz, jbyteArray event_wrapper) {
    CUevent event;
    event_from_array(env, &event, event_wrapper);
    CUresult result = cuEventDestroy(event);
    LOG_PTX_AND_VALIDATE("cuEventDestroy", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    tornadoCUDAEventsSynchronize
 * Signature: ([[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_tornadoCUDAEventsSynchronize
  (JNIEnv *env, jclass clazz, jobjectArray wrappers) {
    jsize events_length = env->GetArrayLength(wrappers);

    for (int i = 0; i < events_length; i++) {
        jbyteArray array = (jbyteArray) env->GetObjectArrayElement(wrappers, i);
        CUevent event;
        event_from_array(env, &event, array);
        if (cuEventQuery(event) != CUDA_SUCCESS) {
            // Only wait on event if not completed yet
            CUresult result = cuEventSynchronize(event);
            LOG_PTX_AND_VALIDATE("cuEventSynchronize", result);
        }
        env->DeleteLocalRef(array);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    cuEventElapsedTime
 * Signature: ([[B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuEventElapsedTime
  (JNIEnv *env, jclass clazz, jobjectArray wrapper) {
    jbyteArray array1 = (jbyteArray) env->GetObjectArrayElement(wrapper,0);
    jbyteArray array2 = (jbyteArray) env->GetObjectArrayElement(wrapper,1);

    CUevent beforeEvent, afterEvent;
    event_from_array(env, &beforeEvent, array1);
    event_from_array(env, &afterEvent, array2);

    float time;
    CUresult result = cuEventElapsedTime(&time, beforeEvent, afterEvent);
    LOG_PTX_AND_VALIDATE("cuEventElapsedTime", result);
    // cuEventElapsedTime returns the time in milliseconds.  We convert because the tornado profiler uses nanoseconds.
    return (jlong) (time * 1e+6);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXEvent
 * Method:    cuEventQuery
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXEvent_cuEventQuery
  (JNIEnv *env, jclass clazz, jbyteArray wrapper) {
    CUevent event;
    event_from_array(env, &event, wrapper);

    CUresult result = cuEventQuery(event);
    LOG_PTX_AND_VALIDATE("cuEventQuery", result);

    if (result != CUDA_SUCCESS && result != CUDA_ERROR_NOT_READY) {
        std::cout << "\t[JNI] " << __FILE__ << ":" << __LINE__ << " cuEventQuery returned" << result << std::endl;
    }
    return (unsigned long) result;
}