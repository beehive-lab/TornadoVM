/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include <jni.h>
#include "cuda_jni.h"

extern "C" {

/* OpenCL cl_event_info: CL_EVENT_COMMAND_EXECUTION_STATUS == 0x11D3. */
#define CL_EVENT_COMMAND_EXECUTION_STATUS 0x11D3
/* CL_COMPLETE == 0 (see cloned CUDACommandExecutionStatus). */
#define CL_COMPLETE 0

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    clGetEventInfo
 * Signature: (JI[B)V
 *
 * For MVP we report events as complete (transfers/launches are synchronised
 * inside the queue calls), so the Java status decode resolves to CL_COMPLETE.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_clGetEventInfo
        (JNIEnv *env, jclass clazz, jlong event_id, jint param_name, jbyteArray array) {
    cuda_event_t *ev = (cuda_event_t *) event_id;
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);

    if (param_name == CL_EVENT_COMMAND_EXECUTION_STATUS && len >= (jsize) sizeof(int)) {
        int status = CL_COMPLETE;
        if (ev != nullptr) {
            CUresult q = cuEventQuery(ev->event);
            status = (q == CUDA_SUCCESS) ? CL_COMPLETE : 1 /* CL_RUNNING */;
        }
        std::memcpy(buf, &status, sizeof(int));
    }
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    clGetEventProfilingInfo
 * Signature: (JJ[B)V
 *
 * STUB: profiling timestamps are not wired for MVP; report zero (cl_ulong).
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_clGetEventProfilingInfo
        (JNIEnv *env, jclass clazz, jlong event_id, jlong param_name, jbyteArray array) {
    jbyte *buf = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize len = env->GetArrayLength(array);
    std::memset(buf, 0, len);
    env->ReleasePrimitiveArrayCritical(array, buf, 0);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    clWaitForEvents
 * Signature: ([J)V
 *
 * Events array layout is [count, e0, e1, ...].
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_clWaitForEvents
        (JNIEnv *env, jclass clazz, jlongArray array) {
    if (array == NULL) {
        return;
    }
    jlong *raw = static_cast<jlong *>(env->GetPrimitiveArrayCritical(array, NULL));
    jsize count = (jsize) raw[0];
    for (jsize i = 0; i < count; i++) {
        cuda_event_t *ev = (cuda_event_t *) raw[i + 1];
        if (ev != nullptr) {
            CUresult result = cuEventSynchronize(ev->event);
            LOG_CUDA_AND_VALIDATE("cuEventSynchronize", result);
        }
    }
    env->ReleasePrimitiveArrayCritical(array, raw, JNI_ABORT);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    clReleaseEvent
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_clReleaseEvent
        (JNIEnv *env, jclass clazz, jlong event_id) {
    cuda_event_t *ev = (cuda_event_t *) event_id;
    if (ev == nullptr) {
        return;
    }
    CUresult result = cuEventDestroy(ev->event);
    LOG_CUDA_AND_VALIDATE("cuEventDestroy", result);
    delete ev;
}

} // extern "C"
