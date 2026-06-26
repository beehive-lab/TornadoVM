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
#include <cstdint>
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
 * CUDA exposes no absolute event timestamps (unlike OpenCL's COMMAND_START/END),
 * only cuEventElapsedTime between two events. The absolute-timestamp queries are
 * therefore reported as zero; elapsed time is obtained via cuEventElapsedTime
 * below.
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
 * Method:    cuEventElapsedTime
 * Signature: (J)J
 *
 * Returns the device time, in NANOSECONDS, of the operation bracketed by the
 * event's start/end CUevents (cuEventElapsedTime reports milliseconds as a
 * float). Returns 0 when the event has no start timestamp (e.g. a marker), or if
 * the events are not both complete / the query fails.
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_cuEventElapsedTime
        (JNIEnv *env, jclass clazz, jlong event_id) {
    cuda_event_t *ev = (cuda_event_t *) event_id;
    if (ev == nullptr || ev->start == nullptr || ev->event == nullptr) {
        return 0;
    }
    // Ensure both events have completed before querying (otherwise
    // cuEventElapsedTime returns CUDA_ERROR_NOT_READY).
    if (cuEventSynchronize(ev->event) != CUDA_SUCCESS) {
        return 0;
    }
    float milliseconds = 0.0f;
    CUresult result = cuEventElapsedTime(&milliseconds, ev->start, ev->event);
    if (result != CUDA_SUCCESS) {
        return 0;
    }
    return (jlong) (milliseconds * 1.0e6); // ms -> ns
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    clWaitForEvents
 * Signature: ([J)V
 *
 * IMPORTANT: this method is reached with TWO different array layouts, so it
 * must not assume a leading count word:
 *
 *   1. CUDAEvent.waitForEvents()  -> new long[] { handle }          (plain list)
 *   2. CUDAEvent.waitOnPassive()  -> long[] { 1, handle }           (count-prefixed)
 *
 * The original code blindly read raw[0] as the element count. For layout (1)
 * raw[0] is the boxed cuda_event_t* pointer value (a large number), so the loop
 * ran for billions of iterations and dereferenced raw[i + 1], walking far past
 * the 1-element array. That read garbage as cuda_event_t* and crashed in
 * cuEventSynchronize(ev->event) with a SIGSEGV.
 *
 * Each boxed handle is a heap pointer returned by `new cuda_event_t()`
 * (see CUDACommandQueue.cpp::record_event), so it is non-null, pointer-aligned
 * and well above the low address page. A spurious count word such as 1 fails
 * those checks. We therefore iterate over EVERY element and only synchronise on
 * entries that look like a genuine boxed-event pointer. This handles both
 * layouts safely without dereferencing wild memory, and never reads out of
 * bounds because the loop is bounded by the real array length.
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_clWaitForEvents
        (JNIEnv *env, jclass clazz, jlongArray array) {
    if (array == NULL) {
        return;
    }
    jsize len = env->GetArrayLength(array);
    if (len <= 0) {
        return;
    }
    jlong *raw = static_cast<jlong *>(env->GetPrimitiveArrayCritical(array, NULL));
    if (raw == NULL) {
        return;
    }
    /* Minimum plausible heap address: reject null and small integers (e.g. a
     * stale count word) that can never be a valid cuda_event_t* pointer. */
    const uintptr_t MIN_VALID_PTR = 0x10000;
    for (jsize i = 0; i < len; i++) {
        uintptr_t value = (uintptr_t) raw[i];
        if (value < MIN_VALID_PTR) {
            continue; // null, a count word, or otherwise not a real handle
        }
        if ((value % alignof(cuda_event_t *)) != 0) {
            continue; // misaligned: cannot be a heap-allocated handle pointer
        }
        cuda_event_t *ev = (cuda_event_t *) value;
        CUresult result = cuEventSynchronize(ev->event);
        LOG_CUDA_AND_VALIDATE("cuEventSynchronize", result);
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
    if (ev->start != nullptr) {
        CUresult startResult = cuEventDestroy(ev->start);
        LOG_CUDA_AND_VALIDATE("cuEventDestroy(start)", startResult);
    }
    if (ev->event != nullptr) {
        CUresult result = cuEventDestroy(ev->event);
        LOG_CUDA_AND_VALIDATE("cuEventDestroy", result);
    }
    delete ev;
}

} // extern "C"
