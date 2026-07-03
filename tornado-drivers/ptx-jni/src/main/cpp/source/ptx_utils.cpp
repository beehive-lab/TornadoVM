/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
 * The University of Manchester.
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
#include <cuda.h>
#include <iostream>
#include "ptx_utils.h"
#include "ptx_log.h"

/* Creates a before/after timing event pair with CU_EVENT_DEFAULT (timing enabled). */
CUresult record_events_create(CUevent* beforeEvent, CUevent* afterEvent) {
    CUresult result = cuEventCreate(beforeEvent, CU_EVENT_DEFAULT);
    LOG_PTX_AND_VALIDATE("cuEventCreate (beforeEvent)", result);
    result = cuEventCreate(afterEvent, CU_EVENT_DEFAULT);
    LOG_PTX_AND_VALIDATE("cuEventCreate (afterEvent)", result);
    return result;
}

/*
 * Creates a lightweight sync-only CUDA event (CU_EVENT_DISABLE_TIMING).
 * Use this instead of record_events_create when timing data is not needed,
 * e.g. for cross-stream dependency edges during CUDA graph capture fork/join.
 */
CUresult sync_event_create(CUevent* event) {
    return cuEventCreate(event, CU_EVENT_DISABLE_TIMING);
}

/* Records an event on the given stream. */
CUresult record_event(CUevent* event, CUstream* stream) {
    CUresult result = cuEventRecord(*event, *stream);
    LOG_PTX_AND_VALIDATE("cuEventRecord", result);
    return result;
}

/* Deserializes a CUstream handle from a JNI byte array. */
void stream_from_array(JNIEnv *env, CUstream *stream_ptr, jbyteArray array) {
    env->GetByteArrayRegion(array, 0, sizeof(CUstream), reinterpret_cast<jbyte *>(stream_ptr));
}

/* Deserializes a CUevent handle from a JNI byte array. */
void event_from_array(JNIEnv *env, CUevent *event_ptr, jbyteArray array) {
    env->GetByteArrayRegion(array, 0, sizeof(CUevent), reinterpret_cast<jbyte *>(event_ptr));
}

/* Serializes a CUstream handle into a newly allocated JNI byte array. */
jbyteArray array_from_stream(JNIEnv *env, CUstream *stream) {
    jbyteArray array = env->NewByteArray(sizeof(CUstream));
    env->SetByteArrayRegion(array, 0, sizeof(CUstream), reinterpret_cast<const jbyte *>(stream));
    return array;
}

/* Serializes a CUevent handle into a newly allocated JNI byte array. */
jbyteArray array_from_event(JNIEnv *env, CUevent *event) {
    jbyteArray array = env->NewByteArray(sizeof(CUevent));
    env->SetByteArrayRegion(array, 0, sizeof(CUevent), reinterpret_cast<const jbyte *>(event));
    return array;
}
